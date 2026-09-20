/// Estados por los que pasa un lote y etapas del proceso (RF-LOT-02).
library;

/// Estado del lote de grano, en el orden en que ocurre.
enum EstadoLote {
  recepcion,
  reposo,
  fermentacion,
  secado,
  almacenado,
  tostado,
  descascarillado,
  refinado,
  atemperado,
  empacado,
  vendido,
  descartado,
}

extension EstadoLoteInfo on EstadoLote {
  String get etiqueta => switch (this) {
        EstadoLote.recepcion => 'Recepción',
        EstadoLote.reposo => 'Reposo',
        EstadoLote.fermentacion => 'Fermentación',
        EstadoLote.secado => 'Secado',
        EstadoLote.almacenado => 'Almacenado',
        EstadoLote.tostado => 'Tostado',
        EstadoLote.descascarillado => 'Descascarillado',
        EstadoLote.refinado => 'Refinado',
        EstadoLote.atemperado => 'Atemperado',
        EstadoLote.empacado => 'Empacado',
        EstadoLote.vendido => 'Vendido o consumido',
        EstadoLote.descartado => 'Descartado',
      };

  /// Qué toca hacer en esta etapa, en una frase.
  String get queSigue => switch (this) {
        EstadoLote.recepcion => 'Registra las mazorcas y programa la apertura',
        EstadoLote.reposo => 'Espera los días de reposo y luego abre las mazorcas',
        EstadoLote.fermentacion => 'Mide temperatura y voltea cada día',
        EstadoLote.secado => 'Remueve el grano y mide la humedad',
        EstadoLote.almacenado => 'Inspecciona los sacos cada semana',
        EstadoLote.tostado => 'Registra temperatura, tiempo y merma',
        EstadoLote.descascarillado => 'Anota los kg de nibs y de cascarilla',
        EstadoLote.refinado => 'Controla las horas y prueba la textura',
        EstadoLote.atemperado => 'Sigue el asistente de atemperado',
        EstadoLote.empacado => 'Registra las barras y las fechas',
        EstadoLote.vendido => 'Lote cerrado',
        EstadoLote.descartado => 'Lote descartado',
      };

  /// Las etapas que no se pueden saltar sin dar un motivo (RF-LOT-03).
  bool get esObligatoria => switch (this) {
        EstadoLote.recepcion ||
        EstadoLote.fermentacion ||
        EstadoLote.secado =>
          true,
        _ => false,
      };

  /// La siguiente etapa del camino normal.
  EstadoLote? get siguiente {
    const camino = [
      EstadoLote.recepcion,
      EstadoLote.reposo,
      EstadoLote.fermentacion,
      EstadoLote.secado,
      EstadoLote.almacenado,
      EstadoLote.tostado,
      EstadoLote.descascarillado,
      EstadoLote.refinado,
      EstadoLote.atemperado,
      EstadoLote.empacado,
      EstadoLote.vendido,
    ];
    final i = camino.indexOf(this);
    if (i < 0 || i == camino.length - 1) return null;
    return camino[i + 1];
  }
}

/// Olores que se pueden registrar durante la fermentación (RF-FER-03).
enum OlorFermentacion { alcoholico, avinagrado, putrido, amoniaco, moho }

extension OlorInfo on OlorFermentacion {
  String get etiqueta => switch (this) {
        OlorFermentacion.alcoholico => 'Alcohólico',
        OlorFermentacion.avinagrado => 'Avinagrado',
        OlorFermentacion.putrido => 'Pútrido',
        OlorFermentacion.amoniaco => 'A amoniaco',
        OlorFermentacion.moho => 'A moho',
      };

  /// Qué significa ese olor en el momento del proceso.
  String get significado => switch (this) {
        OlorFermentacion.alcoholico =>
          'Normal en los primeros dos días: las levaduras están trabajando.',
        OlorFermentacion.avinagrado =>
          'Normal del día 2 al 4: ahora trabajan las bacterias del vinagre.',
        OlorFermentacion.putrido =>
          'Mala señal: la fermentación se pasó.',
        OlorFermentacion.amoniaco =>
          'Mala señal: sobrefermentación avanzada.',
        OlorFermentacion.moho =>
          'Mala señal: hay humedad mal manejada.',
      };

  /// Olores que indican que algo va mal (RN-06).
  bool get esMalaSenal => switch (this) {
        OlorFermentacion.putrido ||
        OlorFermentacion.amoniaco ||
        OlorFermentacion.moho =>
          true,
        _ => false,
      };
}

/// Método de secado (RF-SEC-01).
enum MetodoSecado { marquesina, sol, secador }

extension MetodoSecadoInfo on MetodoSecado {
  String get etiqueta => switch (this) {
        MetodoSecado.marquesina => 'Marquesina',
        MetodoSecado.sol => 'Al sol',
        MetodoSecado.secador => 'Secador',
      };
}

/// Método de atemperado (RF-ATE-01).
enum MetodoAtemperado { siembra, marmol }

extension MetodoAtemperadoInfo on MetodoAtemperado {
  String get etiqueta => switch (this) {
        MetodoAtemperado.siembra => 'Siembra',
        MetodoAtemperado.marmol => 'Mármol',
      };
}
