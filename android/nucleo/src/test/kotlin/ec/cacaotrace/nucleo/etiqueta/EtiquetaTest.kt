package ec.cacaotrace.nucleo.etiqueta

import ec.cacaotrace.nucleo.calculo.CalculadoraReceta
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EtiquetaTest {

    private val tabla = TablaSemaforo.porDefecto()
    private val calculadora = CalculadoraNutricional()

    @Nested
    @DisplayName("Semáforo")
    inner class Semaforo {

        @Test
        fun `los extremos caen del lado que dice el reglamento`() {
            val azucares = tabla.corteDe(Componente.AZUCARES)
            // El reglamento dice BAJO si es menor O IGUAL que 5, así que 5
            // exactos es BAJO. Equivocarse aquí cambia el color impreso.
            assertEquals(Nivel.BAJO, azucares.nivel(5.0))
            assertEquals(Nivel.MEDIO, azucares.nivel(5.01))
            assertEquals(Nivel.MEDIO, azucares.nivel(14.99))
            // Y ALTO si es mayor O IGUAL que 15.
            assertEquals(Nivel.ALTO, azucares.nivel(15.0))
        }

        @Test
        fun `cero siempre es bajo`() {
            Componente.entries.forEach { c ->
                assertEquals(Nivel.BAJO, tabla.corteDe(c).nivel(0.0))
            }
        }

        @Test
        fun `un componente que la tabla no define es un error, no un silencio`() {
            val incompleta = TablaSemaforo(
                listOf(CorteSemaforo(Componente.GRASAS, 3.0, 20.0)),
            )
            // Si faltara un corte, devolver BAJO por descuido imprimiría una
            // barra verde sin fundamento. Mejor reventar.
            assertThrows<IllegalArgumentException> {
                incompleta.corteDe(Componente.AZUCARES)
            }
        }
    }

    @Nested
    @DisplayName("Tabla nutricional")
    inner class Nutricional {

        /** La receta de la casa: 90 % de cacao, con lecitina y sin manteca añadida. */
        private val noventa = CalculadoraReceta().calcular(
            kgNibs = 90.0,
            porcentajeCacao = 90.0,
            usarLecitina = true,
        )

        @Test
        fun `un 90 por ciento sale con los valores que se esperan de una barra asi`() {
            val n = calculadora.calcular(noventa)

            // Un chocolate 90 % ronda las 600 kcal por 100 g.
            assertTrue(n.energiaKcal in 550.0..650.0, "energía = ${n.energiaKcal}")
            // Casi la mitad es grasa, que viene del propio nib.
            assertTrue(n.grasasG in 40.0..55.0, "grasas = ${n.grasasG}")
            // El azúcar es el 10 % que no es cacao, poco más.
            assertTrue(n.azucaresG in 8.0..13.0, "azúcares = ${n.azucaresG}")
            // Sin sal añadida, el sodio es el del cacao: muy poco.
            assertTrue(n.sodioMg < 50.0, "sodio = ${n.sodioMg}")
        }

        @Test
        fun `el semaforo de un 90 por ciento sale alto en grasa, medio en azucar y bajo en sal`() {
            val niveles = tabla.evaluar(calculadora.calcular(noventa))

            // Este es el resultado que va impreso en la barra, así que conviene
            // que esté fijado: si un cambio en la composición lo mueve, que se
            // entere el test y no el productor al recibir 500 etiquetas.
            assertEquals(Nivel.ALTO, niveles[Componente.GRASAS])
            assertEquals(Nivel.MEDIO, niveles[Componente.AZUCARES])
            assertEquals(Nivel.BAJO, niveles[Componente.SAL])
        }

        @Test
        fun `bajar el porcentaje de cacao sube el azucar hasta ponerlo en rojo`() {
            val cincuenta = CalculadoraReceta().calcular(
                kgNibs = 50.0,
                porcentajeCacao = 50.0,
                usarLecitina = true,
            )
            val niveles = tabla.evaluar(calculadora.calcular(cincuenta))
            assertEquals(Nivel.ALTO, niveles[Componente.AZUCARES])
        }

        @Test
        fun `la porcion es proporcional al peso de la barra`() {
            val por100 = calculadora.calcular(noventa)
            val barra = por100.porPorcion(50.0)
            assertEquals(por100.energiaKcal / 2, barra.energiaKcal, 0.001)
            assertEquals(por100.grasasG / 2, barra.grasasG, 0.001)
        }

        @Test
        fun `los kilojulios acompañan a las kilocalorias`() {
            val n = calculadora.calcular(noventa)
            assertEquals(n.energiaKcal * 4.184, n.energiaKj, 0.001)
        }

        @Test
        fun `una receta sin peso no se calcula a medias`() {
            val vacia = noventa.copy(
                kgNibs = 0.0, kgAzucar = 0.0,
                kgMantecaAnadida = 0.0, kgLecitina = 0.0,
            )
            assertThrows<IllegalArgumentException> { calculadora.calcular(vacia) }
        }

        @Test
        fun `la manteca anadida sube la grasa y baja el azucar`() {
            val sinManteca = calculadora.calcular(noventa)
            val conManteca = calculadora.calcular(
                CalculadoraReceta().calcular(
                    kgNibs = 90.0,
                    porcentajeCacao = 90.0,
                    mantecaExtraPct = 5.0,
                    usarLecitina = true,
                ),
            )
            assertTrue(
                conManteca.grasasG > sinManteca.grasasG,
                "con manteca ${conManteca.grasasG} debería superar ${sinManteca.grasasG}",
            )
            assertTrue(
                conManteca.azucaresG < sinManteca.azucaresG,
                "con manteca ${conManteca.azucaresG} debería bajar de ${sinManteca.azucaresG}",
            )
        }

        @Test
        fun `un ingrediente sin composicion conocida no infla el resto`() {
            // Se calcula con una tabla a la que le falta el azúcar: el
            // resultado tiene que salir BAJO en azúcares, no repartir el total
            // entre los ingredientes que sí conoce y dar un número inventado.
            val incompleta = CalculadoraNutricional(
                CalculadoraNutricional.COMPOSICION_POR_DEFECTO - "azucar",
            )
            val n = incompleta.calcular(noventa)
            assertTrue(n.azucaresG < 2.0, "azúcares = ${n.azucaresG}")
            assertTrue(n.grasasG < calculadora.calcular(noventa).grasasG + 0.001)
        }
    }
}
