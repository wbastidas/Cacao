/// Base de datos local de CacaoTrace.
///
/// Es LA FUENTE DE VERDAD de la app (RF-SYN-01): todo se escribe aquí primero
/// y la nube es un espejo que se pone al día cuando hay señal. Por eso la app
/// funciona completa en la finca sin cobertura (RNF-01).
library;

import 'package:drift/drift.dart';
import 'package:drift_flutter/drift_flutter.dart';

// El codigo generado vive en un 'part' de este archivo, asi que los enums que
// usan las columnas tienen que estar visibles AQUI, no solo en tablas/.
import '../../nucleo/modelo/etapas.dart';
import 'tablas/apoyo.dart';
import 'tablas/comunes.dart';
import 'tablas/lote.dart';
import 'tablas/produccion.dart';

part 'base_datos.g.dart';

@DriftDatabase(
  tables: [
    // Configuración
    Fincas,
    Equipos,
    UmbralesGuardados,
    Configuracion,
    ModelosIa,
    // Lote de grano
    Lotes,
    Recepciones,
    Aperturas,
    Fermentaciones,
    LecturasFermentacion,
    Volteos,
    Secados,
    LecturasSecado,
    PruebasCorte,
    Sacos,
    InspeccionesAlmacen,
    // Lote de producción
    LotesProduccion,
    LotesProduccionOrigen,
    Tostados,
    Descascarillados,
    Refinados,
    Atemperados,
    Empaques,
    // Apoyo
    MovimientosInventario,
    StocksMinimos,
    Costos,
    Ventas,
    ChecklistsBpm,
    RegistrosBpm,
    Laboratorios,
    Alertas,
    CorreccionesAplicadas,
    Fotos,
    Auditoria,
    ColaSync,
  ],
)
class BaseDatos extends _$BaseDatos {
  BaseDatos([QueryExecutor? ejecutor])
      : super(ejecutor ?? driftDatabase(name: 'cacaotrace'));

  @override
  int get schemaVersion => 1;

  @override
  MigrationStrategy get migration => MigrationStrategy(
        onCreate: (m) async {
          await m.createAll();
          await _crearIndices();
          await _sembrarDatosIniciales();
        },
        beforeOpen: (detalles) async {
          // Las claves foráneas no vienen activadas por defecto en SQLite.
          await customStatement('PRAGMA foreign_keys = ON');
        },
      );

  /// Índices para las consultas que la app hace todo el tiempo.
  ///
  /// Sin estos, la pantalla de inicio recorre tablas enteras en cada refresco,
  /// que con unos pocos lotes no se nota pero con dos años de historial sí.
  Future<void> _crearIndices() async {
    const indices = [
      'CREATE INDEX IF NOT EXISTS idx_lotes_estado ON lotes(estado, eliminado)',
      'CREATE INDEX IF NOT EXISTS idx_lecturas_ferm ON lecturas_fermentacion(fermentacion_id, fecha_hora)',
      'CREATE INDEX IF NOT EXISTS idx_volteos_ferm ON volteos(fermentacion_id, fecha_hora)',
      'CREATE INDEX IF NOT EXISTS idx_lecturas_secado ON lecturas_secado(secado_id, fecha)',
      'CREATE INDEX IF NOT EXISTS idx_alertas_estado ON alertas(estado, fecha)',
      'CREATE INDEX IF NOT EXISTS idx_alertas_dedup ON alertas(clave_dedup)',
      'CREATE INDEX IF NOT EXISTS idx_fotos_lote ON fotos(lote_id, etapa)',
      'CREATE INDEX IF NOT EXISTS idx_cola_proximo ON cola_sync(proximo_intento)',
      'CREATE INDEX IF NOT EXISTS idx_inventario_item ON movimientos_inventario(item, fecha)',
      'CREATE INDEX IF NOT EXISTS idx_costos_lote ON costos(lote_id, lote_produccion_id)',
    ];
    for (final sql in indices) {
      await customStatement(sql);
    }
  }

  /// Datos con los que la app es útil desde el primer arranque.
  Future<void> _sembrarDatosIniciales() async {
    await batch((b) {
      b.insertAll(checklistsBpm, [
        ChecklistsBpmCompanion.insert(
          nombre: 'Limpieza diaria del taller',
          frecuencia: const Value('diario'),
          itemsJson: const Value(
            '["Mesas y superficies limpias y desinfectadas",'
            '"Utensilios lavados y guardados",'
            '"Pisos barridos y trapeados",'
            '"Basura retirada",'
            '"Equipos apagados y limpios"]',
          ),
        ),
        ChecklistsBpmCompanion.insert(
          nombre: 'Higiene personal',
          frecuencia: const Value('diario'),
          itemsJson: const Value(
            '["Manos lavadas antes de empezar",'
            '"Uñas cortas y sin esmalte",'
            '"Cofia o gorro puesto",'
            '"Ropa de trabajo limpia",'
            '"Sin joyas ni reloj",'
            '"Sin heridas expuestas"]',
          ),
        ),
        ChecklistsBpmCompanion.insert(
          nombre: 'Control de plagas',
          frecuencia: const Value('semanal'),
          itemsJson: const Value(
            '["Trampas revisadas",'
            '"Sin rastros de roedores ni insectos",'
            '"Puertas y ventanas con mallas en buen estado",'
            '"Basura fuera del area de proceso"]',
          ),
        ),
        ChecklistsBpmCompanion.insert(
          nombre: 'Agua e insumos',
          frecuencia: const Value('semanal'),
          itemsJson: const Value(
            '["Agua potable disponible",'
            '"Tanque de agua limpio",'
            '"Insumos dentro de su fecha de vencimiento",'
            '"Insumos almacenados sobre pallets, no en el piso"]',
          ),
        ),
      ]);

      b.insertAll(stocksMinimos, [
        StocksMinimosCompanion.insert(item: 'azucar', minimo: 2),
        StocksMinimosCompanion.insert(item: 'manteca', minimo: 0.5),
        StocksMinimosCompanion.insert(item: 'lecitina', minimo: 0.1),
        StocksMinimosCompanion.insert(
            item: 'empaques', minimo: 50, unidad: const Value('unidades')),
      ]);
    });
  }

  /// Borra todo. Solo para tests y para "empezar de cero" desde Ajustes.
  Future<void> vaciar() async {
    await transaction(() async {
      for (final t in allTables) {
        await delete(t).go();
      }
    });
  }
}
