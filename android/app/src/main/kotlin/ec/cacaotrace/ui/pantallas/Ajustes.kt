package ec.cacaotrace.ui.pantallas

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
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import ec.cacaotrace.datos.bd.entidades.EquipoEntidad
import ec.cacaotrace.datos.bd.entidades.FincaEntidad
import ec.cacaotrace.ia.ServicioModelos
import ec.cacaotrace.nucleo.reglas.CatalogoUmbrales
import ec.cacaotrace.nucleo.reglas.Umbral
import ec.cacaotrace.ui.ColoresEstado
import ec.cacaotrace.ui.Rutas
import ec.cacaotrace.ui.comun.Aviso
import ec.cacaotrace.ui.comun.BarraSuperior
import ec.cacaotrace.ui.comun.BotonGrande
import ec.cacaotrace.ui.comun.CampoNumero
import ec.cacaotrace.ui.comun.EstadoUbicacion
import ec.cacaotrace.ui.comun.FilaDato
import ec.cacaotrace.ui.comun.Formato
import ec.cacaotrace.ui.comun.TarjetaSeccion
import ec.cacaotrace.ui.comun.leerNumero
import kotlinx.coroutines.launch

/**
 * Ajustes (RF-CFG-01 a RF-CFG-04).
 *
 * Aquí se cumple el RNF-12: ningún umbral está cableado en el código. El
 * usuario puede subir la temperatura a la que la app avisa, cambiar los días
 * de reposo o desactivar un aviso que en su taller no aplica, y todo eso sin
 * esperar una versión nueva de la app.
 *
 * Cada umbral se muestra con su explicación en español y su rango válido: un
 * número suelto en una pantalla de ajustes no se toca nunca porque nadie sabe
 * qué hace.
 */
@Composable
fun PantallaAjustes(contenedor: ContenedorApp, navegacion: NavHostController) {
    val alcance = rememberCoroutineScope()
    val estadoSync by contenedor.sync.estado.collectAsState()
    val fincas by contenedor.config.observarFincas().collectAsState(initial = emptyList())
    val equipos by contenedor.config.observarEquipos().collectAsState(initial = emptyList())
    val modelos by contenedor.config.observarModelos().collectAsState(initial = emptyList())

    var valores by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var soloWifi by remember { mutableStateOf(true) }
    var dispositivo by remember { mutableStateOf("") }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var editandoUmbral by remember { mutableStateOf<Umbral?>(null) }
    var nuevaFinca by remember { mutableStateOf(false) }
    var nuevoEquipo by remember { mutableStateOf(false) }
    var recargar by remember { mutableStateOf(0) }

    LaunchedEffect(recargar) {
        valores = contenedor.config.umbrales().valores
        soloWifi = contenedor.config.subirFotosSoloConWifi()
        dispositivo = contenedor.config.dispositivoId()
        contenedor.sync.refrescarEstado()
    }

    Scaffold(topBar = { BarraSuperior("Ajustes") }) { relleno ->
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

            // ------------------------------------------------ sincronización
            TarjetaSeccion("Copia en la nube", icono = Icons.Default.CloudSync) {
                FilaDato("Estado", estadoSync.etiqueta)
                FilaDato("Pendientes de subir", estadoSync.pendientes.toString())
                estadoSync.ultimaSincronizacion?.let {
                    FilaDato("Última vez", Formato.haceCuanto(it))
                }
                FilaDato("Este teléfono", dispositivo)

                if (estadoSync.ultimoError.isNotBlank()) {
                    Aviso(
                        titulo = "El último intento falló",
                        texto = estadoSync.ultimoError,
                        color = ColoresEstado.atencion,
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Subir fotos solo con WiFi", fontSize = 16.sp)
                        Text(
                            "Las fotos pesan. Con datos móviles se gasta el plan en un día.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = soloWifi,
                        onCheckedChange = { valor ->
                            soloWifi = valor
                            alcance.launch {
                                contenedor.config.cambiarSubirFotosSoloConWifi(valor)
                            }
                        },
                    )
                }

                Spacer(Modifier.height(8.dp))
                EstadoUbicacion(contenedor)

                Spacer(Modifier.height(8.dp))
                Aviso(
                    texto = "La app funciona entera sin internet. Todo se guarda en el " +
                        "teléfono y se sube cuando hay señal; nunca se pierde nada por " +
                        "estar sin cobertura.",
                    color = ColoresEstado.neutro,
                    icono = Icons.Default.Info,
                )

                Spacer(Modifier.height(8.dp))
                BotonGrande(
                    texto = "Intentar sincronizar ahora",
                    icono = Icons.Default.CloudSync,
                    habilitado = estadoSync.pendientes > 0 && !estadoSync.sincronizando,
                ) {
                    alcance.launch {
                        val subidos = contenedor.sync.sincronizar()
                        mensaje = if (subidos > 0) {
                            "Se subieron $subidos registro(s)."
                        } else {
                            "No se pudo subir nada. Se reintentará solo."
                        }
                        recargar++
                    }
                }
            }

            // ------------------------------------------------------ umbrales
            TarjetaSeccion("Cuándo avisa la app", icono = Icons.Default.Tune) {
                Text(
                    "Estos números deciden cuándo salta cada aviso. Están puestos para " +
                        "CCN-51 en la costa ecuatoriana; si en tu taller la realidad es " +
                        "otra, cámbialos.",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                CatalogoUmbrales.todos.forEach { umbral ->
                    val actual = valores[umbral.clave] ?: umbral.porDefecto
                    val cambiado = actual != umbral.porDefecto

                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(umbral.etiqueta, fontSize = 16.sp)
                            Text(
                                umbral.explicacion,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { editandoUmbral = umbral }) {
                            Text(
                                "${Formato.numero(actual, 2)} ${umbral.unidad}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (cambiado) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                        if (cambiado) {
                            IconButton(
                                onClick = {
                                    alcance.launch {
                                        contenedor.config.restaurarUmbral(umbral.clave)
                                        recargar++
                                        mensaje = "«${umbral.etiqueta}» volvió al valor de fábrica."
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Default.Restore,
                                    contentDescription = "Volver al valor de fábrica",
                                )
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------- fincas
            TarjetaSeccion(
                "Fincas",
                icono = Icons.Default.Agriculture,
                accion = {
                    IconButton(onClick = { nuevaFinca = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Añadir finca")
                    }
                },
            ) {
                if (fincas.isEmpty()) {
                    Text(
                        "Sin fincas registradas. Añade de dónde viene el cacao para que " +
                            "el reporte lo pueda decir.",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    fincas.forEach { finca ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(finca.nombre, fontSize = 16.sp)
                                Text(
                                    listOf(finca.canton, finca.provincia)
                                        .filter { it.isNotBlank() }
                                        .joinToString(", "),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = {
                                    alcance.launch {
                                        contenedor.config.eliminarFinca(finca.id)
                                        mensaje = "Finca «${finca.nombre}» eliminada."
                                    }
                                },
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------------- equipos
            TarjetaSeccion(
                "Equipos del taller",
                icono = Icons.Default.Rule,
                accion = {
                    IconButton(onClick = { nuevoEquipo = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Añadir equipo")
                    }
                },
            ) {
                Text(
                    "Las medidas del fermentador importan de verdad: uno demasiado " +
                        "grande para poca baba enfría la masa, y esa es la causa más " +
                        "común de grano pizarroso.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                equipos.forEach { equipo ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${equipo.nombre} · ${equipo.tipo}", fontSize = 16.sp)
                            Text(
                                listOfNotNull(
                                    equipo.capacidadKg?.let { "${Formato.numero(it, 0)} kg" },
                                    equipo.material.takeIf { it.isNotBlank() },
                                ).joinToString(" · "),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = {
                                alcance.launch {
                                    contenedor.config.eliminarEquipo(equipo.id)
                                    mensaje = "Equipo «${equipo.nombre}» eliminado."
                                }
                            },
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                        }
                    }
                }
            }

            // ---------------------------------------------------- modelos IA
            TarjetaSeccion("Modelos de reconocimiento", icono = Icons.Default.Memory) {
                ServicioModelos.TAREAS.forEach { (tarea, nombreVisible) ->
                    val disponible = contenedor.modelos.estaDisponible(tarea)
                    FilaDato(
                        etiqueta = nombreVisible.replaceFirstChar { it.uppercase() },
                        valor = if (disponible) {
                            contenedor.modelos.versionDe(tarea) ?: "instalado"
                        } else {
                            "sin modelo"
                        },
                        color = if (disponible) ColoresEstado.bien else ColoresEstado.neutro,
                    )
                    if (!disponible) {
                        contenedor.modelos.porQueNoEstaDisponible(tarea)?.message?.let {
                            Text(
                                it,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                            )
                        }
                    }
                }

                if (modelos.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Versiones instaladas", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    modelos.forEach { m ->
                        FilaDato(
                            "${m.nombre} ${m.version}",
                            if (m.activo) "en uso" else "guardada",
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Aviso(
                    texto = "La app funciona entera sin modelos: todo lo que hace la " +
                        "cámara se puede hacer a mano. Los modelos solo ahorran tiempo, " +
                        "y su respuesta siempre se puede corregir.",
                    color = ColoresEstado.neutro,
                    icono = Icons.Default.Info,
                )
            }

            // ------------------------------------------------------- ayuda
            TarjetaSeccion("Ayuda", icono = Icons.Default.Info) {
                TextButton(onClick = { navegacion.navigate(Rutas.GUIAS) }) {
                    Text("Guías del proceso")
                }
                TextButton(onClick = { navegacion.navigate(Rutas.CORRECCIONES) }) {
                    Text("Qué hacer cuando algo sale mal")
                }
                TextButton(onClick = { navegacion.navigate(Rutas.INVENTARIO) }) {
                    Text("Inventario de insumos")
                }
                TextButton(onClick = { navegacion.navigate(Rutas.BPM) }) {
                    Text("Buenas prácticas")
                }
                TextButton(onClick = { navegacion.navigate(Rutas.LABORATORIO) }) {
                    Text("Resultados de laboratorio")
                }
                TextButton(onClick = { navegacion.navigate(Rutas.almacen()) }) {
                    Text("Almacén")
                }
                TextButton(onClick = { navegacion.navigate(Rutas.COMPARAR) }) {
                    Text("Comparar dos lotes")
                }
                TextButton(onClick = { navegacion.navigate(Rutas.PRIVACIDAD) }) {
                    Text("Privacidad: qué se guarda y dónde")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    editandoUmbral?.let { umbral ->
        DialogoUmbral(
            umbral = umbral,
            actual = valores[umbral.clave] ?: umbral.porDefecto,
            alGuardar = { valor ->
                editandoUmbral = null
                alcance.launch {
                    runCatching { contenedor.config.guardarUmbral(umbral.clave, valor) }
                        .onSuccess {
                            recargar++
                            mensaje = "«${umbral.etiqueta}» ahora vale " +
                                "${Formato.numero(valor, 2)} ${umbral.unidad}."
                        }
                        .onFailure { mensaje = it.message ?: "Ese valor no es válido." }
                }
            },
            alCancelar = { editandoUmbral = null },
        )
    }

    if (nuevaFinca) {
        DialogoFinca(
            alGuardar = { finca ->
                nuevaFinca = false
                alcance.launch {
                    contenedor.config.guardarFinca(finca)
                    mensaje = "Finca «${finca.nombre}» guardada."
                }
            },
            alCancelar = { nuevaFinca = false },
        )
    }

    if (nuevoEquipo) {
        DialogoEquipo(
            alGuardar = { equipo ->
                nuevoEquipo = false
                alcance.launch {
                    contenedor.config.guardarEquipo(equipo)
                    mensaje = "Equipo «${equipo.nombre}» guardado."
                }
            },
            alCancelar = { nuevoEquipo = false },
        )
    }
}

/**
 * Edita un umbral.
 *
 * Muestra el rango válido y el valor de fábrica: son los dos datos que hacen
 * que alguien se atreva a tocar el número sin miedo a romper algo.
 */
@Composable
private fun DialogoUmbral(
    umbral: Umbral,
    actual: Double,
    alGuardar: (Double) -> Unit,
    alCancelar: () -> Unit,
) {
    var texto by remember { mutableStateOf(Formato.numero(actual, 2)) }
    val valor = leerNumero(texto)
    val valido = valor != null && umbral.esValido(valor)

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(umbral.etiqueta) },
        text = {
            Column {
                Text(umbral.explicacion, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                CampoNumero(
                    etiqueta = "Valor",
                    valor = texto,
                    alCambiar = { texto = it },
                    unidad = umbral.unidad,
                    error = if (valor != null && !valido) {
                        "Debe estar entre ${umbral.minimo?.let { Formato.numero(it, 2) } ?: "—"} " +
                            "y ${umbral.maximo?.let { Formato.numero(it, 2) } ?: "—"}."
                    } else {
                        null
                    },
                )
                Text(
                    "De fábrica: ${Formato.numero(umbral.porDefecto, 2)} ${umbral.unidad}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { valor?.let(alGuardar) },
                enabled = valido,
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoFinca(alGuardar: (FincaEntidad) -> Unit, alCancelar: () -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var canton by remember { mutableStateOf("") }
    var provincia by remember { mutableStateOf("") }
    var contacto by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Finca nueva") },
        text = {
            Column {
                CampoTexto("Nombre de la finca", nombre) { nombre = it }
                CampoTexto("Cantón", canton) { canton = it }
                CampoTexto("Provincia", provincia) { provincia = it }
                CampoTexto("Contacto", contacto) { contacto = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    alGuardar(
                        FincaEntidad(
                            nombre = nombre.trim(),
                            canton = canton.trim(),
                            provincia = provincia.trim(),
                            contacto = contacto.trim(),
                        ),
                    )
                },
                enabled = nombre.isNotBlank(),
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}

@Composable
private fun DialogoEquipo(alGuardar: (EquipoEntidad) -> Unit, alCancelar: () -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf("fermentador") }
    var capacidad by remember { mutableStateOf("") }
    var material by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Equipo nuevo") },
        text = {
            Column {
                CampoTexto("Nombre", nombre) { nombre = it }
                CampoTexto("Tipo (fermentador, marquesina, horno…)", tipo) { tipo = it }
                CampoNumero("Capacidad", capacidad, { capacidad = it }, unidad = "kg")
                CampoTexto("Material", material) { material = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    alGuardar(
                        EquipoEntidad(
                            nombre = nombre.trim(),
                            tipo = tipo.trim(),
                            capacidadKg = leerNumero(capacidad),
                            material = material.trim(),
                        ),
                    )
                },
                enabled = nombre.isNotBlank() && tipo.isNotBlank(),
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}

@Composable
private fun CampoTexto(etiqueta: String, valor: String, alCambiar: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
