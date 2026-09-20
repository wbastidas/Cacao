/// Calificación de la prueba de corte según la NTE INEN 176.
///
/// Convierte el conteo de granos por clase —venga del modelo M2 o del conteo
/// manual— en porcentajes y en un resultado de calidad (RF-PRC-06, RN-10).
///
/// Es la MISMA lógica que `entrenamiento/calificar_corte.py` y lee el MISMO
/// archivo JSON. Los dos comparten los casos de prueba de
/// `test/nucleo/casos_norma.json`: si una implementación se desvía de la otra,
/// los tests fallan.
library;

import 'tabla_norma.dart';

/// Total de granos que manda la norma para una prueba de corte completa.
const int granosNorma = 100;

/// Margen aceptado antes de avisar que el conteo no cuadra (RF-PRC-07).
const int granosMinimoAviso = 97;
const int granosMaximoAviso = 103;

/// Indicadores que siempre aparecen en el resultado, aunque valgan 0 %.
const List<String> indicadoresNorma = [
  'fermentado_bueno',
  'fermentado_ligero',
  'violeta',
  'pizarroso',
  'mohoso',
  'defectuoso',
];

/// Problema con el conteo o con la tabla de la norma.
class ErrorNorma implements Exception {
  const ErrorNorma(this.mensaje);
  final String mensaje;
  @override
  String toString() => mensaje;
}

/// Los porcentajes calculados sobre los granos válidos.
class PorcentajesCorte {
  const PorcentajesCorte({
    required this.valores,
    required this.granosEvaluados,
    required this.granosIgnorados,
    required this.clasesDesconocidas,
  });

  /// Indicador de la norma -> porcentaje. Incluye `fermentado_total`.
  final Map<String, double> valores;

  /// Granos que entraron en el cálculo.
  final int granosEvaluados;

  /// Granos de clases marcadas como `ignorar` (por ejemplo 'otro').
  final int granosIgnorados;

  /// Clases del conteo que no están en la tabla y se descartaron.
  final List<String> clasesDesconocidas;

  /// Todo lo que el usuario contó, válido o no.
  int get granosContados => granosEvaluados + granosIgnorados;

  double operator [](String indicador) => valores[indicador] ?? 0.0;

  double get fermentadoTotal => this['fermentado_total'];
}

/// Resultado completo de calificar un tablero.
class ResultadoCorte {
  const ResultadoCorte({
    required this.perfil,
    required this.resultado,
    required this.conforme,
    required this.porcentajes,
    required this.fallas,
    required this.fallasPorGrado,
    required this.avisos,
  });

  final String perfil;

  /// Texto para mostrar: "Grado 1", "CCN-51 conforme", "Fuera de grado".
  final String resultado;
  final bool conforme;
  final PorcentajesCorte porcentajes;

  /// Requisitos que fallan, en perfiles simples.
  final List<String> fallas;

  /// Requisitos que fallan por cada grado, en perfiles por grados.
  final Map<String, List<String>> fallasPorGrado;

  /// Advertencias que no invalidan el cálculo pero el usuario debe ver.
  final List<String> avisos;

  /// Todas las fallas en una sola lista, para mostrarlas de corrido.
  List<String> get todasLasFallas => [
        ...fallas,
        for (final e in fallasPorGrado.entries)
          ...e.value.map((f) => '${e.key}: $f'),
      ];
}

/// Calificador de la prueba de corte.
class CalificadorCorte {
  const CalificadorCorte(this.tabla);

  final TablaNorma tabla;

  /// Perfil que se usa si el usuario no eligió otro.
  static const String perfilPorDefecto = 'ccn51_referencia';

  /// Agrupa el conteo por indicador de la norma y lo pasa a porcentaje.
  ///
  /// Las clases mapeadas a `ignorar` no entran en el total: un grano que el
  /// modelo no supo clasificar no debe diluir los porcentajes de los demás.
  PorcentajesCorte porcentajes(Map<String, int> conteo) {
    if (conteo.isEmpty) {
      throw const ErrorNorma('No hay granos contados para calificar.');
    }

    final agrupado = <String, int>{};
    final desconocidas = <String>[];
    var ignorados = 0;

    conteo.forEach((clase, n) {
      if (n < 0) {
        throw ErrorNorma('La cantidad de "$clase" no puede ser negativa.');
      }
      final grupo = tabla.clasesDetector[clase];
      if (grupo == null) {
        desconocidas.add(clase);
        return;
      }
      if (grupo == 'ignorar') {
        ignorados += n;
        return;
      }
      agrupado[grupo] = (agrupado[grupo] ?? 0) + n;
    });

    final total = agrupado.values.fold<int>(0, (a, b) => a + b);
    if (total == 0) {
      throw const ErrorNorma(
        'No hay granos válidos para calificar. Revisa que las clases contadas '
        'existan en la tabla de la norma.',
      );
    }

    final valores = <String, double>{};
    agrupado.forEach((k, v) => valores[k] = 100.0 * v / total);
    for (final k in indicadoresNorma) {
      valores.putIfAbsent(k, () => 0.0);
    }
    valores['fermentado_total'] =
        valores['fermentado_bueno']! + valores['fermentado_ligero']!;

    return PorcentajesCorte(
      valores: valores,
      granosEvaluados: total,
      granosIgnorados: ignorados,
      clasesDesconocidas: desconocidas,
    );
  }

  List<String> _fallasDe(PorcentajesCorte pct, List<RequisitoNorma> requisitos) {
    final fallas = <String>[];
    for (final r in requisitos) {
      final medido = pct[r.indicador];
      if (!r.seCumpleCon(medido)) fallas.add(r.explicarFalla(medido));
    }
    return fallas;
  }

  List<String> _avisos(PorcentajesCorte pct) {
    final avisos = <String>[];
    final contados = pct.granosContados;
    if (contados < granosMinimoAviso || contados > granosMaximoAviso) {
      avisos.add(
        'Se contaron $contados granos; la norma usa $granosNorma. '
        'Revisa el conteo antes de dar el resultado por bueno.',
      );
    }
    if (pct.granosIgnorados > 0) {
      avisos.add(
        '${pct.granosIgnorados} grano(s) quedaron sin clasificar y no entraron '
        'en los porcentajes.',
      );
    }
    if (pct.clasesDesconocidas.isNotEmpty) {
      final unicas = pct.clasesDesconocidas.toSet().toList()..sort();
      avisos.add(
        'Clases que no están en la tabla de la norma y se descartaron: '
        '${unicas.join(', ')}',
      );
    }
    return avisos;
  }

  /// Califica un conteo de granos con el perfil indicado.
  ResultadoCorte calificar(
    Map<String, int> conteo, {
    String perfil = perfilPorDefecto,
  }) {
    final p = tabla.perfiles[perfil];
    if (p == null) {
      throw ErrorNorma(
        'El perfil "$perfil" no existe. '
        'Disponibles: ${tabla.perfiles.keys.join(', ')}',
      );
    }

    final pct = porcentajes(conteo);
    final avisos = _avisos(pct);

    // Perfil por grados: gana el primer grado que cumpla.
    if (p.esPorGrados) {
      final fallasPorGrado = <String, List<String>>{};
      for (final g in p.grados) {
        final fallas = _fallasDe(pct, g.requisitos);
        if (fallas.isEmpty) {
          return ResultadoCorte(
            perfil: perfil,
            resultado: g.grado,
            conforme: true,
            porcentajes: pct,
            fallas: const [],
            fallasPorGrado: const {},
            avisos: avisos,
          );
        }
        fallasPorGrado[g.grado] = fallas;
      }
      return ResultadoCorte(
        perfil: perfil,
        resultado: p.resultadoSiNoCumple,
        conforme: false,
        porcentajes: pct,
        fallas: const [],
        fallasPorGrado: fallasPorGrado,
        avisos: avisos,
      );
    }

    // Perfil simple: cumple o no cumple.
    final fallas = _fallasDe(pct, p.requisitos);
    return ResultadoCorte(
      perfil: perfil,
      resultado: fallas.isEmpty ? p.resultadoSiCumple : p.resultadoSiNoCumple,
      conforme: fallas.isEmpty,
      porcentajes: pct,
      fallas: fallas,
      fallasPorGrado: const {},
      avisos: avisos,
    );
  }

  /// Califica con todos los perfiles de la tabla, para mostrarlos juntos.
  Map<String, ResultadoCorte> calificarTodos(Map<String, int> conteo) => {
        for (final clave in tabla.perfiles.keys)
          clave: calificar(conteo, perfil: clave),
      };
}
