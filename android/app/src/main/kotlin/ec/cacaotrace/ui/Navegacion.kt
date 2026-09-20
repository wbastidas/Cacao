package ec.cacaotrace.ui

/**
 * Rutas de navegación.
 *
 * Se declaran como objetos y no como cadenas sueltas para que un cambio de
 * nombre lo detecte el compilador y no el usuario.
 */
object Rutas {
    const val INICIO = "inicio"
    const val LOTES = "lotes"
    const val PRODUCCION = "produccion"
    const val PANEL = "panel"
    const val AJUSTES = "ajustes"

    const val DETALLE_LOTE = "lote/{loteId}"
    const val RECEPCION = "lote/{loteId}/recepcion"
    const val APERTURA = "lote/{loteId}/apertura"
    const val FERMENTACION = "lote/{loteId}/fermentacion"
    const val SECADO = "lote/{loteId}/secado"
    const val PRUEBA_CORTE = "lote/{loteId}/prueba-corte"
    const val ALMACEN = "almacen?loteId={loteId}"
    const val REPORTE = "lote/{loteId}/reporte"
    const val ALERTAS = "alertas?loteId={loteId}"
    const val CORRECCIONES = "correcciones"
    const val DETALLE_TANDA = "tanda/{tandaId}"
    const val INVENTARIO = "inventario"
    const val BPM = "bpm"
    const val LABORATORIO = "laboratorio"
    const val GUIAS = "guias"
    const val ESCANER = "escaner"
    const val TABLERO_PDF = "tablero-pdf"

    fun detalleLote(loteId: String) = "lote/$loteId"
    fun recepcion(loteId: String) = "lote/$loteId/recepcion"
    fun apertura(loteId: String) = "lote/$loteId/apertura"
    fun fermentacion(loteId: String) = "lote/$loteId/fermentacion"
    fun secado(loteId: String) = "lote/$loteId/secado"
    fun pruebaCorte(loteId: String) = "lote/$loteId/prueba-corte"
    fun reporte(loteId: String) = "lote/$loteId/reporte"
    fun almacen(loteId: String? = null) = "almacen?loteId=${loteId.orEmpty()}"
    fun alertas(loteId: String? = null) = "alertas?loteId=${loteId.orEmpty()}"
    fun detalleTanda(tandaId: String) = "tanda/$tandaId"

    /** Destino de una tarea del día, para poder tocarla y llegar al sitio. */
    fun deTarea(destino: String?, loteId: String?): String? = when (destino) {
        "apertura" -> loteId?.let { apertura(it) }
        "fermentacion" -> loteId?.let { fermentacion(it) }
        "secado" -> loteId?.let { secado(it) }
        "almacen" -> almacen(loteId)
        "bpm" -> BPM
        else -> loteId?.let { detalleLote(it) }
    }
}
