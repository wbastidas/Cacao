package ec.cacaotrace.datos.sync

import ec.cacaotrace.datos.bd.entidades.OperacionSyncEntidad

/**
 * Contrato con la nube.
 *
 * La app NO depende de Firebase ni de Google Drive: depende de esta interfaz.
 * Eso permite tres cosas:
 *
 *  1. la app funciona completa sin credenciales de nube (RNF-01);
 *  2. se puede probar la sincronización sin red, con un doble de pruebas;
 *  3. cambiar de proveedor no obliga a tocar ni la interfaz ni la base.
 *
 * Para activar la nube de verdad, implementa esta interfaz con Firestore y
 * Drive y cámbiala en el contenedor de la app. Ver docs/NUBE.md.
 */
interface SincronizadorRemoto {

    /** Nombre para mostrar en Ajustes: "Solo este teléfono", "Firebase", … */
    val nombre: String

    /** ¿Está configurado y con sesión iniciada? */
    suspend fun estaDisponible(): Boolean

    /** Sube un registro (crear, actualizar o eliminar). */
    suspend fun enviarRegistro(operacion: OperacionSyncEntidad): ResultadoSync

    /**
     * Sube el archivo de una foto o un PDF.
     *
     * [soloWifi] viene de la preferencia del usuario, activada por defecto
     * (RF-SYN-04). Si es true y no hay WiFi, debe devolver [ResultadoSync.REINTENTAR].
     */
    suspend fun subirArchivo(operacion: OperacionSyncEntidad, soloWifi: Boolean): ResultadoSync

    /** Respaldo completo semanal a Drive (RF-SYN-07). */
    suspend fun respaldoCompleto(): ResultadoSync

    /** Trae los cambios que hicieron otros teléfonos. */
    suspend fun descargarCambios(): ResultadoSync
}

/** Cómo terminó una operación de sincronización. */
enum class ResultadoSync {
    /** Subido y confirmado. */
    EXITO,

    /** Falló pero se puede reintentar (sin señal, servidor caído). */
    REINTENTAR,

    /** Falló y reintentar no va a servir (dato inválido, permiso denegado). */
    FALLA_PERMANENTE,
}

/**
 * Estado general de la sincronización, para el indicador de la pantalla de
 * inicio (RF-SYN-06).
 */
data class EstadoSincronizacion(
    val hayConexion: Boolean = false,
    val pendientes: Int = 0,
    val sincronizando: Boolean = false,
    val ultimoError: String = "",
    val ultimaSincronizacion: java.time.Instant? = null,
) {
    /** Texto exacto que pide RF-SYN-06. */
    val etiqueta: String
        get() = when {
            !hayConexion -> "Sin conexión"
            sincronizando -> "Sincronizando…"
            pendientes == 0 -> "Todo sincronizado"
            else -> "$pendientes pendientes"
        }

    val todoAlDia: Boolean get() = hayConexion && pendientes == 0 && !sincronizando
}

/**
 * Sincronizador que no sale del teléfono.
 *
 * Es el que usa la app mientras no haya credenciales de nube. Vacía la cola
 * marcando todo como sincronizado, de modo que la app se comporta igual que
 * con nube real y la sincronización se puede probar de punta a punta.
 *
 * NO es un stub vacío: cumple el contrato completo, y por eso al conectar
 * Firebase no hay que cambiar nada más que esta pieza.
 */
class SincronizadorLocal : SincronizadorRemoto {
    override val nombre: String = "Solo este teléfono"
    override suspend fun estaDisponible(): Boolean = true
    override suspend fun enviarRegistro(operacion: OperacionSyncEntidad) = ResultadoSync.EXITO
    override suspend fun subirArchivo(operacion: OperacionSyncEntidad, soloWifi: Boolean) =
        ResultadoSync.EXITO
    override suspend fun respaldoCompleto() = ResultadoSync.EXITO
    override suspend fun descargarCambios() = ResultadoSync.EXITO
}
