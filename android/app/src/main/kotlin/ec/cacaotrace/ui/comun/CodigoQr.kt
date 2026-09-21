package ec.cacaotrace.ui.comun

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Dibuja el código QR de un lote o de un saco (RF-LOT-01, RF-ALM-01).
 *
 * Se usa corrección de errores ALTA a propósito: estos códigos se imprimen en
 * papel corriente y se pegan en un saco de yute que se moja, se ensucia y se
 * raspa. Con corrección alta el código sigue leyéndose aunque se pierda hasta
 * un 30 % de su superficie.
 */
@Composable
fun CodigoQr(
    contenido: String,
    modifier: Modifier = Modifier,
    lado: Int = 512,
) {
    val bitmap = remember(contenido, lado) { generarQr(contenido, lado) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Código QR de $contenido",
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    }
}

fun generarQr(contenido: String, lado: Int = 512): Bitmap? = runCatching {
    val matriz = QRCodeWriter().encode(
        contenido,
        BarcodeFormat.QR_CODE,
        lado,
        lado,
        mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        ),
    )
    val bitmap = Bitmap.createBitmap(lado, lado, Bitmap.Config.RGB_565)
    for (x in 0 until lado) {
        for (y in 0 until lado) {
            bitmap.setPixel(x, y, if (matriz[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    bitmap
}.getOrNull()
