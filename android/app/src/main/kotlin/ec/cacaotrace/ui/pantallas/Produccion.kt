package ec.cacaotrace.ui.pantallas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Blender
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateMapOf
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
import ec.cacaotrace.datos.bd.entidades.LoteProduccionEntidad
import ec.cacaotrace.datos.repositorios.ProduccionCompleta
import ec.cacaotrace.datos.repositorios.ResumenCostos
import ec.cacaotrace.nucleo.modelo.EstadoLote
import ec.cacaotrace.nucleo.modelo.MetodoAtemperado
import ec.cacaotrace.ui.ColoresEstado
import ec.cacaotrace.ui.Rutas
import ec.cacaotrace.ui.comun.Aviso
import ec.cacaotrace.ui.comun.BarraSuperior
import ec.cacaotrace.ui.comun.BotonFotoIa
import ec.cacaotrace.ui.comun.BotonGrande
import ec.cacaotrace.ui.comun.CampoNumero
import ec.cacaotrace.ui.comun.CodigoQr
import ec.cacaotrace.ui.comun.EstadoVacio
import ec.cacaotrace.ui.comun.FilaDato
import ec.cacaotrace.ui.comun.Formato
import ec.cacaotrace.ui.comun.TarjetaSeccion
import ec.cacaotrace.ui.comun.leerNumero
import java.time.Instant
import kotlinx.coroutines.launch

/**
 * Lista de tandas de producción (RF-LOT-05).
 *
 * Una tanda es la porción de grano seco que entra junta al tostador y sale
 * como barras. Puede venir de varios lotes: por eso al crearla se elige cuánto
 * se toma de cada uno, y esa mezcla es la que después permite rastrear una
 * barra hasta las fincas de origen.
 */
@Composable
fun PantallaProduccion(contenedor: ContenedorApp, navegacion: NavHostController) {
    val alcance = rememberCoroutineScope()
    val tandas by contenedor.produccion.observar().collectAsState(initial = emptyList())
    var creando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { BarraSuperior("Producción") },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creando = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nueva tanda") },
            )
        },
    ) { relleno ->
        if (tandas.isEmpty() && error == null) {
            EstadoVacio(
                icono = Icons.Default.Factory,
                titulo = "Todavía no hay tandas",
                explicacion = "Una tanda empieza cuando tomas grano seco del almacén y " +
                    "lo pones a tostar. Desde ahí la app te acompaña hasta la barra " +
                    "empacada.",
                modifier = Modifier.fillMaxSize().padding(relleno),
                accion = { Button(onClick = { creando = true }) { Text("Crear la primera") } },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(relleno),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            ) {
                error?.let { texto ->
                    item { Aviso(texto = texto, color = ColoresEstado.problema) }
                }
                items(tandas, key = { it.id }) { tanda ->
                    TarjetaTanda(tanda) { navegacion.navigate(Rutas.detalleTanda(tanda.id)) }
                }
            }
        }
    }

    if (creando) {
        DialogoNuevaTanda(
            contenedor = contenedor,
            alCancelar = { creando = false },
            alCrear = { kgPorLote, porcentaje ->
                creando = false
                error = null
                alcance.launch {
                    runCatching { contenedor.produccion.crear(kgPorLote, porcentaje) }
                        .onSuccess { navegacion.navigate(Rutas.detalleTanda(it.id)) }
                        .onFailure { fallo ->
                            // Un lote bloqueado por laboratorio trae su propio motivo
                            // explicado; el resto de fallos son de datos mal puestos.
                            error = fallo.message
                                ?: "No se pudo crear la tanda. Revisa los kilos indicados."
                        }
                }
            },
        )
    }
}

@Composable
private fun TarjetaTanda(tanda: LoteProduccionEntidad, alTocar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = alTocar),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tanda.codigo,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${Formato.numero(tanda.porcentajeCacao, 0)} %",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Inventory2,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    listOfNotNull(
                        Formato.fecha(tanda.fecha),
                        tanda.kgChocolate?.let { Formato.kg(it) },
                        if (tanda.estado == "terminado") "Terminada" else "En proceso",
                    ).joinToString(" · "),
                    fontSize = 15.sp,
                )
            }
        }
    }
}

/**
 * Elige de qué lotes y cuántos kilos se toman para la tanda.
 *
 * Solo aparecen los lotes almacenados y sin bloqueo de venta: pedir kilos de
 * un lote que no se puede usar solo sirve para llevarse el error después.
 */
@Composable
private fun DialogoNuevaTanda(
    contenedor: ContenedorApp,
    alCrear: (Map<String, Double>, Double) -> Unit,
    alCancelar: () -> Unit,
) {
    val lotes by contenedor.lotes.observarLotes(incluirCerrados = false)
        .collectAsState(initial = emptyList())
    val disponibles = lotes.filter {
        it.estado.ordinal >= EstadoLote.ALMACENADO.ordinal && !it.ventaBloqueada
    }

    val kilos = remember { mutableStateMapOf<String, String>() }
    var porcentaje by remember { mutableStateOf("90") }

    val seleccion = disponibles.mapNotNull { lote ->
        leerNumero(kilos[lote.id].orEmpty())?.takeIf { it > 0 }?.let { lote.id to it }
    }.toMap()

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Nueva tanda") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (disponibles.isEmpty()) {
                    Text(
                        "No hay lotes almacenados disponibles. Primero seca y ensaca un " +
                            "lote de grano; si alguno está bloqueado por laboratorio, " +
                            "resuélvelo antes de usarlo.",
                        fontSize = 16.sp,
                    )
                } else {
                    Text(
                        "¿Cuántos kilos tomas de cada lote? Puedes mezclar varios: la " +
                            "app guarda la proporción para la trazabilidad.",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    disponibles.forEach { lote ->
                        CampoNumero(
                            etiqueta = lote.codigo,
                            valor = kilos[lote.id].orEmpty(),
                            alCambiar = { kilos[lote.id] = it },
                            unidad = "kg",
                        )
                    }
                    CampoNumero(
                        etiqueta = "Porcentaje de cacao",
                        valor = porcentaje,
                        alCambiar = { porcentaje = it },
                        unidad = "%",
                        ayuda = "90 % es la receta de la casa. Se puede cambiar por tanda.",
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    alCrear(seleccion, leerNumero(porcentaje) ?: 90.0)
                },
                enabled = seleccion.isNotEmpty() && leerNumero(porcentaje) != null,
            ) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}

/**
 * Detalle de una tanda: tostado, descascarillado, refinado, atemperado,
 * empaque y venta (RF-TOS-*, RF-DES-*, RF-REF-*, RF-ATE-*, RF-EMP-*, RF-VEN-*).
 *
 * Es una sola pantalla larga en vez de seis pantallas separadas a propósito:
 * el chocolatero trabaja con una tanda delante durante varios días y necesita
 * ver de un vistazo en qué punto va, no navegar por un menú cada vez.
 */
@Composable
fun PantallaDetalleTanda(
    contenedor: ContenedorApp,
    navegacion: NavHostController,
    tandaId: String,
) {
    var completa by remember { mutableStateOf<ProduccionCompleta?>(null) }
    var costos by remember { mutableStateOf<ResumenCostos?>(null) }
    var trazabilidad by remember { mutableStateOf<List<String>>(emptyList()) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var recargar by remember { mutableStateOf(0) }

    LaunchedEffect(tandaId, recargar) {
        completa = contenedor.produccion.completa(tandaId)
        costos = contenedor.apoyo.costosDeProduccion(tandaId)
        trazabilidad = contenedor.produccion.trazabilidadDe(tandaId)
    }

    val datos = completa
    Scaffold(
        topBar = {
            BarraSuperior(datos?.produccion?.codigo ?: "Tanda", navegacion)
        },
    ) { relleno ->
        if (datos == null) {
            Text(
                "Cargando la tanda…",
                modifier = Modifier.padding(relleno).padding(24.dp),
                fontSize = 16.sp,
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
            mensaje?.let {
                Aviso(texto = it, color = ColoresEstado.bien, icono = Icons.Default.CheckCircle)
            }

            // --------------------------------------------------- resumen
            TarjetaSeccion("De dónde viene", icono = Icons.Default.Inventory2) {
                FilaDato("Grano usado", Formato.kg(datos.kgGranoUsado))
                FilaDato("Cacao", "${Formato.numero(datos.produccion.porcentajeCacao, 0)} %")
                Spacer(Modifier.height(4.dp))
                trazabilidad.forEach { linea ->
                    Text("• $linea", fontSize = 15.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
            }

            SeccionTostado(contenedor, datos) { texto ->
                mensaje = texto
                recargar++
            }
            SeccionDescascarillado(contenedor, datos) { texto ->
                mensaje = texto
                recargar++
            }
            SeccionRefinado(contenedor, datos) { texto ->
                mensaje = texto
                recargar++
            }
            SeccionAtemperado(contenedor, datos) { texto ->
                mensaje = texto
                recargar++
            }
            SeccionEmpaque(contenedor, datos) { texto ->
                mensaje = texto
                recargar++
            }
            SeccionVentaYCostos(contenedor, datos, costos) { texto ->
                mensaje = texto
                recargar++
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Tostado (RF-TOS-01 a RF-TOS-05).
 *
 * La merma es el dato que de verdad importa: un grano bien tostado pierde
 * entre un 5 y un 8 % de peso. Por debajo se quedó crudo, por encima se
 * quemó, y en ambos casos el sabor de la barra ya no se arregla después.
 */
@Composable
private fun SeccionTostado(
    contenedor: ContenedorApp,
    datos: ProduccionCompleta,
    alGuardar: (String) -> Unit,
) {
    val alcance = rememberCoroutineScope()
    val previo = datos.tostado

    var kgEntrada by remember(previo) {
        mutableStateOf(previo?.kgEntrada?.takeIf { it > 0 }?.let { Formato.numero(it, 2) }
            ?: Formato.numero(datos.kgGranoUsado, 2))
    }
    var kgSalida by remember(previo) {
        mutableStateOf(previo?.kgSalida?.let { Formato.numero(it, 2) }.orEmpty())
    }
    var temp by remember(previo) {
        mutableStateOf(previo?.tempC?.let { Formato.numero(it, 0) }.orEmpty())
    }
    var minutos by remember(previo) { mutableStateOf(previo?.minutos?.toString().orEmpty()) }
    var receta by remember(previo) { mutableStateOf(previo?.recetaNombre.orEmpty()) }
    var gradoUsuario by remember(previo) { mutableStateOf(previo?.gradoUsuario.orEmpty()) }
    var fotoId by remember(previo) { mutableStateOf(previo?.fotoId) }
    var analisisIa by remember(previo) { mutableStateOf<Map<String, String>>(emptyMap()) }

    val entrada = leerNumero(kgEntrada)
    val salida = leerNumero(kgSalida)
    val merma = if (entrada != null && salida != null && entrada > 0) {
        100.0 * (entrada - salida) / entrada
    } else {
        null
    }

    TarjetaSeccion("1 · Tostado", icono = Icons.Default.LocalFireDepartment) {
        CampoNumero("Grano que entra", kgEntrada, { kgEntrada = it }, unidad = "kg")
        CampoNumero("Grano que sale", kgSalida, { kgSalida = it }, unidad = "kg")
        CampoNumero("Temperatura", temp, { temp = it }, unidad = "°C")
        CampoNumero("Minutos", minutos, { minutos = it }, unidad = "min", decimales = false)
        OutlinedTextField(
            value = receta,
            onValueChange = { receta = it },
            label = { Text("Nombre del perfil de tostado") },
            placeholder = { Text("Ej.: CCN-51 suave 130 °C") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )

        merma?.let {
            Spacer(Modifier.height(4.dp))
            Aviso(
                titulo = "Merma: ${Formato.porcentaje(it)}",
                texto = when {
                    it < 4 -> "Es poca merma: puede que el grano se haya quedado corto " +
                        "de tostado y sepa a crudo."
                    it > 10 -> "Es mucha merma: revisa si se pasó de temperatura o de " +
                        "tiempo. El sabor a quemado no se quita después."
                    else -> "Está en el rango esperado para CCN-51."
                },
                color = if (it in 4.0..10.0) ColoresEstado.bien else ColoresEstado.atencion,
                icono = if (it in 4.0..10.0) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
            )
        }

        Spacer(Modifier.height(8.dp))
        BotonFotoIa(
            contenedor = contenedor,
            tarea = "tostado",
            clases = listOf("crudo", "claro", "medio", "oscuro", "quemado"),
            texto = "Foto del grano tostado",
            consejo = "Parte un grano por la mitad y fotografía el corte con luz de día.",
            alCapturar = { captura ->
                alcance.launch {
                    fotoId = contenedor.apoyo.guardarFoto(
                        rutaLocal = captura.archivo.absolutePath,
                        etapa = "tostado",
                        loteProduccionId = datos.produccion.id,
                        analisisIa = captura.analisisJson,
                        etiquetaUsuario = captura.decisionUsuario,
                        aptaDataset = captura.aptaParaDataset,
                    )
                    gradoUsuario = captura.decisionUsuario
                    analisisIa = captura.analisisJson
                }
            },
        )
        if (gradoUsuario.isNotBlank()) {
            FilaDato("Grado de tostado", Formato.nombreBonito(gradoUsuario))
        }

        Spacer(Modifier.height(8.dp))
        BotonGrande(
            texto = "Guardar el tostado",
            icono = Icons.Default.Save,
            habilitado = entrada != null && entrada > 0,
        ) {
            alcance.launch {
                val alertas = contenedor.produccion.guardarTostado(
                    loteProduccionId = datos.produccion.id,
                    kgEntrada = entrada ?: 0.0,
                    kgSalida = salida,
                    tempC = leerNumero(temp),
                    minutos = minutos.toIntOrNull(),
                    gradoIa = analisisIa["clase"].orEmpty(),
                    confianzaIa = analisisIa["confianza"]?.toDoubleOrNull(),
                    gradoUsuario = gradoUsuario,
                    modeloVersion = analisisIa["modelo"].orEmpty(),
                    fotoId = fotoId,
                    recetaNombre = receta.trim(),
                )
                alGuardar(
                    if (alertas.isEmpty()) "Tostado guardado."
                    else "Tostado guardado, con ${alertas.size} aviso(s) sobre la merma.",
                )
            }
        }
    }
}

/** Descascarillado (RF-DES-01): nibs y cascarilla, que van al inventario. */
@Composable
private fun SeccionDescascarillado(
    contenedor: ContenedorApp,
    datos: ProduccionCompleta,
    alGuardar: (String) -> Unit,
) {
    val alcance = rememberCoroutineScope()
    val previo = datos.descascarillado

    var nibs by remember(previo) {
        mutableStateOf(previo?.kgNibs?.takeIf { it > 0 }?.let { Formato.numero(it, 2) }.orEmpty())
    }
    var cascarilla by remember(previo) {
        mutableStateOf(
            previo?.kgCascarilla?.takeIf { it > 0 }?.let { Formato.numero(it, 2) }.orEmpty(),
        )
    }
    var notas by remember(previo) { mutableStateOf(previo?.notas.orEmpty()) }

    val kgNibs = leerNumero(nibs)
    val kgCascarilla = leerNumero(cascarilla)

    TarjetaSeccion("2 · Descascarillado", icono = Icons.Default.Grain) {
        Text(
            "La cascarilla suele ser entre el 10 y el 15 % del grano tostado. Si sale " +
                "mucho más, se está yendo nib a la basura.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        CampoNumero("Nibs obtenidos", nibs, { nibs = it }, unidad = "kg")
        CampoNumero("Cascarilla", cascarilla, { cascarilla = it }, unidad = "kg")
        OutlinedTextField(
            value = notas,
            onValueChange = { notas = it },
            label = { Text("Notas") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )

        if (kgNibs != null && kgCascarilla != null && kgNibs + kgCascarilla > 0) {
            val pct = 100.0 * kgCascarilla / (kgNibs + kgCascarilla)
            FilaDato(
                "Cascarilla sobre el total",
                Formato.porcentaje(pct),
                color = if (pct > 18) ColoresEstado.atencion else ColoresEstado.bien,
            )
        }

        Spacer(Modifier.height(8.dp))
        BotonGrande(
            texto = "Guardar el descascarillado",
            icono = Icons.Default.Save,
            habilitado = kgNibs != null && kgNibs > 0,
        ) {
            alcance.launch {
                contenedor.produccion.guardarDescascarillado(
                    loteProduccionId = datos.produccion.id,
                    kgNibs = kgNibs ?: 0.0,
                    kgCascarilla = kgCascarilla ?: 0.0,
                    notas = notas.trim(),
                )
                alGuardar("Descascarillado guardado. Los nibs entraron al inventario.")
            }
        }
    }
}

/**
 * Refinado y conchado (RF-REF-01 a RF-REF-04).
 *
 * La calculadora de receta va primero porque es el motivo por el que se abre
 * esta sección: el usuario tiene X kilos de nibs y necesita saber cuánta
 * azúcar pesar, ahora, antes de encender el melanger.
 */
@Composable
private fun SeccionRefinado(
    contenedor: ContenedorApp,
    datos: ProduccionCompleta,
    alGuardar: (String) -> Unit,
) {
    val alcance = rememberCoroutineScope()
    val previo = datos.refinado
    val nibsDisponibles = datos.descascarillado?.kgNibs ?: 0.0

    var nibs by remember(previo, nibsDisponibles) {
        mutableStateOf(
            (previo?.nibsKg?.takeIf { it > 0 } ?: nibsDisponibles.takeIf { it > 0 })
                ?.let { Formato.numero(it, 2) }.orEmpty(),
        )
    }
    var mantecaExtra by remember(previo) { mutableStateOf("0") }
    var usarLecitina by remember(previo) { mutableStateOf((previo?.lecitinaKg ?: 1.0) > 0) }
    var horas by remember(previo) {
        mutableStateOf(previo?.horas?.let { Formato.numero(it, 1) }.orEmpty())
    }
    var notas by remember(previo) { mutableStateOf(previo?.notas.orEmpty()) }

    // Evaluación sensorial 1–5 (RF-REF-04).
    val sensorial = remember(previo) {
        mutableStateMapOf(
            "acidez" to 3,
            "amargor" to 3,
            "astringencia" to 3,
            "textura" to 3,
        )
    }

    val kgNibs = leerNumero(nibs)
    val receta = remember(kgNibs, mantecaExtra, usarLecitina, datos.produccion.porcentajeCacao) {
        val valor = kgNibs ?: return@remember null
        runCatching {
            contenedor.produccion.calcularReceta(
                kgNibs = valor,
                porcentajeCacao = datos.produccion.porcentajeCacao,
                mantecaExtraPct = leerNumero(mantecaExtra) ?: 0.0,
                usarLecitina = usarLecitina,
            )
        }.getOrNull()
    }

    TarjetaSeccion("3 · Refinado y conchado", icono = Icons.Default.Blender) {
        CampoNumero("Nibs que van al melanger", nibs, { nibs = it }, unidad = "kg")
        CampoNumero(
            etiqueta = "Manteca de cacao añadida",
            valor = mantecaExtra,
            alCambiar = { mantecaExtra = it },
            unidad = "% del total",
            ayuda = "En un 90 % suele hacer falta algo para que fluya al moldear.",
        )
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = usarLecitina, onCheckedChange = { usarLecitina = it })
            Spacer(Modifier.width(8.dp))
            Text("Usar lecitina (0,5 %)", fontSize = 16.sp)
        }

        // ------------------------------------------------- receta calculada
        receta?.let { r ->
            Spacer(Modifier.height(8.dp))
            Aviso(
                titulo = "Receta para ${Formato.kg(r.kgTotal)}",
                texto = buildString {
                    append("Nibs ${Formato.kg(r.kgNibs)}")
                    append(" · Azúcar ${Formato.kg(r.kgAzucar)}")
                    if (r.kgMantecaAnadida > 0) {
                        append(" · Manteca ${Formato.kg(r.kgMantecaAnadida)}")
                    }
                    if (r.kgLecitina > 0) {
                        append(" · Lecitina ${Formato.numero(r.kgLecitina * 1000, 0)} g")
                    }
                    append(
                        "\nSalen unas ${r.barrasEstimadas()} barras de 50 g " +
                            "(cacao real ${Formato.porcentaje(r.porcentajeCacaoReal)}).",
                    )
                },
                color = ColoresEstado.bien,
                icono = Icons.Default.Calculate,
            )
        }

        CampoNumero(
            etiqueta = "Horas de refinado",
            valor = horas,
            alCambiar = { horas = it },
            unidad = "h",
            ayuda = "Entre 24 y 48 h es lo normal en un melanger casero.",
        )

        Spacer(Modifier.height(8.dp))
        Text("¿Cómo está de sabor y textura?", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Del 1 al 5. Sirve para comparar tandas y saber si media hora más de " +
                "conchado le vendría bien.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        listOf("acidez", "amargor", "astringencia", "textura").forEach { atributo ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    Formato.nombreBonito(atributo),
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                (1..5).forEach { nota ->
                    val elegida = sensorial[atributo] == nota
                    TextButton(onClick = { sensorial[atributo] = nota }) {
                        Text(
                            nota.toString(),
                            fontSize = 18.sp,
                            fontWeight = if (elegida) FontWeight.Bold else FontWeight.Normal,
                            color = if (elegida) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = notas,
            onValueChange = { notas = it },
            label = { Text("Notas del conchado") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )

        Spacer(Modifier.height(8.dp))
        BotonGrande(
            texto = "Guardar el refinado",
            icono = Icons.Default.Save,
            habilitado = receta != null,
        ) {
            val r = receta ?: return@BotonGrande
            alcance.launch {
                val ahora = Instant.now()
                val horasReales = leerNumero(horas)
                contenedor.produccion.guardarRefinado(
                    loteProduccionId = datos.produccion.id,
                    inicio = previo?.inicio ?: ahora,
                    nibsKg = r.kgNibs,
                    azucarKg = r.kgAzucar,
                    fin = if (horasReales != null) ahora else null,
                    horas = horasReales,
                    mantecaKg = r.kgMantecaAnadida,
                    lecitinaKg = r.kgLecitina,
                    sensorial = sensorial.toMap(),
                    notas = notas.trim(),
                )
                alGuardar("Refinado guardado. Los insumos salieron del inventario.")
            }
        }
    }
}

/**
 * Atemperado y moldeado (RF-ATE-01 a RF-ATE-05).
 *
 * Antes de nada se comprueba el cuarto: con más de 24 °C o más de 60 % de
 * humedad el chocolate no cuaja bien por mucho que las tres temperaturas se
 * hagan perfectas. Avisar después de moldear no sirve de nada.
 */
@Composable
private fun SeccionAtemperado(
    contenedor: ContenedorApp,
    datos: ProduccionCompleta,
    alGuardar: (String) -> Unit,
) {
    val alcance = rememberCoroutineScope()

    var metodo by remember { mutableStateOf(MetodoAtemperado.SIEMBRA) }
    var fundido by remember { mutableStateOf("48") }
    var enfriado by remember { mutableStateOf("27") }
    var trabajo by remember { mutableStateOf("31.5") }
    var tempCuarto by remember { mutableStateOf("") }
    var hrCuarto by remember { mutableStateOf("") }
    var pruebaPapel by remember { mutableStateOf<Boolean?>(null) }
    var resultadoUsuario by remember { mutableStateOf("") }
    var fotoId by remember { mutableStateOf<String?>(null) }
    var analisisIa by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var advertencia by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tempCuarto, hrCuarto) {
        advertencia = contenedor.produccion.advertenciaDelCuarto(
            leerNumero(tempCuarto),
            leerNumero(hrCuarto),
        )
    }

    TarjetaSeccion("4 · Atemperado y moldeado", icono = Icons.Default.AcUnit) {
        Text("¿Cómo vas a atemperar?", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            MetodoAtemperado.entries.forEach { opcion ->
                FilterChip(
                    selected = metodo == opcion,
                    onClick = { metodo = opcion },
                    label = { Text(opcion.etiqueta) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        CampoNumero("Temperatura del cuarto", tempCuarto, { tempCuarto = it }, unidad = "°C")
        CampoNumero("Humedad del cuarto", hrCuarto, { hrCuarto = it }, unidad = "%")

        advertencia?.let {
            Aviso(
                titulo = "Ojo con las condiciones",
                texto = it,
                color = ColoresEstado.atencion,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text("Las tres temperaturas", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        CampoNumero("1. Fundido", fundido, { fundido = it }, unidad = "°C")
        CampoNumero("2. Enfriado", enfriado, { enfriado = it }, unidad = "°C")
        CampoNumero("3. De trabajo", trabajo, { trabajo = it }, unidad = "°C")

        Spacer(Modifier.height(8.dp))
        Text(
            "Prueba del papel: moja la punta de un papel y déjala 3 minutos a " +
                "temperatura ambiente. Si endurece con brillo, está atemperado.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            FilterChip(
                selected = pruebaPapel == true,
                onClick = { pruebaPapel = true },
                label = { Text("Endureció con brillo") },
                modifier = Modifier.padding(end = 8.dp),
            )
            FilterChip(
                selected = pruebaPapel == false,
                onClick = { pruebaPapel = false },
                label = { Text("Quedó mate o blando") },
            )
        }

        Spacer(Modifier.height(8.dp))
        BotonFotoIa(
            contenedor = contenedor,
            tarea = "chocolate",
            clases = listOf("brillo_correcto", "fat_bloom", "sugar_bloom", "mate", "burbujas"),
            texto = "Foto de la barra desmoldada",
            consejo = "Con luz lateral se ve mucho mejor si hay brillo o si está velada.",
            alCapturar = { captura ->
                alcance.launch {
                    fotoId = contenedor.apoyo.guardarFoto(
                        rutaLocal = captura.archivo.absolutePath,
                        etapa = "chocolate",
                        loteProduccionId = datos.produccion.id,
                        analisisIa = captura.analisisJson,
                        etiquetaUsuario = captura.decisionUsuario,
                        aptaDataset = captura.aptaParaDataset,
                    )
                    resultadoUsuario = captura.decisionUsuario
                    analisisIa = captura.analisisJson
                }
            },
        )
        if (resultadoUsuario.isNotBlank()) {
            FilaDato("Aspecto de la barra", Formato.nombreBonito(resultadoUsuario))
        }

        Spacer(Modifier.height(8.dp))
        BotonGrande(texto = "Guardar el atemperado", icono = Icons.Default.Save) {
            alcance.launch {
                val alertas = contenedor.produccion.guardarAtemperado(
                    loteProduccionId = datos.produccion.id,
                    metodo = metodo,
                    temperaturas = buildMap {
                        leerNumero(fundido)?.let { put("fundido", it) }
                        leerNumero(enfriado)?.let { put("enfriado", it) }
                        leerNumero(trabajo)?.let { put("trabajo", it) }
                    },
                    tempCuarto = leerNumero(tempCuarto),
                    hrCuarto = leerNumero(hrCuarto),
                    pruebaPapel = pruebaPapel,
                    resultadoIa = analisisIa["clase"].orEmpty(),
                    confianzaIa = analisisIa["confianza"]?.toDoubleOrNull(),
                    resultadoUsuario = resultadoUsuario,
                    modeloVersion = analisisIa["modelo"].orEmpty(),
                    fotoId = fotoId,
                )
                alGuardar(
                    if (alertas.isEmpty()) "Atemperado guardado."
                    else "Atemperado guardado, con ${alertas.size} aviso(s).",
                )
            }
        }

        // Inspecciones posteriores (RF-ATE-05): el fat bloom aparece días después.
        if (datos.atemperados.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Inspecciones hechas", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            datos.atemperados.forEach { a ->
                FilaDato(
                    Formato.fechaHora(a.fecha),
                    listOfNotNull(
                        a.resultadoUsuario.takeIf { it.isNotBlank() }
                            ?.let { Formato.nombreBonito(it) },
                        a.diasInspeccion?.let { "día $it" },
                    ).joinToString(" · ").ifBlank { a.metodo.etiqueta },
                )
            }
        }
    }
}

/**
 * Empaque y etiquetado (RF-EMP-01, RF-EMP-02).
 *
 * Aquí se cierra la tanda: se cuentan las barras reales, no las estimadas, y
 * la fecha de vencimiento se calcula sola a partir de la vida útil para que
 * nadie la ponga mal a mano.
 */
@Composable
private fun SeccionEmpaque(
    contenedor: ContenedorApp,
    datos: ProduccionCompleta,
    alGuardar: (String) -> Unit,
) {
    val alcance = rememberCoroutineScope()
    val previo = datos.empaque

    var barras by remember(previo) { mutableStateOf(previo?.barras?.toString().orEmpty()) }
    var peso by remember(previo) {
        mutableStateOf(Formato.numero(previo?.pesoUnitarioG ?: 50.0, 0))
    }
    var vidaUtil by remember(previo) {
        mutableStateOf((previo?.vidaUtilMeses ?: 12).toString())
    }
    var notificacion by remember(previo) { mutableStateOf("") }
    var qrVisible by remember { mutableStateOf(false) }

    val numeroBarras = barras.toIntOrNull()
    val pesoUnitario = leerNumero(peso)

    TarjetaSeccion("5 · Empaque", icono = Icons.Default.Inventory2) {
        CampoNumero("Barras empacadas", barras, { barras = it }, decimales = false)
        CampoNumero("Peso por barra", peso, { peso = it }, unidad = "g")
        CampoNumero(
            etiqueta = "Vida útil",
            valor = vidaUtil,
            alCambiar = { vidaUtil = it },
            unidad = "meses",
            decimales = false,
            ayuda = "Un 90 % sin leche aguanta bien 12 meses en sitio fresco y seco.",
        )
        OutlinedTextField(
            value = notificacion,
            onValueChange = { notificacion = it },
            label = { Text("Notificación sanitaria (si la tienes)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )

        if (numeroBarras != null && pesoUnitario != null) {
            FilaDato(
                "Chocolate empacado",
                Formato.kg(numeroBarras * pesoUnitario / 1000.0),
            )
        }

        previo?.let { e ->
            Spacer(Modifier.height(8.dp))
            FilaDato("Elaborado", Formato.fecha(e.fechaElaboracion))
            FilaDato("Vence", Formato.fecha(e.fechaVencimiento))
            TextButton(onClick = { qrVisible = true }) {
                Text("Ver el QR de la etiqueta")
            }
        }

        Spacer(Modifier.height(8.dp))
        BotonGrande(
            texto = "Guardar el empaque",
            icono = Icons.Default.Save,
            habilitado = numeroBarras != null && numeroBarras > 0 && pesoUnitario != null,
        ) {
            alcance.launch {
                contenedor.produccion.guardarEmpaque(
                    loteProduccionId = datos.produccion.id,
                    barras = numeroBarras ?: 0,
                    pesoUnitarioG = pesoUnitario ?: 50.0,
                    vidaUtilMeses = vidaUtil.toIntOrNull() ?: 12,
                    etiqueta = buildMap {
                        put(
                            "ingredientes",
                            "Pasta de cacao, azúcar" +
                                (if ((datos.refinado?.lecitinaKg ?: 0.0) > 0) ", lecitina de girasol" else ""),
                        )
                        put("cacao", "${Formato.numero(datos.produccion.porcentajeCacao, 0)} % mínimo")
                        if (notificacion.isNotBlank()) {
                            put("notificacion_sanitaria", notificacion.trim())
                        }
                    },
                )
                alGuardar("Empaque guardado. La tanda queda terminada.")
            }
        }
    }

    if (qrVisible && previo != null) {
        AlertDialog(
            onDismissRequest = { qrVisible = false },
            title = { Text(previo.codigoQr) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CodigoQr(previo.codigoQr, Modifier.size(240.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Imprímelo en la etiqueta. Quien lo escanee llega a la historia " +
                            "completa de esta barra, desde la finca.",
                        fontSize = 15.sp,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { qrVisible = false }) { Text("Listo") } },
        )
    }
}

/**
 * Ventas y costos de la tanda (RF-VEN-01, RF-VEN-02, RF-COS-01, RF-COS-02).
 *
 * El costo por barra se muestra junto al precio de venta a propósito: es el
 * número que decide si el negocio se sostiene, y verlo separado en otra
 * pantalla hace que nadie lo mire.
 */
@Composable
private fun SeccionVentaYCostos(
    contenedor: ContenedorApp,
    datos: ProduccionCompleta,
    costos: ResumenCostos?,
    alGuardar: (String) -> Unit,
) {
    val alcance = rememberCoroutineScope()

    var concepto by remember { mutableStateOf("insumos") }
    var monto by remember { mutableStateOf("") }
    var barrasVendidas by remember { mutableStateOf("") }
    var precio by remember { mutableStateOf("") }
    var cliente by remember { mutableStateOf("") }
    var consumoPropio by remember { mutableStateOf(false) }
    var margen by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(datos.produccion.id, costos) {
        margen = contenedor.apoyo.margenDeProduccion(datos.produccion.id)
    }

    TarjetaSeccion("6 · Costos y ventas", icono = Icons.Default.Payments) {
        costos?.let { c ->
            FilaDato("Costo total", Formato.dolares(c.totalUsd))
            c.costoPorBarra?.let { FilaDato("Costo por barra", Formato.dolares(it)) }
            c.costoPorKgGrano?.let { FilaDato("Costo por kg de grano", Formato.dolares(it)) }
            margen?.let {
                FilaDato(
                    "Margen hasta ahora",
                    Formato.dolares(it),
                    color = if (it >= 0) ColoresEstado.bien else ColoresEstado.problema,
                )
            }
            if (c.porConcepto.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                c.porConcepto.forEach { (nombre, valor) ->
                    FilaDato(Formato.nombreBonito(nombre), Formato.dolares(valor))
                }
            }
        }

        // ------------------------------------------------------ nuevo costo
        Spacer(Modifier.height(12.dp))
        Text("Anotar un costo", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("mazorcas", "transporte", "insumos", "energia", "empaques").forEach { c ->
                FilterChip(
                    selected = concepto == c,
                    onClick = { concepto = c },
                    label = { Text(Formato.nombreBonito(c), fontSize = 13.sp) },
                )
            }
        }
        CampoNumero("Monto", monto, { monto = it }, unidad = "USD")
        BotonGrande(
            texto = "Guardar el costo",
            icono = Icons.Default.Save,
            habilitado = leerNumero(monto) != null,
        ) {
            alcance.launch {
                contenedor.apoyo.registrarCosto(
                    concepto = concepto,
                    montoUsd = leerNumero(monto) ?: 0.0,
                    loteProduccionId = datos.produccion.id,
                )
                monto = ""
                alGuardar("Costo anotado.")
            }
        }

        // ------------------------------------------------------ nueva venta
        Spacer(Modifier.height(16.dp))
        Text("Registrar una salida", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        CampoNumero("Barras", barrasVendidas, { barrasVendidas = it }, decimales = false)
        CampoNumero("Precio por barra", precio, { precio = it }, unidad = "USD")
        OutlinedTextField(
            value = cliente,
            onValueChange = { cliente = it },
            label = { Text("Cliente") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = consumoPropio, onCheckedChange = { consumoPropio = it })
            Spacer(Modifier.width(8.dp))
            Text("Es consumo propio o regalo", fontSize = 16.sp)
        }
        BotonGrande(
            texto = "Guardar la salida",
            icono = Icons.Default.Save,
            habilitado = (barrasVendidas.toIntOrNull() ?: 0) > 0,
        ) {
            alcance.launch {
                contenedor.apoyo.registrarVenta(
                    loteProduccionId = datos.produccion.id,
                    barras = barrasVendidas.toIntOrNull() ?: 0,
                    cliente = cliente.trim(),
                    precioUnitarioUsd = if (consumoPropio) 0.0 else leerNumero(precio) ?: 0.0,
                    consumoPropio = consumoPropio,
                )
                barrasVendidas = ""
                precio = ""
                cliente = ""
                alGuardar("Salida registrada y descontada del inventario.")
            }
        }
    }
}
