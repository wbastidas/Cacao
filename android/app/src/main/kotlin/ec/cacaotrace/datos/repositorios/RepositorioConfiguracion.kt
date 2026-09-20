package ec.cacaotrace.datos.repositorios

import android.content.Context
import ec.cacaotrace.datos.bd.BaseDatos
import ec.cacaotrace.datos.bd.Comunes
import ec.cacaotrace.datos.bd.entidades.AjusteEntidad
import ec.cacaotrace.datos.bd.entidades.AuditoriaEntidad
import ec.cacaotrace.datos.bd.entidades.EquipoEntidad
import ec.cacaotrace.datos.bd.entidades.FincaEntidad
import ec.cacaotrace.datos.bd.entidades.ModeloIaEntidad
import ec.cacaotrace.datos.bd.entidades.UmbralGuardadoEntidad
import ec.cacaotrace.datos.sync.ServicioSincronizacion
import ec.cacaotrace.nucleo.norma.TablaNorma
import ec.cacaotrace.nucleo.reglas.Umbrales
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Configuración: umbrales, tabla de la norma, fincas, equipos y modelos.
 *
 * Aquí se cumple el RNF-12: nada de esto está cableado en el código, todo es
 * dato editable que se puede cambiar sin publicar una versión nueva.
 */
class RepositorioConfiguracion(
    private val bd: BaseDatos,
    private val sync: ServicioSincronizacion,
    private val contexto: Context,
) {
    private val dao get() = bd.configuracion()

    @Volatile
    private var normaEnCache: TablaNorma? = null

    // ----------------------------------------------------------- umbrales

    /** Los umbrales vigentes: los de fábrica con encima los que el usuario cambió. */
    suspend fun umbrales(): Umbrales =
        Umbrales(dao.umbrales().associate { it.clave to it.valor })

    /**
     * Guarda un umbral cambiado por el usuario (RF-CFG-03).
     *
     * Solo se guarda lo que difiere de fábrica: así, si un valor por defecto
     * mejora en una versión futura, lo hereda quien no lo haya tocado.
     */
    suspend fun guardarUmbral(clave: String, valor: Double) {
        // Valida el rango antes de escribir: mejor un error claro aquí que una
        // alerta absurda tres días después.
        Umbrales.porDefecto().con(clave, valor)

        val existente = dao.umbral(clave)
        val entidad = existente?.copy(valor = valor, comunes = existente.comunes.tocada())
            ?: UmbralGuardadoEntidad(clave = clave, valor = valor)
        dao.guardarUmbral(entidad)
        sync.encolar("umbrales_guardados", entidad.id, "actualizar")
    }

    /** Devuelve un umbral a su valor de fábrica. */
    suspend fun restaurarUmbral(clave: String) = dao.borrarUmbral(clave)

    // ----------------------------------------------------------- norma

    /**
     * La tabla de la norma vigente.
     *
     * Primero la copia que editó el usuario; si no hay ninguna, la que viene
     * dentro de la app. Nunca falla por falta de internet: usa siempre la
     * última copia válida (§3.1 de la ERS).
     */
    suspend fun norma(): TablaNorma {
        normaEnCache?.let { return it }

        val editada = leerAjuste(CLAVE_NORMA)
        if (!editada.isNullOrBlank()) {
            runCatching { TablaNorma.desdeJsonTexto(editada) }
                .onSuccess { normaEnCache = it; return it }
            // Una tabla editada mal no puede dejar la app sin calificar: se cae
            // a la de fábrica y se avisa desde Ajustes.
        }

        val texto = contexto.assets.open(RUTA_NORMA).bufferedReader().use { it.readText() }
        return TablaNorma.desdeJsonTexto(texto).also { normaEnCache = it }
    }

    /** Guarda una tabla de norma editada. Valida antes de aceptarla. */
    suspend fun guardarNorma(tabla: TablaNorma) {
        val texto = tabla.aJsonTexto()
        TablaNorma.desdeJsonTexto(texto) // si no es válida, revienta aquí
        guardarAjuste(CLAVE_NORMA, texto)
        normaEnCache = tabla
    }

    /** Vuelve a la tabla de la norma que viene con la app. */
    suspend fun restaurarNorma() {
        guardarAjuste(CLAVE_NORMA, "")
        normaEnCache = null
    }

    // ----------------------------------------------------------- fincas

    fun observarFincas(): Flow<List<FincaEntidad>> = bd.fincas().observarTodas()

    suspend fun fincaPorId(id: String): FincaEntidad? = bd.fincas().porId(id)

    suspend fun guardarFinca(finca: FincaEntidad) {
        bd.fincas().guardar(finca)
        sync.encolar("fincas", finca.id, "actualizar")
    }

    suspend fun eliminarFinca(id: String) {
        bd.fincas().eliminarLogico(id)
        sync.encolar("fincas", id, "eliminar")
    }

    // ----------------------------------------------------------- equipos

    fun observarEquipos(tipo: String? = null): Flow<List<EquipoEntidad>> =
        if (tipo == null) bd.equipos().observarTodos() else bd.equipos().observarPorTipo(tipo)

    suspend fun guardarEquipo(equipo: EquipoEntidad) {
        bd.equipos().guardar(equipo)
        sync.encolar("equipos", equipo.id, "actualizar")
    }

    suspend fun eliminarEquipo(id: String) {
        bd.equipos().eliminarLogico(id)
        sync.encolar("equipos", id, "eliminar")
    }

    // ----------------------------------------------------------- modelos IA

    suspend fun modeloActivo(nombre: String): ModeloIaEntidad? = dao.modeloActivo(nombre)

    fun observarModelos(): Flow<List<ModeloIaEntidad>> = dao.observarModelos()

    /**
     * Registra un modelo y lo deja activo, desactivando el anterior.
     *
     * El anterior no se borra: permite volver atrás si el nuevo resulta peor
     * en el uso real (RF-IA-06).
     */
    suspend fun activarModelo(modelo: ModeloIaEntidad) {
        dao.desactivarModelos(modelo.nombre)
        dao.guardarModelo(modelo.copy(activo = true))
    }

    // ----------------------------------------------------------- ajustes

    suspend fun leerAjuste(clave: String): String? = dao.ajuste(clave)?.valor

    suspend fun guardarAjuste(clave: String, valor: String) {
        val existente = dao.ajuste(clave)
        val entidad = existente?.copy(valor = valor, comunes = existente.comunes.tocada())
            ?: AjusteEntidad(clave = clave, valor = valor)
        dao.guardarAjuste(entidad)
        sync.encolar("configuracion", entidad.id, "actualizar")
    }

    /** RF-SYN-04: activada por defecto, y así se queda mientras no la cambien. */
    suspend fun subirFotosSoloConWifi(): Boolean = leerAjuste(CLAVE_SOLO_WIFI) != "no"

    suspend fun cambiarSubirFotosSoloConWifi(soloWifi: Boolean) {
        guardarAjuste(CLAVE_SOLO_WIFI, if (soloWifi) "si" else "no")
        sync.soloWifiParaFotos = soloWifi
    }

    /**
     * Identificador estable de este teléfono, para saber de dónde vino un
     * cambio cuando haya varios dispositivos sincronizando (RNF-11).
     */
    suspend fun dispositivoId(): String {
        leerAjuste(CLAVE_DISPOSITIVO)?.takeIf { it.isNotBlank() }?.let { return it }
        val nuevo = java.util.UUID.randomUUID().toString().take(8)
        guardarAjuste(CLAVE_DISPOSITIVO, nuevo)
        return nuevo
    }

    // ----------------------------------------------------------- auditoría

    /**
     * Deja constancia de un cambio en un registro sensible (RNF-11).
     *
     * Se usa en BPM, prueba de corte y laboratorio: son los registros que un
     * inspector puede pedir y donde importa poder decir quién cambió qué.
     */
    suspend fun auditar(
        tabla: String,
        registroId: String,
        campo: String,
        valorAnterior: String,
        valorNuevo: String,
        motivo: String = "",
    ) {
        if (valorAnterior == valorNuevo) return
        dao.auditar(
            AuditoriaEntidad(
                comunes = Comunes(usuarioId = "local"),
                tabla = tabla,
                registroId = registroId,
                campo = campo,
                valorAnterior = valorAnterior,
                valorNuevo = valorNuevo,
                fecha = Instant.now(),
                motivo = motivo,
            ),
        )
    }

    companion object {
        const val CLAVE_NORMA = "norma_json"
        const val CLAVE_SOLO_WIFI = "subir_fotos_solo_wifi"
        const val CLAVE_DISPOSITIVO = "dispositivo_id"
        const val CLAVE_UNIDAD_PESO = "unidad_peso"
        const val RUTA_NORMA = "norma/norma_inen176.json"
    }
}
