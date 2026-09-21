package ec.cacaotrace.ui.comun

import ec.cacaotrace.nucleo.norma.formatear
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formato de fechas y números para la interfaz.
 *
 * Se centraliza aquí para que toda la app muestre "12/03/2026" y no una
 * mezcla de formatos según quién escribió la pantalla.
 */
object Formato {

    private val zona: ZoneId get() = ZoneId.systemDefault()
    private val ecuador = Locale.forLanguageTag("es-EC")

    private val soloFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy", ecuador)
    private val fechaCorta = DateTimeFormatter.ofPattern("dd/MM", ecuador)
    private val fechaYHora = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", ecuador)
    private val horaCorta = DateTimeFormatter.ofPattern("HH:mm", ecuador)

    fun fecha(instante: Instant): String = soloFecha.format(instante.atZone(zona))

    fun fechaCorta(instante: Instant): String = fechaCorta.format(instante.atZone(zona))

    fun fechaHora(instante: Instant): String = fechaYHora.format(instante.atZone(zona))

    fun hora(instante: Instant): String = horaCorta.format(instante.atZone(zona))

    fun fecha(fecha: LocalDate): String = soloFecha.format(fecha)

    /** "hace 3 h", "hace 2 días". */
    fun haceCuanto(instante: Instant): String {
        val d = Duration.between(instante, Instant.now())
        return when {
            d.toMinutes() < 60 -> "hace ${d.toMinutes()} min"
            d.toHours() < 24 -> "hace ${d.toHours()} h"
            else -> "hace ${d.toDays()} día(s)"
        }
    }

    /** Número con punto decimal, igual que en el pipeline de entrenamiento. */
    fun numero(valor: Double, decimales: Int = 1): String = formatear(valor, decimales)

    fun kg(valor: Double): String = "${numero(valor, 1)} kg"

    fun porcentaje(valor: Double): String = "${numero(valor, 1)} %"

    fun grados(valor: Double): String = "${numero(valor, 1)} °C"

    fun dolares(valor: Double): String = "$ ${numero(valor, 2)}"

    /** "dano_insectos" -> "Daño insectos". */
    fun nombreBonito(clase: String): String {
        val t = clase.replace('_', ' ').replace("dano", "daño")
        return t.replaceFirstChar { it.uppercase() }
    }
}
