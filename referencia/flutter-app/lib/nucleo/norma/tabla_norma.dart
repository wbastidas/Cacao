/// Tabla de la norma NTE INEN 176 para la prueba de corte.
///
/// La tabla es un DATO, no código: viene de `assets/norma/norma_inen176.json`,
/// el usuario puede editarla desde Ajustes y puede actualizarse por Remote Config
/// sin publicar una versión nueva de la app (RF-CFG-03, RNF-12).
///
/// Es el mismo archivo que usa `entrenamiento/calificar_corte.py`, para que el
/// resultado en la computadora y en el teléfono sea idéntico.
library;

import 'dart:convert';

/// Un requisito de la norma: "fermentado_total, como mínimo, 75 %".
class RequisitoNorma {
  const RequisitoNorma({
    required this.indicador,
    required this.tipo,
    required this.valor,
  });

  final String indicador;

  /// `min` o `max`.
  final String tipo;
  final double valor;

  bool get esMinimo => tipo == 'min';

  /// ¿El valor medido cumple este requisito?
  bool seCumpleCon(double medido) =>
      esMinimo ? medido >= valor : medido <= valor;

  /// Texto para el usuario: "fermentado total = 70,0 % (requiere ≥ 75 %)".
  String explicarFalla(double medido) {
    final signo = esMinimo ? '≥' : '≤';
    final nombre = indicador.replaceAll('_', ' ');
    return '$nombre = ${medido.toStringAsFixed(1)} % '
        '(requiere $signo ${valor.toStringAsFixed(0)} %)';
  }

  factory RequisitoNorma.desdeJson(Map<String, dynamic> j) => RequisitoNorma(
        indicador: j['indicador'] as String,
        tipo: j['tipo'] as String,
        valor: (j['valor'] as num).toDouble(),
      );

  Map<String, dynamic> aJson() =>
      {'indicador': indicador, 'tipo': tipo, 'valor': valor};
}

/// Un grado de calidad (Grado 1, 2 o 3) con sus requisitos.
class GradoNorma {
  const GradoNorma({required this.grado, required this.requisitos});

  final String grado;
  final List<RequisitoNorma> requisitos;

  factory GradoNorma.desdeJson(Map<String, dynamic> j) => GradoNorma(
        grado: j['grado'] as String,
        requisitos: (j['requisitos'] as List)
            .map((r) => RequisitoNorma.desdeJson(r as Map<String, dynamic>))
            .toList(),
      );

  Map<String, dynamic> aJson() =>
      {'grado': grado, 'requisitos': requisitos.map((r) => r.aJson()).toList()};
}

/// Un perfil de calificación.
///
/// Hay dos formas: la simple (cumple o no cumple, como `ccn51_referencia`) y la
/// de grados (se prueba Grado 1, luego 2, luego 3, como `grados_1_2_3`).
class PerfilNorma {
  const PerfilNorma({
    required this.clave,
    required this.descripcion,
    this.requisitos = const [],
    this.grados = const [],
    this.resultadoSiCumple = '',
    this.resultadoSiNoCumple = '',
  });

  final String clave;
  final String descripcion;
  final List<RequisitoNorma> requisitos;
  final List<GradoNorma> grados;
  final String resultadoSiCumple;
  final String resultadoSiNoCumple;

  bool get esPorGrados => grados.isNotEmpty;

  factory PerfilNorma.desdeJson(String clave, Map<String, dynamic> j) =>
      PerfilNorma(
        clave: clave,
        descripcion: (j['descripcion'] as String?) ?? '',
        requisitos: ((j['requisitos'] as List?) ?? [])
            .map((r) => RequisitoNorma.desdeJson(r as Map<String, dynamic>))
            .toList(),
        grados: ((j['grados'] as List?) ?? [])
            .map((g) => GradoNorma.desdeJson(g as Map<String, dynamic>))
            .toList(),
        resultadoSiCumple: (j['resultado_si_cumple'] as String?) ?? 'Conforme',
        resultadoSiNoCumple:
            (j['resultado_si_no_cumple'] as String?) ?? 'No conforme',
      );

  Map<String, dynamic> aJson() => {
        'descripcion': descripcion,
        if (requisitos.isNotEmpty)
          'requisitos': requisitos.map((r) => r.aJson()).toList(),
        if (grados.isNotEmpty) 'grados': grados.map((g) => g.aJson()).toList(),
        'resultado_si_cumple': resultadoSiCumple,
        'resultado_si_no_cumple': resultadoSiNoCumple,
      };
}

/// La tabla completa: cómo se agrupan las clases del detector y qué perfiles hay.
class TablaNorma {
  const TablaNorma({required this.clasesDetector, required this.perfiles});

  /// Clase que reporta el modelo o el conteo manual -> indicador de la norma.
  /// El valor `ignorar` quiere decir que ese grano no entra en el cálculo.
  final Map<String, String> clasesDetector;
  final Map<String, PerfilNorma> perfiles;

  /// Clases que el usuario puede contar, en el orden en que se muestran.
  List<String> get clasesContables => clasesDetector.keys.toList();

  factory TablaNorma.desdeJsonTexto(String texto) =>
      TablaNorma.desdeJson(jsonDecode(texto) as Map<String, dynamic>);

  factory TablaNorma.desdeJson(Map<String, dynamic> j) {
    final clases = (j['clases_detector'] as Map).map(
      (k, v) => MapEntry(k as String, v as String),
    );
    final perfiles = (j['perfiles'] as Map).map(
      (k, v) => MapEntry(
        k as String,
        PerfilNorma.desdeJson(k, v as Map<String, dynamic>),
      ),
    );
    if (clases.isEmpty) {
      throw const FormatException('La tabla de la norma no tiene clases_detector');
    }
    if (perfiles.isEmpty) {
      throw const FormatException('La tabla de la norma no tiene perfiles');
    }
    return TablaNorma(clasesDetector: clases, perfiles: perfiles);
  }

  Map<String, dynamic> aJson() => {
        'clases_detector': clasesDetector,
        'perfiles': perfiles.map((k, v) => MapEntry(k, v.aJson())),
      };

  String aJsonTexto() => const JsonEncoder.withIndent('  ').convert(aJson());
}
