package ec.cacaotrace.datos.bd.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import ec.cacaotrace.datos.bd.entidades.AperturaEntidad
import ec.cacaotrace.datos.bd.entidades.EquipoEntidad
import ec.cacaotrace.datos.bd.entidades.FermentacionEntidad
import ec.cacaotrace.datos.bd.entidades.FincaEntidad
import ec.cacaotrace.datos.bd.entidades.InspeccionAlmacenEntidad
import ec.cacaotrace.datos.bd.entidades.LecturaFermentacionEntidad
import ec.cacaotrace.datos.bd.entidades.LecturaSecadoEntidad
import ec.cacaotrace.datos.bd.entidades.LoteEntidad
import ec.cacaotrace.datos.bd.entidades.PruebaCorteEntidad
import ec.cacaotrace.datos.bd.entidades.RecepcionEntidad
import ec.cacaotrace.datos.bd.entidades.SacoEntidad
import ec.cacaotrace.datos.bd.entidades.SecadoEntidad
import ec.cacaotrace.datos.bd.entidades.VolteoEntidad
import ec.cacaotrace.nucleo.modelo.EstadoLote
import kotlinx.coroutines.flow.Flow

/**
 * Consultas del lote de grano.
 *
 * Todas filtran por `eliminado = 0`: el borrado es lógico, así que una
 * consulta que se olvide del filtro mostraría registros que el usuario ya
 * borró. Por eso no hay un "selectAll" genérico.
 */
@Dao
interface LoteDao {

    @Query("SELECT * FROM lotes WHERE eliminado = 0 ORDER BY fechaLlegada DESC")
    fun observarTodos(): Flow<List<LoteEntidad>>

    @Query(
        """
        SELECT * FROM lotes
        WHERE eliminado = 0 AND estado NOT IN ('VENDIDO', 'DESCARTADO')
        ORDER BY fechaLlegada DESC
        """,
    )
    fun observarActivos(): Flow<List<LoteEntidad>>

    @Query("SELECT * FROM lotes WHERE eliminado = 0 ORDER BY fechaLlegada DESC")
    suspend fun todos(): List<LoteEntidad>

    @Query("SELECT * FROM lotes WHERE id = :id")
    suspend fun porId(id: String): LoteEntidad?

    @Query("SELECT * FROM lotes WHERE id = :id")
    fun observarPorId(id: String): Flow<LoteEntidad?>

    @Query("SELECT * FROM lotes WHERE codigo = :codigo AND eliminado = 0")
    suspend fun porCodigo(codigo: String): LoteEntidad?

    /** Los códigos ya usados, para generar el siguiente correlativo del año. */
    @Query("SELECT codigo FROM lotes")
    suspend fun codigosUsados(): List<String>

    @Query("SELECT * FROM lotes WHERE estado = :estado AND eliminado = 0")
    suspend fun porEstado(estado: EstadoLote): List<LoteEntidad>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertar(lote: LoteEntidad)

    @Update
    suspend fun actualizar(lote: LoteEntidad)

    @Query("UPDATE lotes SET eliminado = 1, estadoSync = 'PENDIENTE' WHERE id = :id")
    suspend fun eliminarLogico(id: String)
}

@Dao
interface FincaDao {
    @Query("SELECT * FROM fincas WHERE eliminado = 0 ORDER BY nombre")
    fun observarTodas(): Flow<List<FincaEntidad>>

    @Query("SELECT * FROM fincas WHERE id = :id")
    suspend fun porId(id: String): FincaEntidad?

    @Upsert
    suspend fun guardar(finca: FincaEntidad)

    @Query("UPDATE fincas SET eliminado = 1, estadoSync = 'PENDIENTE' WHERE id = :id")
    suspend fun eliminarLogico(id: String)
}

@Dao
interface EquipoDao {
    @Query("SELECT * FROM equipos WHERE eliminado = 0 ORDER BY nombre")
    fun observarTodos(): Flow<List<EquipoEntidad>>

    @Query("SELECT * FROM equipos WHERE eliminado = 0 AND tipo = :tipo ORDER BY nombre")
    fun observarPorTipo(tipo: String): Flow<List<EquipoEntidad>>

    @Upsert
    suspend fun guardar(equipo: EquipoEntidad)

    @Query("UPDATE equipos SET eliminado = 1, estadoSync = 'PENDIENTE' WHERE id = :id")
    suspend fun eliminarLogico(id: String)
}

@Dao
interface RecepcionDao {
    @Query("SELECT * FROM recepciones WHERE loteId = :loteId AND eliminado = 0")
    suspend fun deLote(loteId: String): RecepcionEntidad?

    @Query("SELECT * FROM recepciones WHERE eliminado = 0")
    suspend fun todas(): List<RecepcionEntidad>

    @Upsert
    suspend fun guardar(recepcion: RecepcionEntidad)
}

@Dao
interface AperturaDao {
    @Query("SELECT * FROM aperturas WHERE loteId = :loteId AND eliminado = 0 LIMIT 1")
    suspend fun deLote(loteId: String): AperturaEntidad?

    @Upsert
    suspend fun guardar(apertura: AperturaEntidad)
}

@Dao
interface FermentacionDao {
    @Query("SELECT * FROM fermentaciones WHERE loteId = :loteId AND eliminado = 0 LIMIT 1")
    suspend fun deLote(loteId: String): FermentacionEntidad?

    @Query(
        "SELECT * FROM fermentaciones WHERE loteId = :loteId AND fin IS NULL " +
            "AND eliminado = 0 LIMIT 1",
    )
    suspend fun enCursoDeLote(loteId: String): FermentacionEntidad?

    @Query("SELECT * FROM fermentaciones WHERE fin IS NULL AND eliminado = 0")
    suspend fun todasEnCurso(): List<FermentacionEntidad>

    @Upsert
    suspend fun guardar(fermentacion: FermentacionEntidad)

    @Query(
        """
        SELECT * FROM lecturas_fermentacion
        WHERE fermentacionId = :fermentacionId AND eliminado = 0
        ORDER BY fechaHora
        """,
    )
    fun observarLecturas(fermentacionId: String): Flow<List<LecturaFermentacionEntidad>>

    @Query(
        """
        SELECT * FROM lecturas_fermentacion
        WHERE fermentacionId = :fermentacionId AND eliminado = 0
        ORDER BY fechaHora
        """,
    )
    suspend fun lecturas(fermentacionId: String): List<LecturaFermentacionEntidad>

    @Insert
    suspend fun insertarLectura(lectura: LecturaFermentacionEntidad)

    @Query(
        """
        SELECT * FROM volteos
        WHERE fermentacionId = :fermentacionId AND eliminado = 0
        ORDER BY fechaHora
        """,
    )
    fun observarVolteos(fermentacionId: String): Flow<List<VolteoEntidad>>

    @Query(
        """
        SELECT * FROM volteos
        WHERE fermentacionId = :fermentacionId AND eliminado = 0
        ORDER BY fechaHora
        """,
    )
    suspend fun volteos(fermentacionId: String): List<VolteoEntidad>

    @Query(
        """
        SELECT * FROM volteos
        WHERE fermentacionId = :fermentacionId AND eliminado = 0
        ORDER BY fechaHora DESC LIMIT 1
        """,
    )
    suspend fun ultimoVolteo(fermentacionId: String): VolteoEntidad?

    @Insert
    suspend fun insertarVolteo(volteo: VolteoEntidad)
}

@Dao
interface SecadoDao {
    @Query("SELECT * FROM secados WHERE loteId = :loteId AND eliminado = 0 LIMIT 1")
    suspend fun deLote(loteId: String): SecadoEntidad?

    @Query("SELECT * FROM secados WHERE loteId = :loteId AND fin IS NULL AND eliminado = 0 LIMIT 1")
    suspend fun enCursoDeLote(loteId: String): SecadoEntidad?

    @Query("SELECT * FROM secados WHERE fin IS NOT NULL AND eliminado = 0")
    suspend fun terminados(): List<SecadoEntidad>

    @Upsert
    suspend fun guardar(secado: SecadoEntidad)

    @Query(
        "SELECT * FROM lecturas_secado WHERE secadoId = :secadoId AND eliminado = 0 ORDER BY fecha",
    )
    fun observarLecturas(secadoId: String): Flow<List<LecturaSecadoEntidad>>

    @Query(
        "SELECT * FROM lecturas_secado WHERE secadoId = :secadoId AND eliminado = 0 ORDER BY fecha",
    )
    suspend fun lecturas(secadoId: String): List<LecturaSecadoEntidad>

    @Insert
    suspend fun insertarLectura(lectura: LecturaSecadoEntidad)
}

@Dao
interface PruebaCorteDao {
    @Query(
        "SELECT * FROM pruebas_corte WHERE loteId = :loteId AND eliminado = 0 ORDER BY fecha",
    )
    suspend fun deLote(loteId: String): List<PruebaCorteEntidad>

    @Query("SELECT * FROM pruebas_corte WHERE eliminado = 0 AND esParcial = 0")
    suspend fun completas(): List<PruebaCorteEntidad>

    @Insert
    suspend fun insertar(prueba: PruebaCorteEntidad)
}

@Dao
interface SacoDao {
    @Query("SELECT * FROM sacos WHERE eliminado = 0 ORDER BY codigoQr")
    fun observarTodos(): Flow<List<SacoEntidad>>

    @Query("SELECT * FROM sacos WHERE loteId = :loteId AND eliminado = 0 ORDER BY codigoQr")
    fun observarDeLote(loteId: String): Flow<List<SacoEntidad>>

    @Query("SELECT * FROM sacos WHERE loteId = :loteId AND eliminado = 0")
    suspend fun deLote(loteId: String): List<SacoEntidad>

    @Query("SELECT COUNT(*) FROM sacos WHERE eliminado = 0")
    suspend fun cuantos(): Int

    @Query("SELECT * FROM sacos WHERE codigoQr = :codigo AND eliminado = 0")
    suspend fun porCodigo(codigo: String): SacoEntidad?

    @Insert
    suspend fun insertar(saco: SacoEntidad)
}

@Dao
interface InspeccionAlmacenDao {
    @Query(
        "SELECT * FROM inspecciones_almacen WHERE eliminado = 0 ORDER BY fecha DESC LIMIT 1",
    )
    suspend fun ultima(): InspeccionAlmacenEntidad?

    @Query(
        """
        SELECT * FROM inspecciones_almacen
        WHERE loteId = :loteId AND eliminado = 0
        ORDER BY fecha DESC LIMIT 1
        """,
    )
    suspend fun ultimaDeLote(loteId: String): InspeccionAlmacenEntidad?

    @Query("SELECT * FROM inspecciones_almacen WHERE eliminado = 0 ORDER BY fecha DESC")
    fun observarTodas(): Flow<List<InspeccionAlmacenEntidad>>

    @Insert
    suspend fun insertar(inspeccion: InspeccionAlmacenEntidad)
}
