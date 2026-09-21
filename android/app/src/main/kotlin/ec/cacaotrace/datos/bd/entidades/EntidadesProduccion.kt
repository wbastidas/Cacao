package ec.cacaotrace.datos.bd.entidades

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import ec.cacaotrace.datos.bd.Comunes
import ec.cacaotrace.datos.bd.ConComunes
import ec.cacaotrace.nucleo.modelo.MetodoAtemperado
import java.time.Instant

/** Una tanda de chocolate, hecha con uno o varios lotes de grano (RF-LOT-05). */
@Entity(
    tableName = "lotes_produccion",
    primaryKeys = ["id"],
    indices = [Index(value = ["codigo"], unique = true)],
)
data class LoteProduccionEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    /** Código P-AAAA-NNN. */
    val codigo: String,
    val fecha: Instant,
    val porcentajeCacao: Double = 90.0,
    val kgChocolate: Double? = null,
    /** `en_proceso`, `terminado`, `descartado`. */
    val estado: String = "en_proceso",
    val notas: String = "",
) : ConComunes

/**
 * Qué lotes de grano y cuántos kg entraron en una tanda de producción.
 *
 * Esta tabla es la que hace posible la trazabilidad hacia atrás: escaneando el
 * QR de una barra se llega hasta la finca de origen (RF-LOT-04).
 */
@Entity(
    tableName = "lotes_produccion_origen",
    primaryKeys = ["id"],
    indices = [Index(value = ["loteProduccionId"]), Index(value = ["loteId"])],
    foreignKeys = [
        ForeignKey(
            entity = LoteProduccionEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteProduccionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = LoteEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class LoteProduccionOrigenEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val loteProduccionId: String,
    val loteId: String,
    val kgUsados: Double,
) : ConComunes

/** Tostado del grano (RF-TOS-01). */
@Entity(
    tableName = "tostados",
    primaryKeys = ["id"],
    indices = [
        Index(value = ["loteProduccionId"], unique = true),
        Index(value = ["equipoId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = LoteProduccionEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteProduccionId"],
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
data class TostadoEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val loteProduccionId: String,
    val equipoId: String? = null,
    val fecha: Instant,
    val kgEntrada: Double = 0.0,
    val kgSalida: Double? = null,
    /** Perfil por tramos, como JSON: [{"minutos":10,"tempC":140}, ...] */
    val perfilJson: String = "[]",
    val tempC: Double? = null,
    val minutos: Int? = null,
    /** Grado que estimó el modelo M3, si está instalado (RF-TOS-03). */
    val gradoIa: String = "",
    val confianzaIa: Double? = null,
    /** Lo que dijo el usuario. Si difiere de la IA, manda esto (RF-IA-03). */
    val gradoUsuario: String = "",
    val modeloVersion: String = "",
    val fotoId: String? = null,
    /** Nombre de la receta de tostado guardada, para comparar lotes (RF-TOS-05). */
    val recetaNombre: String = "",
) : ConComunes

/** Separación de nibs y cascarilla (RF-DES-01). */
@Entity(
    tableName = "descascarillados",
    primaryKeys = ["id"],
    indices = [Index(value = ["loteProduccionId"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = LoteProduccionEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteProduccionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DescascarilladoEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val loteProduccionId: String,
    val fecha: Instant,
    val kgNibs: Double = 0.0,
    val kgCascarilla: Double = 0.0,
    val notas: String = "",
) : ConComunes

/** Refinado y conchado en el melanger (RF-REF-01 a RF-REF-04). */
@Entity(
    tableName = "refinados",
    primaryKeys = ["id"],
    indices = [Index(value = ["loteProduccionId"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = LoteProduccionEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteProduccionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RefinadoEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val loteProduccionId: String,
    val inicio: Instant,
    val fin: Instant? = null,
    val horas: Double? = null,
    val nibsKg: Double = 0.0,
    val azucarKg: Double = 0.0,
    val mantecaKg: Double = 0.0,
    val lecitinaKg: Double = 0.0,
    val momentoAzucar: Instant? = null,
    val tempC: Double? = null,
    /**
     * Evaluación sensorial 1-5, como JSON:
     * {"acidez":2,"amargor":4,"astringencia":2,"textura":5}
     */
    val sensorialJson: String = "{}",
    val notas: String = "",
) : ConComunes

/** Atemperado y moldeado (RF-ATE-01 a RF-ATE-05). */
@Entity(
    tableName = "atemperados",
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
data class AtemperadoEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val loteProduccionId: String,
    val fecha: Instant,
    val metodo: MetodoAtemperado = MetodoAtemperado.SIEMBRA,
    /**
     * Las tres temperaturas del proceso, como JSON:
     * {"fundido":48,"enfriado":27,"trabajo":31.5}
     */
    val tempsJson: String = "{}",
    val tempCuarto: Double? = null,
    val hrCuarto: Double? = null,
    /** Prueba del papel: ¿endureció con brillo en ~3 minutos? (RF-ATE-03) */
    val pruebaPapel: Boolean? = null,
    /** Resultado del modelo M4 sobre la foto de las barras (RF-ATE-04). */
    val resultadoIa: String = "",
    val confianzaIa: Double? = null,
    val resultadoUsuario: String = "",
    val modeloVersion: String = "",
    val fotoId: String? = null,
    /** Días transcurridos si es una inspección posterior (RF-ATE-05: 7, 30, 90). */
    val diasInspeccion: Int? = null,
) : ConComunes

/** Empaque y etiquetado de las barras (RF-EMP-01). */
@Entity(
    tableName = "empaques",
    primaryKeys = ["id"],
    indices = [Index(value = ["loteProduccionId"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = LoteProduccionEntidad::class,
            parentColumns = ["id"],
            childColumns = ["loteProduccionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class EmpaqueEntidad(
    @Embedded override val comunes: Comunes = Comunes(),
    val loteProduccionId: String,
    val barras: Int = 0,
    val pesoUnitarioG: Double = 50.0,
    val fechaElaboracion: Instant,
    val fechaVencimiento: Instant,
    val vidaUtilMeses: Int = 12,
    /**
     * Datos de la etiqueta, como JSON: ingredientes, tabla nutricional,
     * semáforo y número de notificación sanitaria (RF-EMP-02).
     */
    val etiquetaJson: String = "{}",
    val codigoQr: String = "",
) : ConComunes
