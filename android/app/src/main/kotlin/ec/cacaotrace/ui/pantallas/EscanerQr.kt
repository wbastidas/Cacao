package ec.cacaotrace.ui.pantallas

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import ec.cacaotrace.ui.comun.BarraSuperior
import ec.cacaotrace.ui.comun.EstadoVacio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NoPhotography
import java.util.concurrent.Executors

/** Escáner de QR con guía visual (RF-LOT-04). */
@OptIn(ExperimentalGetImage::class)
@Composable
fun PantallaEscanerQr(alEscanear: (String) -> Unit, alCancelar: () -> Unit) {
    val contexto = LocalContext.current
    val duenoCiclo = LocalLifecycleOwner.current
    var hayPermiso by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    // Una vez leído, se deja de procesar: sin esto el mismo código dispararía
    // la navegación decenas de veces por segundo.
    var yaLeido by remember { mutableStateOf(false) }

    val pedirPermiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido -> hayPermiso = concedido }

    LaunchedEffect(Unit) {
        if (!hayPermiso) pedirPermiso.launch(Manifest.permission.CAMERA)
    }

    val ejecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { ejecutor.shutdown() } }

    Scaffold(topBar = { BarraSuperior("Escanear QR") }) { relleno ->
        if (!hayPermiso) {
            EstadoVacio(
                icono = Icons.Default.NoPhotography,
                titulo = "Sin permiso de cámara",
                explicacion = "Para escanear el QR de un saco hace falta la cámara. " +
                    "Puedes concederlo desde los ajustes del teléfono, o buscar el lote " +
                    "por su código en la lista.",
                modifier = Modifier.fillMaxSize().padding(relleno),
            )
            return@Scaffold
        }

        Box(Modifier.fillMaxSize().padding(relleno)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val vista = PreviewView(ctx)
                    val futuro = ProcessCameraProvider.getInstance(ctx)
                    futuro.addListener({
                        val proveedor = futuro.get()
                        val previa = Preview.Builder().build().also {
                            it.surfaceProvider = vista.surfaceProvider
                        }
                        val lector = BarcodeScanning.getClient()
                        val analisis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(
                                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST,
                            )
                            .build()
                            .also { analizador ->
                                analizador.setAnalyzer(ejecutor) { proxy ->
                                    val imagen = proxy.image
                                    if (imagen == null || yaLeido) {
                                        proxy.close()
                                        return@setAnalyzer
                                    }
                                    lector.process(
                                        InputImage.fromMediaImage(
                                            imagen,
                                            proxy.imageInfo.rotationDegrees,
                                        ),
                                    )
                                        .addOnSuccessListener { codigos ->
                                            val valor = codigos.firstOrNull {
                                                it.format == Barcode.FORMAT_QR_CODE
                                            }?.rawValue
                                            if (!valor.isNullOrBlank() && !yaLeido) {
                                                yaLeido = true
                                                alEscanear(valor)
                                            }
                                        }
                                        .addOnCompleteListener { proxy.close() }
                                }
                            }

                        proveedor.unbindAll()
                        proveedor.bindToLifecycle(
                            duenoCiclo,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            previa,
                            analisis,
                        )
                    }, ContextCompat.getMainExecutor(ctx))
                    vista
                },
            )

            Text(
                "Apunta al código QR del saco o de la barra.\n" +
                    "Se abrirá la historia completa del lote.",
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(24.dp),
            )
        }
    }
}
