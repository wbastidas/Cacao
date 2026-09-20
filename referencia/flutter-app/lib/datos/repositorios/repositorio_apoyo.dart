/// Inventario, costos, ventas, BPM, laboratorio, sacos, fotos y el panel de
/// indicadores. Todo lo que no es una etapa del proceso pero la ERS pide.
library;

import 'dart:convert';

import 'package:drift/drift.dart';

import '../../nucleo/reglas/alerta.dart';
import '../../nucleo/reglas/motor_reglas.dart';
import '../bd/base_datos.dart';
import '../bd/tablas/comunes.dart';
import 'repositorio_alertas.dart';
import 'repositorio_base.dart';
import 'repositorio_configuracion.dart';

/// Existencia actual de un artículo.
class Existencia {
  const Existencia({
    required this.item,
    required this.cantidad,
    required this.unidad,
    this.minimo,
  });

  final String item;
  final double cantidad;
  final String unidad;
  final double? minimo;

  /// RF-INV-02: queda poco y hay que reponer.
  bool get bajoMinimo => minimo != null && cantidad < minimo!;
}

/// Lo que cuesta un lote de producción y lo que sale cada barra (RF-COS-02).
class ResumenCostos {
  const ResumenCostos({
    required this.totalUsd,
    required this.porConcepto,
    this.barras = 0,
    this.kgGrano = 0,
  });

  final double totalUsd;
  final Map<String, double> porConcepto;
  final int barras;
  final double kgGrano;

  double? get costoPorBarra => barras > 0 ? totalUsd / barras : null;
  double? get costoPorKgGrano => kgGrano > 0 ? totalUsd / kgGrano : null;
}

/// Una tarea del día para la pantalla de inicio (RF-TAB-01).
class TareaDelDia {
  const TareaDelDia({
    required this.titulo,
    required this.detalle,
    required this.loteCodigo,
    this.loteId,
    this.urgente = false,
    this.ruta,
  });

  final String titulo;
  final String detalle;
  final String loteCodigo;
  final String? loteId;
  final bool urgente;
  final String? ruta;
}

class RepositorioApoyo extends RepositorioBase {
  RepositorioApoyo(
    super.bd,
    super.sync, {
    required this.config,
    required this.alertas,
  });

  final RepositorioConfiguracion config;
  final RepositorioAlertas alertas;

  // ------------------------------------------------------------ inventario

  /// Existencias calculadas desde los movimientos.
  ///
  /// No se guarda un saldo: se suma la historia. Así el número siempre se
  /// puede explicar, y un error se corrige con un movimiento, no editando
  /// un total a mano.
  Future<List<Existencia>> existencias() async {
    final movimientos = await (bd.select(bd.movimientosInventario)
          ..where((t) => t.eliminado.equals(false)))
        .get();
    final minimos = {
      for (final m in await bd.select(bd.stocksMinimos).get())
        m.item: m,
    };

    final saldos = <String, double>{};
    final unidades = <String, String>{};
    for (final m in movimientos) {
      final signo = m.tipo == 'salida' ? -1 : 1;
      saldos[m.item] = (saldos[m.item] ?? 0) + signo * m.cantidad;
      unidades[m.item] = m.unidad;
    }
    for (final item in minimos.keys) {
      saldos.putIfAbsent(item, () => 0);
      unidades.putIfAbsent(item, () => minimos[item]!.unidad);
    }

    final lista = [
      for (final e in saldos.entries)
        Existencia(
          item: e.key,
          cantidad: e.value,
          unidad: unidades[e.key] ?? 'kg',
          minimo: minimos[e.key]?.minimo,
        ),
    ]..sort((a, b) => a.item.compareTo(b.item));
    return lista;
  }

  Future<void> registrarMovimiento({
    required String item,
    required String tipo,
    required double cantidad,
    String unidad = 'kg',
    String referencia = '',
    String notas = '',
  }) async {
    final id = uuidGenerador.v4();
    await bd.into(bd.movimientosInventario).insert(
          MovimientosInventarioCompanion.insert(
            id: Value(id),
            item: item,
            tipo: tipo,
            cantidad: cantidad,
            unidad: Value(unidad),
            fecha: DateTime.now(),
            referencia: Value(referencia),
            notas: Value(notas),
          ),
        );
    await encolar('movimientos_inventario', id, 'crear');
  }

  // ------------------------------------------------------------ costos

  Future<void> registrarCosto({
    String? loteId,
    String? loteProduccionId,
    required String concepto,
    required double montoUsd,
    DateTime? fecha,
    String notas = '',
  }) async {
    final id = uuidGenerador.v4();
    await bd.into(bd.costos).insert(
          CostosCompanion.insert(
            id: Value(id),
            loteId: Value(loteId),
            loteProduccionId: Value(loteProduccionId),
            concepto: concepto,
            montoUsd: montoUsd,
            fecha: fecha ?? DateTime.now(),
            notas: Value(notas),
          ),
        );
    await encolar('costos', id, 'crear');
  }

  /// Costo de una tanda, incluyendo el de los lotes de grano que la formaron.
  Future<ResumenCostos> costosDeProduccion(String loteProduccionId) async {
    final origenes = await (bd.select(bd.lotesProduccionOrigen)
          ..where((t) => t.loteProduccionId.equals(loteProduccionId)))
        .get();
    final loteIds = origenes.map((o) => o.loteId).toList();

    final costos = await (bd.select(bd.costos)
          ..where((t) =>
              (t.loteProduccionId.equals(loteProduccionId) |
                  (loteIds.isEmpty
                      ? const Constant(false)
                      : t.loteId.isIn(loteIds))) &
              t.eliminado.equals(false)))
        .get();

    final porConcepto = <String, double>{};
    for (final c in costos) {
      porConcepto[c.concepto] = (porConcepto[c.concepto] ?? 0) + c.montoUsd;
    }

    final empaque = await (bd.select(bd.empaques)
          ..where((t) => t.loteProduccionId.equals(loteProduccionId)))
        .getSingleOrNull();

    return ResumenCostos(
      totalUsd: porConcepto.values.fold<double>(0, (a, b) => a + b),
      porConcepto: porConcepto,
      barras: empaque?.barras ?? 0,
      kgGrano: origenes.fold<double>(0, (a, o) => a + o.kgUsados),
    );
  }

  // ------------------------------------------------------------ ventas

  Future<void> registrarVenta({
    required String loteProduccionId,
    required int barras,
    String cliente = '',
    double precioUnitarioUsd = 0,
    bool consumoPropio = false,
    String notas = '',
  }) async {
    final id = uuidGenerador.v4();
    await bd.transaction(() async {
      await bd.into(bd.ventas).insert(
            VentasCompanion.insert(
              id: Value(id),
              loteProduccionId: loteProduccionId,
              cliente: Value(cliente),
              barras: barras,
              precioUnitarioUsd: Value(precioUnitarioUsd),
              fecha: DateTime.now(),
              consumoPropio: Value(consumoPropio),
              notas: Value(notas),
            ),
          );
      await bd.into(bd.movimientosInventario).insert(
            MovimientosInventarioCompanion.insert(
              item: 'chocolate',
              tipo: 'salida',
              cantidad: barras.toDouble(),
              unidad: const Value('barras'),
              fecha: DateTime.now(),
              referencia: Value(loteProduccionId),
            ),
          );
    });
    await encolar('ventas', id, 'crear');
  }

  /// Ingresos menos costos de una tanda (RF-VEN-02).
  Future<double> margenDeProduccion(String loteProduccionId) async {
    final ventas = await (bd.select(bd.ventas)
          ..where((t) =>
              t.loteProduccionId.equals(loteProduccionId) &
              t.consumoPropio.equals(false) &
              t.eliminado.equals(false)))
        .get();
    final ingresos = ventas.fold<double>(
        0, (a, v) => a + v.barras * v.precioUnitarioUsd);
    final costos = await costosDeProduccion(loteProduccionId);
    return ingresos - costos.totalUsd;
  }

  // ------------------------------------------------------------ BPM

  Stream<List<ChecklistBpm>> observarChecklists() =>
      (bd.select(bd.checklistsBpm)
            ..where((t) => t.activo.equals(true) & t.eliminado.equals(false))
            ..orderBy([(t) => OrderingTerm(expression: t.nombre)]))
          .watch();

  Future<RegistroBpm?> registroDeHoy(String checklistId) async {
    final hoy = DateTime.now();
    final desde = DateTime(hoy.year, hoy.month, hoy.day);
    return (bd.select(bd.registrosBpm)
          ..where((t) =>
              t.checklistId.equals(checklistId) &
              t.fecha.isBiggerOrEqualValue(desde) &
              t.eliminado.equals(false))
          ..limit(1))
        .getSingleOrNull();
  }

  Future<String> guardarRegistroBpm({
    required String checklistId,
    required Map<String, bool> respuestas,
    required String responsable,
    String observaciones = '',
  }) async {
    final existente = await registroDeHoy(checklistId);
    final id = existente?.id ?? uuidGenerador.v4();

    // RF-BPM-02: pasadas 24 h el registro se cierra y solo admite anotaciones.
    if (existente != null && existente.cerrado) {
      throw RegistroBpmCerrado(existente.fecha);
    }

    await bd.into(bd.registrosBpm).insertOnConflictUpdate(
          RegistrosBpmCompanion.insert(
            id: Value(id),
            checklistId: checklistId,
            fecha: existente?.fecha ?? DateTime.now(),
            respuestasJson: Value(jsonEncode(respuestas)),
            responsable: responsable,
            observaciones: Value(observaciones),
            modificadoEn: Value(DateTime.now()),
          ),
        );

    if (existente != null) {
      await auditar(
        tabla: 'registros_bpm',
        registroId: id,
        campo: 'respuestas',
        valorAnterior: existente.respuestasJson,
        valorNuevo: jsonEncode(respuestas),
        motivo: 'corrección el mismo día',
      );
    }
    await encolar('registros_bpm', id, 'actualizar');
    return id;
  }

  /// Cierra los registros de BPM con más de 24 horas (RF-BPM-02).
  ///
  /// Un registro que se puede editar para siempre no sirve como evidencia
  /// ante una inspección. Desde aquí solo se pueden añadir anotaciones.
  Future<int> cerrarRegistrosBpmVencidos() async {
    final limite = DateTime.now().subtract(const Duration(hours: 24));
    return (bd.update(bd.registrosBpm)
          ..where((t) =>
              t.fecha.isSmallerThanValue(limite) & t.cerrado.equals(false)))
        .write(const RegistrosBpmCompanion(cerrado: Value(true)));
  }

  Future<void> anotarEnRegistroBpm(String registroId, String texto) async {
    final registro = await (bd.select(bd.registrosBpm)
          ..where((t) => t.id.equals(registroId)))
        .getSingle();
    final anotaciones =
        (jsonDecode(registro.anotacionesJson) as List).cast<dynamic>();
    anotaciones.add({
      'fecha': DateTime.now().toIso8601String(),
      'texto': texto,
    });
    await (bd.update(bd.registrosBpm)..where((t) => t.id.equals(registroId)))
        .write(RegistrosBpmCompanion(
      anotacionesJson: Value(jsonEncode(anotaciones)),
      modificadoEn: Value(DateTime.now()),
      estadoSync: const Value(EstadoSync.pendiente),
    ));
    await encolar('registros_bpm', registroId, 'actualizar');
  }

  // ------------------------------------------------------------ laboratorio

  /// Guarda un resultado de laboratorio y aplica RN-15 (cadmio).
  ///
  /// Si el cadmio supera el límite, el lote queda bloqueado para venta hasta
  /// que el usuario lo desbloquee a conciencia.
  Future<List<Alerta>> guardarLaboratorio({
    String? loteId,
    String? loteProduccionId,
    required String analisis,
    required double valor,
    String unidad = 'mg/kg',
    double? limite,
    String laboratorio = '',
    DateTime? fecha,
    String? archivoId,
  }) async {
    final id = uuidGenerador.v4();
    await bd.into(bd.laboratorios).insert(
          LaboratoriosCompanion.insert(
            id: Value(id),
            loteId: Value(loteId),
            loteProduccionId: Value(loteProduccionId),
            analisis: analisis,
            valor: valor,
            unidad: Value(unidad),
            limite: Value(limite),
            laboratorio: Value(laboratorio),
            fecha: fecha ?? DateTime.now(),
            archivoId: Value(archivoId),
          ),
        );
    await encolar('laboratorios', id, 'crear');
    await auditar(
      tabla: 'laboratorios',
      registroId: id,
      campo: analisis,
      valorAnterior: '',
      valorNuevo: '$valor $unidad',
      motivo: 'resultado de $laboratorio',
    );

    final motor = MotorReglas(await config.umbrales());
    final generadas = motor.evaluarLaboratorio(ContextoLaboratorio(
        analisis: analisis, valor: valor, unidad: unidad));

    if (generadas.any((a) => a.bloquea) && loteId != null) {
      await (bd.update(bd.lotes)..where((t) => t.id.equals(loteId))).write(
        LotesCompanion(
          ventaBloqueada: const Value(true),
          motivoBloqueo: Value('$analisis $valor $unidad supera el límite'),
          modificadoEn: Value(DateTime.now()),
          estadoSync: const Value(EstadoSync.pendiente),
        ),
      );
      await encolar('lotes', loteId, 'actualizar');
    }
    return alertas.registrar(generadas,
        loteId: loteId, loteProduccionId: loteProduccionId);
  }

  /// Levanta el bloqueo de venta. Queda registrado quién y por qué.
  Future<void> desbloquearVenta(String loteId, String motivo) async {
    await (bd.update(bd.lotes)..where((t) => t.id.equals(loteId))).write(
      LotesCompanion(
        ventaBloqueada: const Value(false),
        modificadoEn: Value(DateTime.now()),
        estadoSync: const Value(EstadoSync.pendiente),
      ),
    );
    await auditar(
      tabla: 'lotes',
      registroId: loteId,
      campo: 'venta_bloqueada',
      valorAnterior: 'true',
      valorNuevo: 'false',
      motivo: motivo,
    );
    await encolar('lotes', loteId, 'actualizar');
  }

  // ------------------------------------------------------------ sacos

  Future<String> crearSaco({
    required String loteId,
    required double pesoKg,
    String ubicacion = '',
  }) async {
    final existentes = await (bd.select(bd.sacos)
          ..where((t) => t.loteId.equals(loteId)))
        .get();
    final lote =
        await (bd.select(bd.lotes)..where((t) => t.id.equals(loteId))).getSingle();
    final codigo = '${lote.codigo}-S${(existentes.length + 1)
        .toString()
        .padLeft(2, '0')}';
    final id = uuidGenerador.v4();

    await bd.into(bd.sacos).insert(
          SacosCompanion.insert(
            id: Value(id),
            loteId: loteId,
            codigoQr: codigo,
            pesoKg: Value(pesoKg),
            ubicacion: Value(ubicacion),
          ),
        );
    await encolar('sacos', id, 'crear');
    await registrarMovimiento(
      item: 'grano_seco',
      tipo: 'entrada',
      cantidad: pesoKg,
      referencia: codigo,
    );
    return codigo;
  }

  Stream<List<Saco>> observarSacos({String? loteId}) {
    final consulta = bd.select(bd.sacos)
      ..where((t) => t.eliminado.equals(false))
      ..orderBy([(t) => OrderingTerm(expression: t.codigoQr)]);
    if (loteId != null) consulta.where((t) => t.loteId.equals(loteId));
    return consulta.watch();
  }

  /// Inspección del almacén, con RN-11.
  Future<List<Alerta>> inspeccionarAlmacen({
    String? loteId,
    double? humedadRelativa,
    double? temperatura,
    bool plagas = false,
    bool moho = false,
    String olor = '',
    String? fotoId,
    String notas = '',
  }) async {
    final id = uuidGenerador.v4();
    await bd.into(bd.inspeccionesAlmacen).insert(
          InspeccionesAlmacenCompanion.insert(
            id: Value(id),
            loteId: Value(loteId),
            fecha: DateTime.now(),
            humedadRelativa: Value(humedadRelativa),
            temperatura: Value(temperatura),
            plagas: Value(plagas),
            moho: Value(moho),
            olor: Value(olor),
            fotoId: Value(fotoId),
            notas: Value(notas),
          ),
        );
    await encolar('inspecciones_almacen', id, 'crear');

    final motor = MotorReglas(await config.umbrales());
    final generadas = motor.evaluarAlmacen(ContextoAlmacen(
      humedadRelativaPct: humedadRelativa,
      mohoVisible: moho,
      plagas: plagas,
    ));
    return alertas.registrar(generadas, loteId: loteId);
  }

  // ------------------------------------------------------------ fotos

  Future<String> guardarFoto({
    required String rutaLocal,
    String etapa = '',
    String? loteId,
    String? loteProduccionId,
    Map<String, dynamic> analisisIa = const {},
    String etiquetaUsuario = '',
    bool aptaDataset = false,
    double? latitud,
    double? longitud,
  }) async {
    final id = uuidGenerador.v4();
    await bd.into(bd.fotos).insert(
          FotosCompanion.insert(
            id: Value(id),
            rutaLocal: rutaLocal,
            etapa: Value(etapa),
            loteId: Value(loteId),
            loteProduccionId: Value(loteProduccionId),
            analisisIaJson: Value(jsonEncode(analisisIa)),
            etiquetaUsuario: Value(etiquetaUsuario),
            aptaDataset: Value(aptaDataset),
            latitud: Value(latitud),
            longitud: Value(longitud),
          ),
        );
    await encolar('fotos', id, 'crear');
    // La foto en sí viaja aparte y solo con WiFi (RF-SYN-04).
    await encolar('fotos', id, 'subir_foto',
        carga: {'ruta': rutaLocal}, esArchivo: true);
    return id;
  }

  /// Las fotos que el usuario corrigió y sirven para reentrenar (§8.3 ERS).
  Future<List<Foto>> fotosParaDataset({String? etapa}) {
    final consulta = bd.select(bd.fotos)
      ..where((t) => t.aptaDataset.equals(true) & t.eliminado.equals(false));
    if (etapa != null) consulta.where((t) => t.etapa.equals(etapa));
    return consulta.get();
  }

  // ------------------------------------------------------------ panel

  /// Indicadores de la pantalla de tablero (RF-TAB-02).
  Future<Map<String, String>> indicadores() async {
    final lotes = await (bd.select(bd.lotes)
          ..where((t) => t.eliminado.equals(false)))
        .get();
    final abiertas = await (bd.select(bd.alertas)
          ..where((t) => t.estado.equals('abierta') & t.eliminado.equals(false)))
        .get();
    final pruebas = await (bd.select(bd.pruebasCorte)
          ..where((t) => t.eliminado.equals(false) & t.esParcial.equals(false)))
        .get();
    final secados = await (bd.select(bd.secados)
          ..where((t) => t.eliminado.equals(false) & t.fin.isNotNull()))
        .get();
    final lecturas = await (bd.select(bd.lecturasFermentacion)
          ..where((t) => t.eliminado.equals(false) & t.tempC.isNotNull()))
        .get();

    double? promedioFermentado;
    if (pruebas.isNotEmpty) {
      var suma = 0.0;
      var n = 0;
      for (final p in pruebas) {
        final pct = jsonDecode(p.porcentajesJson) as Map<String, dynamic>;
        final total = pct['fermentado_total'];
        if (total is num) {
          suma += total.toDouble();
          n++;
        }
      }
      if (n > 0) promedioFermentado = suma / n;
    }

    final tempMaxima = lecturas.isEmpty
        ? null
        : lecturas.map((l) => l.tempC!).reduce((a, b) => a > b ? a : b);

    final diasSecado = secados.isEmpty
        ? null
        : secados
                .map((s) => s.fin!.difference(s.inicio).inDays)
                .reduce((a, b) => a + b) /
            secados.length;

    return {
      'Lotes registrados': '${lotes.length}',
      'Lotes activos': '${lotes.where((l) => l.estado.index < 10).length}',
      'Alertas abiertas': '${abiertas.length}',
      'Pruebas de corte': '${pruebas.length}',
      'Fermentado promedio': promedioFermentado == null
          ? 'sin datos'
          : '${promedioFermentado.toStringAsFixed(1)} %',
      'Temperatura máxima de fermentación':
          tempMaxima == null ? 'sin datos' : '${tempMaxima.toStringAsFixed(1)} °C',
      'Días de secado (promedio)':
          diasSecado == null ? 'sin datos' : diasSecado.toStringAsFixed(1),
    };
  }

  /// Lo que hay que hacer hoy, juntando todos los lotes (RF-TAB-01).
  Future<List<TareaDelDia>> tareasDeHoy() async {
    final tareas = <TareaDelDia>[];
    final ahora = DateTime.now();
    final umbrales = await config.umbrales();

    final lotes = await (bd.select(bd.lotes)
          ..where((t) => t.eliminado.equals(false)))
        .get();

    for (final lote in lotes) {
      // Mazorcas en reposo que ya toca abrir.
      final recepcion = await (bd.select(bd.recepciones)
            ..where((t) => t.loteId.equals(lote.id)))
          .getSingleOrNull();
      if (recepcion?.fechaAperturaPlan != null &&
          lote.estado.index <= 1 &&
          !recepcion!.fechaAperturaPlan!.isAfter(ahora)) {
        final dias = ahora.difference(recepcion.fechaAperturaPlan!).inDays;
        tareas.add(TareaDelDia(
          titulo: 'Abrir las mazorcas',
          detalle: dias > 0
              ? 'Debían abrirse hace $dias día(s)'
              : 'Toca hoy, según los ${recepcion.diasReposo} días de reposo',
          loteCodigo: lote.codigo,
          loteId: lote.id,
          urgente: dias > 1,
          ruta: 'apertura',
        ));
      }

      // Fermentación: volteo pendiente.
      final ferm = await (bd.select(bd.fermentaciones)
            ..where((t) => t.loteId.equals(lote.id) & t.fin.isNull()))
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
        final horas = ahora.difference(desde).inHours;
        if (horas >= umbrales['volteo_horas']) {
          tareas.add(TareaDelDia(
            titulo: 'Voltear la masa',
            detalle: 'Van $horas horas desde el último volteo',
            loteCodigo: lote.codigo,
            loteId: lote.id,
            urgente: horas >= umbrales['volteo_horas_alerta'],
            ruta: 'fermentacion',
          ));
        }
        tareas.add(TareaDelDia(
          titulo: 'Medir la temperatura',
          detalle: 'Lleva ${ahora.difference(ferm.inicio).inHours} horas '
              'de fermentación',
          loteCodigo: lote.codigo,
          loteId: lote.id,
          ruta: 'fermentacion',
        ));
      }

      // Secado en curso: jornada del día.
      final secado = await (bd.select(bd.secados)
            ..where((t) => t.loteId.equals(lote.id) & t.fin.isNull()))
          .getSingleOrNull();
      if (secado != null) {
        tareas.add(TareaDelDia(
          titulo: 'Registrar la jornada de secado',
          detalle: 'Día ${ahora.difference(secado.inicio).inDays + 1} de secado',
          loteCodigo: lote.codigo,
          loteId: lote.id,
          ruta: 'secado',
        ));
      }
    }

    // Inspección de almacén vencida.
    final dias = umbrales.entero('almacen_dias_inspeccion');
    final ultima = await (bd.select(bd.inspeccionesAlmacen)
          ..orderBy([
            (t) => OrderingTerm(expression: t.fecha, mode: OrderingMode.desc)
          ])
          ..limit(1))
        .getSingleOrNull();
    final haySacos = (await bd.select(bd.sacos).get()).isNotEmpty;
    if (haySacos &&
        (ultima == null ||
            ahora.difference(ultima.fecha).inDays >= dias)) {
      tareas.add(const TareaDelDia(
        titulo: 'Inspeccionar el almacén',
        detalle: 'Revisar humedad, olor, plagas y moho en los sacos',
        loteCodigo: 'Almacén',
        ruta: 'almacen',
      ));
    }

    // BPM del día sin diligenciar.
    for (final c in await (bd.select(bd.checklistsBpm)
          ..where((t) =>
              t.activo.equals(true) & t.frecuencia.equals('diario')))
        .get()) {
      if (await registroDeHoy(c.id) == null) {
        tareas.add(TareaDelDia(
          titulo: c.nombre,
          detalle: 'Checklist de buenas prácticas de hoy',
          loteCodigo: 'BPM',
          ruta: 'bpm',
        ));
      }
    }

    tareas.sort((a, b) {
      if (a.urgente == b.urgente) return 0;
      return a.urgente ? -1 : 1;
    });
    return tareas;
  }
}

/// El registro de BPM ya se cerró y solo admite anotaciones (RF-BPM-02).
class RegistroBpmCerrado implements Exception {
  const RegistroBpmCerrado(this.fecha);
  final DateTime fecha;

  @override
  String toString() =>
      'Este registro se cerró pasadas 24 horas y ya no se puede cambiar. '
      'Puedes añadir una anotación explicando lo que corresponda.';
}
