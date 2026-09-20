package ec.cacaotrace.datos.bd

import androidx.room.Embedded
import androidx.room.TypeConverter
import ec.cacaotrace.nucleo.modelo.EstadoLote
import ec.cacaotrace.nucleo.modelo.EstadoSync
import ec.cacaotrace.nucleo.modelo.MetodoAtemperado
import ec.cacaotrace.nucleo.modelo.MetodoSecado
import ec.cacaotrace.nucleo.modelo.OlorFermentacion
import java.time.Instant
import java.util.UUID

/**
 * Columnas que lleva TODA tabla de la app (§6 de la ERS).
 *
 * Van en una clase `@Embedded` en vez de repetirse en cada entidad: son siete
 * columnas por 35 tablas, y repetirlas sería la forma más rápida de que una
 * tabla se quedara sin `estadoSync` y sus registros no se subieran nunca.
 *
 * Están pensadas para que la sincronización funcione sin un servidor que
 * asigne identificadores y sin perder información al borrar:
 *
 *  - [id]            UUID generado en el teléfono; dos teléfonos sin conexión
 *                    nunca chocan.
 *  - [modificadoEn]  base de la resolución de conflictos "gana el último".
 *  - [usuarioId] y [dispositivoId]  auditoría (RNF-11) y origen del cambio.
 *  - [estadoSync]    en qué punto va la subida a la nube.
 *  - [eliminado]     borrado LÓGICO: la fila se queda para poder sincronizar
 *                    el borrado a los demás teléfonos y para la auditoría.
 */
data class Comunes(
    val id: String = UUID.randomUUID().toString(),
    val creadoEn: Instant = Instant.now(),
    val modificadoEn: Instant = Instant.now(),
    val usuarioId: String = "local",
    val dispositivoId: String = "",
    val estadoSync: EstadoSync = EstadoSync.PENDIENTE,
    val eliminado: Boolean = false,
) {
    /** Marca la fila como cambiada y pendiente de subir. */
    fun tocada(): Comunes =
        copy(modificadoEn = Instant.now(), estadoSync = EstadoSync.PENDIENTE)
}

/** Atajo para las entidades, que siempre exponen sus columnas comunes. */
interface ConComunes {
    val comunes: Comunes
    val id: String get() = comunes.id
}

/**
 * Conversores de tipos para Room.
 *
 * Los enums se guardan por NOMBRE y no por posición. Guardar el ordinal es
 * más compacto pero convierte cualquier reordenación del enum en una
 * corrupción silenciosa de los datos ya guardados.
 */
object Convertidores {
    @TypeConverter
    fun instanteADesdeLong(valor: Instant?): Long? = valor?.toEpochMilli()

    @TypeConverter
    fun longAInstante(valor: Long?): Instant? = valor?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun estadoLoteATexto(v: EstadoLote?): String? = v?.name

    @TypeConverter
    fun textoAEstadoLote(v: String?): EstadoLote? = v?.let { EstadoLote.valueOf(it) }

    @TypeConverter
    fun estadoSyncATexto(v: EstadoSync?): String? = v?.name

    @TypeConverter
    fun textoAEstadoSync(v: String?): EstadoSync? = v?.let { EstadoSync.valueOf(it) }

    @TypeConverter
    fun olorATexto(v: OlorFermentacion?): String? = v?.name

    @TypeConverter
    fun textoAOlor(v: String?): OlorFermentacion? = v?.let { OlorFermentacion.valueOf(it) }

    @TypeConverter
    fun metodoSecadoATexto(v: MetodoSecado?): String? = v?.name

    @TypeConverter
    fun textoAMetodoSecado(v: String?): MetodoSecado? = v?.let { MetodoSecado.valueOf(it) }

    @TypeConverter
    fun metodoAtemperadoATexto(v: MetodoAtemperado?): String? = v?.name

    @TypeConverter
    fun textoAMetodoAtemperado(v: String?): MetodoAtemperado? =
        v?.let { MetodoAtemperado.valueOf(it) }
}
