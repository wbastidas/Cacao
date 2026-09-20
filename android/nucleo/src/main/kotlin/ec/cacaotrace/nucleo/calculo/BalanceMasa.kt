package ec.cacaotrace.nucleo.calculo

/**
 * Balance de masa del lote (RF-LOT-08).
 *
 * Compara lo que se obtuvo en cada etapa con lo que era esperable, para
 * detectar errores al anotar pesos y para saber cuánto chocolate va a salir.
 *
 * Referencia del §2.5 de la ERS para un lote típico:
 * ~100 mazorcas -> 16–18 kg de baba -> ~6 kg de grano seco -> ~5 kg de chocolate.
 */
class BalanceMasa(private val esperados: RendimientosEsperados = RendimientosEsperados()) {

    /**
     * Construye el balance con lo registrado hasta el momento.
     *
     * Cada parámetro puede ser nulo si esa etapa aún no ocurrió: el balance se
     * muestra igual, con los pasos futuros marcados como "sin registrar".
     */
    fun calcular(
        mazorcas: Int,
        kgBaba: Double? = null,
        kgSeco: Double? = null,
        kgNibs: Double? = null,
        kgChocolate: Double? = null,
    ): List<PasoBalance> = buildList {
        add(
            PasoBalance(
                etapa = "Mazorcas a baba",
                entrada = mazorcas.toDouble(),
                unidadEntrada = "mazorcas",
                salidaReal = kgBaba,
                salidaEsperada = mazorcas * esperados.kgBabaPorMazorca,
                unidadSalida = "kg",
            ),
        )

        // Cada paso se compara con lo REAL del paso anterior cuando existe, no
        // con lo esperado: si no, un error temprano contamina todo el balance.
        val baseSeco = kgBaba ?: (mazorcas * esperados.kgBabaPorMazorca)
        add(
            PasoBalance(
                etapa = "Baba a grano seco",
                entrada = baseSeco,
                unidadEntrada = "kg",
                salidaReal = kgSeco,
                salidaEsperada = baseSeco * esperados.fraccionBabaASeco,
                unidadSalida = "kg",
            ),
        )

        val baseNibs = kgSeco ?: (baseSeco * esperados.fraccionBabaASeco)
        add(
            PasoBalance(
                etapa = "Grano seco a nibs",
                entrada = baseNibs,
                unidadEntrada = "kg",
                salidaReal = kgNibs,
                salidaEsperada = baseNibs * esperados.fraccionSecoANibs,
                unidadSalida = "kg",
            ),
        )

        val baseChocolate = kgNibs ?: (baseNibs * esperados.fraccionSecoANibs)
        add(
            PasoBalance(
                etapa = "Nibs a chocolate",
                entrada = baseChocolate,
                unidadEntrada = "kg",
                salidaReal = kgChocolate,
                salidaEsperada = baseChocolate * esperados.fraccionNibsAChocolate,
                unidadSalida = "kg",
            ),
        )
    }

    /** Cuánto chocolate cabe esperar del lote con lo que se sabe hasta ahora. */
    fun proyectarChocolate(
        mazorcas: Int,
        kgBaba: Double? = null,
        kgSeco: Double? = null,
        kgNibs: Double? = null,
        porcentajeCacao: Double = 90.0,
    ): Double {
        val baba = kgBaba ?: (mazorcas * esperados.kgBabaPorMazorca)
        val seco = kgSeco ?: (baba * esperados.fraccionBabaASeco)
        val nibs = kgNibs ?: (seco * esperados.fraccionSecoANibs)
        // En un chocolate al 90 %, los nibs son el 90 % del producto final.
        return if (porcentajeCacao <= 0) 0.0 else nibs * 100.0 / porcentajeCacao
    }
}

/**
 * Rendimientos esperados de cada paso, en fracción (0–1) o kg por unidad.
 *
 * Son valores por defecto para CCN-51 en la costa ecuatoriana. Se pueden
 * ajustar cuando el usuario tenga historial propio: después de 5 o 6 lotes, su
 * promedio real vale más que cualquier referencia.
 */
data class RendimientosEsperados(
    /** kg de baba que da una mazorca. */
    val kgBabaPorMazorca: Double = 0.17,
    /** Cuánto queda al secar: la baba pierde ~64 % de su peso en agua y pulpa. */
    val fraccionBabaASeco: Double = 0.36,
    /** Cuánto queda al tostar y descascarillar: se va la cascarilla y humedad. */
    val fraccionSecoANibs: Double = 0.85,
    /**
     * En un chocolate 90 %, los nibs son casi todo el producto; el azúcar y la
     * manteca añaden peso, así que el factor real se calcula con la receta.
     */
    val fraccionNibsAChocolate: Double = 1.0,
)

/** Un paso del balance: lo esperado frente a lo real. */
data class PasoBalance(
    val etapa: String,
    val entrada: Double,
    val unidadEntrada: String,
    val salidaReal: Double?,
    val salidaEsperada: Double,
    val unidadSalida: String,
) {
    val registrado: Boolean get() = salidaReal != null

    /** Cuántos puntos porcentuales se desvía lo real de lo esperado. */
    val desvioPct: Double?
        get() {
            val real = salidaReal ?: return null
            if (salidaEsperada <= 0) return null
            return 100.0 * (real - salidaEsperada) / salidaEsperada
        }

    /** Rendimiento real de este paso, en fracción. */
    val rendimiento: Double?
        get() {
            val real = salidaReal ?: return null
            return if (entrada <= 0) null else real / entrada
        }
}
