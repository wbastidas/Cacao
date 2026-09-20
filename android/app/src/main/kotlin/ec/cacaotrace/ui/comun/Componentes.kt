package ec.cacaotrace.ui.comun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.cacaotrace.ui.ColoresEstado

/**
 * Piezas de interfaz que se repiten en toda la app.
 *
 * Tenerlas en un solo sitio es lo que hace que la app se sienta coherente: el
 * mismo botón grande, la misma forma de pedir un número, el mismo aviso
 * cuando algo no está.
 */

/** Botón grande de acción principal, con icono Y texto (§7 de la ERS). */
@Composable
fun BotonGrande(
    texto: String,
    icono: ImageVector,
    modifier: Modifier = Modifier,
    subtitulo: String? = null,
    color: Color? = null,
    habilitado: Boolean = true,
    alPulsar: () -> Unit,
) {
    Button(
        onClick = alPulsar,
        enabled = habilitado,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = if (color == null) {
            ButtonDefaults.buttonColors()
        } else {
            ButtonDefaults.buttonColors(containerColor = color)
        },
    ) {
        Icon(icono, contentDescription = null, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.Start) {
            Text(texto, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (subtitulo != null) {
                Text(subtitulo, fontSize = 13.sp, fontWeight = FontWeight.Normal)
            }
        }
    }
}

/**
 * Campo para pedir un número, con teclado numérico.
 *
 * Acepta coma o punto como separador decimal: en Ecuador se usan los dos y el
 * usuario no tiene por qué saber cuál espera la app.
 */
@Composable
fun CampoNumero(
    etiqueta: String,
    valor: String,
    alCambiar: (String) -> Unit,
    modifier: Modifier = Modifier,
    unidad: String = "",
    ayuda: String? = null,
    decimales: Boolean = true,
    error: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        OutlinedTextField(
            value = valor,
            onValueChange = { nuevo ->
                val permitidos = if (decimales) "0123456789.," else "0123456789"
                alCambiar(nuevo.filter { it in permitidos })
            },
            label = { Text(etiqueta) },
            suffix = if (unidad.isEmpty()) null else ({ Text(unidad) }),
            supportingText = when {
                error != null -> ({ Text(error, color = ColoresEstado.problema) })
                ayuda != null -> ({ Text(ayuda) })
                else -> null
            },
            isError = error != null,
            singleLine = true,
            textStyle = TextStyle(fontSize = 20.sp),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimales) KeyboardType.Decimal else KeyboardType.Number,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Lee un campo de número tolerando la coma decimal. */
fun leerNumero(texto: String): Double? = texto.trim().replace(',', '.').toDoubleOrNull()

/**
 * Lo que se muestra cuando una lista está vacía.
 *
 * Nunca se deja una pantalla en blanco: siempre se dice qué falta y cuál es el
 * siguiente paso.
 */
@Composable
fun EstadoVacio(
    icono: ImageVector,
    titulo: String,
    explicacion: String,
    modifier: Modifier = Modifier,
    accion: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icono,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(16.dp))
        Text(titulo, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            explicacion,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (accion != null) {
            Spacer(Modifier.height(24.dp))
            accion()
        }
    }
}

/** Tarjeta con título, para agrupar información. */
@Composable
fun TarjetaSeccion(
    titulo: String,
    modifier: Modifier = Modifier,
    icono: ImageVector? = null,
    accion: (@Composable () -> Unit)? = null,
    contenido: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icono != null) {
                    Icon(icono, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    titulo,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                accion?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            contenido()
        }
    }
}

/** Una fila "etiqueta … valor", que es como se lee un dato de un vistazo. */
@Composable
fun FilaDato(
    etiqueta: String,
    valor: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(etiqueta, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            valor,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = color ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}
