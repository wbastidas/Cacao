/// Tablas del lote de producción: del grano seco a la barra empacada
/// (§6 de la ERS, módulos TOS, DES, REF, ATE, EMP).
library;

import 'package:drift/drift.dart';

import '../../../nucleo/modelo/etapas.dart';
import 'comunes.dart';
import 'lote.dart';

/// Una tanda de chocolate, hecha con uno o varios lotes de grano (RF-LOT-05).
@DataClassName('LoteProduccion')
class LotesProduccion extends Table with ColumnasComunes {
  /// Código P-AAAA-NNN.
  TextColumn get codigo => text().unique()();
  DateTimeColumn get fecha => dateTime()();
  RealColumn get porcentajeCacao => real().withDefault(const Constant(90))();
  RealColumn get kgChocolate => real().nullable()();

  /// `en_proceso`, `terminado`, `descartado`.
  TextColumn get estado => text().withDefault(const Constant('en_proceso'))();
  TextColumn get notas => text().withDefault(const Constant(''))();
}

/// Qué lotes de grano y cuántos kg entraron en una tanda de producción.
///
/// Esta tabla es la que hace posible la trazabilidad hacia atrás: escaneando
/// el QR de una barra se llega a la finca de origen (RF-LOT-04).
@DataClassName('LoteProduccionOrigen')
class LotesProduccionOrigen extends Table with ColumnasComunes {
  TextColumn get loteProduccionId => text().references(LotesProduccion, #id)();
  TextColumn get loteId => text().references(Lotes, #id)();
  RealColumn get kgUsados => real()();
}

/// Tostado del grano (RF-TOS-01).
@DataClassName('Tostado')
class Tostados extends Table with ColumnasComunes {
  TextColumn get loteProduccionId => text().references(LotesProduccion, #id)();
  TextColumn get equipoId => text().nullable().references(Equipos, #id)();
  DateTimeColumn get fecha => dateTime()();
  RealColumn get kgEntrada => real().withDefault(const Constant(0))();
  RealColumn get kgSalida => real().nullable()();

  /// Perfil por tramos, como JSON: [{"minutos":10,"tempC":140}, ...]
  TextColumn get perfilJson => text().withDefault(const Constant('[]'))();
  RealColumn get tempC => real().nullable()();
  IntColumn get minutos => integer().nullable()();

  /// Grado que estimó el modelo M3, si está instalado (RF-TOS-03).
  TextColumn get gradoIa => text().withDefault(const Constant(''))();
  RealColumn get confianzaIa => real().nullable()();

  /// Lo que dijo el usuario. Si difiere de la IA, manda esto (RF-IA-03).
  TextColumn get gradoUsuario => text().withDefault(const Constant(''))();
  TextColumn get modeloVersion => text().withDefault(const Constant(''))();
  TextColumn get fotoId => text().nullable()();

  /// Nombre de la receta de tostado guardada, para comparar lotes (RF-TOS-05).
  TextColumn get recetaNombre => text().withDefault(const Constant(''))();
}

/// Separación de nibs y cascarilla (RF-DES-01).
@DataClassName('Descascarillado')
class Descascarillados extends Table with ColumnasComunes {
  TextColumn get loteProduccionId => text().references(LotesProduccion, #id)();
  DateTimeColumn get fecha => dateTime()();
  RealColumn get kgNibs => real().withDefault(const Constant(0))();
  RealColumn get kgCascarilla => real().withDefault(const Constant(0))();
  TextColumn get notas => text().withDefault(const Constant(''))();
}

/// Refinado y conchado en el melanger (RF-REF-01 a RF-REF-04).
@DataClassName('Refinado')
class Refinados extends Table with ColumnasComunes {
  TextColumn get loteProduccionId => text().references(LotesProduccion, #id)();
  DateTimeColumn get inicio => dateTime()();
  DateTimeColumn get fin => dateTime().nullable()();
  RealColumn get horas => real().nullable()();
  RealColumn get nibsKg => real().withDefault(const Constant(0))();
  RealColumn get azucarKg => real().withDefault(const Constant(0))();
  RealColumn get mantecaKg => real().withDefault(const Constant(0))();
  RealColumn get lecitinaKg => real().withDefault(const Constant(0))();
  DateTimeColumn get momentoAzucar => dateTime().nullable()();
  RealColumn get tempC => real().nullable()();

  /// Evaluación sensorial 1-5, como JSON:
  /// {"acidez":2,"amargor":4,"astringencia":2,"textura":5}
  TextColumn get sensorialJson => text().withDefault(const Constant('{}'))();
  TextColumn get notas => text().withDefault(const Constant(''))();
}

/// Atemperado y moldeado (RF-ATE-01 a RF-ATE-03).
@DataClassName('Atemperado')
class Atemperados extends Table with ColumnasComunes {
  TextColumn get loteProduccionId => text().references(LotesProduccion, #id)();
  DateTimeColumn get fecha => dateTime()();
  IntColumn get metodo =>
      intEnum<MetodoAtemperado>().withDefault(const Constant(0))();

  /// Las tres temperaturas del proceso, como JSON:
  /// {"fundido":48,"enfriado":27,"trabajo":31.5}
  TextColumn get tempsJson => text().withDefault(const Constant('{}'))();
  RealColumn get tempCuarto => real().nullable()();
  RealColumn get hrCuarto => real().nullable()();

  /// Prueba del papel: ¿endureció con brillo en ~3 minutos? (RF-ATE-03)
  BoolColumn get pruebaPapel => boolean().nullable()();

  /// Resultado del modelo M4 sobre la foto de las barras (RF-ATE-04).
  TextColumn get resultadoIa => text().withDefault(const Constant(''))();
  RealColumn get confianzaIa => real().nullable()();
  TextColumn get resultadoUsuario => text().withDefault(const Constant(''))();
  TextColumn get modeloVersion => text().withDefault(const Constant(''))();
  TextColumn get fotoId => text().nullable()();

  /// Días transcurridos si es una inspección posterior (RF-ATE-05: 7, 30, 90).
  IntColumn get diasInspeccion => integer().nullable()();
}

/// Empaque y etiquetado de las barras (RF-EMP-01).
@DataClassName('Empaque')
class Empaques extends Table with ColumnasComunes {
  TextColumn get loteProduccionId => text().references(LotesProduccion, #id)();
  IntColumn get barras => integer().withDefault(const Constant(0))();
  RealColumn get pesoUnitarioG => real().withDefault(const Constant(50))();
  DateTimeColumn get fechaElaboracion => dateTime()();
  DateTimeColumn get fechaVencimiento => dateTime()();
  IntColumn get vidaUtilMeses => integer().withDefault(const Constant(12))();

  /// Datos de la etiqueta, como JSON: ingredientes, tabla nutricional,
  /// semáforo y número de notificación sanitaria (RF-EMP-02).
  TextColumn get etiquetaJson => text().withDefault(const Constant('{}'))();
  TextColumn get codigoQr => text().withDefault(const Constant(''))();
}
