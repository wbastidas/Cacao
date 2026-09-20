package ec.cacaotrace

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CacaoTraceApp : Application() {

    lateinit var contenedor: ContenedorApp
        private set

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        contenedor = ContenedorApp(this)

        alcance.launch {
            // Nada de esto puede retrasar el arranque: que falte un modelo o
            // que no haya permiso de notificaciones no impide usar la app.
            contenedor.notificaciones.crearCanales()
            contenedor.sync.soloWifiParaFotos = contenedor.config.subirFotosSoloConWifi()
            contenedor.modelos.cargarTodos()
        }
    }
}
