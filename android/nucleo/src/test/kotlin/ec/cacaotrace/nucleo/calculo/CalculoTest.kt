package ec.cacaotrace.nucleo.calculo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs

/** Tests del balance de masa (RF-LOT-08) y de la receta (RF-REF-01). */
class CalculoTest {

    @Nested
    inner class Balance {
        private val balance = BalanceMasa()

        @Test
        fun `el lote tipico de la ERS cae dentro de lo esperado`() {
            // §2.5: ~100 mazorcas -> 16-18 kg baba -> ~6 kg seco -> ~5 kg chocolate
            val pasos = balance.calcular(
                mazorcas = 100, kgBaba = 17.0, kgSeco = 6.1, kgNibs = 5.2,
            )
            pasos.filter { it.registrado }.forEach { p ->
                assertTrue(
                    abs(p.desvioPct!!) < 25,
                    "${p.etapa} se desvía ${p.desvioPct}",
                )
            }
        }

        @Test
        fun `marca los pasos que aun no se registraron`() {
            val pasos = balance.calcular(mazorcas = 100, kgBaba = 17.0)
            assertTrue(pasos[0].registrado)
            assertFalse(pasos[1].registrado)
            assertNull(pasos[1].desvioPct)
            assertNull(pasos[1].rendimiento)
        }

        @Test
        fun `un peso muy bajo produce un desvio negativo grande`() {
            val pasos = balance.calcular(mazorcas = 100, kgBaba = 5.0)
            assertTrue(pasos[0].desvioPct!! < -50)
        }

        @Test
        fun `cada paso parte de lo real del anterior y no de lo esperado`() {
            // Con la mitad de baba, lo esperado del secado debe bajar a la
            // mitad: si no, un error temprano ensucia todo el balance.
            val normal = balance.calcular(mazorcas = 100, kgBaba = 17.0)
            val pocaBaba = balance.calcular(mazorcas = 100, kgBaba = 8.5)
            assertEquals(normal[1].salidaEsperada / 2, pocaBaba[1].salidaEsperada, 0.01)
        }

        @Test
        fun `proyecta el chocolate desde solo el numero de mazorcas`() {
            val kg = balance.proyectarChocolate(mazorcas = 100)
            assertTrue(kg > 4 && kg < 7, "salió $kg")
        }

        @Test
        fun `la proyeccion usa los datos reales cuando existen`() {
            assertEquals(
                5.0,
                balance.proyectarChocolate(mazorcas = 100, kgNibs = 4.5),
                0.01,
            )
        }

        @Test
        fun `el rendimiento de un paso se calcula bien`() {
            val pasos = balance.calcular(mazorcas = 100, kgBaba = 17.0, kgSeco = 6.12)
            assertEquals(0.36, pasos[1].rendimiento!!, 0.005)
        }

        @Test
        fun `no divide por cero sin mazorcas`() {
            val pasos = balance.calcular(mazorcas = 0)
            assertNull(pasos[0].rendimiento)
            assertNull(pasos[0].desvioPct)
        }
    }

    @Nested
    inner class RecetaDelChocolate {
        private val calc = CalculadoraReceta()

        @Test
        fun `un chocolate 90 por ciento da las proporciones correctas`() {
            val r = calc.calcular(kgNibs = 4.5)
            assertEquals(5.0, r.kgTotal, 0.001)
            assertEquals(90.0, r.porcentajeCacaoReal, 0.5)
            assertTrue(r.kgAzucar > 0)
            assertEquals(5.0, r.kgAzucar + r.kgNibs + r.kgLecitina, 0.001)
        }

        @Test
        fun `la manteca anadida cuenta como cacao`() {
            val r = calc.calcular(kgNibs = 4.0, mantecaExtraPct = 5.0)
            assertTrue(r.kgMantecaAnadida > 0)
            assertEquals(90.0, r.porcentajeCacaoReal, 0.5)
        }

        @Test
        fun `se puede hacer sin lecitina`() {
            val r = calc.calcular(kgNibs = 4.5, usarLecitina = false)
            assertEquals(0.0, r.kgLecitina)
            assertEquals(90.0, r.porcentajeCacaoReal, 0.5)
        }

        @Test
        fun `un 70 por ciento lleva mucha mas azucar que un 90`() {
            val r90 = calc.calcular(kgNibs = 4.5)
            val r70 = calc.calcular(kgNibs = 4.5, porcentajeCacao = 70.0)
            assertTrue(r70.kgAzucar > r90.kgAzucar * 2)
        }

        @Test
        fun `estima las barras que salen`() {
            val r = calc.calcular(kgNibs = 4.5)
            // 5 kg menos 5 % de merma, en barras de 50 g
            assertEquals(95, r.barrasEstimadas())
            assertEquals(47, r.barrasEstimadas(pesoBarraG = 100.0))
        }

        @Test
        fun `el camino inverso es coherente`() {
            val nibs = calc.nibsNecesarios(kgTotalDeseado = 5.0)
            assertEquals(5.0, calc.calcular(kgNibs = nibs).kgTotal, 0.001)
        }

        @Test
        fun `rechaza entradas imposibles`() {
            assertThrows<IllegalArgumentException> { calc.calcular(kgNibs = 0.0) }
            assertThrows<IllegalArgumentException> { calc.calcular(kgNibs = -1.0) }
            assertThrows<IllegalArgumentException> {
                calc.calcular(kgNibs = 1.0, porcentajeCacao = 0.0)
            }
            assertThrows<IllegalArgumentException> {
                calc.calcular(kgNibs = 1.0, porcentajeCacao = 150.0)
            }
            assertThrows<IllegalArgumentException> {
                calc.calcular(kgNibs = 1.0, mantecaExtraPct = 95.0)
            }
            assertThrows<IllegalArgumentException> {
                calc.calcular(kgNibs = 1.0, mantecaExtraPct = -5.0)
            }
        }

        @Test
        fun `al 100 por ciento de cacao no queda azucar`() {
            val r = calc.calcular(
                kgNibs = 5.0, porcentajeCacao = 100.0, usarLecitina = false,
            )
            assertEquals(0.0, r.kgAzucar, 1e-9)
            assertEquals(5.0, r.kgTotal, 1e-9)
        }
    }
}
