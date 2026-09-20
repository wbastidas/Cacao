/// Lo que devuelve un modelo de visión (RF-IA-02, RF-IA-03).
library;

/// Una clase con su probabilidad.
class Prediccion {
  const Prediccion(this.clase, this.confianza);
  final String clase;
  final double confianza;

  /// "monilia (83 %)"
  String get etiqueta => '$clase (${(confianza * 100).round()} %)';
}

/// El resultado de analizar una foto con un clasificador.
class ResultadoClasificacion {
  const ResultadoClasificacion({
    required this.predicciones,
    required this.modeloVersion,
    required this.umbralConfianza,
    this.milisegundos = 0,
  });

  /// Ordenadas de mayor a menor confianza.
  final List<Prediccion> predicciones;
  final String modeloVersion;
  final double umbralConfianza;
  final int milisegundos;

  Prediccion? get mejor =>
      predicciones.isEmpty ? null : predicciones.first;

  /// RN-16: por debajo del umbral la app no afirma nada, pregunta.
  bool get estaSeguro =>
      mejor != null && mejor!.confianza >= umbralConfianza;

  /// Lo que se le muestra al usuario.
  String get texto => mejor == null
      ? 'Sin resultado'
      : estaSeguro
          ? mejor!.etiqueta
          : 'No estoy seguro';

  /// Mensaje de apoyo cuando la confianza es baja.
  String? get pistaBajaConfianza => estaSeguro || mejor == null
      ? null
      : 'Lo más parecido es "${mejor!.clase}" con '
          '${(mejor!.confianza * 100).round()} % de seguridad, por debajo del '
          'mínimo. Revisa la foto o elige tú la respuesta.';
}

/// Una caja detectada por el modelo M2 sobre la foto del tablero.
class GranoDetectado {
  const GranoDetectado({
    required this.clase,
    required this.confianza,
    required this.x,
    required this.y,
    required this.ancho,
    required this.alto,
    this.corregidoPorUsuario = false,
  });

  final String clase;
  final double confianza;

  /// Coordenadas normalizadas 0-1 respecto de la foto: así la caja se dibuja
  /// bien sea cual sea el tamaño en pantalla.
  final double x;
  final double y;
  final double ancho;
  final double alto;

  /// True si el usuario tocó la caja y cambió su clase (RF-PRC-04).
  final bool corregidoPorUsuario;

  GranoDetectado corregir(String nuevaClase) => GranoDetectado(
        clase: nuevaClase,
        confianza: 1.0,
        x: x,
        y: y,
        ancho: ancho,
        alto: alto,
        corregidoPorUsuario: true,
      );
}

/// El resultado de detectar los granos de un tablero.
class ResultadoDeteccion {
  const ResultadoDeteccion({
    required this.granos,
    required this.modeloVersion,
    this.milisegundos = 0,
  });

  final List<GranoDetectado> granos;
  final String modeloVersion;
  final int milisegundos;

  /// Conteo por clase, que es lo que come el calificador de la norma.
  Map<String, int> get conteo {
    final c = <String, int>{};
    for (final g in granos) {
      c[g.clase] = (c[g.clase] ?? 0) + 1;
    }
    return c;
  }

  int get total => granos.length;

  /// RF-PRC-07: avisar si el conteo se sale de 97-103.
  bool get conteoDudoso => total < 97 || total > 103;

  ResultadoDeteccion conGranoCorregido(int indice, String nuevaClase) {
    final copia = [...granos];
    copia[indice] = copia[indice].corregir(nuevaClase);
    return ResultadoDeteccion(
      granos: copia,
      modeloVersion: modeloVersion,
      milisegundos: milisegundos,
    );
  }
}

/// Por qué un modelo no está disponible, para poder decírselo al usuario.
enum MotivoSinModelo {
  /// Todavía no se ha entrenado ni instalado (el caso normal al empezar).
  noInstalado,

  /// El archivo existe pero no se pudo cargar.
  errorAlCargar,
}

class ModeloNoDisponible implements Exception {
  const ModeloNoDisponible(this.tarea, this.motivo, [this.detalle = '']);

  final String tarea;
  final MotivoSinModelo motivo;
  final String detalle;

  /// Mensaje para el usuario, sin tecnicismos.
  String get mensaje => switch (motivo) {
        MotivoSinModelo.noInstalado =>
          'Todavía no hay un modelo de $tarea instalado. Puedes registrar y '
              'contar a mano mientras tanto.',
        MotivoSinModelo.errorAlCargar =>
          'El modelo de $tarea no se pudo abrir. Usa el registro manual y '
              'vuelve a instalarlo desde Ajustes.',
      };

  @override
  String toString() => mensaje;
}
