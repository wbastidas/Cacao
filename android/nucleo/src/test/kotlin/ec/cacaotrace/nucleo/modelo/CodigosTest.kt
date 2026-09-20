package ec.cacaotrace.nucleo.modelo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CodigosTest {

    @Test
    fun `el primero del anio es L-AAAA-001`() {
        assertEquals("L-2026-001", Codigos.siguiente("L", 2026, emptyList()))
    }

    @Test
    fun `sigue la numeracion del anio en curso`() {
        assertEquals(
            "L-2026-003",
            Codigos.siguiente("L", 2026, listOf("L-2026-001", "L-2026-002", "L-2025-009")),
        )
    }

    @Test
    fun `cada anio empieza de nuevo en 001`() {
        assertEquals("L-2026-001", Codigos.siguiente("L", 2026, listOf("L-2025-040")))
    }

    @Test
    fun `no se confunde con otro prefijo`() {
        assertEquals("P-2026-001", Codigos.siguiente("P", 2026, listOf("L-2026-007")))
    }

    @Test
    fun `ignora codigos con formato raro sin reventar`() {
        val existentes = listOf("L-2026-002", "no es un codigo", "L-2026-", "L-2026-abc", "")
        assertEquals("L-2026-003", Codigos.siguiente("L", 2026, existentes))
    }

    @Test
    fun `no se deja confundir por el codigo de un saco`() {
        // L-2026-001-S03 no debe leerse como el lote 1 y adelantar el contador.
        assertEquals("L-2026-002", Codigos.siguiente("L", 2026, listOf("L-2026-001", "L-2026-001-S03")))
    }

    @Test
    fun `pasa de 009 a 010 conservando tres digitos`() {
        assertEquals("L-2026-010", Codigos.siguiente("L", 2026, listOf("L-2026-009")))
    }

    @Test
    fun `pasa de 999 a 1000 sin perder el correlativo`() {
        assertEquals("L-2026-1000", Codigos.siguiente("L", 2026, listOf("L-2026-999")))
    }

    @Test
    fun `del codigo de un saco se saca el del lote`() {
        assertEquals("L-2026-001", Codigos.loteDeSaco("L-2026-001-S03"))
        assertEquals("L-2026-001", Codigos.loteDeSaco("L-2026-001"))
    }
}
