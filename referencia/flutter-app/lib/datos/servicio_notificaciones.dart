/// Notificaciones locales programadas (RF-ALE-02).
///
/// Son LOCALES a propósito: se programan en el teléfono y suenan aunque no
/// haya señal, que es justo lo que pasa en la finca. No hacen falta Cloud
/// Functions ni push, y por eso la app opera con el plan gratuito de Firebase.
library;

import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:timezone/data/latest_all.dart' as zonas;
import 'package:timezone/timezone.dart' as tz;

/// Canales de notificación, uno por tipo de aviso, para que el usuario pueda
/// silenciar los recordatorios sin perder las alertas importantes.
class CanalesNotificacion {
  static const recordatorios = AndroidNotificationChannel(
    'recordatorios',
    'Recordatorios',
    description: 'Volteos, apertura de mazorcas, refinado e inspecciones',
    importance: Importance.high,
  );

  static const alertas = AndroidNotificationChannel(
    'alertas',
    'Alertas',
    description: 'Cuando una lectura se sale de los umbrales',
    importance: Importance.max,
  );
}

class ServicioNotificaciones {
  ServicioNotificaciones([FlutterLocalNotificationsPlugin? plugin])
      : _plugin = plugin ?? FlutterLocalNotificationsPlugin();

  final FlutterLocalNotificationsPlugin _plugin;
  bool _listo = false;

  /// Identificadores estables por tipo de aviso, para poder reemplazar un
  /// recordatorio en vez de acumular diez iguales.
  static int idVolteo(String fermentacionId) =>
      'volteo_$fermentacionId'.hashCode & 0x7fffffff;
  static int idApertura(String loteId) =>
      'apertura_$loteId'.hashCode & 0x7fffffff;
  static int idInspeccion(String loteId) =>
      'inspeccion_$loteId'.hashCode & 0x7fffffff;

  Future<void> iniciar() async {
    if (_listo) return;
    zonas.initializeTimeZones();
    // Guayaquil. Si el teléfono está en otra zona, el usuario la cambia en
    // Ajustes del sistema y las notificaciones siguen su hora local.
    tz.setLocalLocation(tz.getLocation('America/Guayaquil'));

    await _plugin.initialize(
      const InitializationSettings(
        android: AndroidInitializationSettings('@mipmap/ic_launcher'),
        iOS: DarwinInitializationSettings(),
      ),
    );

    final android = _plugin.resolvePlatformSpecificImplementation<
        AndroidFlutterLocalNotificationsPlugin>();
    await android?.createNotificationChannel(CanalesNotificacion.recordatorios);
    await android?.createNotificationChannel(CanalesNotificacion.alertas);
    _listo = true;
  }

  /// Pide permiso. Si el usuario lo niega, la app sigue funcionando: los
  /// avisos aparecen igual dentro de la pantalla de inicio.
  Future<bool> pedirPermiso() async {
    await iniciar();
    final android = _plugin.resolvePlatformSpecificImplementation<
        AndroidFlutterLocalNotificationsPlugin>();
    final concedido = await android?.requestNotificationsPermission();
    return concedido ?? true;
  }

  NotificationDetails _detalles({bool esAlerta = false}) {
    final canal = esAlerta
        ? CanalesNotificacion.alertas
        : CanalesNotificacion.recordatorios;
    return NotificationDetails(
      android: AndroidNotificationDetails(
        canal.id,
        canal.name,
        channelDescription: canal.description,
        importance: canal.importance,
        priority: esAlerta ? Priority.max : Priority.high,
        styleInformation: const BigTextStyleInformation(''),
      ),
      iOS: const DarwinNotificationDetails(),
    );
  }

  /// Programa un aviso para un momento futuro.
  ///
  /// Si el momento ya pasó no se programa nada: avisar de algo que tocaba
  /// ayer, hoy y a la vez, sería ruido.
  Future<void> programar({
    required int id,
    required String titulo,
    required String cuerpo,
    required DateTime cuando,
    bool esAlerta = false,
  }) async {
    await iniciar();
    if (!cuando.isAfter(DateTime.now())) return;

    await _plugin.zonedSchedule(
      id,
      titulo,
      cuerpo,
      tz.TZDateTime.from(cuando, tz.local),
      _detalles(esAlerta: esAlerta),
      // inexacto a propósito: un recordatorio de volteo no necesita el
      // minuto exacto, y el modo exacto gasta batería y exige un permiso
      // especial desde Android 12.
      androidScheduleMode: AndroidScheduleMode.inexactAllowWhileIdle,
      uiLocalNotificationDateInterpretation:
          UILocalNotificationDateInterpretation.absoluteTime,
    );
  }

  /// Recordatorio de volteo (RN-03).
  Future<void> programarVolteo({
    required String fermentacionId,
    required String codigoLote,
    required DateTime proximoVolteo,
  }) =>
      programar(
        id: idVolteo(fermentacionId),
        titulo: 'Toca voltear el lote $codigoLote',
        cuerpo: 'Mueve toda la masa, incluidas las esquinas, y registra el '
            'volteo en la app.',
        cuando: proximoVolteo,
      );

  /// Aviso del día de apertura de las mazorcas (RF-REC-05).
  Future<void> programarApertura({
    required String loteId,
    required String codigoLote,
    required DateTime fechaApertura,
  }) =>
      programar(
        id: idApertura(loteId),
        titulo: 'Hoy toca abrir las mazorcas del lote $codigoLote',
        cuerpo: 'Terminaron los días de reposo. Abre, pesa la baba y empieza '
            'la fermentación.',
        // A las 7 de la mañana: la apertura se hace temprano.
        cuando: DateTime(fechaApertura.year, fechaApertura.month,
            fechaApertura.day, 7),
      );

  /// Inspección periódica del almacén (RF-ALM-02).
  Future<void> programarInspeccion({
    required String loteId,
    required String codigoLote,
    required DateTime cuando,
  }) =>
      programar(
        id: idInspeccion(loteId),
        titulo: 'Inspecciona los sacos del lote $codigoLote',
        cuerpo: 'Revisa humedad, olor, plagas y moho.',
        cuando: cuando,
      );

  /// Avisa ahora mismo de una alerta que acaba de dispararse.
  Future<void> avisarAlerta({
    required String titulo,
    required String cuerpo,
  }) async {
    await iniciar();
    await _plugin.show(
      DateTime.now().millisecondsSinceEpoch.remainder(100000),
      titulo,
      cuerpo,
      _detalles(esAlerta: true),
    );
  }

  Future<void> cancelar(int id) async {
    await iniciar();
    await _plugin.cancel(id);
  }

  Future<void> cancelarTodo() async {
    await iniciar();
    await _plugin.cancelAll();
  }

  /// Lo que hay programado, para poder mostrarlo en Ajustes.
  Future<List<PendingNotificationRequest>> pendientes() async {
    await iniciar();
    return _plugin.pendingNotificationRequests();
  }
}
