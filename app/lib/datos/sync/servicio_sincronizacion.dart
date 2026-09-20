/// Servicio que vacía la cola de salida hacia la nube (RF-SYN-01 a RF-SYN-07).
///
/// Reglas que implementa:
///  · primero los registros, después los archivos pesados (RF-SYN-03);
///  · reintentos con retroceso exponencial, para no gastar batería ni datos
///    insistiendo contra un servidor caído;
///  · las fotos solo con WiFi si el usuario dejó activa esa opción (RF-SYN-04);
///  · una falla permanente no bloquea la cola: se aparta y se sigue.
library;

import 'dart:async';

import 'package:drift/drift.dart';

import '../bd/base_datos.dart';
import '../bd/tablas/comunes.dart';
import 'sincronizador_remoto.dart';

/// Cuánto esperar antes de reintentar, según cuántas veces ya falló.
///
/// 1 min, 5 min, 15 min, 1 h, 6 h, y de ahí no pasa. Un teléfono en la finca
/// puede estar días sin señal: insistir cada minuto solo gastaría batería.
const List<Duration> esperasReintento = [
  Duration(minutes: 1),
  Duration(minutes: 5),
  Duration(minutes: 15),
  Duration(hours: 1),
  Duration(hours: 6),
];

/// Tras estos intentos, la operación se marca con error y deja de reintentarse
/// sola; el usuario la puede reintentar a mano desde Ajustes.
const int intentosMaximos = 12;

class ServicioSincronizacion {
  ServicioSincronizacion({
    required this.bd,
    required this.remoto,
    this.hayConexion,
    this.hayWifi,
    this.soloWifiParaFotos = true,
  });

  final BaseDatos bd;
  final SincronizadorRemoto remoto;

  /// Cómo saber si hay red. Se inyecta para poder probar sin dispositivo.
  final Future<bool> Function()? hayConexion;
  final Future<bool> Function()? hayWifi;

  /// RF-SYN-04: activada por defecto.
  bool soloWifiParaFotos;

  bool _corriendo = false;
  DateTime? _ultimaSincronizacion;
  String _ultimoError = '';

  final _estado = StreamController<EstadoSincronizacion>.broadcast();

  /// Estado para el indicador de la pantalla de inicio (RF-SYN-06).
  Stream<EstadoSincronizacion> get estado => _estado.stream;

  /// Encola una operación. Lo llaman los repositorios en cada escritura.
  Future<void> encolar({
    required String tabla,
    required String registroId,
    required String operacion,
    String cargaJson = '{}',
    bool esArchivo = false,
  }) async {
    await bd.into(bd.colaSync).insert(
          ColaSyncCompanion.insert(
            tabla: tabla,
            registroId: registroId,
            operacion: operacion,
            cargaJson: Value(cargaJson),
            esArchivo: Value(esArchivo),
            proximoIntento: Value(DateTime.now()),
          ),
        );
    await _emitirEstado();
  }

  /// Cuántas operaciones esperan subida.
  Future<int> pendientes() async {
    final consulta = bd.selectOnly(bd.colaSync)
      ..addColumns([bd.colaSync.id.count()]);
    final fila = await consulta.getSingle();
    return fila.read(bd.colaSync.id.count()) ?? 0;
  }

  Future<EstadoSincronizacion> estadoActual() async =>
      EstadoSincronizacion(
        hayConexion: await (hayConexion?.call() ?? Future.value(false)),
        pendientes: await pendientes(),
        sincronizando: _corriendo,
        ultimoError: _ultimoError,
        ultimaSincronizacion: _ultimaSincronizacion,
      );

  Future<void> _emitirEstado() async {
    if (_estado.hasListener) _estado.add(await estadoActual());
  }

  /// Vacía la cola. Devuelve cuántas operaciones se subieron.
  ///
  /// Si ya hay una sincronización en curso, no arranca otra: dos a la vez
  /// duplicarían registros en la nube.
  Future<int> sincronizar() async {
    // La bandera se levanta ANTES del primer await. Si se comprobara después,
    // dos llamadas casi simultáneas pasarían las dos el guardia y subirían el
    // mismo registro dos veces a la nube.
    if (_corriendo) return 0;
    _corriendo = true;

    if (!await (hayConexion?.call() ?? Future.value(false))) {
      _corriendo = false;
      return 0;
    }
    if (!await remoto.estaDisponible()) {
      _corriendo = false;
      return 0;
    }

    _ultimoError = '';
    await _emitirEstado();
    var subidas = 0;

    try {
      final ahora = DateTime.now();
      final conWifi = await (hayWifi?.call() ?? Future.value(false));

      // Primero los registros y después los archivos: si se corta la señal a
      // media subida, lo que se salva son los datos, que es lo que importa.
      final consulta = bd.select(bd.colaSync)
        ..where((t) =>
            t.proximoIntento.isSmallerOrEqualValue(ahora) |
            t.proximoIntento.isNull())
        ..orderBy([
          (t) => OrderingTerm(expression: t.esArchivo),
          (t) => OrderingTerm(expression: t.creadoEn),
        ]);

      for (final op in await consulta.get()) {
        // Una foto pesada se queda esperando al WiFi, pero no bloquea el resto.
        if (op.esArchivo && soloWifiParaFotos && !conWifi) continue;

        final resultado = op.esArchivo
            ? await remoto.subirArchivo(op, soloWifi: soloWifiParaFotos)
            : await remoto.enviarRegistro(op);

        switch (resultado) {
          case ResultadoSync.exito:
            await (bd.delete(bd.colaSync)..where((t) => t.id.equals(op.id)))
                .go();
            await _marcarSincronizado(op);
            subidas++;
          case ResultadoSync.reintentar:
            await _programarReintento(op, 'Sin respuesta; se reintentará');
          case ResultadoSync.fallaPermanente:
            // No se borra: queda para que el usuario la vea en Ajustes y
            // decida. Borrarla en silencio sería perder un dato suyo.
            await _marcarError(op, 'No se pudo subir');
        }
      }
      _ultimaSincronizacion = DateTime.now();
    } catch (e) {
      _ultimoError = e.toString();
    } finally {
      _corriendo = false;
      await _emitirEstado();
    }
    return subidas;
  }

  Future<void> _programarReintento(OperacionSync op, String error) async {
    final intentos = op.intentos + 1;
    if (intentos >= intentosMaximos) {
      await _marcarError(op, 'Se agotaron los reintentos');
      return;
    }
    final espera = esperasReintento[
        intentos.clamp(0, esperasReintento.length - 1)];
    await (bd.update(bd.colaSync)..where((t) => t.id.equals(op.id))).write(
      ColaSyncCompanion(
        intentos: Value(intentos),
        proximoIntento: Value(DateTime.now().add(espera)),
        ultimoError: Value(error),
      ),
    );
  }

  Future<void> _marcarError(OperacionSync op, String error) async {
    await (bd.update(bd.colaSync)..where((t) => t.id.equals(op.id))).write(
      ColaSyncCompanion(
        intentos: Value(intentosMaximos),
        proximoIntento: const Value(null),
        ultimoError: Value(error),
      ),
    );
  }

  /// Marca el registro original como sincronizado.
  ///
  /// Se hace por SQL genérico porque la cola guarda el nombre de la tabla como
  /// texto: escribir un `switch` con 30 casos sería el mismo código repetido.
  Future<void> _marcarSincronizado(OperacionSync op) async {
    if (op.operacion == 'subir_foto' || op.tabla.isEmpty) return;
    try {
      await customStatementSeguro(
        'UPDATE ${op.tabla} SET estado_sync = ? WHERE id = ?',
        [EstadoSync.sincronizado.index, op.registroId],
      );
      // Drift no se entera de lo que cambia un customStatement, así que hay
      // que avisarle: sin esto, una lista abierta en pantalla seguiría
      // mostrando "pendiente" después de sincronizar.
      notificarTablaCambiada(op.tabla);
    } catch (_) {
      // Si la tabla ya no existe (migración), no vale la pena fallar por esto.
    }
  }

  /// Envoltura sobre customStatement, aislada para poder simularla en tests.
  Future<void> customStatementSeguro(String sql, List<Object?> args) =>
      bd.customStatement(sql, args);

  /// Avisa a Drift de que una tabla cambió, para que refresque los streams.
  void notificarTablaCambiada(String nombreTabla) {
    for (final tabla in bd.allTables) {
      if (tabla.actualTableName == nombreTabla) {
        bd.notifyUpdates({TableUpdate.onTable(tabla, kind: UpdateKind.update)});
        return;
      }
    }
  }

  /// Operaciones que quedaron con error y el usuario puede reintentar a mano.
  Future<List<OperacionSync>> conError() => (bd.select(bd.colaSync)
        ..where((t) => t.intentos.isBiggerOrEqualValue(intentosMaximos)))
      .get();

  /// Pone a cero los contadores para volver a intentarlo todo ahora.
  Future<void> reintentarTodo() async {
    await bd.update(bd.colaSync).write(
          ColaSyncCompanion(
            intentos: const Value(0),
            proximoIntento: Value(DateTime.now()),
            ultimoError: const Value(''),
          ),
        );
    await sincronizar();
  }

  Future<void> cerrar() => _estado.close();
}
