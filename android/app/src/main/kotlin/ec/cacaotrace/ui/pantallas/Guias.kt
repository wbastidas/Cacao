package ec.cacaotrace.ui.pantallas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ec.cacaotrace.ui.comun.BarraSuperior

/**
 * Guías para quien empieza (RF-GUI-01).
 *
 * Están dentro de la app y no en un PDF ni en una web a propósito: se
 * consultan en el taller, con el teléfono en la mano, muchas veces sin señal y
 * justo en el momento de la duda. Una guía que hay que ir a buscar fuera no se
 * lee nunca.
 *
 * El texto evita el vocabulario técnico cuando hay una palabra corriente que
 * dice lo mismo, y cada guía termina con el error más común, que es lo que
 * de verdad ahorra un lote perdido.
 */
@Composable
fun PantallaGuias(navegacion: NavHostController) {
    Scaffold(topBar = { BarraSuperior("Guías", navegacion) }) { relleno ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(relleno),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
        ) {
            item {
                Text(
                    "Lo básico de cada etapa, explicado sin tecnicismos. Se pueden leer " +
                        "sin conexión.",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            items(GUIAS, key = { it.titulo }) { guia -> TarjetaGuia(guia) }
        }
    }
}

@Composable
private fun TarjetaGuia(guia: Guia) {
    var abierta by rememberSaveable(guia.titulo) { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { abierta = !abierta },
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(guia.titulo, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        guia.resumen,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    if (abierta) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (abierta) "Cerrar" else "Abrir",
                )
            }

            AnimatedVisibility(visible = abierta) {
                Column(Modifier.padding(top = 12.dp)) {
                    guia.pasos.forEachIndexed { indice, paso ->
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                            Text(
                                "${indice + 1}.",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(28.dp),
                            )
                            Text(paso, fontSize = 16.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("El error más común", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(guia.errorComun, fontSize = 16.sp)
                }
            }
        }
    }
}

/** Una guía: para qué sirve la etapa, cómo se hace y en qué se falla. */
private data class Guia(
    val titulo: String,
    val resumen: String,
    val pasos: List<String>,
    val errorComun: String,
)

private val GUIAS = listOf(
    Guia(
        titulo = "Cosecha y recepción",
        resumen = "Qué mazorca sirve y cuál hay que apartar",
        pasos = listOf(
            "Corta solo las mazorcas maduras: el CCN-51 pasa de verde a amarillo " +
                "anaranjado y suena hueca al darle un golpecito.",
            "Corta con tijera o machete bien afilado, dejando el pedúnculo en el " +
                "árbol. Arrancarla a tirones daña el cojín floral y ahí no vuelve a " +
                "salir fruto.",
            "Aparta las que tengan monilia (manchas marrones duras) o fitóftora " +
                "(manchas oscuras húmedas). No las abras junto a las sanas.",
            "Cuenta y pesa todo al llegar. Ese número es el que después dice si el " +
                "rendimiento cuadra.",
        ),
        errorComun = "Cosechar mazorcas verdes por tener más cantidad. El grano sin " +
            "madurar no fermenta bien y el lote entero sale pizarroso.",
    ),
    Guia(
        titulo = "Reposo de la mazorca",
        resumen = "Por qué esperar 3 a 6 días antes de abrir",
        pasos = listOf(
            "Guarda las mazorcas enteras a la sombra, en un sitio ventilado.",
            "Espera entre 3 y 6 días. En ese tiempo la pulpa pierde algo de agua y " +
                "sube el azúcar, que es de lo que comen las levaduras.",
            "La app te avisa el día que toca abrirlas.",
        ),
        errorComun = "Pasarse de los 7 días. El grano empieza a germinar dentro de la " +
            "mazorca y eso ya no se arregla.",
    ),
    Guia(
        titulo = "Fermentación",
        resumen = "La etapa que decide el sabor del chocolate",
        pasos = listOf(
            "Junta al menos 20 kg de baba. Con menos, el montón no guarda el calor y " +
                "se queda frío.",
            "Tapa el cajón con hoja de plátano o saco de yute. No lo cierres " +
                "herméticamente: necesita algo de aire.",
            "Los dos primeros días huele a alcohol; del día 2 al 4, a vinagre. Eso " +
                "es lo normal.",
            "Voltea cada 24 horas a partir del segundo día, para que fermente parejo.",
            "La temperatura debe subir hasta 45–50 °C entre el día 2 y el 4.",
            "Termina entre el día 5 y el 6. Haz una prueba de corte parcial el día 5 " +
                "para decidir.",
        ),
        errorComun = "Dejarla más de 7 días. Aparece olor a amoniaco, el grano se pasa " +
            "y el chocolate sale con sabor a podrido.",
    ),
    Guia(
        titulo = "Secado",
        resumen = "Bajar del 55 % al 7 % de humedad sin prisa",
        pasos = listOf(
            "Empieza con capa fina, de 3 a 5 cm, y ve subiéndola.",
            "Remueve el grano varias veces al día para que seque parejo.",
            "El primer día no lo pongas al sol fuerte: si la cáscara seca de golpe, " +
                "el ácido se queda dentro y el chocolate sale agrio.",
            "Tarda entre 5 y 7 días. Menos de 4 días casi siempre significa que quedó " +
                "ácido por dentro.",
            "Está listo cuando el grano cruje al partirlo y la humedad queda entre " +
                "6 y 7,5 %.",
        ),
        errorComun = "Guardar el grano con más del 8 % de humedad. En dos semanas hay " +
            "moho, y el moho sí se siente en la barra.",
    ),
    Guia(
        titulo = "Prueba de corte",
        resumen = "Cómo se mide si la fermentación salió bien",
        pasos = listOf(
            "Toma 100 granos al azar del lote, no los más bonitos.",
            "Córtalos por la mitad a lo largo, para ver el interior.",
            "Cuenta cuántos hay de cada tipo: bien fermentado (marrón con surcos), " +
                "ligeramente fermentado, violeta, pizarroso, mohoso, con daño de " +
                "insectos, germinado o vano.",
            "La app calcula los porcentajes y te dice el grado según la norma " +
                "NTE INEN 176.",
            "Puedes contarlos a mano o tomarle una foto al tablero y dejar que la " +
                "app los cuente; siempre puedes corregirla.",
        ),
        errorComun = "Elegir los granos más grandes y bonitos. La prueba deja de servir: " +
            "lo que se quiere saber es cómo está el lote entero.",
    ),
    Guia(
        titulo = "Tostado",
        resumen = "Sacar el sabor sin quemarlo",
        pasos = listOf(
            "Para CCN-51, empieza probando entre 120 y 140 °C durante 15 a 25 minutos.",
            "Escucha: los granos crujen ligeramente cuando van tomando punto.",
            "Pesa antes y después. La merma normal está entre el 5 y el 8 %.",
            "Parte un grano y míralo: debe estar marrón parejo, sin centro claro ni " +
                "bordes negros.",
            "Apunta el perfil que usaste. Es la única forma de repetir un tostado que " +
                "salió bien.",
        ),
        errorComun = "Subir la temperatura para ir más rápido. El grano se quema por " +
            "fuera y sigue crudo por dentro, y eso no se corrige después.",
    ),
    Guia(
        titulo = "Refinado y conchado",
        resumen = "De nib a chocolate liso, en horas",
        pasos = listOf(
            "Precalienta el melanger: si entra frío, la masa no arranca.",
            "Mete los nibs poco a poco hasta que se haga una pasta líquida.",
            "Añade el azúcar cuando la pasta ya fluya, nunca al principio.",
            "Deja de 24 a 48 horas. Prueba cada pocas horas: cuando no notes " +
                "granitos en la lengua, está.",
            "En un 90 % suele hacer falta algo de manteca de cacao para que fluya al " +
                "moldear.",
        ),
        errorComun = "Echar el azúcar al principio. Se apelmaza con los nibs y el " +
            "melanger se atasca.",
    ),
    Guia(
        titulo = "Atemperado",
        resumen = "Las tres temperaturas del brillo",
        pasos = listOf(
            "Funde todo el chocolate hasta unos 45–48 °C.",
            "Enfría hasta 27 °C removiendo, o añadiendo chocolate ya atemperado " +
                "(método de siembra).",
            "Sube otra vez hasta 31–32 °C: esa es la temperatura de trabajo.",
            "Comprueba con la prueba del papel: moja la punta y déjala 3 minutos. " +
                "Si endurece con brillo, está.",
            "Moldea con el cuarto por debajo de 24 °C y menos del 60 % de humedad.",
        ),
        errorComun = "Moldear en un cuarto caliente y húmedo. Por perfecto que sea el " +
            "atemperado, la barra sale mate y con manchas blancas a los pocos días.",
    ),
    Guia(
        titulo = "Almacenamiento",
        resumen = "Cómo no perder un lote ya terminado",
        pasos = listOf(
            "Guarda los sacos sobre pallets, separados de la pared.",
            "Mantén la bodega por debajo del 65 % de humedad relativa.",
            "Nada de combustibles, pinturas ni jabones cerca: el cacao absorbe " +
                "olores con una facilidad asombrosa.",
            "Revisa cada semana: humedad, olor, moho y señales de plagas.",
        ),
        errorComun = "Apoyar los sacos directamente en el piso de cemento. La humedad " +
            "sube por abajo y el saco de abajo se enmohece sin que se vea.",
    ),
)
