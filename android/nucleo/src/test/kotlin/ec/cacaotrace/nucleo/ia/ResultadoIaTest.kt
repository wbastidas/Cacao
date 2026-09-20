package ec.cacaotrace.nucleo.ia

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests de la capa de IA que no necesitan un modelo instalado: el umbral de
 * confianza (RN-16), el conteo que alimenta la norma, el aviso de RF-PRC-07 y
 * la supresión de solapamientos, que es lo que evita contar dos veces el mismo
 * grano.
 */
class ResultadoIaTest {

    private fun clasificacion(vararg p: Pair<String, Double>, umbral: Double = 0.6) =
        ResultadoClasificacion(
            predicciones = p.map { Prediccion(it.first, it.second) },
            modeloVersion = "2026.01.01",
            umbralConfianza = umbral,
        )

    private fun grano(
        clase: String,
        x: Double,
        y: Double,
        lado: Double = 0.08,
        conf: Double = 0.9,
    ) = GranoDetectado(clase, conf, x, y, lado, lado)

    @Nested
    inner class UmbralDeConfianza {
        @Test
        fun `por encima del umbral afirma la clase`() {
            val r = clasificacion("monilia" to 0.83, "sana" to 0.12)
            assertTrue(r.estaSeguro)
            assertEquals("monilia (83 %)", r.texto)
            assertNull(r.pistaBajaConfianza)
        }

        @Test
        fun `por debajo del umbral dice No estoy seguro`() {
            val r = clasificacion("monilia" to 0.41, "sana" to 0.39)
            assertFalse(r.estaSeguro)
            assertEquals("No estoy seguro", r.texto)
            assertTrue(r.pistaBajaConfianza!!.contains("monilia"))
            assertTrue(r.pistaBajaConfianza!!.contains("41 %"))
        }

        @Test
        fun `el umbral se puede subir para una tarea exigente`() {
            assertTrue(clasificacion("monilia" to 0.70, "sana" to 0.30).estaSeguro)
            assertFalse(
                clasificacion("monilia" to 0.70, "sana" to 0.30, umbral = 0.9).estaSeguro,
            )
        }

        @Test
        fun `sin predicciones no inventa nada`() {
            val r = clasificacion()
            assertNull(r.mejor)
            assertFalse(r.estaSeguro)
            assertEquals("Sin resultado", r.texto)
        }

        @Test
        fun `ordena las predicciones de mayor a menor confianza`() {
            val r = ResultadoClasificacion.de(
                clases = listOf("sana", "monilia", "fitoftora"),
                probabilidades = listOf(0.1, 0.7, 0.2),
                modeloVersion = "1.0",
                umbralConfianza = 0.6,
            )
            assertEquals("monilia", r.mejor?.clase)
            assertEquals(listOf("monilia", "fitoftora", "sana"), r.predicciones.map { it.clase })
        }
    }

    @Nested
    inner class ConteoDeLaPruebaDeCorte {
        @Test
        fun `agrupa los granos por clase`() {
            val r = ResultadoDeteccion(
                granos = listOf(
                    grano("bien_fermentado", 0.1, 0.1),
                    grano("bien_fermentado", 0.3, 0.1),
                    grano("violeta", 0.5, 0.1),
                ),
                modeloVersion = "1.0",
            )
            assertEquals(mapOf("bien_fermentado" to 2, "violeta" to 1), r.conteo)
            assertEquals(3, r.total)
        }

        @Test
        fun `avisa si el total se sale de 97 a 103`() {
            fun conN(n: Int) = ResultadoDeteccion(
                granos = (0 until n).map {
                    grano("bien_fermentado", (it % 10) / 10.0, (it / 10) / 10.0)
                },
                modeloVersion = "1.0",
            )
            assertFalse(conN(100).conteoDudoso)
            assertFalse(conN(97).conteoDudoso)
            assertFalse(conN(103).conteoDudoso)
            assertTrue(conN(96).conteoDudoso)
            assertTrue(conN(104).conteoDudoso)
        }

        @Test
        fun `corregir un grano cambia el conteo y queda marcado`() {
            val r = ResultadoDeteccion(
                granos = listOf(
                    grano("bien_fermentado", 0.1, 0.1),
                    grano("bien_fermentado", 0.3, 0.1),
                ),
                modeloVersion = "1.0",
            )
            val corregido = r.conGranoCorregido(1, "violeta")
            assertEquals(mapOf("bien_fermentado" to 1, "violeta" to 1), corregido.conteo)
            assertTrue(corregido.granos[1].corregidoPorUsuario)
            assertEquals(1.0, corregido.granos[1].confianza)
            // El original no se toca: nunca se pierde lo que dijo el modelo.
            assertEquals(2, r.conteo["bien_fermentado"])
        }
    }

    @Nested
    inner class SupresionDeSolapes {
        @Test
        fun `dos cajas sobre el mismo grano se cuentan una vez`() {
            val cajas = listOf(
                grano("bien_fermentado", 0.10, 0.10, conf = 0.6),
                // casi encima de la anterior, pero con más confianza
                grano("violeta", 0.105, 0.105, conf = 0.9),
            )
            val resultado = suprimirSolapes(cajas)
            assertEquals(1, resultado.size)
            assertEquals("violeta", resultado.single().clase, "debe ganar la más segura")
        }

        @Test
        fun `dos granos separados se conservan los dos`() {
            val cajas = listOf(
                grano("bien_fermentado", 0.10, 0.10),
                grano("violeta", 0.50, 0.50),
            )
            assertEquals(2, suprimirSolapes(cajas).size)
        }

        @Test
        fun `un tablero de 100 granos separados se mantiene en 100`() {
            val cajas = (0 until 100).map {
                grano("bien_fermentado", (it % 10) * 0.1, (it / 10) * 0.1, lado = 0.08)
            }
            assertEquals(100, suprimirSolapes(cajas).size)
        }

        @Test
        fun `el umbral de solape se puede ajustar`() {
            val cajas = listOf(
                grano("a", 0.10, 0.10, lado = 0.10, conf = 0.9),
                // se solapan a la mitad
                grano("b", 0.15, 0.10, lado = 0.10, conf = 0.8),
            )
            assertEquals(1, suprimirSolapes(cajas, umbralSolape = 0.2).size)
            assertEquals(2, suprimirSolapes(cajas, umbralSolape = 0.9).size)
        }

        @Test
        fun `la interseccion sobre union es cero si no se tocan`() {
            val a = grano("a", 0.0, 0.0, lado = 0.1)
            val b = grano("b", 0.5, 0.5, lado = 0.1)
            assertEquals(0.0, a.interseccionSobreUnion(b))
        }

        @Test
        fun `dos cajas identicas dan interseccion sobre union de 1`() {
            val a = grano("a", 0.2, 0.2, lado = 0.1)
            assertEquals(1.0, a.interseccionSobreUnion(a), 1e-9)
        }

        @Test
        fun `una lista vacia no revienta`() {
            assertTrue(suprimirSolapes(emptyList()).isEmpty())
        }
    }

    @Nested
    inner class LecturaDeLaSalidaYolo {
        private val clases = listOf("bien_fermentado", "violeta", "pizarroso")

        /** Construye la matriz [4 + clases][cajas] como la entrega YOLO. */
        private fun salida(vararg cajas: DoubleArray): Array<DoubleArray> {
            val atributos = 4 + clases.size
            return Array(atributos) { fila ->
                DoubleArray(cajas.size) { i -> cajas[i][fila] }
            }
        }

        @Test
        fun `convierte el centro y el tamano en esquina superior izquierda`() {
            // centro (0.5, 0.5), tamaño 0.2 -> esquina en (0.4, 0.4)
            val cajas = leerCajasYolo(
                salida(doubleArrayOf(0.5, 0.5, 0.2, 0.2, 0.9, 0.05, 0.05)),
                clases,
            )
            assertEquals(1, cajas.size)
            val c = cajas.single()
            assertEquals(0.4, c.x, 1e-9)
            assertEquals(0.4, c.y, 1e-9)
            assertEquals(0.2, c.ancho, 1e-9)
            assertEquals("bien_fermentado", c.clase)
        }

        @Test
        fun `descarta las cajas por debajo del umbral`() {
            val cajas = leerCajasYolo(
                salida(
                    doubleArrayOf(0.2, 0.2, 0.1, 0.1, 0.90, 0.02, 0.02),
                    doubleArrayOf(0.6, 0.6, 0.1, 0.1, 0.10, 0.05, 0.05),
                ),
                clases,
                umbralConfianza = 0.35,
            )
            assertEquals(1, cajas.size)
        }

        @Test
        fun `elige la clase de mayor puntaje`() {
            val cajas = leerCajasYolo(
                salida(doubleArrayOf(0.5, 0.5, 0.1, 0.1, 0.1, 0.8, 0.2)),
                clases,
            )
            assertEquals("violeta", cajas.single().clase)
        }

        @Test
        fun `una caja que se sale del borde se recorta`() {
            val cajas = leerCajasYolo(
                salida(doubleArrayOf(0.02, 0.02, 0.2, 0.2, 0.9, 0.0, 0.0)),
                clases,
            )
            assertEquals(0.0, cajas.single().x)
            assertEquals(0.0, cajas.single().y)
        }

        @Test
        fun `una salida con forma inesperada no revienta`() {
            assertTrue(leerCajasYolo(arrayOf(DoubleArray(3)), clases).isEmpty())
            assertTrue(leerCajasYolo(emptyArray(), clases).isEmpty())
        }

        @Test
        fun `un indice de clase fuera de rango cae en otro`() {
            // La matriz trae 3 clases pero solo se conocen 2 nombres.
            val cajas = leerCajasYolo(
                salida(doubleArrayOf(0.5, 0.5, 0.1, 0.1, 0.1, 0.1, 0.9)),
                listOf("bien_fermentado", "violeta"),
            )
            assertEquals("otro", cajas.single().clase)
        }
    }

    @Nested
    inner class SinModeloInstalado {
        @Test
        fun `el mensaje invita a contar a mano y no habla de archivos`() {
            val e = ModeloNoDisponible("mazorcas", MotivoSinModelo.NO_INSTALADO)
            assertTrue(e.message!!.contains("a mano"))
            assertFalse(e.message!!.contains("tflite"))
        }

        @Test
        fun `un error al cargar se distingue de no estar instalado`() {
            val e = ModeloNoDisponible("corte", MotivoSinModelo.ERROR_AL_CARGAR)
            assertTrue(e.message!!.contains("no se pudo abrir"))
        }
    }
}
