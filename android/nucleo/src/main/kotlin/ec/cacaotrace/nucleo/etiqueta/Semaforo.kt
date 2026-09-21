package ec.cacaotrace.nucleo.etiqueta

/**
 * Semáforo nutricional del RTE INEN 022 (RF-EMP-02).
 *
 * Es el rótulo obligatorio en Ecuador para alimentos procesados: tres barras
 * —grasas, azúcares y sal— cada una en rojo, amarillo o verde según cuánto
 * lleve el producto por cada 100 g.
 *
 * ## Aviso importante
 *
 * Los valores de esta tabla son de **referencia** y hay que contrastarlos con
 * el texto oficial vigente del RTE INEN 022 antes de imprimir una etiqueta que
 * se vaya a vender. Igual que con la NTE INEN 176, la app no sustituye a la
 * norma ni a un laboratorio: lo que calcula aquí sirve para saber qué esperar
 * y para preparar el trámite, no para dar fe.
 *
 * Por eso la tabla es un dato editable y no constantes en el código (RNF-12):
 * si el reglamento cambia un umbral, se corrige sin publicar una versión nueva
 * de la app.
 */
enum class Nivel(val etiqueta: String, val color: String) {
    BAJO("BAJO", "verde"),
    MEDIO("MEDIO", "amarillo"),
    ALTO("ALTO", "rojo"),
}

/** Los tres componentes que el semáforo obliga a declarar. */
enum class Componente(val etiqueta: String, val unidad: String) {
    GRASAS("Grasas totales", "g"),
    AZUCARES("Azúcares", "g"),
    SAL("Sal", "mg de sodio"),
}

/**
 * Los cortes de cada componente.
 *
 * `hastaBajo` y `desdeAlto` se interpretan como los define el reglamento:
 * BAJO si el valor es **menor o igual** que `hastaBajo`, ALTO si es **mayor o
 * igual** que `desdeAlto`, y MEDIO en el hueco entre los dos. Los extremos
 * importan: un producto con exactamente 5 g de azúcar es BAJO, no MEDIO.
 */
data class CorteSemaforo(
    val componente: Componente,
    val hastaBajo: Double,
    val desdeAlto: Double,
) {
    fun nivel(valorPor100g: Double): Nivel = when {
        valorPor100g <= hastaBajo -> Nivel.BAJO
        valorPor100g >= desdeAlto -> Nivel.ALTO
        else -> Nivel.MEDIO
    }
}

/**
 * La tabla del semáforo para alimentos sólidos, por 100 g.
 *
 * Solo se incluye la columna de sólidos: esta app hace barras de chocolate, y
 * arrastrar la tabla de líquidos sería código que nunca se ejecuta y que
 * alguien tendría que mantener igualmente.
 */
data class TablaSemaforo(val cortes: List<CorteSemaforo>) {

    fun corteDe(componente: Componente): CorteSemaforo =
        cortes.firstOrNull { it.componente == componente }
            ?: throw IllegalArgumentException(
                "La tabla del semáforo no define el componente ${componente.etiqueta}"
            )

    /** Clasifica una tabla nutricional completa. */
    fun evaluar(nutricion: TablaNutricional): Map<Componente, Nivel> = mapOf(
        Componente.GRASAS to corteDe(Componente.GRASAS).nivel(nutricion.grasasG),
        Componente.AZUCARES to corteDe(Componente.AZUCARES).nivel(nutricion.azucaresG),
        Componente.SAL to corteDe(Componente.SAL).nivel(nutricion.sodioMg),
    )

    companion object {
        /**
         * Valores de referencia del RTE INEN 022 para sólidos, por 100 g.
         *
         * Contrástalos con el texto oficial vigente antes de imprimir.
         */
        fun porDefecto(): TablaSemaforo = TablaSemaforo(
            listOf(
                CorteSemaforo(Componente.GRASAS, hastaBajo = 3.0, desdeAlto = 20.0),
                CorteSemaforo(Componente.AZUCARES, hastaBajo = 5.0, desdeAlto = 15.0),
                CorteSemaforo(Componente.SAL, hastaBajo = 120.0, desdeAlto = 600.0),
            )
        )
    }
}
