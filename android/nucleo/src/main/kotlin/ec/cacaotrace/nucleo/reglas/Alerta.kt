package ec.cacaotrace.nucleo.reglas

/**
 * Alertas que produce el motor de reglas.
 *
 * Cada alerta responde tres preguntas (RF-ALE-03): qué pasó, por qué importa y
 * qué hacer. La tercera es un enlace a la biblioteca de correcciones.
 */
data class Alerta(
    /** Código de la regla que la disparó: RN-01 … RN-17. */
    val regla: String,
    val severidad: Severidad,
    /** Titular corto: "La fermentación está fría". */
    val quePaso: String,
    /** Consecuencia, en palabras simples. */
    val porQueImporta: String,
    /** Acción inmediata, en una frase. */
    val queHacer: String,
    /** Código de la corrección asociada (C-01 … C-09), si la hay. */
    val correccion: String? = null,
    val valorMedido: Double? = null,
    val valorEsperado: Double? = null,
) {
    val bloquea: Boolean get() = severidad == Severidad.BLOQUEANTE

    /**
     * Clave estable para no repetir la misma alerta una y otra vez.
     *
     * Si la fermentación lleva tres días fría, el usuario no necesita doce
     * alertas idénticas: necesita una que siga abierta.
     */
    fun claveDeduplicacion(loteId: String): String = "$regla|$loteId"

    override fun toString(): String = "[$regla] $quePaso"
}

/** Qué tan urgente es una alerta. */
enum class Severidad(val etiqueta: String) {
    /** Conviene revisarlo, pero nada se echa a perder hoy. */
    AVISO("Aviso"),

    /** Hay que actuar ahora o el lote se daña. */
    URGENTE("Atención"),

    /** Impide continuar hasta que el usuario confirme (RN-08, RN-15). */
    BLOQUEANTE("Bloqueo"),
}
