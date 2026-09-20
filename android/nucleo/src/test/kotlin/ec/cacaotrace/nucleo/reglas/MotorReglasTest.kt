package ec.cacaotrace.nucleo.reglas

import ec.cacaotrace.nucleo.modelo.EstadoLote
import ec.cacaotrace.nucleo.modelo.OlorFermentacion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Tests del motor de reglas (tabla 5.1 de la ERS).
 *
 * Incluye el caso de aceptación del §10: "simular temperatura de 38 °C a las
 * 72 h debe producir la alerta RN-04 con la corrección C-01".
 */
class MotorReglasTest {

    private val motor = MotorReglas(Umbrales.porDefecto())

    private fun List<Alerta>.regla(codigo: String): Alerta? = firstOrNull { it.regla == codigo }

    @Nested
    inner class Reposo {
        @Test
        fun `no avisa dentro del rango normal`() {
            assertTrue(motor.evaluarReposo(ContextoReposo(4)).isEmpty())
        }

        @Test
        fun `avisa el dia en que toca abrir`() {
            val a = motor.evaluarReposo(ContextoReposo(6))
            assertEquals(Severidad.AVISO, a.regla("RN-01")?.severidad)
        }

        @Test
        fun `se pone urgente si se pasa del limite`() {
            val a = motor.evaluarReposo(ContextoReposo(9))
            assertEquals(Severidad.URGENTE, a.regla("RN-01")?.severidad)
        }

        @Test
        fun `no dice nada si ya se abrieron las mazorcas`() {
            assertTrue(motor.evaluarReposo(ContextoReposo(20, abierto = true)).isEmpty())
        }
    }

    @Nested
    inner class Apertura {
        @Test
        fun `avisa con poca baba y sugiere C-01`() {
            val a = motor.evaluarApertura(ContextoApertura(80, 13.6))
            val rn02 = a.regla("RN-02")
            assertNotNull(rn02)
            assertEquals("C-01", rn02!!.correccion)
            assertNotNull(Correcciones.buscar(rn02.correccion))
        }

        @Test
        fun `la cascara anadida cuenta para la masa minima`() {
            val sinCascara = motor.evaluarApertura(ContextoApertura(100, 17.0))
            val conCascara = motor.evaluarApertura(ContextoApertura(100, 17.0, 5.0))
            assertNotNull(sinCascara.regla("RN-02"))
            assertNull(conCascara.regla("RN-02"))
        }

        @Test
        fun `no avisa con masa suficiente`() {
            val a = motor.evaluarApertura(ContextoApertura(130, 22.1))
            assertNull(a.regla("RN-02"))
        }

        @Test
        fun `RN-17 avisa si el rendimiento por mazorca no cuadra`() {
            // 100 mazorcas y 40 kg de baba: el doble de lo esperado.
            val a = motor.evaluarApertura(ContextoApertura(100, 40.0))
            assertNotNull(a.regla("RN-17"))
        }
    }

    @Nested
    inner class Fermentacion {
        @Test
        fun `RN-03 avisa tras demasiadas horas sin voltear`() {
            val a = motor.evaluarFermentacion(
                ContextoFermentacion(horasDesdeInicio = 48.0, horasDesdeUltimoVolteo = 30.0),
            )
            assertEquals(Severidad.URGENTE, a.regla("RN-03")?.severidad)
        }

        @Test
        fun `RN-03 no molesta en las primeras horas`() {
            val a = motor.evaluarFermentacion(
                ContextoFermentacion(horasDesdeInicio = 10.0, horasDesdeUltimoVolteo = 10.0),
            )
            assertNull(a.regla("RN-03"))
        }

        @Test
        fun `RN-04 a 38 grados y 72 horas da alerta con correccion C-01`() {
            // Este es el criterio de aceptación del §10 de la ERS.
            val a = motor.evaluarFermentacion(
                ContextoFermentacion(horasDesdeInicio = 72.0, temperaturaC = 38.0),
            )
            val rn04 = a.regla("RN-04")
            assertNotNull(rn04, "debía dispararse RN-04")
            assertEquals("C-01", rn04!!.correccion)
            assertEquals(Severidad.URGENTE, rn04.severidad)
            assertEquals(38.0, rn04.valorMedido)
            assertEquals(40.0, rn04.valorEsperado)
            assertTrue(Correcciones.buscar("C-01")!!.pasos.isNotEmpty())
        }

        @Test
        fun `RN-04 no se dispara antes de las 60 horas`() {
            val a = motor.evaluarFermentacion(
                ContextoFermentacion(horasDesdeInicio = 30.0, temperaturaC = 38.0),
            )
            assertNull(a.regla("RN-04"))
        }

        @Test
        fun `RN-05 avisa por encima de 52 grados`() {
            val a = motor.evaluarFermentacion(
                ContextoFermentacion(horasDesdeInicio = 40.0, temperaturaC = 54.0),
            )
            assertNotNull(a.regla("RN-05"))
        }

        @Test
        fun `RN-06 dispara con olor putrido y sugiere C-05`() {
            val a = motor.evaluarFermentacion(
                ContextoFermentacion(horasDesdeInicio = 100.0, olor = OlorFermentacion.PUTRIDO),
            )
            assertEquals("C-05", a.regla("RN-06")?.correccion)
        }

        @Test
        fun `RN-06 no dispara con olor avinagrado que es normal`() {
            val a = motor.evaluarFermentacion(
                ContextoFermentacion(
                    horasDesdeInicio = 60.0,
                    olor = OlorFermentacion.AVINAGRADO,
                ),
            )
            assertNull(a.regla("RN-06"))
        }

        @Test
        fun `RN-06 con moho sugiere C-03 y no C-05`() {
            val a = motor.evaluarFermentacion(
                ContextoFermentacion(horasDesdeInicio = 60.0, olor = OlorFermentacion.MOHO),
            )
            assertEquals("C-03", a.regla("RN-06")?.correccion)
        }

        @Test
        fun `RN-07 avisa pasados 7 dias`() {
            val a = motor.evaluarFermentacion(ContextoFermentacion(horasDesdeInicio = 24.0 * 8))
            assertEquals("C-05", a.regla("RN-07")?.correccion)
        }

        @Test
        fun `una lectura normal no produce ninguna alerta`() {
            val a = motor.evaluarFermentacion(
                ContextoFermentacion(
                    horasDesdeInicio = 72.0,
                    temperaturaC = 47.0,
                    horasDesdeUltimoVolteo = 12.0,
                    olor = OlorFermentacion.AVINAGRADO,
                ),
            )
            assertTrue(a.isEmpty())
        }
    }

    @Nested
    inner class Secado {
        @Test
        fun `RN-08 bloquea al cerrar con humedad alta`() {
            val a = motor.evaluarSecado(
                ContextoSecado(humedadGranoPct = 9.0, cerrandoEtapa = true),
            )
            assertEquals(Severidad.BLOQUEANTE, a.regla("RN-08")?.severidad)
            assertTrue(a.regla("RN-08")!!.bloquea)
        }

        @Test
        fun `RN-08 no bloquea a media etapa`() {
            val a = motor.evaluarSecado(ContextoSecado(humedadGranoPct = 9.0))
            assertNull(a.regla("RN-08"))
        }

        @Test
        fun `RN-08 deja pasar con humedad correcta`() {
            val a = motor.evaluarSecado(
                ContextoSecado(humedadGranoPct = 6.5, cerrandoEtapa = true),
            )
            assertNull(a.regla("RN-08"))
        }

        @Test
        fun `RN-09 avisa por moho visible`() {
            val a = motor.evaluarSecado(ContextoSecado(mohoVisible = true))
            assertEquals("C-03", a.regla("RN-09")?.correccion)
        }
    }

    @Nested
    inner class PruebaDeCorte {
        @Test
        fun `no dice nada si el lote cumple`() {
            val a = motor.evaluarPruebaCorte(
                conforme = true,
                resultado = "Grado 1",
                fallas = emptyList(),
                pctVioleta = 10.0, pctPizarroso = 3.0, pctMohoso = 0.0,
            )
            assertTrue(a.isEmpty())
        }

        @Test
        fun `elige C-04 cuando el defecto dominante es pizarroso`() {
            val a = motor.evaluarPruebaCorte(
                conforme = false,
                resultado = "Fuera de grado",
                fallas = listOf("pizarroso = 25.0% (requiere ≤ 18%)"),
                pctVioleta = 10.0, pctPizarroso = 25.0, pctMohoso = 1.0,
            )
            assertEquals("C-04", a.regla("RN-10")?.correccion)
        }

        @Test
        fun `elige C-02 cuando el defecto dominante es violeta`() {
            val a = motor.evaluarPruebaCorte(
                conforme = false,
                resultado = "Fuera de grado",
                fallas = listOf("violeta = 30.0% (requiere ≤ 25%)"),
                pctVioleta = 30.0, pctPizarroso = 5.0, pctMohoso = 1.0,
            )
            assertEquals("C-02", a.regla("RN-10")?.correccion)
        }

        @Test
        fun `elige C-03 cuando el defecto dominante es moho`() {
            val a = motor.evaluarPruebaCorte(
                conforme = false,
                resultado = "Fuera de grado",
                fallas = listOf("mohoso = 8.0% (requiere ≤ 4%)"),
                pctVioleta = 5.0, pctPizarroso = 3.0, pctMohoso = 8.0,
            )
            assertEquals("C-03", a.regla("RN-10")?.correccion)
        }
    }

    @Nested
    inner class Almacen {
        @Test
        fun `avisa con humedad relativa alta`() {
            val a = motor.evaluarAlmacen(ContextoAlmacen(humedadRelativaPct = 80.0))
            assertNotNull(a.regla("RN-11"))
        }

        @Test
        fun `no avisa con humedad correcta`() {
            val a = motor.evaluarAlmacen(ContextoAlmacen(humedadRelativaPct = 60.0))
            assertTrue(a.isEmpty())
        }

        @Test
        fun `moho y plagas producen alertas distintas`() {
            val a = motor.evaluarAlmacen(ContextoAlmacen(mohoVisible = true, plagas = true))
            assertEquals(2, a.size)
        }
    }

    @Nested
    inner class Tostado {
        @Test
        fun `avisa con merma excesiva`() {
            val a = motor.evaluarTostado(ContextoTostado(kgEntrada = 10.0, kgSalida = 8.2))
            assertNotNull(a.regla("RN-12"))
        }

        @Test
        fun `no avisa con merma normal`() {
            val a = motor.evaluarTostado(ContextoTostado(kgEntrada = 10.0, kgSalida = 9.3))
            assertTrue(a.isEmpty())
        }

        @Test
        fun `avisa si la cascarilla se aleja de lo esperado`() {
            val a = motor.evaluarTostado(
                ContextoTostado(
                    kgEntrada = 10.0, kgSalida = 9.3, kgNibs = 7.0, kgCascarilla = 2.3,
                ),
            )
            assertTrue(a.any { it.quePaso.contains("cascarilla") })
        }

        @Test
        fun `el calculo de merma es correcto`() {
            val c = ContextoTostado(kgEntrada = 10.0, kgSalida = 9.0)
            assertEquals(10.0, c.mermaPct!!, 1e-9)
        }

        @Test
        fun `no divide por cero con entrada nula`() {
            val c = ContextoTostado(kgEntrada = 0.0, kgSalida = 0.0)
            assertNull(c.mermaPct)
            assertTrue(motor.evaluarTostado(c).isEmpty())
        }
    }

    @Nested
    inner class Atemperado {
        @Test
        fun `RN-13 avisa con cuarto caliente y sugiere C-08`() {
            val a = motor.evaluarAtemperado(ContextoAtemperado(tempCuartoC = 26.0))
            assertEquals("C-08", a.regla("RN-13")?.correccion)
        }

        @Test
        fun `RN-13 con humedad alta sugiere C-09`() {
            val a = motor.evaluarAtemperado(ContextoAtemperado(humedadCuartoPct = 75.0))
            assertEquals("C-09", a.regla("RN-13")?.correccion)
        }

        @Test
        fun `RN-14 avisa fuera del rango de trabajo`() {
            assertNotNull(
                motor.evaluarAtemperado(ContextoAtemperado(tempTrabajoC = 29.0)).regla("RN-14"),
            )
            assertNotNull(
                motor.evaluarAtemperado(ContextoAtemperado(tempTrabajoC = 34.0)).regla("RN-14"),
            )
        }

        @Test
        fun `condiciones correctas no producen alertas`() {
            val a = motor.evaluarAtemperado(
                ContextoAtemperado(
                    tempCuartoC = 20.0, humedadCuartoPct = 50.0, tempTrabajoC = 31.5,
                ),
            )
            assertTrue(a.isEmpty())
        }
    }

    @Nested
    inner class Laboratorio {
        @Test
        fun `RN-15 bloquea por encima del limite`() {
            val a = motor.evaluarLaboratorio(ContextoLaboratorio("cadmio", 1.2, "mg/kg"))
            assertTrue(a.regla("RN-15")!!.bloquea)
        }

        @Test
        fun `no bloquea dentro del limite`() {
            val a = motor.evaluarLaboratorio(ContextoLaboratorio("cadmio", 0.4, "mg/kg"))
            assertTrue(a.isEmpty())
        }

        @Test
        fun `ignora otros analisis`() {
            val a = motor.evaluarLaboratorio(ContextoLaboratorio("humedad", 9.0, "%"))
            assertTrue(a.isEmpty())
        }
    }

    @Nested
    inner class UmbralesEditables {
        @Test
        fun `cambiar un umbral cambia el comportamiento`() {
            val estricto = MotorReglas(Umbrales.porDefecto().con("ferm_temp_minima_c", 45.0))
            val ctx = ContextoFermentacion(horasDesdeInicio = 72.0, temperaturaC = 42.0)
            assertNull(motor.evaluarFermentacion(ctx).regla("RN-04"))
            assertNotNull(estricto.evaluarFermentacion(ctx).regla("RN-04"))
        }

        @Test
        fun `rechaza un valor fuera del rango razonable`() {
            assertThrows<IllegalArgumentException> {
                Umbrales.porDefecto().con("ferm_temp_maxima_c", 200.0)
            }
        }

        @Test
        fun `rechaza un umbral que no existe`() {
            assertThrows<IllegalArgumentException> {
                Umbrales.porDefecto().con("inventado", 1.0)
            }
        }

        @Test
        fun `todos los umbrales del catalogo tienen explicacion y rango`() {
            CatalogoUmbrales.todos.forEach { u ->
                assertTrue(u.etiqueta.isNotEmpty(), u.clave)
                assertTrue(u.explicacion.isNotEmpty(), u.clave)
                assertTrue(
                    u.esValido(u.porDefecto),
                    "${u.clave}: el valor por defecto está fuera de su rango",
                )
            }
        }
    }

    @Nested
    inner class IntegridadDeLasAlertas {
        @Test
        fun `toda correccion referida por una alerta existe`() {
            val todas = buildList {
                addAll(motor.evaluarApertura(ContextoApertura(80, 10.0)))
                addAll(
                    motor.evaluarFermentacion(
                        ContextoFermentacion(
                            horasDesdeInicio = 200.0,
                            temperaturaC = 30.0,
                            olor = OlorFermentacion.AMONIACO,
                        ),
                    ),
                )
                addAll(
                    motor.evaluarSecado(
                        ContextoSecado(
                            humedadGranoPct = 12.0, mohoVisible = true, cerrandoEtapa = true,
                        ),
                    ),
                )
                addAll(
                    motor.evaluarAlmacen(
                        ContextoAlmacen(
                            humedadRelativaPct = 90.0, mohoVisible = true, plagas = true,
                        ),
                    ),
                )
                addAll(
                    motor.evaluarAtemperado(
                        ContextoAtemperado(
                            tempCuartoC = 30.0, humedadCuartoPct = 80.0, tempTrabajoC = 40.0,
                        ),
                    ),
                )
            }
            assertTrue(todas.isNotEmpty())
            todas.forEach { a ->
                assertTrue(a.quePaso.isNotEmpty(), a.regla)
                assertTrue(a.porQueImporta.isNotEmpty(), a.regla)
                assertTrue(a.queHacer.isNotEmpty(), a.regla)
                a.correccion?.let {
                    assertNotNull(
                        Correcciones.buscar(it),
                        "${a.regla} apunta a $it, que no existe",
                    )
                }
            }
        }

        @Test
        fun `la biblioteca de correcciones esta completa`() {
            assertEquals(9, Correcciones.todas.size)
            Correcciones.todas.forEach { c ->
                assertTrue(c.pasos.isNotEmpty(), c.codigo)
                assertTrue(c.porQuePasa.isNotEmpty(), c.codigo)
                assertTrue(c.paraLaProximaVez.isNotEmpty(), c.codigo)
            }
        }
    }

    @Nested
    inner class EtapasDelLote {
        @Test
        fun `saltar de recepcion a almacenado deja etapas obligatorias sin hacer`() {
            val saltadas = EstadoLote.etapasSaltadas(
                EstadoLote.RECEPCION,
                EstadoLote.ALMACENADO,
            )
            assertEquals(listOf(EstadoLote.FERMENTACION, EstadoLote.SECADO), saltadas)
        }

        @Test
        fun `avanzar de a una etapa nunca salta nada`() {
            EstadoLote.CAMINO.zipWithNext { a, b ->
                assertTrue(
                    EstadoLote.etapasSaltadas(a, b).isEmpty(),
                    "de ${a.etiqueta} a ${b.etiqueta}",
                )
            }
        }

        @Test
        fun `ir hacia atras no cuenta como salto`() {
            assertTrue(
                EstadoLote.etapasSaltadas(EstadoLote.SECADO, EstadoLote.RECEPCION).isEmpty(),
            )
        }

        @Test
        fun `el camino termina en vendido`() {
            assertNull(EstadoLote.VENDIDO.siguiente)
            assertEquals(EstadoLote.REPOSO, EstadoLote.RECEPCION.siguiente)
            assertTrue(EstadoLote.DESCARTADO.estaCerrado)
            assertFalse(EstadoLote.FERMENTACION.estaCerrado)
        }
    }
}
