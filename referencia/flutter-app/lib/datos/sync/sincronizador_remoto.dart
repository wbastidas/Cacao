/// Contrato con la nube.
///
/// La app NO depende de Firebase ni de Google Drive: depende de esta interfaz.
/// Eso permite tres cosas:
///
///  1. la app funciona completa sin credenciales de nube (RNF-01);
///  2. se puede probar la sincronización sin red, con un doble de pruebas;
///  3. cambiar de proveedor no obliga a tocar ni la UI ni la base de datos.
///
/// Para activar la nube de verdad, implementa esta interfaz con Firestore y
/// Drive y cámbiala en `proveedor_sincronizador.dart`. Ver docs/NUBE.md.
library;

import '../bd/base_datos.dart';

/// Cómo terminó una operación de sincronización.
enum ResultadoSync {
  /// Subido y confirmado.
  exito,

  /// Falló pero se puede reintentar (sin señal, servidor caído).
  reintentar,

  /// Falló y reintentar no va a servir (dato inválido, permiso denegado).
  fallaPermanente,
}

/// Estado general de la sincronización, para el indicador de la pantalla de
/// inicio (RF-SYN-06).
class EstadoSincronizacion {
  const EstadoSincronizacion({
    required this.hayConexion,
    required this.pendientes,
    required this.sincronizando,
    this.ultimoError = '',
    this.ultimaSincronizacion,
  });

  final bool hayConexion;
  final int pendientes;
  final bool sincronizando;
  final String ultimoError;
  final DateTime? ultimaSincronizacion;

  /// Texto exacto que pide RF-SYN-06.
  String get etiqueta {
    if (!hayConexion) return 'Sin conexión';
    if (sincronizando) return 'Sincronizando…';
    if (pendientes == 0) return 'Todo sincronizado';
    return '$pendientes pendientes';
  }

  bool get todoAlDia => hayConexion && pendientes == 0 && !sincronizando;
}

/// Lo que la app necesita de la nube.
abstract class SincronizadorRemoto {
  /// Nombre para mostrar en Ajustes: "Solo este teléfono", "Firebase", …
  String get nombre;

  /// ¿Está configurado y con sesión iniciada?
  Future<bool> estaDisponible();

  /// Sube un registro (crear, actualizar o eliminar).
  Future<ResultadoSync> enviarRegistro(OperacionSync operacion);

  /// Sube el archivo de una foto o un PDF.
  ///
  /// [soloWifi] viene de la preferencia del usuario, activada por defecto
  /// (RF-SYN-04). Si es true y no hay WiFi, debe devolver [ResultadoSync.reintentar].
  Future<ResultadoSync> subirArchivo(OperacionSync operacion,
      {required bool soloWifi});

  /// Respaldo completo semanal a Drive (RF-SYN-07).
  Future<ResultadoSync> respaldoCompleto();

  /// Trae los cambios que hicieron otros teléfonos.
  Future<ResultadoSync> descargarCambios();
}

/// Sincronizador que no sale del teléfono.
///
/// Es el que usa la app mientras no haya credenciales de nube. Vacía la cola
/// marcando todo como sincronizado, de modo que la app se comporta igual que
/// con nube real y la sincronización se puede probar de punta a punta.
///
/// NO es un stub vacío: cumple el contrato completo, y por eso al conectar
/// Firebase no hay que cambiar nada más que esta pieza.
class SincronizadorLocal implements SincronizadorRemoto {
  const SincronizadorLocal();

  @override
  String get nombre => 'Solo este teléfono';

  @override
  Future<bool> estaDisponible() async => true;

  @override
  Future<ResultadoSync> enviarRegistro(OperacionSync operacion) async =>
      ResultadoSync.exito;

  @override
  Future<ResultadoSync> subirArchivo(OperacionSync operacion,
          {required bool soloWifi}) async =>
      ResultadoSync.exito;

  @override
  Future<ResultadoSync> respaldoCompleto() async => ResultadoSync.exito;

  @override
  Future<ResultadoSync> descargarCambios() async => ResultadoSync.exito;
}
