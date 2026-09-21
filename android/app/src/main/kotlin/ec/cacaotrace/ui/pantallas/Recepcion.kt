package ec.cacaotrace.ui.pantallas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ec.cacaotrace.ContenedorApp
import ec.cacaotrace.ui.ColoresEstado
import ec.cacaotrace.ui.comun.Aviso
import ec.cacaotrace.ui.comun.BarraSuperior
import ec.cacaotrace.ui.comun.BotonFotoIa
import ec.cacaotrace.ui.comun.BotonGrande
import ec.cacaotrace.ui.comun.CampoNumero
import ec.cacaotrace.ui.comun.Formato
import ec.cacaotrace.ui.comun.OfrecerUbicacion
import ec.cacaotrace.ui.comun.TarjetaSeccion
import ec.cacaotrace.ui.comun.leerNumero
import kotlinx.coroutines.launch

val clasesMazorca = listOf("sana", "monilia", "fitoftora", "otro")

/**
 * Recepción de mazorcas (RF-REC-01 a RF-REC-06).
 *
 * El modo foto a foto es la parte importante: fotografiar mazorca por mazorca
 * y que la app lleve el conteo sola, en vez de que el usuario recuerde cuántas
 * tenían monilia mientras tiene las manos ocupadas.
 */
@Composable
fun PantallaRecepcion(
    contenedor: ContenedorApp,
    navegacion: NavHostController,
    loteId: String,
) {
    val alcance = rememberCoroutineScope()
    var mazorcas by rememberSaveable { mutableStateOf("") }
    var peso by rememberSaveable { mutableStateOf("") }
    var sacos by rememberSaveable { mutableStateOf("") }
    var descartadas by rememberSaveable { mutableStateOf("0") }
    var motivo by rememberSaveable { mutableStateOf("") }
    var diasReposo by rememberSaveable { mutableStateOf(4f) }
    var guardando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val conteo: SnapshotStateMap<String, Int> = remember {
        mutableStateMapOf<String, Int>().apply { clasesMazorca.forEach { put(it, 0) } }
    }

    LaunchedEffect(loteId) {
        contenedor.lotes.completo(loteId)?.recepcion?.let { r ->
            mazorcas = r.mazorcasTotal.toString()
            peso = r.pesoKg.toString()
            sacos = r.sacos.toString()
            descartadas = r.mazorcasDescartadas.toString()
            motivo = r.motivoDescarte
            diasReposo = r.diasReposo.toFloat()
            conteo["sana"] = r.mazorcasSanas
            conteo["monilia"] = r.mazorcasMonilia
            conteo["fitoftora"] = r.mazorcasFitoftora
            conteo["otro"] = r.mazorcasOtro
        }
    }

    Scaffold(topBar = { BarraSuperior("Recepción de mazorcas", navegacion) }) { relleno ->
        LazyColumn(
            Modifier.fillMaxSize().padding(relleno),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                aviso?.let { Aviso(texto = it, color = ColoresEstado.problema) }

                // Se ofrece aquí y no en otra pantalla porque es donde el dato
                // tiene sentido: la recepción es el momento en que se sabe de
                // qué finca viene el lote (RF-REC-06).
                OfrecerUbicacion(contenedor)

                TarjetaSeccion("Lo que llegó", icono = Icons.Default.LocalShipping) {
                    Column {
                        CampoNumero(
                            "Mazorcas en total", mazorcas, { mazorcas = it },
                            decimales = false,
                        )
                        CampoNumero("Peso total", peso, { peso = it }, unidad = "kg")
                        CampoNumero("Sacos", sacos, { sacos = it }, decimales = false)
                    }
                }

                TarjetaSeccion(
                    "Estado sanitario",
                    icono = Icons.Default.HealthAndSafety,
                    accion = {
                        Text("${conteo.values.sum()} clasificadas", fontSize = 14.sp)
                    },
                ) {
                    Column {
                        Text(
                            if (contenedor.modelos.estaDisponible("mazorca")) {
                                "Fotografía mazorca por mazorca: la app la clasifica y " +
                                    "lleva el conteo. Puedes corregir cada resultado."
                            } else {
                                "Todavía no hay modelo instalado. Cuenta a mano con los " +
                                    "botones, o toma la foto y elige tú la clase."
                            },
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        BotonFotoIa(
                            contenedor = contenedor,
                            tarea = "mazorca",
                            clases = clasesMazorca,
                            texto = "Fotografiar una mazorca",
                            consejo = "Fondo claro, sin flash, a unos 30 cm y de frente.",
                            alCapturar = { captura ->
                                conteo[captura.decisionUsuario] =
                                    (conteo[captura.decisionUsuario] ?: 0) + 1
                                alcance.launch {
                                    contenedor.apoyo.guardarFoto(
                                        rutaLocal = captura.archivo.absolutePath,
                                        etapa = "recepcion",
                                        loteId = loteId,
                                        analisisIa = captura.analisisJson,
                                        etiquetaUsuario = captura.decisionUsuario,
                                        aptaDataset = captura.aptaParaDataset,
                                    )
                                }
                            },
                        )
                        Spacer(Modifier.height(16.dp))
                        clasesMazorca.forEach { clase ->
                            ContadorClase(
                                clase = clase,
                                valor = conteo[clase] ?: 0,
                                alCambiar = { conteo[clase] = it },
                            )
                        }
                    }
                }

                TarjetaSeccion("Descartes", icono = Icons.Default.DeleteOutline) {
                    Column {
                        CampoNumero(
                            "Mazorcas descartadas", descartadas, { descartadas = it },
                            decimales = false,
                            ayuda = "Las que no entran al proceso",
                        )
                        OutlinedTextField(
                            value = motivo,
                            onValueChange = { motivo = it },
                            label = { Text("Motivo del descarte") },
                            placeholder = { Text("Monilia avanzada, mazorcas verdes…") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                TarjetaSeccion("Reposo antes de abrir", icono = Icons.Default.Hotel) {
                    Column {
                        Text(
                            "El reposo de la mazorca cerrada ayuda a que la fermentación " +
                                "arranque mejor. La app te avisará el día de la apertura.",
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = diasReposo,
                                onValueChange = { diasReposo = it },
                                valueRange = 0f..10f,
                                steps = 9,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${diasReposo.toInt()} días",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (diasReposo < 3 || diasReposo > 6) {
                            Aviso(
                                texto = "Lo habitual son entre 3 y 6 días. Fuera de ese " +
                                    "rango, la fermentación suele arrancar peor.",
                                color = ColoresEstado.neutro,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                BotonGrande(
                    texto = if (guardando) "Guardando…" else "Guardar la recepción",
                    icono = Icons.Default.Save,
                    habilitado = !guardando,
                ) {
                    val total = leerNumero(mazorcas)?.toInt()
                    if (total == null || total <= 0) {
                        aviso = "Escribe cuántas mazorcas llegaron"
                        return@BotonGrande
                    }
                    guardando = true
                    alcance.launch {
                        runCatching {
                            contenedor.lotes.guardarRecepcion(
                                loteId = loteId,
                                mazorcasTotal = total,
                                pesoKg = leerNumero(peso) ?: 0.0,
                                sacos = leerNumero(sacos)?.toInt() ?: 0,
                                sanas = conteo["sana"] ?: 0,
                                monilia = conteo["monilia"] ?: 0,
                                fitoftora = conteo["fitoftora"] ?: 0,
                                otro = conteo["otro"] ?: 0,
                                descartadas = leerNumero(descartadas)?.toInt() ?: 0,
                                motivoDescarte = motivo.trim(),
                                diasReposo = diasReposo.toInt(),
                            )
                        }.onSuccess { navegacion.popBackStack() }
                            .onFailure { aviso = it.message }
                        guardando = false
                    }
                }
            }
        }
    }
}

/** Contador con botones grandes, para usar con las manos ocupadas. */
@Composable
private fun ContadorClase(clase: String, valor: Int, alCambiar: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Formato.nombreBonito(clase), fontSize = 17.sp, modifier = Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = { alCambiar(valor - 1) },
            enabled = valor > 0,
        ) { Icon(Icons.Default.Remove, contentDescription = "Uno menos") }
        Text(
            "$valor",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp),
        )
        FilledTonalIconButton(
            onClick = { alCambiar(valor + 1) },
        ) { Icon(Icons.Default.Add, contentDescription = "Uno más") }
    }
}
