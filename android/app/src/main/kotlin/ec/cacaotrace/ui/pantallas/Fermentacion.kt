package ec.cacaotrace.ui.pantallas

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ec.cacaotrace.ContenedorApp
import ec.cacaotrace.datos.bd.entidades.FermentacionEntidad
import ec.cacaotrace.nucleo.modelo.OlorFermentacion
import ec.cacaotrace.ui.ColoresEstado
import ec.cacaotrace.ui.comun.Aviso
import ec.cacaotrace.ui.comun.BarraSuperior
import ec.cacaotrace.ui.comun.BotonGrande
import ec.cacaotrace.ui.comun.CampoNumero
import ec.cacaotrace.ui.comun.EstadoVacio
import ec.cacaotrace.ui.comun.FilaDato
import ec.cacaotrace.ui.comun.Formato
import ec.cacaotrace.ui.comun.TarjetaSeccion
import ec.cacaotrace.ui.comun.leerNumero
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.launch

/**
 * Fermentación: curva de temperatura, volteos y lecturas (RF-FER-01 a 07).
 *
 * El botón "Registré un volteo" está arriba y ocupa todo el ancho a propósito:
 * es la acción que se hace todos los días, a veces con las manos sucias, y
 * tiene que poder hacerse en un toque (RNF-09).
 */
@Composable
fun PantallaFermentacion(
    contenedor: ContenedorApp,
    navegacion: NavHostController,
    loteId: String,
) {
    val alcance = rememberCoroutineScope()
    var fermentacion by remember { mutableStateOf<FermentacionEntidad?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var mostrandoLectura by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(loteId, recarga) {
        fermentacion = contenedor.lotes.fermentacionDe(loteId)
    }

    Scaffold(topBar = { BarraSuperior("Fermentación", navegacion) }) { relleno ->
        val f = fermentacion
        if (f == null) {
            IniciarFermentacion(contenedor, loteId, Modifier.padding(relleno)) { recarga++ }
            return@Scaffold
        }

        val lecturas by contenedor.lotes.observarLecturas(f.id)
            .collectAsState(initial = emptyList())
        val volteos by contenedor.lotes.observarVolteos(f.id)
            .collectAsState(initial = emptyList())
        val terminada = f.fin != null
        val horas = Duration.between(f.inicio, Instant.now()).toHours()
        val desdeVolteo = Duration.between(
            volteos.lastOrNull()?.fechaHora ?: f.inicio,
            Instant.now(),
        ).toHours()

        if (mostrandoLectura) {
            DialogoLectura(
                alCerrar = { mostrandoLectura = false },
                alGuardar = { temp, ph, olor ->
                    alcance.launch {
                        val alertas = contenedor.lotes.registrarLectura(
                            fermentacionId = f.id,
                            loteId = loteId,
                            tempC = temp,
                            ph = ph,
                            olor = olor,
                        )
                        mensaje = alertas.firstOrNull()
                            ?.let { "${it.quePaso}. ${it.queHacer}" }
                        mostrandoLectura = false
                        recarga++
                    }
                },
            )
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(relleno),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                mensaje?.let { Aviso(texto = it) }

                if (!terminada) {
                    BotonGrande(
                        texto = "Registré un volteo",
                        subtitulo = "Van $desdeVolteo horas desde el último",
                        icono = Icons.Default.RotateRight,
                        color = if (desdeVolteo >= 24) ColoresEstado.atencion else null,
                    ) {
                        alcance.launch {
                            contenedor.lotes.registrarVolteo(f.id)
                            recarga++
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                TarjetaSeccion(
                    if (terminada) "Fermentación terminada" else "En curso",
                    icono = Icons.Default.Timer,
                ) {
                    Column {
                        FilaDato(
                            "Tiempo",
                            if (terminada) {
                                "${Duration.between(f.inicio, f.fin).toHours()} h"
                            } else {
                                "$horas h (${Formato.numero(horas / 24.0)} días)"
                            },
                        )
                        FilaDato("Masa", Formato.kg(f.masaKg))
                        FilaDato("Volteos", "${volteos.size}")
                        FilaDato("Lecturas", "${lecturas.size}")
                        lecturas.lastOrNull()?.tempC?.let {
                            FilaDato("Última temperatura", Formato.grados(it))
                        }
                    }
                }

                GraficaTemperatura(f, lecturas.mapNotNull { l ->
                    l.tempC?.let {
                        Duration.between(f.inicio, l.fechaHora).toMinutes() / 60.0 to it
                    }
                })

                if (!terminada) {
                    Spacer(Modifier.height(8.dp))
                    BotonGrande(
                        texto = "Registrar una lectura",
                        subtitulo = "Temperatura, pH y olor",
                        icono = Icons.Default.Thermostat,
                    ) { mostrandoLectura = true }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            alcance.launch {
                                contenedor.lotes.cerrarFermentacion(f.id, loteId)
                                recarga++
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.StopCircle, contentDescription = null)
                        Text("  Cerrar la fermentación")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            items(lecturas.reversed(), key = { it.id }) { l ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                if (l.fuente == "sensor") {
                                    Icons.Default.Sensors
                                } else {
                                    Icons.Default.TouchApp
                                },
                                contentDescription = null,
                            )
                        },
                        headlineContent = {
                            Text(l.tempC?.let { Formato.grados(it) } ?: "Lectura")
                        },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    Formato.fechaHora(l.fechaHora),
                                    l.ph?.let { "pH ${Formato.numero(it)}" },
                                    l.olor?.etiqueta,
                                ).joinToString(" · "),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun IniciarFermentacion(
    contenedor: ContenedorApp,
    loteId: String,
    modifier: Modifier,
    alIniciar: () -> Unit,
) {
    val alcance = rememberCoroutineScope()
    var masa by remember { mutableStateOf("") }
    var aislamiento by remember { mutableStateOf("Hojas de plátano y sacos") }
    val equipos by contenedor.config.observarEquipos("fermentador")
        .collectAsState(initial = emptyList())
    var equipoId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(loteId) {
        contenedor.lotes.completo(loteId)?.apertura?.let { a ->
            masa = Formato.numero(a.kgBaba + a.kgCascaraAnadida)
        }
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            EstadoVacio(
                icono = Icons.Default.Thermostat,
                titulo = "Empezar la fermentación",
                explicacion = "Aquí es donde el grano desarrolla el sabor a chocolate. " +
                    "Durante los próximos días medirás la temperatura y voltearás la " +
                    "masa cada 24 horas.",
            )
            if (equipos.isEmpty()) {
                Aviso(
                    texto = "No has registrado ningún fermentador. Puedes empezar igual y " +
                        "registrarlo después desde Ajustes.",
                    color = ColoresEstado.neutro,
                )
            } else {
                Row(Modifier.fillMaxWidth()) {
                    equipos.forEach { e ->
                        FilterChip(
                            selected = equipoId == e.id,
                            onClick = { equipoId = e.id },
                            label = { Text(e.nombre) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
            CampoNumero("Masa que entra", masa, { masa = it }, unidad = "kg")
            OutlinedTextField(
                value = aislamiento,
                onValueChange = { aislamiento = it },
                label = { Text("Aislamiento") },
                supportingText = { Text("Qué le pusiste encima para conservar el calor") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            BotonGrande("Empezar ahora", Icons.Default.PlayArrow) {
                val kg = leerNumero(masa) ?: return@BotonGrande
                alcance.launch {
                    contenedor.lotes.iniciarFermentacion(
                        loteId = loteId,
                        equipoId = equipoId ?: equipos.firstOrNull()?.id,
                        masaKg = kg,
                        aislamiento = aislamiento.trim(),
                    )
                    alIniciar()
                }
            }
        }
    }
}

@Composable
private fun DialogoLectura(
    alCerrar: () -> Unit,
    alGuardar: (Double?, Double?, OlorFermentacion?) -> Unit,
) {
    var temp by remember { mutableStateOf("") }
    var ph by remember { mutableStateOf("") }
    var olor by remember { mutableStateOf<OlorFermentacion?>(null) }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Nueva lectura") },
        text = {
            LazyColumn {
                item {
                    CampoNumero(
                        "Temperatura", temp, { temp = it }, unidad = "°C",
                        ayuda = "Clava el termómetro en el centro de la masa",
                    )
                    CampoNumero("pH (si lo mides)", ph, { ph = it })
                    Spacer(Modifier.height(8.dp))
                    Text("¿A qué huele?", fontSize = 17.sp)
                    OlorFermentacion.entries.forEach { o ->
                        FilterChip(
                            selected = olor == o,
                            onClick = { olor = if (olor == o) null else o },
                            label = { Text(o.etiqueta) },
                            modifier = Modifier.padding(end = 6.dp, top = 4.dp),
                        )
                    }
                    olor?.let { o ->
                        Text(
                            o.significado,
                            fontSize = 15.sp,
                            color = if (o.esMalaSenal) {
                                ColoresEstado.problema
                            } else {
                                ColoresEstado.bien
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { alGuardar(leerNumero(temp), leerNumero(ph), olor) },
            ) { Text("Guardar la lectura") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

/**
 * Curva de temperatura frente a la de referencia (RF-FER-05).
 *
 * Se dibuja con Canvas y no con una librería de gráficas: es una sola serie
 * contra una curva fija, y una dependencia entera para esto sería peso muerto
 * en un APK que debe quedar por debajo de 150 MB (RNF-03).
 */
@Composable
private fun GraficaTemperatura(
    fermentacion: FermentacionEntidad,
    puntos: List<Pair<Double, Double>>,
) {
    if (puntos.isEmpty()) {
        TarjetaSeccion("Curva de temperatura", icono = Icons.Default.ShowChart) {
            Text(
                "Cuando registres temperaturas, aquí verás tu curva comparada con la de " +
                    "una fermentación que va bien.",
                fontSize = 15.sp,
            )
        }
        return
    }

    // Curva típica de una fermentación que va bien: sube hasta ~48 °C hacia el
    // tercer día y baja despacio. Es referencia visual, no una regla.
    val objetivo = listOf(
        0.0 to 26.0, 24.0 to 38.0, 48.0 to 46.0, 72.0 to 48.0,
        96.0 to 47.0, 120.0 to 44.0, 144.0 to 41.0,
    )
    val maxX = maxOf(puntos.maxOf { it.first }, objetivo.last().first).coerceAtLeast(1.0)
    val minY = 20.0
    val maxY = 55.0

    TarjetaSeccion("Curva de temperatura", icono = Icons.Default.ShowChart) {
        Column {
            Canvas(Modifier.fillMaxWidth().height(200.dp)) {
                fun aPantalla(x: Double, y: Double) = Offset(
                    (x / maxX * size.width).toFloat(),
                    (size.height - (y - minY) / (maxY - minY) * size.height).toFloat(),
                )

                fun trazar(datos: List<Pair<Double, Double>>) = Path().apply {
                    datos.forEachIndexed { i, (x, y) ->
                        val p = aPantalla(x, y)
                        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                }

                drawPath(
                    trazar(objetivo),
                    color = ColoresEstado.neutro,
                    style = Stroke(
                        width = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                    ),
                )
                drawPath(
                    trazar(puntos),
                    color = Color(0xFF8D5524),
                    style = Stroke(width = 6f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Línea discontinua: referencia.  Línea llena: tu lote.  " +
                    "Eje horizontal en horas, vertical en °C.",
                fontSize = 13.sp,
            )
        }
    }
}
