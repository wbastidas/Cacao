package ec.cacaotrace.nucleo.etiqueta

import ec.cacaotrace.nucleo.calculo.Receta

/**
 * Tabla nutricional calculada a partir de la receta (RF-EMP-02).
 *
 * La gracia es que la app ya sabe exactamente cuántos kilos de nibs, azúcar,
 * manteca y lecitina entraron en la tanda, así que no hay que pedirle al
 * usuario que teclee nada: la tabla sale sola de lo que ya registró.
 *
 * ## Hasta dónde llega esto, y hasta dónde no
 *
 * Es una **estimación a partir de la composición típica** de cada ingrediente.
 * Sirve para saber qué va a salir, preparar el trámite y decidir si conviene
 * ajustar la receta. **No sustituye a un análisis bromatológico**, que es lo
 * que exige ARCSA para la etiqueta definitiva de un producto a la venta.
 *
 * La diferencia real está sobre todo en la grasa del nib, que varía con la
 * genética y con el año: un CCN-51 puede ir del 48 % al 56 %. Por eso la
 * composición es un dato editable y no constantes en el código (RNF-12): con
 * el resultado del laboratorio en la mano, se corrige y todo lo demás cuadra.
 */
data class TablaNutricional(
    /** Todo lo que sigue va por 100 g de producto terminado. */
    val energiaKcal: Double,
    val grasasG: Double,
    val carbohidratosG: Double,
    val azucaresG: Double,
    val fibraG: Double,
    val proteinaG: Double,
    val sodioMg: Double,
) {
    /** Los kJ que la etiqueta pide junto a las kcal. */
    val energiaKj: Double get() = energiaKcal * 4.184

    /** Lo mismo, pero para una barra del peso que sea. */
    fun porPorcion(gramos: Double): TablaNutricional {
        val f = gramos / 100.0
        return TablaNutricional(
            energiaKcal = energiaKcal * f,
            grasasG = grasasG * f,
            carbohidratosG = carbohidratosG * f,
            azucaresG = azucaresG * f,
            fibraG = fibraG * f,
            proteinaG = proteinaG * f,
            sodioMg = sodioMg * f,
        )
    }
}

/**
 * Composición de un ingrediente, por 100 g de ese ingrediente.
 *
 * Los valores por defecto son de referencia y están pensados para CCN-51
 * ecuatoriano. Se sustituyen por los del laboratorio en cuanto se tengan.
 */
data class Ingrediente(
    val nombre: String,
    val energiaKcal: Double,
    val grasasG: Double,
    val carbohidratosG: Double,
    val azucaresG: Double,
    val fibraG: Double,
    val proteinaG: Double,
    val sodioMg: Double,
)

/** Calcula la tabla nutricional de una receta. */
class CalculadoraNutricional(
    private val ingredientes: Map<String, Ingrediente> = COMPOSICION_POR_DEFECTO,
) {
    /**
     * Devuelve la tabla por 100 g de producto terminado.
     *
     * Se normaliza por el peso total de la receta, no por la suma de los
     * ingredientes declarados: si algún día se añade uno sin composición
     * conocida, es mejor que el resultado salga bajo y se note, a que salga
     * inflado por repartir el total entre menos masa de la que hay.
     */
    fun calcular(receta: Receta): TablaNutricional {
        val total = receta.kgTotal
        require(total > 0) { "La receta no tiene peso; no se puede calcular la tabla" }

        val partes = listOf(
            "nibs" to receta.kgNibs,
            "azucar" to receta.kgAzucar,
            "manteca" to receta.kgMantecaAnadida,
            "lecitina" to receta.kgLecitina,
        )

        var energia = 0.0; var grasas = 0.0; var carbos = 0.0
        var azucares = 0.0; var fibra = 0.0; var proteina = 0.0; var sodio = 0.0

        for ((clave, kg) in partes) {
            if (kg <= 0) continue
            val i = ingredientes[clave] ?: continue
            // Fracción de la mezcla que aporta este ingrediente, llevada a 100 g.
            val f = (kg / total) * 1.0
            energia += i.energiaKcal * f
            grasas += i.grasasG * f
            carbos += i.carbohidratosG * f
            azucares += i.azucaresG * f
            fibra += i.fibraG * f
            proteina += i.proteinaG * f
            sodio += i.sodioMg * f
        }

        return TablaNutricional(
            energiaKcal = energia,
            grasasG = grasas,
            carbohidratosG = carbos,
            azucaresG = azucares,
            fibraG = fibra,
            proteinaG = proteina,
            sodioMg = sodio,
        )
    }

    companion object {
        /**
         * Composición típica de cada ingrediente, por 100 g.
         *
         * Valores de referencia. El que más conviene reemplazar por uno medido
         * es la grasa del nib: es el que más varía y el que decide si el
         * semáforo de grasas sale ALTO, que en un 90 % va a salir igual.
         */
        val COMPOSICION_POR_DEFECTO: Map<String, Ingrediente> = mapOf(
            "nibs" to Ingrediente(
                nombre = "Nibs de cacao",
                energiaKcal = 600.0,
                grasasG = 52.0,
                carbohidratosG = 34.0,
                azucaresG = 1.0,
                fibraG = 30.0,
                proteinaG = 13.5,
                sodioMg = 20.0,
            ),
            "azucar" to Ingrediente(
                nombre = "Azúcar",
                energiaKcal = 400.0,
                grasasG = 0.0,
                carbohidratosG = 100.0,
                azucaresG = 100.0,
                fibraG = 0.0,
                proteinaG = 0.0,
                sodioMg = 0.0,
            ),
            "manteca" to Ingrediente(
                nombre = "Manteca de cacao",
                energiaKcal = 900.0,
                grasasG = 100.0,
                carbohidratosG = 0.0,
                azucaresG = 0.0,
                fibraG = 0.0,
                proteinaG = 0.0,
                sodioMg = 0.0,
            ),
            "lecitina" to Ingrediente(
                nombre = "Lecitina de girasol",
                energiaKcal = 900.0,
                grasasG = 100.0,
                carbohidratosG = 0.0,
                azucaresG = 0.0,
                fibraG = 0.0,
                proteinaG = 0.0,
                sodioMg = 0.0,
            ),
        )
    }
}
