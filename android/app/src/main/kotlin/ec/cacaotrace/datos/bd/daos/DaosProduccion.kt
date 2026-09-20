package ec.cacaotrace.datos.bd.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import ec.cacaotrace.datos.bd.entidades.AtemperadoEntidad
import ec.cacaotrace.datos.bd.entidades.DescascarilladoEntidad
import ec.cacaotrace.datos.bd.entidades.EmpaqueEntidad
import ec.cacaotrace.datos.bd.entidades.LoteProduccionEntidad
import ec.cacaotrace.datos.bd.entidades.LoteProduccionOrigenEntidad
import ec.cacaotrace.datos.bd.entidades.RefinadoEntidad
import ec.cacaotrace.datos.bd.entidades.TostadoEntidad
import kotlinx.coroutines.flow.Flow

@Dao
interface ProduccionDao {

    @Query("SELECT * FROM lotes_produccion WHERE eliminado = 0 ORDER BY fecha DESC")
    fun observarTodas(): Flow<List<LoteProduccionEntidad>>

    @Query("SELECT * FROM lotes_produccion WHERE id = :id")
    suspend fun porId(id: String): LoteProduccionEntidad?

    @Query("SELECT * FROM lotes_produccion WHERE id IN (:ids) AND eliminado = 0")
    suspend fun porIds(ids: List<String>): List<LoteProduccionEntidad>

    @Query("SELECT codigo FROM lotes_produccion")
    suspend fun codigosUsados(): List<String>

    @Insert
    suspend fun insertar(tanda: LoteProduccionEntidad)

    @Update
    suspend fun actualizar(tanda: LoteProduccionEntidad)

    // ----- orígenes -----

    @Query("SELECT * FROM lotes_produccion_origen WHERE loteProduccionId = :id AND eliminado = 0")
    suspend fun origenes(id: String): List<LoteProduccionOrigenEntidad>

    @Query("SELECT * FROM lotes_produccion_origen WHERE loteId = :loteId AND eliminado = 0")
    suspend fun origenesDeLote(loteId: String): List<LoteProduccionOrigenEntidad>

    @Insert
    suspend fun insertarOrigen(origen: LoteProduccionOrigenEntidad)

    // ----- etapas -----

    @Query("SELECT * FROM tostados WHERE loteProduccionId = :id AND eliminado = 0")
    suspend fun tostado(id: String): TostadoEntidad?

    @Upsert
    suspend fun guardarTostado(tostado: TostadoEntidad)

    @Query("SELECT * FROM descascarillados WHERE loteProduccionId = :id AND eliminado = 0")
    suspend fun descascarillado(id: String): DescascarilladoEntidad?

    @Query("SELECT * FROM descascarillados WHERE loteProduccionId IN (:ids) AND eliminado = 0")
    suspend fun descascarilladosDe(ids: List<String>): List<DescascarilladoEntidad>

    @Upsert
    suspend fun guardarDescascarillado(d: DescascarilladoEntidad)

    @Query("SELECT * FROM refinados WHERE loteProduccionId = :id AND eliminado = 0")
    suspend fun refinado(id: String): RefinadoEntidad?

    @Upsert
    suspend fun guardarRefinado(r: RefinadoEntidad)

    @Query(
        "SELECT * FROM atemperados WHERE loteProduccionId = :id AND eliminado = 0 ORDER BY fecha",
    )
    suspend fun atemperados(id: String): List<AtemperadoEntidad>

    @Insert
    suspend fun insertarAtemperado(a: AtemperadoEntidad)

    @Query("SELECT * FROM empaques WHERE loteProduccionId = :id AND eliminado = 0")
    suspend fun empaque(id: String): EmpaqueEntidad?

    @Upsert
    suspend fun guardarEmpaque(e: EmpaqueEntidad)
}
