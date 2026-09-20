package ec.cacaotrace.trabajo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import ec.cacaotrace.CacaoTraceApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Muestra el recordatorio cuando llega su hora. */
class ReceptorRecordatorio : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intencion: Intent) {
        val servicio = ServicioNotificaciones(contexto)
        if (!servicio.hayPermiso()) return

        val id = intencion.getIntExtra(ServicioNotificaciones.EXTRA_ID, 0)
        val titulo = intencion.getStringExtra(ServicioNotificaciones.EXTRA_TITULO).orEmpty()
        val cuerpo = intencion.getStringExtra(ServicioNotificaciones.EXTRA_CUERPO).orEmpty()
        val esAlerta = intencion.getBooleanExtra(ServicioNotificaciones.EXTRA_ES_ALERTA, false)
        if (titulo.isBlank()) return

        NotificationManagerCompat.from(contexto)
            .notify(id, servicio.construir(titulo, cuerpo, esAlerta))
    }
}

/**
 * Vuelve a programar los recordatorios cuando el teléfono se reinicia.
 *
 * Las alarmas no sobreviven al apagado. Sin esto, un productor que apaga el
 * teléfono por la noche se quedaría sin el aviso de voltear.
 */
class ReceptorArranque : BroadcastReceiver() {
    override fun onReceive(contexto: Context, intencion: Intent) {
        if (intencion.action != Intent.ACTION_BOOT_COMPLETED) return
        val resultado = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = contexto.applicationContext as? CacaoTraceApp ?: return@launch
                app.contenedor.reprogramarAvisos()
            } finally {
                resultado.finish()
            }
        }
    }
}
