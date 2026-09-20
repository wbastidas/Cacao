package ec.cacaotrace.ui.pantallas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Roofing
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ec.cacaotrace.ContenedorApp
import ec.cacaotrace.datos.bd.entidades.SecadoEntidad
import ec.cacaotrace.nucleo.modelo.MetodoSecado
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

/** Guía de la prueba del puñado, para cuando no hay medidor (RF-SEC-03). */
private val pruebaPunado = listOf(
    "Suena a quebradizo" to
        "Al apretar un puñado, los granos crujen y se separan solos. Está listo.",
    "Suena sordo" to
        "Los granos se pegan entre sí y no crujen. Le falta secado.",
    "Se aplasta" to
        "El grano cede al apretarlo. Le falta bastante.",
)

/** Secado del grano (RF-SEC-01 a RF-SEC-05). */
@Composable
fun PantallaSecado(
    contenedor: ContenedorApp,
    navegacion: NavHostController,
    loteId: String,
) {
    val alcance = rememberCoroutineScope()
    var secado by remember { mutableStateOf<SecadoEntidad?>(null) }
    var recarga by remember { mutableIntStateOf(0) }
    var mostrandoJornada by remember { mutableStateOf(false) }
    var mostrandoCierre by remember { mutableStateOf(false) }

    LaunchedEffect(loteId, recarga) { secado = contenedor.lotes.secadoDe(loteId) }

    Scaffold(topBar = { BarraSuperior("Secado", navegacion) }) { relleno ->
        val s = secado
        if (s == null) {
            Column(Modifier.fillMaxSize().padding(relleno).padding(16.dp)) {
                EstadoVacio(
                    icono = Icons.Default.WbSunny,
                    titulo = "Empezar el secado",
                    explicacion = "El secado baja la humedad del grano hasta un 7 % para " +
                        "que no críe moho. Los primeros dos días son los que más agua " +
                        "sueltan: extiende en capa delgada y remueve seguido.",
                )
                MetodoSecado.entries.forEach { metodo ->
                    BotonGrande(
                        texto = metodo.etiqueta,
                        icono = when (metodo) {
                            MetodoSecado.MARQUESINA -> Icons.Default.Roofing
                            MetodoSecado.SOL -> Icons.Default.WbSunny
                            MetodoSecado.SECADOR -> Icons.Default.Air
                        },
                        modifier = Modifier.padding(vertical = 6.dp),
                    ) {
                        alcance.launch {
                            contenedor.lotes.iniciarSecado(loteId, metodo)
                            recarga++
                        }
                    }
                }
            }
            return@Scaffold
        }

        val lecturas by contenedor.lotes.observarLecturasSecado(s.id)
            .collectAsState(initial = emptyList())
        val terminado = s.fin != null
        val dias = Duration.between(s.inicio, Instant.now()).toDays() + 1

        if (mostrandoJornada) {
            DialogoJornadaSecado(
                alCerrar = { mostrandoJornada = false },
                alGuardar = { humedad, punado, espesor, remociones, moho, tempAmb, hrAmb ->
                    alcance.launch {
                        contenedor.lotes.registrarLecturaSecado(
                            secadoId = s.id,
                            loteId = loteId,
                            humedadGrano = humedad,
                            pruebaPunado = punado,
                            tempAmbiente = tempAmb,
                            hrAmbiente = hrAmb,
                            espesorCm = espesor,
                            remociones = remociones,
                            moho = moho,
                        )
                        mostrandoJornada = false
                        recarga++
                    }
                },
            )
        }

        if (mostrandoCierre) {
            DialogoCerrarSecado(
                alCerrar = { mostrandoCierre = false },
                alGuardar = { kgSeco, humedad, confirmado ->
                    alcance.launch {
                        val alertas = contenedor.lotes.cerrarSecado(
                            secadoId = s.id,
                            loteId = loteId,
                            kgSeco = kgSeco,
                            humedadFinal = humedad,
                            confirmado = confirmado,
                        )
                        // Si RN-08 bloquea, el diálogo se queda abierto para
                        // que el usuario decida a conciencia.
                        if (alertas.none { it.bloquea } || confirmado) {
                            mostrandoCierre = false
                            recarga++
                        }
                    }
                },
            )
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(relleno),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                TarjetaSeccion(
                    if (terminado) "Secado terminado" else "Día $dias de secado",
                    icono = Icons.Default.CalendarToday,
                ) {
                    Column {
                        FilaDato("Método", s.metodo.etiqueta)
                        FilaDato("Jornadas registradas", "${lecturas.size}")
                        lecturas.lastOrNull()?.humedadGrano?.let { h ->
                            FilaDato(
                                "Última humedad",
                                Formato.porcentaje(h),
                                color = if (h > 7) {
                                    ColoresEstado.atencion
                                } else {
                                    ColoresEstado.bien
                                },
                            )
                        }
                        s.kgSeco?.let { FilaDato("Grano seco", Formato.kg(it)) }
                    }
                }

                if (!terminado) {
                    BotonGrande(
                        texto = "Registrar la jornada de hoy",
                        subtitulo = "Humedad, remociones y estado del grano",
                        icono = Icons.Default.AddTask,
                    ) { mostrandoJornada = true }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { mostrandoCierre = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.StopCircle, contentDescription = null)
                        Text("  Cerrar el secado")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            items(lecturas.reversed(), key = { it.id }) { l ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                if (l.moho) Icons.Default.Warning else Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = if (l.moho) {
                                    ColoresEstado.problema
                                } else {
                                    ColoresEstado.neutro
                                },
                            )
                        },
                        headlineContent = {
                            Text(
                                l.humedadGrano?.let { "Humedad ${Formato.porcentaje(it)}" }
                                    ?: l.pruebaPunado.ifBlank { "Jornada" },
                            )
                        },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    Formato.fechaCorta(l.fecha),
                                    "${l.remociones} remociones",
                                    l.espesorCm?.let { "capa ${Formato.numero(it)} cm" },
                                    if (l.moho) "MOHO" else null,
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
private fun DialogoJornadaSecado(
    alCerrar: () -> Unit,
    alGuardar: (Double?, String, Double?, Int, Boolean, Double?, Double?) -> Unit,
) {
    var humedad by remember { mutableStateOf("") }
    var espesor by remember { mutableStateOf("") }
    var remociones by remember { mutableStateOf("3") }
    var tempAmb by remember { mutableStateOf("") }
    var hrAmb by remember { mutableStateOf("") }
    var moho by remember { mutableStateOf(false) }
    var punado by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Jornada de secado") },
        text = {
            LazyColumn {
                item {
                    CampoNumero(
                        "Humedad del grano", humedad, { humedad = it }, unidad = "%",
                        ayuda = "Si tienes medidor. Si no, usa la prueba del puñado",
                    )
                    Text("Prueba del puñado", fontSize = 17.sp)
                    pruebaPunado.forEach { (titulo, explicacion) ->
                        Column(
                            Modifier.fillMaxWidth().selectable(
                                selected = punado == titulo,
                                onClick = { punado = titulo },
                            ).padding(vertical = 4.dp),
                        ) {
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = punado == titulo,
                                    onClick = { punado = titulo },
                                )
                                Text(titulo, fontSize = 16.sp)
                            }
                            Text(explicacion, fontSize = 14.sp)
                        }
                    }
                    CampoNumero(
                        "Espesor de la capa", espesor, { espesor = it }, unidad = "cm",
                        ayuda = "Los primeros días, 3-4 cm",
                    )
                    CampoNumero(
                        "Remociones del día", remociones, { remociones = it },
                        decimales = false,
                    )
                    CampoNumero("Temperatura ambiente", tempAmb, { tempAmb = it }, unidad = "°C")
                    CampoNumero("Humedad ambiente", hrAmb, { hrAmb = it }, unidad = "%")
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(checked = moho, onCheckedChange = { moho = it })
                        Spacer(Modifier.height(8.dp))
                        Column {
                            Text("  Vi moho en el grano", fontSize = 16.sp)
                            Text(
                                "  Separa ahora los granos afectados: el moho da un " +
                                    "sabor que no se quita después.",
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    alGuardar(
                        leerNumero(humedad),
                        punado,
                        leerNumero(espesor),
                        leerNumero(remociones)?.toInt() ?: 0,
                        moho,
                        leerNumero(tempAmb),
                        leerNumero(hrAmb),
                    )
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoCerrarSecado(
    alCerrar: () -> Unit,
    alGuardar: (Double, Double?, Boolean) -> Unit,
) {
    var kgSeco by remember { mutableStateOf("") }
    var humedad by remember { mutableStateOf("") }
    var confirmado by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Cerrar el secado") },
        text = {
            Column {
                Text(
                    "Con más del 7 % de humedad el grano cría moho en el almacén, así " +
                        "que la app te pedirá confirmación.",
                    fontSize = 15.sp,
                )
                CampoNumero("Peso del grano seco", kgSeco, { kgSeco = it }, unidad = "kg")
                CampoNumero("Humedad final", humedad, { humedad = it }, unidad = "%")
                val h = leerNumero(humedad)
                if (h != null && h > 7) {
                    Aviso(
                        titulo = "Humedad alta",
                        texto = "Por encima del 7 % el lote puede perderse en el almacén. " +
                            "Si aun así quieres almacenarlo, márcalo aquí y quedará " +
                            "registrado.",
                        color = ColoresEstado.problema,
                        accion = {
                            androidx.compose.foundation.layout.Row(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Switch(
                                    checked = confirmado,
                                    onCheckedChange = { confirmado = it },
                                )
                                Text("  Almacenar de todos modos", fontSize = 15.sp)
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val kg = leerNumero(kgSeco) ?: return@TextButton
                    alGuardar(kg, leerNumero(humedad), confirmado)
                },
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Text(" Cerrar el secado")
            }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}
