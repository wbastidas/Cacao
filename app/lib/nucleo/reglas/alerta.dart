/// Alertas que produce el motor de reglas.
///
/// Cada alerta responde tres preguntas (RF-ALE-03): qué pasó, por qué importa y
/// qué hacer. La tercera es un enlace a la biblioteca de correcciones.
library;

/// Qué tan urgente es.
enum Severidad {
  /// Conviene revisarlo, pero nada se echa a perder hoy.
  aviso,

  /// Hay que actuar ahora o el lote se daña.
  urgente,

  /// Impide continuar hasta que el usuario confirme (RN-08, RN-15).
  bloqueante,
}

extension SeveridadTexto on Severidad {
  String get etiqueta => switch (this) {
        Severidad.aviso => 'Aviso',
        Severidad.urgente => 'Atención',
        Severidad.bloqueante => 'Bloqueo',
      };
}

/// Una alerta concreta sobre un lote.
class Alerta {
  const Alerta({
    required this.regla,
    required this.severidad,
    required this.quePaso,
    required this.porQueImporta,
    required this.queHacer,
    this.correccion,
    this.valorMedido,
    this.valorEsperado,
  });

  /// Código de la regla que la disparó: RN-01 … RN-17.
  final String regla;
  final Severidad severidad;

  /// Titular corto: "La fermentación está fría".
  final String quePaso;

  /// Consecuencia, en palabras simples.
  final String porQueImporta;

  /// Acción inmediata, en una frase.
  final String queHacer;

  /// Código de la corrección asociada (C-01 … C-09), si la hay.
  final String? correccion;

  final double? valorMedido;
  final double? valorEsperado;

  bool get bloquea => severidad == Severidad.bloqueante;

  /// Clave estable para no repetir la misma alerta una y otra vez.
  String claveDeduplicacion(String loteId) => '$regla|$loteId';

  @override
  String toString() => '[$regla] $quePaso';
}
