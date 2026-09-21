package ec.cacaotrace.ui.comun

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import ec.cacaotrace.ContenedorApp
import ec.cacaotrace.ia.PreparadorImagen
import ec.cacaotrace.nucleo.ia.ResultadoClasificacion
import ec.cacaotrace.ui.ColoresEstado
import java.io.File
import kotlinx.coroutines.launch

/**
 * Lo que devuelve la captura: la foto, lo que dijo el modelo y lo que decidió
 * el usuario, que es lo que manda (RF-IA-03).
 */
data class CapturaAnalizada(
    val archivo: File,
    val decisionUsuario: String,
    val resultado: ResultadoClasificacion? = null,
) {
    val laIaAcerto: Boolean get() = resultado?.mejor?.clase == decisionUsuario

    /** Sirve para reentrenar si el usuario la revisó (§8.3 de la ERS). */
    val aptaParaDataset: Boolean get() = decisionUsuario.isNotBlank()

    val analisisJson: Map<String, String>
        get() = resultado?.mejor?.let {
            mapOf(
                "clase" to it.clase,
                "confianza" to it.confianza.toString(),
                "modelo" to resultado.modeloVersion,
                "ms" to resultado.milisegundos.toString(),
            )
        } ?: emptyMap()
}

/**
 * Botón que abre la cámara, analiza la foto y pide la confirmación del usuario.
 *
 * Si no hay modelo instalado esto NO desaparece: sigue sirviendo para tomar la
 * foto y elegir la respuesta a mano. Esa es la diferencia entre una app que
 * depende de la IA y una que se apoya en ella.
 */
@Composable
fun BotonFotoIa(
    contenedor: ContenedorApp,
    tarea: String,
    clases: List<String>,
    alCapturar: (CapturaAnalizada) -> Unit,
    modifier: Modifier = Modifier,
    texto: String = "Tomar foto",
    consejo: String? = null,
) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val disponible = contenedor.modelos.estaDisponible(tarea)

    var archivoPendiente by remember { mutableStateOf<File?>(null) }
    var resultado by remember { mutableStateOf<ResultadoClasificacion?>(null) }
    var pidiendoDecision by remember { mutableStateOf(false) }

    val tomarFoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { exito ->
        val archivo = archivoPendiente
        if (!exito || archivo == null) return@rememberLauncherForActivityResult
        alcance.launch {
            val comprimida = PreparadorImagen.guardarComprimida(contexto, archivo, tarea)
            archivoPendiente = comprimida
            resultado = if (contenedor.modelos.estaDisponible(tarea)) {
                runCatching {
                    val pixeles = PreparadorImagen.paraModelo(comprimida, 224)
                    pixeles?.let { contenedor.modelos.clasificar(tarea, it) }
                }.getOrNull()
            } else {
                null
            }
            pidiendoDecision = true
        }
    }

    if (pidiendoDecision) {
        val archivo = archivoPendiente
        AlertDialog(
            onDismissRequest = { pidiendoDecision = false },
            title = {
                Text(
                    resultado?.let {
                        if (it.estaSeguro) "La app ve: ${it.texto}" else "No estoy seguro"
                    } ?: "¿Qué es?",
                )
            },
            text = {
                Column {
                    resultado?.let { r ->
                        r.pistaBajaConfianza?.let { Text(it, fontSize = 15.sp) }
                        Text(
                            "Modelo ${r.modeloVersion} · ${r.milisegundos} ms",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } ?: Text(
                        contenedor.modelos.porQueNoEstaDisponible(tarea)?.message
                            ?: "Elige tú qué muestra la foto.",
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Tu respuesta es la que se guarda, y sirve para que el modelo mejore.",
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    clases.forEach { clase ->
                        OutlinedButton(
                            onClick = {
                                pidiendoDecision = false
                                if (archivo != null) {
                                    alCapturar(CapturaAnalizada(archivo, clase, resultado))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Text(
                                Formato.nombreBonito(clase),
                                color = if (resultado?.mejor?.clase == clase) {
                                    ColoresEstado.bien
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pidiendoDecision = false }) { Text("Cancelar") }
            },
        )
    }

    Column(modifier) {
        BotonGrande(
            texto = texto,
            subtitulo = if (disponible) {
                "Se analiza en el teléfono, sin internet"
            } else {
                "Sin modelo instalado: eliges tú la respuesta"
            },
            icono = Icons.Default.PhotoCamera,
        ) {
            val archivo = archivoTemporal(contexto, tarea)
            archivoPendiente = archivo
            tomarFoto.launch(uriDe(contexto, archivo))
        }
        if (consejo != null) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    consejo,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

fun archivoTemporal(contexto: Context, etapa: String): File {
    val carpeta = File(contexto.filesDir, "fotos/$etapa").apply { mkdirs() }
    return File(carpeta, "captura_${System.currentTimeMillis()}.jpg")
}

fun uriDe(contexto: Context, archivo: File): Uri =
    FileProvider.getUriForFile(contexto, "${contexto.packageName}.archivos", archivo)
