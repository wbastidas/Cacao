package ec.cacaotrace.ui.pantallas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ec.cacaotrace.ContenedorApp
import ec.cacaotrace.datos.repositorios.HechoLote
import ec.cacaotrace.datos.repositorios.LoteCompleto
import ec.cacaotrace.nucleo.calculo.PasoBalance
import ec.cacaotrace.ui.ColoresEstado
import ec.cacaotrace.ui.Rutas
import ec.cacaotrace.ui.comun.Aviso
import ec.cacaotrace.ui.comun.BarraSuperior
import ec.cacaotrace.ui.comun.BotonGrande
import ec.cacaotrace.ui.comun.EstadoVacio
import ec.cacaotrace.ui.comun.FilaDato
import ec.cacaotrace.ui.comun.Formato
import ec.cacaotrace.ui.comun.TarjetaSeccion
import kotlin.math.abs

/**
 * Detalle del lote: línea de tiempo, balance de masa y lo que toca hacer en la
 * etapa actual (RF-LOT-06, RF-LOT-08).
 */
@Composable
fun PantallaDetalleLote(
    contenedor: ContenedorApp,
    navegacion: NavHostController,
    loteId: String,
) {
    var completo by remember { mutableStateOf<LoteCompleto?>(null) }
    var historia by remember { mutableStateOf<List<HechoLote>>(emptyList()) }
    var balance by remember { mutableStateOf<List<PasoBalance>>(emptyList()) }
    var pestana by remember { mutableIntStateOf(0) }
    var mostrandoQr by remember { mutableStateOf(false) }

    // Se recarga al volver de una pantalla hija: el lote pudo cambiar de etapa.
    val entradaActual = navegacion.currentBackStackEntry
    LaunchedEffect(loteId, entradaActual) {
        completo = contenedor.lotes.completo(loteId)
        historia = contenedor.lotes.lineaDeTiempo(loteId)
        balance = contenedor.lotes.balance(loteId)
    }

    val c = completo ?: return

    if (mostrandoQr) {
        DialogoQr(c.lote.codigo) { mostrandoQr = false }
    }

    Scaffold(
        topBar = {
            BarraSuperior(c.lote.codigo, navegacion) {
                IconButton(onClick = { mostrandoQr = true }) {
                    Icon(Icons.Default.QrCode2, contentDescription = "Código QR")
                }
                IconButton(onClick = { navegacion.navigate(Rutas.reporte(loteId)) }) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "Reporte")
                }
            }
        },
    ) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno)) {
            TabRow(selectedTabIndex = pestana) {
                listOf("Qué sigue", "Historia", "Balance").forEachIndexed { i, titulo ->
                    Tab(
                        selected = pestana == i,
                        onClick = { pestana = i },
                        text = { Text(titulo) },
                    )
                }
            }
            when (pestana) {
                0 -> QueSigue(c, navegacion)
                1 -> Historia(historia)
                else -> Balance(balance)
            }
        }
    }
}

@Composable
private fun QueSigue(c: LoteCompleto, navegacion: NavHostController) {
    val loteId = c.lote.id

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            if (c.lote.ventaBloqueada) {
                Aviso(
                    titulo = "Venta bloqueada",
                    texto = c.lote.motivoBloqueo.ifBlank {
                        "Este lote no se puede vender hasta resolverlo."
                    },
                    color = ColoresEstado.problema,
                    icono = Icons.Default.Block,
                )
            }

            TarjetaSeccion(c.lote.estado.etiqueta, icono = Icons.Default.Timeline) {
                Column {
                    Text(c.lote.estado.queSigue, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    c.finca?.let { FilaDato("Finca", it.nombre) }
                    FilaDato("Variedad", c.lote.variedad)
                    c.recepcion?.let { FilaDato("Mazorcas", "${it.mazorcasTotal}") }
                    c.apertura?.let { FilaDato("Baba", Formato.kg(it.kgBaba)) }
                    c.secado?.kgSeco?.let { FilaDato("Grano seco", Formato.kg(it)) }
                    c.pruebaCorteFinal?.let { p ->
                        FilaDato(
                            "Prueba de corte",
                            p.resultado,
                            color = if (p.conforme) {
                                ColoresEstado.bien
                            } else {
                                ColoresEstado.atencion
                            },
                        )
                    }
                }
            }

            if (c.alertasAbiertas > 0) {
                Aviso(
                    texto = if (c.alertasAbiertas == 1) {
                        "Hay 1 alerta abierta en este lote"
                    } else {
                        "Hay ${c.alertasAbiertas} alertas abiertas en este lote"
                    },
                    accion = {
                        OutlinedButton(
                            onClick = { navegacion.navigate(Rutas.alertas(loteId)) },
                        ) { Text("Ver") }
                    },
                )
            }

            Spacer(Modifier.height(8.dp))

            if (c.recepcion == null) {
                BotonGrande(
                    texto = "Registrar la recepción",
                    subtitulo = "Mazorcas, peso y días de reposo",
                    icono = Icons.Default.LocalShipping,
                ) { navegacion.navigate(Rutas.recepcion(loteId)) }
            } else {
                BotonGrande(
                    texto = if (c.apertura == null) "Abrir las mazorcas" else "Ver la apertura",
                    subtitulo = c.apertura?.let { Formato.kg(it.kgBaba) + " de baba" }
                        ?: "Registrar kg de baba",
                    icono = Icons.Default.Egg,
                ) { navegacion.navigate(Rutas.apertura(loteId)) }
                Spacer(Modifier.height(12.dp))

                BotonGrande(
                    texto = if (c.fermentacion == null) {
                        "Empezar la fermentación"
                    } else {
                        "Fermentación"
                    },
                    subtitulo = if (c.fermentacion == null) {
                        "Elegir fermentador y masa"
                    } else {
                        "Temperatura, volteos y olor"
                    },
                    icono = Icons.Default.Thermostat,
                ) { navegacion.navigate(Rutas.fermentacion(loteId)) }
                Spacer(Modifier.height(12.dp))

                BotonGrande(
                    texto = if (c.secado == null) "Empezar el secado" else "Secado",
                    subtitulo = c.secado?.kgSeco?.let { Formato.kg(it) + " secos" }
                        ?: "Humedad, remociones y moho",
                    icono = Icons.Default.WbSunny,
                ) { navegacion.navigate(Rutas.secado(loteId)) }
                Spacer(Modifier.height(12.dp))

                BotonGrande(
                    texto = "Prueba de corte",
                    subtitulo = c.pruebaCorteFinal?.resultado
                        ?: "Cortar 100 granos y calificar",
                    icono = Icons.Default.GridOn,
                ) { navegacion.navigate(Rutas.pruebaCorte(loteId)) }
                Spacer(Modifier.height(12.dp))

                BotonGrande(
                    texto = "Almacenamiento",
                    subtitulo = "Sacos, QR e inspecciones",
                    icono = Icons.Default.Warehouse,
                ) { navegacion.navigate(Rutas.almacen(loteId)) }
            }

            Spacer(Modifier.height(24.dp))
            OutlinedButton(
                onClick = { navegacion.navigate(Rutas.alertas(loteId)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Notifications, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Alertas y correcciones de este lote")
            }
        }
    }
}

@Composable
private fun Historia(hechos: List<HechoLote>) {
    if (hechos.isEmpty()) {
        EstadoVacio(
            icono = Icons.Default.History,
            titulo = "Todavía no hay historia",
            explicacion = "Aquí se irá anotando todo lo que le pase al lote: lecturas, " +
                "volteos, fotos y alertas.",
        )
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        itemsIndexed(hechos) { i, h ->
            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.size(14.dp).padding(top = 4.dp)) {
                        drawCircle(
                            color = if (h.esAlerta) ColoresEstado.atencion else Color.Gray,
                        )
                    }
                    if (i < hechos.lastIndex) {
                        Canvas(Modifier.width(2.dp).height(52.dp)) {
                            drawRect(Color.LightGray)
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(bottom = 16.dp)) {
                    Text(
                        "${Formato.fechaHora(h.fecha)} · ${h.etapa}",
                        fontSize = 13.sp,
                        color = ColoresEstado.neutro,
                    )
                    Text(h.titulo, fontSize = 17.sp)
                    if (h.detalle.isNotBlank()) Text(h.detalle, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun Balance(pasos: List<PasoBalance>) {
    if (pasos.isEmpty()) {
        EstadoVacio(
            icono = Icons.Default.Balance,
            titulo = "Sin datos para el balance",
            explicacion = "Registra la recepción para empezar a ver cuánto debería rendir " +
                "el lote en cada etapa.",
        )
        return
    }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Text(
                "Compara lo que obtuviste con lo que era esperable. Una diferencia grande " +
                    "casi siempre es un error al anotar un peso.",
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(12.dp))
        }
        itemsIndexed(pasos) { _, p ->
            Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(p.etapa, fontSize = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    FilaDato(
                        "Entró",
                        "${Formato.numero(p.entrada)} ${p.unidadEntrada}",
                    )
                    FilaDato(
                        "Esperado",
                        "${Formato.numero(p.salidaEsperada)} ${p.unidadSalida}",
                    )
                    if (p.registrado) {
                        FilaDato(
                            "Obtenido",
                            "${Formato.numero(p.salidaReal!!)} ${p.unidadSalida}",
                            color = if (abs(p.desvioPct!!) > 20) {
                                ColoresEstado.atencion
                            } else {
                                ColoresEstado.bien
                            },
                        )
                        Text(
                            if (p.desvioPct!! >= 0) {
                                "${Formato.numero(p.desvioPct!!, 0)} % por encima de lo esperado"
                            } else {
                                "${Formato.numero(abs(p.desvioPct!!), 0)} % por debajo de lo " +
                                    "esperado"
                            },
                            fontSize = 14.sp,
                        )
                    } else {
                        FilaDato("Obtenido", "sin registrar", color = ColoresEstado.neutro)
                    }
                }
            }
        }
    }
}

/**
 * Dibuja el QR del lote.
 *
 * Se genera con un generador propio y no con una librería para no añadir una
 * dependencia por un único uso; ver [ec.cacaotrace.ui.comun.GeneradorQr].
 */
@Composable
private fun DialogoQr(codigo: String, alCerrar: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(codigo) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(220.dp)) {
                    ec.cacaotrace.ui.comun.CodigoQr(codigo, Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Imprime este código y pégalo en el saco. Al escanearlo se abre la " +
                        "historia completa del lote.",
                    fontSize = 15.sp,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = alCerrar) { Text("Cerrar") }
        },
    )
}
