package ec.cacaotrace.datos.repositorios

import ec.cacaotrace.datos.bd.BaseDatos
import ec.cacaotrace.datos.bd.entidades.AlertaEntidad
import ec.cacaotrace.datos.bd.entidades.CorreccionAplicadaEntidad
import ec.cacaotrace.datos.sync.ServicioSincronizacion
import ec.cacaotrace.nucleo.modelo.EstadoSync
import ec.cacaotrace.nucleo.reglas.Alerta
import ec.cacaotrace.nucleo.reglas.Severidad
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Guarda y consulta las alertas que produce el motor de reglas (RF-ALE-01).
 *
 * La clave está en no repetir: si la fermentación lleva tres días fría, el
 * usuario no necesita doce alertas idénticas, necesita una que siga abierta.
 */
class RepositorioAlertas(
    private val bd: BaseDatos,
    private val sync: ServicioSincronizacion,
) {
    private val dao get() = bd.alertas()

    /**
     * Registra las alertas nuevas y devuelve cuáles se guardaron.
     *
     * Una alerta con la misma clave que otra ya abierta no se duplica: se deja
     * la original. Así la lista de "alertas abiertas" es útil en vez de ser un
     * muro de repeticiones.
     */
    suspend fun registrar(
        alertas: List<Alerta>,
        loteId: String? = null,
        loteProduccionId: String? = null,
    ): List<Alerta> {
        if (alertas.isEmpty()) return emptyList()
        val nuevas = mutableListOf<Alerta>()

        for (a in alertas) {
            val clave = a.claveDeduplicacion(loteId ?: loteProduccionId ?: "-")
            if (dao.abiertaConClave(clave) != null) continue

            val entidad = AlertaEntidad(
                loteId = loteId,
                loteProduccionId = loteProduccionId,
                regla = a.regla,
                severidad = a.severidad.name,
                quePaso = a.quePaso,
                porQueImporta = a.porQueImporta,
                queHacer = a.queHacer,
                correccionCodigo = a.correccion.orEmpty(),
                valorMedido = a.valorMedido,
                valorEsperado = a.valorEsperado,
                fecha = Instant.now(),
                claveDedup = clave,
            )
            dao.insertar(entidad)
            sync.encolar("alertas", entidad.id, "crear")
            nuevas += a
        }
        return nuevas
    }

    fun observarAbiertas(loteId: String? = null): Flow<List<AlertaEntidad>> =
        if (loteId == null) dao.observarAbiertas() else dao.observarAbiertasDeLote(loteId)

    suspend fun deLote(loteId: String): List<AlertaEntidad> = dao.deLote(loteId)

    suspend fun abiertas(): List<AlertaEntidad> = dao.abiertas()

    /** ¿Hay algún bloqueo activo sobre este lote? (RN-08, RN-15) */
    suspend fun bloqueoActivo(loteId: String): AlertaEntidad? = dao.bloqueoActivo(loteId)

    /** El usuario aplicó una corrección y da la alerta por atendida (RF-COR-02). */
    suspend fun atender(
        alertaId: String,
        correccionCodigo: String? = null,
        resultado: String = "",
    ) {
        val alerta = dao.porId(alertaId) ?: return
        dao.actualizar(
            alerta.copy(
                estado = "atendida",
                // Vuelve a la cola: el cambio de estado también hay que subirlo.
                comunes = alerta.comunes.copy(
                    modificadoEn = Instant.now(),
                    estadoSync = EstadoSync.PENDIENTE,
                ),
            ),
        )
        if (correccionCodigo != null) {
            dao.insertarCorreccion(
                CorreccionAplicadaEntidad(
                    alertaId = alertaId,
                    correccionCodigo = correccionCodigo,
                    fecha = Instant.now(),
                    resultado = resultado,
                ),
            )
        }
        sync.encolar("alertas", alertaId, "actualizar")
    }

    /** El usuario decide que la alerta no aplica. */
    suspend fun descartar(alertaId: String) {
        val alerta = dao.porId(alertaId) ?: return
        dao.actualizar(alerta.copy(estado = "descartada", comunes = alerta.comunes.tocada()))
        sync.encolar("alertas", alertaId, "actualizar")
    }

    /** Historial de correcciones aplicadas a un lote (RF-COR-03). */
    suspend fun correccionesDeLote(loteId: String): List<CorreccionAplicadaEntidad> {
        val ids = dao.deLote(loteId).map { it.id }
        return if (ids.isEmpty()) emptyList() else dao.correccionesDe(ids)
    }

    /** Cuántas alertas abiertas hay por severidad, para el resumen de inicio. */
    suspend fun resumenAbiertas(): Map<Severidad, Int> =
        abiertas().groupingBy { runCatching { Severidad.valueOf(it.severidad) }
            .getOrDefault(Severidad.AVISO) }
            .eachCount()
}
