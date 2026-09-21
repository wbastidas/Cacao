/// Carga y ejecuta los modelos TFLite dentro del teléfono (RF-IA-01).
///
/// Principio de diseño: **si no hay modelo, la app no falla**. Muestra
/// "modelo no instalado" y el usuario sigue trabajando con el registro y el
/// conteo manual. Eso es deliberado: la ERS prevé arrancar así mientras los
/// modelos alcanzan sus metas de la §8.1, y significa que la app es útil desde
/// el primer día aunque todavía no existan los .tflite.
library;

import 'dart:convert';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter/services.dart' show rootBundle;
import 'package:tflite_flutter/tflite_flutter.dart';

import 'resultado_ia.dart';

/// Un modelo instalado y listo para usar.
class ModeloCargado {
  ModeloCargado({
    required this.tarea,
    required this.interprete,
    required this.clases,
    required this.version,
    required this.umbralConfianza,
    required this.ladoEntrada,
  });

  final String tarea;
  final Interpreter interprete;
  final List<String> clases;
  final String version;
  final double umbralConfianza;
  final int ladoEntrada;

  void cerrar() => interprete.close();
}

class ServicioModelos {
  ServicioModelos({this.rutaBase = 'assets/modelos'});

  final String rutaBase;
  final Map<String, ModeloCargado> _cargados = {};
  final Map<String, ModeloNoDisponible> _fallidos = {};

  /// Las tareas que la app puede usar, con el nombre que ve el usuario.
  static const Map<String, String> tareas = {
    'mazorca': 'mazorcas',
    'corte': 'prueba de corte',
    'tostado': 'tostado',
    'chocolate': 'chocolate',
  };

  /// ¿Hay modelo instalado para esta tarea?
  bool estaDisponible(String tarea) => _cargados.containsKey(tarea);

  /// Por qué no está, si no está.
  ModeloNoDisponible? porQueNoEstaDisponible(String tarea) => _fallidos[tarea];

  String? versionDe(String tarea) => _cargados[tarea]?.version;

  List<String> clasesDe(String tarea) => _cargados[tarea]?.clases ?? const [];

  /// Intenta cargar todos los modelos. Nunca lanza: los que falten quedan
  /// registrados como no disponibles y la app arranca igual.
  Future<void> cargarTodos() async {
    for (final tarea in tareas.keys) {
      await cargar(tarea);
    }
  }

  /// Carga un modelo. Devuelve false si no está o no se pudo abrir.
  Future<bool> cargar(String tarea) async {
    if (_cargados.containsKey(tarea)) return true;
    final nombreVisible = tareas[tarea] ?? tarea;

    String metadatosTexto;
    try {
      metadatosTexto =
          await rootBundle.loadString('$rutaBase/$tarea/metadatos.json');
    } catch (_) {
      _fallidos[tarea] =
          ModeloNoDisponible(nombreVisible, MotivoSinModelo.noInstalado);
      return false;
    }

    try {
      final meta = jsonDecode(metadatosTexto) as Map<String, dynamic>;
      final etiquetas =
          await rootBundle.loadString('$rutaBase/$tarea/etiquetas.txt');
      final clases = etiquetas
          .split('\n')
          .map((e) => e.trim())
          .where((e) => e.isNotEmpty)
          .toList();

      final interprete =
          await Interpreter.fromAsset('$rutaBase/$tarea/modelo.tflite');

      final entrada = meta['entrada'];
      final lado = entrada is Map
          ? (entrada['alto'] as num?)?.toInt() ?? 224
          : (entrada as num?)?.toInt() ?? 224;

      _cargados[tarea] = ModeloCargado(
        tarea: tarea,
        interprete: interprete,
        clases: clases,
        version: (meta['version'] as String?) ?? 'desconocida',
        umbralConfianza:
            ((meta['umbral_confianza'] as num?) ?? 0.6).toDouble(),
        ladoEntrada: lado,
      );
      _fallidos.remove(tarea);
      return true;
    } catch (e) {
      _fallidos[tarea] = ModeloNoDisponible(
        nombreVisible,
        MotivoSinModelo.errorAlCargar,
        e.toString(),
      );
      return false;
    }
  }

  /// Clasifica una imagen ya redimensionada al lado que pide el modelo.
  ///
  /// [pixeles] son valores 0-255 en orden RGB. El modelo normaliza por dentro,
  /// así que la app no tiene que replicar ninguna fórmula de preprocesado
  /// (§8.2 de la ERS).
  Future<ResultadoClasificacion> clasificar(
    String tarea,
    Uint8List pixeles, {
    double? umbralPersonalizado,
  }) async {
    final modelo = _cargados[tarea];
    if (modelo == null) {
      throw _fallidos[tarea] ??
          ModeloNoDisponible(
              tareas[tarea] ?? tarea, MotivoSinModelo.noInstalado);
    }

    final cronometro = Stopwatch()..start();
    final lado = modelo.ladoEntrada;
    final esperado = lado * lado * 3;
    if (pixeles.length != esperado) {
      throw ArgumentError(
        'La imagen debe venir en $lado×$lado píxeles RGB '
        '($esperado valores); llegaron ${pixeles.length}.',
      );
    }

    // El modelo espera [1, lado, lado, 3].
    final entrada = List.generate(
      1,
      (_) => List.generate(
        lado,
        (y) => List.generate(
          lado,
          (x) {
            final i = (y * lado + x) * 3;
            return [
              pixeles[i].toDouble(),
              pixeles[i + 1].toDouble(),
              pixeles[i + 2].toDouble(),
            ];
          },
        ),
      ),
    );

    final salida = List.filled(modelo.clases.length, 0.0).reshape([1, modelo.clases.length]);
    modelo.interprete.run(entrada, salida);
    cronometro.stop();

    final probabilidades = (salida[0] as List).cast<num>();
    final predicciones = <Prediccion>[
      for (var i = 0; i < modelo.clases.length; i++)
        Prediccion(modelo.clases[i], probabilidades[i].toDouble()),
    ]..sort((a, b) => b.confianza.compareTo(a.confianza));

    return ResultadoClasificacion(
      predicciones: predicciones,
      modeloVersion: modelo.version,
      umbralConfianza: umbralPersonalizado ?? modelo.umbralConfianza,
      milisegundos: cronometro.elapsedMilliseconds,
    );
  }

  /// Detecta y clasifica cada grano del tablero con el modelo M2.
  ///
  /// La salida de YOLO viene como [1, 4 + n_clases, n_cajas]: para cada caja,
  /// su centro y tamaño y la puntuación de cada clase. Aquí se traduce a cajas
  /// normalizadas y se aplica supresión de solapamientos, porque el modelo
  /// suele proponer varias cajas sobre el mismo grano.
  Future<ResultadoDeteccion> detectarGranos(
    String tarea,
    Uint8List pixeles, {
    double umbralConfianza = 0.35,
    double umbralSolape = 0.45,
  }) async {
    final modelo = _cargados[tarea];
    if (modelo == null) {
      throw _fallidos[tarea] ??
          ModeloNoDisponible(
              tareas[tarea] ?? tarea, MotivoSinModelo.noInstalado);
    }

    final cronometro = Stopwatch()..start();
    final lado = modelo.ladoEntrada;
    final entrada = List.generate(
      1,
      (_) => List.generate(
        lado,
        (y) => List.generate(lado, (x) {
          final i = (y * lado + x) * 3;
          // YOLO espera valores 0-1.
          return [
            pixeles[i] / 255.0,
            pixeles[i + 1] / 255.0,
            pixeles[i + 2] / 255.0,
          ];
        }),
      ),
    );

    final formaSalida = modelo.interprete.getOutputTensor(0).shape;
    final salida = List.filled(
      formaSalida.reduce((a, b) => a * b),
      0.0,
    ).reshape(formaSalida);
    modelo.interprete.run(entrada, salida);
    cronometro.stop();

    final crudas = _leerCajasYolo(
      salida,
      formaSalida,
      modelo.clases,
      umbralConfianza,
    );
    final filtradas = _suprimirSolapes(crudas, umbralSolape);

    return ResultadoDeteccion(
      granos: filtradas,
      modeloVersion: modelo.version,
      milisegundos: cronometro.elapsedMilliseconds,
    );
  }

  List<GranoDetectado> _leerCajasYolo(
    dynamic salida,
    List<int> forma,
    List<String> clases,
    double umbral,
  ) {
    final cajas = <GranoDetectado>[];
    // forma: [1, 4 + n_clases, n_cajas]
    if (forma.length != 3) return cajas;
    final atributos = forma[1];
    final total = forma[2];
    final nClases = atributos - 4;
    if (nClases <= 0) return cajas;

    final datos = salida[0] as List;
    for (var i = 0; i < total; i++) {
      var mejorClase = 0;
      var mejorPuntaje = 0.0;
      for (var c = 0; c < nClases; c++) {
        final p = (datos[4 + c][i] as num).toDouble();
        if (p > mejorPuntaje) {
          mejorPuntaje = p;
          mejorClase = c;
        }
      }
      if (mejorPuntaje < umbral) continue;

      final cx = (datos[0][i] as num).toDouble();
      final cy = (datos[1][i] as num).toDouble();
      final w = (datos[2][i] as num).toDouble();
      final h = (datos[3][i] as num).toDouble();

      cajas.add(GranoDetectado(
        clase: mejorClase < clases.length ? clases[mejorClase] : 'otro',
        confianza: mejorPuntaje,
        x: (cx - w / 2).clamp(0.0, 1.0),
        y: (cy - h / 2).clamp(0.0, 1.0),
        ancho: w.clamp(0.0, 1.0),
        alto: h.clamp(0.0, 1.0),
      ));
    }
    return cajas;
  }

  /// Se queda con la mejor caja de cada grupo de cajas superpuestas.
  ///
  /// Sin esto el conteo saldría inflado: el modelo propone varias cajas por
  /// grano y la app estaría contando el mismo grano dos o tres veces.
  List<GranoDetectado> _suprimirSolapes(
    List<GranoDetectado> cajas,
    double umbral,
  ) {
    final ordenadas = [...cajas]
      ..sort((a, b) => b.confianza.compareTo(a.confianza));
    final elegidas = <GranoDetectado>[];

    for (final caja in ordenadas) {
      var solapa = false;
      for (final elegida in elegidas) {
        if (_interseccionSobreUnion(caja, elegida) > umbral) {
          solapa = true;
          break;
        }
      }
      if (!solapa) elegidas.add(caja);
    }
    return elegidas;
  }

  double _interseccionSobreUnion(GranoDetectado a, GranoDetectado b) {
    final x1 = math.max(a.x, b.x);
    final y1 = math.max(a.y, b.y);
    final x2 = math.min(a.x + a.ancho, b.x + b.ancho);
    final y2 = math.min(a.y + a.alto, b.y + b.alto);
    final ancho = x2 - x1;
    final alto = y2 - y1;
    if (ancho <= 0 || alto <= 0) return 0;
    final interseccion = ancho * alto;
    final union = a.ancho * a.alto + b.ancho * b.alto - interseccion;
    return union <= 0 ? 0 : interseccion / union;
  }

  void cerrar() {
    for (final m in _cargados.values) {
      m.cerrar();
    }
    _cargados.clear();
  }
}
