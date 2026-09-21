package ec.cacaotrace.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ec.cacaotrace.CacaoTraceApp
import ec.cacaotrace.ContenedorApp
import ec.cacaotrace.trabajo.TrabajoSincronizacion

class ActividadPrincipal : ComponentActivity() {

    private val pedirPermisoNotificaciones = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Si lo niega, la app sigue funcionando: los avisos salen en pantalla. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val contenedor = (application as CacaoTraceApp).contenedor
        TrabajoSincronizacion.programar(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !contenedor.notificaciones.hayPermiso()
        ) {
            pedirPermisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            TemaCacaoTrace {
                AppCacaoTrace(contenedor)
            }
        }
    }
}

/**
 * Armazón de la app: cinco secciones principales.
 *
 * Cinco y no más: con una barra de seis o siete, los destinos se vuelven
 * demasiado estrechos para tocarlos con el dedo. Todo lo demás cuelga de
 * estas cinco pantallas.
 */
@Composable
fun AppCacaoTrace(contenedor: ContenedorApp) {
    val navegacion = rememberNavController()

    Scaffold(
        bottomBar = { BarraInferior(navegacion) },
    ) { relleno ->
        GrafoNavegacion(
            navegacion = navegacion,
            contenedor = contenedor,
            modifier = Modifier.padding(relleno),
        )
    }
}

private data class Seccion(
    val ruta: String,
    val etiqueta: String,
    val icono: ImageVector,
)

private val secciones = listOf(
    Seccion(Rutas.INICIO, "Hoy", Icons.Default.Today),
    Seccion(Rutas.LOTES, "Lotes", Icons.Default.Inventory2),
    Seccion(Rutas.PRODUCCION, "Chocolate", Icons.Default.Cookie),
    Seccion(Rutas.PANEL, "Panel", Icons.Default.Insights),
    Seccion(Rutas.AJUSTES, "Ajustes", Icons.Default.Settings),
)

@Composable
private fun BarraInferior(navegacion: NavHostController) {
    val entrada by navegacion.currentBackStackEntryAsState()
    val destinoActual = entrada?.destination

    // La barra solo se muestra en las cinco secciones principales: en una
    // pantalla de detalle ocuparía espacio sin aportar nada.
    val enSeccionPrincipal = secciones.any { seccion ->
        destinoActual?.hierarchy?.any { it.route == seccion.ruta } == true
    }
    if (!enSeccionPrincipal) return

    NavigationBar {
        secciones.forEach { seccion ->
            val seleccionada =
                destinoActual?.hierarchy?.any { it.route == seccion.ruta } == true
            NavigationBarItem(
                selected = seleccionada,
                onClick = {
                    navegacion.navigate(seccion.ruta) {
                        popUpTo(navegacion.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                // Icono Y texto siempre: el icono solo no se entiende (§7).
                icon = { Icon(seccion.icono, contentDescription = null) },
                label = { Text(seccion.etiqueta) },
                alwaysShowLabel = true,
            )
        }
    }
}
