package ec.cacaotrace.datos.repositorios

import ec.cacaotrace.datos.bd.BaseDatos
import ec.cacaotrace.datos.bd.Comunes
import ec.cacaotrace.datos.bd.entidades.ChecklistBpmEntidad
import ec.cacaotrace.datos.bd.entidades.CostoEntidad
import ec.cacaotrace.datos.bd.entidades.FotoEntidad
import ec.cacaotrace.datos.bd.entidades.InspeccionAlmacenEntidad
import ec.cacaotrace.datos.bd.entidades.LaboratorioEntidad
import ec.cacaotrace.datos.bd.entidades.MovimientoInventarioEntidad
import ec.cacaotrace.datos.bd.entidades.RegistroBpmEntidad
import ec.cacaotrace.datos.bd.entidades.SacoEntidad
import ec.cacaotrace.datos.bd.entidades.VentaEntidad
import ec.cacaotrace.datos.sync.ServicioSincronizacion
import ec.cacaotrace.nucleo.modelo.EstadoLote
import ec.cacaotrace.nucleo.norma.formatear
import ec.cacaotrace.nucleo.reglas.Alerta
import ec.cacaotrace.nucleo.reglas.ContextoAlmacen
import ec.cacaotrace.nucleo.reglas.ContextoLaboratorio
import ec.cacaotrace.nucleo.reglas.MotorReglas
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Inventario, costos, ventas, BPM, laboratorio, sacos, fotos y el panel de
 * indicadores. Todo lo que no es una etapa del proceso pero la ERS pide.
 */
class RepositorioApoyo(
    private val bd: BaseDatos,
    private val sync: ServicioSincronizacion,
    private val config: RepositorioConfiguracion,
    private val alertas: RepositorioAlertas,
) {
    private val json = Json { encodeDefaults = true }

    private suspend fun motor() = MotorReglas(config.umbrales())

    /** Las clases que cuentan como defecto en el panel, en el orden de la norma. */
    private val clasesDefecto = listOf(
        "violeta", "pizarroso", "mohoso", "dano_insectos", "germinado", "vano_plano",
    )

    // ------------------------------------------------------------ inventario

    /**
     * Existencias calculadas desde los movimientos.
     *
     * No se guarda un saldo: se suma la historia. Así el número siempre se
     * puede explicar, y un error se corrige con un movimiento nuevo, no
     * editando un total a mano.
     */
    suspend fun existencias(): List<Existencia> {
        val movimientos = bd.inventario().movimientos()
        val minimos = bd.inventario().stocksMinimos().associateBy { it.item }

        val saldos = mutableMapOf<String, Double>()
        val unidades = mutableMapOf<String, String>()
        for (m in movimientos) {
            val signo = if (m.tipo == "salida") -1 else 1
            saldos[m.item] = (saldos[m.item] ?: 0.0) + signo * m.cantidad
            unidades[m.item] = m.unidad
        }
        for (item in minimos.keys) {
            saldos.putIfAbsent(item, 0.0)
            unidades.putIfAbsent(item, minimos.getValue(item).unidad)
        }

        return saldos.map { (item, cantidad) ->
            Existencia(
                item = item,
                cantidad = cantidad,
                unidad = unidades[item] ?: "kg",
                minimo = minimos[item]?.minimo,
            )
        }.sortedBy { it.item }
    }

    fun observarMovimientos(): Flow<List<MovimientoInventarioEntidad>> =
        bd.inventario().observarMovimientos()

    suspend fun registrarMovimiento(
        item: String,
        tipo: String,
        cantidad: Double,
        unidad: String = "kg",
        referencia: String = "",
        notas: String = "",
    ) {
        val m = MovimientoInventarioEntidad(
            item = item,
            tipo = tipo,
            cantidad = cantidad,
            unidad = unidad,
            fecha = Instant.now(),
            referencia = referencia,
            notas = notas,
        )
        bd.inventario().insertarMovimiento(m)
        sync.encolar("movimientos_inventario", m.id, "crear")
    }

    // ------------------------------------------------------------ costos

    suspend fun registrarCosto(
        concepto: String,
        montoUsd: Double,
        loteId: String? = null,
        loteProduccionId: String? = null,
        fecha: Instant = Instant.now(),
        notas: String = "",
    ) {
        val c = CostoEntidad(
            loteId = loteId,
            loteProduccionId = loteProduccionId,
            concepto = concepto,
            montoUsd = montoUsd,
            fecha = fecha,
            notas = notas,
        )
        bd.costos().insertar(c)
        sync.encolar("costos", c.id, "crear")
    }

    /** Costo de una tanda, incluyendo el de los lotes de grano que la formaron. */
    suspend fun costosDeProduccion(loteProduccionId: String): ResumenCostos {
        val origenes = bd.produccion().origenes(loteProduccionId)
        val loteIds = origenes.map { it.loteId }
        val costos = bd.costos().deProduccion(loteProduccionId, loteIds)

        val porConcepto = costos.groupBy { it.concepto }
            .mapValues { (_, lista) -> lista.sumOf { it.montoUsd } }

        return ResumenCostos(
            totalUsd = porConcepto.values.sum(),
            porConcepto = porConcepto,
            barras = bd.produccion().empaque(loteProduccionId)?.barras ?: 0,
            kgGrano = origenes.sumOf { it.kgUsados },
        )
    }

    // ------------------------------------------------------------ ventas

    suspend fun registrarVenta(
        loteProduccionId: String,
        barras: Int,
        cliente: String = "",
        precioUnitarioUsd: Double = 0.0,
        consumoPropio: Boolean = false,
        notas: String = "",
    ) {
        val v = VentaEntidad(
            loteProduccionId = loteProduccionId,
            cliente = cliente,
            barras = barras,
            precioUnitarioUsd = precioUnitarioUsd,
            fecha = Instant.now(),
            consumoPropio = consumoPropio,
            notas = notas,
        )
        bd.ventas().insertar(v)
        registrarMovimiento(
            item = "chocolate",
            tipo = "salida",
            cantidad = barras.toDouble(),
            unidad = "barras",
            referencia = loteProduccionId,
        )
        sync.encolar("ventas", v.id, "crear")
    }

    /** Ingresos menos costos de una tanda (RF-VEN-02). */
    suspend fun margenDeProduccion(loteProduccionId: String): Double {
        val ingresos = bd.ventas().deProduccion(loteProduccionId)
            .sumOf { it.barras * it.precioUnitarioUsd }
        return ingresos - costosDeProduccion(loteProduccionId).totalUsd
    }

    // ------------------------------------------------------------ BPM

    fun observarChecklists(): Flow<List<ChecklistBpmEntidad>> = bd.bpm().observarChecklists()

    suspend fun registroDeHoy(checklistId: String): RegistroBpmEntidad? {
        val inicioDelDia = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        return bd.bpm().registroDesde(checklistId, inicioDelDia)
    }

    suspend fun guardarRegistroBpm(
        checklistId: String,
        respuestas: Map<String, Boolean>,
        responsable: String,
        observaciones: String = "",
    ): String {
        val existente = registroDeHoy(checklistId)

        // RF-BPM-02: pasadas 24 h el registro se cierra y solo admite anotaciones.
        if (existente != null && existente.cerrado) {
            throw RegistroBpmCerrado(existente.fecha)
        }

        val respuestasJson = json.encodeToString(respuestas)
        val registro = (existente ?: RegistroBpmEntidad(
            checklistId = checklistId,
            fecha = Instant.now(),
            responsable = responsable,
        )).copy(
            respuestasJson = respuestasJson,
            responsable = responsable,
            observaciones = observaciones,
            comunes = (existente?.comunes ?: Comunes()).tocada(),
        )
        bd.bpm().guardarRegistro(registro)

        if (existente != null) {
            config.auditar(
                tabla = "registros_bpm",
                registroId = registro.id,
                campo = "respuestas",
                valorAnterior = existente.respuestasJson,
                valorNuevo = respuestasJson,
                motivo = "corrección el mismo día",
            )
        }
        sync.encolar("registros_bpm", registro.id, "actualizar")
        return registro.id
    }

    /**
     * Cierra los registros de BPM con más de 24 horas (RF-BPM-02).
     *
     * Un registro que se puede editar para siempre no sirve como evidencia
     * ante una inspección. Desde aquí solo se pueden añadir anotaciones.
     */
    suspend fun cerrarRegistrosBpmVencidos(): Int =
        bd.bpm().cerrarVencidos(Instant.now().minus(24, ChronoUnit.HOURS))

    suspend fun anotarEnRegistroBpm(registroId: String, texto: String) {
        val registro = bd.bpm().registroPorId(registroId) ?: return
        val anotaciones = json.parseToJsonElement(registro.anotacionesJson).jsonArray
        val nuevas = buildJsonArray {
            anotaciones.forEach { add(it) }
            add(
                buildJsonObject {
                    put("fecha", Instant.now().toString())
                    put("texto", texto)
                },
            )
        }
        bd.bpm().guardarRegistro(
            registro.copy(
                anotacionesJson = nuevas.toString(),
                comunes = registro.comunes.tocada(),
            ),
        )
        sync.encolar("registros_bpm", registroId, "actualizar")
    }

    suspend fun todosLosRegistrosBpm(): List<RegistroBpmEntidad> = bd.bpm().todosLosRegistros()

    suspend fun checklists(): List<ChecklistBpmEntidad> = bd.bpm().checklists()

    // ------------------------------------------------------------ laboratorio

    fun observarLaboratorio(): Flow<List<LaboratorioEntidad>> = bd.laboratorio().observarTodos()

    /** Los análisis de un lote, para el reporte y la pantalla de detalle. */
    suspend fun laboratorioDeLote(loteId: String): List<LaboratorioEntidad> =
        bd.laboratorio().deLote(loteId)

    /**
     * Guarda un resultado de laboratorio y aplica RN-15 (cadmio).
     *
     * Si el cadmio supera el límite, el lote queda bloqueado para venta hasta
     * que el usuario lo desbloquee a conciencia.
     */
    suspend fun guardarLaboratorio(
        analisis: String,
        valor: Double,
        loteId: String? = null,
        loteProduccionId: String? = null,
        unidad: String = "mg/kg",
        limite: Double? = null,
        laboratorio: String = "",
        fecha: Instant = Instant.now(),
        archivoId: String? = null,
    ): List<Alerta> {
        val l = LaboratorioEntidad(
            loteId = loteId,
            loteProduccionId = loteProduccionId,
            analisis = analisis,
            valor = valor,
            unidad = unidad,
            limite = limite,
            laboratorio = laboratorio,
            fecha = fecha,
            archivoId = archivoId,
        )
        bd.laboratorio().insertar(l)
        sync.encolar("laboratorios", l.id, "crear")
        config.auditar(
            tabla = "laboratorios",
            registroId = l.id,
            campo = analisis,
            valorAnterior = "",
            valorNuevo = "$valor $unidad",
            motivo = "resultado de $laboratorio",
        )

        val generadas = motor().evaluarLaboratorio(
            ContextoLaboratorio(analisis, valor, unidad),
        )

        if (generadas.any { it.bloquea } && loteId != null) {
            bd.lotes().porId(loteId)?.let { lote ->
                bd.lotes().actualizar(
                    lote.copy(
                        ventaBloqueada = true,
                        motivoBloqueo = "$analisis $valor $unidad supera el límite",
                        comunes = lote.comunes.tocada(),
                    ),
                )
            }
            sync.encolar("lotes", loteId, "actualizar")
        }
        return alertas.registrar(generadas, loteId, loteProduccionId)
    }

    /** Levanta el bloqueo de venta. Queda registrado quién y por qué. */
    suspend fun desbloquearVenta(loteId: String, motivo: String) {
        val lote = bd.lotes().porId(loteId) ?: return
        bd.lotes().actualizar(
            lote.copy(ventaBloqueada = false, comunes = lote.comunes.tocada()),
        )
        config.auditar(
            tabla = "lotes",
            registroId = loteId,
            campo = "venta_bloqueada",
            valorAnterior = "true",
            valorNuevo = "false",
            motivo = motivo,
        )
        sync.encolar("lotes", loteId, "actualizar")
    }

    // ------------------------------------------------------------ sacos

    suspend fun crearSaco(loteId: String, pesoKg: Double, ubicacion: String = ""): String {
        val existentes = bd.sacos().deLote(loteId)
        val lote = bd.lotes().porId(loteId)
            ?: throw IllegalArgumentException("El lote $loteId no existe")
        val codigo = "${lote.codigo}-S${(existentes.size + 1).toString().padStart(2, '0')}"

        val saco = SacoEntidad(
            loteId = loteId,
            codigoQr = codigo,
            pesoKg = pesoKg,
            ubicacion = ubicacion,
        )
        bd.sacos().insertar(saco)
        sync.encolar("sacos", saco.id, "crear")
        registrarMovimiento("grano_seco", "entrada", pesoKg, referencia = codigo)
        return codigo
    }

    fun observarSacos(loteId: String? = null): Flow<List<SacoEntidad>> =
        if (loteId == null) bd.sacos().observarTodos() else bd.sacos().observarDeLote(loteId)

    /** Inspección del almacén, con RN-11. */
    suspend fun inspeccionarAlmacen(
        loteId: String? = null,
        humedadRelativa: Double? = null,
        temperatura: Double? = null,
        plagas: Boolean = false,
        moho: Boolean = false,
        olor: String = "",
        fotoId: String? = null,
        notas: String = "",
    ): List<Alerta> {
        val i = InspeccionAlmacenEntidad(
            loteId = loteId,
            fecha = Instant.now(),
            humedadRelativa = humedadRelativa,
            temperatura = temperatura,
            plagas = plagas,
            moho = moho,
            olor = olor,
            fotoId = fotoId,
            notas = notas,
        )
        bd.inspecciones().insertar(i)
        sync.encolar("inspecciones_almacen", i.id, "crear")

        return alertas.registrar(
            motor().evaluarAlmacen(ContextoAlmacen(humedadRelativa, moho, plagas)),
            loteId = loteId,
        )
    }

    // ------------------------------------------------------------ fotos

    suspend fun guardarFoto(
        rutaLocal: String,
        etapa: String = "",
        loteId: String? = null,
        loteProduccionId: String? = null,
        analisisIa: Map<String, String> = emptyMap(),
        etiquetaUsuario: String = "",
        aptaDataset: Boolean = false,
        latitud: Double? = null,
        longitud: Double? = null,
    ): String {
        val foto = FotoEntidad(
            rutaLocal = rutaLocal,
            etapa = etapa,
            loteId = loteId,
            loteProduccionId = loteProduccionId,
            analisisIaJson = json.encodeToString(analisisIa),
            etiquetaUsuario = etiquetaUsuario,
            aptaDataset = aptaDataset,
            latitud = latitud,
            longitud = longitud,
        )
        bd.fotos().insertar(foto)
        sync.encolar("fotos", foto.id, "crear")
        // La foto en sí viaja aparte y solo con WiFi (RF-SYN-04).
        sync.encolar(
            tabla = "fotos",
            registroId = foto.id,
            operacion = "subir_foto",
            cargaJson = """{"ruta":"$rutaLocal"}""",
            esArchivo = true,
        )
        return foto.id
    }

    /** Las fotos que el usuario corrigió y sirven para reentrenar (§8.3 ERS). */
    suspend fun fotosParaDataset(etapa: String? = null): List<FotoEntidad> =
        if (etapa == null) bd.fotos().paraDataset() else bd.fotos().paraDatasetDeEtapa(etapa)

    // ------------------------------------------------------------ panel

    /** Indicadores de la pantalla de tablero (RF-TAB-02). */
    suspend fun indicadores(): Map<String, String> {
        val lotes = bd.lotes().todos()
        val abiertas = alertas.abiertas()
        val pruebas = bd.pruebasCorte().completas()
        val secados = bd.secados().terminados()

        val fermentados = pruebas.mapNotNull { p ->
            runCatching {
                json.parseToJsonElement(p.porcentajesJson).jsonObject["fermentado_total"]
                    ?.jsonPrimitive?.content?.toDouble()
            }.getOrNull()
        }

        val temperaturas = bd.fermentaciones().todasEnCurso()
            .flatMap { bd.fermentaciones().lecturas(it.id) }
            .mapNotNull { it.tempC }

        val diasSecado = secados.mapNotNull { s ->
            s.fin?.let { Duration.between(s.inicio, it).toDays() }
        }

        return buildMap {
            put("Lotes registrados", lotes.size.toString())
            put("Lotes activos", lotes.count { !it.estado.estaCerrado }.toString())
            put("Alertas abiertas", abiertas.size.toString())
            put("Pruebas de corte", pruebas.size.toString())
            put(
                "Fermentado promedio",
                if (fermentados.isEmpty()) "sin datos"
                else "${formatear(fermentados.average(), 1)} %",
            )
            put(
                "Temperatura máxima de fermentación",
                temperaturas.maxOrNull()?.let { "${formatear(it, 1)} °C" } ?: "sin datos",
            )
            put(
                "Días de secado (promedio)",
                if (diasSecado.isEmpty()) "sin datos"
                else formatear(diasSecado.average(), 1),
            )
        }
    }

    /**
     * Las series que dibuja el panel (RF-TAB-03).
     *
     * Se calculan aquí y no en la pantalla por el mismo motivo que todo lo
     * demás: si la pantalla hablara con Room, cada gráfico tendría que
     * acordarse de filtrar los registros eliminados y las pruebas parciales.
     */
    suspend fun seriesDelPanel(): SeriesPanel {
        val lotes = bd.lotes().todos().associateBy { it.id }

        // Solo las pruebas completas definen el grado del lote: una prueba
        // parcial del día 5 es orientativa y mezclarla falsearía la media.
        val pruebas = bd.pruebasCorte().completas().filter { !it.esParcial }

        val fermentadoPorLote = pruebas.mapNotNull { p ->
            val codigo = lotes[p.loteId]?.codigo ?: return@mapNotNull null
            val pct = runCatching {
                json.parseToJsonElement(p.porcentajesJson).jsonObject["fermentado_total"]
                    ?.jsonPrimitive?.content?.toDouble()
            }.getOrNull() ?: return@mapNotNull null
            PuntoSerie(codigo, pct)
        }

        val defectosPorClase = mutableMapOf<String, Double>()
        for (p in pruebas) {
            val objeto = runCatching {
                json.parseToJsonElement(p.porcentajesJson).jsonObject
            }.getOrNull() ?: continue
            for (clase in clasesDefecto) {
                val valor = objeto[clase]?.jsonPrimitive?.content?.toDoubleOrNull() ?: continue
                defectosPorClase[clase] = (defectosPorClase[clase] ?: 0.0) + valor
            }
        }
        val defectos = defectosPorClase
            .mapValues { (_, suma) -> if (pruebas.isEmpty()) 0.0 else suma / pruebas.size }
            .filterValues { it > 0 }
            .map { (clase, media) -> PuntoSerie(clase, media) }
            .sortedByDescending { it.valor }

        // La curva de la fermentación en curso más reciente: es la que el
        // usuario está mirando de verdad cuando abre el panel.
        val enCurso = bd.fermentaciones().todasEnCurso().maxByOrNull { it.inicio }
        val curva = enCurso?.let { f ->
            bd.fermentaciones().lecturas(f.id)
                .filter { it.tempC != null }
                .sortedBy { it.fechaHora }
                .map { l ->
                    PuntoSerie(
                        etiqueta = formatear(
                            Duration.between(f.inicio, l.fechaHora).toMinutes() / 60.0,
                            0,
                        ),
                        valor = l.tempC ?: 0.0,
                    )
                }
        }.orEmpty()

        val gradoPorLote = pruebas.groupingBy { it.resultado }.eachCount()
            .map { (grado, cuantos) -> PuntoSerie(grado, cuantos.toDouble()) }
            .sortedByDescending { it.valor }

        return SeriesPanel(
            fermentadoPorLote = fermentadoPorLote,
            defectosPromedio = defectos,
            curvaFermentacion = curva,
            loteDeLaCurva = enCurso?.let { lotes[it.loteId]?.codigo }.orEmpty(),
            gradoPorLote = gradoPorLote,
        )
    }

    /** Lo que hay que hacer hoy, juntando todos los lotes (RF-TAB-01). */
    suspend fun tareasDeHoy(): List<TareaDelDia> {
        val lotes = bd.lotes().todos()
        val tareas = mutableListOf<TareaDelDia>()
        val ahora = Instant.now()
        val umbrales = config.umbrales()

        for (lote in lotes.filter { !it.estado.estaCerrado }) {
            // Mazorcas en reposo que ya toca abrir.
            if (lote.estado.ordinal <= EstadoLote.REPOSO.ordinal) {
                val recepcion = bd.recepciones().deLote(lote.id)
                val plan = recepcion?.fechaAperturaPlan
                if (plan != null && !plan.isAfter(ahora)) {
                    val dias = Duration.between(plan, ahora).toDays()
                    tareas += TareaDelDia(
                        titulo = "Abrir las mazorcas",
                        detalle = if (dias > 0) {
                            "Debían abrirse hace $dias día(s)"
                        } else {
                            "Toca hoy, según los ${recepcion.diasReposo} días de reposo"
                        },
                        loteCodigo = lote.codigo,
                        loteId = lote.id,
                        urgente = dias > 1,
                        destino = "apertura",
                    )
                }
            }

            // Fermentación: volteo pendiente y lectura del día.
            val ferm = bd.fermentaciones().enCursoDeLote(lote.id)
            if (ferm != null) {
                val ultimo = bd.fermentaciones().ultimoVolteo(ferm.id)
                val desde = ultimo?.fechaHora ?: ferm.inicio
                val horas = Duration.between(desde, ahora).toHours()
                if (horas >= umbrales["volteo_horas"]) {
                    tareas += TareaDelDia(
                        titulo = "Voltear la masa",
                        detalle = "Van $horas horas desde el último volteo",
                        loteCodigo = lote.codigo,
                        loteId = lote.id,
                        urgente = horas >= umbrales["volteo_horas_alerta"],
                        destino = "fermentacion",
                    )
                }
                tareas += TareaDelDia(
                    titulo = "Medir la temperatura",
                    detalle = "Lleva ${Duration.between(ferm.inicio, ahora).toHours()} " +
                        "horas de fermentación",
                    loteCodigo = lote.codigo,
                    loteId = lote.id,
                    destino = "fermentacion",
                )
            }

            // Secado en curso: jornada del día.
            bd.secados().enCursoDeLote(lote.id)?.let { secado ->
                tareas += TareaDelDia(
                    titulo = "Registrar la jornada de secado",
                    detalle = "Día ${Duration.between(secado.inicio, ahora).toDays() + 1} " +
                        "de secado",
                    loteCodigo = lote.codigo,
                    loteId = lote.id,
                    destino = "secado",
                )
            }
        }

        // Inspección de almacén vencida.
        val dias = umbrales.entero("almacen_dias_inspeccion")
        val ultima = bd.inspecciones().ultima()
        if (bd.sacos().cuantos() > 0 &&
            (ultima == null || Duration.between(ultima.fecha, ahora).toDays() >= dias)
        ) {
            tareas += TareaDelDia(
                titulo = "Inspeccionar el almacén",
                detalle = "Revisar humedad, olor, plagas y moho en los sacos",
                loteCodigo = "Almacén",
                destino = "almacen",
            )
        }

        // BPM del día sin diligenciar.
        for (c in bd.bpm().checklistsPorFrecuencia("diario")) {
            if (registroDeHoy(c.id) == null) {
                tareas += TareaDelDia(
                    titulo = c.nombre,
                    detalle = "Checklist de buenas prácticas de hoy",
                    loteCodigo = "BPM",
                    destino = "bpm",
                )
            }
        }

        return tareas.sortedByDescending { it.urgente }
    }
}

/** Un punto de una serie del panel: una etiqueta y un número. */
data class PuntoSerie(val etiqueta: String, val valor: Double)

/** Todo lo que dibuja el panel, calculado de una vez (RF-TAB-03). */
data class SeriesPanel(
    val fermentadoPorLote: List<PuntoSerie> = emptyList(),
    val defectosPromedio: List<PuntoSerie> = emptyList(),
    val curvaFermentacion: List<PuntoSerie> = emptyList(),
    val loteDeLaCurva: String = "",
    val gradoPorLote: List<PuntoSerie> = emptyList(),
)

/** Existencia actual de un artículo. */
data class Existencia(
    val item: String,
    val cantidad: Double,
    val unidad: String,
    val minimo: Double? = null,
) {
    /** RF-INV-02: queda poco y hay que reponer. */
    val bajoMinimo: Boolean get() = minimo != null && cantidad < minimo
}

/** Lo que cuesta un lote de producción y lo que sale cada barra (RF-COS-02). */
data class ResumenCostos(
    val totalUsd: Double,
    val porConcepto: Map<String, Double>,
    val barras: Int = 0,
    val kgGrano: Double = 0.0,
) {
    val costoPorBarra: Double? get() = if (barras > 0) totalUsd / barras else null
    val costoPorKgGrano: Double? get() = if (kgGrano > 0) totalUsd / kgGrano else null
}

/** Una tarea del día para la pantalla de inicio (RF-TAB-01). */
data class TareaDelDia(
    val titulo: String,
    val detalle: String,
    val loteCodigo: String,
    val loteId: String? = null,
    val urgente: Boolean = false,
    val destino: String? = null,
)

/** El registro de BPM ya se cerró y solo admite anotaciones (RF-BPM-02). */
class RegistroBpmCerrado(val fecha: Instant) : IllegalStateException(
    "Este registro se cerró pasadas 24 horas y ya no se puede cambiar. " +
        "Puedes añadir una anotación explicando lo que corresponda.",
)
