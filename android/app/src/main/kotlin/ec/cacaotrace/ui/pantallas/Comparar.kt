package ec.cacaotrace.ui.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ec.cacaotrace.ContenedorApp
import ec.cacaotrace.datos.bd.entidades.LoteEntidad
import ec.cacaotrace.datos.repositorios.ResumenLote
import ec.cacaotrace.ui.ColoresEstado
import ec.cacaotrace.ui.comun.BarraSuperior
import ec.cacaotrace.ui.comun.EstadoVacio
import ec.cacaotrace.ui.comun.Formato
import ec.cacaotrace.ui.comun.TarjetaSeccion

/**
 * Dos lotes uno al lado del otro (RF-TAB-03).
 *
 * Es la pantalla donde se aprende del proceso. Cuando un lote sale Grado 1 y
 * otro Grado 2, la respuesta casi siempre está en dos o tres números: la
 * temperatura máxima que alcanzó la masa, cuántos volteos se hicieron, o
 * cuántos días duró el secado. Verlos en columnas contiguas hace evidente lo
 * que en dos pantallas separadas no se ve.
 *
 * Las filas donde los dos lotes difieren de forma apreciable se marcan, para
 * no tener que leer veinte números buscando cuál cambió.
 */
@Composable
fun PantallaComparar(contenedor: ContenedorApp, navegacion: NavHostController) {
    val lotes by contenedor.lotes.observarLotes().collectAsState(initial = emptyList())

    var izquierdo by remember { mutableStateOf<LoteEntidad?>(null) }
    var derecho by remember { mutableStateOf<LoteEntidad?>(null) }
    var resumenA by remember { mutableStateOf<ResumenLote?>(null) }
    var resumenB by remember { mutableStateOf<ResumenLote?>(null) }

    // Se preseleccionan los dos más recientes: es lo que se quiere comparar
    // nueve de cada diez veces, y ahorra dos toques.
    LaunchedEffect(lotes) {
        if (izquierdo == null && lotes.size >= 2) {
            izquierdo = lotes[lotes.lastIndex]
            derecho = lotes[lotes.lastIndex - 1]
        }
    }

    LaunchedEffect(izquierdo?.id) {
        resumenA = izquierdo?.let { contenedor.apoyo.resumenParaComparar(it.id) }
    }
    LaunchedEffect(derecho?.id) {
        resumenB = derecho?.let { contenedor.apoyo.resumenParaComparar(it.id) }
    }

    Scaffold(topBar = { BarraSuperior("Comparar lotes", navegacion) }) { relleno ->
        if (lotes.size < 2) {
            EstadoVacio(
                icono = Icons.Default.CompareArrows,
                titulo = "Hacen falta dos lotes",
                explicacion = "Cuando tengas dos lotes registrados podrás ponerlos " +
                    "uno al lado del otro y ver qué hiciste distinto.",
                modifier = Modifier.fillMaxSize().padding(relleno),
            )
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectorDeLote(
                    etiqueta = "Lote A",
                    lotes = lotes,
                    elegido = izquierdo,
                    alElegir = { izquierdo = it },
                    modifier = Modifier.weight(1f),
                )
                SelectorDeLote(
                    etiqueta = "Lote B",
                    lotes = lotes,
                    elegido = derecho,
                    alElegir = { derecho = it },
                    modifier = Modifier.weight(1f),
                )
            }

            val a = resumenA
            val b = resumenB
            if (a == null || b == null) {
                Spacer(Modifier.height(16.dp))
                Text("Elige dos lotes para compararlos.", fontSize = 16.sp)
                return@Column
            }

            if (a.loteId == b.loteId) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Has elegido el mismo lote en los dos lados. Cambia uno para ver " +
                        "las diferencias.",
                    fontSize = 16.sp,
                    color = ColoresEstado.atencion,
                )
                return@Column
            }

            Spacer(Modifier.height(12.dp))
            TarjetaSeccion("Resultado", icono = Icons.Default.CompareArrows) {
                Encabezado(a.codigo, b.codigo)
                FilaComparada("Grado", a.resultadoCorte, b.resultadoCorte)
                FilaComparada(
                    "Cumple la norma",
                    a.conforme?.let { if (it) "Sí" else "No" },
                    b.conforme?.let { if (it) "Sí" else "No" },
                )
                FilaNumerica("Fermentado", a.fermentadoPct, b.fermentadoPct, "%", 1)
                FilaNumerica("Alertas abiertas", a.alertas.toDouble(), b.alertas.toDouble(), "", 0)
            }

            TarjetaSeccion("Fermentación") {
                Encabezado(a.codigo, b.codigo)
                FilaNumerica("Horas", a.horasFermentacion?.toDouble(),
                    b.horasFermentacion?.toDouble(), " h", 0)
                FilaNumerica("Volteos", a.volteos?.toDouble(), b.volteos?.toDouble(), "", 0)
                FilaNumerica("Temperatura máxima", a.tempMaxima, b.tempMaxima, " °C", 1)
                FilaNumerica("Temperatura mínima", a.tempMinima, b.tempMinima, " °C", 1)
            }

            TarjetaSeccion("Secado") {
                Encabezado(a.codigo, b.codigo)
                FilaComparada("Método", a.metodoSecado, b.metodoSecado)
                FilaNumerica("Días", a.diasSecado?.toDouble(), b.diasSecado?.toDouble(), "", 0)
                FilaNumerica("Humedad final", a.humedadFinal, b.humedadFinal, " %", 1)
            }

            TarjetaSeccion("Rendimiento") {
                Encabezado(a.codigo, b.codigo)
                FilaNumerica("Mazorcas", a.mazorcas?.toDouble(), b.mazorcas?.toDouble(), "", 0)
                FilaNumerica("Baba", a.kgBaba, b.kgBaba, " kg", 1)
                FilaNumerica("Grano seco", a.kgSeco, b.kgSeco, " kg", 1)
                FilaNumerica(
                    "De baba a seco",
                    a.rendimientoSecoPct, b.rendimientoSecoPct, " %", 1,
                )
            }

            TarjetaSeccion("Origen") {
                Encabezado(a.codigo, b.codigo)
                FilaComparada("Finca", a.finca, b.finca)
                FilaComparada("Estado", a.estado, b.estado)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Las filas resaltadas son las que más difieren. Suelen ser la pista " +
                    "de por qué un lote salió mejor que el otro.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorDeLote(
    etiqueta: String,
    lotes: List<LoteEntidad>,
    elegido: LoteEntidad?,
    alElegir: (LoteEntidad) -> Unit,
    modifier: Modifier = Modifier,
) {
    var abierto by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = abierto,
        onExpandedChange = { abierto = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = elegido?.codigo.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(etiqueta) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            lotes.forEach { lote ->
                DropdownMenuItem(
                    text = { Text("${lote.codigo} · ${lote.estado.etiqueta}") },
                    onClick = {
                        alElegir(lote)
                        abierto = false
                    },
                )
            }
        }
    }
}

@Composable
private fun Encabezado(codigoA: String, codigoB: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Spacer(Modifier.width(120.dp))
        Text(
            codigoA,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            codigoB,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Fila de dos textos. Se resalta cuando difieren y los dos están puestos. */
@Composable
private fun FilaComparada(etiqueta: String, a: String?, b: String?) {
    val difieren = !a.isNullOrBlank() && !b.isNullOrBlank() && a != b
    FilaBase(etiqueta, a.orEmpty(), b.orEmpty(), difieren)
}

/**
 * Fila de dos números.
 *
 * Se resalta cuando la diferencia pasa del 15 % del mayor de los dos. El
 * porcentaje es deliberado: comparado con un umbral absoluto, funciona igual
 * para horas, grados y kilos sin tener que afinar uno por magnitud.
 */
@Composable
private fun FilaNumerica(
    etiqueta: String,
    a: Double?,
    b: Double?,
    sufijo: String,
    decimales: Int,
) {
    val difieren = if (a != null && b != null) {
        val mayor = maxOf(kotlin.math.abs(a), kotlin.math.abs(b))
        mayor > 0 && kotlin.math.abs(a - b) / mayor > 0.15
    } else {
        false
    }
    FilaBase(
        etiqueta,
        a?.let { "${Formato.numero(it, decimales)}$sufijo" }.orEmpty(),
        b?.let { "${Formato.numero(it, decimales)}$sufijo" }.orEmpty(),
        difieren,
    )
}

@Composable
private fun FilaBase(etiqueta: String, a: String, b: String, resaltar: Boolean) {
    val fondo = if (resaltar) {
        ColoresEstado.atencion.copy(alpha = 0.14f)
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(fondo, RoundedCornerShape(6.dp))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            etiqueta + if (resaltar) " ◂▸" else "",
            fontSize = 14.sp,
            modifier = Modifier.width(120.dp),
        )
        Text(
            a.ifBlank { "—" },
            fontSize = 15.sp,
            fontWeight = if (resaltar) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Text(
            b.ifBlank { "—" },
            fontSize = 15.sp,
            fontWeight = if (resaltar) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
    }
}
