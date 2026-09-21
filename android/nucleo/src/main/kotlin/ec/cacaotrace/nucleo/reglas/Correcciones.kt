package ec.cacaotrace.nucleo.reglas

/**
 * Biblioteca de correcciones (tabla 5.2 de la ERS, RF-COR-01).
 *
 * Cada alerta lleva el código de la corrección que le toca, para que el usuario
 * no solo sepa QUÉ pasó sino QUÉ HACER (RF-ALE-03). Está incluida en la app y
 * funciona sin internet (RF-GUI-01).
 */
data class Correccion(
    /** C-01 … C-09. */
    val codigo: String,
    val problema: String,
    /** La causa, explicada en palabras simples. */
    val porQuePasa: String,
    /** Qué hacer ahora mismo, en orden. */
    val pasos: List<String>,
    /** Cómo evitar que vuelva a pasar en el siguiente lote. */
    val paraLaProximaVez: String,
)

object Correcciones {
    val todas: List<Correccion> = listOf(
        Correccion(
            codigo = "C-01",
            problema = "Fermentación fría",
            porQuePasa = "La fermentación se calienta sola por la actividad de levaduras y " +
                "bacterias. Si hay poca masa, o se pierde el calor por las paredes, " +
                "nunca llega a los 45–50 °C que necesita el grano.",
            pasos = listOf(
                "Añade cáscara de mazorca troceada para aumentar la masa y el azúcar.",
                "Cubre la masa con hojas de plátano y encima sacos de yute.",
                "Cierra la tapa del fermentador.",
                "Refuerza el aislamiento por fuera: cartón, espuma o más sacos.",
                "Vuelve a medir la temperatura en 6 horas y anótala.",
            ),
            paraLaProximaVez = "Junta más baba antes de empezar: por debajo de la masa " +
                "mínima configurada la fermentación casi siempre se enfría.",
        ),
        Correccion(
            codigo = "C-02",
            problema = "Muchos granos violetas",
            porQuePasa = "El violeta es grano que fermentó a medias. Le faltó tiempo, o los " +
                "volteos no fueron parejos y una parte de la masa quedó fría.",
            pasos = listOf(
                "Si el lote sigue en fermentación, alárgala 12–24 horas más y voltea.",
                "Si ya está seco, no tiene arreglo: anótalo y ajusta el próximo lote.",
                "Revisa el registro de volteos: busca huecos de más de 24 horas.",
            ),
            paraLaProximaVez = "Alarga la fermentación un día y cuida que los volteos sean " +
                "completos, moviendo también el grano de las esquinas.",
        ),
        Correccion(
            codigo = "C-03",
            problema = "Moho durante el secado",
            porQuePasa = "El grano se secó demasiado despacio o en capa muy gruesa, y quedó " +
                "humedad atrapada. El moho da un sabor que no se quita después.",
            pasos = listOf(
                "Separa ahora mismo los granos con moho visible y deséchalos.",
                "Extiende el resto en capa más delgada (3–4 cm).",
                "Remueve cada 1–2 horas en vez de cada 3.",
                "Ventila la marquesina: abre los laterales.",
                "Si llueve varios días seguidos, usa secador o ventilador.",
            ),
            paraLaProximaVez = "Empieza el secado con capa delgada los primeros dos días, " +
                "que es cuando el grano suelta más agua.",
        ),
        Correccion(
            codigo = "C-04",
            problema = "Muchos granos pizarrosos",
            porQuePasa = "El pizarroso (gris y compacto) es grano que no fermentó nada. O la " +
                "fermentación nunca arrancó, o ese grano estaba seco antes de empezar.",
            pasos = listOf(
                "Revisa la masa que usaste: por debajo del mínimo no arranca.",
                "Revisa el aislamiento del fermentador.",
                "Comprueba que las mazorcas no estuvieran ya secas o muy verdes.",
                "Mide la temperatura a las 24 y 48 horas del próximo lote.",
            ),
            paraLaProximaVez = "Junta lotes más grandes o usa un fermentador más pequeño y " +
                "mejor aislado. Una caja demasiado grande para poca baba enfría la masa.",
        ),
        Correccion(
            codigo = "C-05",
            problema = "Olor pútrido o a amoniaco",
            porQuePasa = "La fermentación se pasó. Las bacterias equivocadas tomaron el " +
                "control y están produciendo compuestos que arruinan el sabor.",
            pasos = listOf(
                "Pasa el lote a secado de inmediato, sin esperar más horas.",
                "Extiende en capa delgada y con buena ventilación.",
                "Anota la duración total: te servirá para acortar el próximo lote.",
                "Haz una prueba de corte al secar para ver cuánto se salvó.",
            ),
            paraLaProximaVez = "Acorta la fermentación. Con CCN-51 y clima caliente, muchas " +
                "veces bastan 4–5 días en vez de 6–7.",
        ),
        Correccion(
            codigo = "C-06",
            problema = "Chocolate arenoso",
            porQuePasa = "Las partículas de cacao y azúcar siguen siendo más grandes de lo " +
                "que la lengua puede ignorar (unas 20 micras).",
            pasos = listOf(
                "Sigue refinando: 6–12 horas más en el melanger.",
                "Prueba cada 2 horas frotando un poco entre los dedos.",
                "Comprueba que el melanger no esté sobrecargado.",
            ),
            paraLaProximaVez = "Anota cuántas horas necesitó este lote y usa ese tiempo como " +
                "punto de partida la próxima vez.",
        ),
        Correccion(
            codigo = "C-07",
            problema = "Chocolate muy ácido",
            porQuePasa = "Quedaron ácidos de la fermentación (sobre todo ácido acético) que " +
                "no se evaporaron durante el conchado.",
            pasos = listOf(
                "Alarga el conchado con la tapa abierta para que los ácidos escapen.",
                "Sube un poco la temperatura del conchado, sin pasar de 60 °C.",
                "Revisa si el tostado fue demasiado suave.",
            ),
            paraLaProximaVez = "Seca más despacio los primeros días: un secado muy rápido " +
                "encierra los ácidos dentro del grano.",
        ),
        Correccion(
            codigo = "C-08",
            problema = "Fat bloom (velo grisáceo)",
            porQuePasa = "La manteca de cacao cristalizó mal o migró a la superficie. Pasa " +
                "cuando el atemperado falla o el chocolate sufre cambios de temperatura " +
                "después de moldeado.",
            pasos = listOf(
                "Vuelve a fundir las barras y repite el atemperado con cuidado.",
                "Baja la temperatura del cuarto por debajo del umbral configurado.",
                "Respeta las tres temperaturas del asistente de atemperado.",
                "No metas el chocolate al refrigerador para que cuaje rápido.",
            ),
            paraLaProximaVez = "Atempera y moldea a primera hora de la mañana, cuando el " +
                "cuarto está más fresco.",
        ),
        Correccion(
            codigo = "C-09",
            problema = "Sugar bloom (superficie áspera y blanquecina)",
            porQuePasa = "Se condensó humedad sobre el chocolate, disolvió el azúcar de la " +
                "superficie y al evaporarse dejó cristales.",
            pasos = listOf(
                "No se puede revertir: esas barras van a consumo propio.",
                "Guarda el chocolate entre 16 y 18 °C, con humedad baja.",
                "Nunca lo metas al refrigerador sin empaque hermético.",
                "Si viene de frío, déjalo templar cerrado antes de abrirlo.",
            ),
            paraLaProximaVez = "Controla la humedad del cuarto de moldeado y empaca en " +
                "cuanto las barras estén frías.",
        ),
    )

    val porCodigo: Map<String, Correccion> = todas.associateBy { it.codigo }

    fun buscar(codigo: String?): Correccion? = codigo?.let { porCodigo[it] }
}
