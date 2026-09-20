package ec.cacaotrace.nucleo.modelo

/** Estado del lote de grano, en el orden en que ocurre (RF-LOT-02). */
enum class EstadoLote(
    val etiqueta: String,
    /** Qué toca hacer en esta etapa, en una frase. */
    val queSigue: String,
    /** Las etapas que no se pueden saltar sin dar un motivo (RF-LOT-03). */
    val esObligatoria: Boolean = false,
) {
    RECEPCION("Recepción", "Registra las mazorcas y programa la apertura", true),
    REPOSO("Reposo", "Espera los días de reposo y luego abre las mazorcas"),
    FERMENTACION("Fermentación", "Mide temperatura y voltea cada día", true),
    SECADO("Secado", "Remueve el grano y mide la humedad", true),
    ALMACENADO("Almacenado", "Inspecciona los sacos cada semana"),
    TOSTADO("Tostado", "Registra temperatura, tiempo y merma"),
    DESCASCARILLADO("Descascarillado", "Anota los kg de nibs y de cascarilla"),
    REFINADO("Refinado", "Controla las horas y prueba la textura"),
    ATEMPERADO("Atemperado", "Sigue el asistente de atemperado"),
    EMPACADO("Empacado", "Registra las barras y las fechas"),
    VENDIDO("Vendido o consumido", "Lote cerrado"),
    DESCARTADO("Descartado", "Lote descartado");

    /** La siguiente etapa del camino normal, o null si es la última. */
    val siguiente: EstadoLote?
        get() {
            val i = CAMINO.indexOf(this)
            return if (i < 0 || i == CAMINO.lastIndex) null else CAMINO[i + 1]
        }

    /** True si el lote ya no está en proceso. */
    val estaCerrado: Boolean get() = this == VENDIDO || this == DESCARTADO

    companion object {
        /** El recorrido normal, sin DESCARTADO, que es una salida lateral. */
        val CAMINO = listOf(
            RECEPCION, REPOSO, FERMENTACION, SECADO, ALMACENADO, TOSTADO,
            DESCASCARILLADO, REFINADO, ATEMPERADO, EMPACADO, VENDIDO,
        )

        /**
         * Las etapas obligatorias que quedarían saltadas al ir de [desde] a
         * [hasta]. Si la lista no está vacía, hay que pedir un motivo.
         */
        fun etapasSaltadas(desde: EstadoLote, hasta: EstadoLote): List<EstadoLote> {
            if (hasta.ordinal <= desde.ordinal) return emptyList()
            return entries.filter {
                it.ordinal > desde.ordinal && it.ordinal < hasta.ordinal && it.esObligatoria
            }
        }
    }
}

/** Olores que se pueden registrar durante la fermentación (RF-FER-03). */
enum class OlorFermentacion(
    val etiqueta: String,
    /** Qué significa ese olor en ese momento del proceso. */
    val significado: String,
    /** Olores que indican que algo va mal (RN-06). */
    val esMalaSenal: Boolean = false,
) {
    ALCOHOLICO(
        "Alcohólico",
        "Normal en los primeros dos días: las levaduras están trabajando.",
    ),
    AVINAGRADO(
        "Avinagrado",
        "Normal del día 2 al 4: ahora trabajan las bacterias del vinagre.",
    ),
    PUTRIDO("Pútrido", "Mala señal: la fermentación se pasó.", true),
    AMONIACO("A amoniaco", "Mala señal: sobrefermentación avanzada.", true),
    MOHO("A moho", "Mala señal: hay humedad mal manejada.", true),
}

/** Método de secado (RF-SEC-01). */
enum class MetodoSecado(val etiqueta: String) {
    MARQUESINA("Marquesina"),
    SOL("Al sol"),
    SECADOR("Secador"),
}

/** Método de atemperado (RF-ATE-01). */
enum class MetodoAtemperado(val etiqueta: String) {
    SIEMBRA("Siembra"),
    MARMOL("Mármol"),
}

/** En qué punto de la subida a la nube está un registro (RF-SYN-01). */
enum class EstadoSync(val etiqueta: String) {
    /** Guardado en el teléfono, aún no subido. */
    PENDIENTE("Pendiente"),
    /** Se está subiendo ahora mismo. */
    ENVIANDO("Subiendo"),
    /** Confirmado en la nube. */
    SINCRONIZADO("Sincronizado"),
    /** Falló la subida; se reintentará. */
    ERROR("Con error"),
}
