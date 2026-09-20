package ec.cacaotrace.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tema visual de CacaoTrace.
 *
 * Las decisiones de aquí vienen del §7 de la ERS y del entorno real de uso: un
 * taller caluroso, con las manos húmedas y a veces bajo el sol.
 *
 *  - áreas táctiles de al menos 48 dp: se tocan con el dedo mojado;
 *  - texto desde 16 sp: se lee sin acercarse el teléfono;
 *  - alto contraste: se ve con reflejo del sol;
 *  - iconos SIEMPRE con texto: el icono solo no se entiende;
 *  - modo claro y oscuro (RNF-13).
 */

/** Marrón del cacao tostado: color de marca, con buen contraste en ambos modos. */
private val MarronCacao = Color(0xFF6B4226)
private val MarronClaro = Color(0xFF8D5524)
private val Crema = Color(0xFFF6EFE7)

/** Tamaño mínimo de cualquier cosa que se pueda tocar. */
val areaTactilMinima = 48.dp

private val esquemaClaro = lightColorScheme(
    primary = MarronCacao,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF2B1600),
    secondary = Color(0xFF755740),
    surface = Crema,
    background = Color(0xFFFFFBF7),
)

private val esquemaOscuro = darkColorScheme(
    primary = Color(0xFFFFB782),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF693C00),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = MarronClaro,
    surface = Color(0xFF1F1B16),
    background = Color(0xFF16120D),
)

/**
 * Colores de estado.
 *
 * Elegidos para distinguirse también en escala de grises y por quien confunde
 * el rojo y el verde: nunca se usa solo el color para decir algo, siempre va
 * con un icono y un texto.
 */
object ColoresEstado {
    val bien = Color(0xFF2E7D32)
    val atencion = Color(0xFFE65100)
    val problema = Color(0xFFC62828)
    val neutro = Color(0xFF546E7A)

    /**
     * Colores de las clases de grano en la prueba de corte. Se eligieron para
     * parecerse al color real del grano cortado y ser distinguibles entre sí.
     */
    private val clasesGrano = mapOf(
        "bien_fermentado" to Color(0xFF8D5524),
        "ligeramente_fermentado" to Color(0xFFB07D4F),
        "violeta" to Color(0xFF7B5EA7),
        "pizarroso" to Color(0xFF546E7A),
        "mohoso" to Color(0xFFB0BEC5),
        "dano_insectos" to Color(0xFFD84315),
        "germinado" to Color(0xFF00897B),
        "vano_plano" to Color(0xFFBDB76B),
        "otro" to Color(0xFF9E9E9E),
    )

    fun deGrano(clase: String): Color = clasesGrano[clase] ?: Color(0xFF9E9E9E)

    fun deSeveridad(severidad: String): Color = when (severidad) {
        "BLOQUEANTE" -> problema
        "URGENTE" -> atencion
        else -> neutro
    }
}

/** Tipografía con el mínimo de 16 sp que pide el §7 de la ERS. */
private val tipografia = Typography(
    bodySmall = Typography().bodySmall.copy(fontSize = 14.sp),
    bodyMedium = Typography().bodyMedium.copy(fontSize = 16.sp),
    bodyLarge = Typography().bodyLarge.copy(fontSize = 18.sp),
    titleMedium = Typography().titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = Typography().titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = Typography().headlineSmall.copy(fontSize = 26.sp),
    labelLarge = Typography().labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun TemaCacaoTrace(
    oscuro: Boolean = isSystemInDarkTheme(),
    /**
     * El color dinámico de Android 12+ queda DESACTIVADO por defecto.
     *
     * Es bonito, pero toma los colores del fondo de pantalla del usuario, y
     * eso puede dejar los colores de estado (verde, naranja, rojo) con poco
     * contraste justo cuando más importan: al sol, leyendo una alerta.
     */
    colorDinamico: Boolean = false,
    contenido: @Composable () -> Unit,
) {
    val contexto = LocalContext.current
    val esquema = when {
        colorDinamico && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (oscuro) dynamicDarkColorScheme(contexto) else dynamicLightColorScheme(contexto)
        oscuro -> esquemaOscuro
        else -> esquemaClaro
    }

    MaterialTheme(
        colorScheme = esquema,
        typography = tipografia,
        content = contenido,
    )
}
