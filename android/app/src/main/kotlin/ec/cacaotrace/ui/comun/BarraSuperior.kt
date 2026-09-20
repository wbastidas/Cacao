package ec.cacaotrace.ui.comun

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

/** Barra superior con botón de volver, igual en todas las pantallas de detalle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarraSuperior(
    titulo: String,
    navegacion: NavHostController? = null,
    acciones: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(titulo) },
        navigationIcon = {
            if (navegacion != null) {
                IconButton(onClick = { navegacion.popBackStack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                    )
                }
            }
        },
        actions = { acciones() },
    )
}
