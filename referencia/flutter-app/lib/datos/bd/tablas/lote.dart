/// Tablas del lote de grano: desde la recepción de mazorcas hasta el saco
/// almacenado (§6 de la ERS, módulos LOT, REC, APE, FER, SEC, PRC, ALM).
library;

import 'package:drift/drift.dart';

import '../../../nucleo/modelo/etapas.dart';
import 'comunes.dart';

/// Finca de origen del cacao (RF-CFG-01).
@DataClassName('Finca')
class Fincas extends Table with ColumnasComunes {
  TextColumn get nombre => text()();
  TextColumn get provincia => text().withDefault(const Constant(''))();
  TextColumn get canton => text().withDefault(const Constant(''))();
  RealColumn get latitud => real().nullable()();
  RealColumn get longitud => real().nullable()();
  TextColumn get contacto => text().withDefault(const Constant(''))();
  TextColumn get variedad => text().withDefault(const Constant('CCN-51'))();
  TextColumn get notas => text().withDefault(const Constant(''))();
}

/// Equipo del taller: fermentador, marquesina, horno, melanger, moldes
/// (RF-CFG-02). Las medidas importan: un fermentador demasiado grande para
/// poca baba enfría la masa.
@DataClassName('Equipo')
class Equipos extends Table with ColumnasComunes {
  TextColumn get tipo => text()();
  TextColumn get nombre => text()();
  RealColumn get largoCm => real().nullable()();
  RealColumn get anchoCm => real().nullable()();
  RealColumn get altoCm => real().nullable()();
  TextColumn get material => text().withDefault(const Constant(''))();
  RealColumn get capacidadKg => real().nullable()();
  TextColumn get notas => text().withDefault(const Constant(''))();
}

/// Lote de grano. Es la unidad de trazabilidad (RF-LOT-01).
@DataClassName('Lote')
class Lotes extends Table with ColumnasComunes {
  /// Código L-AAAA-NNN, único y visible para el usuario.
  TextColumn get codigo => text().unique()();
  TextColumn get fincaId => text().nullable().references(Fincas, #id)();
  TextColumn get variedad => text().withDefault(const Constant('CCN-51'))();
  DateTimeColumn get fechaCosecha => dateTime().nullable()();
  DateTimeColumn get fechaLlegada => dateTime()();
  IntColumn get estado => intEnum<EstadoLote>()
      .withDefault(const Constant(0))();
  TextColumn get notas => text().withDefault(const Constant(''))();

  /// Motivo si se saltó una etapa obligatoria (RF-LOT-03).
  TextColumn get motivoSalto => text().withDefault(const Constant(''))();

  /// Queda bloqueado para venta si el cadmio supera el límite (RN-15).
  BoolColumn get ventaBloqueada =>
      boolean().withDefault(const Constant(false))();
  TextColumn get motivoBloqueo => text().withDefault(const Constant(''))();
}

/// Recepción de mazorcas (RF-REC-01).
@DataClassName('Recepcion')
class Recepciones extends Table with ColumnasComunes {
  TextColumn get loteId => text().references(Lotes, #id)();
  IntColumn get sacos => integer().withDefault(const Constant(0))();
  IntColumn get mazorcasTotal => integer().withDefault(const Constant(0))();
  RealColumn get pesoKg => real().withDefault(const Constant(0))();
  IntColumn get mazorcasSanas => integer().withDefault(const Constant(0))();
  IntColumn get mazorcasMonilia => integer().withDefault(const Constant(0))();
  IntColumn get mazorcasFitoftora => integer().withDefault(const Constant(0))();
  IntColumn get mazorcasOtro => integer().withDefault(const Constant(0))();
  IntColumn get mazorcasDescartadas =>
      integer().withDefault(const Constant(0))();
  TextColumn get motivoDescarte => text().withDefault(const Constant(''))();
  IntColumn get diasReposo => integer().withDefault(const Constant(4))();
  DateTimeColumn get fechaAperturaPlan => dateTime().nullable()();
}

/// Apertura de las mazorcas y paso de la baba al fermentador (RF-APE-01).
@DataClassName('Apertura')
class Aperturas extends Table with ColumnasComunes {
  TextColumn get loteId => text().references(Lotes, #id)();
  DateTimeColumn get fecha => dateTime()();
  IntColumn get mazorcasAbiertas => integer().withDefault(const Constant(0))();
  RealColumn get kgBaba => real().withDefault(const Constant(0))();
  RealColumn get kgCascaraAnadida => real().withDefault(const Constant(0))();
  TextColumn get notas => text().withDefault(const Constant(''))();
}

/// Una fermentación en curso o terminada (RF-FER-01).
@DataClassName('Fermentacion')
class Fermentaciones extends Table with ColumnasComunes {
  TextColumn get loteId => text().references(Lotes, #id)();
  TextColumn get equipoId => text().nullable().references(Equipos, #id)();
  DateTimeColumn get inicio => dateTime()();
  DateTimeColumn get fin => dateTime().nullable()();
  RealColumn get masaKg => real().withDefault(const Constant(0))();
  TextColumn get aislamiento => text().withDefault(const Constant(''))();
  TextColumn get notas => text().withDefault(const Constant(''))();
}

/// Una lectura de temperatura, pH y olor durante la fermentación (RF-FER-02/03).
@DataClassName('LecturaFermentacion')
class LecturasFermentacion extends Table with ColumnasComunes {
  TextColumn get fermentacionId => text().references(Fermentaciones, #id)();
  DateTimeColumn get fechaHora => dateTime()();
  RealColumn get tempC => real().nullable()();
  RealColumn get ph => real().nullable()();
  IntColumn get olor => intEnum<OlorFermentacion>().nullable()();

  /// `manual` o `sensor`: importa para saber cuánto confiar en la lectura.
  TextColumn get fuente => text().withDefault(const Constant('manual'))();
  TextColumn get fotoId => text().nullable()();
  TextColumn get notas => text().withDefault(const Constant(''))();
}

/// Un volteo registrado (RF-FER-04).
@DataClassName('Volteo')
class Volteos extends Table with ColumnasComunes {
  TextColumn get fermentacionId => text().references(Fermentaciones, #id)();
  DateTimeColumn get fechaHora => dateTime()();
  TextColumn get fotoId => text().nullable()();
}

/// El secado del grano (RF-SEC-01).
@DataClassName('Secado')
class Secados extends Table with ColumnasComunes {
  TextColumn get loteId => text().references(Lotes, #id)();
  IntColumn get metodo =>
      intEnum<MetodoSecado>().withDefault(const Constant(0))();
  DateTimeColumn get inicio => dateTime()();
  DateTimeColumn get fin => dateTime().nullable()();
  RealColumn get kgSeco => real().nullable()();
  TextColumn get notas => text().withDefault(const Constant(''))();
}

/// Una jornada de secado (RF-SEC-02/03/04).
@DataClassName('LecturaSecado')
class LecturasSecado extends Table with ColumnasComunes {
  TextColumn get secadoId => text().references(Secados, #id)();
  DateTimeColumn get fecha => dateTime()();
  RealColumn get humedadGrano => real().nullable()();

  /// Resultado de la "prueba del puñado" cuando no hay medidor (RF-SEC-03).
  TextColumn get pruebaPunado => text().withDefault(const Constant(''))();
  RealColumn get tempAmbiente => real().nullable()();
  RealColumn get hrAmbiente => real().nullable()();
  RealColumn get espesorCm => real().nullable()();
  IntColumn get remociones => integer().withDefault(const Constant(0))();
  BoolColumn get moho => boolean().withDefault(const Constant(false))();
  TextColumn get fotoId => text().nullable()();
}

/// Una prueba de corte (RF-PRC-01 a RF-PRC-10).
@DataClassName('PruebaCorte')
class PruebasCorte extends Table with ColumnasComunes {
  TextColumn get loteId => text().references(Lotes, #id)();
  DateTimeColumn get fecha => dateTime()();
  TextColumn get perfilNorma =>
      text().withDefault(const Constant('ccn51_referencia'))();
  IntColumn get granos => integer().withDefault(const Constant(0))();

  /// Conteo por clase, como JSON: {"bien_fermentado": 70, ...}
  TextColumn get conteosJson => text().withDefault(const Constant('{}'))();

  /// Porcentajes calculados, como JSON. Se guardan para no recalcularlos y
  /// para que el reporte muestre exactamente lo que vio el usuario ese día.
  TextColumn get porcentajesJson => text().withDefault(const Constant('{}'))();
  TextColumn get resultado => text().withDefault(const Constant(''))();
  BoolColumn get conforme => boolean().withDefault(const Constant(false))();
  RealColumn get peso100g => real().nullable()();

  /// Versión del modelo M2 que se usó, o vacío si fue conteo manual (RF-IA-05).
  TextColumn get modeloVersion => text().withDefault(const Constant(''))();

  /// Si es una prueba parcial del día 5 con menos de 100 granos (RF-FER-06).
  BoolColumn get esParcial => boolean().withDefault(const Constant(false))();
  TextColumn get fotoId => text().nullable()();
}

/// Un saco de grano seco almacenado, con su propio QR (RF-ALM-01).
@DataClassName('Saco')
class Sacos extends Table with ColumnasComunes {
  TextColumn get loteId => text().references(Lotes, #id)();
  TextColumn get codigoQr => text().unique()();
  RealColumn get pesoKg => real().withDefault(const Constant(0))();
  TextColumn get ubicacion => text().withDefault(const Constant(''))();

  /// `almacenado`, `en_produccion`, `consumido`.
  TextColumn get estado => text().withDefault(const Constant('almacenado'))();
}

/// Inspección periódica del almacén (RF-ALM-02).
@DataClassName('InspeccionAlmacen')
class InspeccionesAlmacen extends Table with ColumnasComunes {
  TextColumn get loteId => text().nullable().references(Lotes, #id)();
  DateTimeColumn get fecha => dateTime()();
  RealColumn get humedadRelativa => real().nullable()();
  RealColumn get temperatura => real().nullable()();
  BoolColumn get plagas => boolean().withDefault(const Constant(false))();
  BoolColumn get moho => boolean().withDefault(const Constant(false))();
  TextColumn get olor => text().withDefault(const Constant(''))();
  TextColumn get fotoId => text().nullable()();
  TextColumn get notas => text().withDefault(const Constant(''))();
}
