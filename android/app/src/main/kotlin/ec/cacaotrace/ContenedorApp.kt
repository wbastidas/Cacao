package ec.cacaotrace

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import ec.cacaotrace.datos.bd.BaseDatos
import ec.cacaotrace.datos.repositorios.RepositorioAlertas
import ec.cacaotrace.datos.repositorios.RepositorioApoyo
import ec.cacaotrace.datos.repositorios.RepositorioConfiguracion
import ec.cacaotrace.datos.repositorios.RepositorioLotes
import ec.cacaotrace.datos.repositorios.RepositorioProduccion
import ec.cacaotrace.datos.sync.ServicioSincronizacion
import ec.cacaotrace.datos.sync.SincronizadorLocal
import ec.cacaotrace.ia.ServicioModelos
import ec.cacaotrace.trabajo.PlanificadorAvisos
import ec.cacaotrace.trabajo.ServicioNotificaciones

/**
 * Punto único donde se arma la app.
 *
 * Es inyección de dependencias a mano, sin Hilt ni Koin. Para un módulo único
 * como este, una clase de 60 líneas hace el mismo trabajo sin añadir
 * procesamiento de anotaciones, y se lee de arriba abajo: no hay que ir a
 * buscar qué módulo provee qué.
 *
 * Tenerlo en un solo sitio evita además que dos pantallas creen cada una su
 * instancia de Room y acaben peleándose por el mismo archivo.
 */
class ContenedorApp(private val contexto: Context) {

    val bd: BaseDatos by lazy { BaseDatos.obtener(contexto) }

    val sync: ServicioSincronizacion by lazy {
        ServicioSincronizacion(
            bd = bd,
            remoto = SincronizadorLocal(),
            hayConexion = { hayRed(exigirWifi = false) },
            hayWifi = { hayRed(exigirWifi = true) },
        )
    }

    val config: RepositorioConfiguracion by lazy {
        RepositorioConfiguracion(bd, sync, contexto)
    }

    val alertas: RepositorioAlertas by lazy { RepositorioAlertas(bd, sync) }

    val lotes: RepositorioLotes by lazy { RepositorioLotes(bd, sync, config, alertas) }

    val produccion: RepositorioProduccion by lazy {
        RepositorioProduccion(bd, sync, config, alertas)
    }

    val apoyo: RepositorioApoyo by lazy { RepositorioApoyo(bd, sync, config, alertas) }

    val modelos: ServicioModelos by lazy { ServicioModelos(contexto) }

    val notificaciones: ServicioNotificaciones by lazy { ServicioNotificaciones(contexto) }

    val planificador: PlanificadorAvisos by lazy {
        PlanificadorAvisos(bd, notificaciones)
    }

    /**
     * Recalcula todos los recordatorios a partir del estado de la base.
     *
     * Se llama al abrir la app: así los avisos siguen siendo correctos aunque
     * el teléfono haya estado apagado o el usuario haya cambiado un umbral.
     */
    suspend fun reprogramarAvisos(): Int = planificador.reprogramar(config.umbrales())

    private fun hayRed(exigirWifi: Boolean): Boolean {
        val manejador = contexto.getSystemService(ConnectivityManager::class.java)
            ?: return false
        val red = manejador.activeNetwork ?: return false
        val capacidades = manejador.getNetworkCapabilities(red) ?: return false
        if (!capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return if (exigirWifi) {
            capacidades.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capacidades.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            true
        }
    }
}
