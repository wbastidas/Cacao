package ec.cacaotrace.ui.pantallas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ec.cacaotrace.ContenedorApp
import ec.cacaotrace.datos.bd.entidades.LoteEntidad
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
 * Resultados de laboratorio (RF-LAB-01, RF-LAB-02, RN-15).
 *
 * El análisis que de verdad importa aquí es el cadmio: el CCN-51 de la costa
 * ecuatoriana lo acumula del suelo, y el límite europeo para un chocolate de
 * más del 50 % de cacao es 0,80 mg/kg. Pasarse no es una multa: es que el lote
 * no se puede vender, y por eso la app lo bloquea sola.
 */
@Composable
fun PantallaLaboratorio(contenedor: ContenedorApp, navegacion: NavHostController) {
    val alcance = rememberCoroutineScope()
    val resultados by contenedor.apoyo.observarLaboratorio().collectAsState(initial = emptyList())
    val lotes by contenedor.lotes.observarLotes().collectAsState(initial = emptyList())

    var analisis by remember { mutableStateOf("cadmio") }
    var valor by remember { mutableStateOf("") }
    var laboratorio by remember { mutableStateOf("") }
    var loteElegido by remember { mutableStateOf<LoteEntidad?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var desbloqueando by remember { mutableStateOf<LoteEntidad?>(null) }

    val bloqueados = lotes.filter { it.ventaBloqueada }
    val numero = leerNumero(valor)

    Scaffold(topBar = { BarraSuperior("Laboratorio", navegacion) }) { relleno ->
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

            // --------------------------------------------- lotes bloqueados
            if (bloqueados.isNotEmpty()) {
                TarjetaSeccion("Lotes bloqueados para venta", icono = Icons.Default.Block) {
                    bloqueados.forEach { lote ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(
                                lote.codigo,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ColoresEstado.problema,
                            )
                            Text(lote.motivoBloqueo, fontSize = 15.sp)
                            TextButton(onClick = { desbloqueando = lote }) {
                                Text("Levantar el bloqueo")
                            }
                        }
                    }
                }
            }

            // ----------------------------------------------- nuevo resultado
            TarjetaSeccion("Anotar un resultado", icono = Icons.Default.Science) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ANALISIS.forEach { opcion ->
                        FilterChip(
                            selected = analisis == opcion,
                            onClick = { analisis = opcion },
                            label = { Text(Formato.nombreBonito(opcion), fontSize = 13.sp) },
                        )
                    }
                }

                SelectorLote(
                    lotes = lotes,
                    elegido = loteElegido,
                    alElegir = { loteElegido = it },
                )

                CampoNumero(
                    etiqueta = "Resultado",
                    valor = valor,
                    alCambiar = { valor = it },
                    unidad = unidadDeAnalisis(analisis),
                    ayuda = ayudaDeAnalisis(analisis),
                )
                OutlinedTextField(
                    value = laboratorio,
                    onValueChange = { laboratorio = it },
                    label = { Text("Laboratorio que lo hizo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )

                // Aviso antes de guardar: es mejor verlo ahora que descubrirlo
                // cuando la app bloquee el lote.
                if (analisis == "cadmio" && numero != null && numero > LIMITE_CADMIO) {
                    Aviso(
                        titulo = "Ese valor bloquea el lote",
                        texto = "Supera el límite de ${Formato.numero(LIMITE_CADMIO, 2)} mg/kg. " +
                            "Al guardarlo, el lote quedará bloqueado para venta.",
                        color = ColoresEstado.problema,
                        icono = Icons.Default.Block,
                    )
                }

                Spacer(Modifier.height(8.dp))
                BotonGrande(
                    texto = "Guardar el resultado",
                    icono = Icons.Default.Save,
                    habilitado = numero != null,
                ) {
                    alcance.launch {
                        val alertas = contenedor.apoyo.guardarLaboratorio(
                            analisis = analisis,
                            valor = numero ?: 0.0,
                            loteId = loteElegido?.id,
                            unidad = unidadDeAnalisis(analisis),
                            limite = if (analisis == "cadmio") LIMITE_CADMIO else null,
                            laboratorio = laboratorio.trim(),
                        )
                        valor = ""
                        mensaje = if (alertas.any { it.bloquea }) {
                            "Resultado guardado. El lote quedó bloqueado para venta."
                        } else {
                            "Resultado guardado."
                        }
                    }
                }
            }

            // ------------------------------------------------------ historial
            TarjetaSeccion("Resultados anteriores", icono = Icons.Default.Science) {
                if (resultados.isEmpty()) {
                    Text(
                        "Todavía no hay análisis. Conviene mandar a analizar cadmio al " +
                            "menos una vez por cosecha y finca.",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    resultados.forEach { r ->
                        val codigo = lotes.firstOrNull { it.id == r.loteId }?.codigo ?: "—"
                        val excedido = r.limite != null && r.valor > r.limite
                        FilaDato(
                            etiqueta = "${Formato.nombreBonito(r.analisis)} · $codigo · " +
                                Formato.fecha(r.fecha),
                            valor = "${Formato.numero(r.valor, 2)} ${r.unidad}",
                            color = if (excedido) ColoresEstado.problema else ColoresEstado.bien,
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    desbloqueando?.let { lote ->
        DialogoDesbloquear(
            codigo = lote.codigo,
            alConfirmar = { motivo ->
                desbloqueando = null
                alcance.launch {
                    contenedor.apoyo.desbloquearVenta(lote.id, motivo)
                    mensaje = "Bloqueo levantado en ${lote.codigo}. Queda registrado el motivo."
                }
            },
            alCancelar = { desbloqueando = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorLote(
    lotes: List<LoteEntidad>,
    elegido: LoteEntidad?,
    alElegir: (LoteEntidad?) -> Unit,
) {
    var abierto by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = abierto,
        onExpandedChange = { abierto = it },
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        OutlinedTextField(
            value = elegido?.codigo ?: "Sin lote asociado",
            onValueChange = {},
            readOnly = true,
            label = { Text("¿De qué lote es la muestra?") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(
                text = { Text("Sin lote asociado") },
                onClick = {
                    alElegir(null)
                    abierto = false
                },
            )
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

/**
 * Levantar un bloqueo exige escribir por qué.
 *
 * No es burocracia: si alguien decide vender un lote que dio alto en cadmio,
 * esa decisión tiene que tener un nombre y un motivo escritos, porque la
 * responsabilidad es real.
 */
@Composable
private fun DialogoDesbloquear(
    codigo: String,
    alConfirmar: (String) -> Unit,
    alCancelar: () -> Unit,
) {
    var motivo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Levantar el bloqueo de $codigo") },
        text = {
            Column {
                Text(
                    "Este lote se bloqueó por un resultado de laboratorio. Si lo " +
                        "desbloqueas, explica por qué: un contraanálisis, un error en la " +
                        "muestra, un destino distinto a la venta…",
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { motivo = it },
                    label = { Text("Motivo") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { alConfirmar(motivo.trim()) },
                enabled = motivo.trim().length >= 10,
            ) {
                Text("Levantar el bloqueo", color = ColoresEstado.problema)
            }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}

/** Límite de cadmio para chocolate con más del 50 % de cacao (Reg. UE 2021/1323). */
private const val LIMITE_CADMIO = 0.80

private val ANALISIS = listOf("cadmio", "humedad", "microbiologia", "plomo")

private fun unidadDeAnalisis(analisis: String): String = when (analisis) {
    "humedad" -> "%"
    "microbiologia" -> "UFC/g"
    else -> "mg/kg"
}

private fun ayudaDeAnalisis(analisis: String): String = when (analisis) {
    "cadmio" -> "El límite europeo para un chocolate de más del 50 % de cacao es " +
        "0,80 mg/kg."
    "humedad" -> "El grano seco debe quedar entre el 6 y el 7,5 %."
    else -> ""
}
