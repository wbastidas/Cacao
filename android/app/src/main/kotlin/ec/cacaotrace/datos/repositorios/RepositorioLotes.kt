package ec.cacaotrace.datos.repositorios

import ec.cacaotrace.datos.bd.BaseDatos
import ec.cacaotrace.datos.bd.Comunes
import ec.cacaotrace.datos.bd.entidades.AperturaEntidad
import ec.cacaotrace.datos.bd.entidades.FermentacionEntidad
import ec.cacaotrace.datos.bd.entidades.FincaEntidad
import ec.cacaotrace.datos.bd.entidades.LecturaFermentacionEntidad
import ec.cacaotrace.datos.bd.entidades.LecturaSecadoEntidad
import ec.cacaotrace.datos.bd.entidades.LoteEntidad
import ec.cacaotrace.datos.bd.entidades.PruebaCorteEntidad
import ec.cacaotrace.datos.bd.entidades.RecepcionEntidad
import ec.cacaotrace.datos.bd.entidades.SecadoEntidad
import ec.cacaotrace.datos.bd.entidades.VolteoEntidad
import ec.cacaotrace.datos.sync.ServicioSincronizacion
import ec.cacaotrace.nucleo.calculo.BalanceMasa
import ec.cacaotrace.nucleo.calculo.PasoBalance
import ec.cacaotrace.nucleo.calculo.RendimientosEsperados
import ec.cacaotrace.nucleo.modelo.Codigos
import ec.cacaotrace.nucleo.modelo.EstadoLote
import ec.cacaotrace.nucleo.modelo.MetodoSecado
import ec.cacaotrace.nucleo.modelo.OlorFermentacion
import ec.cacaotrace.nucleo.norma.ResultadoCorte
import ec.cacaotrace.nucleo.reglas.Alerta
import ec.cacaotrace.nucleo.reglas.ContextoApertura
import ec.cacaotrace.nucleo.reglas.ContextoFermentacion
import ec.cacaotrace.nucleo.reglas.ContextoReposo
import ec.cacaotrace.nucleo.reglas.ContextoSecado
import ec.cacaotrace.nucleo.reglas.MotorReglas
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/**
 * Repositorio del lote de grano: de la recepción de mazorcas al saco
 * almacenado. Es donde se juntan la escritura, la sincronización y las reglas.
 *
 * Las pantallas nunca hablan con Room directamente: si lo hicieran, cada una
 * tendría que acordarse de encolar la sincronización y de evaluar las reglas.
 */
class RepositorioLotes(
    private val bd: BaseDatos,
    private val sync: ServicioSincronizacion,
    private val config: RepositorioConfiguracion,
    private val alertas: RepositorioAlertas,
) {
    private val json = Json { encodeDefaults = true }

    private suspend fun motor() = MotorReglas(config.umbrales())

    // ------------------------------------------------------------ lotes

    fun observarLotes(incluirCerrados: Boolean = true): Flow<List<LoteEntidad>> =
        if (incluirCerrados) bd.lotes().observarTodos() else bd.lotes().observarActivos()

    suspend fun porId(id: String): LoteEntidad? = bd.lotes().porId(id)

    fun observarPorId(id: String): Flow<LoteEntidad?> = bd.lotes().observarPorId(id)

    suspend fun porCodigo(codigo: String): LoteEntidad? = bd.lotes().porCodigo(codigo)

    /** Crea un lote con código correlativo del año (RF-LOT-01). */
    suspend fun crearLote(
        fincaId: String? = null,
        fechaCosecha: Instant? = null,
        fechaLlegada: Instant = Instant.now(),
        variedad: String = "CCN-51",
    ): LoteEntidad {
        val anio = fechaLlegada.atZone(java.time.ZoneId.systemDefault()).year
        val codigo = Codigos.siguiente(Codigos.PREFIJO_LOTE, anio, bd.lotes().codigosUsados())

        val lote = LoteEntidad(
            codigo = codigo,
            fincaId = fincaId,
            variedad = variedad,
            fechaCosecha = fechaCosecha,
            fechaLlegada = fechaLlegada,
        )
        bd.lotes().insertar(lote)
        sync.encolar("lotes", lote.id, "crear", """{"codigo":"$codigo"}""")
        return lote
    }

    /**
     * Cambia el estado del lote.
     *
     * Si se salta una etapa obligatoria hay que dar un motivo, y queda escrito
     * en el lote para siempre (RF-LOT-03).
     */
    suspend fun cambiarEstado(
        loteId: String,
        nuevo: EstadoLote,
        motivoSalto: String = "",
    ) {
        val lote = porId(loteId) ?: throw IllegalArgumentException("El lote $loteId no existe")
        val saltadas = EstadoLote.etapasSaltadas(lote.estado, nuevo)
        if (saltadas.isNotEmpty() && motivoSalto.isBlank()) {
            throw EtapaSaltadaSinMotivo(saltadas)
        }

        val motivo = if (saltadas.isEmpty() || motivoSalto.isBlank()) {
            lote.motivoSalto
        } else {
            buildString {
                append(lote.motivoSalto)
                if (isNotEmpty()) append('\n')
                append("${Instant.now()}: se saltó ")
                append(saltadas.joinToString(", ") { it.etiqueta })
                append(" porque $motivoSalto")
            }
        }

        bd.lotes().actualizar(
            lote.copy(estado = nuevo, motivoSalto = motivo, comunes = lote.comunes.tocada()),
        )
        sync.encolar("lotes", loteId, "actualizar", """{"estado":"${nuevo.name}"}""")
    }

    /** Trae el lote con todo lo colgado de él, para la pantalla de detalle. */
    suspend fun completo(loteId: String): LoteCompleto? {
        val lote = porId(loteId) ?: return null
        return LoteCompleto(
            lote = lote,
            finca = lote.fincaId?.let { config.fincaPorId(it) },
            recepcion = bd.recepciones().deLote(loteId),
            apertura = bd.aperturas().deLote(loteId),
            fermentacion = bd.fermentaciones().deLote(loteId),
            secado = bd.secados().deLote(loteId),
            pruebasCorte = bd.pruebasCorte().deLote(loteId),
            alertasAbiertas = alertas.deLote(loteId).count { it.estado == "abierta" },
        )
    }

    // ------------------------------------------------------------ recepción

    /** Registra la recepción y programa la apertura (RF-REC-01, RF-REC-05). */
    suspend fun guardarRecepcion(
        loteId: String,
        mazorcasTotal: Int,
        pesoKg: Double,
        sacos: Int = 0,
        sanas: Int = 0,
        monilia: Int = 0,
        fitoftora: Int = 0,
        otro: Int = 0,
        descartadas: Int = 0,
        motivoDescarte: String = "",
        diasReposo: Int = 4,
    ): RecepcionEntidad {
        val lote = porId(loteId)
        val fechaApertura = (lote?.fechaLlegada ?: Instant.now())
            .plus(diasReposo.toLong(), ChronoUnit.DAYS)

        val existente = bd.recepciones().deLote(loteId)
        val recepcion = (existente ?: RecepcionEntidad(loteId = loteId)).copy(
            loteId = loteId,
            sacos = sacos,
            mazorcasTotal = mazorcasTotal,
            pesoKg = pesoKg,
            mazorcasSanas = sanas,
            mazorcasMonilia = monilia,
            mazorcasFitoftora = fitoftora,
            mazorcasOtro = otro,
            mazorcasDescartadas = descartadas,
            motivoDescarte = motivoDescarte,
            diasReposo = diasReposo,
            fechaAperturaPlan = fechaApertura,
            comunes = (existente?.comunes ?: Comunes()).tocada(),
        )
        bd.recepciones().guardar(recepcion)
        sync.encolar("recepciones", recepcion.id, "actualizar")
        cambiarEstado(loteId, EstadoLote.REPOSO)
        return recepcion
    }

    /** Revisa el reposo de todos los lotes y levanta las alertas RN-01. */
    suspend fun revisarReposos(): List<Alerta> {
        val motor = motor()
        val nuevas = mutableListOf<Alerta>()
        for (lote in bd.lotes().porEstado(EstadoLote.REPOSO)) {
            val dias = Duration.between(lote.fechaLlegada, Instant.now()).toDays().toInt()
            nuevas += alertas.registrar(
                motor.evaluarReposo(ContextoReposo(dias)),
                loteId = lote.id,
            )
        }
        return nuevas
    }

    // ------------------------------------------------------------ apertura

    /** Registra la apertura y evalúa RN-02 y RN-17 (RF-APE-01/02/03). */
    suspend fun guardarApertura(
        loteId: String,
        mazorcasAbiertas: Int,
        kgBaba: Double,
        kgCascaraAnadida: Double = 0.0,
        notas: String = "",
    ): List<Alerta> {
        val existente = bd.aperturas().deLote(loteId)
        val apertura = (existente ?: AperturaEntidad(loteId = loteId, fecha = Instant.now()))
            .copy(
                loteId = loteId,
                fecha = existente?.fecha ?: Instant.now(),
                mazorcasAbiertas = mazorcasAbiertas,
                kgBaba = kgBaba,
                kgCascaraAnadida = kgCascaraAnadida,
                notas = notas,
                comunes = (existente?.comunes ?: Comunes()).tocada(),
            )
        bd.aperturas().guardar(apertura)
        sync.encolar("aperturas", apertura.id, "actualizar")

        return alertas.registrar(
            motor().evaluarApertura(
                ContextoApertura(mazorcasAbiertas, kgBaba, kgCascaraAnadida),
            ),
            loteId = loteId,
        )
    }

    /** Cuánta cáscara hay que añadir para llegar a la masa mínima (RF-APE-03). */
    suspend fun cascaraRecomendada(kgBaba: Double): Double {
        val falta = config.umbrales()["baba_masa_minima_kg"] - kgBaba
        return if (falta > 0) falta else 0.0
    }

    // ------------------------------------------------------------ fermentación

    suspend fun iniciarFermentacion(
        loteId: String,
        equipoId: String? = null,
        masaKg: Double,
        aislamiento: String = "",
        inicio: Instant = Instant.now(),
        motivoSalto: String = "",
    ): FermentacionEntidad {
        val fermentacion = FermentacionEntidad(
            loteId = loteId,
            equipoId = equipoId,
            inicio = inicio,
            masaKg = masaKg,
            aislamiento = aislamiento,
        )
        bd.fermentaciones().guardar(fermentacion)
        sync.encolar("fermentaciones", fermentacion.id, "crear")
        cambiarEstado(loteId, EstadoLote.FERMENTACION, motivoSalto)
        return fermentacion
    }

    suspend fun fermentacionDe(loteId: String): FermentacionEntidad? =
        bd.fermentaciones().deLote(loteId)

    fun observarLecturas(fermentacionId: String): Flow<List<LecturaFermentacionEntidad>> =
        bd.fermentaciones().observarLecturas(fermentacionId)

    fun observarVolteos(fermentacionId: String): Flow<List<VolteoEntidad>> =
        bd.fermentaciones().observarVolteos(fermentacionId)

    /** Registra una lectura y evalúa las reglas de fermentación (RF-FER-02/03). */
    suspend fun registrarLectura(
        fermentacionId: String,
        loteId: String,
        tempC: Double? = null,
        ph: Double? = null,
        olor: OlorFermentacion? = null,
        fuente: String = "manual",
        fotoId: String? = null,
        notas: String = "",
        cuando: Instant = Instant.now(),
    ): List<Alerta> {
        val fermentacion = bd.fermentaciones().deLote(loteId)
            ?: throw IllegalArgumentException("No hay fermentación para el lote $loteId")

        val lectura = LecturaFermentacionEntidad(
            fermentacionId = fermentacionId,
            fechaHora = cuando,
            tempC = tempC,
            ph = ph,
            olor = olor,
            fuente = fuente,
            fotoId = fotoId,
            notas = notas,
        )
        bd.fermentaciones().insertarLectura(lectura)
        sync.encolar("lecturas_fermentacion", lectura.id, "crear")

        val ultimoVolteo = bd.fermentaciones().ultimoVolteo(fermentacionId)
        return alertas.registrar(
            motor().evaluarFermentacion(
                ContextoFermentacion(
                    horasDesdeInicio = horasEntre(fermentacion.inicio, cuando),
                    temperaturaC = tempC,
                    horasDesdeUltimoVolteo = ultimoVolteo?.let {
                        horasEntre(it.fechaHora, cuando)
                    },
                    olor = olor,
                    ph = ph,
                ),
            ),
            loteId = loteId,
        )
    }

    /** Registra un volteo (RF-FER-04). Debe poder hacerse en un solo toque. */
    suspend fun registrarVolteo(fermentacionId: String, fotoId: String? = null) {
        val volteo = VolteoEntidad(
            fermentacionId = fermentacionId,
            fechaHora = Instant.now(),
            fotoId = fotoId,
        )
        bd.fermentaciones().insertarVolteo(volteo)
        sync.encolar("volteos", volteo.id, "crear")
    }

    /** Cierra la fermentación y pasa el lote a secado (RF-FER-07). */
    suspend fun cerrarFermentacion(fermentacionId: String, loteId: String) {
        val f = bd.fermentaciones().deLote(loteId) ?: return
        bd.fermentaciones().guardar(
            f.copy(fin = Instant.now(), comunes = f.comunes.tocada()),
        )
        sync.encolar("fermentaciones", fermentacionId, "actualizar")
        cambiarEstado(loteId, EstadoLote.SECADO)
    }

    // ------------------------------------------------------------ secado

    suspend fun iniciarSecado(
        loteId: String,
        metodo: MetodoSecado = MetodoSecado.MARQUESINA,
        motivoSalto: String = "",
    ): SecadoEntidad {
        val secado = SecadoEntidad(loteId = loteId, metodo = metodo, inicio = Instant.now())
        bd.secados().guardar(secado)
        sync.encolar("secados", secado.id, "crear")
        cambiarEstado(loteId, EstadoLote.SECADO, motivoSalto)
        return secado
    }

    suspend fun secadoDe(loteId: String): SecadoEntidad? = bd.secados().deLote(loteId)

    fun observarLecturasSecado(secadoId: String): Flow<List<LecturaSecadoEntidad>> =
        bd.secados().observarLecturas(secadoId)

    /** Registra la jornada de secado y evalúa RN-09 (RF-SEC-02/03/04). */
    suspend fun registrarLecturaSecado(
        secadoId: String,
        loteId: String,
        humedadGrano: Double? = null,
        pruebaPunado: String = "",
        tempAmbiente: Double? = null,
        hrAmbiente: Double? = null,
        espesorCm: Double? = null,
        remociones: Int = 0,
        moho: Boolean = false,
        fotoId: String? = null,
    ): List<Alerta> {
        val lectura = LecturaSecadoEntidad(
            secadoId = secadoId,
            fecha = Instant.now(),
            humedadGrano = humedadGrano,
            pruebaPunado = pruebaPunado,
            tempAmbiente = tempAmbiente,
            hrAmbiente = hrAmbiente,
            espesorCm = espesorCm,
            remociones = remociones,
            moho = moho,
            fotoId = fotoId,
        )
        bd.secados().insertarLectura(lectura)
        sync.encolar("lecturas_secado", lectura.id, "crear")

        return alertas.registrar(
            motor().evaluarSecado(ContextoSecado(humedadGrano, moho)),
            loteId = loteId,
        )
    }

    /**
     * Cierra el secado. Si la humedad es alta, RN-08 bloquea hasta confirmar.
     *
     * Devuelve las alertas generadas. Si alguna bloquea y [confirmado] es
     * false, el lote NO pasa a almacenado.
     */
    suspend fun cerrarSecado(
        secadoId: String,
        loteId: String,
        kgSeco: Double,
        humedadFinal: Double? = null,
        confirmado: Boolean = false,
    ): List<Alerta> {
        val motor = motor()
        val generadas = motor.evaluarSecado(
            ContextoSecado(humedadGranoPct = humedadFinal, cerrandoEtapa = true),
        )
        val bloquea = generadas.any { it.bloquea }
        alertas.registrar(generadas, loteId = loteId)

        if (bloquea && !confirmado) return generadas

        val secado = bd.secados().deLote(loteId) ?: return generadas
        bd.secados().guardar(
            secado.copy(
                fin = Instant.now(),
                kgSeco = kgSeco,
                comunes = secado.comunes.tocada(),
            ),
        )
        sync.encolar("secados", secadoId, "actualizar")
        cambiarEstado(loteId, EstadoLote.ALMACENADO)

        // RN-17: comparar el rendimiento baba -> seco con lo esperado.
        val apertura = bd.aperturas().deLote(loteId)
        if (apertura != null && apertura.kgBaba > 0) {
            val extra = motor.evaluarRendimiento(
                etapa = "secado",
                rendimientoReal = kgSeco / apertura.kgBaba,
                rendimientoEsperado = RendimientosEsperados().fraccionBabaASeco,
            )
            alertas.registrar(extra, loteId = loteId)
            return generadas + extra
        }
        return generadas
    }

    // ------------------------------------------------------- prueba de corte

    /** Guarda una prueba de corte ya calificada (RF-PRC-06, RF-PRC-10). */
    suspend fun guardarPruebaCorte(
        loteId: String,
        conteo: Map<String, Int>,
        resultado: ResultadoCorte,
        peso100g: Double? = null,
        modeloVersion: String = "",
        fotoId: String? = null,
        esParcial: Boolean = false,
    ): PruebaCorteEntidad {
        val prueba = PruebaCorteEntidad(
            loteId = loteId,
            fecha = Instant.now(),
            perfilNorma = resultado.perfil,
            granos = resultado.porcentajes.granosContados,
            conteosJson = json.encodeToString(conteo),
            porcentajesJson = json.encodeToString(resultado.porcentajes.valores),
            resultado = resultado.resultado,
            conforme = resultado.conforme,
            peso100g = peso100g,
            modeloVersion = modeloVersion,
            esParcial = esParcial,
            fotoId = fotoId,
        )
        bd.pruebasCorte().insertar(prueba)
        sync.encolar("pruebas_corte", prueba.id, "crear")

        // RNF-11: la prueba de corte es un registro sensible.
        config.auditar(
            tabla = "pruebas_corte",
            registroId = prueba.id,
            campo = "resultado",
            valorAnterior = "",
            valorNuevo = resultado.resultado,
            motivo = if (modeloVersion.isBlank()) {
                "conteo manual"
            } else {
                "modelo $modeloVersion con corrección del usuario"
            },
        )

        alertas.registrar(
            motor().evaluarPruebaCorte(
                conforme = resultado.conforme,
                resultado = resultado.resultado,
                fallas = resultado.todasLasFallas,
                pctVioleta = resultado.porcentajes["violeta"],
                pctPizarroso = resultado.porcentajes["pizarroso"],
                pctMohoso = resultado.porcentajes["mohoso"],
            ),
            loteId = loteId,
        )
        return prueba
    }

    // ------------------------------------------------------------ balance

    /** Balance de masa del lote con lo registrado hasta ahora (RF-LOT-08). */
    suspend fun balance(loteId: String): List<PasoBalance> {
        val c = completo(loteId) ?: return emptyList()

        var kgNibs: Double? = null
        var kgChocolate: Double? = null
        val origenes = bd.produccion().origenesDeLote(loteId)
        if (origenes.isNotEmpty()) {
            val ids = origenes.map { it.loteProduccionId }.distinct()
            val desc = bd.produccion().descascarilladosDe(ids)
            if (desc.isNotEmpty()) kgNibs = desc.sumOf { it.kgNibs }
            val tandas = bd.produccion().porIds(ids).mapNotNull { it.kgChocolate }
            if (tandas.isNotEmpty()) kgChocolate = tandas.sum()
        }

        return BalanceMasa().calcular(
            mazorcas = c.recepcion?.mazorcasTotal ?: 0,
            kgBaba = c.apertura?.kgBaba,
            kgSeco = c.secado?.kgSeco,
            kgNibs = kgNibs,
            kgChocolate = kgChocolate,
        )
    }

    // ------------------------------------------------------- línea de tiempo

    /** Todo lo que le pasó al lote, en orden (RF-LOT-06). */
    suspend fun lineaDeTiempo(loteId: String): List<HechoLote> {
        val c = completo(loteId) ?: return emptyList()
        val hechos = mutableListOf<HechoLote>()

        hechos += HechoLote(
            fecha = c.lote.fechaLlegada,
            etapa = "Recepción",
            titulo = "Llegó el lote ${c.lote.codigo}",
            detalle = c.finca?.let { "Desde ${it.nombre}" }.orEmpty(),
        )

        c.recepcion?.let { r ->
            hechos += HechoLote(
                fecha = r.comunes.creadoEn,
                etapa = "Recepción",
                titulo = "${r.mazorcasTotal} mazorcas, ${r.pesoKg} kg",
                detalle = "Sanas ${r.mazorcasSanas} · monilia ${r.mazorcasMonilia} · " +
                    "fitóftora ${r.mazorcasFitoftora} · descartadas ${r.mazorcasDescartadas}",
            )
        }

        c.apertura?.let { a ->
            hechos += HechoLote(
                fecha = a.fecha,
                etapa = "Apertura",
                titulo = "${a.mazorcasAbiertas} mazorcas abiertas",
                detalle = buildString {
                    append("${a.kgBaba} kg de baba")
                    if (a.kgCascaraAnadida > 0) {
                        append(" + ${a.kgCascaraAnadida} kg de cáscara")
                    }
                },
            )
        }

        c.fermentacion?.let { f ->
            hechos += HechoLote(f.inicio, "Fermentación", "Empezó la fermentación", "${f.masaKg} kg")
            bd.fermentaciones().volteos(f.id).forEach {
                hechos += HechoLote(it.fechaHora, "Fermentación", "Volteo", fotoId = it.fotoId)
            }
            bd.fermentaciones().lecturas(f.id).forEach { l ->
                hechos += HechoLote(
                    fecha = l.fechaHora,
                    etapa = "Fermentación",
                    titulo = l.tempC?.let { "${it} °C" } ?: "Lectura",
                    detalle = listOfNotNull(
                        l.ph?.let { "pH $it" },
                        l.olor?.let { "olor ${it.etiqueta.lowercase()}" },
                    ).joinToString(" · "),
                    fotoId = l.fotoId,
                )
            }
            f.fin?.let { fin ->
                hechos += HechoLote(
                    fin,
                    "Fermentación",
                    "Terminó la fermentación",
                    "${Duration.between(f.inicio, fin).toHours()} horas en total",
                )
            }
        }

        c.secado?.let { s ->
            hechos += HechoLote(s.inicio, "Secado", "Empezó el secado (${s.metodo.etiqueta})")
            bd.secados().lecturas(s.id).forEach { l ->
                hechos += HechoLote(
                    fecha = l.fecha,
                    etapa = "Secado",
                    titulo = l.humedadGrano?.let { "Humedad $it %" } ?: "Jornada de secado",
                    detalle = listOfNotNull(
                        if (l.remociones > 0) "${l.remociones} remociones" else null,
                        if (l.moho) "MOHO VISIBLE" else null,
                    ).joinToString(" · "),
                    fotoId = l.fotoId,
                )
            }
            s.fin?.let {
                hechos += HechoLote(it, "Secado", "Terminó el secado", "${s.kgSeco ?: 0.0} kg secos")
            }
        }

        c.pruebasCorte.forEach { p ->
            hechos += HechoLote(
                fecha = p.fecha,
                etapa = "Prueba de corte",
                titulo = p.resultado,
                detalle = "${p.granos} granos" + if (p.esParcial) " (prueba parcial)" else "",
                fotoId = p.fotoId,
            )
        }

        alertas.deLote(loteId).forEach { a ->
            hechos += HechoLote(a.fecha, a.regla, a.quePaso, a.queHacer, esAlerta = true)
        }

        return hechos.sortedBy { it.fecha }
    }

    private fun horasEntre(desde: Instant, hasta: Instant): Double =
        Duration.between(desde, hasta).toMinutes() / 60.0
}

/** Un lote con todo lo que la pantalla de detalle necesita de una sola vez. */
data class LoteCompleto(
    val lote: LoteEntidad,
    val finca: FincaEntidad? = null,
    val recepcion: RecepcionEntidad? = null,
    val apertura: AperturaEntidad? = null,
    val fermentacion: FermentacionEntidad? = null,
    val secado: SecadoEntidad? = null,
    val pruebasCorte: List<PruebaCorteEntidad> = emptyList(),
    val alertasAbiertas: Int = 0,
) {
    /** La última prueba de corte completa, que es la que define el grado. */
    val pruebaCorteFinal: PruebaCorteEntidad?
        get() = pruebasCorte.lastOrNull { !it.esParcial }
}

/** Un hecho de la línea de tiempo del lote (RF-LOT-06). */
data class HechoLote(
    val fecha: Instant,
    val etapa: String,
    val titulo: String,
    val detalle: String = "",
    val fotoId: String? = null,
    val esAlerta: Boolean = false,
)

/** Se intentó saltar una etapa obligatoria sin dar un motivo (RF-LOT-03). */
class EtapaSaltadaSinMotivo(val etapas: List<EstadoLote>) : IllegalStateException(
    "Para saltar ${etapas.joinToString(", ") { it.etiqueta }} hay que indicar el " +
        "motivo, y queda registrado en el lote.",
)
