// Tests de la capa de IA que no necesitan un modelo instalado: el umbral de
// confianza (RN-16), el conteo que alimenta la norma y el aviso de RF-PRC-07.

import 'dart:typed_data';

import 'package:cacaotrace/ia/resultado_ia.dart';
import 'package:cacaotrace/ia/servicio_modelos.dart';
import 'package:flutter_test/flutter_test.dart';

ResultadoClasificacion clasificacion(List<(String, double)> p,
        {double umbral = 0.6}) =>
    ResultadoClasificacion(
      predicciones: [for (final x in p) Prediccion(x.$1, x.$2)],
      modeloVersion: '2026.01.01',
      umbralConfianza: umbral,
    );

GranoDetectado grano(String clase, double x, double y,
        {double lado = 0.08, double conf = 0.9}) =>
    GranoDetectado(
        clase: clase, confianza: conf, x: x, y: y, ancho: lado, alto: lado);

void main() {
  group('RN-16 umbral de confianza', () {
    test('por encima del umbral afirma la clase', () {
      final r = clasificacion([('monilia', 0.83), ('sana', 0.12)]);
      expect(r.estaSeguro, isTrue);
      expect(r.texto, 'monilia (83 %)');
      expect(r.pistaBajaConfianza, isNull);
    });

    test('por debajo del umbral dice "No estoy seguro"', () {
      final r = clasificacion([('monilia', 0.41), ('sana', 0.39)]);
      expect(r.estaSeguro, isFalse);
      expect(r.texto, 'No estoy seguro');
      expect(r.pistaBajaConfianza, contains('monilia'));
      expect(r.pistaBajaConfianza, contains('41 %'));
    });

    test('el umbral se puede subir para una tarea exigente', () {
      final p = [('monilia', 0.70), ('sana', 0.30)];
      expect(clasificacion(p).estaSeguro, isTrue);
      expect(clasificacion(p, umbral: 0.9).estaSeguro, isFalse);
    });

    test('sin predicciones no inventa nada', () {
      final r = clasificacion([]);
      expect(r.mejor, isNull);
      expect(r.estaSeguro, isFalse);
      expect(r.texto, 'Sin resultado');
    });
  });

  group('conteo de la prueba de corte', () {
    test('agrupa los granos por clase', () {
      final r = ResultadoDeteccion(
        granos: [
          grano('bien_fermentado', 0.1, 0.1),
          grano('bien_fermentado', 0.3, 0.1),
          grano('violeta', 0.5, 0.1),
        ],
        modeloVersion: '1.0',
      );
      expect(r.conteo, {'bien_fermentado': 2, 'violeta': 1});
      expect(r.total, 3);
    });

    test('RF-PRC-07: avisa si el total se sale de 97-103', () {
      ResultadoDeteccion conN(int n) => ResultadoDeteccion(
            granos: [
              for (var i = 0; i < n; i++)
                grano('bien_fermentado', (i % 10) / 10, (i ~/ 10) / 10)
            ],
            modeloVersion: '1.0',
          );
      expect(conN(100).conteoDudoso, isFalse);
      expect(conN(97).conteoDudoso, isFalse);
      expect(conN(103).conteoDudoso, isFalse);
      expect(conN(96).conteoDudoso, isTrue);
      expect(conN(104).conteoDudoso, isTrue);
    });

    test('RF-PRC-04: corregir un grano cambia el conteo y queda marcado', () {
      final r = ResultadoDeteccion(
        granos: [
          grano('bien_fermentado', 0.1, 0.1),
          grano('bien_fermentado', 0.3, 0.1),
        ],
        modeloVersion: '1.0',
      );
      final corregido = r.conGranoCorregido(1, 'violeta');
      expect(corregido.conteo, {'bien_fermentado': 1, 'violeta': 1});
      expect(corregido.granos[1].corregidoPorUsuario, isTrue);
      expect(corregido.granos[1].confianza, 1.0);
      // El original no se toca: nunca se pierde lo que dijo el modelo.
      expect(r.conteo['bien_fermentado'], 2);
    });
  });

  group('la app funciona sin modelos instalados', () {
    test('cargar una tarea inexistente no lanza, solo informa', () async {
      final servicio = ServicioModelos(rutaBase: 'assets/no_existe');
      expect(await servicio.cargar('mazorca'), isFalse);
      expect(servicio.estaDisponible('mazorca'), isFalse);

      final motivo = servicio.porQueNoEstaDisponible('mazorca');
      expect(motivo, isNotNull);
      expect(motivo!.motivo, MotivoSinModelo.noInstalado);
      expect(motivo.mensaje, contains('a mano'));
    });

    test('cargarTodos no revienta aunque no haya ningún modelo', () async {
      final servicio = ServicioModelos(rutaBase: 'assets/no_existe');
      await servicio.cargarTodos();
      for (final tarea in ServicioModelos.tareas.keys) {
        expect(servicio.estaDisponible(tarea), isFalse);
      }
    });

    test('usar un modelo ausente da un mensaje en español, no un error técnico',
        () async {
      final servicio = ServicioModelos(rutaBase: 'assets/no_existe');
      await servicio.cargar('mazorca');
      expect(
        () => servicio.clasificar('mazorca', Uint8List(0)),
        throwsA(isA<ModeloNoDisponible>()),
      );
    });
  });
}
