package ec.cacaotrace.datos.bd.entidades

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import ec.cacaotrace.datos.bd.Comunes
import ec.cacaotrace.datos.bd.ConComunes
import ec.cacaotrace.nucleo.modelo.EstadoLote
import ec.cacaotrace.nucleo.modelo.MetodoSecado
import ec.cacaotrace.nucleo.modelo.OlorFermentacion
import java.time.Instant

/** Finca de origen del cacao (RF-CFG-01). */
@Entity(tableName = "fincas", primaryKeys = ["id"])
data class FincaEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val nombre: String,
    val provincia: String = "",
    val canton: String = "",
    val latitud: Double? = null,
    val longitud: Double? = null,
    val contacto: String = "",
    val variedad: String = "CCN-51",
    val notas: String = "",
) : ConComunes

/**
 * Equipo del taller: fermentador, marquesina, horno, melanger, moldes
 * (RF-CFG-02).
 *
 * Las medidas importan de verdad: un fermentador demasiado grande para poca
 * baba enfría la masa, que es la causa más común de granos pizarrosos.
 */
@Entity(tableName = "equipos", primaryKeys = ["id"])
data class EquipoEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val tipo: String,
    val nombre: String,
    val largoCm: Double? = null,
    val anchoCm: Double? = null,
    val altoCm: Double? = null,
    val material: String = "",
    val capacidadKg: Double? = null,
    val notas: String = "",
) : ConComunes

/** Lote de grano. Es la unidad de trazabilidad (RF-LOT-01). */
@Entity(
    tableName = "lotes",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["codigo"], unique = true),
        Index(value = ["estado", "eliminado"]),
        Index(value = ["fincaId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = FincaEntidad::class,
            parentColumns = ["id"],
            childColumns = ["fincaId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class LoteEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    /** Código L-AAAA-NNN, único y visible para el usuario. */
    val codigo: String,
    val fincaId: String? = null,
    val variedad: String = "CCN-51",
    val fechaCosecha: Instant? = null,
    val fechaLlegada: Instant,
    val estado: EstadoLote = EstadoLote.RECEPCION,
    val notas: String = "",
    /** Motivo si se saltó una etapa obligatoria (RF-LOT-03). */
    val motivoSalto: String = "",
    /** Queda bloqueado para venta si el cadmio supera el límite (RN-15). */
    val ventaBloqueada: Boolean = false,
    val motivoBloqueo: String = "",
) : ConComunes

/** Recepción de mazorcas (RF-REC-01). */
@Entity(
    tableName = "recepciones",
    primaryKeys = ["id"],
    indices = [Index(value = ["loteId"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = LoteEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RecepcionEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val loteId: String,
    val sacos: Int = 0,
    val mazorcasTotal: Int = 0,
    val pesoKg: Double = 0.0,
    val mazorcasSanas: Int = 0,
    val mazorcasMonilia: Int = 0,
    val mazorcasFitoftora: Int = 0,
    val mazorcasOtro: Int = 0,
    val mazorcasDescartadas: Int = 0,
    val motivoDescarte: String = "",
    val diasReposo: Int = 4,
    val fechaAperturaPlan: Instant? = null,
) : ConComunes

/** Apertura de las mazorcas y paso de la baba al fermentador (RF-APE-01). */
@Entity(
    tableName = "aperturas",
    primaryKeys = ["id"],
    indices = [Index(value = ["loteId"])],
    foreignKeys = [
        ForeignKey(
            entity = LoteEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AperturaEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val loteId: String,
    val fecha: Instant,
    val mazorcasAbiertas: Int = 0,
    val kgBaba: Double = 0.0,
    val kgCascaraAnadida: Double = 0.0,
    val notas: String = "",
) : ConComunes

/** Una fermentación en curso o terminada (RF-FER-01). */
@Entity(
    tableName = "fermentaciones",
    primaryKeys = ["id"],
    indices = [Index(value = ["loteId"]), Index(value = ["equipoId"])],
    foreignKeys = [
        ForeignKey(
            entity = LoteEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EquipoEntidad::class,
            parentColumns = ["id"],
            childColumns = ["equipoId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class FermentacionEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val loteId: String,
    val equipoId: String? = null,
    val inicio: Instant,
    val fin: Instant? = null,
    val masaKg: Double = 0.0,
    val aislamiento: String = "",
    val notas: String = "",
) : ConComunes

/** Una lectura de temperatura, pH y olor durante la fermentación (RF-FER-02/03). */
@Entity(
    tableName = "lecturas_fermentacion",
    primaryKeys = ["id"],
    indices = [Index(value = ["fermentacionId", "fechaHora"])],
    foreignKeys = [
        ForeignKey(
            entity = FermentacionEntidad::class,
            parentColumns = ["id"],
            childColumns = ["fermentacionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LecturaFermentacionEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val fermentacionId: String,
    val fechaHora: Instant,
    val tempC: Double? = null,
    val ph: Double? = null,
    val olor: OlorFermentacion? = null,
    /** `manual` o `sensor`: importa para saber cuánto confiar en la lectura. */
    val fuente: String = "manual",
    val fotoId: String? = null,
    val notas: String = "",
) : ConComunes

/** Un volteo registrado (RF-FER-04). */
@Entity(
    tableName = "volteos",
    primaryKeys = ["id"],
    indices = [Index(value = ["fermentacionId", "fechaHora"])],
    foreignKeys = [
        ForeignKey(
            entity = FermentacionEntidad::class,
            parentColumns = ["id"],
            childColumns = ["fermentacionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class VolteoEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val fermentacionId: String,
    val fechaHora: Instant,
    val fotoId: String? = null,
) : ConComunes

/** El secado del grano (RF-SEC-01). */
@Entity(
    tableName = "secados",
    primaryKeys = ["id"],
    indices = [Index(value = ["loteId"])],
    foreignKeys = [
        ForeignKey(
            entity = LoteEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SecadoEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val loteId: String,
    val metodo: MetodoSecado = MetodoSecado.MARQUESINA,
    val inicio: Instant,
    val fin: Instant? = null,
    val kgSeco: Double? = null,
    val notas: String = "",
) : ConComunes

/** Una jornada de secado (RF-SEC-02/03/04). */
@Entity(
    tableName = "lecturas_secado",
    primaryKeys = ["id"],
    indices = [Index(value = ["secadoId", "fecha"])],
    foreignKeys = [
        ForeignKey(
            entity = SecadoEntidad::class,
            parentColumns = ["id"],
            childColumns = ["secadoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LecturaSecadoEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val secadoId: String,
    val fecha: Instant,
    val humedadGrano: Double? = null,
    /** Resultado de la "prueba del puñado" cuando no hay medidor (RF-SEC-03). */
    val pruebaPunado: String = "",
    val tempAmbiente: Double? = null,
    val hrAmbiente: Double? = null,
    val espesorCm: Double? = null,
    val remociones: Int = 0,
    val moho: Boolean = false,
    val fotoId: String? = null,
) : ConComunes

/** Una prueba de corte (RF-PRC-01 a RF-PRC-10). */
@Entity(
    tableName = "pruebas_corte",
    primaryKeys = ["id"],
    indices = [Index(value = ["loteId", "fecha"])],
    foreignKeys = [
        ForeignKey(
            entity = LoteEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PruebaCorteEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val loteId: String,
    val fecha: Instant,
    val perfilNorma: String = "ccn51_referencia",
    val granos: Int = 0,
    /** Conteo por clase, como JSON: {"bien_fermentado": 70, ...} */
    val conteosJson: String = "{}",
    /**
     * Porcentajes calculados, como JSON. Se guardan para no recalcularlos y
     * para que el reporte muestre exactamente lo que vio el usuario ese día,
     * aunque después se edite la tabla de la norma.
     */
    val porcentajesJson: String = "{}",
    val resultado: String = "",
    val conforme: Boolean = false,
    val peso100g: Double? = null,
    /** Versión del modelo M2 usada, o vacío si fue conteo manual (RF-IA-05). */
    val modeloVersion: String = "",
    /** Prueba parcial del día 5 con menos de 100 granos (RF-FER-06). */
    val esParcial: Boolean = false,
    val fotoId: String? = null,
) : ConComunes

/** Un saco de grano seco almacenado, con su propio QR (RF-ALM-01). */
@Entity(
    tableName = "sacos",
    primaryKeys = ["id"],
    indices = [Index(value = ["codigoQr"], unique = true), Index(value = ["loteId"])],
    foreignKeys = [
        ForeignKey(
            entity = LoteEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SacoEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val loteId: String,
    val codigoQr: String,
    val pesoKg: Double = 0.0,
    val ubicacion: String = "",
    /** `almacenado`, `en_produccion`, `consumido`. */
    val estado: String = "almacenado",
) : ConComunes

/** Inspección periódica del almacén (RF-ALM-02). */
@Entity(
    tableName = "inspecciones_almacen",
    primaryKeys = ["id"],
    indices = [Index(value = ["loteId", "fecha"])],
    foreignKeys = [
        ForeignKey(
            entity = LoteEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class InspeccionAlmacenEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val loteId: String? = null,
    val fecha: Instant,
    val humedadRelativa: Double? = null,
    val temperatura: Double? = null,
    val plagas: Boolean = false,
    val moho: Boolean = false,
    val olor: String = "",
    val fotoId: String? = null,
    val notas: String = "",
) : ConComunes
