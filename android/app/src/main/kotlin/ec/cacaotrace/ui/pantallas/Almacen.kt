package ec.cacaotrace.ui.pantallas

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import ec.cacaotrace.datos.bd.entidades.SacoEntidad
import ec.cacaotrace.nucleo.modelo.EstadoLote
import ec.cacaotrace.nucleo.reglas.Alerta
import ec.cacaotrace.ui.ColoresEstado
import ec.cacaotrace.ui.Rutas
import ec.cacaotrace.ui.comun.Aviso
import ec.cacaotrace.ui.comun.BarraSuperior
import ec.cacaotrace.ui.comun.BotonGrande
import ec.cacaotrace.ui.comun.CampoNumero
import ec.cacaotrace.ui.comun.CodigoQr
import ec.cacaotrace.ui.comun.FilaDato
import ec.cacaotrace.ui.comun.Formato
import ec.cacaotrace.ui.comun.TarjetaSeccion
import ec.cacaotrace.ui.comun.leerNumero
import kotlinx.coroutines.launch

/**
 * Almacén: sacos con su QR e inspección periódica (RF-ALM-01 a RF-ALM-03).
 *
 * Se entra de dos maneras: desde un lote concreto, para ensacarlo, o desde el
 * menú, para revisar todo el almacén. Por eso `loteId` es opcional y la
 * pantalla cambia de cara según venga o no.
 */
@Composable
fun PantallaAlmacen(
    contenedor: ContenedorApp,
    navegacion: NavHostController,
    loteId: String?,
) {
    val alcance = rememberCoroutineScope()
    val sacos by contenedor.apoyo.observarSacos(loteId).collectAsState(initial = emptyList())

    var codigoLote by remember { mutableStateOf("") }
    var creandoSaco by remember { mutableStateOf(false) }
    var qrVisible by remember { mutableStateOf<SacoEntidad?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(loteId) {
        codigoLote = loteId?.let { contenedor.lotes.porId(it)?.codigo }.orEmpty()
    }

    Scaffold(
        topBar = {
            BarraSuperior(
                if (codigoLote.isBlank()) "Almacén" else "Almacén · $codigoLote",
                navegacion,
            )
        },
    ) { relleno ->
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

            // ------------------------------------------------------ sacos
            TarjetaSeccion("Sacos almacenados", icono = Icons.Default.Inventory) {
                if (sacos.isEmpty()) {
                    Text(
                        "Todavía no hay sacos. Cuando el grano esté seco, pésalo y " +
                            "ensácalo: cada saco lleva su propio QR para poder seguirle " +
                            "la pista dentro de la bodega.",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FilaDato("Sacos", sacos.size.toString())
                    FilaDato("Peso total", Formato.kg(sacos.sumOf { it.pesoKg }))
                    Spacer(Modifier.height(8.dp))
                    sacos.forEach { saco ->
                        FilaSaco(saco) { qrVisible = saco }
                    }
                }

                if (loteId != null) {
                    Spacer(Modifier.height(12.dp))
                    BotonGrande(
                        texto = "Ensacar",
                        subtitulo = "Registra un saco nuevo y genera su QR",
                        icono = Icons.Default.Add,
                    ) { creandoSaco = true }
                }
            }

            // ------------------------------------------- inspección (RF-ALM-02)
            Spacer(Modifier.height(8.dp))
            FormularioInspeccion(contenedor, loteId) { alertas ->
                mensaje = if (alertas.isEmpty()) {
                    "Inspección registrada. El almacén está en condiciones."
                } else {
                    "Inspección registrada. Se abrieron ${alertas.size} alerta(s)."
                }
                if (alertas.isNotEmpty()) navegacion.navigate(Rutas.alertas(loteId))
            }

            // Si no se entró desde un lote, se ofrece llegar a uno que esté listo.
            if (loteId == null) {
                Spacer(Modifier.height(8.dp))
                SugerenciaLotesParaEnsacar(contenedor, navegacion)
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (creandoSaco && loteId != null) {
        DialogoNuevoSaco(
            alGuardar = { pesoKg, ubicacion ->
                creandoSaco = false
                alcance.launch {
                    val codigo = contenedor.apoyo.crearSaco(loteId, pesoKg, ubicacion)
                    // Ensacar es lo que cierra el lote de grano: a partir de aquí
                    // el lote está listo para entrar en una tanda de producción.
                    runCatching {
                        contenedor.lotes.cambiarEstado(loteId, EstadoLote.ALMACENADO)
                    }
                    mensaje = "Saco $codigo registrado. Imprime su QR y pégalo al saco."
                }
            },
            alCancelar = { creandoSaco = false },
        )
    }

    qrVisible?.let { saco ->
        AlertDialog(
            onDismissRequest = { qrVisible = null },
            title = { Text(saco.codigoQr) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CodigoQr(saco.codigoQr, Modifier.size(240.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Imprímelo y pégalo al saco. Al escanearlo, la app abre este " +
                            "lote con toda su historia.",
                        fontSize = 15.sp,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { qrVisible = null }) { Text("Listo") } },
        )
    }
}

@Composable
private fun FilaSaco(saco: SacoEntidad, alTocarQr: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = alTocarQr),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.QrCode2, contentDescription = "Ver el QR del saco")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(saco.codigoQr, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    listOfNotNull(
                        Formato.kg(saco.pesoKg),
                        saco.ubicacion.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AssistChip(onClick = alTocarQr, label = { Text(saco.estado) })
        }
    }
}

@Composable
private fun DialogoNuevoSaco(
    alGuardar: (Double, String) -> Unit,
    alCancelar: () -> Unit,
) {
    var peso by remember { mutableStateOf("") }
    var ubicacion by remember { mutableStateOf("") }
    val pesoKg = leerNumero(peso)

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Saco nuevo") },
        text = {
            Column {
                CampoNumero(
                    etiqueta = "Peso del saco",
                    valor = peso,
                    alCambiar = { peso = it },
                    unidad = "kg",
                    ayuda = "Pesa el saco lleno y resta el saco vacío.",
                )
                OutlinedTextField(
                    value = ubicacion,
                    onValueChange = { ubicacion = it },
                    label = { Text("¿Dónde queda?") },
                    placeholder = { Text("Ej.: estante 2, fila de arriba") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { pesoKg?.let { alGuardar(it, ubicacion.trim()) } },
                enabled = pesoKg != null && pesoKg > 0,
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}

/**
 * Inspección del almacén (RF-ALM-02, RN-11).
 *
 * Son cuatro preguntas: humedad, temperatura, y si se ve moho o bichos. El
 * olor va aparte porque es la señal más temprana de que algo va mal y no se
 * mide con ningún aparato: se huele.
 */
@Composable
private fun FormularioInspeccion(
    contenedor: ContenedorApp,
    loteId: String?,
    alGuardar: (List<Alerta>) -> Unit,
) {
    val alcance = rememberCoroutineScope()
    var humedad by remember { mutableStateOf("") }
    var temperatura by remember { mutableStateOf("") }
    var plagas by remember { mutableStateOf(false) }
    var moho by remember { mutableStateOf(false) }
    var olor by remember { mutableStateOf("") }
    var notas by remember { mutableStateOf("") }

    TarjetaSeccion("Inspección del almacén", icono = Icons.Default.Warehouse) {
        Text(
            "Conviene hacerla cada semana. El grano seco vuelve a tomar humedad del " +
                "aire, y si la bodega pasa del 65 % de humedad relativa el moho " +
                "aparece en pocos días.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        CampoNumero(
            etiqueta = "Humedad relativa de la bodega",
            valor = humedad,
            alCambiar = { humedad = it },
            unidad = "%",
            ayuda = "Con un higrómetro barato basta.",
        )
        CampoNumero(
            etiqueta = "Temperatura de la bodega",
            valor = temperatura,
            alCambiar = { temperatura = it },
            unidad = "°C",
        )

        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = moho, onCheckedChange = { moho = it })
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.Science, contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Se ve moho en algún saco", fontSize = 16.sp)
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = plagas, onCheckedChange = { plagas = it })
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.BugReport, contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Hay señales de plagas (polilla, roedores)", fontSize = 16.sp)
        }

        OutlinedTextField(
            value = olor,
            onValueChange = { olor = it },
            label = { Text("¿A qué huele la bodega?") },
            placeholder = { Text("Normal, a humedad, a vinagre, a moho…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        OutlinedTextField(
            value = notas,
            onValueChange = { notas = it },
            label = { Text("Notas") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )

        Spacer(Modifier.height(8.dp))
        BotonGrande(texto = "Guardar la inspección", icono = Icons.Default.CheckCircle) {
            alcance.launch {
                val alertas = contenedor.apoyo.inspeccionarAlmacen(
                    loteId = loteId,
                    humedadRelativa = leerNumero(humedad),
                    temperatura = leerNumero(temperatura),
                    plagas = plagas,
                    moho = moho,
                    olor = olor.trim(),
                    notas = notas.trim(),
                )
                humedad = ""
                temperatura = ""
                plagas = false
                moho = false
                olor = ""
                notas = ""
                alGuardar(alertas)
            }
        }
    }
}

/** Atajo a los lotes que ya se secaron y esperan que alguien los ensaque. */
@Composable
private fun SugerenciaLotesParaEnsacar(
    contenedor: ContenedorApp,
    navegacion: NavHostController,
) {
    val lotes by contenedor.lotes.observarLotes(incluirCerrados = false)
        .collectAsState(initial = emptyList())
    val listos = lotes.filter { it.estado == EstadoLote.SECADO }

    if (listos.isEmpty()) return

    TarjetaSeccion("Lotes listos para ensacar", icono = Icons.Default.Inventory) {
        listos.forEach { lote ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { navegacion.navigate(Rutas.almacen(lote.id)) },
            ) {
                Row(
                    Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(lote.codigo, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(lote.estado.etiqueta, fontSize = 14.sp)
                }
            }
        }
    }
}
