package ec.cacaotrace.ui.pantallas

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ec.cacaotrace.ContenedorApp
import ec.cacaotrace.informes.GeneradorPdf
import ec.cacaotrace.informes.LineaPdf
import ec.cacaotrace.informes.SeccionPdf
import ec.cacaotrace.nucleo.norma.CalificadorCorte
import ec.cacaotrace.ui.ColoresEstado
import ec.cacaotrace.ui.comun.Aviso
import ec.cacaotrace.ui.comun.BarraSuperior
import ec.cacaotrace.ui.comun.BotonGrande
import ec.cacaotrace.ui.comun.CodigoQr
import ec.cacaotrace.ui.comun.Formato
import ec.cacaotrace.ui.comun.TarjetaSeccion
import ec.cacaotrace.ui.comun.uriDe
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reporte del lote en PDF y exportación a CSV (RF-REP-01 a RF-REP-03).
 *
 * El PDF es el documento que el productor enseña a un comprador o a un
 * inspector: por eso lleva el código QR del lote, el resultado de la norma con
 * su número de referencia y la cadena completa hasta la finca. Se genera en el
 * teléfono, sin internet.
 */
@Composable
fun PantallaReporte(
    contenedor: ContenedorApp,
    navegacion: NavHostController,
    loteId: String,
) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val generador = remember { GeneradorPdf(contexto) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    var codigo by remember { mutableStateOf("") }
    var secciones by remember { mutableStateOf<List<SeccionPdf>>(emptyList()) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var trabajando by remember { mutableStateOf(false) }

    LaunchedEffect(loteId) {
        val completo = contenedor.lotes.completo(loteId) ?: return@LaunchedEffect
        codigo = completo.lote.codigo
        secciones = armarSecciones(contenedor, loteId, json)
    }

    fun compartir(archivo: File, tipo: String) {
        val intento = Intent(Intent.ACTION_SEND).apply {
            type = tipo
            putExtra(Intent.EXTRA_STREAM, uriDe(contexto, archivo))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        contexto.startActivity(Intent.createChooser(intento, "Compartir ${archivo.name}"))
    }

    Scaffold(
        topBar = { BarraSuperior(if (codigo.isBlank()) "Reporte" else "Reporte · $codigo", navegacion) },
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

            if (codigo.isNotBlank()) {
                TarjetaSeccion("Código del lote") {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CodigoQr(codigo, Modifier.size(180.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Este QR va impreso en el reporte y en los sacos.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ------------------------------------------ vista previa del PDF
            TarjetaSeccion("Lo que va en el reporte", icono = Icons.Default.PictureAsPdf) {
                if (secciones.isEmpty()) {
                    Text("Preparando el contenido…", fontSize = 15.sp)
                } else {
                    secciones.forEach { seccion ->
                        Text(
                            seccion.titulo,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                        Text(
                            "${seccion.lineas.size} línea(s)",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            BotonGrande(
                texto = "Generar el reporte en PDF",
                subtitulo = "Se crea en el teléfono, sin internet",
                icono = Icons.Default.PictureAsPdf,
                habilitado = secciones.isNotEmpty() && !trabajando,
            ) {
                trabajando = true
                alcance.launch {
                    val archivo = withContext(Dispatchers.IO) {
                        generador.reporte(
                            nombre = "reporte_$codigo.pdf",
                            titulo = "Reporte del lote $codigo",
                            secciones = secciones,
                        )
                    }
                    trabajando = false
                    mensaje = "Reporte generado: ${archivo.name}"
                    compartir(archivo, "application/pdf")
                }
            }

            Spacer(Modifier.height(8.dp))
            BotonGrande(
                texto = "Imprimir el tablero de 100 granos",
                subtitulo = "Plantilla con cuadrícula y tarjeta de color",
                icono = Icons.Default.GridOn,
                habilitado = !trabajando,
            ) {
                trabajando = true
                alcance.launch {
                    val archivo = withContext(Dispatchers.IO) { generador.plantillaTablero() }
                    trabajando = false
                    mensaje = "Plantilla generada: ${archivo.name}"
                    compartir(archivo, "application/pdf")
                }
            }

            Spacer(Modifier.height(8.dp))
            BotonGrande(
                texto = "Exportar la línea de tiempo a CSV",
                subtitulo = "Para abrirla en una hoja de cálculo",
                icono = Icons.Default.TableChart,
                habilitado = !trabajando,
            ) {
                trabajando = true
                alcance.launch {
                    val hechos = contenedor.lotes.lineaDeTiempo(loteId)
                    val archivo = withContext(Dispatchers.IO) {
                        generador.csv(
                            nombre = "linea_tiempo_$codigo.csv",
                            cabecera = listOf("fecha", "etapa", "titulo", "detalle", "es_alerta"),
                            filas = hechos.map {
                                listOf(
                                    Formato.fechaHora(it.fecha),
                                    it.etapa,
                                    it.titulo,
                                    it.detalle,
                                    if (it.esAlerta) "si" else "no",
                                )
                            },
                        )
                    }
                    trabajando = false
                    mensaje = "CSV generado: ${archivo.name}"
                    compartir(archivo, "text/csv")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Arma el contenido del reporte.
 *
 * Se deja fuera del composable porque es lógica de negocio pura: decide qué
 * se cuenta y en qué orden, y eso no tiene nada que ver con cómo se dibuja.
 */
private suspend fun armarSecciones(
    contenedor: ContenedorApp,
    loteId: String,
    json: Json,
): List<SeccionPdf> {
    val completo = contenedor.lotes.completo(loteId) ?: return emptyList()
    val lote = completo.lote
    val secciones = mutableListOf<SeccionPdf>()

    // ------------------------------------------------------- identificación
    secciones += SeccionPdf(
        titulo = "Identificación",
        lineas = buildList {
            add(LineaPdf.Dato("Código del lote", lote.codigo))
            add(LineaPdf.Dato("Variedad", lote.variedad))
            add(LineaPdf.Dato("Llegada", Formato.fecha(lote.fechaLlegada)))
            lote.fechaCosecha?.let { add(LineaPdf.Dato("Cosecha", Formato.fecha(it))) }
            completo.finca?.let { finca ->
                add(LineaPdf.Dato("Finca", finca.nombre))
                if (finca.canton.isNotBlank() || finca.provincia.isNotBlank()) {
                    add(
                        LineaPdf.Dato(
                            "Procedencia",
                            listOf(finca.canton, finca.provincia)
                                .filter { it.isNotBlank() }
                                .joinToString(", "),
                        ),
                    )
                }
            }
            add(LineaPdf.Dato("Estado", lote.estado.etiqueta))
            if (lote.ventaBloqueada) {
                add(LineaPdf.Parrafo("VENTA BLOQUEADA: ${lote.motivoBloqueo}"))
            }
        },
    )

    // ------------------------------------------------------------ proceso
    val proceso = buildList {
        completo.recepcion?.let { r ->
            add(LineaPdf.Dato("Mazorcas recibidas", r.mazorcasTotal.toString()))
            add(LineaPdf.Dato("Peso en recepción", Formato.kg(r.pesoKg)))
            add(LineaPdf.Dato("Mazorcas sanas", r.mazorcasSanas.toString()))
            add(LineaPdf.Dato("Con monilia", r.mazorcasMonilia.toString()))
            add(LineaPdf.Dato("Con fitóftora", r.mazorcasFitoftora.toString()))
        }
        completo.apertura?.let { a ->
            add(LineaPdf.Dato("Mazorcas abiertas", a.mazorcasAbiertas.toString()))
            add(LineaPdf.Dato("Baba obtenida", Formato.kg(a.kgBaba)))
        }
        completo.fermentacion?.let { f ->
            add(LineaPdf.Dato("Inicio de fermentación", Formato.fechaHora(f.inicio)))
            f.fin?.let { add(LineaPdf.Dato("Fin de fermentación", Formato.fechaHora(it))) }
            add(LineaPdf.Dato("Masa fermentada", Formato.kg(f.masaKg)))
        }
        completo.secado?.let { s ->
            add(LineaPdf.Dato("Método de secado", s.metodo.etiqueta))
            add(LineaPdf.Dato("Inicio de secado", Formato.fecha(s.inicio)))
            s.fin?.let { add(LineaPdf.Dato("Fin de secado", Formato.fecha(it))) }
            s.kgSeco?.let { add(LineaPdf.Dato("Grano seco", Formato.kg(it))) }
        }
    }
    if (proceso.isNotEmpty()) {
        secciones += SeccionPdf("Proceso", proceso)
    }

    // ------------------------------------------- prueba de corte (la clave)
    completo.pruebaCorteFinal?.let { prueba ->
        val porcentajes = runCatching {
            json.parseToJsonElement(prueba.porcentajesJson).jsonObject
                .mapValues { (_, valor) -> valor.jsonPrimitive.content }
        }.getOrDefault(emptyMap())

        secciones += SeccionPdf(
            titulo = "Prueba de corte (NTE INEN 176)",
            lineas = buildList {
                add(LineaPdf.Dato("Fecha", Formato.fecha(prueba.fecha)))
                add(LineaPdf.Dato("Granos evaluados", prueba.granos.toString()))
                add(LineaPdf.Dato("Resultado", prueba.resultado))
                add(
                    LineaPdf.Dato(
                        "Conforme con la norma",
                        if (prueba.conforme) "Sí" else "No",
                    ),
                )
                prueba.peso100g?.let {
                    add(LineaPdf.Dato("Peso de 100 granos", "${Formato.numero(it, 1)} g"))
                }
                porcentajes.forEach { (clase, valor) ->
                    val numero = valor.toDoubleOrNull() ?: return@forEach
                    add(
                        LineaPdf.Dato(
                            Formato.nombreBonito(clase),
                            "${Formato.numero(numero, 1)} %",
                        ),
                    )
                }
                add(
                    LineaPdf.Parrafo(
                        "Conteo hecho " + if (prueba.modeloVersion.isBlank()) {
                            "a mano."
                        } else {
                            "con ayuda del modelo ${prueba.modeloVersion} y revisado por " +
                                "el usuario."
                        } + " Perfil de la norma: ${prueba.perfilNorma}.",
                    ),
                )
            },
        )
    }

    // ------------------------------------------------------ balance de masa
    val balance = contenedor.lotes.balance(loteId)
    if (balance.isNotEmpty()) {
        secciones += SeccionPdf(
            titulo = "Balance de masa",
            lineas = balance.map { paso ->
                LineaPdf.Dato(
                    paso.etapa,
                    buildString {
                        append("${Formato.numero(paso.entrada, 1)} ${paso.unidadEntrada}")
                        append(" → ")
                        val real = paso.salidaReal
                        if (real == null) {
                            append("esperado ${Formato.numero(paso.salidaEsperada, 1)} ")
                            append(paso.unidadSalida)
                        } else {
                            append("${Formato.numero(real, 1)} ${paso.unidadSalida}")
                            paso.desvioPct?.let {
                                append(" (${if (it >= 0) "+" else ""}${Formato.numero(it, 1)} %)")
                            }
                        }
                    },
                )
            },
        )
    }

    // --------------------------------------------------------- laboratorio
    val analisis = contenedor.apoyo.laboratorioDeLote(loteId)
    if (analisis.isNotEmpty()) {
        secciones += SeccionPdf(
            titulo = "Resultados de laboratorio",
            lineas = analisis.map { a ->
                LineaPdf.Dato(
                    "${Formato.nombreBonito(a.analisis)} · ${Formato.fecha(a.fecha)}",
                    "${Formato.numero(a.valor, 2)} ${a.unidad}",
                )
            },
        )
    }

    // --------------------------------------------------- alertas atendidas
    val alertas = contenedor.alertas.deLote(loteId)
    if (alertas.isNotEmpty()) {
        secciones += SeccionPdf(
            titulo = "Incidencias registradas",
            lineas = alertas.map { a ->
                LineaPdf.Parrafo("[${a.regla}] ${a.quePaso} — ${a.estado}")
            },
        )
    }

    // ------------------------------------------------------------ nota final
    secciones += SeccionPdf(
        titulo = "Sobre este documento",
        lineas = listOf(
            LineaPdf.Parrafo(
                "Generado por CacaoTrace a partir de los registros del propio " +
                    "productor. La calificación sigue la tabla de la norma " +
                    "NTE INEN 176 con el perfil " +
                    (completo.pruebaCorteFinal?.perfilNorma
                        ?: CalificadorCorte.PERFIL_POR_DEFECTO) +
                    ". Los porcentajes se guardaron el día de la prueba, así que " +
                    "siguen siendo los que se vieron aunque la tabla se edite después.",
            ),
            LineaPdf.Dato("Emitido", Formato.fechaHora(java.time.Instant.now())),
        ),
    )

    return secciones
}
