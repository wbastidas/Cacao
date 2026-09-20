package ec.cacaotrace.datos.sync

import ec.cacaotrace.datos.bd.BaseDatos
import ec.cacaotrace.datos.bd.entidades.OperacionSyncEntidad
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Servicio que vacía la cola de salida hacia la nube (RF-SYN-01 a RF-SYN-07).
 *
 * Reglas que implementa:
 *  - primero los registros, después los archivos pesados (RF-SYN-03);
 *  - reintentos con retroceso exponencial, para no gastar batería ni datos
 *    insistiendo contra un servidor caído;
 *  - las fotos solo con WiFi si el usuario dejó activa esa opción (RF-SYN-04);
 *  - una falla permanente no bloquea la cola: se aparta y se sigue.
 */
class ServicioSincronizacion(
    private val bd: BaseDatos,
    val remoto: SincronizadorRemoto,
    private val hayConexion: suspend () -> Boolean,
    private val hayWifi: suspend () -> Boolean,
) {
    /** RF-SYN-04: activada por defecto. */
    var soloWifiParaFotos: Boolean = true

    /**
     * Un mutex y no una bandera booleana.
     *
     * Con una bandera, dos llamadas casi simultáneas pueden pasar las dos el
     * `if` antes de que ninguna la levante, y acabar subiendo el mismo
     * registro dos veces a la nube. El mutex no tiene esa ventana.
     */
    private val enCurso = Mutex()

    private val _estado = MutableStateFlow(EstadoSincronizacion())
    val estado: StateFlow<EstadoSincronizacion> = _estado.asStateFlow()

    /** Encola una operación. Lo llaman los repositorios en cada escritura. */
    suspend fun encolar(
        tabla: String,
        registroId: String,
        operacion: String,
        cargaJson: String = "{}",
        esArchivo: Boolean = false,
    ) {
        bd.colaSync().encolar(
            OperacionSyncEntidad(
                tabla = tabla,
                registroId = registroId,
                operacion = operacion,
                cargaJson = cargaJson,
                proximoIntento = Instant.now(),
                esArchivo = esArchivo,
            ),
        )
        refrescarEstado()
    }

    suspend fun pendientes(): Int = bd.colaSync().pendientes()

    suspend fun refrescarEstado() {
        _estado.value = _estado.value.copy(
            hayConexion = hayConexion(),
            pendientes = pendientes(),
        )
    }

    /**
     * Vacía la cola. Devuelve cuántas operaciones se subieron.
     *
     * Si ya hay una sincronización en curso, no arranca otra.
     */
    suspend fun sincronizar(): Int {
        if (enCurso.isLocked) return 0

        return enCurso.withLock {
            if (!hayConexion() || !remoto.estaDisponible()) return@withLock 0

            _estado.value = _estado.value.copy(sincronizando = true, ultimoError = "")
            var subidas = 0

            try {
                val conWifi = hayWifi()
                val operaciones = bd.colaSync().listasParaEnviar(Instant.now())

                for (op in operaciones) {
                    // Una foto pesada espera al WiFi, pero no bloquea el resto.
                    if (op.esArchivo && soloWifiParaFotos && !conWifi) continue

                    val resultado = if (op.esArchivo) {
                        remoto.subirArchivo(op, soloWifiParaFotos)
                    } else {
                        remoto.enviarRegistro(op)
                    }

                    when (resultado) {
                        ResultadoSync.EXITO -> {
                            bd.colaSync().borrar(op)
                            marcarSincronizado(op)
                            subidas++
                        }

                        ResultadoSync.REINTENTAR ->
                            programarReintento(op, "Sin respuesta; se reintentará")

                        // No se borra: queda para que el usuario la vea en
                        // Ajustes y decida. Borrarla en silencio sería perder
                        // un dato suyo.
                        ResultadoSync.FALLA_PERMANENTE ->
                            marcarError(op, "No se pudo subir")
                    }
                }
                _estado.value = _estado.value.copy(ultimaSincronizacion = Instant.now())
            } catch (e: Exception) {
                _estado.value = _estado.value.copy(ultimoError = e.message.orEmpty())
            } finally {
                _estado.value = _estado.value.copy(sincronizando = false)
                refrescarEstado()
            }
            subidas
        }
    }

    private suspend fun programarReintento(op: OperacionSyncEntidad, error: String) {
        val intentos = op.intentos + 1
        if (intentos >= INTENTOS_MAXIMOS) {
            marcarError(op, "Se agotaron los reintentos")
            return
        }
        val espera = ESPERAS_REINTENTO[intentos.coerceAtMost(ESPERAS_REINTENTO.lastIndex)]
        bd.colaSync().actualizar(
            op.copy(
                intentos = intentos,
                proximoIntento = Instant.now().plus(espera),
                ultimoError = error,
            ),
        )
    }

    private suspend fun marcarError(op: OperacionSyncEntidad, error: String) {
        bd.colaSync().actualizar(
            op.copy(intentos = INTENTOS_MAXIMOS, proximoIntento = null, ultimoError = error),
        )
    }

    /**
     * Marca el registro original como sincronizado.
     *
     * Se hace por SQL genérico porque la cola guarda el nombre de la tabla como
     * texto: un `when` con 35 ramas sería el mismo código repetido. A
     * diferencia de un `customStatement` suelto, Room sí invalida sus
     * observadores con `execSQL` sobre una tabla conocida.
     */
    private fun marcarSincronizado(op: OperacionSyncEntidad) {
        if (op.operacion == "subir_foto" || op.tabla.isBlank()) return
        if (op.tabla !in TABLAS_CONOCIDAS) return
        try {
            bd.openHelper.writableDatabase.execSQL(
                "UPDATE ${op.tabla} SET estadoSync = 'SINCRONIZADO' WHERE id = ?",
                arrayOf(op.registroId),
            )
            bd.invalidationTracker.refreshVersionsAsync()
        } catch (_: Exception) {
            // Si la tabla ya no existe (migración), no vale la pena fallar.
        }
    }

    /** Operaciones que quedaron con error y el usuario puede reintentar a mano. */
    suspend fun conError(): List<OperacionSyncEntidad> =
        bd.colaSync().conError(INTENTOS_MAXIMOS)

    /** Pone a cero los contadores para volver a intentarlo todo ahora. */
    suspend fun reintentarTodo(): Int {
        bd.colaSync().reintentarTodo(Instant.now())
        return sincronizar()
    }

    companion object {
        /**
         * Cuánto esperar antes de reintentar, según cuántas veces ya falló.
         *
         * 1 min, 5 min, 15 min, 1 h, 6 h, y de ahí no pasa. Un teléfono en la
         * finca puede estar días sin señal: insistir cada minuto solo gastaría
         * batería.
         */
        val ESPERAS_REINTENTO = listOf(
            Duration.ofMinutes(1),
            Duration.ofMinutes(5),
            Duration.ofMinutes(15),
            Duration.ofHours(1),
            Duration.ofHours(6),
        )

        /**
         * Tras estos intentos la operación deja de reintentarse sola; el
         * usuario la puede reintentar a mano desde Ajustes.
         */
        const val INTENTOS_MAXIMOS = 12

        /**
         * Lista blanca de tablas. La cola guarda el nombre como texto, así que
         * interpolarlo en SQL sin comprobarlo sería una inyección esperando a
         * ocurrir, aunque hoy los nombres los escriba solo la app.
         */
        val TABLAS_CONOCIDAS = setOf(
            "fincas", "equipos", "umbrales_guardados", "configuracion", "modelos_ia",
            "lotes", "recepciones", "aperturas", "fermentaciones",
            "lecturas_fermentacion", "volteos", "secados", "lecturas_secado",
            "pruebas_corte", "sacos", "inspecciones_almacen",
            "lotes_produccion", "lotes_produccion_origen", "tostados",
            "descascarillados", "refinados", "atemperados", "empaques",
            "movimientos_inventario", "stocks_minimos", "costos", "ventas",
            "checklists_bpm", "registros_bpm", "laboratorios", "alertas",
            "correcciones_aplicadas", "fotos", "auditoria",
        )
    }
}
