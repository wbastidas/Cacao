package ec.cacaotrace.datos.repositorios

import androidx.room.withTransaction
import ec.cacaotrace.datos.bd.BaseDatos
import ec.cacaotrace.datos.bd.Comunes
import ec.cacaotrace.datos.bd.entidades.AtemperadoEntidad
import ec.cacaotrace.datos.bd.entidades.DescascarilladoEntidad
import ec.cacaotrace.datos.bd.entidades.EmpaqueEntidad
import ec.cacaotrace.datos.bd.entidades.LoteProduccionEntidad
import ec.cacaotrace.datos.bd.entidades.LoteProduccionOrigenEntidad
import ec.cacaotrace.datos.bd.entidades.MovimientoInventarioEntidad
import ec.cacaotrace.datos.bd.entidades.RefinadoEntidad
import ec.cacaotrace.datos.bd.entidades.TostadoEntidad
import ec.cacaotrace.datos.sync.ServicioSincronizacion
import ec.cacaotrace.nucleo.calculo.CalculadoraReceta
import ec.cacaotrace.nucleo.calculo.Receta
import ec.cacaotrace.nucleo.modelo.Codigos
import ec.cacaotrace.nucleo.modelo.EstadoSync
import ec.cacaotrace.nucleo.modelo.MetodoAtemperado
import ec.cacaotrace.nucleo.reglas.Alerta
import ec.cacaotrace.nucleo.reglas.ContextoAtemperado
import ec.cacaotrace.nucleo.reglas.ContextoTostado
import ec.cacaotrace.nucleo.reglas.MotorReglas
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/** Repositorio del lote de producción: del grano seco a la barra empacada. */
class RepositorioProduccion(
    private val bd: BaseDatos,
    private val sync: ServicioSincronizacion,
    private val config: RepositorioConfiguracion,
    private val alertas: RepositorioAlertas,
) {
    private val json = Json { encodeDefaults = true }
    private val dao get() = bd.produccion()

    private suspend fun motor() = MotorReglas(config.umbrales())

    fun observar(): Flow<List<LoteProduccionEntidad>> = dao.observarTodas()

    suspend fun porId(id: String): LoteProduccionEntidad? = dao.porId(id)

    /**
     * Crea una tanda a partir de uno o varios lotes de grano (RF-LOT-05).
     *
     * Descuenta el grano del inventario, porque salir de almacén hacia
     * producción es un movimiento real (RF-ALM-03). Y se niega a usar un lote
     * bloqueado por laboratorio: dejar pasar cadmio a producción convertiría
     * un lote perdido en una tanda entera perdida (RN-15).
     */
    suspend fun crear(
        kgPorLote: Map<String, Double>,
        porcentajeCacao: Double = 90.0,
    ): LoteProduccionEntidad {
        require(kgPorLote.isNotEmpty()) { "Hay que indicar al menos un lote de grano" }

        for (loteId in kgPorLote.keys) {
            alertas.bloqueoActivo(loteId)?.let { throw LoteBloqueado(loteId, it.quePaso) }
        }

        val ahora = Instant.now()
        val anio = ahora.atZone(ZoneId.systemDefault()).year
        val codigo = Codigos.siguiente(Codigos.PREFIJO_PRODUCCION, anio, dao.codigosUsados())

        val tanda = LoteProduccionEntidad(
            codigo = codigo,
            fecha = ahora,
            porcentajeCacao = porcentajeCacao,
        )

        bd.withTransaction {
            dao.insertar(tanda)
            for ((loteId, kg) in kgPorLote) {
                dao.insertarOrigen(
                    LoteProduccionOrigenEntidad(
                        loteProduccionId = tanda.id,
                        loteId = loteId,
                        kgUsados = kg,
                    ),
                )
                bd.inventario().insertarMovimiento(
                    MovimientoInventarioEntidad(
                        item = "grano_seco",
                        tipo = "salida",
                        cantidad = kg,
                        fecha = ahora,
                        referencia = codigo,
                    ),
                )
            }
        }
        sync.encolar("lotes_produccion", tanda.id, "crear", """{"codigo":"$codigo"}""")
        return tanda
    }

    suspend fun completa(id: String): ProduccionCompleta? {
        val p = porId(id) ?: return null
        return ProduccionCompleta(
            produccion = p,
            origenes = dao.origenes(id),
            tostado = dao.tostado(id),
            descascarillado = dao.descascarillado(id),
            refinado = dao.refinado(id),
            atemperados = dao.atemperados(id),
            empaque = dao.empaque(id),
        )
    }

    // ------------------------------------------------------------ tostado

    /** Registra el tostado y evalúa la merma (RN-12). */
    suspend fun guardarTostado(
        loteProduccionId: String,
        kgEntrada: Double,
        equipoId: String? = null,
        kgSalida: Double? = null,
        tempC: Double? = null,
        minutos: Int? = null,
        perfil: List<Map<String, Double>> = emptyList(),
        gradoIa: String = "",
        confianzaIa: Double? = null,
        gradoUsuario: String = "",
        modeloVersion: String = "",
        fotoId: String? = null,
        recetaNombre: String = "",
    ): List<Alerta> {
        val existente = dao.tostado(loteProduccionId)
        val tostado = (existente ?: TostadoEntidad(
            loteProduccionId = loteProduccionId,
            fecha = Instant.now(),
        )).copy(
            equipoId = equipoId,
            kgEntrada = kgEntrada,
            kgSalida = kgSalida,
            tempC = tempC,
            minutos = minutos,
            perfilJson = json.encodeToString(perfil),
            gradoIa = gradoIa,
            confianzaIa = confianzaIa,
            gradoUsuario = gradoUsuario,
            modeloVersion = modeloVersion,
            fotoId = fotoId,
            recetaNombre = recetaNombre,
            comunes = (existente?.comunes ?: Comunes()).tocada(),
        )
        dao.guardarTostado(tostado)
        sync.encolar("tostados", tostado.id, "actualizar")

        if (kgSalida == null) return emptyList()
        return alertas.registrar(
            motor().evaluarTostado(ContextoTostado(kgEntrada, kgSalida)),
            loteProduccionId = loteProduccionId,
        )
    }

    // ------------------------------------------------------- descascarillado

    suspend fun guardarDescascarillado(
        loteProduccionId: String,
        kgNibs: Double,
        kgCascarilla: Double,
        notas: String = "",
    ): List<Alerta> {
        val existente = dao.descascarillado(loteProduccionId)
        val d = (existente ?: DescascarilladoEntidad(
            loteProduccionId = loteProduccionId,
            fecha = Instant.now(),
        )).copy(
            kgNibs = kgNibs,
            kgCascarilla = kgCascarilla,
            notas = notas,
            comunes = (existente?.comunes ?: Comunes()).tocada(),
        )
        dao.guardarDescascarillado(d)
        sync.encolar("descascarillados", d.id, "actualizar")

        bd.inventario().insertarMovimiento(
            MovimientoInventarioEntidad(
                item = "nibs",
                tipo = "entrada",
                cantidad = kgNibs,
                fecha = Instant.now(),
                referencia = loteProduccionId,
            ),
        )

        val tostado = dao.tostado(loteProduccionId)
        val kgSalida = tostado?.kgSalida ?: return emptyList()
        return alertas.registrar(
            motor().evaluarTostado(
                ContextoTostado(tostado.kgEntrada, kgSalida, kgNibs, kgCascarilla),
            ),
            loteProduccionId = loteProduccionId,
        )
    }

    // ------------------------------------------------------------ refinado

    /** Calcula la receta para los nibs disponibles (RF-REF-01). */
    fun calcularReceta(
        kgNibs: Double,
        porcentajeCacao: Double = 90.0,
        mantecaExtraPct: Double = 0.0,
        usarLecitina: Boolean = true,
    ): Receta = CalculadoraReceta().calcular(
        kgNibs = kgNibs,
        porcentajeCacao = porcentajeCacao,
        mantecaExtraPct = mantecaExtraPct,
        usarLecitina = usarLecitina,
    )

    suspend fun guardarRefinado(
        loteProduccionId: String,
        inicio: Instant,
        nibsKg: Double,
        azucarKg: Double,
        fin: Instant? = null,
        horas: Double? = null,
        mantecaKg: Double = 0.0,
        lecitinaKg: Double = 0.0,
        momentoAzucar: Instant? = null,
        tempC: Double? = null,
        sensorial: Map<String, Int> = emptyMap(),
        notas: String = "",
    ): String {
        val existente = dao.refinado(loteProduccionId)
        val r = (existente ?: RefinadoEntidad(
            loteProduccionId = loteProduccionId,
            inicio = inicio,
        )).copy(
            inicio = inicio,
            fin = fin,
            horas = horas,
            nibsKg = nibsKg,
            azucarKg = azucarKg,
            mantecaKg = mantecaKg,
            lecitinaKg = lecitinaKg,
            momentoAzucar = momentoAzucar,
            tempC = tempC,
            sensorialJson = json.encodeToString(sensorial),
            notas = notas,
            comunes = (existente?.comunes ?: Comunes()).tocada(),
        )
        dao.guardarRefinado(r)
        sync.encolar("refinados", r.id, "actualizar")

        // El azúcar, la manteca y la lecitina salen del inventario al usarse.
        mapOf("azucar" to azucarKg, "manteca" to mantecaKg, "lecitina" to lecitinaKg)
            .filterValues { it > 0 }
            .forEach { (item, cantidad) ->
                bd.inventario().insertarMovimiento(
                    MovimientoInventarioEntidad(
                        item = item,
                        tipo = "salida",
                        cantidad = cantidad,
                        fecha = Instant.now(),
                        referencia = loteProduccionId,
                    ),
                )
            }
        return r.id
    }

    // ------------------------------------------------------------ atemperado

    /** Registra el atemperado y evalúa RN-13 y RN-14. */
    suspend fun guardarAtemperado(
        loteProduccionId: String,
        metodo: MetodoAtemperado = MetodoAtemperado.SIEMBRA,
        temperaturas: Map<String, Double> = emptyMap(),
        tempCuarto: Double? = null,
        hrCuarto: Double? = null,
        pruebaPapel: Boolean? = null,
        resultadoIa: String = "",
        confianzaIa: Double? = null,
        resultadoUsuario: String = "",
        modeloVersion: String = "",
        fotoId: String? = null,
        diasInspeccion: Int? = null,
    ): List<Alerta> {
        val a = AtemperadoEntidad(
            loteProduccionId = loteProduccionId,
            fecha = Instant.now(),
            metodo = metodo,
            tempsJson = json.encodeToString(temperaturas),
            tempCuarto = tempCuarto,
            hrCuarto = hrCuarto,
            pruebaPapel = pruebaPapel,
            resultadoIa = resultadoIa,
            confianzaIa = confianzaIa,
            resultadoUsuario = resultadoUsuario,
            modeloVersion = modeloVersion,
            fotoId = fotoId,
            diasInspeccion = diasInspeccion,
        )
        dao.insertarAtemperado(a)
        sync.encolar("atemperados", a.id, "crear")

        return alertas.registrar(
            motor().evaluarAtemperado(
                ContextoAtemperado(tempCuarto, hrCuarto, temperaturas["trabajo"]),
            ),
            loteProduccionId = loteProduccionId,
        )
    }

    /** ¿Se puede moldear ahora mismo en estas condiciones? (RF-ATE-02) */
    suspend fun advertenciaDelCuarto(tempCuarto: Double?, hrCuarto: Double?): String? {
        val generadas = motor().evaluarAtemperado(ContextoAtemperado(tempCuarto, hrCuarto))
        return generadas.takeIf { it.isNotEmpty() }
            ?.joinToString(" ") { "${it.quePaso}. ${it.queHacer}" }
    }

    // ------------------------------------------------------------ empaque

    suspend fun guardarEmpaque(
        loteProduccionId: String,
        barras: Int,
        pesoUnitarioG: Double,
        fechaElaboracion: Instant = Instant.now(),
        vidaUtilMeses: Int = 12,
        etiqueta: Map<String, String> = emptyMap(),
    ): String {
        val vencimiento = fechaElaboracion
            .atZone(ZoneId.systemDefault())
            .plusMonths(vidaUtilMeses.toLong())
            .toInstant()

        val e = EmpaqueEntidad(
            loteProduccionId = loteProduccionId,
            barras = barras,
            pesoUnitarioG = pesoUnitarioG,
            fechaElaboracion = fechaElaboracion,
            fechaVencimiento = vencimiento,
            vidaUtilMeses = vidaUtilMeses,
            etiquetaJson = json.encodeToString(etiqueta),
            codigoQr = porId(loteProduccionId)?.codigo.orEmpty(),
        )

        bd.withTransaction {
            dao.guardarEmpaque(e)
            porId(loteProduccionId)?.let { tanda ->
                dao.actualizar(
                    tanda.copy(
                        kgChocolate = barras * pesoUnitarioG / 1000.0,
                        estado = "terminado",
                        comunes = tanda.comunes.copy(
                            modificadoEn = Instant.now(),
                            estadoSync = EstadoSync.PENDIENTE,
                        ),
                    ),
                )
            }
            bd.inventario().insertarMovimiento(
                MovimientoInventarioEntidad(
                    item = "chocolate",
                    tipo = "entrada",
                    cantidad = barras.toDouble(),
                    unidad = "barras",
                    fecha = fechaElaboracion,
                    referencia = loteProduccionId,
                ),
            )
        }
        sync.encolar("empaques", e.id, "crear")
        return e.id
    }

    /** Todo el recorrido de una barra hasta su finca de origen (RF-LOT-04). */
    suspend fun trazabilidadDe(loteProduccionId: String): List<String> {
        val c = completa(loteProduccionId) ?: return emptyList()
        val lineas = mutableListOf("Tanda ${c.produccion.codigo}")

        for (o in c.origenes) {
            val lote = bd.lotes().porId(o.loteId) ?: continue
            val finca = lote.fincaId?.let { config.fincaPorId(it) }
            lineas += buildString {
                append("Lote ${lote.codigo} · ${o.kgUsados} kg")
                finca?.let { append(" · ${it.nombre}") }
            }
        }
        return lineas
    }
}

/** Una tanda con todo lo que cuelga de ella. */
data class ProduccionCompleta(
    val produccion: LoteProduccionEntidad,
    val origenes: List<LoteProduccionOrigenEntidad> = emptyList(),
    val tostado: TostadoEntidad? = null,
    val descascarillado: DescascarilladoEntidad? = null,
    val refinado: RefinadoEntidad? = null,
    val atemperados: List<AtemperadoEntidad> = emptyList(),
    val empaque: EmpaqueEntidad? = null,
) {
    val kgGranoUsado: Double get() = origenes.sumOf { it.kgUsados }
}

/** No se puede usar un lote con la venta bloqueada (RN-15). */
class LoteBloqueado(val loteId: String, val motivo: String) : IllegalStateException(
    "Ese lote está bloqueado y no se puede usar en producción: $motivo",
)
