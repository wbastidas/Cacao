/// Repositorio del lote de producción: del grano seco a la barra empacada.
library;

import 'dart:convert';

import 'package:drift/drift.dart';

import '../../nucleo/calculo/receta.dart';
import '../../nucleo/modelo/etapas.dart';
import '../../nucleo/reglas/alerta.dart';
import '../../nucleo/reglas/motor_reglas.dart';
import '../bd/base_datos.dart';
import '../bd/tablas/comunes.dart';
import 'repositorio_alertas.dart';
import 'repositorio_base.dart';
import 'repositorio_configuracion.dart';

/// Una tanda con todo lo que cuelga de ella.
class ProduccionCompleta {
  const ProduccionCompleta({
    required this.produccion,
    this.origenes = const [],
    this.tostado,
    this.descascarillado,
    this.refinado,
    this.atemperados = const [],
    this.empaque,
  });

  final LoteProduccion produccion;
  final List<LoteProduccionOrigen> origenes;
  final Tostado? tostado;
  final Descascarillado? descascarillado;
  final Refinado? refinado;
  final List<Atemperado> atemperados;
  final Empaque? empaque;

  double get kgGranoUsado =>
      origenes.fold<double>(0, (a, o) => a + o.kgUsados);
}

class RepositorioProduccion extends RepositorioBase {
  RepositorioProduccion(
    super.bd,
    super.sync, {
    required this.config,
    required this.alertas,
  });

  final RepositorioConfiguracion config;
  final RepositorioAlertas alertas;

  Future<MotorReglas> _motor() async => MotorReglas(await config.umbrales());

  Stream<List<LoteProduccion>> observar() => (bd.select(bd.lotesProduccion)
        ..where((t) => t.eliminado.equals(false))
        ..orderBy([
          (t) => OrderingTerm(expression: t.fecha, mode: OrderingMode.desc)
        ]))
      .watch();

  Future<LoteProduccion?> porId(String id) =>
      (bd.select(bd.lotesProduccion)..where((t) => t.id.equals(id)))
          .getSingleOrNull();

  /// Crea una tanda a partir de uno o varios lotes de grano (RF-LOT-05).
  ///
  /// Descuenta el grano del inventario, porque salir de almacén hacia
  /// producción es un movimiento real (RF-ALM-03).
  Future<LoteProduccion> crear({
    required Map<String, double> kgPorLote,
    double porcentajeCacao = 90,
  }) async {
    if (kgPorLote.isEmpty) {
      throw ArgumentError('Hay que indicar al menos un lote de grano');
    }
    for (final entrada in kgPorLote.entries) {
      final bloqueo = await alertas.bloqueoActivo(entrada.key);
      if (bloqueo != null) {
        throw LoteBloqueado(entrada.key, bloqueo.quePaso);
      }
    }

    final ahora = DateTime.now();
    final existentes = await bd.select(bd.lotesProduccion).get();
    final codigo = siguienteCodigo(
        'P', ahora.year, existentes.map((p) => p.codigo).toList());
    final id = uuidGenerador.v4();

    await bd.transaction(() async {
      await bd.into(bd.lotesProduccion).insert(
            LotesProduccionCompanion.insert(
              id: Value(id),
              codigo: codigo,
              fecha: ahora,
              porcentajeCacao: Value(porcentajeCacao),
            ),
          );
      for (final entrada in kgPorLote.entries) {
        await bd.into(bd.lotesProduccionOrigen).insert(
              LotesProduccionOrigenCompanion.insert(
                loteProduccionId: id,
                loteId: entrada.key,
                kgUsados: entrada.value,
              ),
            );
        await bd.into(bd.movimientosInventario).insert(
              MovimientosInventarioCompanion.insert(
                item: 'grano_seco',
                tipo: 'salida',
                cantidad: entrada.value,
                fecha: ahora,
                referencia: Value(codigo),
              ),
            );
      }
    });
    await encolar('lotes_produccion', id, 'crear', carga: {'codigo': codigo});
    return (await porId(id))!;
  }

  Future<ProduccionCompleta?> completa(String id) async {
    final p = await porId(id);
    if (p == null) return null;
    return ProduccionCompleta(
      produccion: p,
      origenes: await (bd.select(bd.lotesProduccionOrigen)
            ..where((t) => t.loteProduccionId.equals(id)))
          .get(),
      tostado: await (bd.select(bd.tostados)
            ..where((t) =>
                t.loteProduccionId.equals(id) & t.eliminado.equals(false)))
          .getSingleOrNull(),
      descascarillado: await (bd.select(bd.descascarillados)
            ..where((t) =>
                t.loteProduccionId.equals(id) & t.eliminado.equals(false)))
          .getSingleOrNull(),
      refinado: await (bd.select(bd.refinados)
            ..where((t) =>
                t.loteProduccionId.equals(id) & t.eliminado.equals(false)))
          .getSingleOrNull(),
      atemperados: await (bd.select(bd.atemperados)
            ..where((t) =>
                t.loteProduccionId.equals(id) & t.eliminado.equals(false))
            ..orderBy([(t) => OrderingTerm(expression: t.fecha)]))
          .get(),
      empaque: await (bd.select(bd.empaques)
            ..where((t) =>
                t.loteProduccionId.equals(id) & t.eliminado.equals(false)))
          .getSingleOrNull(),
    );
  }

  // ------------------------------------------------------------ tostado

  /// Registra el tostado y evalúa la merma (RN-12).
  Future<List<Alerta>> guardarTostado({
    required String loteProduccionId,
    String? equipoId,
    required double kgEntrada,
    double? kgSalida,
    double? tempC,
    int? minutos,
    List<Map<String, dynamic>> perfil = const [],
    String gradoIa = '',
    double? confianzaIa,
    String gradoUsuario = '',
    String modeloVersion = '',
    String? fotoId,
    String recetaNombre = '',
  }) async {
    final existente = await (bd.select(bd.tostados)
          ..where((t) => t.loteProduccionId.equals(loteProduccionId)))
        .getSingleOrNull();
    final id = existente?.id ?? uuidGenerador.v4();

    await bd.into(bd.tostados).insertOnConflictUpdate(
          TostadosCompanion.insert(
            id: Value(id),
            loteProduccionId: loteProduccionId,
            equipoId: Value(equipoId),
            fecha: DateTime.now(),
            kgEntrada: Value(kgEntrada),
            kgSalida: Value(kgSalida),
            tempC: Value(tempC),
            minutos: Value(minutos),
            perfilJson: Value(jsonEncode(perfil)),
            gradoIa: Value(gradoIa),
            confianzaIa: Value(confianzaIa),
            gradoUsuario: Value(gradoUsuario),
            modeloVersion: Value(modeloVersion),
            fotoId: Value(fotoId),
            recetaNombre: Value(recetaNombre),
            modificadoEn: Value(DateTime.now()),
          ),
        );
    await encolar('tostados', id, 'actualizar');

    if (kgSalida == null) return const [];
    final motor = await _motor();
    final generadas = motor.evaluarTostado(
        ContextoTostado(kgEntrada: kgEntrada, kgSalida: kgSalida));
    return alertas.registrar(generadas, loteProduccionId: loteProduccionId);
  }

  // ------------------------------------------------------- descascarillado

  Future<List<Alerta>> guardarDescascarillado({
    required String loteProduccionId,
    required double kgNibs,
    required double kgCascarilla,
    String notas = '',
  }) async {
    final existente = await (bd.select(bd.descascarillados)
          ..where((t) => t.loteProduccionId.equals(loteProduccionId)))
        .getSingleOrNull();
    final id = existente?.id ?? uuidGenerador.v4();

    await bd.into(bd.descascarillados).insertOnConflictUpdate(
          DescascarilladosCompanion.insert(
            id: Value(id),
            loteProduccionId: loteProduccionId,
            fecha: DateTime.now(),
            kgNibs: Value(kgNibs),
            kgCascarilla: Value(kgCascarilla),
            notas: Value(notas),
            modificadoEn: Value(DateTime.now()),
          ),
        );
    await encolar('descascarillados', id, 'actualizar');
    await bd.into(bd.movimientosInventario).insert(
          MovimientosInventarioCompanion.insert(
            item: 'nibs',
            tipo: 'entrada',
            cantidad: kgNibs,
            fecha: DateTime.now(),
            referencia: Value(loteProduccionId),
          ),
        );

    final tostado = await (bd.select(bd.tostados)
          ..where((t) => t.loteProduccionId.equals(loteProduccionId)))
        .getSingleOrNull();
    if (tostado == null || tostado.kgSalida == null) return const [];

    final motor = await _motor();
    final generadas = motor.evaluarTostado(ContextoTostado(
      kgEntrada: tostado.kgEntrada,
      kgSalida: tostado.kgSalida!,
      kgNibs: kgNibs,
      kgCascarilla: kgCascarilla,
    ));
    return alertas.registrar(generadas, loteProduccionId: loteProduccionId);
  }

  // ------------------------------------------------------------ refinado

  /// Calcula la receta para los nibs disponibles (RF-REF-01).
  Receta calcularReceta({
    required double kgNibs,
    double porcentajeCacao = 90,
    double mantecaExtraPct = 0,
    bool usarLecitina = true,
  }) =>
      const CalculadoraReceta().calcular(
        kgNibs: kgNibs,
        porcentajeCacao: porcentajeCacao,
        mantecaExtraPct: mantecaExtraPct,
        usarLecitina: usarLecitina,
      );

  Future<String> guardarRefinado({
    required String loteProduccionId,
    required DateTime inicio,
    DateTime? fin,
    double? horas,
    required double nibsKg,
    required double azucarKg,
    double mantecaKg = 0,
    double lecitinaKg = 0,
    DateTime? momentoAzucar,
    double? tempC,
    Map<String, int> sensorial = const {},
    String notas = '',
  }) async {
    final existente = await (bd.select(bd.refinados)
          ..where((t) => t.loteProduccionId.equals(loteProduccionId)))
        .getSingleOrNull();
    final id = existente?.id ?? uuidGenerador.v4();

    await bd.into(bd.refinados).insertOnConflictUpdate(
          RefinadosCompanion.insert(
            id: Value(id),
            loteProduccionId: loteProduccionId,
            inicio: inicio,
            fin: Value(fin),
            horas: Value(horas),
            nibsKg: Value(nibsKg),
            azucarKg: Value(azucarKg),
            mantecaKg: Value(mantecaKg),
            lecitinaKg: Value(lecitinaKg),
            momentoAzucar: Value(momentoAzucar),
            tempC: Value(tempC),
            sensorialJson: Value(jsonEncode(sensorial)),
            notas: Value(notas),
            modificadoEn: Value(DateTime.now()),
          ),
        );
    await encolar('refinados', id, 'actualizar');

    // El azúcar y la manteca salen del inventario al usarse.
    for (final par in {
      'azucar': azucarKg,
      'manteca': mantecaKg,
      'lecitina': lecitinaKg,
    }.entries) {
      if (par.value > 0) {
        await bd.into(bd.movimientosInventario).insert(
              MovimientosInventarioCompanion.insert(
                item: par.key,
                tipo: 'salida',
                cantidad: par.value,
                fecha: DateTime.now(),
                referencia: Value(loteProduccionId),
              ),
            );
      }
    }
    return id;
  }

  // ------------------------------------------------------------ atemperado

  /// Registra el atemperado y evalúa RN-13 y RN-14.
  Future<List<Alerta>> guardarAtemperado({
    required String loteProduccionId,
    MetodoAtemperado metodo = MetodoAtemperado.siembra,
    Map<String, double> temperaturas = const {},
    double? tempCuarto,
    double? hrCuarto,
    bool? pruebaPapel,
    String resultadoIa = '',
    double? confianzaIa,
    String resultadoUsuario = '',
    String modeloVersion = '',
    String? fotoId,
    int? diasInspeccion,
  }) async {
    final id = uuidGenerador.v4();
    await bd.into(bd.atemperados).insert(
          AtemperadosCompanion.insert(
            id: Value(id),
            loteProduccionId: loteProduccionId,
            fecha: DateTime.now(),
            metodo: Value(metodo),
            tempsJson: Value(jsonEncode(temperaturas)),
            tempCuarto: Value(tempCuarto),
            hrCuarto: Value(hrCuarto),
            pruebaPapel: Value(pruebaPapel),
            resultadoIa: Value(resultadoIa),
            confianzaIa: Value(confianzaIa),
            resultadoUsuario: Value(resultadoUsuario),
            modeloVersion: Value(modeloVersion),
            fotoId: Value(fotoId),
            diasInspeccion: Value(diasInspeccion),
          ),
        );
    await encolar('atemperados', id, 'crear');

    final motor = await _motor();
    final generadas = motor.evaluarAtemperado(ContextoAtemperado(
      tempCuartoC: tempCuarto,
      humedadCuartoPct: hrCuarto,
      tempTrabajoC: temperaturas['trabajo'],
    ));
    return alertas.registrar(generadas, loteProduccionId: loteProduccionId);
  }

  /// ¿Se puede moldear ahora mismo en estas condiciones? (RF-ATE-02)
  Future<String?> advertenciaDelCuarto(
      {double? tempCuarto, double? hrCuarto}) async {
    final motor = await _motor();
    final generadas = motor.evaluarAtemperado(ContextoAtemperado(
        tempCuartoC: tempCuarto, humedadCuartoPct: hrCuarto));
    if (generadas.isEmpty) return null;
    return generadas.map((a) => '${a.quePaso}. ${a.queHacer}').join(' ');
  }

  // ------------------------------------------------------------ empaque

  Future<String> guardarEmpaque({
    required String loteProduccionId,
    required int barras,
    required double pesoUnitarioG,
    DateTime? fechaElaboracion,
    int vidaUtilMeses = 12,
    Map<String, dynamic> etiqueta = const {},
  }) async {
    final elaboracion = fechaElaboracion ?? DateTime.now();
    final vencimiento = DateTime(
      elaboracion.year,
      elaboracion.month + vidaUtilMeses,
      elaboracion.day,
    );
    final id = uuidGenerador.v4();

    await bd.transaction(() async {
      await bd.into(bd.empaques).insert(
            EmpaquesCompanion.insert(
              id: Value(id),
              loteProduccionId: loteProduccionId,
              barras: Value(barras),
              pesoUnitarioG: Value(pesoUnitarioG),
              fechaElaboracion: elaboracion,
              fechaVencimiento: vencimiento,
              vidaUtilMeses: Value(vidaUtilMeses),
              etiquetaJson: Value(jsonEncode(etiqueta)),
              codigoQr: Value(loteProduccionId),
            ),
          );
      await (bd.update(bd.lotesProduccion)
            ..where((t) => t.id.equals(loteProduccionId)))
          .write(LotesProduccionCompanion(
        kgChocolate: Value(barras * pesoUnitarioG / 1000),
        estado: const Value('terminado'),
        modificadoEn: Value(DateTime.now()),
        estadoSync: const Value(EstadoSync.pendiente),
      ));
      await bd.into(bd.movimientosInventario).insert(
            MovimientosInventarioCompanion.insert(
              item: 'chocolate',
              tipo: 'entrada',
              cantidad: barras.toDouble(),
              unidad: const Value('barras'),
              fecha: elaboracion,
              referencia: Value(loteProduccionId),
            ),
          );
    });
    await encolar('empaques', id, 'crear');
    return id;
  }

  /// Todo el recorrido de una barra hasta su finca de origen (RF-LOT-04).
  Future<List<String>> trazabilidadDe(String loteProduccionId) async {
    final c = await completa(loteProduccionId);
    if (c == null) return const [];
    final lineas = <String>['Tanda ${c.produccion.codigo}'];

    for (final o in c.origenes) {
      final lote = await (bd.select(bd.lotes)..where((t) => t.id.equals(o.loteId)))
          .getSingleOrNull();
      if (lote == null) continue;
      final finca = lote.fincaId == null
          ? null
          : await (bd.select(bd.fincas)..where((t) => t.id.equals(lote.fincaId!)))
              .getSingleOrNull();
      lineas.add('Lote ${lote.codigo} · ${o.kgUsados} kg'
          '${finca == null ? '' : ' · ${finca.nombre}'}');
    }
    return lineas;
  }
}

/// No se puede usar un lote con la venta bloqueada (RN-15).
class LoteBloqueado implements Exception {
  const LoteBloqueado(this.loteId, this.motivo);
  final String loteId;
  final String motivo;

  @override
  String toString() =>
      'Ese lote está bloqueado y no se puede usar en producción: $motivo';
}
