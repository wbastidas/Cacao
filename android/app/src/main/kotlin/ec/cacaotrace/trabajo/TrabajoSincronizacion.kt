package ec.cacaotrace.trabajo

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ec.cacaotrace.CacaoTraceApp
import java.util.concurrent.TimeUnit

/**
 * Vacía la cola de sincronización en segundo plano (RF-SYN-03).
 *
 * Se usa WorkManager y no un servicio propio porque respeta las restricciones
 * de batería de Android: si el usuario está en ahorro de energía, el sistema
 * lo aplaza en vez de gastarle la carga en la finca.
 */
class TrabajoSincronizacion(
    contexto: Context,
    parametros: WorkerParameters,
) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? CacaoTraceApp ?: return Result.success()
        return runCatching { app.contenedor.sync.sincronizar() }
            .fold(
                onSuccess = { Result.success() },
                // Reintentar, no fallar: la cola conserva los datos y el
                // retroceso exponencial del servicio ya evita insistir.
                onFailure = { Result.retry() },
            )
    }

    companion object {
        private const val NOMBRE = "sincronizacion_periodica"

        fun programar(contexto: Context) {
            val trabajo = PeriodicWorkRequestBuilder<TrabajoSincronizacion>(
                repeatInterval = 3,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            WorkManager.getInstance(contexto).enqueueUniquePeriodicWork(
                NOMBRE,
                ExistingPeriodicWorkPolicy.KEEP,
                trabajo,
            )
        }
    }
}
