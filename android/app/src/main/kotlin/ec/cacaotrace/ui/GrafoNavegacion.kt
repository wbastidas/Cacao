package ec.cacaotrace.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import ec.cacaotrace.ContenedorApp
import ec.cacaotrace.ui.pantallas.PantallaAjustes
import ec.cacaotrace.ui.pantallas.PantallaAlertas
import ec.cacaotrace.ui.pantallas.PantallaAlmacen
import ec.cacaotrace.ui.pantallas.PantallaApertura
import ec.cacaotrace.ui.pantallas.PantallaBpm
import ec.cacaotrace.ui.pantallas.PantallaComparar
import ec.cacaotrace.ui.pantallas.PantallaCorrecciones
import ec.cacaotrace.ui.pantallas.PantallaDetalleLote
import ec.cacaotrace.ui.pantallas.PantallaDetalleTanda
import ec.cacaotrace.ui.pantallas.PantallaEscanerQr
import ec.cacaotrace.ui.pantallas.PantallaFermentacion
import ec.cacaotrace.ui.pantallas.PantallaGuias
import ec.cacaotrace.ui.pantallas.PantallaInicio
import ec.cacaotrace.ui.pantallas.PantallaInventario
import ec.cacaotrace.ui.pantallas.PantallaLaboratorio
import ec.cacaotrace.ui.pantallas.PantallaLotes
import ec.cacaotrace.ui.pantallas.PantallaPanel
import ec.cacaotrace.ui.pantallas.PantallaPrivacidad
import ec.cacaotrace.ui.pantallas.PantallaProduccion
import ec.cacaotrace.ui.pantallas.PantallaPruebaCorte
import ec.cacaotrace.ui.pantallas.PantallaRecepcion
import ec.cacaotrace.ui.pantallas.PantallaReporte
import ec.cacaotrace.ui.pantallas.PantallaSecado

@Composable
fun GrafoNavegacion(
    navegacion: NavHostController,
    contenedor: ContenedorApp,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navegacion,
        startDestination = Rutas.INICIO,
        modifier = modifier,
    ) {
        composable(Rutas.INICIO) { PantallaInicio(contenedor, navegacion) }
        composable(Rutas.LOTES) { PantallaLotes(contenedor, navegacion) }
        composable(Rutas.PRODUCCION) { PantallaProduccion(contenedor, navegacion) }
        composable(Rutas.PANEL) { PantallaPanel(contenedor, navegacion) }
        composable(Rutas.AJUSTES) { PantallaAjustes(contenedor, navegacion) }

        composable(
            Rutas.DETALLE_LOTE,
            arguments = listOf(navArgument("loteId") { type = NavType.StringType }),
        ) { entrada ->
            PantallaDetalleLote(
                contenedor,
                navegacion,
                entrada.arguments?.getString("loteId").orEmpty(),
            )
        }

        composable(Rutas.RECEPCION) { entrada ->
            PantallaRecepcion(
                contenedor,
                navegacion,
                entrada.arguments?.getString("loteId").orEmpty(),
            )
        }
        composable(Rutas.APERTURA) { entrada ->
            PantallaApertura(
                contenedor,
                navegacion,
                entrada.arguments?.getString("loteId").orEmpty(),
            )
        }
        composable(Rutas.FERMENTACION) { entrada ->
            PantallaFermentacion(
                contenedor,
                navegacion,
                entrada.arguments?.getString("loteId").orEmpty(),
            )
        }
        composable(Rutas.SECADO) { entrada ->
            PantallaSecado(
                contenedor,
                navegacion,
                entrada.arguments?.getString("loteId").orEmpty(),
            )
        }
        composable(Rutas.PRUEBA_CORTE) { entrada ->
            PantallaPruebaCorte(
                contenedor,
                navegacion,
                entrada.arguments?.getString("loteId").orEmpty(),
            )
        }
        composable(Rutas.REPORTE) { entrada ->
            PantallaReporte(
                contenedor,
                navegacion,
                entrada.arguments?.getString("loteId").orEmpty(),
            )
        }

        composable(
            Rutas.ALMACEN,
            arguments = listOf(
                navArgument("loteId") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entrada ->
            PantallaAlmacen(
                contenedor,
                navegacion,
                entrada.arguments?.getString("loteId")?.takeIf { it.isNotBlank() },
            )
        }

        composable(
            Rutas.ALERTAS,
            arguments = listOf(
                navArgument("loteId") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entrada ->
            PantallaAlertas(
                contenedor,
                navegacion,
                entrada.arguments?.getString("loteId")?.takeIf { it.isNotBlank() },
            )
        }

        composable(Rutas.CORRECCIONES) { PantallaCorrecciones(navegacion) }

        composable(
            Rutas.DETALLE_TANDA,
            arguments = listOf(navArgument("tandaId") { type = NavType.StringType }),
        ) { entrada ->
            PantallaDetalleTanda(
                contenedor,
                navegacion,
                entrada.arguments?.getString("tandaId").orEmpty(),
            )
        }

        composable(Rutas.INVENTARIO) { PantallaInventario(contenedor, navegacion) }
        composable(Rutas.BPM) { PantallaBpm(contenedor, navegacion) }
        composable(Rutas.LABORATORIO) { PantallaLaboratorio(contenedor, navegacion) }
        composable(Rutas.GUIAS) { PantallaGuias(navegacion) }
        composable(Rutas.COMPARAR) { PantallaComparar(contenedor, navegacion) }
        composable(Rutas.PRIVACIDAD) { PantallaPrivacidad(navegacion) }

        composable(Rutas.ESCANER) {
            PantallaEscanerQr(
                alEscanear = { codigo ->
                    navegacion.previousBackStackEntry
                        ?.savedStateHandle?.set("codigoQr", codigo)
                    navegacion.popBackStack()
                },
                alCancelar = { navegacion.popBackStack() },
            )
        }
    }
}
