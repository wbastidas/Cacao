package ec.cacaotrace.ubicacion

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Dónde se tomó una foto. Aproximada a propósito: ver [ServicioUbicacion]. */
data class Ubicacion(val latitud: Double, val longitud: Double)

/**
 * Posición aproximada, para la trazabilidad de origen (RF-REC-06).
 *
 * ## Tres decisiones que conviene explicar
 *
 * **Solo precisión gruesa.** Se pide `ACCESS_COARSE_LOCATION` y nunca la fina.
 * Para decir de qué finca vino un lote sobran unos cientos de metros, y pedir
 * precisión fina sería recoger más de lo que hace falta: el productor trabaja
 * en su propia casa y en su propio taller, y esos puntos no tienen por qué
 * quedar registrados al metro.
 *
 * **Solo la última posición conocida.** No se suscribe a actualizaciones ni
 * enciende el GPS. Encender el receptor para sellar una foto gastaría batería
 * durante toda la jornada de recepción a cambio de un dato que es accesorio
 * (RNF-04).
 *
 * **Nunca falla.** Si no hay permiso, no hay proveedor activo o el sistema no
 * tiene ninguna posición guardada, devuelve `null` y la foto se guarda igual.
 * La ubicación es un extra de la trazabilidad, no un requisito para registrar.
 */
class ServicioUbicacion(private val contexto: Context) {

    fun hayPermiso(): Boolean = ContextCompat.checkSelfPermission(
        contexto,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * La última posición que el sistema tenga guardada, o `null`.
     *
     * Va en el hilo de entrada/salida porque `getLastKnownLocation` toca disco
     * en algunos fabricantes, y bloquear el hilo principal mientras el usuario
     * acaba de tomar una foto se nota.
     */
    suspend fun actual(): Ubicacion? = withContext(Dispatchers.IO) {
        if (!hayPermiso()) return@withContext null

        val manejador = contexto.getSystemService(LocationManager::class.java)
            ?: return@withContext null

        // Se prueban en orden de menor a mayor coste: el fusionado y el de red
        // se resuelven con antenas y WiFi, sin encender el receptor de GPS.
        val proveedores = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }

        for (proveedor in proveedores) {
            val posicion = runCatching {
                // El permiso ya se comprobó arriba, pero el sistema puede
                // revocarlo mientras la app corre; de ahí el runCatching.
                if (manejador.isProviderEnabled(proveedor)) {
                    manejador.getLastKnownLocation(proveedor)
                } else {
                    null
                }
            }.getOrNull()

            if (posicion != null) {
                return@withContext Ubicacion(posicion.latitude, posicion.longitude)
            }
        }
        null
    }
}
