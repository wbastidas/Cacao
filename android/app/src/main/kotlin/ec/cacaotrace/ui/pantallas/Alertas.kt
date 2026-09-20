package ec.cacaotrace.ui.pantallas

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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ec.cacaotrace.ContenedorApp
import ec.cacaotrace.datos.bd.entidades.AlertaEntidad
import ec.cacaotrace.nucleo.reglas.Correccion
import ec.cacaotrace.nucleo.reglas.Correcciones
import ec.cacaotrace.ui.ColoresEstado
import ec.cacaotrace.ui.Rutas
import ec.cacaotrace.ui.comun.BarraSuperior
import ec.cacaotrace.ui.comun.EstadoVacio
import ec.cacaotrace.ui.comun.Formato
import ec.cacaotrace.ui.comun.TarjetaSeccion
import kotlinx.coroutines.launch

/**
 * Alertas abiertas y biblioteca de correcciones (RF-ALE-03, RF-COR-01/02).
 *
 * Cada alerta dice qué pasó, por qué importa y qué hacer. La corrección no es
 * un texto suelto: se puede marcar como aplicada y anotar qué resultó, que es
 * lo que permite aprender de un lote al siguiente.
 */
@Composable
fun PantallaAlertas(
    contenedor: ContenedorApp,
    navegacion: NavHostController,
    loteId: String? = null,
) {
    val alertas by contenedor.alertas.observarAbiertas(loteId)
        .collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            BarraSuperior("Alertas", navegacion) {
                IconButton(onClick = { navegacion.navigate(Rutas.CORRECCIONES) }) {
                    Icon(Icons.Default.MenuBook, contentDescription = "Correcciones")
                }
            }
        },
    ) { relleno ->
        if (alertas.isEmpty()) {
            EstadoVacio(
                icono = Icons.Default.CheckCircle,
                titulo = "No hay alertas abiertas",
                explicacion = "Cuando una lectura se salga de los umbrales, aparecerá " +
                    "aquí con la corrección que toca.",
                modifier = Modifier.fillMaxSize().padding(relleno),
                accion = {
                    OutlinedButton(onClick = { navegacion.navigate(Rutas.CORRECCIONES) }) {
                        Text("Ver las correcciones")
                    }
                },
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(relleno),
            contentPadding = PaddingValues(16.dp),
        ) {
            items(alertas, key = { it.id }) { alerta ->
                TarjetaAlerta(contenedor, navegacion, alerta)
            }
        }
    }
}

@Composable
private fun TarjetaAlerta(
    contenedor: ContenedorApp,
    navegacion: NavHostController,
    alerta: AlertaEntidad,
) {
    val alcance = rememberCoroutineScope()
    val color = ColoresEstado.deSeveridad(alerta.severidad)
    val correccion = Correcciones.buscar(alerta.correccionCodigo.takeIf { it.isNotBlank() })
    var pidiendoResultado by remember { mutableStateOf(false) }
    var resultado by remember { mutableStateOf("") }

    if (pidiendoResultado) {
        AlertDialog(
            onDismissRequest = { pidiendoResultado = false },
            title = { Text("¿Qué hiciste?") },
            text = {
                Column {
                    Text(
                        "Anotar el resultado sirve para saber qué funcionó cuando vuelva " +
                            "a pasar.",
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = resultado,
                        onValueChange = { resultado = it },
                        label = { Text("Qué hiciste y cómo salió") },
                        placeholder = { Text("Añadí 12 kg de cáscara y subió a 45 °C") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        alcance.launch {
                            contenedor.alertas.atender(
                                alerta.id,
                                correccion?.codigo,
                                resultado.trim(),
                            )
                        }
                        pidiendoResultado = false
                    },
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { pidiendoResultado = false }) { Text("Cancelar") }
            },
        )
    }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    when (alerta.severidad) {
                        "BLOQUEANTE" -> Icons.Default.Block
                        "URGENTE" -> Icons.Default.WarningAmber
                        else -> Icons.Default.Info
                    },
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(alerta.quePaso, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${alerta.regla} · ${Formato.haceCuanto(alerta.fecha)}",
                        fontSize = 13.sp,
                        color = color,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            BloqueTexto("Por qué importa", alerta.porQueImporta)
            BloqueTexto("Qué hacer", alerta.queHacer)

            if (alerta.valorMedido != null && alerta.valorEsperado != null) {
                Text(
                    "Medido ${Formato.numero(alerta.valorMedido)} · " +
                        "esperado ${Formato.numero(alerta.valorEsperado)}",
                    fontSize = 15.sp,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (correccion != null) {
                    OutlinedButton(onClick = { navegacion.navigate(Rutas.CORRECCIONES) }) {
                        Icon(Icons.Default.MenuBook, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Ver ${correccion.codigo}")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Button(onClick = { pidiendoResultado = true }) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Ya lo resolví")
                }
            }
            TextButton(
                onClick = { alcance.launch { contenedor.alertas.descartar(alerta.id) } },
            ) { Text("No aplica") }
        }
    }
}

@Composable
private fun BloqueTexto(titulo: String, texto: String) {
    if (texto.isBlank()) return
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            titulo,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(texto, fontSize = 16.sp)
    }
}

/** Biblioteca de correcciones, disponible sin internet (RF-COR-01). */
@Composable
fun PantallaCorrecciones(navegacion: NavHostController) {
    var elegida by remember { mutableStateOf<Correccion?>(null) }

    elegida?.let { c ->
        AlertDialog(
            onDismissRequest = { elegida = null },
            title = { Text(c.problema) },
            text = {
                LazyColumn {
                    item {
                        TarjetaSeccion("Por qué pasa", icono = Icons.Default.Info) {
                            Text(c.porQuePasa, fontSize = 16.sp)
                        }
                        TarjetaSeccion("Qué hacer ahora", icono = Icons.Default.Check) {
                            Column {
                                c.pasos.forEachIndexed { i, paso ->
                                    Text("${i + 1}. $paso", fontSize = 16.sp)
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                        }
                        TarjetaSeccion("Para la próxima vez", icono = Icons.Default.Info) {
                            Text(c.paraLaProximaVez, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { elegida = null }) { Text("Cerrar") } },
        )
    }

    Scaffold(topBar = { BarraSuperior("Qué hacer si…", navegacion) }) { relleno ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(relleno),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                Text(
                    "Problemas frecuentes y qué hacer en cada caso. Funciona sin internet.",
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(12.dp))
            }
            items(Correcciones.todas) { c ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        headlineContent = { Text("${c.codigo} · ${c.problema}") },
                        supportingContent = { Text(c.pasos.first(), maxLines = 2) },
                        modifier = Modifier.then(
                            androidx.compose.foundation.clickable { elegida = c },
                        ),
                    )
                }
            }
        }
    }
}
