package ec.cacaotrace.ui.comun

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.cacaotrace.ui.ColoresEstado

/**
 * Aviso en color según su gravedad, con icono Y texto.
 *
 * El color nunca va solo: quien no distingue el rojo del verde tiene que poder
 * leer lo mismo del icono y del texto.
 */
@Composable
fun Aviso(
    texto: String,
    modifier: Modifier = Modifier,
    titulo: String? = null,
    color: Color = ColoresEstado.atencion,
    icono: ImageVector = Icons.Default.WarningAmber,
    accion: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .border(BorderStroke(1.5.dp, color), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icono, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            if (titulo != null) {
                Text(titulo, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = color)
            }
            Text(texto, fontSize = 16.sp)
            if (accion != null) {
                Spacer(Modifier.height(8.dp))
                accion()
            }
        }
    }
}

/** Pregunta de sí o no con botones grandes. */
@Composable
fun DialogoConfirmar(
    titulo: String,
    mensaje: String,
    si: String = "Sí",
    no: String = "Cancelar",
    peligroso: Boolean = false,
    alConfirmar: () -> Unit,
    alCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(titulo) },
        text = { Text(mensaje, fontSize = 16.sp) },
        confirmButton = {
            TextButton(onClick = alConfirmar) {
                Text(
                    si,
                    color = if (peligroso) ColoresEstado.problema else Color.Unspecified,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text(no) } },
    )
}
