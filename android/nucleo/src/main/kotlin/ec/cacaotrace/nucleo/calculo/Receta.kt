package ec.cacaotrace.nucleo.calculo

import kotlin.math.floor

/**
 * Calculadora de receta de chocolate (RF-REF-01).
 *
 * El usuario entra los kg de nibs que tiene y el porcentaje de cacao que
 * quiere (90 %), y la app le dice cuánto azúcar, manteca y lecitina poner.
 */
class CalculadoraReceta {

    /**
     * Calcula la receta.
     *
     * @param porcentajeCacao objetivo (90 para un chocolate 90 %).
     * @param mantecaExtraPct manteca añadida sobre el total, para que el
     *   chocolate fluya mejor al moldear; en un 90 % suele hacer falta algo.
     */
    fun calcular(
        kgNibs: Double,
        porcentajeCacao: Double = 90.0,
        mantecaExtraPct: Double = 0.0,
        usarLecitina: Boolean = true,
    ): Receta {
        require(kgNibs > 0) { "Los kg de nibs deben ser mayores que cero" }
        require(porcentajeCacao > 0 && porcentajeCacao <= 100) {
            "El porcentaje de cacao debe estar entre 1 y 100"
        }
        require(mantecaExtraPct >= 0 && mantecaExtraPct < porcentajeCacao) {
            "La manteca añadida debe ser menor que el porcentaje de cacao"
        }

        // El total sale de que nibs + manteca = porcentajeCacao % del total.
        val fraccionNibs = (porcentajeCacao - mantecaExtraPct) / 100.0
        val total = kgNibs / fraccionNibs

        val manteca = total * mantecaExtraPct / 100.0
        val lecitina = if (usarLecitina) total * PROPORCION_LECITINA else 0.0
        val azucar = total - kgNibs - manteca - lecitina

        require(azucar >= -1e-9) {
            "Con esos valores no queda espacio para el azúcar. Baja el porcentaje " +
                "de cacao o la manteca añadida."
        }

        return Receta(
            kgNibs = kgNibs,
            kgAzucar = if (azucar < 0) 0.0 else azucar,
            kgMantecaAnadida = manteca,
            kgLecitina = lecitina,
            porcentajeCacao = porcentajeCacao,
        )
    }

    /** El camino inverso: cuántos nibs hacen falta para un total dado. */
    fun nibsNecesarios(
        kgTotalDeseado: Double,
        porcentajeCacao: Double = 90.0,
        mantecaExtraPct: Double = 0.0,
    ): Double = kgTotalDeseado * (porcentajeCacao - mantecaExtraPct) / 100.0

    companion object {
        /**
         * Proporción de lecitina sobre el total. Es un emulsionante: con muy
         * poco basta, y de más deja sabor.
         */
        const val PROPORCION_LECITINA = 0.005
    }
}

/** Los ingredientes de una tanda, en kg. */
data class Receta(
    val kgNibs: Double,
    val kgAzucar: Double,
    /** Manteca de cacao añadida aparte de la que ya traen los nibs. */
    val kgMantecaAnadida: Double,
    val kgLecitina: Double,
    val porcentajeCacao: Double,
) {
    /** Peso total de la tanda. */
    val kgTotal: Double get() = kgNibs + kgAzucar + kgMantecaAnadida + kgLecitina

    /**
     * Porcentaje de cacao que realmente sale, contando la manteca añadida como
     * parte del cacao (que lo es).
     */
    val porcentajeCacaoReal: Double
        get() = if (kgTotal <= 0) 0.0 else 100.0 * (kgNibs + kgMantecaAnadida) / kgTotal

    /** Cuántas barras salen, con la merma de moldeado. */
    fun barrasEstimadas(pesoBarraG: Double = 50.0, mermaPct: Double = 5.0): Int {
        if (pesoBarraG <= 0) return 0
        val kgUtiles = kgTotal * (1 - mermaPct / 100)
        return floor(kgUtiles * 1000 / pesoBarraG).toInt()
    }
}
