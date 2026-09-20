package ec.cacaotrace.datos.bd.daos

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import ec.cacaotrace.datos.bd.entidades.AjusteEntidad
import ec.cacaotrace.datos.bd.entidades.AlertaEntidad
import ec.cacaotrace.datos.bd.entidades.AuditoriaEntidad
import ec.cacaotrace.datos.bd.entidades.ChecklistBpmEntidad
import ec.cacaotrace.datos.bd.entidades.CorreccionAplicadaEntidad
import ec.cacaotrace.datos.bd.entidades.CostoEntidad
import ec.cacaotrace.datos.bd.entidades.FotoEntidad
import ec.cacaotrace.datos.bd.entidades.LaboratorioEntidad
import ec.cacaotrace.datos.bd.entidades.ModeloIaEntidad
import ec.cacaotrace.datos.bd.entidades.MovimientoInventarioEntidad
import ec.cacaotrace.datos.bd.entidades.OperacionSyncEntidad
import ec.cacaotrace.datos.bd.entidades.RegistroBpmEntidad
import ec.cacaotrace.datos.bd.entidades.StockMinimoEntidad
import ec.cacaotrace.datos.bd.entidades.UmbralGuardadoEntidad
import ec.cacaotrace.datos.bd.entidades.VentaEntidad
import java.time.Instant
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertaDao {

    @Query(
        """
        SELECT * FROM alertas
        WHERE estado = 'abierta' AND eliminado = 0
        ORDER BY CASE severidad
            WHEN 'BLOQUEANTE' THEN 0 WHEN 'URGENTE' THEN 1 ELSE 2 END, fecha DESC
        """,
    )
    fun observarAbiertas(): Flow<List<AlertaEntidad>>

    @Query(
        """
        SELECT * FROM alertas
        WHERE estado = 'abierta' AND eliminado = 0 AND loteId = :loteId
        ORDER BY CASE severidad
            WHEN 'BLOQUEANTE' THEN 0 WHEN 'URGENTE' THEN 1 ELSE 2 END, fecha DESC
        """,
    )
    fun observarAbiertasDeLote(loteId: String): Flow<List<AlertaEntidad>>

    @Query("SELECT * FROM alertas WHERE estado = 'abierta' AND eliminado = 0")
    suspend fun abiertas(): List<AlertaEntidad>

    @Query("SELECT * FROM alertas WHERE loteId = :loteId AND eliminado = 0 ORDER BY fecha")
    suspend fun deLote(loteId: String): List<AlertaEntidad>

    /** Para no duplicar una alerta que sigue abierta. */
    @Query(
        "SELECT * FROM alertas WHERE claveDedup = :clave AND estado = 'abierta' " +
            "AND eliminado = 0 LIMIT 1",
    )
    suspend fun abiertaConClave(clave: String): AlertaEntidad?

    /** ¿Hay algún bloqueo activo sobre este lote? (RN-08, RN-15) */
    @Query(
        """
        SELECT * FROM alertas
        WHERE loteId = :loteId AND estado = 'abierta'
          AND severidad = 'BLOQUEANTE' AND eliminado = 0 LIMIT 1
        """,
    )
    suspend fun bloqueoActivo(loteId: String): AlertaEntidad?

    @Insert
    suspend fun insertar(alerta: AlertaEntidad)

    @Update
    suspend fun actualizar(alerta: AlertaEntidad)

    @Query("SELECT * FROM alertas WHERE id = :id")
    suspend fun porId(id: String): AlertaEntidad?

    @Insert
    suspend fun insertarCorreccion(c: CorreccionAplicadaEntidad)

    @Query(
        "SELECT * FROM correcciones_aplicadas WHERE alertaId IN (:alertaIds) ORDER BY fecha",
    )
    suspend fun correccionesDe(alertaIds: List<String>): List<CorreccionAplicadaEntidad>
}

@Dao
interface InventarioDao {
    @Query("SELECT * FROM movimientos_inventario WHERE eliminado = 0 ORDER BY fecha DESC")
    fun observarMovimientos(): Flow<List<MovimientoInventarioEntidad>>

    @Query("SELECT * FROM movimientos_inventario WHERE eliminado = 0")
    suspend fun movimientos(): List<MovimientoInventarioEntidad>

    @Insert
    suspend fun insertarMovimiento(m: MovimientoInventarioEntidad)

    @Query("SELECT * FROM stocks_minimos WHERE eliminado = 0")
    suspend fun stocksMinimos(): List<StockMinimoEntidad>

    @Upsert
    suspend fun guardarStockMinimo(s: StockMinimoEntidad)
}

@Dao
interface CostoDao {
    @Query(
        """
        SELECT * FROM costos
        WHERE eliminado = 0 AND (loteProduccionId = :tandaId OR loteId IN (:loteIds))
        """,
    )
    suspend fun deProduccion(tandaId: String, loteIds: List<String>): List<CostoEntidad>

    @Query("SELECT * FROM costos WHERE eliminado = 0 ORDER BY fecha DESC")
    fun observarTodos(): Flow<List<CostoEntidad>>

    @Insert
    suspend fun insertar(costo: CostoEntidad)
}

@Dao
interface VentaDao {
    @Query(
        """
        SELECT * FROM ventas
        WHERE loteProduccionId = :tandaId AND consumoPropio = 0 AND eliminado = 0
        """,
    )
    suspend fun deProduccion(tandaId: String): List<VentaEntidad>

    @Query("SELECT * FROM ventas WHERE eliminado = 0 ORDER BY fecha DESC")
    fun observarTodas(): Flow<List<VentaEntidad>>

    @Insert
    suspend fun insertar(venta: VentaEntidad)
}

@Dao
interface BpmDao {
    @Query("SELECT * FROM checklists_bpm WHERE activo = 1 AND eliminado = 0 ORDER BY nombre")
    fun observarChecklists(): Flow<List<ChecklistBpmEntidad>>

    @Query("SELECT * FROM checklists_bpm WHERE eliminado = 0")
    suspend fun checklists(): List<ChecklistBpmEntidad>

    @Query(
        "SELECT * FROM checklists_bpm WHERE activo = 1 AND eliminado = 0 " +
            "AND frecuencia = :frecuencia",
    )
    suspend fun checklistsPorFrecuencia(frecuencia: String): List<ChecklistBpmEntidad>

    @Insert
    suspend fun insertarChecklist(c: ChecklistBpmEntidad)

    @Query(
        """
        SELECT * FROM registros_bpm
        WHERE checklistId = :checklistId AND fecha >= :desde AND eliminado = 0
        LIMIT 1
        """,
    )
    suspend fun registroDesde(checklistId: String, desde: Instant): RegistroBpmEntidad?

    @Query("SELECT * FROM registros_bpm WHERE eliminado = 0 ORDER BY fecha")
    suspend fun todosLosRegistros(): List<RegistroBpmEntidad>

    @Query("SELECT * FROM registros_bpm WHERE id = :id")
    suspend fun registroPorId(id: String): RegistroBpmEntidad?

    @Upsert
    suspend fun guardarRegistro(r: RegistroBpmEntidad)

    /** Cierra los registros con más de 24 horas (RF-BPM-02). */
    @Query("UPDATE registros_bpm SET cerrado = 1 WHERE fecha < :limite AND cerrado = 0")
    suspend fun cerrarVencidos(limite: Instant): Int
}

@Dao
interface LaboratorioDao {
    @Query("SELECT * FROM laboratorios WHERE eliminado = 0 ORDER BY fecha DESC")
    fun observarTodos(): Flow<List<LaboratorioEntidad>>

    @Query("SELECT * FROM laboratorios WHERE loteId = :loteId AND eliminado = 0")
    suspend fun deLote(loteId: String): List<LaboratorioEntidad>

    @Insert
    suspend fun insertar(l: LaboratorioEntidad)
}

@Dao
interface FotoDao {
    @Query("SELECT * FROM fotos WHERE loteId = :loteId AND eliminado = 0")
    suspend fun deLote(loteId: String): List<FotoEntidad>

    @Query("SELECT * FROM fotos WHERE aptaDataset = 1 AND eliminado = 0")
    suspend fun paraDataset(): List<FotoEntidad>

    @Query("SELECT * FROM fotos WHERE aptaDataset = 1 AND etapa = :etapa AND eliminado = 0")
    suspend fun paraDatasetDeEtapa(etapa: String): List<FotoEntidad>

    @Query("SELECT * FROM fotos WHERE id = :id")
    suspend fun porId(id: String): FotoEntidad?

    @Insert
    suspend fun insertar(foto: FotoEntidad)

    @Update
    suspend fun actualizar(foto: FotoEntidad)
}

@Dao
interface ConfiguracionDao {
    @Query("SELECT * FROM umbrales_guardados WHERE eliminado = 0")
    suspend fun umbrales(): List<UmbralGuardadoEntidad>

    @Query("SELECT * FROM umbrales_guardados WHERE eliminado = 0")
    fun observarUmbrales(): Flow<List<UmbralGuardadoEntidad>>

    @Query("SELECT * FROM umbrales_guardados WHERE clave = :clave")
    suspend fun umbral(clave: String): UmbralGuardadoEntidad?

    @Upsert
    suspend fun guardarUmbral(u: UmbralGuardadoEntidad)

    @Query("DELETE FROM umbrales_guardados WHERE clave = :clave")
    suspend fun borrarUmbral(clave: String)

    @Query("SELECT * FROM configuracion WHERE clave = :clave")
    suspend fun ajuste(clave: String): AjusteEntidad?

    @Query("SELECT * FROM configuracion WHERE clave = :clave")
    fun observarAjuste(clave: String): Flow<AjusteEntidad?>

    @Upsert
    suspend fun guardarAjuste(a: AjusteEntidad)

    @Query("SELECT * FROM modelos_ia WHERE nombre = :nombre AND activo = 1 AND eliminado = 0")
    suspend fun modeloActivo(nombre: String): ModeloIaEntidad?

    @Query("SELECT * FROM modelos_ia WHERE eliminado = 0")
    fun observarModelos(): Flow<List<ModeloIaEntidad>>

    @Query("UPDATE modelos_ia SET activo = 0 WHERE nombre = :nombre")
    suspend fun desactivarModelos(nombre: String)

    @Upsert
    suspend fun guardarModelo(m: ModeloIaEntidad)

    @Insert
    suspend fun auditar(a: AuditoriaEntidad)

    @Query("SELECT * FROM auditoria WHERE tabla = :tabla AND registroId = :id ORDER BY fecha")
    suspend fun historialDe(tabla: String, id: String): List<AuditoriaEntidad>
}

@Dao
interface ColaSyncDao {
    @Query("SELECT COUNT(*) FROM cola_sync")
    suspend fun pendientes(): Int

    @Query("SELECT COUNT(*) FROM cola_sync")
    fun observarPendientes(): Flow<Int>

    /**
     * Primero los registros y después los archivos: si se corta la señal a
     * media subida, lo que se salva son los datos, que es lo que importa.
     */
    @Query(
        """
        SELECT * FROM cola_sync
        WHERE proximoIntento IS NULL OR proximoIntento <= :ahora
        ORDER BY esArchivo ASC, creadoEn ASC
        """,
    )
    suspend fun listasParaEnviar(ahora: Instant): List<OperacionSyncEntidad>

    @Query("SELECT * FROM cola_sync WHERE intentos >= :maximo")
    suspend fun conError(maximo: Int): List<OperacionSyncEntidad>

    @Insert
    suspend fun encolar(op: OperacionSyncEntidad)

    @Update
    suspend fun actualizar(op: OperacionSyncEntidad)

    @Delete
    suspend fun borrar(op: OperacionSyncEntidad)

    @Query("UPDATE cola_sync SET intentos = 0, proximoIntento = :ahora, ultimoError = ''")
    suspend fun reintentarTodo(ahora: Instant)
}
