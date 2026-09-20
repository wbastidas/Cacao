/// Motor de reglas de negocio (RF-ALE-01, tabla 5.1 de la ERS).
///
/// Corre DENTRO del teléfono, sin internet y sin Cloud Functions: así funciona
/// en la finca sin señal y se puede operar con el plan gratuito de Firebase.
///
/// Es código puro: recibe datos, devuelve alertas. No toca la base ni la red, y
/// por eso se puede probar entero en milisegundos.
library;

import '../modelo/etapas.dart';
import 'alerta.dart';
import 'umbrales.dart';

/// Datos de un lote en reposo, para evaluar RN-01.
class ContextoReposo {
  const ContextoReposo({required this.diasDesdeLlegada, this.abierto = false});
  final int diasDesdeLlegada;
  final bool abierto;
}

/// Datos de la apertura, para RN-02 y el aviso de rendimiento.
class ContextoApertura {
  const ContextoApertura({
    required this.mazorcasAbiertas,
    required this.kgBaba,
    this.kgCascaraAnadida = 0,
  });
  final int mazorcasAbiertas;
  final double kgBaba;
  final double kgCascaraAnadida;
}

/// Una lectura de fermentación, para RN-03 a RN-07.
class ContextoFermentacion {
  const ContextoFermentacion({
    required this.horasDesdeInicio,
    this.temperaturaC,
    this.horasDesdeUltimoVolteo,
    this.olor,
    this.ph,
  });

  final double horasDesdeInicio;
  final double? temperaturaC;
  final double? horasDesdeUltimoVolteo;
  final OlorFermentacion? olor;
  final double? ph;

  double get diasDesdeInicio => horasDesdeInicio / 24.0;
}

/// Datos del secado, para RN-08 y RN-09.
class ContextoSecado {
  const ContextoSecado({
    this.humedadGranoPct,
    this.mohoVisible = false,
    this.cerrandoEtapa = false,
  });
  final double? humedadGranoPct;
  final bool mohoVisible;

  /// Solo se bloquea el paso a almacenamiento al cerrar el secado.
  final bool cerrandoEtapa;
}

/// Datos de una inspección de almacén, para RN-11.
class ContextoAlmacen {
  const ContextoAlmacen({
    this.humedadRelativaPct,
    this.mohoVisible = false,
    this.plagas = false,
    this.diasDesdeUltimaInspeccion,
  });
  final double? humedadRelativaPct;
  final bool mohoVisible;
  final bool plagas;
  final int? diasDesdeUltimaInspeccion;
}

/// Datos del tostado y el descascarillado, para RN-12.
class ContextoTostado {
  const ContextoTostado({
    required this.kgEntrada,
    required this.kgSalida,
    this.kgNibs,
    this.kgCascarilla,
  });
  final double kgEntrada;
  final double kgSalida;
  final double? kgNibs;
  final double? kgCascarilla;

  double? get mermaPct =>
      kgEntrada <= 0 ? null : 100.0 * (kgEntrada - kgSalida) / kgEntrada;

  double? get cascarillaPct {
    final nibs = kgNibs, cascara = kgCascarilla;
    if (nibs == null || cascara == null) return null;
    final total = nibs + cascara;
    return total <= 0 ? null : 100.0 * cascara / total;
  }
}

/// Datos del atemperado, para RN-13 y RN-14.
class ContextoAtemperado {
  const ContextoAtemperado({
    this.tempCuartoC,
    this.humedadCuartoPct,
    this.tempTrabajoC,
  });
  final double? tempCuartoC;
  final double? humedadCuartoPct;
  final double? tempTrabajoC;
}

/// Un resultado de laboratorio, para RN-15.
class ContextoLaboratorio {
  const ContextoLaboratorio({
    required this.analisis,
    required this.valor,
    required this.unidad,
  });
  final String analisis;
  final double valor;
  final String unidad;
}

/// El motor. Se construye con los umbrales vigentes del usuario.
class MotorReglas {
  const MotorReglas(this.umbrales);

  final Umbrales umbrales;

  // ------------------------------------------------------------ reposo

  /// RN-01: días de reposo fuera del rango configurado.
  List<Alerta> evaluarReposo(ContextoReposo c) {
    if (c.abierto) return const [];
    final alerta = umbrales.entero('reposo_dias_alerta');
    final maximo = umbrales.entero('reposo_dias_max');

    if (c.diasDesdeLlegada > alerta) {
      return [
        Alerta(
          regla: 'RN-01',
          severidad: Severidad.urgente,
          quePaso: 'Las mazorcas llevan ${c.diasDesdeLlegada} días sin abrirse',
          porQueImporta:
              'Pasados $maximo días la mazorca empieza a germinar o a pudrirse '
              'por dentro, y eso se lleva parte del lote.',
          queHacer: 'Abre las mazorcas hoy mismo.',
          valorMedido: c.diasDesdeLlegada.toDouble(),
          valorEsperado: maximo.toDouble(),
        ),
      ];
    }
    if (c.diasDesdeLlegada >= maximo) {
      return [
        Alerta(
          regla: 'RN-01',
          severidad: Severidad.aviso,
          quePaso: 'Hoy toca abrir las mazorcas',
          porQueImporta:
              'Llevan ${c.diasDesdeLlegada} días de reposo, que es el máximo '
              'que configuraste.',
          queHacer: 'Abre las mazorcas y registra los kg de baba.',
          valorMedido: c.diasDesdeLlegada.toDouble(),
          valorEsperado: maximo.toDouble(),
        ),
      ];
    }
    return const [];
  }

  // ------------------------------------------------------------ apertura

  /// RN-02: poca baba para que la fermentación arranque.
  /// Y aviso si el rendimiento por mazorca se aleja de lo esperado (RN-17).
  List<Alerta> evaluarApertura(ContextoApertura c) {
    final alertas = <Alerta>[];
    final masaMinima = umbrales['baba_masa_minima_kg'];
    final masaTotal = c.kgBaba + c.kgCascaraAnadida;

    if (masaTotal < masaMinima) {
      final faltan = masaMinima - masaTotal;
      alertas.add(
        Alerta(
          regla: 'RN-02',
          severidad: Severidad.urgente,
          quePaso: 'Hay poca masa para fermentar '
              '(${masaTotal.toStringAsFixed(1)} kg)',
          porQueImporta:
              'Por debajo de ${masaMinima.toStringAsFixed(0)} kg el montón no '
              'conserva el calor y la fermentación se queda fría, que es la '
              'causa más común de granos pizarrosos y violetas.',
          queHacer: 'Añade unos ${faltan.toStringAsFixed(1)} kg de cáscara de '
              'mazorca troceada y refuerza el aislamiento.',
          correccion: 'C-01',
          valorMedido: masaTotal,
          valorEsperado: masaMinima,
        ),
      );
    }

    if (c.mazorcasAbiertas > 0) {
      final esperadoPorMazorca = umbrales['baba_por_mazorca_kg'];
      final real = c.kgBaba / c.mazorcasAbiertas;
      final desvio = umbrales['rendimiento_desvio_pct'];
      final diferencia =
          100.0 * (real - esperadoPorMazorca).abs() / esperadoPorMazorca;
      if (diferencia > desvio) {
        alertas.add(
          Alerta(
            regla: 'RN-17',
            severidad: Severidad.aviso,
            quePaso: 'El rendimiento por mazorca no cuadra '
                '(${real.toStringAsFixed(2)} kg)',
            porQueImporta:
                'Lo normal en CCN-51 son unos '
                '${esperadoPorMazorca.toStringAsFixed(2)} kg por mazorca. Una '
                'diferencia así casi siempre es un error al anotar el peso o el '
                'número de mazorcas.',
            queHacer: 'Revisa el peso de la baba y el conteo de mazorcas.',
            valorMedido: real,
            valorEsperado: esperadoPorMazorca,
          ),
        );
      }
    }
    return alertas;
  }

  // ------------------------------------------------------------ fermentación

  /// RN-03 a RN-07: volteos, temperatura, olor y duración.
  List<Alerta> evaluarFermentacion(ContextoFermentacion c) {
    final alertas = <Alerta>[];

    // RN-03: sin volteo registrado en demasiadas horas.
    final horasVolteo = umbrales['volteo_horas_alerta'];
    final desdeVolteo = c.horasDesdeUltimoVolteo;
    if (desdeVolteo != null &&
        desdeVolteo > horasVolteo &&
        c.horasDesdeInicio > umbrales['volteo_horas']) {
      alertas.add(
        Alerta(
          regla: 'RN-03',
          severidad: Severidad.urgente,
          quePaso: 'Llevas ${desdeVolteo.toStringAsFixed(0)} horas sin voltear',
          porQueImporta:
              'Sin voltear, el grano de afuera queda frío y el de adentro se '
              'recalienta. El lote fermenta disparejo.',
          queHacer: 'Voltea la masa ahora y registra el volteo.',
          valorMedido: desdeVolteo,
          valorEsperado: horasVolteo,
        ),
      );
    }

    final temp = c.temperaturaC;
    if (temp != null) {
      // RN-04: fermentación fría pasado el arranque.
      final tempMinima = umbrales['ferm_temp_minima_c'];
      final desdeHoras = umbrales['ferm_temp_minima_desde_h'];
      if (c.horasDesdeInicio >= desdeHoras && temp < tempMinima) {
        alertas.add(
          Alerta(
            regla: 'RN-04',
            severidad: Severidad.urgente,
            quePaso: 'La fermentación está fría '
                '(${temp.toStringAsFixed(1)} °C)',
            porQueImporta:
                'A las ${c.horasDesdeInicio.toStringAsFixed(0)} horas la masa '
                'debería pasar de ${tempMinima.toStringAsFixed(0)} °C. Fría, el '
                'grano no desarrolla sabor y sale pizarroso o violeta.',
            queHacer: 'Añade cáscara, tapa bien y refuerza el aislamiento.',
            correccion: 'C-01',
            valorMedido: temp,
            valorEsperado: tempMinima,
          ),
        );
      }

      // RN-05: demasiado caliente.
      final tempMaxima = umbrales['ferm_temp_maxima_c'];
      if (temp > tempMaxima) {
        alertas.add(
          Alerta(
            regla: 'RN-05',
            severidad: Severidad.urgente,
            quePaso: 'La masa está muy caliente '
                '(${temp.toStringAsFixed(1)} °C)',
            porQueImporta:
                'Por encima de ${tempMaxima.toStringAsFixed(0)} °C el grano se '
                'cocina y aparecen sabores a quemado que no se quitan después.',
            queHacer: 'Voltea y destapa un rato para que baje la temperatura.',
            valorMedido: temp,
            valorEsperado: tempMaxima,
          ),
        );
      }
    }

    // RN-06: olor de sobrefermentación.
    final olor = c.olor;
    if (olor != null && olor.esMalaSenal) {
      alertas.add(
        Alerta(
          regla: 'RN-06',
          severidad: Severidad.urgente,
          quePaso: 'Olor ${olor.etiqueta.toLowerCase()} en el fermentador',
          porQueImporta: olor.significado,
          queHacer: 'Pasa el lote a secado de inmediato.',
          correccion: olor == OlorFermentacion.moho ? 'C-03' : 'C-05',
        ),
      );
    }

    // RN-07: fermentación demasiado larga.
    final diasMaximos = umbrales['ferm_duracion_maxima_dias'];
    if (c.diasDesdeInicio > diasMaximos) {
      alertas.add(
        Alerta(
          regla: 'RN-07',
          severidad: Severidad.urgente,
          quePaso: 'La fermentación lleva '
              '${c.diasDesdeInicio.toStringAsFixed(1)} días',
          porQueImporta:
              'Más de ${diasMaximos.toStringAsFixed(0)} días es '
              'sobrefermentación: aparece olor a amoniaco y el chocolate sale '
              'con sabores desagradables.',
          queHacer: 'Cierra la fermentación y pasa el lote a secado.',
          correccion: 'C-05',
          valorMedido: c.diasDesdeInicio,
          valorEsperado: diasMaximos,
        ),
      );
    }
    return alertas;
  }

  // ------------------------------------------------------------ secado

  /// RN-08 y RN-09: humedad al cerrar y moho visible.
  List<Alerta> evaluarSecado(ContextoSecado c) {
    final alertas = <Alerta>[];

    final humedad = c.humedadGranoPct;
    final maxima = umbrales['humedad_maxima_pct'];
    if (c.cerrandoEtapa && humedad != null && humedad > maxima) {
      alertas.add(
        Alerta(
          regla: 'RN-08',
          severidad: Severidad.bloqueante,
          quePaso: 'El grano tiene ${humedad.toStringAsFixed(1)} % de humedad',
          porQueImporta:
              'Por encima de ${maxima.toStringAsFixed(0)} % el grano cría moho '
              'en el almacén y el lote se pierde en semanas.',
          queHacer: 'Sigue secando. Si aun así quieres almacenarlo, tendrás que '
              'confirmarlo y quedará registrado.',
          correccion: 'C-03',
          valorMedido: humedad,
          valorEsperado: maxima,
        ),
      );
    }

    if (c.mohoVisible) {
      alertas.add(
        const Alerta(
          regla: 'RN-09',
          severidad: Severidad.urgente,
          quePaso: 'Hay moho visible en el grano',
          porQueImporta:
              'El moho da un sabor que no se quita en ninguna etapa posterior, '
              'y la norma casi no admite granos mohosos.',
          queHacer: 'Separa los granos afectados y seca en capa más delgada.',
          correccion: 'C-03',
        ),
      );
    }
    return alertas;
  }

  // ------------------------------------------------------------ prueba de corte

  /// RN-10: el resultado de la prueba de corte no cumple la norma.
  ///
  /// Recibe el resultado ya calculado por [CalificadorCorte] para no duplicar
  /// el cálculo: aquí solo se decide qué corrección sugerir.
  List<Alerta> evaluarPruebaCorte({
    required bool conforme,
    required String resultado,
    required List<String> fallas,
    required double pctVioleta,
    required double pctPizarroso,
    required double pctMohoso,
  }) {
    if (conforme) return const [];

    // La corrección se elige por el defecto que más pesa, no por el primero.
    String? correccion;
    if (pctPizarroso >= pctVioleta && pctPizarroso >= pctMohoso) {
      correccion = 'C-04';
    } else if (pctVioleta >= pctMohoso) {
      correccion = 'C-02';
    } else {
      correccion = 'C-03';
    }

    return [
      Alerta(
        regla: 'RN-10',
        severidad: Severidad.aviso,
        quePaso: 'La prueba de corte dio "$resultado"',
        porQueImporta: fallas.isEmpty
            ? 'El lote no alcanza los requisitos de la norma.'
            : 'No cumple: ${fallas.join('; ')}.',
        queHacer: 'Revisa la corrección sugerida para el próximo lote.',
        correccion: correccion,
      ),
    ];
  }

  // ------------------------------------------------------------ almacén

  /// RN-11: humedad ambiente alta, moho o plagas en el almacén.
  List<Alerta> evaluarAlmacen(ContextoAlmacen c) {
    final alertas = <Alerta>[];
    final hr = c.humedadRelativaPct;
    final maxima = umbrales['almacen_hr_maxima_pct'];

    if (hr != null && hr > maxima) {
      alertas.add(
        Alerta(
          regla: 'RN-11',
          severidad: Severidad.aviso,
          quePaso: 'El almacén está a ${hr.toStringAsFixed(0)} % de humedad',
          porQueImporta:
              'Por encima de ${maxima.toStringAsFixed(0)} % el grano seco vuelve '
              'a tomar agua del aire y aparece moho.',
          queHacer: 'Ventila el almacén o usa deshumidificador. Revisa que los '
              'sacos no toquen el piso ni las paredes.',
          correccion: 'C-03',
          valorMedido: hr,
          valorEsperado: maxima,
        ),
      );
    }
    if (c.mohoVisible) {
      alertas.add(
        const Alerta(
          regla: 'RN-11',
          severidad: Severidad.urgente,
          quePaso: 'Hay moho en los sacos almacenados',
          porQueImporta: 'El moho se extiende de un saco a otro.',
          queHacer: 'Separa los sacos afectados hoy mismo.',
          correccion: 'C-03',
        ),
      );
    }
    if (c.plagas) {
      alertas.add(
        const Alerta(
          regla: 'RN-11',
          severidad: Severidad.urgente,
          quePaso: 'Se detectaron plagas en el almacén',
          porQueImporta:
              'Las plagas dañan el grano y son un incumplimiento de BPM que '
              'ARCSA observa en inspección.',
          queHacer: 'Aplica el control de plagas y regístralo en BPM.',
        ),
      );
    }
    return alertas;
  }

  // ------------------------------------------------------------ tostado

  /// RN-12: merma de tostado fuera de rango. Y desvío de cascarilla.
  List<Alerta> evaluarTostado(ContextoTostado c) {
    final alertas = <Alerta>[];
    final merma = c.mermaPct;
    final minima = umbrales['tostado_merma_min_pct'];
    final maxima = umbrales['tostado_merma_max_pct'];

    if (merma != null && (merma < minima || merma > maxima)) {
      final alto = merma > maxima;
      alertas.add(
        Alerta(
          regla: 'RN-12',
          severidad: Severidad.aviso,
          quePaso: 'La merma del tostado fue ${merma.toStringAsFixed(1)} %',
          porQueImporta: alto
              ? 'Lo normal es entre ${minima.toStringAsFixed(0)} y '
                  '${maxima.toStringAsFixed(0)} %. Una merma así de alta suele '
                  'significar que el tostado fue excesivo.'
              : 'Lo normal es entre ${minima.toStringAsFixed(0)} y '
                  '${maxima.toStringAsFixed(0)} %. Una merma tan baja suele ser '
                  'un error de balanza o un tostado muy corto.',
          queHacer: alto
              ? 'Baja la temperatura o el tiempo en el próximo tostado.'
              : 'Revisa la balanza y el perfil de tostado.',
          valorMedido: merma,
          valorEsperado: alto ? maxima : minima,
        ),
      );
    }

    final cascarilla = c.cascarillaPct;
    if (cascarilla != null) {
      final esperada = umbrales['cascarilla_esperada_pct'];
      final desvio = umbrales['cascarilla_desvio_pct'];
      if ((cascarilla - esperada).abs() > desvio) {
        final alta = cascarilla > esperada;
        alertas.add(
          Alerta(
            regla: 'RN-12',
            severidad: Severidad.aviso,
            quePaso: 'La cascarilla fue ${cascarilla.toStringAsFixed(1)} %',
            porQueImporta: alta
                ? 'Lo esperado es ${esperada.toStringAsFixed(0)} %. Tanta '
                    'cascarilla suele significar que se están yendo nibs con ella.'
                : 'Lo esperado es ${esperada.toStringAsFixed(0)} %. Tan poca '
                    'cascarilla suele significar que quedan nibs sucios.',
            queHacer: 'Ajusta el ventilador del descascarillador.',
            valorMedido: cascarilla,
            valorEsperado: esperada,
          ),
        );
      }
    }
    return alertas;
  }

  // ------------------------------------------------------------ atemperado

  /// RN-13 y RN-14: cuarto caliente o húmedo, temperatura de trabajo fuera.
  List<Alerta> evaluarAtemperado(ContextoAtemperado c) {
    final alertas = <Alerta>[];

    final temp = c.tempCuartoC;
    final tempMax = umbrales['cuarto_temp_maxima_c'];
    if (temp != null && temp > tempMax) {
      alertas.add(
        Alerta(
          regla: 'RN-13',
          severidad: Severidad.aviso,
          quePaso: 'El cuarto está a ${temp.toStringAsFixed(1)} °C',
          porQueImporta:
              'Por encima de ${tempMax.toStringAsFixed(0)} °C el chocolate no '
              'cristaliza bien y sale con fat bloom (velo grisáceo).',
          queHacer: 'Atempera temprano en la mañana o enfría el cuarto.',
          correccion: 'C-08',
          valorMedido: temp,
          valorEsperado: tempMax,
        ),
      );
    }

    final hr = c.humedadCuartoPct;
    final hrMax = umbrales['cuarto_hr_maxima_pct'];
    if (hr != null && hr > hrMax) {
      alertas.add(
        Alerta(
          regla: 'RN-13',
          severidad: Severidad.aviso,
          quePaso: 'El cuarto está a ${hr.toStringAsFixed(0)} % de humedad',
          porQueImporta:
              'Con más de ${hrMax.toStringAsFixed(0)} % se condensa agua sobre '
              'el chocolate y aparece sugar bloom.',
          queHacer: 'Baja la humedad antes de moldear.',
          correccion: 'C-09',
          valorMedido: hr,
          valorEsperado: hrMax,
        ),
      );
    }

    final trabajo = c.tempTrabajoC;
    final minimo = umbrales['atemperado_temp_trabajo_min_c'];
    final maximo = umbrales['atemperado_temp_trabajo_max_c'];
    if (trabajo != null && (trabajo < minimo || trabajo > maximo)) {
      alertas.add(
        Alerta(
          regla: 'RN-14',
          severidad: Severidad.aviso,
          quePaso: 'Temperatura de trabajo ${trabajo.toStringAsFixed(1)} °C',
          porQueImporta:
              'El chocolate negro se moldea entre ${minimo.toStringAsFixed(0)} '
              'y ${maximo.toStringAsFixed(0)} °C. Fuera de ahí, o queda sin '
              'brillo o se deshacen los cristales buenos.',
          queHacer: trabajo < minimo
              ? 'Calienta un poco, con cuidado de no pasarte.'
              : 'Deja enfriar antes de moldear.',
          correccion: 'C-08',
          valorMedido: trabajo,
          valorEsperado: trabajo < minimo ? minimo : maximo,
        ),
      );
    }
    return alertas;
  }

  // ------------------------------------------------------------ laboratorio

  /// RN-15: cadmio por encima del límite. Bloquea la venta del lote.
  List<Alerta> evaluarLaboratorio(ContextoLaboratorio c) {
    if (c.analisis.toLowerCase() != 'cadmio') return const [];
    final limite = umbrales['cadmio_limite_mg_kg'];
    if (c.valor <= limite) return const [];

    return [
      Alerta(
        regla: 'RN-15',
        severidad: Severidad.bloqueante,
        quePaso: 'Cadmio de ${c.valor.toStringAsFixed(2)} ${c.unidad}',
        porQueImporta:
            'Supera el límite configurado de ${limite.toStringAsFixed(2)} '
            'mg/kg. Este lote no se puede vender mientras no se resuelva.',
        queHacer: 'La venta de este lote queda bloqueada. Consulta con el '
            'laboratorio y, si corresponde, repite el análisis.',
        valorMedido: c.valor,
        valorEsperado: limite,
      ),
    ];
  }

  // ------------------------------------------------------------ rendimientos

  /// RN-17: el rendimiento de una etapa se aleja de lo esperado.
  List<Alerta> evaluarRendimiento({
    required String etapa,
    required double rendimientoReal,
    required double rendimientoEsperado,
  }) {
    if (rendimientoEsperado <= 0) return const [];
    final desvio = umbrales['rendimiento_desvio_pct'];
    final diferencia =
        100.0 * (rendimientoReal - rendimientoEsperado).abs() /
            rendimientoEsperado;
    if (diferencia <= desvio) return const [];

    return [
      Alerta(
        regla: 'RN-17',
        severidad: Severidad.aviso,
        quePaso: 'El rendimiento de $etapa no cuadra',
        porQueImporta:
            'Salió ${rendimientoReal.toStringAsFixed(2)} y lo esperado era '
            '${rendimientoEsperado.toStringAsFixed(2)}, una diferencia de '
            '${diferencia.toStringAsFixed(0)} %. Casi siempre es un error al '
            'anotar un peso.',
        queHacer: 'Revisa los pesos que registraste en esta etapa.',
        valorMedido: rendimientoReal,
        valorEsperado: rendimientoEsperado,
      ),
    ];
  }
}
