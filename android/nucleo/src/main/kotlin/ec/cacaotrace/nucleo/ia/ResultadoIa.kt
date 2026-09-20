package ec.cacaotrace.nucleo.ia

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lo que devuelve un modelo de visión (RF-IA-02, RF-IA-03).
 *
 * Vive en `:nucleo` y no en el módulo de Android porque es lógica pura —
 * umbral de confianza, conteo, supresión de solapamientos— y así se puede
 * probar en la JVM, sin emulador y sin un modelo instalado.
 */

/** Una clase con su probabilidad. */
data class Prediccion(val clase: String, val confianza: Double) {
    /** "monilia (83 %)" */
    val etiqueta: String get() = "$clase (${(confianza * 100).roundToInt()} %)"
}

/** El resultado de analizar una foto con un clasificador. */
data class ResultadoClasificacion(
    /** Ordenadas de mayor a menor confianza. */
    val predicciones: List<Prediccion>,
    val modeloVersion: String,
    val umbralConfianza: Double,
    val milisegundos: Long = 0,
) {
    val mejor: Prediccion? get() = predicciones.firstOrNull()

    /** RN-16: por debajo del umbral la app no afirma nada, pregunta. */
    val estaSeguro: Boolean get() = mejor?.let { it.confianza >= umbralConfianza } == true

    /** Lo que se le muestra al usuario. */
    val texto: String
        get() = when {
            mejor == null -> "Sin resultado"
            estaSeguro -> mejor!!.etiqueta
            else -> "No estoy seguro"
        }

    /** Mensaje de apoyo cuando la confianza es baja. */
    val pistaBajaConfianza: String?
        get() {
            val m = mejor ?: return null
            if (estaSeguro) return null
            return "Lo más parecido es \"${m.clase}\" con " +
                "${(m.confianza * 100).roundToInt()} % de seguridad, por debajo del " +
                "mínimo. Revisa la foto o elige tú la respuesta."
        }

    companion object {
        /** Construye el resultado ordenando y emparejando clases con probabilidades. */
        fun de(
            clases: List<String>,
            probabilidades: List<Double>,
            modeloVersion: String,
            umbralConfianza: Double,
            milisegundos: Long = 0,
        ): ResultadoClasificacion = ResultadoClasificacion(
            predicciones = clases.zip(probabilidades) { c, p -> Prediccion(c, p) }
                .sortedByDescending { it.confianza },
            modeloVersion = modeloVersion,
            umbralConfianza = umbralConfianza,
            milisegundos = milisegundos,
        )
    }
}

/**
 * Una caja detectada por el modelo M2 sobre la foto del tablero.
 *
 * Las coordenadas son normalizadas 0-1 respecto de la foto: así la caja se
 * dibuja bien sea cual sea el tamaño en pantalla.
 */
data class GranoDetectado(
    val clase: String,
    val confianza: Double,
    val x: Double,
    val y: Double,
    val ancho: Double,
    val alto: Double,
    /** True si el usuario tocó la caja y cambió su clase (RF-PRC-04). */
    val corregidoPorUsuario: Boolean = false,
) {
    fun corregir(nuevaClase: String): GranoDetectado =
        copy(clase = nuevaClase, confianza = 1.0, corregidoPorUsuario = true)

    /** Cuánto se solapa con otra caja, de 0 (nada) a 1 (idénticas). */
    fun interseccionSobreUnion(otra: GranoDetectado): Double {
        val x1 = max(x, otra.x)
        val y1 = max(y, otra.y)
        val x2 = min(x + ancho, otra.x + otra.ancho)
        val y2 = min(y + alto, otra.y + otra.alto)
        val w = x2 - x1
        val h = y2 - y1
        if (w <= 0 || h <= 0) return 0.0
        val interseccion = w * h
        val union = ancho * alto + otra.ancho * otra.alto - interseccion
        return if (union <= 0) 0.0 else interseccion / union
    }
}

/** El resultado de detectar los granos de un tablero. */
data class ResultadoDeteccion(
    val granos: List<GranoDetectado>,
    val modeloVersion: String,
    val milisegundos: Long = 0,
) {
    /** Conteo por clase, que es lo que come el calificador de la norma. */
    val conteo: Map<String, Int> get() = granos.groupingBy { it.clase }.eachCount()

    val total: Int get() = granos.size

    /** RF-PRC-07: avisar si el conteo se sale de 97-103. */
    val conteoDudoso: Boolean get() = total < GRANOS_MINIMO || total > GRANOS_MAXIMO

    fun conGranoCorregido(indice: Int, nuevaClase: String): ResultadoDeteccion =
        copy(granos = granos.mapIndexed { i, g ->
            if (i == indice) g.corregir(nuevaClase) else g
        })

    companion object {
        const val GRANOS_MINIMO = 97
        const val GRANOS_MAXIMO = 103
    }
}

/**
 * Se queda con la mejor caja de cada grupo de cajas superpuestas.
 *
 * Sin esto el conteo saldría inflado: el modelo propone varias cajas por grano
 * y la app estaría contando el mismo grano dos o tres veces, lo que desplaza
 * los porcentajes y puede cambiar el grado del lote.
 */
fun suprimirSolapes(
    cajas: List<GranoDetectado>,
    umbralSolape: Double = 0.45,
): List<GranoDetectado> {
    val elegidas = mutableListOf<GranoDetectado>()
    for (caja in cajas.sortedByDescending { it.confianza }) {
        if (elegidas.none { it.interseccionSobreUnion(caja) > umbralSolape }) {
            elegidas += caja
        }
    }
    return elegidas
}

/**
 * Traduce la salida cruda de YOLO a cajas normalizadas.
 *
 * YOLO entrega, para cada caja candidata, su centro y tamaño y la puntuación
 * de cada clase: `[4 + n_clases][n_cajas]`. Se descartan las que no llegan al
 * umbral y se convierte el centro a esquina superior izquierda, que es lo que
 * necesita el dibujo en pantalla.
 */
fun leerCajasYolo(
    salida: Array<DoubleArray>,
    clases: List<String>,
    umbralConfianza: Double = 0.35,
): List<GranoDetectado> {
    if (salida.size < 5) return emptyList()
    val nClases = salida.size - 4
    if (nClases <= 0) return emptyList()
    val total = salida[0].size

    val cajas = mutableListOf<GranoDetectado>()
    for (i in 0 until total) {
        var mejorClase = 0
        var mejorPuntaje = 0.0
        for (c in 0 until nClases) {
            val p = salida[4 + c][i]
            if (p > mejorPuntaje) {
                mejorPuntaje = p
                mejorClase = c
            }
        }
        if (mejorPuntaje < umbralConfianza) continue

        val cx = salida[0][i]
        val cy = salida[1][i]
        val w = salida[2][i]
        val h = salida[3][i]

        cajas += GranoDetectado(
            clase = clases.getOrElse(mejorClase) { "otro" },
            confianza = mejorPuntaje,
            x = (cx - w / 2).coerceIn(0.0, 1.0),
            y = (cy - h / 2).coerceIn(0.0, 1.0),
            ancho = w.coerceIn(0.0, 1.0),
            alto = h.coerceIn(0.0, 1.0),
        )
    }
    return cajas
}

/** Por qué un modelo no está disponible, para poder decírselo al usuario. */
enum class MotivoSinModelo {
    /** Todavía no se ha entrenado ni instalado (el caso normal al empezar). */
    NO_INSTALADO,

    /** El archivo existe pero no se pudo cargar. */
    ERROR_AL_CARGAR,
}

class ModeloNoDisponible(
    val tarea: String,
    val motivo: MotivoSinModelo,
    val detalle: String = "",
) : IllegalStateException(
    when (motivo) {
        MotivoSinModelo.NO_INSTALADO ->
            "Todavía no hay un modelo de $tarea instalado. Puedes registrar y " +
                "contar a mano mientras tanto."
        MotivoSinModelo.ERROR_AL_CARGAR ->
            "El modelo de $tarea no se pudo abrir. Usa el registro manual y " +
                "vuelve a instalarlo desde Ajustes."
    },
)
