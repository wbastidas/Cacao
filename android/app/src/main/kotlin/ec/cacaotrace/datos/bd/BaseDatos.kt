package ec.cacaotrace.datos.bd

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import ec.cacaotrace.datos.bd.daos.AlertaDao
import ec.cacaotrace.datos.bd.daos.AperturaDao
import ec.cacaotrace.datos.bd.daos.BpmDao
import ec.cacaotrace.datos.bd.daos.ColaSyncDao
import ec.cacaotrace.datos.bd.daos.ConfiguracionDao
import ec.cacaotrace.datos.bd.daos.CostoDao
import ec.cacaotrace.datos.bd.daos.EquipoDao
import ec.cacaotrace.datos.bd.daos.FermentacionDao
import ec.cacaotrace.datos.bd.daos.FincaDao
import ec.cacaotrace.datos.bd.daos.FotoDao
import ec.cacaotrace.datos.bd.daos.InspeccionAlmacenDao
import ec.cacaotrace.datos.bd.daos.InventarioDao
import ec.cacaotrace.datos.bd.daos.LaboratorioDao
import ec.cacaotrace.datos.bd.daos.LoteDao
import ec.cacaotrace.datos.bd.daos.ProduccionDao
import ec.cacaotrace.datos.bd.daos.PruebaCorteDao
import ec.cacaotrace.datos.bd.daos.RecepcionDao
import ec.cacaotrace.datos.bd.daos.SacoDao
import ec.cacaotrace.datos.bd.daos.SecadoDao
import ec.cacaotrace.datos.bd.daos.VentaDao
import ec.cacaotrace.datos.bd.entidades.AjusteEntidad
import ec.cacaotrace.datos.bd.entidades.AlertaEntidad
import ec.cacaotrace.datos.bd.entidades.AperturaEntidad
import ec.cacaotrace.datos.bd.entidades.AtemperadoEntidad
import ec.cacaotrace.datos.bd.entidades.AuditoriaEntidad
import ec.cacaotrace.datos.bd.entidades.ChecklistBpmEntidad
import ec.cacaotrace.datos.bd.entidades.CorreccionAplicadaEntidad
import ec.cacaotrace.datos.bd.entidades.CostoEntidad
import ec.cacaotrace.datos.bd.entidades.DescascarilladoEntidad
import ec.cacaotrace.datos.bd.entidades.EmpaqueEntidad
import ec.cacaotrace.datos.bd.entidades.EquipoEntidad
import ec.cacaotrace.datos.bd.entidades.FermentacionEntidad
import ec.cacaotrace.datos.bd.entidades.FincaEntidad
import ec.cacaotrace.datos.bd.entidades.FotoEntidad
import ec.cacaotrace.datos.bd.entidades.InspeccionAlmacenEntidad
import ec.cacaotrace.datos.bd.entidades.LaboratorioEntidad
import ec.cacaotrace.datos.bd.entidades.LecturaFermentacionEntidad
import ec.cacaotrace.datos.bd.entidades.LecturaSecadoEntidad
import ec.cacaotrace.datos.bd.entidades.LoteEntidad
import ec.cacaotrace.datos.bd.entidades.LoteProduccionEntidad
import ec.cacaotrace.datos.bd.entidades.LoteProduccionOrigenEntidad
import ec.cacaotrace.datos.bd.entidades.ModeloIaEntidad
import ec.cacaotrace.datos.bd.entidades.MovimientoInventarioEntidad
import ec.cacaotrace.datos.bd.entidades.OperacionSyncEntidad
import ec.cacaotrace.datos.bd.entidades.PruebaCorteEntidad
import ec.cacaotrace.datos.bd.entidades.RecepcionEntidad
import ec.cacaotrace.datos.bd.entidades.RefinadoEntidad
import ec.cacaotrace.datos.bd.entidades.RegistroBpmEntidad
import ec.cacaotrace.datos.bd.entidades.SacoEntidad
import ec.cacaotrace.datos.bd.entidades.SecadoEntidad
import ec.cacaotrace.datos.bd.entidades.StockMinimoEntidad
import ec.cacaotrace.datos.bd.entidades.TostadoEntidad
import ec.cacaotrace.datos.bd.entidades.UmbralGuardadoEntidad
import ec.cacaotrace.datos.bd.entidades.VentaEntidad
import ec.cacaotrace.datos.bd.entidades.VolteoEntidad
import java.util.UUID

/**
 * Base de datos local de CacaoTrace.
 *
 * Es LA FUENTE DE VERDAD de la app (RF-SYN-01): todo se escribe aquí primero y
 * la nube es un espejo que se pone al día cuando hay señal. Por eso la app
 * funciona completa en la finca sin cobertura (RNF-01).
 */
@Database(
    entities = [
        // Configuración
        FincaEntidad::class,
        EquipoEntidad::class,
        UmbralGuardadoEntidad::class,
        AjusteEntidad::class,
        ModeloIaEntidad::class,
        // Lote de grano
        LoteEntidad::class,
        RecepcionEntidad::class,
        AperturaEntidad::class,
        FermentacionEntidad::class,
        LecturaFermentacionEntidad::class,
        VolteoEntidad::class,
        SecadoEntidad::class,
        LecturaSecadoEntidad::class,
        PruebaCorteEntidad::class,
        SacoEntidad::class,
        InspeccionAlmacenEntidad::class,
        // Lote de producción
        LoteProduccionEntidad::class,
        LoteProduccionOrigenEntidad::class,
        TostadoEntidad::class,
        DescascarilladoEntidad::class,
        RefinadoEntidad::class,
        AtemperadoEntidad::class,
        EmpaqueEntidad::class,
        // Apoyo
        MovimientoInventarioEntidad::class,
        StockMinimoEntidad::class,
        CostoEntidad::class,
        VentaEntidad::class,
        ChecklistBpmEntidad::class,
        RegistroBpmEntidad::class,
        LaboratorioEntidad::class,
        AlertaEntidad::class,
        CorreccionAplicadaEntidad::class,
        FotoEntidad::class,
        AuditoriaEntidad::class,
        OperacionSyncEntidad::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Convertidores::class)
abstract class BaseDatos : RoomDatabase() {

    abstract fun lotes(): LoteDao
    abstract fun fincas(): FincaDao
    abstract fun equipos(): EquipoDao
    abstract fun recepciones(): RecepcionDao
    abstract fun aperturas(): AperturaDao
    abstract fun fermentaciones(): FermentacionDao
    abstract fun secados(): SecadoDao
    abstract fun pruebasCorte(): PruebaCorteDao
    abstract fun sacos(): SacoDao
    abstract fun inspecciones(): InspeccionAlmacenDao
    abstract fun produccion(): ProduccionDao
    abstract fun alertas(): AlertaDao
    abstract fun inventario(): InventarioDao
    abstract fun costos(): CostoDao
    abstract fun ventas(): VentaDao
    abstract fun bpm(): BpmDao
    abstract fun laboratorio(): LaboratorioDao
    abstract fun fotos(): FotoDao
    abstract fun configuracion(): ConfiguracionDao
    abstract fun colaSync(): ColaSyncDao

    companion object {
        private const val NOMBRE = "cacaotrace.db"

        @Volatile
        private var instancia: BaseDatos? = null

        fun obtener(contexto: Context): BaseDatos = instancia ?: synchronized(this) {
            instancia ?: construir(contexto.applicationContext).also { instancia = it }
        }

        private fun construir(contexto: Context): BaseDatos =
            Room.databaseBuilder(contexto, BaseDatos::class.java, NOMBRE)
                .addCallback(SembradoInicial)
                // Sin fallbackToDestructiveMigration a propósito: prefiero que
                // una migración olvidada reviente en desarrollo a que borre en
                // silencio dos años de registros del usuario.
                .build()
    }
}

/**
 * Datos con los que la app es útil desde el primer arranque.
 *
 * Se insertan con SQL directo porque en `onCreate` la base todavía no expone
 * sus DAOs: Room aún está construyéndose.
 */
private object SembradoInicial : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        sembrarChecklists(db)
        sembrarStocksMinimos(db)
    }

    private fun comunes(): String {
        val ahora = System.currentTimeMillis()
        return "'${UUID.randomUUID()}', $ahora, $ahora, 'local', '', 'PENDIENTE', 0"
    }

    private fun sembrarChecklists(db: SupportSQLiteDatabase) {
        val checklists = listOf(
            "Limpieza diaria del taller" to Triple(
                "diario",
                listOf(
                    "Mesas y superficies limpias y desinfectadas",
                    "Utensilios lavados y guardados",
                    "Pisos barridos y trapeados",
                    "Basura retirada",
                    "Equipos apagados y limpios",
                ),
                true,
            ),
            "Higiene personal" to Triple(
                "diario",
                listOf(
                    "Manos lavadas antes de empezar",
                    "Uñas cortas y sin esmalte",
                    "Cofia o gorro puesto",
                    "Ropa de trabajo limpia",
                    "Sin joyas ni reloj",
                    "Sin heridas expuestas",
                ),
                true,
            ),
            "Control de plagas" to Triple(
                "semanal",
                listOf(
                    "Trampas revisadas",
                    "Sin rastros de roedores ni insectos",
                    "Puertas y ventanas con mallas en buen estado",
                    "Basura fuera del área de proceso",
                ),
                true,
            ),
            "Agua e insumos" to Triple(
                "semanal",
                listOf(
                    "Agua potable disponible",
                    "Tanque de agua limpio",
                    "Insumos dentro de su fecha de vencimiento",
                    "Insumos almacenados sobre pallets, no en el piso",
                ),
                true,
            ),
        )

        checklists.forEach { (nombre, datos) ->
            val (frecuencia, items, activo) = datos
            val itemsJson = items.joinToString(",", "[", "]") { "\"$it\"" }
            db.execSQL(
                """
                INSERT INTO checklists_bpm
                (id, creadoEn, modificadoEn, usuarioId, dispositivoId, estadoSync,
                 eliminado, nombre, frecuencia, itemsJson, activo)
                VALUES (${comunes()}, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(nombre, frecuencia, itemsJson, if (activo) 1 else 0),
            )
        }
    }

    private fun sembrarStocksMinimos(db: SupportSQLiteDatabase) {
        val stocks = listOf(
            Triple("azucar", 2.0, "kg"),
            Triple("manteca", 0.5, "kg"),
            Triple("lecitina", 0.1, "kg"),
            Triple("empaques", 50.0, "unidades"),
        )
        stocks.forEach { (item, minimo, unidad) ->
            db.execSQL(
                """
                INSERT INTO stocks_minimos
                (id, creadoEn, modificadoEn, usuarioId, dispositivoId, estadoSync,
                 eliminado, item, minimo, unidad)
                VALUES (${comunes()}, ?, ?, ?)
                """.trimIndent(),
                arrayOf(item, minimo, unidad),
            )
        }
    }
}
