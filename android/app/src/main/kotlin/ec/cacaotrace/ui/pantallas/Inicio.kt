package ec.cacaotrace.ui.pantallas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ec.cacaotrace.ContenedorApp
import ec.cacaotrace.datos.repositorios.TareaDelDia
import ec.cacaotrace.ui.ColoresEstado
import ec.cacaotrace.ui.Rutas
import ec.cacaotrace.ui.comun.Aviso
import ec.cacaotrace.ui.comun.BarraSuperior
import kotlinx.coroutines.launch

/**
 * Pantalla de inicio: "¿Qué hago hoy?" (RF-TAB-01).
 *
 * Es la pantalla que el usuario abre veinte veces al día. Tiene que responder
 * de un vistazo a tres preguntas: qué me toca hacer, qué está fallando y si
 * mis datos están a salvo.
 */
@Composable
fun PantallaInicio(contenedor: ContenedorApp, navegacion: NavHostController) {
    val alcance = rememberCoroutineScope()
    var tareas by remember { mutableStateOf<List<TareaDelDia>>(emptyList()) }
    var recarga by remember { mutableIntStateOf(0) }
    val estadoSync by contenedor.sync.estado.collectAsState()
    val alertas by contenedor.alertas.observarAbiertas().collectAsState(initial = emptyList())

    LaunchedEffect(recarga) {
        // Al abrir se revisan los reposos vencidos y se cierran los registros
        // de BPM del día anterior: así el aviso aparece aunque el teléfono
        // haya estado apagado.
        contenedor.lotes.revisarReposos()
        contenedor.apoyo.cerrarRegistrosBpmVencidos()
        contenedor.reprogramarAvisos()
        contenedor.sync.refrescarEstado()
        tareas = contenedor.apoyo.tareasDeHoy()
    }

    Scaffold(
        topBar = {
            BarraSuperior("¿Qué hago hoy?") {
                IconButton(onClick = { recarga++ }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                }
            }
        },
    ) { relleno ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(relleno),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                // RF-SYN-06: "Todo sincronizado" / "N pendientes" / "Sin conexión".
                val (color, icono) = when {
                    !estadoSync.hayConexion -> ColoresEstado.neutro to Icons.Default.CloudOff
                    estadoSync.todoAlDia -> ColoresEstado.bien to Icons.Default.CloudDone
                    else -> ColoresEstado.atencion to Icons.Default.CloudUpload
                }
                Card {
                    ListItem(
                        leadingContent = {
                            Icon(icono, contentDescription = null, tint = color)
                        },
                        headlineContent = { Text(estadoSync.etiqueta) },
                        supportingContent = {
                            Text(
                                when {
                                    !estadoSync.hayConexion ->
                                        "Puedes seguir trabajando; se subirá al volver la señal"
                                    estadoSync.todoAlDia -> "Tus registros están respaldados"
                                    else -> "Se subirán en cuanto se pueda"
                                },
                            )
                        },
                        trailingContent = {
                            if (estadoSync.pendientes > 0 && estadoSync.hayConexion) {
                                TextButton(
                                    onClick = {
                                        alcance.launch {
                                            contenedor.sync.sincronizar()
                                            recarga++
                                        }
                                    },
                                ) { Text("Subir ahora") }
                            }
                        },
                    )
                }
            }

            if (alertas.isNotEmpty()) {
                item {
                    val bloqueantes = alertas.count { it.severidad == "BLOQUEANTE" }
                    val urgentes = alertas.count { it.severidad == "URGENTE" }
                    val color = when {
                        bloqueantes > 0 -> ColoresEstado.problema
                        urgentes > 0 -> ColoresEstado.atencion
                        else -> ColoresEstado.neutro
                    }
                    Aviso(
                        titulo = if (alertas.size == 1) {
                            "1 alerta abierta"
                        } else {
                            "${alertas.size} alertas abiertas"
                        },
                        texto = alertas.first().quePaso,
                        color = color,
                        icono = Icons.Default.NotificationsActive,
                        accion = {
                            FilledTonalButton(
                                onClick = { navegacion.navigate(Rutas.alertas()) },
                            ) { Text("Ver las alertas") }
                        },
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text("Tareas de hoy", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
            }

            if (tareas.isEmpty()) {
                item {
                    Card {
                        Column(Modifier.padding(24.dp)) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ColoresEstado.bien,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Nada pendiente por ahora",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Cuando haya un lote en fermentación o en secado, aquí " +
                                    "aparecerá lo que toca hacer cada día.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            } else {
                items(tareas) { tarea ->
                    Card(
                        colors = if (tarea.urgente) {
                            CardDefaults.cardColors(
                                containerColor = ColoresEstado.atencion.copy(alpha = 0.10f),
                            )
                        } else {
                            CardDefaults.cardColors()
                        },
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        ListItem(
                            leadingContent = {
                                Icon(
                                    if (tarea.urgente) {
                                        Icons.Default.PriorityHigh
                                    } else {
                                        Icons.Default.RadioButtonUnchecked
                                    },
                                    contentDescription = null,
                                    tint = if (tarea.urgente) {
                                        ColoresEstado.atencion
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            },
                            headlineContent = { Text(tarea.titulo) },
                            supportingContent = {
                                Text("${tarea.detalle} · ${tarea.loteCodigo}")
                            },
                            trailingContent = {
                                if (tarea.loteId != null || tarea.destino != null) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                    )
                                }
                            },
                            modifier = Modifier.clickableSiHayDestino(tarea) { ruta ->
                                navegacion.navigate(ruta)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun Modifier.clickableSiHayDestino(
    tarea: TareaDelDia,
    alTocar: (String) -> Unit,
): Modifier {
    val ruta = Rutas.deTarea(tarea.destino, tarea.loteId) ?: return this
    return this.then(
        androidx.compose.foundation.clickable { alTocar(ruta) },
    )
}
