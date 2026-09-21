/// Punto único donde se arma la app: base de datos, sincronización,
/// repositorios y modelos de IA.
///
/// Tenerlo en un solo sitio evita que las pantallas creen sus propias
/// instancias de la base, que es la forma más rápida de acabar con dos
/// conexiones peleándose por el mismo archivo.
library;

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/widgets.dart';

import 'datos/bd/base_datos.dart';
import 'datos/repositorios/repositorio_alertas.dart';
import 'datos/repositorios/repositorio_apoyo.dart';
import 'datos/repositorios/repositorio_configuracion.dart';
import 'datos/repositorios/repositorio_lotes.dart';
import 'datos/repositorios/repositorio_produccion.dart';
import 'datos/planificador_avisos.dart';
import 'datos/servicio_notificaciones.dart';
import 'datos/sync/servicio_sincronizacion.dart';
import 'datos/sync/sincronizador_remoto.dart';
import 'ia/servicio_modelos.dart';

class Servicios {
  Servicios._({
    required this.bd,
    required this.sync,
    required this.config,
    required this.alertas,
    required this.lotes,
    required this.produccion,
    required this.apoyo,
    required this.modelos,
    required this.notificaciones,
  });

  final BaseDatos bd;
  final ServicioSincronizacion sync;
  final RepositorioConfiguracion config;
  final RepositorioAlertas alertas;
  final RepositorioLotes lotes;
  final RepositorioProduccion produccion;
  final RepositorioApoyo apoyo;
  final ServicioModelos modelos;
  final ServicioNotificaciones notificaciones;

  /// Recalcula todos los recordatorios a partir del estado de la base.
  ///
  /// Se llama al abrir la app: así los avisos siguen siendo correctos aunque
  /// el teléfono haya estado apagado o el usuario haya cambiado un umbral.
  Future<int> reprogramarAvisos() async {
    final umbrales = await config.umbrales();
    return PlanificadorAvisos(bd: bd, notificaciones: notificaciones)
        .reprogramar(umbrales);
  }

  /// Arranca todo. Los modelos de IA se cargan aparte y sin bloquear: si no
  /// están, la app funciona igual (RF-IA-01 se cumple cuando existan).
  static Future<Servicios> arrancar({BaseDatos? baseDatos}) async {
    final bd = baseDatos ?? BaseDatos();
    final conectividad = Connectivity();

    final sync = ServicioSincronizacion(
      bd: bd,
      remoto: const SincronizadorLocal(),
      hayConexion: () async {
        final estado = await conectividad.checkConnectivity();
        return !estado.contains(ConnectivityResult.none) && estado.isNotEmpty;
      },
      hayWifi: () async {
        final estado = await conectividad.checkConnectivity();
        return estado.contains(ConnectivityResult.wifi) ||
            estado.contains(ConnectivityResult.ethernet);
      },
    );

    final config = RepositorioConfiguracion(bd, sync);
    sync.soloWifiParaFotos = await config.subirFotosSoloConWifi();

    final alertas = RepositorioAlertas(bd, sync);
    final servicios = Servicios._(
      bd: bd,
      sync: sync,
      config: config,
      alertas: alertas,
      lotes: RepositorioLotes(bd, sync, config: config, alertas: alertas),
      produccion:
          RepositorioProduccion(bd, sync, config: config, alertas: alertas),
      apoyo: RepositorioApoyo(bd, sync, config: config, alertas: alertas),
      modelos: ServicioModelos(),
      notificaciones: ServicioNotificaciones(),
    );

    // No se espera: que falte un modelo no puede retrasar el arranque.
    unawaited(servicios.modelos.cargarTodos());
    return servicios;
  }

  Future<void> cerrar() async {
    modelos.cerrar();
    await sync.cerrar();
    await bd.close();
  }
}

void unawaited(Future<void> futuro) {}

/// Acceso a los servicios desde cualquier pantalla.
class ProveedorServicios extends InheritedWidget {
  const ProveedorServicios({
    super.key,
    required this.servicios,
    required super.child,
  });

  final Servicios servicios;

  static Servicios de(BuildContext context) {
    final p =
        context.dependOnInheritedWidgetOfExactType<ProveedorServicios>();
    assert(p != null, 'Falta ProveedorServicios encima de esta pantalla');
    return p!.servicios;
  }

  @override
  bool updateShouldNotify(ProveedorServicios anterior) =>
      servicios != anterior.servicios;
}
