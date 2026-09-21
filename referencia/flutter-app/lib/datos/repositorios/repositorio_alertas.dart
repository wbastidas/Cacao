/// Guarda y consulta las alertas que produce el motor de reglas (RF-ALE-01).
///
/// La clave está en no repetir: si la fermentación lleva tres días fría, el
/// usuario no necesita doce alertas idénticas, necesita una que siga abierta.
library;

import 'package:drift/drift.dart';

import '../../nucleo/reglas/alerta.dart';
import '../bd/base_datos.dart';
import '../bd/tablas/comunes.dart';
import 'repositorio_base.dart';

class RepositorioAlertas extends RepositorioBase {
  const RepositorioAlertas(super.bd, super.sync);

  /// Registra las alertas nuevas y devuelve cuáles se guardaron.
  ///
  /// Una alerta con la misma clave que otra ya abierta no se duplica: se deja
  /// la original. Así la lista de "alertas abiertas" es útil en vez de ser un
  /// muro de repeticiones.
  Future<List<Alerta>> registrar(
    List<Alerta> alertas, {
    String? loteId,
    String? loteProduccionId,
  }) async {
    if (alertas.isEmpty) return const [];
    final nuevas = <Alerta>[];

    for (final a in alertas) {
      final clave = a.claveDeduplicacion(loteId ?? loteProduccionId ?? '-');
      final abierta = await (bd.select(bd.alertas)
            ..where((t) =>
                t.claveDedup.equals(clave) &
                t.estado.equals('abierta') &
                t.eliminado.equals(false)))
          .getSingleOrNull();
      if (abierta != null) continue;

      final id = uuidGenerador.v4();
      await bd.into(bd.alertas).insert(
            AlertasCompanion.insert(
              id: Value(id),
              loteId: Value(loteId),
              loteProduccionId: Value(loteProduccionId),
              regla: a.regla,
              severidad: Value(a.severidad.index),
              quePaso: a.quePaso,
              porQueImporta: Value(a.porQueImporta),
              queHacer: Value(a.queHacer),
              correccionCodigo: Value(a.correccion ?? ''),
              valorMedido: Value(a.valorMedido),
              valorEsperado: Value(a.valorEsperado),
              fecha: DateTime.now(),
              claveDedup: Value(clave),
            ),
          );
      await encolar('alertas', id, 'crear', carga: {'regla': a.regla});
      nuevas.add(a);
    }
    return nuevas;
  }

  Stream<List<AlertaGuardada>> observarAbiertas({String? loteId}) {
    final consulta = bd.select(bd.alertas)
      ..where((t) => t.estado.equals('abierta') & t.eliminado.equals(false))
      ..orderBy([
        (t) => OrderingTerm(expression: t.severidad, mode: OrderingMode.desc),
        (t) => OrderingTerm(expression: t.fecha, mode: OrderingMode.desc),
      ]);
    if (loteId != null) consulta.where((t) => t.loteId.equals(loteId));
    return consulta.watch();
  }

  Future<List<AlertaGuardada>> deLote(String loteId) => (bd.select(bd.alertas)
        ..where((t) => t.loteId.equals(loteId) & t.eliminado.equals(false))
        ..orderBy([(t) => OrderingTerm(expression: t.fecha)]))
      .get();

  /// ¿Hay algún bloqueo activo sobre este lote? (RN-08, RN-15)
  Future<AlertaGuardada?> bloqueoActivo(String loteId) => (bd.select(bd.alertas)
        ..where((t) =>
            t.loteId.equals(loteId) &
            t.estado.equals('abierta') &
            t.severidad.equals(Severidad.bloqueante.index) &
            t.eliminado.equals(false))
        ..limit(1))
      .getSingleOrNull();

  /// El usuario aplicó una corrección y da la alerta por atendida (RF-COR-02).
  Future<void> atender(
    String alertaId, {
    String? correccionCodigo,
    String resultado = '',
  }) async {
    await bd.transaction(() async {
      await (bd.update(bd.alertas)..where((t) => t.id.equals(alertaId))).write(
        AlertasCompanion(
          estado: const Value('atendida'),
          modificadoEn: Value(DateTime.now()),
          // Vuelve a la cola: el cambio de estado también hay que subirlo.
          estadoSync: const Value(EstadoSync.pendiente),
        ),
      );
      if (correccionCodigo != null) {
        await bd.into(bd.correccionesAplicadas).insert(
              CorreccionesAplicadasCompanion.insert(
                alertaId: alertaId,
                correccionCodigo: correccionCodigo,
                fecha: DateTime.now(),
                resultado: Value(resultado),
              ),
            );
      }
    });
    await encolar('alertas', alertaId, 'actualizar');
  }

  /// El usuario decide que la alerta no aplica.
  Future<void> descartar(String alertaId) async {
    await (bd.update(bd.alertas)..where((t) => t.id.equals(alertaId))).write(
      AlertasCompanion(
        estado: const Value('descartada'),
        modificadoEn: Value(DateTime.now()),
      ),
    );
    await encolar('alertas', alertaId, 'actualizar');
  }

  /// Historial de correcciones aplicadas a un lote (RF-COR-03).
  Future<List<CorreccionAplicada>> correccionesDeLote(
      String loteId) async {
    final alertas = await deLote(loteId);
    if (alertas.isEmpty) return const [];
    final ids = alertas.map((a) => a.id).toList();
    return (bd.select(bd.correccionesAplicadas)
          ..where((t) => t.alertaId.isIn(ids))
          ..orderBy([(t) => OrderingTerm(expression: t.fecha)]))
        .get();
  }
}
