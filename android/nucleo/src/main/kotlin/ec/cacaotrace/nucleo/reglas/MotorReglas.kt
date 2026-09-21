package ec.cacaotrace.nucleo.reglas

import ec.cacaotrace.nucleo.modelo.OlorFermentacion
import ec.cacaotrace.nucleo.norma.formatear
import kotlin.math.abs

/**
 * Motor de reglas de negocio (RF-ALE-01, tabla 5.1 de la ERS).
 *
 * Corre DENTRO del teléfono, sin internet y sin funciones en la nube: así
 * funciona en la finca sin señal y se puede operar con el plan gratuito.
 *
 * Es código puro: recibe datos, devuelve alertas. No toca la base ni la red, y
 * por eso se puede probar entero en milisegundos, sin emulador.
 */
class MotorReglas(val umbrales: Umbrales) {

    // ------------------------------------------------------------ reposo

    /** RN-01: días de reposo fuera del rango configurado. */
    fun evaluarReposo(c: ContextoReposo): List<Alerta> {
        if (c.abierto) return emptyList()
        val alerta = umbrales.entero("reposo_dias_alerta")
        val maximo = umbrales.entero("reposo_dias_max")

        return when {
            c.diasDesdeLlegada > alerta -> listOf(
                Alerta(
                    regla = "RN-01",
                    severidad = Severidad.URGENTE,
                    quePaso = "Las mazorcas llevan ${c.diasDesdeLlegada} días sin abrirse",
                    porQueImporta = "Pasados $maximo días la mazorca empieza a germinar o " +
                        "a pudrirse por dentro, y eso se lleva parte del lote.",
                    queHacer = "Abre las mazorcas hoy mismo.",
                    valorMedido = c.diasDesdeLlegada.toDouble(),
                    valorEsperado = maximo.toDouble(),
                ),
            )

            c.diasDesdeLlegada >= maximo -> listOf(
                Alerta(
                    regla = "RN-01",
                    severidad = Severidad.AVISO,
                    quePaso = "Hoy toca abrir las mazorcas",
                    porQueImporta = "Llevan ${c.diasDesdeLlegada} días de reposo, que es " +
                        "el máximo que configuraste.",
                    queHacer = "Abre las mazorcas y registra los kg de baba.",
                    valorMedido = c.diasDesdeLlegada.toDouble(),
                    valorEsperado = maximo.toDouble(),
                ),
            )

            else -> emptyList()
        }
    }

    // ------------------------------------------------------------ apertura

    /**
     * RN-02: poca baba para que la fermentación arranque.
     * Y RN-17 si el rendimiento por mazorca se aleja de lo esperado.
     */
    fun evaluarApertura(c: ContextoApertura): List<Alerta> = buildList {
        val masaMinima = umbrales["baba_masa_minima_kg"]
        val masaTotal = c.kgBaba + c.kgCascaraAnadida

        if (masaTotal < masaMinima) {
            val faltan = masaMinima - masaTotal
            add(
                Alerta(
                    regla = "RN-02",
                    severidad = Severidad.URGENTE,
                    quePaso = "Hay poca masa para fermentar (${formatear(masaTotal, 1)} kg)",
                    porQueImporta = "Por debajo de ${formatear(masaMinima, 0)} kg el montón " +
                        "no conserva el calor y la fermentación se queda fría, que es la " +
                        "causa más común de granos pizarrosos y violetas.",
                    queHacer = "Añade unos ${formatear(faltan, 1)} kg de cáscara de mazorca " +
                        "troceada y refuerza el aislamiento.",
                    correccion = "C-01",
                    valorMedido = masaTotal,
                    valorEsperado = masaMinima,
                ),
            )
        }

        if (c.mazorcasAbiertas > 0) {
            val esperadoPorMazorca = umbrales["baba_por_mazorca_kg"]
            val real = c.kgBaba / c.mazorcasAbiertas
            val desvio = umbrales["rendimiento_desvio_pct"]
            val diferencia = 100.0 * abs(real - esperadoPorMazorca) / esperadoPorMazorca
            if (diferencia > desvio) {
                add(
                    Alerta(
                        regla = "RN-17",
                        severidad = Severidad.AVISO,
                        quePaso = "El rendimiento por mazorca no cuadra " +
                            "(${formatear(real, 2)} kg)",
                        porQueImporta = "Lo normal en CCN-51 son unos " +
                            "${formatear(esperadoPorMazorca, 2)} kg por mazorca. Una " +
                            "diferencia así casi siempre es un error al anotar el peso o " +
                            "el número de mazorcas.",
                        queHacer = "Revisa el peso de la baba y el conteo de mazorcas.",
                        valorMedido = real,
                        valorEsperado = esperadoPorMazorca,
                    ),
                )
            }
        }
    }

    // ------------------------------------------------------------ fermentación

    /** RN-03 a RN-07: volteos, temperatura, olor y duración. */
    fun evaluarFermentacion(c: ContextoFermentacion): List<Alerta> = buildList {
        // RN-03: sin volteo registrado en demasiadas horas.
        val horasVolteo = umbrales["volteo_horas_alerta"]
        val desdeVolteo = c.horasDesdeUltimoVolteo
        if (desdeVolteo != null &&
            desdeVolteo > horasVolteo &&
            c.horasDesdeInicio > umbrales["volteo_horas"]
        ) {
            add(
                Alerta(
                    regla = "RN-03",
                    severidad = Severidad.URGENTE,
                    quePaso = "Llevas ${formatear(desdeVolteo, 0)} horas sin voltear",
                    porQueImporta = "Sin voltear, el grano de afuera queda frío y el de " +
                        "adentro se recalienta. El lote fermenta disparejo.",
                    queHacer = "Voltea la masa ahora y registra el volteo.",
                    valorMedido = desdeVolteo,
                    valorEsperado = horasVolteo,
                ),
            )
        }

        val temp = c.temperaturaC
        if (temp != null) {
            // RN-04: fermentación fría pasado el arranque.
            val tempMinima = umbrales["ferm_temp_minima_c"]
            val desdeHoras = umbrales["ferm_temp_minima_desde_h"]
            if (c.horasDesdeInicio >= desdeHoras && temp < tempMinima) {
                add(
                    Alerta(
                        regla = "RN-04",
                        severidad = Severidad.URGENTE,
                        quePaso = "La fermentación está fría (${formatear(temp, 1)} °C)",
                        porQueImporta = "A las ${formatear(c.horasDesdeInicio, 0)} horas la " +
                            "masa debería pasar de ${formatear(tempMinima, 0)} °C. Fría, el " +
                            "grano no desarrolla sabor y sale pizarroso o violeta.",
                        queHacer = "Añade cáscara, tapa bien y refuerza el aislamiento.",
                        correccion = "C-01",
                        valorMedido = temp,
                        valorEsperado = tempMinima,
                    ),
                )
            }

            // RN-05: demasiado caliente.
            val tempMaxima = umbrales["ferm_temp_maxima_c"]
            if (temp > tempMaxima) {
                add(
                    Alerta(
                        regla = "RN-05",
                        severidad = Severidad.URGENTE,
                        quePaso = "La masa está muy caliente (${formatear(temp, 1)} °C)",
                        porQueImporta = "Por encima de ${formatear(tempMaxima, 0)} °C el " +
                            "grano se cocina y aparecen sabores a quemado que no se quitan " +
                            "después.",
                        queHacer = "Voltea y destapa un rato para que baje la temperatura.",
                        valorMedido = temp,
                        valorEsperado = tempMaxima,
                    ),
                )
            }
        }

        // RN-06: olor de sobrefermentación.
        val olor = c.olor
        if (olor != null && olor.esMalaSenal) {
            add(
                Alerta(
                    regla = "RN-06",
                    severidad = Severidad.URGENTE,
                    quePaso = "Olor ${olor.etiqueta.lowercase()} en el fermentador",
                    porQueImporta = olor.significado,
                    queHacer = "Pasa el lote a secado de inmediato.",
                    correccion = if (olor == OlorFermentacion.MOHO) "C-03" else "C-05",
                ),
            )
        }

        // RN-07: fermentación demasiado larga.
        val diasMaximos = umbrales["ferm_duracion_maxima_dias"]
        if (c.diasDesdeInicio > diasMaximos) {
            add(
                Alerta(
                    regla = "RN-07",
                    severidad = Severidad.URGENTE,
                    quePaso = "La fermentación lleva ${formatear(c.diasDesdeInicio, 1)} días",
                    porQueImporta = "Más de ${formatear(diasMaximos, 0)} días es " +
                        "sobrefermentación: aparece olor a amoniaco y el chocolate sale " +
                        "con sabores desagradables.",
                    queHacer = "Cierra la fermentación y pasa el lote a secado.",
                    correccion = "C-05",
                    valorMedido = c.diasDesdeInicio,
                    valorEsperado = diasMaximos,
                ),
            )
        }
    }

    // ------------------------------------------------------------ secado

    /** RN-08 y RN-09: humedad al cerrar y moho visible. */
    fun evaluarSecado(c: ContextoSecado): List<Alerta> = buildList {
        val humedad = c.humedadGranoPct
        val maxima = umbrales["humedad_maxima_pct"]
        if (c.cerrandoEtapa && humedad != null && humedad > maxima) {
            add(
                Alerta(
                    regla = "RN-08",
                    severidad = Severidad.BLOQUEANTE,
                    quePaso = "El grano tiene ${formatear(humedad, 1)} % de humedad",
                    porQueImporta = "Por encima de ${formatear(maxima, 0)} % el grano cría " +
                        "moho en el almacén y el lote se pierde en semanas.",
                    queHacer = "Sigue secando. Si aun así quieres almacenarlo, tendrás que " +
                        "confirmarlo y quedará registrado.",
                    correccion = "C-03",
                    valorMedido = humedad,
                    valorEsperado = maxima,
                ),
            )
        }

        if (c.mohoVisible) {
            add(
                Alerta(
                    regla = "RN-09",
                    severidad = Severidad.URGENTE,
                    quePaso = "Hay moho visible en el grano",
                    porQueImporta = "El moho da un sabor que no se quita en ninguna etapa " +
                        "posterior, y la norma casi no admite granos mohosos.",
                    queHacer = "Separa los granos afectados y seca en capa más delgada.",
                    correccion = "C-03",
                ),
            )
        }
    }

    // ------------------------------------------------------ prueba de corte

    /**
     * RN-10: el resultado de la prueba de corte no cumple la norma.
     *
     * Recibe el resultado ya calculado por el calificador para no duplicar el
     * cálculo: aquí solo se decide qué corrección sugerir.
     */
    fun evaluarPruebaCorte(
        conforme: Boolean,
        resultado: String,
        fallas: List<String>,
        pctVioleta: Double,
        pctPizarroso: Double,
        pctMohoso: Double,
    ): List<Alerta> {
        if (conforme) return emptyList()

        // La corrección se elige por el defecto que más pesa, no por el primero.
        val correccion = when {
            pctPizarroso >= pctVioleta && pctPizarroso >= pctMohoso -> "C-04"
            pctVioleta >= pctMohoso -> "C-02"
            else -> "C-03"
        }

        return listOf(
            Alerta(
                regla = "RN-10",
                severidad = Severidad.AVISO,
                quePaso = "La prueba de corte dio \"$resultado\"",
                porQueImporta = if (fallas.isEmpty()) {
                    "El lote no alcanza los requisitos de la norma."
                } else {
                    "No cumple: ${fallas.joinToString("; ")}."
                },
                queHacer = "Revisa la corrección sugerida para el próximo lote.",
                correccion = correccion,
            ),
        )
    }

    // ------------------------------------------------------------ almacén

    /** RN-11: humedad ambiente alta, moho o plagas en el almacén. */
    fun evaluarAlmacen(c: ContextoAlmacen): List<Alerta> = buildList {
        val hr = c.humedadRelativaPct
        val maxima = umbrales["almacen_hr_maxima_pct"]

        if (hr != null && hr > maxima) {
            add(
                Alerta(
                    regla = "RN-11",
                    severidad = Severidad.AVISO,
                    quePaso = "El almacén está a ${formatear(hr, 0)} % de humedad",
                    porQueImporta = "Por encima de ${formatear(maxima, 0)} % el grano seco " +
                        "vuelve a tomar agua del aire y aparece moho.",
                    queHacer = "Ventila el almacén o usa deshumidificador. Revisa que los " +
                        "sacos no toquen el piso ni las paredes.",
                    correccion = "C-03",
                    valorMedido = hr,
                    valorEsperado = maxima,
                ),
            )
        }
        if (c.mohoVisible) {
            add(
                Alerta(
                    regla = "RN-11",
                    severidad = Severidad.URGENTE,
                    quePaso = "Hay moho en los sacos almacenados",
                    porQueImporta = "El moho se extiende de un saco a otro.",
                    queHacer = "Separa los sacos afectados hoy mismo.",
                    correccion = "C-03",
                ),
            )
        }
        if (c.plagas) {
            add(
                Alerta(
                    regla = "RN-11",
                    severidad = Severidad.URGENTE,
                    quePaso = "Se detectaron plagas en el almacén",
                    porQueImporta = "Las plagas dañan el grano y son un incumplimiento de " +
                        "BPM que ARCSA observa en inspección.",
                    queHacer = "Aplica el control de plagas y regístralo en BPM.",
                ),
            )
        }
    }

    // ------------------------------------------------------------ tostado

    /** RN-12: merma de tostado fuera de rango, y desvío de cascarilla. */
    fun evaluarTostado(c: ContextoTostado): List<Alerta> = buildList {
        val merma = c.mermaPct
        val minima = umbrales["tostado_merma_min_pct"]
        val maxima = umbrales["tostado_merma_max_pct"]

        if (merma != null && (merma < minima || merma > maxima)) {
            val alto = merma > maxima
            add(
                Alerta(
                    regla = "RN-12",
                    severidad = Severidad.AVISO,
                    quePaso = "La merma del tostado fue ${formatear(merma, 1)} %",
                    porQueImporta = if (alto) {
                        "Lo normal es entre ${formatear(minima, 0)} y " +
                            "${formatear(maxima, 0)} %. Una merma así de alta suele " +
                            "significar que el tostado fue excesivo."
                    } else {
                        "Lo normal es entre ${formatear(minima, 0)} y " +
                            "${formatear(maxima, 0)} %. Una merma tan baja suele ser un " +
                            "error de balanza o un tostado muy corto."
                    },
                    queHacer = if (alto) {
                        "Baja la temperatura o el tiempo en el próximo tostado."
                    } else {
                        "Revisa la balanza y el perfil de tostado."
                    },
                    valorMedido = merma,
                    valorEsperado = if (alto) maxima else minima,
                ),
            )
        }

        val cascarilla = c.cascarillaPct
        if (cascarilla != null) {
            val esperada = umbrales["cascarilla_esperada_pct"]
            val desvio = umbrales["cascarilla_desvio_pct"]
            if (abs(cascarilla - esperada) > desvio) {
                val alta = cascarilla > esperada
                add(
                    Alerta(
                        regla = "RN-12",
                        severidad = Severidad.AVISO,
                        quePaso = "La cascarilla fue ${formatear(cascarilla, 1)} %",
                        porQueImporta = if (alta) {
                            "Lo esperado es ${formatear(esperada, 0)} %. Tanta cascarilla " +
                                "suele significar que se están yendo nibs con ella."
                        } else {
                            "Lo esperado es ${formatear(esperada, 0)} %. Tan poca " +
                                "cascarilla suele significar que quedan nibs sucios."
                        },
                        queHacer = "Ajusta el ventilador del descascarillador.",
                        valorMedido = cascarilla,
                        valorEsperado = esperada,
                    ),
                )
            }
        }
    }

    // ------------------------------------------------------------ atemperado

    /** RN-13 y RN-14: cuarto caliente o húmedo, temperatura de trabajo fuera. */
    fun evaluarAtemperado(c: ContextoAtemperado): List<Alerta> = buildList {
        val temp = c.tempCuartoC
        val tempMax = umbrales["cuarto_temp_maxima_c"]
        if (temp != null && temp > tempMax) {
            add(
                Alerta(
                    regla = "RN-13",
                    severidad = Severidad.AVISO,
                    quePaso = "El cuarto está a ${formatear(temp, 1)} °C",
                    porQueImporta = "Por encima de ${formatear(tempMax, 0)} °C el chocolate " +
                        "no cristaliza bien y sale con fat bloom (velo grisáceo).",
                    queHacer = "Atempera temprano en la mañana o enfría el cuarto.",
                    correccion = "C-08",
                    valorMedido = temp,
                    valorEsperado = tempMax,
                ),
            )
        }

        val hr = c.humedadCuartoPct
        val hrMax = umbrales["cuarto_hr_maxima_pct"]
        if (hr != null && hr > hrMax) {
            add(
                Alerta(
                    regla = "RN-13",
                    severidad = Severidad.AVISO,
                    quePaso = "El cuarto está a ${formatear(hr, 0)} % de humedad",
                    porQueImporta = "Con más de ${formatear(hrMax, 0)} % se condensa agua " +
                        "sobre el chocolate y aparece sugar bloom.",
                    queHacer = "Baja la humedad antes de moldear.",
                    correccion = "C-09",
                    valorMedido = hr,
                    valorEsperado = hrMax,
                ),
            )
        }

        val trabajo = c.tempTrabajoC
        val minimo = umbrales["atemperado_temp_trabajo_min_c"]
        val maximo = umbrales["atemperado_temp_trabajo_max_c"]
        if (trabajo != null && (trabajo < minimo || trabajo > maximo)) {
            add(
                Alerta(
                    regla = "RN-14",
                    severidad = Severidad.AVISO,
                    quePaso = "Temperatura de trabajo ${formatear(trabajo, 1)} °C",
                    porQueImporta = "El chocolate negro se moldea entre " +
                        "${formatear(minimo, 0)} y ${formatear(maximo, 0)} °C. Fuera de " +
                        "ahí, o queda sin brillo o se deshacen los cristales buenos.",
                    queHacer = if (trabajo < minimo) {
                        "Calienta un poco, con cuidado de no pasarte."
                    } else {
                        "Deja enfriar antes de moldear."
                    },
                    correccion = "C-08",
                    valorMedido = trabajo,
                    valorEsperado = if (trabajo < minimo) minimo else maximo,
                ),
            )
        }
    }

    // ------------------------------------------------------------ laboratorio

    /** RN-15: cadmio por encima del límite. Bloquea la venta del lote. */
    fun evaluarLaboratorio(c: ContextoLaboratorio): List<Alerta> {
        if (!c.analisis.equals("cadmio", ignoreCase = true)) return emptyList()
        val limite = umbrales["cadmio_limite_mg_kg"]
        if (c.valor <= limite) return emptyList()

        return listOf(
            Alerta(
                regla = "RN-15",
                severidad = Severidad.BLOQUEANTE,
                quePaso = "Cadmio de ${formatear(c.valor, 2)} ${c.unidad}",
                porQueImporta = "Supera el límite configurado de ${formatear(limite, 2)} " +
                    "mg/kg. Este lote no se puede vender mientras no se resuelva.",
                queHacer = "La venta de este lote queda bloqueada. Consulta con el " +
                    "laboratorio y, si corresponde, repite el análisis.",
                valorMedido = c.valor,
                valorEsperado = limite,
            ),
        )
    }

    // ------------------------------------------------------------ rendimientos

    /** RN-17: el rendimiento de una etapa se aleja de lo esperado. */
    fun evaluarRendimiento(
        etapa: String,
        rendimientoReal: Double,
        rendimientoEsperado: Double,
    ): List<Alerta> {
        if (rendimientoEsperado <= 0) return emptyList()
        val desvio = umbrales["rendimiento_desvio_pct"]
        val diferencia =
            100.0 * abs(rendimientoReal - rendimientoEsperado) / rendimientoEsperado
        if (diferencia <= desvio) return emptyList()

        return listOf(
            Alerta(
                regla = "RN-17",
                severidad = Severidad.AVISO,
                quePaso = "El rendimiento de $etapa no cuadra",
                porQueImporta = "Salió ${formatear(rendimientoReal, 2)} y lo esperado era " +
                    "${formatear(rendimientoEsperado, 2)}, una diferencia de " +
                    "${formatear(diferencia, 0)} %. Casi siempre es un error al anotar un " +
                    "peso.",
                queHacer = "Revisa los pesos que registraste en esta etapa.",
                valorMedido = rendimientoReal,
                valorEsperado = rendimientoEsperado,
            ),
        )
    }
}

// ---------------------------------------------------------------- contextos

/** Datos de un lote en reposo, para evaluar RN-01. */
data class ContextoReposo(val diasDesdeLlegada: Int, val abierto: Boolean = false)

/** Datos de la apertura, para RN-02 y el aviso de rendimiento. */
data class ContextoApertura(
    val mazorcasAbiertas: Int,
    val kgBaba: Double,
    val kgCascaraAnadida: Double = 0.0,
)

/** Una lectura de fermentación, para RN-03 a RN-07. */
data class ContextoFermentacion(
    val horasDesdeInicio: Double,
    val temperaturaC: Double? = null,
    val horasDesdeUltimoVolteo: Double? = null,
    val olor: OlorFermentacion? = null,
    val ph: Double? = null,
) {
    val diasDesdeInicio: Double get() = horasDesdeInicio / 24.0
}

/** Datos del secado, para RN-08 y RN-09. */
data class ContextoSecado(
    val humedadGranoPct: Double? = null,
    val mohoVisible: Boolean = false,
    /** Solo se bloquea el paso a almacenamiento al cerrar el secado. */
    val cerrandoEtapa: Boolean = false,
)

/** Datos de una inspección de almacén, para RN-11. */
data class ContextoAlmacen(
    val humedadRelativaPct: Double? = null,
    val mohoVisible: Boolean = false,
    val plagas: Boolean = false,
    val diasDesdeUltimaInspeccion: Int? = null,
)

/** Datos del tostado y el descascarillado, para RN-12. */
data class ContextoTostado(
    val kgEntrada: Double,
    val kgSalida: Double,
    val kgNibs: Double? = null,
    val kgCascarilla: Double? = null,
) {
    val mermaPct: Double?
        get() = if (kgEntrada <= 0) null else 100.0 * (kgEntrada - kgSalida) / kgEntrada

    val cascarillaPct: Double?
        get() {
            val nibs = kgNibs ?: return null
            val cascara = kgCascarilla ?: return null
            val total = nibs + cascara
            return if (total <= 0) null else 100.0 * cascara / total
        }
}

/** Datos del atemperado, para RN-13 y RN-14. */
data class ContextoAtemperado(
    val tempCuartoC: Double? = null,
    val humedadCuartoPct: Double? = null,
    val tempTrabajoC: Double? = null,
)

/** Un resultado de laboratorio, para RN-15. */
data class ContextoLaboratorio(
    val analisis: String,
    val valor: Double,
    val unidad: String,
)
