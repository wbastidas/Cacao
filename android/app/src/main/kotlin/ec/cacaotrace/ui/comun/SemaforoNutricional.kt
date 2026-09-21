package ec.cacaotrace.ui.comun

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ec.cacaotrace.nucleo.etiqueta.Componente
import ec.cacaotrace.nucleo.etiqueta.Nivel
import ec.cacaotrace.nucleo.etiqueta.TablaNutricional

/**
 * El semáforo nutricional tal como va impreso en la barra (RF-EMP-02).
 *
 * Las tres barras llevan **color, palabra e icono** a la vez. En el envase
 * real el color basta porque está normalizado y la gente lo conoce, pero en la
 * pantalla del taller se mira a contraluz y con el teléfono sucio: quien no
 * distingue el rojo del verde tiene que poder leer lo mismo sin el color.
 */
@Composable
fun SemaforoNutricional(
    nutricion: TablaNutricional,
    niveles: Map<Componente, Nivel>,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            "Este producto es",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        Componente.entries.forEach { componente ->
            val nivel = niveles[componente] ?: return@forEach
            BarraSemaforo(componente, nivel, valorDe(componente, nutricion))
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Calculado desde la receta registrada. Para la etiqueta que se vende " +
                "hace falta un análisis bromatológico; esto sirve para saber qué " +
                "esperar y preparar el trámite.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BarraSemaforo(componente: Componente, nivel: Nivel, valor: Double) {
    val color = colorDe(nivel)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .border(1.5.dp, color, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                iconoDe(nivel),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(componente.etiqueta, fontSize = 15.sp)
                Text(
                    "${Formato.numero(valor, 1)} ${componente.unidad} por 100 g",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(nivel.etiqueta, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

/**
 * Los colores del rótulo, no los de estado de la app.
 *
 * Aquí el rojo NO significa "hay un problema": significa "contenido alto",
 * que es lo que dice el reglamento. Un chocolate 90 % sale rojo en grasa y
 * eso está perfectamente bien, así que se usan colores propios y no los de
 * `ColoresEstado` para no sugerir una alarma que no existe.
 */
private fun colorDe(nivel: Nivel): Color = when (nivel) {
    Nivel.BAJO -> Color(0xFF2E7D32)
    Nivel.MEDIO -> Color(0xFFF9A825)
    Nivel.ALTO -> Color(0xFFC62828)
}

private fun iconoDe(nivel: Nivel): ImageVector = when (nivel) {
    Nivel.BAJO -> Icons.Default.ArrowDownward
    Nivel.MEDIO -> Icons.Default.Remove
    Nivel.ALTO -> Icons.Default.ArrowUpward
}

private fun valorDe(componente: Componente, n: TablaNutricional): Double = when (componente) {
    Componente.GRASAS -> n.grasasG
    Componente.AZUCARES -> n.azucaresG
    Componente.SAL -> n.sodioMg
}

/** La tabla nutricional en texto, para la pantalla y para el PDF. */
fun lineasNutricionales(n: TablaNutricional): List<Pair<String, String>> = listOf(
    "Energía" to "${Formato.numero(n.energiaKcal, 0)} kcal " +
        "(${Formato.numero(n.energiaKj, 0)} kJ)",
    "Grasas totales" to "${Formato.numero(n.grasasG, 1)} g",
    "Carbohidratos" to "${Formato.numero(n.carbohidratosG, 1)} g",
    "  de los cuales azúcares" to "${Formato.numero(n.azucaresG, 1)} g",
    "Fibra" to "${Formato.numero(n.fibraG, 1)} g",
    "Proteína" to "${Formato.numero(n.proteinaG, 1)} g",
    "Sodio" to "${Formato.numero(n.sodioMg, 0)} mg",
)
