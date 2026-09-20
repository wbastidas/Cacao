package ec.cacaotrace.nucleo.norma

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows

/**
 * Tests de la calificación según la norma INEN 176.
 *
 * Los casos vienen de `casos_norma.json`, EL MISMO archivo que usan los tests
 * de Python en `entrenamiento/pruebas/test_calificar_corte.py`. Si la lógica de
 * Kotlin se desvía de la de Python, estos tests fallan. Esa es toda la idea: el
 * resultado que ve el usuario en el teléfono tiene que ser idéntico al que se
 * valida en la computadora contra tableros contados por un experto.
 */
class CalificadorCorteTest {

    private val tabla = TablaNorma.desdeJsonTexto(recurso("norma_inen176.json"))
    private val calificador = CalificadorCorte(tabla)
    private val casos = Json.parseToJsonElement(recurso("casos_norma.json")).jsonObject

    private fun recurso(nombre: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream(nombre)) {
            "Falta el recurso de prueba $nombre"
        }.bufferedReader().readText()

    private fun conteoDe(caso: JsonObject): Map<String, Int> =
        caso["conteo"]!!.jsonObject.mapValues { it.value.jsonPrimitive.content.toInt() }

    private fun nombreDe(caso: JsonObject): String = caso["nombre"]!!.jsonPrimitive.content

    @Test
    fun `hay casos cargados`() {
        assertTrue(casos["casos"]!!.jsonArray.isNotEmpty())
        assertTrue(casos["errores"]!!.jsonArray.isNotEmpty())
    }

    @TestFactory
    fun `cada caso da el resultado esperado en cada perfil`(): List<DynamicTest> =
        casos["casos"]!!.jsonArray.map { elemento ->
            val caso = elemento.jsonObject
            DynamicTest.dynamicTest(nombreDe(caso)) {
                val conteo = conteoDe(caso)
                caso["espera"]!!.jsonObject.forEach { (perfil, esperadoJson) ->
                    val esperado = esperadoJson.jsonObject
                    val r = calificador.calificar(conteo, perfil)
                    assertEquals(
                        esperado["resultado"]!!.jsonPrimitive.content,
                        r.resultado,
                        "${nombreDe(caso)} / $perfil",
                    )
                    assertEquals(
                        esperado["conforme"]!!.jsonPrimitive.content.toBoolean(),
                        r.conforme,
                        "${nombreDe(caso)} / $perfil",
                    )
                }
            }
        }

    @TestFactory
    fun `los porcentajes coinciden con los esperados`(): List<DynamicTest> =
        casos["casos"]!!.jsonArray.map { elemento ->
            val caso = elemento.jsonObject
            DynamicTest.dynamicTest(nombreDe(caso)) {
                val pct = calificador.porcentajes(conteoDe(caso))
                caso["porcentajes"]!!.jsonObject.forEach { (indicador, valor) ->
                    assertEquals(
                        valor.jsonPrimitive.content.toDouble(),
                        pct[indicador],
                        0.05,
                        "${nombreDe(caso)}: $indicador",
                    )
                }
                assertEquals(
                    caso["granos_evaluados"]!!.jsonPrimitive.content.toInt(),
                    pct.granosEvaluados,
                    "${nombreDe(caso)}: granos evaluados",
                )
                caso["granos_ignorados"]?.let {
                    assertEquals(it.jsonPrimitive.content.toInt(), pct.granosIgnorados)
                }
            }
        }

    @TestFactory
    fun `los avisos coinciden en cantidad`(): List<DynamicTest> =
        casos["casos"]!!.jsonArray
            .map { it.jsonObject }
            .filter { it.containsKey("avisos_esperados") }
            .map { caso ->
                DynamicTest.dynamicTest(nombreDe(caso)) {
                    val perfil = caso["espera"]!!.jsonObject.keys.first()
                    val r = calificador.calificar(conteoDe(caso), perfil)
                    assertEquals(
                        caso["avisos_esperados"]!!.jsonPrimitive.content.toInt(),
                        r.avisos.size,
                        "${nombreDe(caso)}: ${r.avisos}",
                    )
                }
            }

    @TestFactory
    fun `las entradas invalidas lanzan ErrorNorma`(): List<DynamicTest> =
        casos["errores"]!!.jsonArray.map { elemento ->
            val caso = elemento.jsonObject
            DynamicTest.dynamicTest(nombreDe(caso)) {
                assertThrows<ErrorNorma> { calificador.calificar(conteoDe(caso)) }
            }
        }

    @Test
    fun `fermentado total es la suma de bueno y ligero`() {
        val pct = calificador.porcentajes(
            mapOf(
                "bien_fermentado" to 50,
                "ligeramente_fermentado" to 20,
                "violeta" to 30,
            ),
        )
        assertEquals(
            pct["fermentado_bueno"] + pct["fermentado_ligero"],
            pct.fermentadoTotal,
            1e-9,
        )
    }

    @Test
    fun `un perfil inexistente lanza ErrorNorma`() {
        assertThrows<ErrorNorma> {
            calificador.calificar(mapOf("bien_fermentado" to 100), "no_existe")
        }
    }

    @Test
    fun `una clase desconocida avisa y no rompe el calculo`() {
        val r = calificador.calificar(
            mapOf("bien_fermentado" to 90, "clase_inventada" to 10),
        )
        assertEquals(90, r.porcentajes.granosEvaluados)
        assertTrue(r.avisos.any { it.contains("clase_inventada") })
    }

    @Test
    fun `todos los perfiles de la tabla se pueden calificar`() {
        val conteo = mapOf(
            "bien_fermentado" to 70,
            "ligeramente_fermentado" to 8,
            "violeta" to 12,
            "pizarroso" to 8,
            "mohoso" to 2,
        )
        val todos = calificador.calificarTodos(conteo)
        assertEquals(tabla.perfiles.size, todos.size)
        todos.values.forEach { assertTrue(it.resultado.isNotEmpty()) }
    }

    @Test
    fun `la tabla no usa indicadores que el calculo no produce`() {
        val validos = setOf(
            "fermentado_bueno", "fermentado_ligero", "fermentado_total",
            "violeta", "pizarroso", "mohoso", "defectuoso",
        )
        tabla.perfiles.values.forEach { p ->
            val requisitos =
                if (p.esPorGrados) p.grados.flatMap { it.requisitos } else p.requisitos
            requisitos.forEach { r ->
                assertTrue(
                    r.indicador in validos,
                    "El perfil ${p.clave} usa \"${r.indicador}\"",
                )
                assertTrue(r.tipo in setOf("min", "max"))
            }
        }
    }

    @Test
    fun `la tabla se puede exportar y volver a leer sin perder nada`() {
        val vuelta = TablaNorma.desdeJsonTexto(tabla.aJsonTexto())
        val c2 = CalificadorCorte(vuelta)
        val conteo = mapOf("bien_fermentado" to 70, "violeta" to 20, "pizarroso" to 10)
        assertEquals(calificador.calificar(conteo).resultado, c2.calificar(conteo).resultado)
    }

    @Test
    fun `avisa cuando se cuentan menos de 97 o mas de 103 granos`() {
        val pocos = calificador.calificar(mapOf("bien_fermentado" to 80))
        assertTrue(pocos.avisos.any { it.contains("80 granos") })

        val muchos = calificador.calificar(mapOf("bien_fermentado" to 110))
        assertTrue(muchos.avisos.any { it.contains("110 granos") })

        val justos = calificador.calificar(
            mapOf("bien_fermentado" to 80, "violeta" to 20),
        )
        assertTrue(justos.avisos.isEmpty())
    }

    @Test
    fun `el formato de numeros no depende de la configuracion regional`() {
        // Si se usara String.format, en un teléfono con coma decimal el texto
        // saldría distinto al de Python y los tests compartidos fallarían.
        val r = RequisitoNorma("fermentado_total", "min", 75.0)
        assertEquals(
            "fermentado total = 70.0 % (requiere ≥ 75 %)",
            r.explicarFalla(70.0),
        )
    }
}
