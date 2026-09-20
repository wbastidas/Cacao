package ec.cacaotrace.ui.pantallas

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sanitizer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ec.cacaotrace.ContenedorApp
import ec.cacaotrace.datos.bd.entidades.ChecklistBpmEntidad
import ec.cacaotrace.datos.repositorios.RegistroBpmCerrado
import ec.cacaotrace.informes.GeneradorPdf
import ec.cacaotrace.informes.LineaPdf
import ec.cacaotrace.informes.SeccionPdf
import ec.cacaotrace.ui.ColoresEstado
import ec.cacaotrace.ui.comun.Aviso
import ec.cacaotrace.ui.comun.BarraSuperior
import ec.cacaotrace.ui.comun.BotonGrande
import ec.cacaotrace.ui.comun.Formato
import ec.cacaotrace.ui.comun.TarjetaSeccion
import ec.cacaotrace.ui.comun.uriDe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Buenas prácticas de manufactura (RF-BPM-01, RF-BPM-02).
 *
 * El registro del día se puede corregir mientras el día dure. Pasadas 24 horas
 * se cierra y solo admite anotaciones: un registro que se puede reescribir
 * para siempre no prueba nada ante una inspección, y ese es justo el motivo
 * por el que se lleva.
 */
@Composable
fun PantallaBpm(contenedor: ContenedorApp, navegacion: NavHostController) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val generador = remember { GeneradorPdf(contexto) }
    val checklists by contenedor.apoyo.observarChecklists().collectAsState(initial = emptyList())
    var mensaje by remember { mutableStateOf<String?>(null) }
    var recargar by remember { mutableStateOf(0) }
    var exportando by remember { mutableStateOf(false) }

    // Se cierran los vencidos al entrar: si la app no se abrió ayer, el
    // registro de ayer tiene que quedar cerrado igualmente.
    LaunchedEffect(Unit) { contenedor.apoyo.cerrarRegistrosBpmVencidos() }

    Scaffold(topBar = { BarraSuperior("Buenas prácticas", navegacion) }) { relleno ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            mensaje?.let {
                Aviso(texto = it, color = ColoresEstado.bien, icono = Icons.Default.CheckCircle)
            }

            if (checklists.isEmpty()) {
                Text(
                    "No hay listas de comprobación configuradas.",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }

            checklists.filter { it.activo }.forEach { checklist ->
                FormularioChecklist(contenedor, checklist, recargar) { texto ->
                    mensaje = texto
                    recargar++
                }
            }

            // RF-BPM-03: el PDF es lo que se enseña en una inspección de ARCSA.
            Spacer(Modifier.height(16.dp))
            BotonGrande(
                texto = "Exportar los registros en PDF",
                subtitulo = "Para presentarlos en una inspección",
                icono = Icons.Default.PictureAsPdf,
                habilitado = !exportando,
            ) {
                exportando = true
                alcance.launch {
                    val registros = contenedor.apoyo.todosLosRegistrosBpm()
                    val nombres = checklists.associate { it.id to it.nombre }
                    val archivo = withContext(Dispatchers.IO) {
                        generador.reporte(
                            nombre = "registros_bpm.pdf",
                            titulo = "Registros de buenas prácticas",
                            secciones = registros
                                .groupBy { it.checklistId }
                                .map { (checklistId, lista) ->
                                    SeccionPdf(
                                        titulo = nombres[checklistId] ?: "Lista",
                                        lineas = lista.map { r ->
                                            LineaPdf.Dato(
                                                Formato.fechaHora(r.fecha),
                                                buildString {
                                                    append(r.responsable.ifBlank { "sin firmar" })
                                                    if (r.cerrado) append(" · cerrado")
                                                    if (r.observaciones.isNotBlank()) {
                                                        append(" · ${r.observaciones}")
                                                    }
                                                },
                                            )
                                        },
                                    )
                                },
                        )
                    }
                    exportando = false
                    mensaje = "PDF generado: ${archivo.name}"
                    val intento = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uriDe(contexto, archivo))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    contexto.startActivity(
                        Intent.createChooser(intento, "Compartir ${archivo.name}"),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun FormularioChecklist(
    contenedor: ContenedorApp,
    checklist: ChecklistBpmEntidad,
    recargar: Int,
    alGuardar: (String) -> Unit,
) {
    val alcance = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true } }

    val puntos = remember(checklist.itemsJson) {
        runCatching {
            json.parseToJsonElement(checklist.itemsJson).jsonArray.map { it.jsonPrimitive.content }
        }.getOrDefault(emptyList())
    }

    val respuestas = remember(checklist.id) { mutableStateMapOf<String, Boolean>() }
    var responsable by remember(checklist.id) { mutableStateOf("") }
    var observaciones by remember(checklist.id) { mutableStateOf("") }
    var cerrado by remember(checklist.id) { mutableStateOf(false) }
    var registroId by remember(checklist.id) { mutableStateOf<String?>(null) }
    var anotaciones by remember(checklist.id) { mutableStateOf<List<String>>(emptyList()) }
    var anotando by remember { mutableStateOf(false) }

    LaunchedEffect(checklist.id, recargar) {
        val registro = contenedor.apoyo.registroDeHoy(checklist.id)
        registroId = registro?.id
        cerrado = registro?.cerrado ?: false
        responsable = registro?.responsable.orEmpty()
        observaciones = registro?.observaciones.orEmpty()

        respuestas.clear()
        val guardadas = registro?.respuestasJson
        if (guardadas != null) {
            runCatching {
                json.parseToJsonElement(guardadas).jsonObject.forEach { (punto, valor) ->
                    respuestas[punto] = valor.jsonPrimitive.content.toBoolean()
                }
            }
        }
        puntos.forEach { respuestas.putIfAbsent(it, false) }

        anotaciones = registro?.anotacionesJson?.let { texto ->
            runCatching {
                json.parseToJsonElement(texto).jsonArray.map { elemento ->
                    val objeto = elemento.jsonObject
                    val fecha = objeto["fecha"]?.jsonPrimitive?.content.orEmpty()
                    val nota = objeto["texto"]?.jsonPrimitive?.content.orEmpty()
                    if (fecha.isBlank()) nota else "$fecha — $nota"
                }
            }.getOrDefault(emptyList())
        }.orEmpty()
    }

    val cumplidos = respuestas.values.count { it }

    TarjetaSeccion(checklist.nombre, icono = Icons.Default.Sanitizer) {
        Text(
            "${checklist.frecuencia.replaceFirstChar { it.uppercase() }} · " +
                "$cumplidos de ${puntos.size} puntos",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (cerrado) {
            Aviso(
                titulo = "Registro cerrado",
                texto = "Pasaron más de 24 horas. Ya no se puede cambiar, pero sí " +
                    "puedes añadir una anotación explicando lo que corresponda.",
                color = ColoresEstado.neutro,
                icono = Icons.Default.Lock,
            )
        }

        Spacer(Modifier.height(8.dp))
        puntos.forEach { punto ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = respuestas[punto] == true,
                    onCheckedChange = { if (!cerrado) respuestas[punto] = it },
                    enabled = !cerrado,
                )
                Spacer(Modifier.width(4.dp))
                Text(punto, fontSize = 16.sp, modifier = Modifier.weight(1f))
            }
        }

        OutlinedTextField(
            value = responsable,
            onValueChange = { responsable = it },
            label = { Text("¿Quién lo revisó?") },
            enabled = !cerrado,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        OutlinedTextField(
            value = observaciones,
            onValueChange = { observaciones = it },
            label = { Text("Observaciones") },
            enabled = !cerrado,
            minLines = 2,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )

        if (anotaciones.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Anotaciones posteriores", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            anotaciones.forEach {
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(it, fontSize = 14.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (cerrado) {
            BotonGrande(
                texto = "Añadir una anotación",
                icono = Icons.Default.EditNote,
                habilitado = registroId != null,
            ) { anotando = true }
        } else {
            BotonGrande(
                texto = "Guardar el registro de hoy",
                icono = Icons.Default.Save,
                habilitado = responsable.isNotBlank(),
            ) {
                alcance.launch {
                    runCatching {
                        contenedor.apoyo.guardarRegistroBpm(
                            checklistId = checklist.id,
                            respuestas = respuestas.toMap(),
                            responsable = responsable.trim(),
                            observaciones = observaciones.trim(),
                        )
                    }.onSuccess {
                        alGuardar("Registro de «${checklist.nombre}» guardado.")
                    }.onFailure { fallo ->
                        // El único fallo esperable es que se haya cerrado mientras
                        // el usuario lo rellenaba; se le dice con sus palabras.
                        alGuardar(
                            (fallo as? RegistroBpmCerrado)?.message
                                ?: "No se pudo guardar el registro.",
                        )
                    }
                }
            }
        }
    }

    if (anotando) {
        DialogoAnotacion(
            alGuardar = { texto ->
                anotando = false
                val id = registroId ?: return@DialogoAnotacion
                alcance.launch {
                    contenedor.apoyo.anotarEnRegistroBpm(id, texto)
                    alGuardar("Anotación añadida.")
                }
            },
            alCancelar = { anotando = false },
        )
    }
}

@Composable
private fun DialogoAnotacion(alGuardar: (String) -> Unit, alCancelar: () -> Unit) {
    var texto by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Anotación") },
        text = {
            Column {
                Text(
                    "El registro no se modifica: la anotación se añade al final, con " +
                        "la fecha de hoy.",
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    label = { Text("¿Qué pasó?") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { alGuardar(texto.trim()) },
                enabled = texto.isNotBlank(),
            ) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}
