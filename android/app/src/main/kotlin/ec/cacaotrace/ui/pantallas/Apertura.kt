package ec.cacaotrace.ui.pantallas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ec.cacaotrace.ContenedorApp
import ec.cacaotrace.ui.ColoresEstado
import ec.cacaotrace.ui.comun.Aviso
import ec.cacaotrace.ui.comun.BarraSuperior
import ec.cacaotrace.ui.comun.BotonGrande
import ec.cacaotrace.ui.comun.CampoNumero
import ec.cacaotrace.ui.comun.FilaDato
import ec.cacaotrace.ui.comun.Formato
import ec.cacaotrace.ui.comun.TarjetaSeccion
import ec.cacaotrace.ui.comun.leerNumero
import kotlin.math.abs
import kotlinx.coroutines.launch

/** Apertura de mazorcas (RF-APE-01 a RF-APE-03). */
@Composable
fun PantallaApertura(
    contenedor: ContenedorApp,
    navegacion: NavHostController,
    loteId: String,
) {
    val alcance = rememberCoroutineScope()
    var mazorcas by rememberSaveable { mutableStateOf("") }
    var baba by rememberSaveable { mutableStateOf("") }
    var cascara by rememberSaveable { mutableStateOf("0") }
    var notas by rememberSaveable { mutableStateOf("") }
    var cascaraRecomendada by remember { mutableStateOf(0.0) }
    var aviso by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(loteId) {
        val c = contenedor.lotes.completo(loteId)
        c?.apertura?.let { a ->
            mazorcas = a.mazorcasAbiertas.toString()
            baba = a.kgBaba.toString()
            cascara = a.kgCascaraAnadida.toString()
            notas = a.notas
        } ?: c?.recepcion?.let { r ->
            mazorcas = (r.mazorcasTotal - r.mazorcasDescartadas).toString()
        }
    }

    LaunchedEffect(baba) {
        cascaraRecomendada = contenedor.lotes.cascaraRecomendada(leerNumero(baba) ?: 0.0)
    }

    val nMazorcas = leerNumero(mazorcas) ?: 0.0
    val kgBaba = leerNumero(baba) ?: 0.0
    val porMazorca = if (nMazorcas > 0) kgBaba / nMazorcas else null

    Scaffold(topBar = { BarraSuperior("Apertura de mazorcas", navegacion) }) { relleno ->
        LazyColumn(
            Modifier.fillMaxSize().padding(relleno),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                aviso?.let { Aviso(texto = it, color = ColoresEstado.problema) }

                TarjetaSeccion("Lo que se abrió", icono = Icons.Default.Egg) {
                    Column {
                        CampoNumero(
                            "Mazorcas abiertas", mazorcas, { mazorcas = it },
                            decimales = false,
                        )
                        CampoNumero(
                            "Baba obtenida", baba, { baba = it }, unidad = "kg",
                            ayuda = "El grano fresco con pulpa, recién sacado",
                        )
                        porMazorca?.let { r ->
                            FilaDato(
                                "Rendimiento por mazorca",
                                "${Formato.numero(r, 3)} kg",
                                color = if (abs(r - 0.17) / 0.17 > 0.2) {
                                    ColoresEstado.atencion
                                } else {
                                    ColoresEstado.bien
                                },
                            )
                            Text(
                                "Lo normal en CCN-51 son unos 0.170 kg por mazorca.",
                                fontSize = 14.sp,
                            )
                        }
                    }
                }

                TarjetaSeccion("Cáscara añadida", icono = Icons.Default.AddCircleOutline) {
                    Column {
                        Text(
                            "Si hay poca baba, el montón no conserva el calor y la " +
                                "fermentación se enfría. Añadir cáscara de mazorca " +
                                "troceada aumenta la masa y el azúcar disponible.",
                            fontSize = 15.sp,
                        )
                        if (cascaraRecomendada > 0) {
                            Aviso(
                                titulo = "Te recomiendo añadir cáscara",
                                texto = "Con ${Formato.kg(kgBaba)} de baba te faltan unos " +
                                    "${Formato.kg(cascaraRecomendada)} para llegar a la " +
                                    "masa mínima.",
                                accion = {
                                    OutlinedButton(
                                        onClick = {
                                            cascara = Formato.numero(cascaraRecomendada)
                                        },
                                    ) { Text("Usar esa cantidad") }
                                },
                            )
                        }
                        CampoNumero(
                            "Cáscara añadida", cascara, { cascara = it }, unidad = "kg",
                        )
                        FilaDato(
                            "Masa total al fermentador",
                            Formato.kg(kgBaba + (leerNumero(cascara) ?: 0.0)),
                        )
                    }
                }

                TarjetaSeccion("Notas", icono = Icons.Default.EditNote) {
                    OutlinedTextField(
                        value = notas,
                        onValueChange = { notas = it },
                        placeholder = { Text("Lo que quieras recordar de esta apertura") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(16.dp))
                BotonGrande("Guardar la apertura", Icons.Default.Save) {
                    if (nMazorcas <= 0 || kgBaba <= 0) {
                        aviso = "Escribe las mazorcas abiertas y los kg de baba"
                        return@BotonGrande
                    }
                    alcance.launch {
                        contenedor.lotes.guardarApertura(
                            loteId = loteId,
                            mazorcasAbiertas = nMazorcas.toInt(),
                            kgBaba = kgBaba,
                            kgCascaraAnadida = leerNumero(cascara) ?: 0.0,
                            notas = notas.trim(),
                        )
                        navegacion.popBackStack()
                    }
                }
            }
        }
    }
}
