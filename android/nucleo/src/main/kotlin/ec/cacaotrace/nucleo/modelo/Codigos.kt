package ec.cacaotrace.nucleo.modelo

/**
 * Códigos visibles de lote (L-AAAA-NNN) y de producción (P-AAAA-NNN).
 *
 * Se numeran por año y de forma correlativa dentro del año, que es como el
 * usuario los nombra al hablar: "el lote 3 del año pasado".
 *
 * Vive en :nucleo y no en el repositorio porque es una regla de nombrado del
 * negocio, no de almacenamiento, y así se puede probar sin base de datos.
 */
object Codigos {

    const val PREFIJO_LOTE = "L"
    const val PREFIJO_PRODUCCION = "P"

    /**
     * Devuelve el siguiente código libre para [prefijo] y [anio].
     *
     * Ignora los códigos de otros años y de otros prefijos: si el año pasado
     * se llegó al L-2025-040, este año se empieza igualmente en L-2026-001.
     */
    fun siguiente(prefijo: String, anio: Int, existentes: List<String>): String {
        val patron = Regex("^${Regex.escape(prefijo)}-$anio-(\\d+)$")
        val mayor = existentes
            .mapNotNull { patron.find(it.trim())?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return "$prefijo-$anio-${(mayor + 1).toString().padStart(3, '0')}"
    }

    /** El código de lote que corresponde a un saco `L-2026-001-S03`. */
    fun loteDeSaco(codigoSaco: String): String = codigoSaco.substringBefore("-S")
}
