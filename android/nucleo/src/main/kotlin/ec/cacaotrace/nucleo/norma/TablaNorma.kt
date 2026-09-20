package ec.cacaotrace.nucleo.norma

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Tabla de la norma NTE INEN 176 para la prueba de corte.
 *
 * La tabla es un DATO, no código: viene de `assets/norma/norma_inen176.json`,
 * el usuario puede editarla desde Ajustes y puede actualizarse sin publicar
 * una versión nueva de la app (RF-CFG-03, RNF-12).
 *
 * Es el mismo archivo que usa `entrenamiento/calificar_corte.py`, para que el
 * resultado en la computadora y en el teléfono sea idéntico.
 *
 * Se parsea a mano con [JsonObject] en vez de con clases `@Serializable`
 * porque los perfiles tienen dos formas distintas —unos traen `requisitos` y
 * otros `grados`— y una jerarquía de clases selladas para eso complicaría más
 * de lo que aclara.
 */
data class TablaNorma(
    /**
     * Clase que reporta el modelo o el conteo manual -> indicador de la norma.
     * El valor `ignorar` quiere decir que ese grano no entra en el cálculo.
     */
    val clasesDetector: Map<String, String>,
    val perfiles: Map<String, PerfilNorma>,
) {
    /** Clases que el usuario puede contar, en el orden en que se muestran. */
    val clasesContables: List<String> get() = clasesDetector.keys.toList()

    fun aJson(): JsonObject = buildJsonObject {
        put("clases_detector", buildJsonObject {
            clasesDetector.forEach { (k, v) -> put(k, v) }
        })
        put("perfiles", buildJsonObject {
            perfiles.forEach { (k, v) -> put(k, v.aJson()) }
        })
    }

    fun aJsonTexto(): String = jsonBonito.encodeToString(JsonObject.serializer(), aJson())

    companion object {
        private val jsonBonito = Json { prettyPrint = true }
        private val jsonTolerante = Json { ignoreUnknownKeys = true }

        fun desdeJsonTexto(texto: String): TablaNorma =
            desdeJson(jsonTolerante.parseToJsonElement(texto).jsonObject)

        fun desdeJson(raiz: JsonObject): TablaNorma {
            val clases = (raiz["clases_detector"] as? JsonObject)
                ?.mapValues { it.value.jsonPrimitive.content }
                ?: throw ErrorNorma("La tabla de la norma no tiene clases_detector")
            val perfiles = (raiz["perfiles"] as? JsonObject)
                ?.mapValues { PerfilNorma.desdeJson(it.key, it.value.jsonObject) }
                ?: throw ErrorNorma("La tabla de la norma no tiene perfiles")

            if (clases.isEmpty()) throw ErrorNorma("La tabla no define ninguna clase")
            if (perfiles.isEmpty()) throw ErrorNorma("La tabla no define ningún perfil")
            return TablaNorma(clases, perfiles)
        }
    }
}

/**
 * Un perfil de calificación.
 *
 * Hay dos formas: la simple (cumple o no cumple, como `ccn51_referencia`) y la
 * de grados (se prueba Grado 1, luego 2, luego 3, como `grados_1_2_3`).
 */
data class PerfilNorma(
    val clave: String,
    val descripcion: String = "",
    val requisitos: List<RequisitoNorma> = emptyList(),
    val grados: List<GradoNorma> = emptyList(),
    val resultadoSiCumple: String = "Conforme",
    val resultadoSiNoCumple: String = "No conforme",
) {
    val esPorGrados: Boolean get() = grados.isNotEmpty()

    fun aJson(): JsonObject = buildJsonObject {
        put("descripcion", descripcion)
        if (requisitos.isNotEmpty()) {
            put("requisitos", buildJsonArray { requisitos.forEach { add(it.aJson()) } })
        }
        if (grados.isNotEmpty()) {
            put("grados", buildJsonArray { grados.forEach { add(it.aJson()) } })
        }
        put("resultado_si_cumple", resultadoSiCumple)
        put("resultado_si_no_cumple", resultadoSiNoCumple)
    }

    companion object {
        fun desdeJson(clave: String, j: JsonObject) = PerfilNorma(
            clave = clave,
            descripcion = j["descripcion"]?.jsonPrimitive?.content.orEmpty(),
            requisitos = (j["requisitos"] as? JsonArray)
                ?.map { RequisitoNorma.desdeJson(it.jsonObject) } ?: emptyList(),
            grados = (j["grados"] as? JsonArray)
                ?.map { GradoNorma.desdeJson(it.jsonObject) } ?: emptyList(),
            resultadoSiCumple =
                j["resultado_si_cumple"]?.jsonPrimitive?.content ?: "Conforme",
            resultadoSiNoCumple =
                j["resultado_si_no_cumple"]?.jsonPrimitive?.content ?: "No conforme",
        )
    }
}

/** Un grado de calidad (Grado 1, 2 o 3) con sus requisitos. */
data class GradoNorma(val grado: String, val requisitos: List<RequisitoNorma>) {
    fun aJson(): JsonObject = buildJsonObject {
        put("grado", grado)
        put("requisitos", buildJsonArray { requisitos.forEach { add(it.aJson()) } })
    }

    companion object {
        fun desdeJson(j: JsonObject) = GradoNorma(
            grado = j["grado"]!!.jsonPrimitive.content,
            requisitos = j["requisitos"]!!.jsonArray.map {
                RequisitoNorma.desdeJson(it.jsonObject)
            },
        )
    }
}

/** Un requisito de la norma: "fermentado_total, como mínimo, 75 %". */
data class RequisitoNorma(
    val indicador: String,
    /** `min` o `max`. */
    val tipo: String,
    val valor: Double,
) {
    val esMinimo: Boolean get() = tipo == "min"

    /** ¿El valor medido cumple este requisito? */
    fun seCumpleCon(medido: Double): Boolean =
        if (esMinimo) medido >= valor else medido <= valor

    /** Texto para el usuario: "fermentado total = 70,0 % (requiere ≥ 75 %)". */
    fun explicarFalla(medido: Double): String {
        val signo = if (esMinimo) "≥" else "≤"
        val nombre = indicador.replace('_', ' ')
        return "$nombre = ${formatear(medido, 1)} % (requiere $signo ${formatear(valor, 0)} %)"
    }

    fun aJson(): JsonObject = buildJsonObject {
        put("indicador", indicador)
        put("tipo", tipo)
        put("valor", valor)
    }

    companion object {
        fun desdeJson(j: JsonObject) = RequisitoNorma(
            indicador = j["indicador"]!!.jsonPrimitive.content,
            tipo = j["tipo"]!!.jsonPrimitive.content,
            valor = j["valor"]!!.jsonPrimitive.content.toDouble(),
        )
    }
}

/**
 * Formatea un número con los decimales indicados, SIEMPRE con punto decimal.
 *
 * Se hace a mano en vez de con `String.format` porque este usa la
 * configuración regional de la JVM: en un teléfono configurado con coma
 * decimal, el texto de las alertas saldría distinto al de Python y los tests
 * compartidos fallarían. Toda la app formatea números por aquí.
 */
fun formatear(valor: Double, decimales: Int): String {
    if (decimales == 0) return kotlin.math.round(valor).toInt().toString()
    var factor = 1.0
    repeat(decimales) { factor *= 10 }
    val redondeado = kotlin.math.round(valor * factor) / factor
    val entero = kotlin.math.floor(kotlin.math.abs(redondeado)).toInt()
    val fraccion = kotlin.math.round((kotlin.math.abs(redondeado) - entero) * factor).toInt()
    val signo = if (redondeado < 0) "-" else ""
    return "$signo$entero.${fraccion.toString().padStart(decimales, '0')}"
}

/** Problema con el conteo o con la tabla de la norma. */
class ErrorNorma(mensaje: String) : IllegalArgumentException(mensaje)
