package ec.cacaotrace.ui.pantallas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.rememberAsyncImagePainter
import ec.cacaotrace.ContenedorApp
import ec.cacaotrace.ia.PreparadorImagen
import ec.cacaotrace.nucleo.ia.ResultadoDeteccion
import ec.cacaotrace.nucleo.norma.CalificadorCorte
import ec.cacaotrace.nucleo.norma.ResultadoCorte
import ec.cacaotrace.nucleo.norma.TablaNorma
import ec.cacaotrace.ui.ColoresEstado
import ec.cacaotrace.ui.comun.Aviso
import ec.cacaotrace.ui.comun.BarraSuperior
import ec.cacaotrace.ui.comun.BotonGrande
import ec.cacaotrace.ui.comun.CampoNumero
import ec.cacaotrace.ui.comun.FilaDato
import ec.cacaotrace.ui.comun.Formato
import ec.cacaotrace.ui.comun.TarjetaSeccion
import ec.cacaotrace.ui.comun.archivoTemporal
import ec.cacaotrace.ui.comun.leerNumero
import ec.cacaotrace.ui.comun.uriDe
import java.io.File
import kotlinx.coroutines.launch

/**
 * Prueba de corte (RF-PRC-01 a RF-PRC-10).
 *
 * Es la pantalla que decide el grado del lote, así que tiene dos caminos que
 * llegan al mismo sitio: la foto del tablero con el modelo M2, y el conteo
 * manual con botones grandes. El manual no es un plan B de segunda: es el que
 * se usa mientras el modelo no alcance sus metas, y tiene que ser cómodo.
 */
@Composable
fun PantallaPruebaCorte(
    contenedor: ContenedorApp,
    navegacion: NavHostController,
    loteId: String,
) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()

    var tabla by remember { mutableStateOf<TablaNorma?>(null) }
    val conteo = remember { mutableStateMapOf<String, Int>() }
    var deteccion by remember { mutableStateOf<ResultadoDeteccion?>(null) }
    var foto by remember { mutableStateOf<File?>(null) }
    var perfil by rememberSaveable { mutableStateOf(CalificadorCorte.PERFIL_POR_DEFECTO) }
    var peso100 by rememberSaveable { mutableStateOf("") }
    var esParcial by rememberSaveable { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var granoTocado by remember { mutableStateOf<Int?>(null) }
    var archivoPendiente by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(loteId) {
        val t = contenedor.config.norma()
        tabla = t
        t.clasesContables.forEach { conteo.putIfAbsent(it, 0) }
    }

    val tomarFoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { exito ->
        val archivo = archivoPendiente
        if (!exito || archivo == null) return@rememberLauncherForActivityResult
        alcance.launch {
            val comprimida = PreparadorImagen.guardarComprimida(
                contexto, archivo, "prueba_corte",
            )
            foto = comprimida
            if (!contenedor.modelos.estaDisponible("corte")) {
                mensaje = "La foto se guardó. Todavía no hay modelo de prueba de corte: " +
                    "cuenta con los botones de abajo."
                return@launch
            }
            runCatching {
                val pixeles = PreparadorImagen.paraModelo(comprimida, 640) ?: return@launch
                contenedor.modelos.detectarGranos(pixeles = pixeles)
            }.onSuccess { d ->
                deteccion = d
                tabla?.clasesContables?.forEach { conteo[it] = 0 }
                d.conteo.forEach { (clase, n) -> conteo[clase] = n }
                mensaje = if (d.conteoDudoso) {
                    "Se detectaron ${d.total} granos. Revisa el conteo."
                } else {
                    "Se detectaron ${d.total} granos en ${d.milisegundos} ms"
                }
            }.onFailure { mensaje = it.message }
        }
    }

    val t = tabla ?: return
    val total = conteo.values.sum()
    val resultado: ResultadoCorte? = remember(conteo.toMap(), perfil, t) {
        runCatching { CalificadorCorte(t).calificar(conteo.toMap(), perfil) }.getOrNull()
    }

    granoTocado?.let { indice ->
        val d = deteccion
        if (d != null) {
            AlertDialog(
                onDismissRequest = { granoTocado = null },
                title = { Text("Este grano es…") },
                text = {
                    Column {
                        Text(
                            "El modelo dijo \"${Formato.nombreBonito(d.granos[indice].clase)}\" " +
                                "con ${(d.granos[indice].confianza * 100).toInt()} % de " +
                                "seguridad.",
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        t.clasesContables.forEach { clase ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    val nuevo = d.conGranoCorregido(indice, clase)
                                    deteccion = nuevo
                                    t.clasesContables.forEach { conteo[it] = 0 }
                                    nuevo.conteo.forEach { (c, n) -> conteo[c] = n }
                                    granoTocado = null
                                }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier.size(20.dp).border(
                                        2.dp, ColoresEstado.deGrano(clase), CircleShape,
                                    ),
                                )
                                Text("  ${Formato.nombreBonito(clase)}", fontSize = 16.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { granoTocado = null }) { Text("Cancelar") }
                },
            )
        }
    }

    Scaffold(topBar = { BarraSuperior("Prueba de corte", navegacion) }) { relleno ->
        LazyColumn(
            Modifier.fillMaxSize().padding(relleno),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                mensaje?.let { Aviso(texto = it) }

                TarjetaSeccion("Cómo se hace", icono = Icons.Default.Checklist) {
                    Column {
                        Text(
                            "1. Toma 100 granos al azar del saco, sin escoger.\n" +
                                "2. Córtalos a lo largo, por la mitad.\n" +
                                "3. Ponlos en el tablero de 10 × 10 con la cara cortada " +
                                "arriba.\n" +
                                "4. Fotografía de frente, con buena luz y sin flash.",
                            fontSize = 16.sp,
                        )
                        TextButton(onClick = { /* la plantilla se genera en Reporte */ }) {
                            Icon(Icons.Default.Print, contentDescription = null)
                            Text("  Imprime la plantilla desde el reporte del lote")
                        }
                    }
                }

                TarjetaSeccion("Foto del tablero", icono = Icons.Default.PhotoCamera) {
                    Column {
                        foto?.let { archivo ->
                            BoxWithConstraints(
                                Modifier.fillMaxWidth().aspectRatio(1f),
                            ) {
                                val lado = maxWidth
                                Image(
                                    painter = rememberAsyncImagePainter(archivo),
                                    contentDescription = "Tablero de la prueba de corte",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                        .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp)),
                                )
                                deteccion?.granos?.forEachIndexed { i, g ->
                                    Box(
                                        Modifier
                                            .offset(x = lado * g.x.toFloat(), y = lado * g.y.toFloat())
                                            .size(
                                                width = lado * g.ancho.toFloat(),
                                                height = lado * g.alto.toFloat(),
                                            )
                                            .border(
                                                if (g.corregidoPorUsuario) 3.dp else 2.dp,
                                                ColoresEstado.deGrano(g.clase),
                                                RoundedCornerShape(4.dp),
                                            )
                                            .clickable { granoTocado = i },
                                    )
                                }
                            }
                            deteccion?.let { d ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Toca un grano para corregir su clase. Tu corrección " +
                                        "manda sobre lo que dijo el modelo.",
                                    fontSize = 14.sp,
                                )
                                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    d.conteo.entries.take(4).forEach { (clase, n) ->
                                        AssistChip(
                                            onClick = {},
                                            label = {
                                                Text("${Formato.nombreBonito(clase)}: $n")
                                            },
                                            modifier = Modifier.padding(end = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        BotonGrande(
                            texto = if (foto == null) {
                                "Fotografiar el tablero"
                            } else {
                                "Repetir la foto"
                            },
                            icono = Icons.Default.PhotoCamera,
                        ) {
                            val archivo = archivoTemporal(contexto, "prueba_corte")
                            archivoPendiente = archivo
                            tomarFoto.launch(uriDe(contexto, archivo))
                        }
                    }
                }

                TarjetaSeccion(
                    "Conteo por clase",
                    icono = Icons.Default.GridOn,
                    accion = {
                        Text(
                            "$total granos",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (total in 97..103) {
                                ColoresEstado.bien
                            } else {
                                ColoresEstado.atencion
                            },
                        )
                    },
                ) {
                    Column {
                        if (total > 0 && total !in 97..103) {
                            Aviso(
                                texto = "Llevas $total granos y la norma usa 100. Revisa " +
                                    "el conteo antes de dar el resultado por bueno.",
                            )
                        }
                        t.clasesContables.forEach { clase ->
                            ContadorGrano(
                                clase = clase,
                                valor = conteo[clase] ?: 0,
                                alCambiar = { conteo[clase] = it },
                            )
                        }
                        TextButton(
                            onClick = {
                                t.clasesContables.forEach { conteo[it] = 0 }
                                deteccion = null
                            },
                        ) { Text("Empezar el conteo de cero") }
                    }
                }

                TarjetaSeccion("Resultado", icono = Icons.Default.WorkspacePremium) {
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            t.perfiles.keys.forEach { clave ->
                                FilterChip(
                                    selected = perfil == clave,
                                    onClick = { perfil = clave },
                                    label = {
                                        Text(
                                            if (clave == "ccn51_referencia") {
                                                "CCN-51"
                                            } else {
                                                "Grados 1-2-3"
                                            },
                                        )
                                    },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        if (resultado == null) {
                            Text(
                                "Cuenta al menos un grano para ver el resultado.",
                                fontSize = 16.sp,
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (resultado.conforme) {
                                        Icons.Default.Verified
                                    } else {
                                        Icons.Default.Info
                                    },
                                    contentDescription = null,
                                    tint = if (resultado.conforme) {
                                        ColoresEstado.bien
                                    } else {
                                        ColoresEstado.atencion
                                    },
                                    modifier = Modifier.size(36.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    resultado.resultado,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            listOf(
                                "fermentado_bueno", "fermentado_ligero", "fermentado_total",
                                "violeta", "pizarroso", "mohoso", "defectuoso",
                            ).forEach { indicador ->
                                FilaDato(
                                    Formato.nombreBonito(indicador),
                                    Formato.porcentaje(resultado.porcentajes[indicador]),
                                )
                            }
                            if (resultado.todasLasFallas.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Qué falta para cumplir", fontSize = 16.sp)
                                resultado.todasLasFallas.forEach { falla ->
                                    Row(Modifier.padding(vertical = 3.dp)) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            tint = ColoresEstado.atencion,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text("  $falla", fontSize = 15.sp)
                                    }
                                }
                            }
                            resultado.avisos.forEach { Aviso(texto = it) }
                        }
                    }
                }

                TarjetaSeccion("Datos adicionales", icono = Icons.Default.Scale) {
                    Column {
                        CampoNumero(
                            "Peso de 100 granos", peso100, { peso100 = it }, unidad = "g",
                            ayuda = "Un grano de buen tamaño pesa alrededor de 1.2 g",
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = esParcial, onCheckedChange = { esParcial = it })
                            Column {
                                Text("  Es una prueba parcial", fontSize = 16.sp)
                                Text(
                                    "  Por ejemplo, la del día 5 con 20-50 granos. No " +
                                        "define el grado del lote.",
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                BotonGrande(
                    texto = "Guardar la prueba",
                    icono = Icons.Default.Save,
                    habilitado = resultado != null,
                ) {
                    val r = resultado ?: return@BotonGrande
                    alcance.launch {
                        val fotoId = foto?.let { archivo ->
                            contenedor.apoyo.guardarFoto(
                                rutaLocal = archivo.absolutePath,
                                etapa = "prueba_corte",
                                loteId = loteId,
                                analisisIa = deteccion?.let {
                                    mapOf(
                                        "modelo" to it.modeloVersion,
                                        "total" to it.total.toString(),
                                    )
                                } ?: emptyMap(),
                                // RF-PRC-09: la foto con las correcciones del
                                // usuario sirve para reentrenar el modelo.
                                aptaDataset = true,
                            )
                        }
                        contenedor.lotes.guardarPruebaCorte(
                            loteId = loteId,
                            conteo = conteo.toMap(),
                            resultado = r,
                            peso100g = leerNumero(peso100),
                            modeloVersion = deteccion?.modeloVersion.orEmpty(),
                            fotoId = fotoId,
                            esParcial = esParcial,
                        )
                        navegacion.popBackStack()
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "El grado con validez comercial lo define la norma oficial y un " +
                        "catador certificado. Esta app te ayuda a llevar el control.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ContadorGrano(clase: String, valor: Int, alCambiar: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).border(3.dp, ColoresEstado.deGrano(clase), CircleShape))
        Text(
            "  ${Formato.nombreBonito(clase)}",
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(onClick = { alCambiar(valor - 1) }, enabled = valor > 0) {
            Icon(Icons.Default.Remove, contentDescription = "Uno menos")
        }
        Text(
            "$valor",
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(48.dp),
        )
        FilledTonalIconButton(onClick = { alCambiar(valor + 1) }) {
            Icon(Icons.Default.Add, contentDescription = "Uno más")
        }
    }
}
