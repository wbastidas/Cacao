package ec.cacaotrace.ui.pantallas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ec.cacaotrace.ui.ColoresEstado
import ec.cacaotrace.ui.comun.Aviso
import ec.cacaotrace.ui.comun.BarraSuperior
import ec.cacaotrace.ui.comun.TarjetaSeccion

/**
 * Aviso de privacidad (RNF-06).
 *
 * Está escrito para que se entienda leyéndolo una vez, no para cubrirse las
 * espaldas. La Ley Orgánica de Protección de Datos Personales de Ecuador
 * (LOPDP, 2021) exige informar de forma clara y en lenguaje sencillo, y en una
 * app que usa una sola persona en su propio taller, el texto honesto y el
 * texto legalmente correcto son casi el mismo.
 *
 * Lo esencial cabe en una frase: **los datos son del productor y no salen de
 * su teléfono salvo que él conecte su propia nube**.
 */
@Composable
fun PantallaPrivacidad(navegacion: NavHostController) {
    Scaffold(topBar = { BarraSuperior("Privacidad", navegacion) }) { relleno ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Aviso(
                titulo = "Lo importante, en corto",
                texto = "Tus datos son tuyos y se quedan en tu teléfono. No hay " +
                    "servidores nuestros, no se venden a nadie y no hay publicidad. " +
                    "Si algún día conectas una copia en la nube, va a TU cuenta, no " +
                    "a la de quien hizo la app.",
                color = ColoresEstado.bien,
                icono = Icons.Default.Lock,
            )

            TarjetaSeccion("Qué se guarda", icono = Icons.Default.PhoneAndroid) {
                Parrafo(
                    "Todo lo que registras del proceso: lotes, pesos, temperaturas, " +
                        "pruebas de corte, costos, ventas, checklists de buenas " +
                        "prácticas y resultados de laboratorio."
                )
                Parrafo(
                    "Las fotos que tomas con la app, junto con lo que el modelo " +
                        "respondió y lo que tú corregiste."
                )
                Parrafo(
                    "Si lo autorizas, la zona aproximada donde se tomó una foto. Es " +
                        "opcional, se pide una sola vez, y sirve para poder demostrar " +
                        "de qué finca vino un lote. Nunca se pide la ubicación precisa."
                )
                Parrafo(
                    "Un identificador aleatorio de este teléfono, para saber de dónde " +
                        "vino un cambio si algún día sincronizas varios dispositivos. " +
                        "No identifica a ninguna persona."
                )
            }

            TarjetaSeccion("Qué NO se guarda", icono = Icons.Default.CloudOff) {
                Parrafo("No se pide tu nombre, tu cédula, tu correo ni tu teléfono.")
                Parrafo("No se recoge tu lista de contactos ni tus otras apps.")
                Parrafo(
                    "No hay estadísticas de uso, ni rastreadores, ni publicidad. " +
                        "Nadie recibe un aviso cuando abres la app."
                )
                Parrafo(
                    "No se estima humedad ni cadmio con fotos: eso se mide con " +
                        "medidor y con laboratorio acreditado, y la app solo guarda " +
                        "el resultado que tú anotas."
                )
            }

            TarjetaSeccion("Dónde vive todo esto", icono = Icons.Default.Folder) {
                Parrafo(
                    "En la memoria privada de la app, dentro de tu teléfono. Ninguna " +
                        "otra app puede leerla."
                )
                Parrafo(
                    "La copia de seguridad automática de Android está DESACTIVADA a " +
                        "propósito para esta app: tus registros de producción no se " +
                        "copian sin que tú lo decidas."
                )
                Parrafo(
                    "Si conectas la copia en la nube, los registros van a tu propia " +
                        "cuenta y las fotos a tu propio Google Drive. La app pide el " +
                        "permiso más estrecho que existe para eso, que solo le deja " +
                        "ver los archivos que ella misma creó: no puede abrir el " +
                        "resto de tu Drive."
                )
                Parrafo(
                    "Quien hizo la app no tiene acceso a nada de eso, porque no hay " +
                        "ningún servidor intermedio."
                )
            }

            TarjetaSeccion("Tus derechos", icono = Icons.Default.Gavel) {
                Parrafo(
                    "Como los datos están en tu teléfono, los controlas directamente: " +
                        "puedes exportarlos a PDF y CSV desde la pantalla de reportes, " +
                        "y puedes borrarlo todo desinstalando la app."
                )
                Parrafo(
                    "Si compartes un reporte con un comprador o con una autoridad, lo " +
                        "haces tú y decides qué mandas."
                )
                Parrafo(
                    "La Ley Orgánica de Protección de Datos Personales del Ecuador te " +
                        "reconoce los derechos de acceso, rectificación, eliminación y " +
                        "portabilidad. Aquí los ejerces sin pedirle permiso a nadie."
                )
            }

            TarjetaSeccion("Si empleas a alguien", icono = Icons.Default.Gavel) {
                Parrafo(
                    "Los registros de buenas prácticas llevan el nombre de quien los " +
                        "diligenció, porque eso es lo que les da valor ante una " +
                        "inspección."
                )
                Parrafo(
                    "Si anotas ahí a otra persona, avísale de que su nombre queda " +
                        "registrado y para qué sirve. Esa responsabilidad es tuya como " +
                        "responsable del tratamiento, no de la app."
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Este aviso describe cómo funciona la app tal como está construida. " +
                    "Si vas a vender el producto y necesitas una política de " +
                    "privacidad formal para un trámite o para publicarla en una tienda " +
                    "de aplicaciones, conviene que la revise alguien con criterio " +
                    "legal: este texto explica lo que la app hace, pero no es un " +
                    "documento redactado por un abogado.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Parrafo(texto: String) {
    Text(
        "• $texto",
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    )
}
