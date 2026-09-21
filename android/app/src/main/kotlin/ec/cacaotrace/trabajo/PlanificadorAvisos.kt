package ec.cacaotrace.trabajo

import ec.cacaotrace.datos.bd.BaseDatos
import ec.cacaotrace.nucleo.modelo.EstadoLote
import ec.cacaotrace.nucleo.reglas.Umbrales
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Programa los recordatorios a partir del estado real de la base.
 *
 * Se recalcula todo cada vez, en vez de ir añadiendo avisos sueltos: así no
 * quedan recordatorios huérfanos de un lote que ya se cerró, y da igual si el
 * teléfono estuvo apagado tres días.
 */
class PlanificadorAvisos(
    private val bd: BaseDatos,
    private val notificaciones: ServicioNotificaciones,
) {
    /**
     * Vuelve a programar todos los recordatorios pendientes.
     *
     * Devuelve cuántos quedaron programados, para poder mostrarlo en Ajustes.
     */
    suspend fun reprogramar(umbrales: Umbrales): Int {
        var programados = 0
        val ahora = Instant.now()

        for (lote in bd.lotes().todos().filter { !it.estado.estaCerrado }) {
            // Apertura de las mazorcas (RF-REC-05).
            if (lote.estado.ordinal <= EstadoLote.REPOSO.ordinal) {
                val plan = bd.recepciones().deLote(lote.id)?.fechaAperturaPlan
                if (plan != null && plan.isAfter(ahora)) {
                    notificaciones.programarApertura(lote.id, lote.codigo, plan)
                    programados++
                }
            }

            // Próximo volteo (RN-03).
            bd.fermentaciones().enCursoDeLote(lote.id)?.let { ferm ->
                val desde = bd.fermentaciones().ultimoVolteo(ferm.id)?.fechaHora ?: ferm.inicio
                val proximo = desde.plus(umbrales.entero("volteo_horas").toLong(), ChronoUnit.HOURS)
                if (proximo.isAfter(ahora)) {
                    notificaciones.programarVolteo(ferm.id, lote.codigo, proximo)
                    programados++
                }
            }

            // Inspección del almacén (RF-ALM-02).
            if (lote.estado == EstadoLote.ALMACENADO) {
                val desde = bd.inspecciones().ultimaDeLote(lote.id)?.fecha ?: ahora
                val proxima = desde.plus(
                    umbrales.entero("almacen_dias_inspeccion").toLong(),
                    ChronoUnit.DAYS,
                )
                if (proxima.isAfter(ahora)) {
                    notificaciones.programarInspeccion(lote.id, lote.codigo, proxima)
                    programados++
                }
            }
        }
        return programados
    }
}
