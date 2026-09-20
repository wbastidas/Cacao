/// Programa los recordatorios a partir del estado real de la base.
///
/// Se recalcula todo cada vez, en vez de ir añadiendo avisos sueltos: así no
/// quedan recordatorios huérfanos de un lote que ya se cerró, y da igual si el
/// teléfono estuvo apagado tres días.
library;

import 'package:drift/drift.dart';

import '../nucleo/modelo/etapas.dart';
import '../nucleo/reglas/umbrales.dart';
import 'bd/base_datos.dart';
import 'servicio_notificaciones.dart';

class PlanificadorAvisos {
  const PlanificadorAvisos({
    required this.bd,
    required this.notificaciones,
  });

  final BaseDatos bd;
  final ServicioNotificaciones notificaciones;

  /// Vuelve a programar todos los recordatorios pendientes.
  ///
  /// Devuelve cuántos quedaron programados, para poder mostrarlo en Ajustes.
  Future<int> reprogramar(Umbrales umbrales) async {
    await notificaciones.cancelarTodo();
    var programados = 0;

    final lotes = await (bd.select(bd.lotes)
          ..where((t) =>
              t.eliminado.equals(false) &
              t.estado.isNotIn([
                EstadoLote.vendido.index,
                EstadoLote.descartado.index,
              ])))
        .get();

    for (final lote in lotes) {
      // Apertura de las mazorcas (RF-REC-05).
      if (lote.estado.index <= EstadoLote.reposo.index) {
        final recepcion = await (bd.select(bd.recepciones)
              ..where((t) => t.loteId.equals(lote.id)))
            .getSingleOrNull();
        final fecha = recepcion?.fechaAperturaPlan;
        if (fecha != null && fecha.isAfter(DateTime.now())) {
          await notificaciones.programarApertura(
            loteId: lote.id,
            codigoLote: lote.codigo,
            fechaApertura: fecha,
          );
          programados++;
        }
      }

      // Próximo volteo (RN-03).
      final ferm = await (bd.select(bd.fermentaciones)
            ..where((t) =>
                t.loteId.equals(lote.id) &
                t.fin.isNull() &
                t.eliminado.equals(false)))
          .getSingleOrNull();
      if (ferm != null) {
        final ultimo = await (bd.select(bd.volteos)
              ..where((t) => t.fermentacionId.equals(ferm.id))
              ..orderBy([
                (t) => OrderingTerm(
                    expression: t.fechaHora, mode: OrderingMode.desc)
              ])
              ..limit(1))
            .getSingleOrNull();
        final desde = ultimo?.fechaHora ?? ferm.inicio;
        final proximo =
            desde.add(Duration(hours: umbrales.entero('volteo_horas')));
        if (proximo.isAfter(DateTime.now())) {
          await notificaciones.programarVolteo(
            fermentacionId: ferm.id,
            codigoLote: lote.codigo,
            proximoVolteo: proximo,
          );
          programados++;
        }
      }

      // Inspección del almacén (RF-ALM-02).
      if (lote.estado == EstadoLote.almacenado) {
        final ultima = await (bd.select(bd.inspeccionesAlmacen)
              ..where((t) => t.loteId.equals(lote.id))
              ..orderBy([
                (t) =>
                    OrderingTerm(expression: t.fecha, mode: OrderingMode.desc)
              ])
              ..limit(1))
            .getSingleOrNull();
        final desde = ultima?.fecha ?? DateTime.now();
        final proxima = desde
            .add(Duration(days: umbrales.entero('almacen_dias_inspeccion')));
        if (proxima.isAfter(DateTime.now())) {
          await notificaciones.programarInspeccion(
            loteId: lote.id,
            codigoLote: lote.codigo,
            cuando: proxima,
          );
          programados++;
        }
      }
    }
    return programados;
  }
}
