package ec.cacaotrace.nucleo.norma

/**
 * Calificación de la prueba de corte según la NTE INEN 176.
 *
 * Convierte el conteo de granos por clase —venga del modelo M2 o del conteo
 * manual— en porcentajes y en un resultado de calidad (RF-PRC-06, RN-10).
 *
 * Es la MISMA lógica que `entrenamiento/calificar_corte.py` y lee el MISMO
 * archivo JSON. Los dos comparten los casos de prueba de
 * `entrenamiento/pruebas/casos_norma.json`: si una implementación se desvía de
 * la otra, los tests fallan. Esa es toda la idea — el resultado que ve el
 * usuario en el teléfono tiene que ser idéntico al que se valida en la
 * computadora contra tableros contados por un experto.
 */
class CalificadorCorte(val tabla: TablaNorma) {

    /**
     * Agrupa el conteo por indicador de la norma y lo pasa a porcentaje.
     *
     * Las clases mapeadas a `ignorar` no entran en el total: un grano que el
     * modelo no supo clasificar no debe diluir los porcentajes de los demás.
     */
    fun porcentajes(conteo: Map<String, Int>): PorcentajesCorte {
        if (conteo.isEmpty()) throw ErrorNorma("No hay granos contados para calificar.")

        val agrupado = mutableMapOf<String, Int>()
        val desconocidas = mutableListOf<String>()
        var ignorados = 0

        for ((clase, n) in conteo) {
            if (n < 0) throw ErrorNorma("La cantidad de \"$clase\" no puede ser negativa.")
            when (val grupo = tabla.clasesDetector[clase]) {
                null -> desconocidas += clase
                "ignorar" -> ignorados += n
                else -> agrupado[grupo] = (agrupado[grupo] ?: 0) + n
            }
        }

        val total = agrupado.values.sum()
        if (total == 0) {
            throw ErrorNorma(
                "No hay granos válidos para calificar. Revisa que las clases contadas " +
                    "existan en la tabla de la norma.",
            )
        }

        val valores = mutableMapOf<String, Double>()
        agrupado.forEach { (k, v) -> valores[k] = 100.0 * v / total }
        INDICADORES.forEach { valores.putIfAbsent(it, 0.0) }
        valores["fermentado_total"] =
            valores.getValue("fermentado_bueno") + valores.getValue("fermentado_ligero")

        return PorcentajesCorte(
            valores = valores,
            granosEvaluados = total,
            granosIgnorados = ignorados,
            clasesDesconocidas = desconocidas,
        )
    }

    /** Califica un conteo de granos con el perfil indicado. */
    fun calificar(
        conteo: Map<String, Int>,
        perfil: String = PERFIL_POR_DEFECTO,
    ): ResultadoCorte {
        val p = tabla.perfiles[perfil]
            ?: throw ErrorNorma(
                "El perfil \"$perfil\" no existe. " +
                    "Disponibles: ${tabla.perfiles.keys.joinToString(", ")}",
            )

        val pct = porcentajes(conteo)
        val avisos = avisosDe(pct)

        // Perfil por grados: gana el primer grado que cumpla.
        if (p.esPorGrados) {
            val fallasPorGrado = linkedMapOf<String, List<String>>()
            for (g in p.grados) {
                val fallas = fallasDe(pct, g.requisitos)
                if (fallas.isEmpty()) {
                    return ResultadoCorte(
                        perfil = perfil,
                        resultado = g.grado,
                        conforme = true,
                        porcentajes = pct,
                        avisos = avisos,
                    )
                }
                fallasPorGrado[g.grado] = fallas
            }
            return ResultadoCorte(
                perfil = perfil,
                resultado = p.resultadoSiNoCumple,
                conforme = false,
                porcentajes = pct,
                fallasPorGrado = fallasPorGrado,
                avisos = avisos,
            )
        }

        // Perfil simple: cumple o no cumple.
        val fallas = fallasDe(pct, p.requisitos)
        return ResultadoCorte(
            perfil = perfil,
            resultado = if (fallas.isEmpty()) p.resultadoSiCumple else p.resultadoSiNoCumple,
            conforme = fallas.isEmpty(),
            porcentajes = pct,
            fallas = fallas,
            avisos = avisos,
        )
    }

    /** Califica con todos los perfiles de la tabla, para mostrarlos juntos. */
    fun calificarTodos(conteo: Map<String, Int>): Map<String, ResultadoCorte> =
        tabla.perfiles.keys.associateWith { calificar(conteo, it) }

    private fun fallasDe(
        pct: PorcentajesCorte,
        requisitos: List<RequisitoNorma>,
    ): List<String> = requisitos.mapNotNull { r ->
        val medido = pct[r.indicador]
        if (r.seCumpleCon(medido)) null else r.explicarFalla(medido)
    }

    private fun avisosDe(pct: PorcentajesCorte): List<String> = buildList {
        val contados = pct.granosContados
        if (contados < GRANOS_MINIMO_AVISO || contados > GRANOS_MAXIMO_AVISO) {
            add(
                "Se contaron $contados granos; la norma usa $GRANOS_NORMA. " +
                    "Revisa el conteo antes de dar el resultado por bueno.",
            )
        }
        if (pct.granosIgnorados > 0) {
            add(
                "${pct.granosIgnorados} grano(s) quedaron sin clasificar y no entraron " +
                    "en los porcentajes.",
            )
        }
        if (pct.clasesDesconocidas.isNotEmpty()) {
            add(
                "Clases que no están en la tabla de la norma y se descartaron: " +
                    pct.clasesDesconocidas.distinct().sorted().joinToString(", "),
            )
        }
    }

    companion object {
        /** Perfil que se usa si el usuario no eligió otro. */
        const val PERFIL_POR_DEFECTO = "ccn51_referencia"

        /** Total de granos que manda la norma para una prueba de corte completa. */
        const val GRANOS_NORMA = 100

        /** Margen aceptado antes de avisar que el conteo no cuadra (RF-PRC-07). */
        const val GRANOS_MINIMO_AVISO = 97
        const val GRANOS_MAXIMO_AVISO = 103

        /** Indicadores que siempre aparecen en el resultado, aunque valgan 0 %. */
        val INDICADORES = listOf(
            "fermentado_bueno",
            "fermentado_ligero",
            "violeta",
            "pizarroso",
            "mohoso",
            "defectuoso",
        )
    }
}

/** Los porcentajes calculados sobre los granos válidos. */
data class PorcentajesCorte(
    /** Indicador de la norma -> porcentaje. Incluye `fermentado_total`. */
    val valores: Map<String, Double>,
    /** Granos que entraron en el cálculo. */
    val granosEvaluados: Int,
    /** Granos de clases marcadas como `ignorar` (por ejemplo 'otro'). */
    val granosIgnorados: Int,
    /** Clases del conteo que no están en la tabla y se descartaron. */
    val clasesDesconocidas: List<String>,
) {
    /** Todo lo que el usuario contó, válido o no. */
    val granosContados: Int get() = granosEvaluados + granosIgnorados

    operator fun get(indicador: String): Double = valores[indicador] ?: 0.0

    val fermentadoTotal: Double get() = this["fermentado_total"]
}

/** Resultado completo de calificar un tablero. */
data class ResultadoCorte(
    val perfil: String,
    /** Texto para mostrar: "Grado 1", "CCN-51 conforme", "Fuera de grado". */
    val resultado: String,
    val conforme: Boolean,
    val porcentajes: PorcentajesCorte,
    /** Requisitos que fallan, en perfiles simples. */
    val fallas: List<String> = emptyList(),
    /** Requisitos que fallan por cada grado, en perfiles por grados. */
    val fallasPorGrado: Map<String, List<String>> = emptyMap(),
    /** Advertencias que no invalidan el cálculo pero el usuario debe ver. */
    val avisos: List<String> = emptyList(),
) {
    /** Todas las fallas en una sola lista, para mostrarlas de corrido. */
    val todasLasFallas: List<String>
        get() = fallas + fallasPorGrado.flatMap { (grado, lista) -> lista.map { "$grado: $it" } }
}
