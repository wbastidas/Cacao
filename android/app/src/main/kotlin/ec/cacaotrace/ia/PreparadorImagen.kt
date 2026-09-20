package ec.cacaotrace.ia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Prepara las fotos para el modelo y para guardarlas.
 *
 * Dos cosas que parecen detalles y no lo son:
 *
 *  1. **La rotación EXIF.** Una foto vertical del teléfono se guarda apaisada
 *     con una etiqueta que dice "gírala". Si no se aplica, el modelo ve la
 *     mazorca acostada y falla, aunque en la galería se vea bien.
 *  2. **El tamaño.** El §3.1 de la ERS pide máximo 1600 px y calidad 85: una
 *     foto de 12 MP ocupa 4 MB y llena el teléfono en un par de lotes.
 */
object PreparadorImagen {

    const val LADO_MAXIMO_GUARDADO = 1600
    const val CALIDAD_JPEG = 85

    /**
     * Convierte la foto en los píxeles RGB 0-255 que espera el modelo.
     *
     * Devuelve null si el archivo no es una imagen legible, para que la
     * pantalla pueda avisar en vez de reventar.
     */
    suspend fun paraModelo(archivo: File, lado: Int): ByteArray? =
        withContext(Dispatchers.Default) {
            val original = BitmapFactory.decodeFile(archivo.absolutePath) ?: return@withContext null
            val derecha = aplicarRotacionExif(archivo, original)
            val escalada = Bitmap.createScaledBitmap(derecha, lado, lado, true)

            val pixeles = IntArray(lado * lado)
            escalada.getPixels(pixeles, 0, lado, 0, 0, lado, lado)

            val salida = ByteArray(lado * lado * 3)
            var i = 0
            for (p in pixeles) {
                salida[i++] = ((p shr 16) and 0xFF).toByte()
                salida[i++] = ((p shr 8) and 0xFF).toByte()
                salida[i++] = (p and 0xFF).toByte()
            }

            if (escalada != derecha) escalada.recycle()
            if (derecha != original) derecha.recycle()
            original.recycle()
            salida
        }

    /**
     * Guarda la foto comprimida en la carpeta privada de la app y devuelve el
     * archivo final. La original de la cámara se borra.
     */
    suspend fun guardarComprimida(
        contexto: Context,
        origen: File,
        etapa: String,
    ): File = withContext(Dispatchers.IO) {
        val carpeta = File(contexto.filesDir, "fotos/$etapa").apply { mkdirs() }
        val destino = File(carpeta, "${System.currentTimeMillis()}.jpg")

        val original = BitmapFactory.decodeFile(origen.absolutePath)
            ?: return@withContext origen
        val derecha = aplicarRotacionExif(origen, original)
        val reducida = reducirA(derecha, LADO_MAXIMO_GUARDADO)

        FileOutputStream(destino).use { salida ->
            reducida.compress(Bitmap.CompressFormat.JPEG, CALIDAD_JPEG, salida)
        }

        if (reducida != derecha) reducida.recycle()
        if (derecha != original) derecha.recycle()
        original.recycle()
        if (origen != destino) origen.delete()
        destino
    }

    /** Miniatura para las listas, que no necesitan la foto entera. */
    suspend fun miniatura(contexto: Context, foto: File, lado: Int = 240): File? =
        withContext(Dispatchers.IO) {
            val original = BitmapFactory.decodeFile(foto.absolutePath) ?: return@withContext null
            val pequena = reducirA(original, lado)
            val carpeta = File(contexto.filesDir, "fotos/miniaturas").apply { mkdirs() }
            val destino = File(carpeta, foto.name)
            FileOutputStream(destino).use { pequena.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            if (pequena != original) pequena.recycle()
            original.recycle()
            destino
        }

    private fun reducirA(bitmap: Bitmap, ladoMaximo: Int): Bitmap {
        val mayor = maxOf(bitmap.width, bitmap.height)
        if (mayor <= ladoMaximo) return bitmap
        val escala = ladoMaximo.toFloat() / mayor
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * escala).toInt(),
            (bitmap.height * escala).toInt(),
            true,
        )
    }

    private fun aplicarRotacionExif(archivo: File, bitmap: Bitmap): Bitmap {
        val orientacion = runCatching {
            ExifInterface(archivo.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matriz = Matrix()
        when (orientacion) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matriz.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matriz.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matriz.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matriz.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matriz.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
    }
}
