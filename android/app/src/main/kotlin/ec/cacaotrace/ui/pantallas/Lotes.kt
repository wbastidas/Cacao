package ec.cacaotrace.ui.pantallas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ec.cacaotrace.ContenedorApp
import ec.cacaotrace.datos.bd.entidades.LoteEntidad
import ec.cacaotrace.nucleo.modelo.Codigos
import ec.cacaotrace.ui.ColoresEstado
import ec.cacaotrace.ui.Rutas
import ec.cacaotrace.ui.comun.BarraSuperior
import ec.cacaotrace.ui.comun.EstadoVacio
import ec.cacaotrace.ui.comun.Formato
import kotlinx.coroutines.launch

/** Lista de lotes, creación y escaneo de QR (RF-LOT-01, RF-LOT-04). */
@Composable
fun PantallaLotes(contenedor: ContenedorApp, navegacion: NavHostController) {
    val alcance = rememberCoroutineScope()
    val lotes by contenedor.lotes.observarLotes().collectAsState(initial = emptyList())
    var mensaje by remember { mutableStateOf<String?>(null) }

    // El escáner devuelve el código por el savedStateHandle de esta entrada.
    val entrada = navegacion.currentBackStackEntry
    val codigoEscaneado = entrada?.savedStateHandle
        ?.getStateFlow<String?>("codigoQr", null)?.collectAsState()

    LaunchedEffect(codigoEscaneado?.value) {
        val codigo = codigoEscaneado?.value ?: return@LaunchedEffect
        entrada?.savedStateHandle?.set("codigoQr", null)
        // Un QR puede ser de lote (L-…), de saco (L-…-Sxx) o de tanda (P-…).
        val lote = contenedor.lotes.porCodigo(Codigos.loteDeSaco(codigo))
        if (lote == null) {
            mensaje = "No se encontró ningún lote con el código $codigo"
        } else {
            navegacion.navigate(Rutas.detalleLote(lote.id))
        }
    }

    Scaffold(
        topBar = {
            BarraSuperior("Lotes de cacao") {
                IconButton(onClick = { navegacion.navigate(Rutas.ESCANER) }) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear QR")
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    alcance.launch {
                        val lote = contenedor.lotes.crearLote()
                        navegacion.navigate(Rutas.detalleLote(lote.id))
                    }
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nuevo lote") },
            )
        },
    ) { relleno ->
        if (lotes.isEmpty()) {
            EstadoVacio(
                icono = Icons.Default.Inventory2,
                titulo = "Todavía no hay lotes",
                explicacion = "Un lote es el cacao que llega junto y se procesa junto. " +
                    "Crea el primero cuando recibas las mazorcas.",
                modifier = Modifier.fillMaxSize().padding(relleno),
                accion = {
                    Button(
                        onClick = {
                            alcance.launch {
                                val lote = contenedor.lotes.crearLote()
                                navegacion.navigate(Rutas.detalleLote(lote.id))
                            }
                        },
                    ) { Text("Crear el primer lote") }
                },
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(relleno),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
        ) {
            mensaje?.let { texto ->
                item {
                    ec.cacaotrace.ui.comun.Aviso(
                        texto = texto,
                        color = ColoresEstado.problema,
                    )
                }
            }
            items(lotes, key = { it.id }) { lote ->
                TarjetaLote(contenedor, lote) {
                    navegacion.navigate(Rutas.detalleLote(lote.id))
                }
            }
        }
    }
}

@Composable
private fun TarjetaLote(
    contenedor: ContenedorApp,
    lote: LoteEntidad,
    alTocar: () -> Unit,
) {
    val alertas by contenedor.alertas.observarAbiertas(lote.id)
        .collectAsState(initial = emptyList())

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = alTocar)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    lote.codigo,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (lote.ventaBloqueada) {
                    AssistChip(
                        onClick = alTocar,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Block,
                                contentDescription = null,
                                tint = ColoresEstado.problema,
                            )
                        },
                        label = { Text("Venta bloqueada") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Timeline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "  ${lote.estado.etiqueta}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                lote.estado.queSigue,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Llegó el ${Formato.fecha(lote.fechaLlegada)}",
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                if (alertas.isNotEmpty()) {
                    AssistChip(
                        onClick = alTocar,
                        leadingIcon = {
                            Icon(
                                Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = ColoresEstado.atencion,
                            )
                        },
                        label = { Text("${alertas.size}") },
                    )
                }
            }
        }
    }
}
