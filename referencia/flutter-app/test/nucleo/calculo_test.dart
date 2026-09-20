// Tests del balance de masa (RF-LOT-08) y de la calculadora de receta (RF-REF-01).

import 'package:cacaotrace/nucleo/calculo/balance_masa.dart';
import 'package:cacaotrace/nucleo/calculo/receta.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('balance de masa', () {
    const balance = BalanceMasa();

    test('el lote típico de la ERS cae dentro de lo esperado', () {
      // §2.5: ~100 mazorcas -> 16-18 kg de baba -> ~6 kg seco -> ~5 kg chocolate
      final pasos = balance.calcular(
        mazorcas: 100,
        kgBaba: 17,
        kgSeco: 6.1,
        kgNibs: 5.2,
      );
      for (final p in pasos.where((p) => p.registrado)) {
        expect(p.desvioPct!.abs(), lessThan(25),
            reason: '${p.etapa} se desvía ${p.desvioPct!.toStringAsFixed(1)} %');
      }
    });

    test('marca los pasos que aún no se registraron', () {
      final pasos = balance.calcular(mazorcas: 100, kgBaba: 17);
      expect(pasos[0].registrado, isTrue);
      expect(pasos[1].registrado, isFalse);
      expect(pasos[1].desvioPct, isNull);
      expect(pasos[1].rendimiento, isNull);
    });

    test('un peso muy bajo produce un desvío negativo grande', () {
      final pasos = balance.calcular(mazorcas: 100, kgBaba: 5);
      expect(pasos[0].desvioPct, lessThan(-50));
    });

    test('cada paso parte de lo real del anterior, no de lo esperado', () {
      // Con la mitad de baba de lo esperado, lo esperado del secado también
      // debe bajar a la mitad: si no, un error temprano ensucia todo el balance.
      final normal = balance.calcular(mazorcas: 100, kgBaba: 17);
      final pocaBaba = balance.calcular(mazorcas: 100, kgBaba: 8.5);
      expect(pocaBaba[1].salidaEsperada,
          closeTo(normal[1].salidaEsperada / 2, 0.01));
    });

    test('proyecta el chocolate desde solo el número de mazorcas', () {
      final kg = balance.proyectarChocolate(mazorcas: 100);
      expect(kg, greaterThan(4));
      expect(kg, lessThan(7));
    });

    test('la proyección usa los datos reales cuando existen', () {
      final conNibs = balance.proyectarChocolate(mazorcas: 100, kgNibs: 4.5);
      expect(conNibs, closeTo(5.0, 0.01));
    });

    test('el rendimiento de un paso se calcula bien', () {
      final pasos = balance.calcular(mazorcas: 100, kgBaba: 17, kgSeco: 6.12);
      expect(pasos[1].rendimiento, closeTo(0.36, 0.005));
    });

    test('no divide por cero sin mazorcas', () {
      final pasos = balance.calcular(mazorcas: 0);
      expect(pasos[0].rendimiento, isNull);
      expect(pasos[0].desvioPct, isNull);
    });
  });

  group('calculadora de receta', () {
    const calc = CalculadoraReceta();

    test('un chocolate 90 % da las proporciones correctas', () {
      final r = calc.calcular(kgNibs: 4.5);
      expect(r.kgTotal, closeTo(5.0, 0.001));
      expect(r.porcentajeCacaoReal, closeTo(90, 0.5));
      expect(r.kgAzucar, greaterThan(0));
      expect(r.kgAzucar + r.kgNibs + r.kgLecitina, closeTo(5.0, 0.001));
    });

    test('la manteca añadida cuenta como cacao', () {
      final r = calc.calcular(kgNibs: 4.0, mantecaExtraPct: 5);
      expect(r.kgMantecaAnadida, greaterThan(0));
      expect(r.porcentajeCacaoReal, closeTo(90, 0.5));
    });

    test('se puede hacer sin lecitina', () {
      final r = calc.calcular(kgNibs: 4.5, usarLecitina: false);
      expect(r.kgLecitina, 0);
      expect(r.porcentajeCacaoReal, closeTo(90, 0.5));
    });

    test('un 70 % lleva mucha más azúcar que un 90 %', () {
      final r90 = calc.calcular(kgNibs: 4.5);
      final r70 = calc.calcular(kgNibs: 4.5, porcentajeCacao: 70);
      expect(r70.kgAzucar, greaterThan(r90.kgAzucar * 2));
    });

    test('estima las barras que salen', () {
      final r = calc.calcular(kgNibs: 4.5);
      // 5 kg menos 5 % de merma, en barras de 50 g
      expect(r.barrasEstimadas(), 95);
      expect(r.barrasEstimadas(pesoBarraG: 100), 47);
    });

    test('el camino inverso es coherente', () {
      final nibs = calc.nibsNecesarios(kgTotalDeseado: 5);
      final r = calc.calcular(kgNibs: nibs);
      expect(r.kgTotal, closeTo(5, 0.001));
    });

    test('rechaza entradas imposibles', () {
      expect(() => calc.calcular(kgNibs: 0), throwsArgumentError);
      expect(() => calc.calcular(kgNibs: -1), throwsArgumentError);
      expect(() => calc.calcular(kgNibs: 1, porcentajeCacao: 0),
          throwsArgumentError);
      expect(() => calc.calcular(kgNibs: 1, porcentajeCacao: 150),
          throwsArgumentError);
      expect(() => calc.calcular(kgNibs: 1, mantecaExtraPct: 95),
          throwsArgumentError);
      expect(() => calc.calcular(kgNibs: 1, mantecaExtraPct: -5),
          throwsArgumentError);
    });

    test('al 100 % de cacao no queda azúcar', () {
      final r = calc.calcular(
          kgNibs: 5, porcentajeCacao: 100, usarLecitina: false);
      expect(r.kgAzucar, closeTo(0, 1e-9));
      expect(r.kgTotal, closeTo(5, 1e-9));
    });
  });
}
