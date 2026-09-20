package ec.cacaotrace.datos.bd.entidades

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ec.cacaotrace.datos.bd.Comunes
import ec.cacaotrace.datos.bd.ConComunes
import java.time.Instant
import java.util.UUID

/**
 * Movimiento de inventario (RF-INV-01).
 *
 * El inventario no se guarda como un número que se edita, sino como la suma de
 * sus movimientos: así el saldo siempre se puede explicar, y un error se
 * corrige con un movimiento nuevo, no reescribiendo un total.
 */
@Entity(
    tableName = "movimientos_inventario",
    primaryKeys = ["id"],
    indices = [Index(value = ["item", "fecha"])],
)
data class MovimientoInventarioEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    /**
     * `mazorcas`, `grano_seco`, `nibs`, `chocolate`, `azucar`, `manteca`,
     * `lecitina`, `empaques`.
     */
    val item: String,
    /** `entrada` o `salida`. */
    val tipo: String,
    val cantidad: Double,
    val unidad: String = "kg",
    val fecha: Instant,
    /** A qué lote o documento corresponde, para poder rastrearlo. */
    val referencia: String = "",
    val notas: String = "",
) : ConComunes

/** Stock mínimo por insumo, para avisar cuando queda poco (RF-INV-02). */
@Entity(
    tableName = "stocks_minimos",
    primaryKeys = ["id"],
    indices = [Index(value = ["item"], unique = true)],
)
data class StockMinimoEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val item: String,
    val minimo: Double,
    val unidad: String = "kg",
) : ConComunes

/** Un costo del lote o de la tanda de producción (RF-COS-01). */
@Entity(
    tableName = "costos",
    primaryKeys = ["id"],
    indices = [Index(value = ["loteId"]), Index(value = ["loteProduccionId"])],
    foreignKeys = [
        ForeignKey(
            entity = LoteEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LoteProduccionEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteProduccionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CostoEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val loteId: String? = null,
    val loteProduccionId: String? = null,
    /** `mazorcas`, `transporte`, `insumos`, `energia`, `empaques`, `tramites`. */
    val concepto: String,
    val montoUsd: Double,
    val fecha: Instant,
    val notas: String = "",
) : ConComunes

/** Una venta o un consumo propio (RF-VEN-01). */
@Entity(
    tableName = "ventas",
    primaryKeys = ["id"],
    indices = [Index(value = ["loteProduccionId", "fecha"])],
    foreignKeys = [
        ForeignKey(
            entity = LoteProduccionEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteProduccionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class VentaEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val loteProduccionId: String,
    val cliente: String = "",
    val barras: Int,
    val precioUnitarioUsd: Double = 0.0,
    val fecha: Instant,
    /** True si fue consumo propio o regalo: no entra en el margen. */
    val consumoPropio: Boolean = false,
    val notas: String = "",
) : ConComunes

/** Plantilla de checklist de BPM (RF-BPM-01). */
@Entity(tableName = "checklists_bpm", primaryKeys = ["id"])
data class ChecklistBpmEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val nombre: String,
    /** `diario`, `semanal`, `mensual`. */
    val frecuencia: String = "diario",
    /** Los puntos a revisar, como JSON: ["Limpieza de mesas", ...] */
    val itemsJson: String = "[]",
    val activo: Boolean = true,
) : ConComunes

/**
 * Un checklist de BPM ya diligenciado (RF-BPM-02).
 *
 * Pasadas 24 horas no se puede editar, solo anotar: es lo que le da valor de
 * registro ante una inspección de ARCSA. Un registro que se puede reescribir
 * para siempre no prueba nada.
 */
@Entity(
    tableName = "registros_bpm",
    primaryKeys = ["id"],
    indices = [Index(value = ["checklistId", "fecha"])],
    foreignKeys = [
        ForeignKey(
            entity = ChecklistBpmEntidad::class,
            parentColumns = ["id"],
            childColumns = ["checklistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RegistroBpmEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val checklistId: String,
    val fecha: Instant,
    /** Respuestas, como JSON: {"Limpieza de mesas": true, ...} */
    val respuestasJson: String = "{}",
    val responsable: String,
    val observaciones: String = "",
    /** Anotaciones posteriores al cierre, como JSON con fecha y texto. */
    val anotacionesJson: String = "[]",
    val cerrado: Boolean = false,
) : ConComunes

/** Resultado de laboratorio (RF-LAB-01). */
@Entity(
    tableName = "laboratorios",
    primaryKeys = ["id"],
    indices = [Index(value = ["loteId"]), Index(value = ["loteProduccionId"])],
    foreignKeys = [
        ForeignKey(
            entity = LoteEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LoteProduccionEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteProduccionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LaboratorioEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val loteId: String? = null,
    val loteProduccionId: String? = null,
    /** `cadmio`, `humedad`, `microbiologia`, … */
    val analisis: String,
    val valor: Double,
    val unidad: String = "mg/kg",
    val limite: Double? = null,
    val laboratorio: String = "",
    val fecha: Instant,
    /** PDF del informe, guardado como archivo adjunto. */
    val archivoId: String? = null,
) : ConComunes

/** Una alerta abierta o ya atendida (RF-ALE-01). */
@Entity(
    tableName = "alertas",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["estado", "fecha"]),
        Index(value = ["claveDedup"]),
        Index(value = ["loteId"]),
        Index(value = ["loteProduccionId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = LoteEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LoteProduccionEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteProduccionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AlertaEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val loteId: String? = null,
    val loteProduccionId: String? = null,
    /** Código de la regla: RN-01 … RN-17. */
    val regla: String,
    /** Nombre del enum Severidad. */
    val severidad: String,
    val quePaso: String,
    val porQueImporta: String = "",
    val queHacer: String = "",
    val correccionCodigo: String = "",
    val valorMedido: Double? = null,
    val valorEsperado: Double? = null,
    val fecha: Instant,
    /** `abierta`, `atendida`, `descartada`. */
    val estado: String = "abierta",
    /** Clave para no repetir la misma alerta una y otra vez. */
    val claveDedup: String = "",
) : ConComunes

/** Qué corrección se aplicó a una alerta y cómo salió (RF-COR-02). */
@Entity(
    tableName = "correcciones_aplicadas",
    primaryKeys = ["id"],
    indices = [Index(value = ["alertaId"])],
    foreignKeys = [
        ForeignKey(
            entity = AlertaEntidad::class,
            parentColumns = ["id"],
            childColumns = ["alertaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CorreccionAplicadaEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val alertaId: String,
    val correccionCodigo: String,
    val fecha: Instant,
    /** Qué pasó después de aplicarla. Es lo que permite aprender del error. */
    val resultado: String = "",
) : ConComunes

/** Una foto tomada en cualquier etapa (§3.1 de la ERS). */
@Entity(
    tableName = "fotos",
    primaryKeys = ["id"],
    indices = [Index(value = ["loteId", "etapa"]), Index(value = ["aptaDataset"])],
)
data class FotoEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    /** Ruta en la carpeta privada de la app. */
    val rutaLocal: String,
    val rutaMiniatura: String = "",
    /** Identificador del archivo en Google Drive, cuando ya se subió. */
    val driveFileId: String = "",
    val etapa: String = "",
    val loteId: String? = null,
    val loteProduccionId: String? = null,
    /** Lo que dijo el modelo, como JSON: {"clase":"monilia","confianza":0.83} */
    val analisisIaJson: String = "{}",
    /** Lo que dijo el usuario. Si está puesto, manda sobre la IA (RF-IA-03). */
    val etiquetaUsuario: String = "",
    /**
     * Si sirve para reentrenar: el usuario la revisó y la etiqueta es de fiar
     * (RF-PRC-09, §8.3 de la ERS).
     */
    val aptaDataset: Boolean = false,
    val latitud: Double? = null,
    val longitud: Double? = null,
) : ConComunes

/** Un modelo de IA instalado (RF-IA-05, RF-IA-06). */
@Entity(
    tableName = "modelos_ia",
    primaryKeys = ["id"],
    indices = [Index(value = ["nombre", "version"], unique = true)],
)
data class ModeloIaEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    /** `mazorca`, `corte`, `tostado`, `chocolate`. */
    val nombre: String,
    val version: String,
    /**
     * Las clases en orden, separadas por coma. El orden importa: el modelo
     * devuelve índices, no nombres.
     */
    val clases: String,
    val umbralConfianza: Double = 0.6,
    val ruta: String,
    val activo: Boolean = false,
    val metadatosJson: String = "{}",
) : ConComunes

/**
 * Los umbrales de la tabla 5.1 que el usuario cambió (RF-CFG-03).
 *
 * Solo se guardan los que difieren del valor de fábrica: así, si un valor por
 * defecto mejora en una versión nueva, lo hereda quien no lo haya tocado.
 */
@Entity(
    tableName = "umbrales_guardados",
    primaryKeys = ["id"],
    indices = [Index(value = ["clave"], unique = true)],
)
data class UmbralGuardadoEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val clave: String,
    val valor: Double,
) : ConComunes

/**
 * Ajustes que no son números: la tabla de la norma editada, el identificador
 * del teléfono, las preferencias de sincronización.
 *
 * Es clave/valor a propósito: añadir un ajuste nuevo no obliga a migrar el
 * esquema, que es justo lo que pide RNF-12.
 */
@Entity(
    tableName = "configuracion",
    primaryKeys = ["id"],
    indices = [Index(value = ["clave"], unique = true)],
)
data class AjusteEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val clave: String,
    val valor: String = "",
) : ConComunes

/**
 * Historial de cambios en los registros sensibles (RNF-11).
 *
 * Cubre BPM, prueba de corte y laboratorio: quién cambió qué, cuándo y cuál
 * era el valor anterior.
 */
@Entity(
    tableName = "auditoria",
    primaryKeys = ["id"],
    indices = [Index(value = ["tabla", "registroId"])],
)
data class AuditoriaEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val tabla: String,
    val registroId: String,
    val campo: String,
    val valorAnterior: String = "",
    val valorNuevo: String = "",
    val fecha: Instant,
    val motivo: String = "",
) : ConComunes

/**
 * Cola de salida hacia la nube (RF-SYN-01, RF-SYN-03).
 *
 * No lleva las columnas comunes: no es un dato del usuario, es una tarea
 * interna. Sincronizar la cola de sincronización no tendría sentido.
 */
@Entity(
    tableName = "cola_sync",
    indices = [Index(value = ["proximoIntento"]), Index(value = ["esArchivo"])],
)
data class OperacionSyncEntidad(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val tabla: String,
    val registroId: String,
    /** `crear`, `actualizar`, `eliminar`, `subir_foto`. */
    val operacion: String,
    /** Copia del registro en el momento de encolar, como JSON. */
    val cargaJson: String = "{}",
    val creadoEn: Instant = Instant.now(),
    val intentos: Int = 0,
    val proximoIntento: Instant? = null,
    val ultimoError: String = "",
    /**
     * Las fotos solo se suben con WiFi si el usuario dejó activa esa opción
     * (RF-SYN-04), así que la cola necesita saber si la operación es pesada.
     */
    val esArchivo: Boolean = false,
)
