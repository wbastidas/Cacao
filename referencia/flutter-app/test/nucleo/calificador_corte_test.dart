// Tests de la calificación según la norma INEN 176.
//
// Los casos vienen de `casos_norma.json`, EL MISMO archivo que usan los tests
// de Python en `entrenamiento/pruebas/test_calificar_corte.py`. Si la lógica de
// Dart se desvía de la de Python, estos tests fallan. Esa es toda la idea: el
// resultado que ve el usuario en el teléfono tiene que ser idéntico al que se
// valida en la computadora contra los tableros contados por un experto.

import 'dart:convert';
import 'dart:io';

import 'package:cacaotrace/nucleo/norma/calificador_corte.dart';
import 'package:cacaotrace/nucleo/norma/tabla_norma.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  late CalificadorCorte calificador;
  late Map<String, dynamic> casos;

  setUpAll(() {
    final tabla = TablaNorma.desdeJsonTexto(
      File('assets/norma/norma_inen176.json').readAsStringSync(),
    );
    calificador = CalificadorCorte(tabla);
    casos = jsonDecode(
      File('test/nucleo/casos_norma.json').readAsStringSync(),
    ) as Map<String, dynamic>;
  });

  group('casos compartidos con la implementación Python', () {
    test('hay casos cargados', () {
      expect((casos['casos'] as List), isNotEmpty);
      expect((casos['errores'] as List), isNotEmpty);
    });

    test('cada caso da el resultado esperado en cada perfil', () {
      for (final caso in casos['casos'] as List) {
        final c = caso as Map<String, dynamic>;
        final conteo = (c['conteo'] as Map).map(
          (k, v) => MapEntry(k as String, v as int),
        );
        (c['espera'] as Map).forEach((perfil, esperado) {
          final e = esperado as Map<String, dynamic>;
          final r = calificador.calificar(conteo, perfil: perfil as String);
          expect(
            r.resultado,
            e['resultado'],
            reason: '${c['nombre']} / $perfil',
          );
          expect(r.conforme, e['conforme'], reason: '${c['nombre']} / $perfil');
        });
      }
    });

    test('los porcentajes coinciden con los esperados', () {
      for (final caso in casos['casos'] as List) {
        final c = caso as Map<String, dynamic>;
        final conteo = (c['conteo'] as Map).map(
          (k, v) => MapEntry(k as String, v as int),
        );
        final pct = calificador.porcentajes(conteo);
        (c['porcentajes'] as Map).forEach((indicador, valor) {
          expect(
            pct[indicador as String],
            closeTo((valor as num).toDouble(), 0.05),
            reason: '${c['nombre']}: $indicador',
          );
        });
        expect(
          pct.granosEvaluados,
          c['granos_evaluados'],
          reason: '${c['nombre']}: granos evaluados',
        );
        if (c.containsKey('granos_ignorados')) {
          expect(pct.granosIgnorados, c['granos_ignorados']);
        }
      }
    });

    test('los avisos coinciden en cantidad', () {
      for (final caso in casos['casos'] as List) {
        final c = caso as Map<String, dynamic>;
        if (!c.containsKey('avisos_esperados')) continue;
        final conteo = (c['conteo'] as Map).map(
          (k, v) => MapEntry(k as String, v as int),
        );
        final perfil = (c['espera'] as Map).keys.first as String;
        final r = calificador.calificar(conteo, perfil: perfil);
        expect(
          r.avisos.length,
          c['avisos_esperados'],
          reason: '${c['nombre']}: ${r.avisos}',
        );
      }
    });

    test('las entradas inválidas lanzan ErrorNorma', () {
      for (final caso in casos['errores'] as List) {
        final c = caso as Map<String, dynamic>;
        final conteo = (c['conteo'] as Map).map(
          (k, v) => MapEntry(k as String, v as int),
        );
        expect(
          () => calificador.calificar(conteo),
          throwsA(isA<ErrorNorma>()),
          reason: c['nombre'] as String,
        );
      }
    });
  });

  group('reglas del cálculo', () {
    test('fermentado_total es la suma de bueno y ligero', () {
      final pct = calificador.porcentajes({
        'bien_fermentado': 50,
        'ligeramente_fermentado': 20,
        'violeta': 30,
      });
      expect(
        pct.fermentadoTotal,
        closeTo(pct['fermentado_bueno'] + pct['fermentado_ligero'], 1e-9),
      );
    });

    test('un perfil inexistente lanza ErrorNorma', () {
      expect(
        () => calificador.calificar({'bien_fermentado': 100},
            perfil: 'no_existe'),
        throwsA(isA<ErrorNorma>()),
      );
    });

    test('una clase desconocida avisa y no rompe el cálculo', () {
      final r = calificador.calificar({
        'bien_fermentado': 90,
        'clase_inventada': 10,
      });
      expect(r.porcentajes.granosEvaluados, 90);
      expect(r.avisos.any((a) => a.contains('clase_inventada')), isTrue);
    });

    test('todos los perfiles de la tabla se pueden calificar', () {
      final conteo = {
        'bien_fermentado': 70,
        'ligeramente_fermentado': 8,
        'violeta': 12,
        'pizarroso': 8,
        'mohoso': 2,
      };
      final todos = calificador.calificarTodos(conteo);
      expect(todos.length, calificador.tabla.perfiles.length);
      for (final r in todos.values) {
        expect(r.resultado, isNotEmpty);
      }
    });

    test('la tabla no usa indicadores que el cálculo no produce', () {
      const validos = {
        'fermentado_bueno',
        'fermentado_ligero',
        'fermentado_total',
        'violeta',
        'pizarroso',
        'mohoso',
        'defectuoso',
      };
      for (final p in calificador.tabla.perfiles.values) {
        final requisitos = p.esPorGrados
            ? p.grados.expand((g) => g.requisitos)
            : p.requisitos;
        for (final r in requisitos) {
          expect(validos, contains(r.indicador),
              reason: 'El perfil ${p.clave} usa "${r.indicador}"');
          expect(['min', 'max'], contains(r.tipo));
        }
      }
    });

    test('la tabla se puede exportar y volver a leer sin perder nada', () {
      final texto = calificador.tabla.aJsonTexto();
      final vuelta = TablaNorma.desdeJsonTexto(texto);
      final c2 = CalificadorCorte(vuelta);
      const conteo = {'bien_fermentado': 70, 'violeta': 20, 'pizarroso': 10};
      expect(
        c2.calificar(conteo).resultado,
        calificador.calificar(conteo).resultado,
      );
    });

    test('avisa cuando se cuentan menos de 97 o más de 103 granos', () {
      final pocos = calificador.calificar({'bien_fermentado': 80});
      expect(pocos.avisos.any((a) => a.contains('80 granos')), isTrue);

      final muchos = calificador.calificar({'bien_fermentado': 110});
      expect(muchos.avisos.any((a) => a.contains('110 granos')), isTrue);

      final justos = calificador.calificar({
        'bien_fermentado': 80,
        'violeta': 20,
      });
      expect(justos.avisos, isEmpty);
    });
  });
}
