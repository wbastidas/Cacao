package ec.cacaotrace.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ec.cacaotrace.ContenedorApp
import ec.cacaotrace.datos.repositorios.Existencia
import ec.cacaotrace.ui.ColoresEstado
import ec.cacaotrace.ui.comun.Aviso
import ec.cacaotrace.ui.comun.BarraSuperior
import ec.cacaotrace.ui.comun.BotonGrande
import ec.cacaotrace.ui.comun.CampoNumero
import ec.cacaotrace.ui.comun.FilaDato
import ec.cacaotrace.ui.comun.Formato
import ec.cacaotrace.ui.comun.TarjetaSeccion
import ec.cacaotrace.ui.comun.leerNumero
import kotlinx.coroutines.launch

/**
 * Inventario de insumos y producto (RF-INV-01, RF-INV-02).
 *
 * Las existencias no se editan: se anotan entradas y salidas, y el saldo sale
 * de sumarlas. Por eso no hay ningún campo "cantidad actual" que se pueda
 * escribir a mano. Si un número no cuadra, se corrige con un movimiento de
 * ajuste, y queda dicho por qué.
 */
@Composable
fun PantallaInventario(contenedor: ContenedorApp, navegacion: NavHostController) {
    val alcance = rememberCoroutineScope()
    val movimientos by contenedor.apoyo.observarMovimientos()
        .collectAsState(initial = emptyList())

    var existencias by remember { mutableStateOf<List<Existencia>>(emptyList()) }
    var item by remember { mutableStateOf(ITEMS.first()) }
    var tipo by remember { mutableStateOf("entrada") }
    var cantidad by remember { mutableStateOf("") }
    var notas by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }

    // Se recalcula cada vez que cambia la lista de movimientos: así el saldo
    // que se ve arriba nunca va por detrás de lo que se acaba de anotar.
    LaunchedEffect(movimientos) {
        existencias = contenedor.apoyo.existencias()
    }

    val bajoMinimo = existencias.filter { it.bajoMinimo }
    val cantidadNumero = leerNumero(cantidad)

    Scaffold(topBar = { BarraSuperior("Inventario", navegacion) }) { relleno ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            mensaje?.let {
                Aviso(texto = it, color = ColoresEstado.bien, icono = Icons.Default.CheckCircle)
            }

            // ----------------------------------------- avisos de reposición
            if (bajoMinimo.isNotEmpty()) {
                Aviso(
                    titulo = "Hay que reponer",
                    texto = bajoMinimo.joinToString(", ") {
                        "${Formato.nombreBonito(it.item)} " +
                            "(${Formato.numero(it.cantidad, 1)} ${it.unidad})"
                    },
                    color = ColoresEstado.atencion,
                    icono = Icons.Default.WarningAmber,
                )
            }

            // --------------------------------------------------- existencias
            TarjetaSeccion("Lo que hay", icono = Icons.Default.Inventory) {
                if (existencias.isEmpty()) {
                    Text(
                        "Todavía no hay movimientos. Anota una entrada cuando compres " +
                            "azúcar, manteca o empaques.",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    existencias.forEach { e ->
                        FilaDato(
                            etiqueta = Formato.nombreBonito(e.item) +
                                (e.minimo?.let { " (mín. ${Formato.numero(it, 0)})" }.orEmpty()),
                            valor = "${Formato.numero(e.cantidad, 1)} ${e.unidad}",
                            color = if (e.bajoMinimo) {
                                ColoresEstado.atencion
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
            }

            // ------------------------------------------- nuevo movimiento
            TarjetaSeccion("Anotar un movimiento", icono = Icons.Default.Save) {
                Text("¿Qué artículo?", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ITEMS.take(4).forEach { opcion ->
                        FilterChip(
                            selected = item == opcion,
                            onClick = { item = opcion },
                            label = { Text(Formato.nombreBonito(opcion), fontSize = 13.sp) },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ITEMS.drop(4).forEach { opcion ->
                        FilterChip(
                            selected = item == opcion,
                            onClick = { item = opcion },
                            label = { Text(Formato.nombreBonito(opcion), fontSize = 13.sp) },
                        )
                    }
                }

                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    FilterChip(
                        selected = tipo == "entrada",
                        onClick = { tipo = "entrada" },
                        leadingIcon = {
                            Icon(
                                Icons.Default.ArrowDownward,
                                contentDescription = null,
                                Modifier.size(18.dp),
                            )
                        },
                        label = { Text("Entra") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    FilterChip(
                        selected = tipo == "salida",
                        onClick = { tipo = "salida" },
                        leadingIcon = {
                            Icon(
                                Icons.Default.ArrowUpward,
                                contentDescription = null,
                                Modifier.size(18.dp),
                            )
                        },
                        label = { Text("Sale") },
                    )
                }

                CampoNumero(
                    etiqueta = "Cantidad",
                    valor = cantidad,
                    alCambiar = { cantidad = it },
                    unidad = unidadDe(item),
                )
                OutlinedTextField(
                    value = notas,
                    onValueChange = { notas = it },
                    label = { Text("¿Por qué?") },
                    placeholder = { Text("Ej.: compra a proveedor, ajuste por conteo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )

                Spacer(Modifier.height(8.dp))
                BotonGrande(
                    texto = "Guardar el movimiento",
                    icono = Icons.Default.Save,
                    habilitado = cantidadNumero != null && cantidadNumero > 0,
                ) {
                    alcance.launch {
                        contenedor.apoyo.registrarMovimiento(
                            item = item,
                            tipo = tipo,
                            cantidad = cantidadNumero ?: 0.0,
                            unidad = unidadDe(item),
                            notas = notas.trim(),
                        )
                        cantidad = ""
                        notas = ""
                        mensaje = "Movimiento anotado."
                    }
                }
            }

            // ----------------------------------------------------- historial
            TarjetaSeccion("Últimos movimientos", icono = Icons.Default.History) {
                if (movimientos.isEmpty()) {
                    Text("Sin movimientos todavía.", fontSize = 15.sp)
                } else {
                    movimientos.take(30).forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (m.tipo == "salida") {
                                    Icons.Default.ArrowUpward
                                } else {
                                    Icons.Default.ArrowDownward
                                },
                                contentDescription = if (m.tipo == "salida") "Salida" else "Entrada",
                                tint = if (m.tipo == "salida") {
                                    ColoresEstado.atencion
                                } else {
                                    ColoresEstado.bien
                                },
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    Formato.nombreBonito(m.item),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    listOfNotNull(
                                        Formato.fechaCorta(m.fecha),
                                        m.referencia.takeIf { it.isNotBlank() },
                                        m.notas.takeIf { it.isNotBlank() },
                                    ).joinToString(" · "),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${if (m.tipo == "salida") "−" else "+"}" +
                                    "${Formato.numero(m.cantidad, 1)} ${m.unidad}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Los artículos que la app maneja. Coinciden con los que mueve el proceso. */
private val ITEMS = listOf(
    "grano_seco", "nibs", "azucar", "manteca", "lecitina", "empaques", "chocolate",
)

/** El chocolate y los empaques se cuentan por unidades; el resto se pesa. */
private fun unidadDe(item: String): String = when (item) {
    "chocolate" -> "barras"
    "empaques" -> "unidades"
    else -> "kg"
}
