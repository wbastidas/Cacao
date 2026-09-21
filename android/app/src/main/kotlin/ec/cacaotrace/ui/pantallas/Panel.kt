package ec.cacaotrace.ui.pantallas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ec.cacaotrace.ContenedorApp
import ec.cacaotrace.datos.repositorios.PuntoSerie
import ec.cacaotrace.datos.repositorios.SeriesPanel
import ec.cacaotrace.ui.ColoresEstado
import ec.cacaotrace.ui.Rutas
import ec.cacaotrace.ui.comun.BarraSuperior
import ec.cacaotrace.ui.comun.Formato
import ec.cacaotrace.ui.comun.TarjetaSeccion

/**
 * Panel de indicadores (RF-TAB-02, RF-TAB-03).
 *
 * Los gráficos se dibujan con `Canvas` y no con una librería de terceros. No
 * es purismo: son cuatro gráficos sencillos, y una dependencia menos es una
 * cosa menos que se rompa al actualizar, en una app que tiene que seguir
 * funcionando años sin tocarla.
 *
 * Cada gráfico lleva SIEMPRE sus números escritos al lado. Un gráfico que solo
 * se entiende mirando las alturas no sirve al sol, ni a quien no distingue
 * bien los colores.
 */
@Composable
fun PantallaPanel(contenedor: ContenedorApp, navegacion: NavHostController) {
    var indicadores by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var series by remember { mutableStateOf(SeriesPanel()) }

    LaunchedEffect(Unit) {
        indicadores = contenedor.apoyo.indicadores()
        series = contenedor.apoyo.seriesDelPanel()
    }

    Scaffold(
        topBar = {
            BarraSuperior("Panel", navegacion) {
                IconButton(onClick = { navegacion.navigate(Rutas.COMPARAR) }) {
                    Icon(
                        Icons.Default.CompareArrows,
                        contentDescription = "Comparar dos lotes",
                    )
                }
            }
        },
    ) { relleno ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // ------------------------------------------------ indicadores
            TarjetaSeccion("Cómo va todo", icono = Icons.Default.Insights) {
                if (indicadores.isEmpty()) {
                    Text("Aún no hay datos suficientes.", fontSize = 16.sp)
                } else {
                    indicadores.forEach { (nombre, valor) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(nombre, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text(valor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // -------------------------- fermentación alcanzada por lote
            if (series.fermentadoPorLote.isNotEmpty()) {
                TarjetaSeccion("Fermentación alcanzada por lote", icono = Icons.Default.Science) {
                    Text(
                        "La norma pide al menos un 76 % de grano fermentado para que el " +
                            "lote sea conforme. La línea marca ese punto.",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    GraficoBarras(
                        puntos = series.fermentadoPorLote,
                        maximo = 100.0,
                        referencia = 76.0,
                        sufijo = " %",
                        colorDe = { p ->
                            if (p.valor >= 76) ColoresEstado.bien else ColoresEstado.atencion
                        },
                    )
                }
            }

            // ------------------------------------ defectos más frecuentes
            if (series.defectosPromedio.isNotEmpty()) {
                TarjetaSeccion("Defectos más frecuentes", icono = Icons.Default.WarningAmber) {
                    Text(
                        "Promedio de todas las pruebas de corte. El defecto que más se " +
                            "repite es el que hay que atacar primero.",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    GraficoBarras(
                        puntos = series.defectosPromedio.map {
                            PuntoSerie(Formato.nombreBonito(it.etiqueta), it.valor)
                        },
                        maximo = series.defectosPromedio.maxOf { it.valor }.coerceAtLeast(1.0),
                        sufijo = " %",
                        colorDe = { ColoresEstado.problema },
                    )
                }
            }

            // -------------------------------- curva de temperatura actual
            if (series.curvaFermentacion.size >= 2) {
                TarjetaSeccion(
                    "Temperatura de la fermentación en curso",
                    icono = Icons.Default.Thermostat,
                ) {
                    Text(
                        buildString {
                            if (series.loteDeLaCurva.isNotBlank()) {
                                append("Lote ${series.loteDeLaCurva}. ")
                            }
                            append(
                                "Debe subir hasta unos 45–50 °C entre el día 2 y el 4. " +
                                    "Si se queda plana y fría, la masa es poca o hace frío.",
                            )
                        },
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    GraficoLinea(series.curvaFermentacion)
                }
            }

            // ------------------------------------------- grados obtenidos
            if (series.gradoPorLote.isNotEmpty()) {
                TarjetaSeccion("Grados obtenidos", icono = Icons.Default.Insights) {
                    series.gradoPorLote.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(p.etiqueta, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text(
                                "${p.valor.toInt()} lote(s)",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            if (series.fermentadoPorLote.isEmpty() && series.curvaFermentacion.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        "Los gráficos aparecen solos cuando haya datos: la curva en cuanto " +
                            "midas la primera temperatura, y las barras en cuanto hagas la " +
                            "primera prueba de corte completa.",
                        fontSize = 15.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Barras horizontales con el número escrito al lado.
 *
 * Horizontales y no verticales porque las etiquetas son códigos de lote y
 * nombres de defecto: en vertical no caben y acaban girados o cortados.
 */
@Composable
private fun GraficoBarras(
    puntos: List<PuntoSerie>,
    maximo: Double,
    sufijo: String = "",
    referencia: Double? = null,
    colorDe: (PuntoSerie) -> Color,
) {
    val colorReferencia = MaterialTheme.colorScheme.outline
    val fondoBarra = MaterialTheme.colorScheme.surfaceVariant

    puntos.forEach { punto ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                punto.etiqueta,
                fontSize = 14.sp,
                modifier = Modifier.width(96.dp),
                maxLines = 2,
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).height(24.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val radio = 6.dp.toPx()
                    drawRoundRect(
                        color = fondoBarra,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radio, radio),
                    )
                    val fraccion = (punto.valor / maximo).coerceIn(0.0, 1.0).toFloat()
                    if (fraccion > 0) {
                        drawRoundRect(
                            color = colorDe(punto),
                            size = size.copy(width = size.width * fraccion),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radio, radio),
                        )
                    }
                    referencia?.let { valor ->
                        val x = (valor / maximo).coerceIn(0.0, 1.0).toFloat() * size.width
                        drawLine(
                            color = colorReferencia,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 2.dp.toPx(),
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "${Formato.numero(punto.valor, 1)}$sufijo",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(60.dp),
            )
        }
    }
}

/**
 * Curva de temperatura con las horas en el eje de abajo.
 *
 * Se marcan las bandas de la norma con color de fondo suave, para que se vea
 * de un vistazo si la fermentación está donde debe sin leer ningún número.
 */
@Composable
private fun GraficoLinea(puntos: List<PuntoSerie>) {
    val minimo = puntos.minOf { it.valor }.coerceAtMost(20.0)
    val maximo = puntos.maxOf { it.valor }.coerceAtLeast(52.0)
    val rango = (maximo - minimo).coerceAtLeast(1.0)

    val colorLinea = MaterialTheme.colorScheme.primary
    val colorBanda = ColoresEstado.bien.copy(alpha = 0.15f)
    val colorEje = MaterialTheme.colorScheme.outlineVariant

    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(vertical = 4.dp),
        ) {
            fun y(valor: Double): Float =
                (size.height * (1 - (valor - minimo) / rango)).toFloat()

            // Banda 45–50 °C: el rango donde debe estar la masa en el pico.
            drawRect(
                color = colorBanda,
                topLeft = Offset(0f, y(50.0)),
                size = size.copy(height = (y(45.0) - y(50.0)).coerceAtLeast(1f)),
            )
            drawLine(
                color = colorEje,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.5.dp.toPx(),
            )

            val paso = if (puntos.size > 1) size.width / (puntos.size - 1) else size.width
            val camino = Path()
            puntos.forEachIndexed { indice, punto ->
                val x = paso * indice
                val altura = y(punto.valor)
                if (indice == 0) camino.moveTo(x, altura) else camino.lineTo(x, altura)
                drawCircle(color = colorLinea, radius = 4.dp.toPx(), center = Offset(x, altura))
            }
            drawPath(
                path = camino,
                color = colorLinea,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${puntos.first().etiqueta} h", fontSize = 13.sp)
            Text(
                "máx ${Formato.grados(puntos.maxOf { it.valor })}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text("${puntos.last().etiqueta} h", fontSize = 13.sp)
        }
    }
}
