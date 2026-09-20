/// Columnas que lleva TODA tabla de la app (§6 de la ERS).
///
/// Están pensadas para que la sincronización funcione sin un servidor que
/// asigne identificadores y sin perder información al borrar:
///
///   id             UUID generado en el teléfono; dos teléfonos sin conexión
///                  nunca chocan
///   creadoEn       cuándo se registró
///   modificadoEn   base de la resolución de conflictos "gana el último"
///   usuarioId      quién lo hizo (auditoría, RNF-11)
///   dispositivoId  desde qué teléfono, para rastrear el origen de un cambio
///   estadoSync     en qué punto va la subida a la nube
///   eliminado      borrado LÓGICO: la fila se queda para poder sincronizar el
///                  borrado a los demás teléfonos y para la auditoría
library;

import 'package:drift/drift.dart';
import 'package:uuid/uuid.dart';

const Uuid uuidGenerador = Uuid();

/// En qué punto de la subida a la nube está un registro (RF-SYN-01).
enum EstadoSync {
  /// Guardado en el teléfono, aún no subido.
  pendiente,

  /// Se está subiendo ahora mismo.
  enviando,

  /// Confirmado en la nube.
  sincronizado,

  /// Falló la subida; se reintentará.
  error,
}

extension EstadoSyncTexto on EstadoSync {
  String get etiqueta => switch (this) {
        EstadoSync.pendiente => 'Pendiente',
        EstadoSync.enviando => 'Subiendo',
        EstadoSync.sincronizado => 'Sincronizado',
        EstadoSync.error => 'Con error',
      };
}

/// Mixin con las columnas comunes. Toda tabla del dominio lo usa.
mixin ColumnasComunes on Table {
  TextColumn get id => text().clientDefault(() => uuidGenerador.v4())();

  DateTimeColumn get creadoEn =>
      dateTime().clientDefault(() => DateTime.now())();

  DateTimeColumn get modificadoEn =>
      dateTime().clientDefault(() => DateTime.now())();

  TextColumn get usuarioId => text().withDefault(const Constant('local'))();

  TextColumn get dispositivoId => text().withDefault(const Constant(''))();

  IntColumn get estadoSync =>
      intEnum<EstadoSync>().withDefault(const Constant(0))();

  BoolColumn get eliminado => boolean().withDefault(const Constant(false))();

  @override
  Set<Column> get primaryKey => {id};
}
