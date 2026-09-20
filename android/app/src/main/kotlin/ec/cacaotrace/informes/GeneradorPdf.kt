package ec.cacaotrace.informes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Genera los PDF de la app: el reporte del lote y la plantilla del tablero
 * (RF-REP-01, RF-REP-02, RF-PRC-08).
 *
 * Se usa `android.graphics.pdf.PdfDocument`, que viene en Android desde la
 * versión 4.4, en vez de una librería externa. Para páginas de texto y líneas
 * sobra, y evita añadir varios megas al APK de una app que tiene que caber en
 * un teléfono modesto.
 *
 * El tamaño de página es A4 a 72 puntos por pulgada, que es lo que espera
 * cualquier impresora de oficina en Ecuador.
 */
class GeneradorPdf(private val contexto: Context) {

    /**
     * Escribe el reporte de un lote.
     *
     * Recibe el contenido ya armado en secciones y no los datos crudos: así
     * esta clase solo sabe de dibujar, y qué se cuenta en el reporte se decide
     * en la pantalla, que es donde están las reglas.
     */
    fun reporte(nombre: String, titulo: String, secciones: List<SeccionPdf>): File {
        val documento = PdfDocument()
        var numeroPagina = 1
        var pagina = documento.startPage(descriptor(numeroPagina))
        var lienzo = pagina.canvas
        var y = MARGEN + 24f

        fun saltarPagina() {
            pieDePagina(lienzo, numeroPagina)
            documento.finishPage(pagina)
            numeroPagina++
            pagina = documento.startPage(descriptor(numeroPagina))
            lienzo = pagina.canvas
            y = MARGEN
        }

        fun asegurarEspacio(alto: Float) {
            if (y + alto > ALTO - MARGEN - 24f) saltarPagina()
        }

        lienzo.drawText(titulo, MARGEN, y, pinturaTitulo)
        y += 10f
        lienzo.drawLine(MARGEN, y, ANCHO - MARGEN, y, pinturaLinea)
        y += 24f

        for (seccion in secciones) {
            asegurarEspacio(40f)
            lienzo.drawText(seccion.titulo, MARGEN, y, pinturaSubtitulo)
            y += 18f

            for (linea in seccion.lineas) {
                asegurarEspacio(16f)
                when (linea) {
                    is LineaPdf.Dato -> {
                        lienzo.drawText(linea.etiqueta, MARGEN + 8f, y, pinturaTexto)
                        lienzo.drawText(
                            linea.valor,
                            ANCHO - MARGEN - pinturaValor.measureText(linea.valor),
                            y,
                            pinturaValor,
                        )
                    }
                    is LineaPdf.Parrafo -> {
                        // Se parte a mano porque `drawText` no hace saltos de línea.
                        for (trozo in partir(linea.texto, ANCHO - 2 * MARGEN - 8f)) {
                            asegurarEspacio(16f)
                            lienzo.drawText(trozo, MARGEN + 8f, y, pinturaTexto)
                            y += 15f
                        }
                        y -= 15f
                    }
                }
                y += 16f
            }
            y += 14f
        }

        pieDePagina(lienzo, numeroPagina)
        documento.finishPage(pagina)
        return escribir(documento, nombre)
    }

    /**
     * Plantilla del tablero de 100 granos, con su tarjeta de color
     * (RF-PRC-08).
     *
     * Se imprime, se pegan los granos cortados en las casillas y se le toma
     * una foto. La cuadrícula le da al modelo M2 un punto de referencia
     * constante, y la tarjeta de color permite corregir el tono de la foto:
     * el mismo grano fotografiado a mediodía o bajo un foco amarillo se ve de
     * colores muy distintos, y es justo el color lo que define la clase.
     */
    fun plantillaTablero(nombre: String = "tablero_100_granos.pdf"): File {
        val documento = PdfDocument()
        val pagina = documento.startPage(descriptor(1))
        val lienzo = pagina.canvas

        lienzo.drawText("Tablero de prueba de corte · 100 granos", MARGEN, MARGEN + 20f, pinturaTitulo)
        lienzo.drawText(
            "Corta 100 granos al azar por la mitad y pega uno en cada casilla.",
            MARGEN,
            MARGEN + 40f,
            pinturaTexto,
        )

        // Cuadrícula de 10 × 10, cuadrada, centrada en el ancho útil.
        val lado = (ANCHO - 2 * MARGEN) / 10f
        val arriba = MARGEN + 60f
        val pinturaCelda = Paint().apply {
            color = Color.DKGRAY
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        for (fila in 0..10) {
            val y = arriba + fila * lado
            lienzo.drawLine(MARGEN, y, MARGEN + 10 * lado, y, pinturaCelda)
        }
        for (columna in 0..10) {
            val x = MARGEN + columna * lado
            lienzo.drawLine(x, arriba, x, arriba + 10 * lado, pinturaCelda)
        }

        // Marcas de esquina: son lo que permite al modelo enderezar la foto
        // aunque el tablero salga torcido.
        val pinturaMarca = Paint().apply { color = Color.BLACK }
        val marca = 14f
        listOf(
            MARGEN to arriba,
            MARGEN + 10 * lado - marca to arriba,
            MARGEN to arriba + 10 * lado - marca,
            MARGEN + 10 * lado - marca to arriba + 10 * lado - marca,
        ).forEach { (x, y) ->
            lienzo.drawRect(Rect(x.toInt(), y.toInt(), (x + marca).toInt(), (y + marca).toInt()), pinturaMarca)
        }

        // Tarjeta de color de referencia.
        var y = arriba + 10 * lado + 30f
        lienzo.drawText("Referencia de color", MARGEN, y, pinturaSubtitulo)
        y += 12f
        val anchoParche = (ANCHO - 2 * MARGEN) / COLORES_REFERENCIA.size
        COLORES_REFERENCIA.forEachIndexed { indice, (etiqueta, color) ->
            val x = MARGEN + indice * anchoParche
            lienzo.drawRect(
                Rect(x.toInt(), y.toInt(), (x + anchoParche - 4).toInt(), (y + 36).toInt()),
                Paint().apply { this.color = color },
            )
            lienzo.drawText(etiqueta, x, y + 50f, pinturaPequena)
        }

        y += 80f
        lienzo.drawText(
            "Fotografía el tablero completo, de frente y con luz de día.",
            MARGEN,
            y,
            pinturaTexto,
        )

        documento.finishPage(pagina)
        return escribir(documento, nombre)
    }

    /** Exporta una lista de filas a CSV (RF-REP-03). */
    fun csv(nombre: String, cabecera: List<String>, filas: List<List<String>>): File {
        val archivo = File(carpeta(), nombre)
        archivo.writeText(
            buildString {
                appendLine(cabecera.joinToString(",") { comillas(it) })
                filas.forEach { fila -> appendLine(fila.joinToString(",") { comillas(it) }) }
            },
        )
        return archivo
    }

    // ------------------------------------------------------------ interno

    private fun escribir(documento: PdfDocument, nombre: String): File {
        val archivo = File(carpeta(), nombre)
        FileOutputStream(archivo).use { documento.writeTo(it) }
        documento.close()
        return archivo
    }

    /**
     * Los PDF van a `files/reportes`, que es una de las rutas declaradas en el
     * FileProvider. Fuera de ahí no se podrían compartir.
     */
    private fun carpeta(): File = File(contexto.filesDir, "reportes").apply { mkdirs() }

    private fun descriptor(numero: Int) =
        PdfDocument.PageInfo.Builder(ANCHO.toInt(), ALTO.toInt(), numero).create()

    private fun pieDePagina(lienzo: Canvas, numero: Int) {
        lienzo.drawText(
            "CacaoTrace · página $numero",
            MARGEN,
            ALTO - MARGEN + 14f,
            pinturaPequena,
        )
    }

    /** Parte un texto largo en líneas que quepan en el ancho dado. */
    private fun partir(texto: String, ancho: Float): List<String> {
        val lineas = mutableListOf<String>()
        var actual = StringBuilder()
        for (palabra in texto.split(' ')) {
            val prueba = if (actual.isEmpty()) palabra else "$actual $palabra"
            if (pinturaTexto.measureText(prueba) > ancho && actual.isNotEmpty()) {
                lineas += actual.toString()
                actual = StringBuilder(palabra)
            } else {
                actual = StringBuilder(prueba)
            }
        }
        if (actual.isNotEmpty()) lineas += actual.toString()
        return lineas
    }

    private fun comillas(valor: String): String =
        if (valor.contains(',') || valor.contains('"')) {
            "\"" + valor.replace("\"", "\"\"") + "\""
        } else {
            valor
        }

    private val pinturaTitulo = Paint().apply {
        color = Color.BLACK
        textSize = 18f
        isFakeBoldText = true
    }
    private val pinturaSubtitulo = Paint().apply {
        color = Color.rgb(0x6B, 0x42, 0x26)
        textSize = 14f
        isFakeBoldText = true
    }
    private val pinturaTexto = Paint().apply {
        color = Color.BLACK
        textSize = 11f
    }
    private val pinturaValor = Paint().apply {
        color = Color.BLACK
        textSize = 11f
        isFakeBoldText = true
    }
    private val pinturaPequena = Paint().apply {
        color = Color.GRAY
        textSize = 9f
    }
    private val pinturaLinea = Paint().apply {
        color = Color.rgb(0x6B, 0x42, 0x26)
        strokeWidth = 2f
    }

    companion object {
        /** A4 a 72 ppp, en puntos. */
        private const val ANCHO = 595f
        private const val ALTO = 842f
        private const val MARGEN = 40f

        /**
         * Colores de referencia del tablero, elegidos para parecerse al tono
         * real del grano cortado en cada clase.
         */
        private val COLORES_REFERENCIA = listOf(
            "Bien fermentado" to Color.rgb(0x8D, 0x55, 0x24),
            "Ligero" to Color.rgb(0xB0, 0x7D, 0x4F),
            "Violeta" to Color.rgb(0x7B, 0x5E, 0xA7),
            "Pizarroso" to Color.rgb(0x54, 0x6E, 0x7A),
            "Blanco" to Color.WHITE,
            "Negro" to Color.BLACK,
        )
    }
}

/** Una sección del reporte, con su título y sus líneas. */
data class SeccionPdf(val titulo: String, val lineas: List<LineaPdf>)

/** Una línea del reporte: o un dato con su valor, o un párrafo corrido. */
sealed interface LineaPdf {
    data class Dato(val etiqueta: String, val valor: String) : LineaPdf
    data class Parrafo(val texto: String) : LineaPdf
}
