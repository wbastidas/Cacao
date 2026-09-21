package ec.cacaotrace.trabajo

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ec.cacaotrace.R
import ec.cacaotrace.ui.ActividadPrincipal
import java.time.Instant
import kotlin.math.absoluteValue

/**
 * Notificaciones locales programadas (RF-ALE-02).
 *
 * Son LOCALES a propósito: se programan en el teléfono y suenan aunque no haya
 * señal, que es justo lo que pasa en la finca. No hacen falta funciones en la
 * nube ni push, y por eso la app opera con el plan gratuito.
 */
class ServicioNotificaciones(private val contexto: Context) {

    private val gestorAlarmas: AlarmManager
        get() = contexto.getSystemService(AlarmManager::class.java)

    fun crearCanales() {
        val gestor = contexto.getSystemService(NotificationManager::class.java) ?: return
        // Un canal por tipo para que el usuario pueda silenciar los
        // recordatorios sin perder las alertas importantes.
        gestor.createNotificationChannel(
            NotificationChannel(
                CANAL_RECORDATORIOS,
                "Recordatorios",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Volteos, apertura de mazorcas, refinado e inspecciones"
            },
        )
        gestor.createNotificationChannel(
            NotificationChannel(
                CANAL_ALERTAS,
                "Alertas",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Cuando una lectura se sale de los umbrales"
            },
        )
    }

    fun hayPermiso(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(
                contexto,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * Programa un aviso para un momento futuro.
     *
     * Si el momento ya pasó no se programa nada: avisar a la vez de algo que
     * tocaba ayer y de algo que toca hoy sería ruido.
     */
    fun programar(
        id: Int,
        titulo: String,
        cuerpo: String,
        cuando: Instant,
        esAlerta: Boolean = false,
    ) {
        if (!cuando.isAfter(Instant.now())) return

        val intencion = Intent(contexto, ReceptorRecordatorio::class.java).apply {
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_TITULO, titulo)
            putExtra(EXTRA_CUERPO, cuerpo)
            putExtra(EXTRA_ES_ALERTA, esAlerta)
        }
        val pendiente = PendingIntent.getBroadcast(
            contexto,
            id,
            intencion,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Inexacto a propósito: un recordatorio de volteo no necesita el minuto
        // exacto, y el modo exacto gasta batería y exige un permiso especial
        // desde Android 12 que el usuario tendría que conceder a mano.
        gestorAlarmas.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            cuando.toEpochMilli(),
            pendiente,
        )
    }

    /** Recordatorio de volteo (RN-03). */
    fun programarVolteo(fermentacionId: String, codigoLote: String, proximoVolteo: Instant) =
        programar(
            id = idDe("volteo", fermentacionId),
            titulo = "Toca voltear el lote $codigoLote",
            cuerpo = "Mueve toda la masa, incluidas las esquinas, y registra el volteo " +
                "en la app.",
            cuando = proximoVolteo,
        )

    /** Aviso del día de apertura de las mazorcas (RF-REC-05). */
    fun programarApertura(loteId: String, codigoLote: String, fechaApertura: Instant) {
        // A las 7 de la mañana: la apertura se hace temprano.
        val aLasSiete = fechaApertura.atZone(java.time.ZoneId.systemDefault())
            .withHour(7).withMinute(0).withSecond(0).toInstant()
        programar(
            id = idDe("apertura", loteId),
            titulo = "Hoy toca abrir las mazorcas del lote $codigoLote",
            cuerpo = "Terminaron los días de reposo. Abre, pesa la baba y empieza la " +
                "fermentación.",
            cuando = aLasSiete,
        )
    }

    /** Inspección periódica del almacén (RF-ALM-02). */
    fun programarInspeccion(loteId: String, codigoLote: String, cuando: Instant) =
        programar(
            id = idDe("inspeccion", loteId),
            titulo = "Inspecciona los sacos del lote $codigoLote",
            cuerpo = "Revisa humedad, olor, plagas y moho.",
            cuando = cuando,
        )

    /** Avisa ahora mismo de una alerta que acaba de dispararse. */
    fun avisarAhora(titulo: String, cuerpo: String, esAlerta: Boolean = true) {
        if (!hayPermiso()) return
        NotificationManagerCompat.from(contexto).notify(
            (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            construir(titulo, cuerpo, esAlerta),
        )
    }

    fun construir(titulo: String, cuerpo: String, esAlerta: Boolean) =
        NotificationCompat.Builder(
            contexto,
            if (esAlerta) CANAL_ALERTAS else CANAL_RECORDATORIOS,
        )
            // Silueta monocroma, no el icono del lanzador: Android pinta de
            // blanco entero el icono pequeño, así que uno a color se vería
            // como una mancha sin forma.
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(cuerpo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(cuerpo))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    contexto,
                    0,
                    Intent(contexto, ActividadPrincipal::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()

    fun cancelar(id: Int) {
        val pendiente = PendingIntent.getBroadcast(
            contexto,
            id,
            Intent(contexto, ReceptorRecordatorio::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pendiente?.let { gestorAlarmas.cancel(it) }
    }

    companion object {
        const val CANAL_RECORDATORIOS = "recordatorios"
        const val CANAL_ALERTAS = "alertas"
        const val EXTRA_ID = "id"
        const val EXTRA_TITULO = "titulo"
        const val EXTRA_CUERPO = "cuerpo"
        const val EXTRA_ES_ALERTA = "es_alerta"

        /**
         * Identificador estable por tipo y registro, para poder reemplazar un
         * recordatorio en vez de acumular diez iguales.
         */
        fun idDe(tipo: String, registroId: String): Int =
            "$tipo:$registroId".hashCode().absoluteValue
    }
}
