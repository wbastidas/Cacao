// Tests del motor de reglas (tabla 5.1 de la ERS).
//
// Incluye el caso de aceptación del §10: "simular temperatura de 38 °C a las
// 72 h debe producir la alerta RN-04 con la corrección C-01".

import 'package:cacaotrace/nucleo/modelo/etapas.dart';
import 'package:cacaotrace/nucleo/reglas/alerta.dart';
import 'package:cacaotrace/nucleo/reglas/correcciones.dart';
import 'package:cacaotrace/nucleo/reglas/motor_reglas.dart';
import 'package:cacaotrace/nucleo/reglas/umbrales.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final motor = MotorReglas(Umbrales.porDefecto());

  Alerta? buscar(List<Alerta> alertas, String regla) {
    for (final a in alertas) {
      if (a.regla == regla) return a;
    }
    return null;
  }

  group('RN-01 reposo', () {
    test('no avisa dentro del rango normal', () {
      expect(motor.evaluarReposo(const ContextoReposo(diasDesdeLlegada: 4)),
          isEmpty);
    });

    test('avisa el día en que toca abrir', () {
      final a = motor.evaluarReposo(const ContextoReposo(diasDesdeLlegada: 6));
      expect(buscar(a, 'RN-01')?.severidad, Severidad.aviso);
    });

    test('se pone urgente si se pasa del límite', () {
      final a = motor.evaluarReposo(const ContextoReposo(diasDesdeLlegada: 9));
      expect(buscar(a, 'RN-01')?.severidad, Severidad.urgente);
    });

    test('no dice nada si ya se abrieron las mazorcas', () {
      expect(
        motor.evaluarReposo(
            const ContextoReposo(diasDesdeLlegada: 20, abierto: true)),
        isEmpty,
      );
    });
  });

  group('RN-02 masa mínima de baba', () {
    test('avisa con poca baba y sugiere C-01', () {
      final a = motor.evaluarApertura(
          const ContextoApertura(mazorcasAbiertas: 80, kgBaba: 13.6));
      final rn02 = buscar(a, 'RN-02');
      expect(rn02, isNotNull);
      expect(rn02!.correccion, 'C-01');
      expect(Correcciones.buscar(rn02.correccion), isNotNull);
    });

    test('la cáscara añadida cuenta para la masa mínima', () {
      final sinCascara = motor.evaluarApertura(
          const ContextoApertura(mazorcasAbiertas: 100, kgBaba: 17));
      final conCascara = motor.evaluarApertura(const ContextoApertura(
          mazorcasAbiertas: 100, kgBaba: 17, kgCascaraAnadida: 5));
      expect(buscar(sinCascara, 'RN-02'), isNotNull);
      expect(buscar(conCascara, 'RN-02'), isNull);
    });

    test('no avisa con masa suficiente', () {
      final a = motor.evaluarApertura(
          const ContextoApertura(mazorcasAbiertas: 130, kgBaba: 22.1));
      expect(buscar(a, 'RN-02'), isNull);
    });

    test('RN-17 avisa si el rendimiento por mazorca no cuadra', () {
      // 100 mazorcas y 40 kg de baba: el doble de lo esperado.
      final a = motor.evaluarApertura(
          const ContextoApertura(mazorcasAbiertas: 100, kgBaba: 40));
      expect(buscar(a, 'RN-17'), isNotNull);
    });
  });

  group('fermentación', () {
    test('RN-03 avisa tras demasiadas horas sin voltear', () {
      final a = motor.evaluarFermentacion(const ContextoFermentacion(
          horasDesdeInicio: 48, horasDesdeUltimoVolteo: 30));
      expect(buscar(a, 'RN-03')?.severidad, Severidad.urgente);
    });

    test('RN-03 no molesta en las primeras horas', () {
      final a = motor.evaluarFermentacion(const ContextoFermentacion(
          horasDesdeInicio: 10, horasDesdeUltimoVolteo: 10));
      expect(buscar(a, 'RN-03'), isNull);
    });

    test('RN-04: 38 °C a las 72 h da alerta con corrección C-01', () {
      // Este es el criterio de aceptación del §10 de la ERS.
      final a = motor.evaluarFermentacion(
          const ContextoFermentacion(horasDesdeInicio: 72, temperaturaC: 38));
      final rn04 = buscar(a, 'RN-04');
      expect(rn04, isNotNull, reason: 'debía dispararse RN-04');
      expect(rn04!.correccion, 'C-01');
      expect(rn04.severidad, Severidad.urgente);
      expect(rn04.valorMedido, 38);
      expect(rn04.valorEsperado, 40);
      expect(Correcciones.buscar('C-01')!.pasos, isNotEmpty);
    });

    test('RN-04 no se dispara antes de las 60 h', () {
      final a = motor.evaluarFermentacion(
          const ContextoFermentacion(horasDesdeInicio: 30, temperaturaC: 38));
      expect(buscar(a, 'RN-04'), isNull);
    });

    test('RN-05 avisa por encima de 52 °C', () {
      final a = motor.evaluarFermentacion(
          const ContextoFermentacion(horasDesdeInicio: 40, temperaturaC: 54));
      expect(buscar(a, 'RN-05'), isNotNull);
    });

    test('RN-06 dispara con olor pútrido y sugiere C-05', () {
      final a = motor.evaluarFermentacion(const ContextoFermentacion(
          horasDesdeInicio: 100, olor: OlorFermentacion.putrido));
      expect(buscar(a, 'RN-06')?.correccion, 'C-05');
    });

    test('RN-06 no dispara con olor avinagrado, que es normal', () {
      final a = motor.evaluarFermentacion(const ContextoFermentacion(
          horasDesdeInicio: 60, olor: OlorFermentacion.avinagrado));
      expect(buscar(a, 'RN-06'), isNull);
    });

    test('RN-06 con moho sugiere C-03, no C-05', () {
      final a = motor.evaluarFermentacion(const ContextoFermentacion(
          horasDesdeInicio: 60, olor: OlorFermentacion.moho));
      expect(buscar(a, 'RN-06')?.correccion, 'C-03');
    });

    test('RN-07 avisa pasados 7 días', () {
      final a = motor.evaluarFermentacion(
          const ContextoFermentacion(horasDesdeInicio: 24 * 8));
      expect(buscar(a, 'RN-07')?.correccion, 'C-05');
    });

    test('una lectura normal no produce ninguna alerta', () {
      final a = motor.evaluarFermentacion(const ContextoFermentacion(
        horasDesdeInicio: 72,
        temperaturaC: 47,
        horasDesdeUltimoVolteo: 12,
        olor: OlorFermentacion.avinagrado,
      ));
      expect(a, isEmpty);
    });
  });

  group('secado', () {
    test('RN-08 bloquea al cerrar con humedad alta', () {
      final a = motor.evaluarSecado(
          const ContextoSecado(humedadGranoPct: 9, cerrandoEtapa: true));
      final rn08 = buscar(a, 'RN-08');
      expect(rn08?.severidad, Severidad.bloqueante);
      expect(rn08?.bloquea, isTrue);
    });

    test('RN-08 no bloquea a media etapa', () {
      final a = motor.evaluarSecado(const ContextoSecado(humedadGranoPct: 9));
      expect(buscar(a, 'RN-08'), isNull);
    });

    test('RN-08 deja pasar con humedad correcta', () {
      final a = motor.evaluarSecado(
          const ContextoSecado(humedadGranoPct: 6.5, cerrandoEtapa: true));
      expect(buscar(a, 'RN-08'), isNull);
    });

    test('RN-09 avisa por moho visible', () {
      final a = motor.evaluarSecado(const ContextoSecado(mohoVisible: true));
      expect(buscar(a, 'RN-09')?.correccion, 'C-03');
    });
  });

  group('RN-10 prueba de corte', () {
    test('no dice nada si el lote cumple', () {
      final a = motor.evaluarPruebaCorte(
        conforme: true,
        resultado: 'Grado 1',
        fallas: const [],
        pctVioleta: 10,
        pctPizarroso: 3,
        pctMohoso: 0,
      );
      expect(a, isEmpty);
    });

    test('elige C-04 cuando el defecto dominante es pizarroso', () {
      final a = motor.evaluarPruebaCorte(
        conforme: false,
        resultado: 'Fuera de grado',
        fallas: const ['pizarroso = 25.0% (requiere ≤ 18%)'],
        pctVioleta: 10,
        pctPizarroso: 25,
        pctMohoso: 1,
      );
      expect(buscar(a, 'RN-10')?.correccion, 'C-04');
    });

    test('elige C-02 cuando el defecto dominante es violeta', () {
      final a = motor.evaluarPruebaCorte(
        conforme: false,
        resultado: 'Fuera de grado',
        fallas: const ['violeta = 30.0% (requiere ≤ 25%)'],
        pctVioleta: 30,
        pctPizarroso: 5,
        pctMohoso: 1,
      );
      expect(buscar(a, 'RN-10')?.correccion, 'C-02');
    });

    test('elige C-03 cuando el defecto dominante es moho', () {
      final a = motor.evaluarPruebaCorte(
        conforme: false,
        resultado: 'Fuera de grado',
        fallas: const ['mohoso = 8.0% (requiere ≤ 4%)'],
        pctVioleta: 5,
        pctPizarroso: 3,
        pctMohoso: 8,
      );
      expect(buscar(a, 'RN-10')?.correccion, 'C-03');
    });
  });

  group('RN-11 almacén', () {
    test('avisa con humedad relativa alta', () {
      final a = motor
          .evaluarAlmacen(const ContextoAlmacen(humedadRelativaPct: 80));
      expect(buscar(a, 'RN-11'), isNotNull);
    });

    test('no avisa con humedad correcta', () {
      final a = motor
          .evaluarAlmacen(const ContextoAlmacen(humedadRelativaPct: 60));
      expect(a, isEmpty);
    });

    test('moho y plagas producen alertas distintas', () {
      final a = motor.evaluarAlmacen(
          const ContextoAlmacen(mohoVisible: true, plagas: true));
      expect(a.length, 2);
    });
  });

  group('RN-12 tostado', () {
    test('avisa con merma excesiva', () {
      final a = motor.evaluarTostado(
          const ContextoTostado(kgEntrada: 10, kgSalida: 8.2));
      expect(buscar(a, 'RN-12'), isNotNull);
    });

    test('no avisa con merma normal', () {
      final a = motor.evaluarTostado(
          const ContextoTostado(kgEntrada: 10, kgSalida: 9.3));
      expect(a, isEmpty);
    });

    test('avisa si la cascarilla se aleja de lo esperado', () {
      final a = motor.evaluarTostado(const ContextoTostado(
          kgEntrada: 10, kgSalida: 9.3, kgNibs: 7, kgCascarilla: 2.3));
      expect(a.any((x) => x.quePaso.contains('cascarilla')), isTrue);
    });

    test('el cálculo de merma es correcto', () {
      const c = ContextoTostado(kgEntrada: 10, kgSalida: 9);
      expect(c.mermaPct, closeTo(10, 1e-9));
    });

    test('no divide por cero con entrada nula', () {
      const c = ContextoTostado(kgEntrada: 0, kgSalida: 0);
      expect(c.mermaPct, isNull);
      expect(motor.evaluarTostado(c), isEmpty);
    });
  });

  group('atemperado', () {
    test('RN-13 avisa con cuarto caliente y sugiere C-08', () {
      final a = motor
          .evaluarAtemperado(const ContextoAtemperado(tempCuartoC: 26));
      expect(buscar(a, 'RN-13')?.correccion, 'C-08');
    });

    test('RN-13 con humedad alta sugiere C-09', () {
      final a = motor
          .evaluarAtemperado(const ContextoAtemperado(humedadCuartoPct: 75));
      expect(buscar(a, 'RN-13')?.correccion, 'C-09');
    });

    test('RN-14 avisa fuera del rango de trabajo', () {
      final frio = motor
          .evaluarAtemperado(const ContextoAtemperado(tempTrabajoC: 29));
      final caliente = motor
          .evaluarAtemperado(const ContextoAtemperado(tempTrabajoC: 34));
      expect(buscar(frio, 'RN-14'), isNotNull);
      expect(buscar(caliente, 'RN-14'), isNotNull);
    });

    test('condiciones correctas no producen alertas', () {
      final a = motor.evaluarAtemperado(const ContextoAtemperado(
          tempCuartoC: 20, humedadCuartoPct: 50, tempTrabajoC: 31.5));
      expect(a, isEmpty);
    });
  });

  group('RN-15 cadmio', () {
    test('bloquea por encima del límite', () {
      final a = motor.evaluarLaboratorio(const ContextoLaboratorio(
          analisis: 'cadmio', valor: 1.2, unidad: 'mg/kg'));
      expect(buscar(a, 'RN-15')?.bloquea, isTrue);
    });

    test('no bloquea dentro del límite', () {
      final a = motor.evaluarLaboratorio(const ContextoLaboratorio(
          analisis: 'cadmio', valor: 0.4, unidad: 'mg/kg'));
      expect(a, isEmpty);
    });

    test('ignora otros análisis', () {
      final a = motor.evaluarLaboratorio(const ContextoLaboratorio(
          analisis: 'humedad', valor: 9, unidad: '%'));
      expect(a, isEmpty);
    });
  });

  group('umbrales editables', () {
    test('cambiar un umbral cambia el comportamiento', () {
      final estricto = MotorReglas(
          Umbrales.porDefecto().con('ferm_temp_minima_c', 45));
      final ctx = const ContextoFermentacion(
          horasDesdeInicio: 72, temperaturaC: 42);
      expect(buscar(motor.evaluarFermentacion(ctx), 'RN-04'), isNull);
      expect(buscar(estricto.evaluarFermentacion(ctx), 'RN-04'), isNotNull);
    });

    test('rechaza un valor fuera del rango razonable', () {
      expect(() => Umbrales.porDefecto().con('ferm_temp_maxima_c', 200),
          throwsArgumentError);
    });

    test('rechaza un umbral que no existe', () {
      expect(() => Umbrales.porDefecto().con('inventado', 1),
          throwsArgumentError);
    });

    test('todos los umbrales del catálogo tienen explicación y rango', () {
      for (final u in CatalogoUmbrales.todos) {
        expect(u.etiqueta, isNotEmpty, reason: u.clave);
        expect(u.explicacion, isNotEmpty, reason: u.clave);
        expect(u.esValido(u.porDefecto), isTrue,
            reason: '${u.clave}: el valor por defecto está fuera de su rango');
      }
    });
  });

  group('integridad de las alertas', () {
    test('toda corrección referida por una alerta existe', () {
      final todas = <Alerta>[
        ...motor.evaluarApertura(
            const ContextoApertura(mazorcasAbiertas: 80, kgBaba: 10)),
        ...motor.evaluarFermentacion(const ContextoFermentacion(
            horasDesdeInicio: 200,
            temperaturaC: 30,
            olor: OlorFermentacion.amoniaco)),
        ...motor.evaluarSecado(const ContextoSecado(
            humedadGranoPct: 12, mohoVisible: true, cerrandoEtapa: true)),
        ...motor.evaluarAlmacen(const ContextoAlmacen(
            humedadRelativaPct: 90, mohoVisible: true, plagas: true)),
        ...motor.evaluarAtemperado(const ContextoAtemperado(
            tempCuartoC: 30, humedadCuartoPct: 80, tempTrabajoC: 40)),
      ];
      expect(todas, isNotEmpty);
      for (final a in todas) {
        expect(a.quePaso, isNotEmpty, reason: a.regla);
        expect(a.porQueImporta, isNotEmpty, reason: a.regla);
        expect(a.queHacer, isNotEmpty, reason: a.regla);
        if (a.correccion != null) {
          expect(Correcciones.buscar(a.correccion), isNotNull,
              reason: '${a.regla} apunta a ${a.correccion}, que no existe');
        }
      }
    });

    test('la biblioteca de correcciones está completa', () {
      expect(Correcciones.todas.length, 9);
      for (final c in Correcciones.todas) {
        expect(c.pasos, isNotEmpty, reason: c.codigo);
        expect(c.porQuePasa, isNotEmpty, reason: c.codigo);
        expect(c.paraLaProximaVez, isNotEmpty, reason: c.codigo);
      }
    });
  });
}
