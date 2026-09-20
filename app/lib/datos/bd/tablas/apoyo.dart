/// Tablas de apoyo: inventario, costos, ventas, BPM, laboratorio, alertas,
/// fotos, modelos de IA, umbrales, auditoría y cola de sincronización
/// (§6 de la ERS, módulos INV, COS, VEN, BPM, LAB, ALE, COR).
library;

import 'package:drift/drift.dart';

import 'comunes.dart';
import 'lote.dart';
import 'produccion.dart';

/// Movimiento de inventario (RF-INV-01).
///
/// El inventario no se guarda como un número que se edita, sino como la suma
/// de sus movimientos: así siempre se puede explicar de dónde salió cada kg.
@DataClassName('MovimientoInventario')
class MovimientosInventario extends Table with ColumnasComunes {
  /// `mazorcas`, `grano_seco`, `nibs`, `chocolate`, `azucar`, `manteca`,
  /// `lecitina`, `empaques`.
  TextColumn get item => text()();

  /// `entrada` o `salida`.
  TextColumn get tipo => text()();
  RealColumn get cantidad => real()();
  TextColumn get unidad => text().withDefault(const Constant('kg'))();
  DateTimeColumn get fecha => dateTime()();

  /// A qué lote o documento corresponde, para poder rastrearlo.
  TextColumn get referencia => text().withDefault(const Constant(''))();
  TextColumn get notas => text().withDefault(const Constant(''))();
}

/// Stock mínimo por insumo, para avisar cuando queda poco (RF-INV-02).
@DataClassName('StockMinimo')
class StocksMinimos extends Table with ColumnasComunes {
  TextColumn get item => text().unique()();
  RealColumn get minimo => real()();
  TextColumn get unidad => text().withDefault(const Constant('kg'))();
}

/// Un costo del lote o de la tanda de producción (RF-COS-01).
@DataClassName('Costo')
class Costos extends Table with ColumnasComunes {
  TextColumn get loteId => text().nullable().references(Lotes, #id)();
  TextColumn get loteProduccionId =>
      text().nullable().references(LotesProduccion, #id)();

  /// `mazorcas`, `transporte`, `insumos`, `energia`, `empaques`, `tramites`.
  TextColumn get concepto => text()();
  RealColumn get montoUsd => real()();
  DateTimeColumn get fecha => dateTime()();
  TextColumn get notas => text().withDefault(const Constant(''))();
}

/// Una venta o un consumo propio (RF-VEN-01).
@DataClassName('Venta')
class Ventas extends Table with ColumnasComunes {
  TextColumn get loteProduccionId => text().references(LotesProduccion, #id)();
  TextColumn get cliente => text().withDefault(const Constant(''))();
  IntColumn get barras => integer()();
  RealColumn get precioUnitarioUsd => real().withDefault(const Constant(0))();
  DateTimeColumn get fecha => dateTime()();

  /// True si fue consumo propio o regalo: no entra en el margen.
  BoolColumn get consumoPropio =>
      boolean().withDefault(const Constant(false))();
  TextColumn get notas => text().withDefault(const Constant(''))();
}

/// Plantilla de checklist de BPM (RF-BPM-01).
@DataClassName('ChecklistBpm')
class ChecklistsBpm extends Table with ColumnasComunes {
  TextColumn get nombre => text()();

  /// `diario`, `semanal`, `mensual`.
  TextColumn get frecuencia => text().withDefault(const Constant('diario'))();

  /// Los puntos a revisar, como JSON: ["Limpieza de mesas", ...]
  TextColumn get itemsJson => text().withDefault(const Constant('[]'))();
  BoolColumn get activo => boolean().withDefault(const Constant(true))();
}

/// Un checklist de BPM ya diligenciado (RF-BPM-02).
///
/// Pasadas 24 horas no se puede editar, solo anotar: es lo que le da valor de
/// registro ante una inspección.
@DataClassName('RegistroBpm')
class RegistrosBpm extends Table with ColumnasComunes {
  TextColumn get checklistId => text().references(ChecklistsBpm, #id)();
  DateTimeColumn get fecha => dateTime()();

  /// Respuestas, como JSON: {"Limpieza de mesas": true, ...}
  TextColumn get respuestasJson => text().withDefault(const Constant('{}'))();
  TextColumn get responsable => text()();
  TextColumn get observaciones => text().withDefault(const Constant(''))();

  /// Anotaciones posteriores al cierre, como JSON con fecha y texto.
  TextColumn get anotacionesJson => text().withDefault(const Constant('[]'))();
  BoolColumn get cerrado => boolean().withDefault(const Constant(false))();
}

/// Resultado de laboratorio (RF-LAB-01).
@DataClassName('Laboratorio')
class Laboratorios extends Table with ColumnasComunes {
  TextColumn get loteId => text().nullable().references(Lotes, #id)();
  TextColumn get loteProduccionId =>
      text().nullable().references(LotesProduccion, #id)();

  /// `cadmio`, `humedad`, `microbiologia`, ...
  TextColumn get analisis => text()();
  RealColumn get valor => real()();
  TextColumn get unidad => text().withDefault(const Constant('mg/kg'))();
  RealColumn get limite => real().nullable()();
  TextColumn get laboratorio => text().withDefault(const Constant(''))();
  DateTimeColumn get fecha => dateTime()();

  /// PDF del informe, guardado como archivo adjunto.
  TextColumn get archivoId => text().nullable()();
}

/// Una alerta abierta o ya atendida (RF-ALE-01).
@DataClassName('AlertaGuardada')
class Alertas extends Table with ColumnasComunes {
  TextColumn get loteId => text().nullable().references(Lotes, #id)();
  TextColumn get loteProduccionId =>
      text().nullable().references(LotesProduccion, #id)();

  /// Código de la regla: RN-01 … RN-17.
  TextColumn get regla => text()();

  /// 0 aviso, 1 urgente, 2 bloqueante.
  IntColumn get severidad => integer().withDefault(const Constant(0))();
  TextColumn get quePaso => text()();
  TextColumn get porQueImporta => text().withDefault(const Constant(''))();
  TextColumn get queHacer => text().withDefault(const Constant(''))();
  TextColumn get correccionCodigo => text().withDefault(const Constant(''))();
  RealColumn get valorMedido => real().nullable()();
  RealColumn get valorEsperado => real().nullable()();
  DateTimeColumn get fecha => dateTime()();

  /// `abierta`, `atendida`, `descartada`.
  TextColumn get estado => text().withDefault(const Constant('abierta'))();

  /// Clave para no repetir la misma alerta una y otra vez.
  TextColumn get claveDedup => text().withDefault(const Constant(''))();
}

/// Qué corrección se aplicó a una alerta y cómo salió (RF-COR-02).
@DataClassName('CorreccionAplicada')
class CorreccionesAplicadas extends Table with ColumnasComunes {
  TextColumn get alertaId => text().references(Alertas, #id)();
  TextColumn get correccionCodigo => text()();
  DateTimeColumn get fecha => dateTime()();

  /// Qué pasó después de aplicarla. Es lo que permite aprender del error.
  TextColumn get resultado => text().withDefault(const Constant(''))();
}

/// Una foto tomada en cualquier etapa (§3.1 de la ERS).
@DataClassName('Foto')
class Fotos extends Table with ColumnasComunes {
  /// Ruta en la carpeta privada de la app.
  TextColumn get rutaLocal => text()();
  TextColumn get rutaMiniatura => text().withDefault(const Constant(''))();

  /// Identificador del archivo en Google Drive, cuando ya se subió.
  TextColumn get driveFileId => text().withDefault(const Constant(''))();
  TextColumn get etapa => text().withDefault(const Constant(''))();
  TextColumn get loteId => text().nullable().references(Lotes, #id)();
  TextColumn get loteProduccionId =>
      text().nullable().references(LotesProduccion, #id)();

  /// Lo que dijo el modelo, como JSON: {"clase":"monilia","confianza":0.83}
  TextColumn get analisisIaJson => text().withDefault(const Constant('{}'))();

  /// Lo que dijo el usuario. Si está puesto, manda sobre la IA (RF-IA-03).
  TextColumn get etiquetaUsuario => text().withDefault(const Constant(''))();

  /// Si sirve para reentrenar: el usuario la revisó y la etiqueta es de fiar
  /// (RF-PRC-09, §8.3 de la ERS).
  BoolColumn get aptaDataset => boolean().withDefault(const Constant(false))();
  RealColumn get latitud => real().nullable()();
  RealColumn get longitud => real().nullable()();
}

/// Un modelo de IA instalado (RF-IA-05, RF-IA-06).
@DataClassName('ModeloIa')
class ModelosIa extends Table with ColumnasComunes {
  /// `mazorca`, `corte`, `tostado`, `chocolate`.
  TextColumn get nombre => text()();
  TextColumn get version => text()();

  /// Las clases en orden, separadas por coma. El orden importa: el modelo
  /// devuelve índices, no nombres.
  TextColumn get clases => text()();
  RealColumn get umbralConfianza => real().withDefault(const Constant(0.6))();
  TextColumn get ruta => text()();
  BoolColumn get activo => boolean().withDefault(const Constant(false))();
  TextColumn get metadatosJson => text().withDefault(const Constant('{}'))();
}

/// Los umbrales de la tabla 5.1 que el usuario cambió (RF-CFG-03).
///
/// Solo se guardan los que difieren del valor de fábrica: así, si un valor por
/// defecto mejora en una versión nueva, lo hereda quien no lo haya tocado.
@DataClassName('UmbralGuardado')
class UmbralesGuardados extends Table with ColumnasComunes {
  TextColumn get clave => text().unique()();
  RealColumn get valor => real()();
}

/// Ajustes de la app que no son números: la tabla de la norma editada, el
/// usuario, el identificador del teléfono, las preferencias de sincronización.
///
/// Es una tabla clave/valor a propósito: así añadir un ajuste nuevo no obliga a
/// migrar el esquema, que es justo lo que pide RNF-12.
@DataClassName('Ajuste')
class Configuracion extends Table with ColumnasComunes {
  TextColumn get clave => text().unique()();
  TextColumn get valor => text().withDefault(const Constant(''))();
}

/// Historial de cambios en los registros sensibles (RNF-11).
///
/// Cubre BPM, prueba de corte y laboratorio: quién cambió qué, cuándo y cuál
/// era el valor anterior.
@DataClassName('RegistroAuditoria')
class Auditoria extends Table with ColumnasComunes {
  TextColumn get tabla => text()();
  TextColumn get registroId => text()();
  TextColumn get campo => text()();
  TextColumn get valorAnterior => text().withDefault(const Constant(''))();
  TextColumn get valorNuevo => text().withDefault(const Constant(''))();
  DateTimeColumn get fecha => dateTime()();
  TextColumn get motivo => text().withDefault(const Constant(''))();
}

/// Cola de salida hacia la nube (RF-SYN-01, RF-SYN-03).
///
/// Cada escritura encola una operación. El servicio de sincronización la vacía
/// en orden cuando hay internet, con reintentos y retroceso exponencial.
@DataClassName('OperacionSync')
class ColaSync extends Table {
  TextColumn get id => text().clientDefault(() => uuidGenerador.v4())();
  TextColumn get tabla => text()();
  TextColumn get registroId => text()();

  /// `crear`, `actualizar`, `eliminar`, `subir_foto`.
  TextColumn get operacion => text()();

  /// Copia del registro en el momento de encolar, como JSON.
  TextColumn get cargaJson => text().withDefault(const Constant('{}'))();
  DateTimeColumn get creadoEn =>
      dateTime().clientDefault(() => DateTime.now())();
  IntColumn get intentos => integer().withDefault(const Constant(0))();
  DateTimeColumn get proximoIntento => dateTime().nullable()();
  TextColumn get ultimoError => text().withDefault(const Constant(''))();

  /// Las fotos solo se suben con WiFi si el usuario dejó activa esa opción
  /// (RF-SYN-04), así que la cola necesita saber si esta operación es pesada.
  BoolColumn get esArchivo => boolean().withDefault(const Constant(false))();

  @override
  Set<Column> get primaryKey => {id};
}
