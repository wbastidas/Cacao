package ec.cacaotrace.ui.comun

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ec.cacaotrace.ContenedorApp
import ec.cacaotrace.ui.ColoresEstado
import kotlinx.coroutines.launch

/**
 * Ofrece sellar las fotos con la posición aproximada (RF-REC-06).
 *
 * El permiso se pide **una sola vez y explicando para qué**, y si el usuario
 * dice que no, no se vuelve a preguntar: la app queda igual de completa sin
 * él. Una app que insiste con un permiso opcional cada vez que se abre una
 * pantalla acaba consiguiendo que le digan que no a todo.
 *
 * Tampoco se muestra nada si el permiso ya está concedido: en ese momento no
 * hay ninguna decisión que tomar, y un aviso permanente sería ruido.
 */
@Composable
fun OfrecerUbicacion(contenedor: ContenedorApp, modifier: Modifier = Modifier) {
    val alcance = rememberCoroutineScope()

    var concedido by remember { mutableStateOf(contenedor.ubicacion.hayPermiso()) }
    var yaSePregunto by remember { mutableStateOf(true) }
    var ocultarAhora by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        yaSePregunto = contenedor.config.leerAjuste(CLAVE_UBICACION_PREGUNTADA) == "si"
    }

    val pedir = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { aceptado ->
        concedido = aceptado
        ocultarAhora = true
        alcance.launch {
            contenedor.config.guardarAjuste(CLAVE_UBICACION_PREGUNTADA, "si")
        }
    }

    if (concedido || yaSePregunto || ocultarAhora) return

    Aviso(
        titulo = "¿Marcar las fotos con el lugar?",
        texto = "Sirve para demostrar de qué finca vino un lote. Es opcional: la " +
            "app funciona igual sin esto, y solo se guarda la zona aproximada, " +
            "no tu posición exacta.",
        color = ColoresEstado.neutro,
        icono = Icons.Default.LocationOn,
        modifier = modifier,
        accion = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { pedir.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                ) { Text("Sí, marcarlas") }
                TextButton(
                    onClick = {
                        ocultarAhora = true
                        alcance.launch {
                            contenedor.config.guardarAjuste(CLAVE_UBICACION_PREGUNTADA, "si")
                        }
                    },
                ) { Text("No hace falta") }
            }
        },
    )
}

/**
 * Estado de la ubicación para la pantalla de Ajustes.
 *
 * Aquí sí tiene sentido mostrarlo siempre: es el sitio donde alguien va a
 * mirar qué permisos dio, y donde puede cambiar de idea.
 */
@Composable
fun EstadoUbicacion(contenedor: ContenedorApp) {
    val alcance = rememberCoroutineScope()
    var concedido by remember { mutableStateOf(contenedor.ubicacion.hayPermiso()) }

    val pedir = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { aceptado ->
        concedido = aceptado
        alcance.launch { contenedor.config.guardarAjuste(CLAVE_UBICACION_PREGUNTADA, "si") }
    }

    FilaDato(
        etiqueta = "Marcar las fotos con el lugar",
        valor = if (concedido) "Activado" else "Desactivado",
        color = if (concedido) ColoresEstado.bien else ColoresEstado.neutro,
    )
    if (!concedido) {
        TextButton(onClick = { pedir.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) {
            Text("Activar")
        }
    } else {
        Aviso(
            texto = "Para desactivarlo, quita el permiso de ubicación a CacaoTrace " +
                "desde los ajustes de Android. Las fotos ya marcadas conservan su " +
                "posición.",
            color = ColoresEstado.neutro,
            icono = Icons.Default.LocationOff,
        )
    }
}

/** Se guarda en la configuración para no volver a preguntar lo mismo. */
private const val CLAVE_UBICACION_PREGUNTADA = "ubicacion_preguntada"
