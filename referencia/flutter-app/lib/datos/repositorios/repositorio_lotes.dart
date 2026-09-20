/// Repositorio del lote de grano: de la recepción de mazorcas al saco
/// almacenado. Es donde se juntan la escritura, la sincronización y las reglas.
library;

import 'dart:convert';

import 'package:drift/drift.dart';

import '../../nucleo/calculo/balance_masa.dart';
import '../../nucleo/modelo/etapas.dart';
import '../../nucleo/norma/calificador_corte.dart';
import '../../nucleo/reglas/alerta.dart';
import '../../nucleo/reglas/motor_reglas.dart';
import '../bd/base_datos.dart';
import '../bd/tablas/comunes.dart';
import 'repositorio_alertas.dart';
import 'repositorio_base.dart';
import 'repositorio_configuracion.dart';

/// Un lote con todo lo que la pantalla de detalle necesita de una sola vez.
class LoteCompleto {
  const LoteCompleto({
    required this.lote,
    this.finca,
    this.recepcion,
    this.apertura,
    this.fermentacion,
    this.secado,
    this.pruebasCorte = const [],
    this.alertasAbiertas = 0,
  });

  final Lote lote;
  final Finca? finca;
  final Recepcion? recepcion;
  final Apertura? apertura;
  final Fermentacion? fermentacion;
  final Secado? secado;
  final List<PruebaCorte> pruebasCorte;
  final int alertasAbiertas;

  /// La última prueba de corte completa, que es la que define el grado.
  PruebaCorte? get pruebaCorteFinal {
    final completas = pruebasCorte.where((p) => !p.esParcial).toList();
    return completas.isEmpty ? null : completas.last;
  }
}

/// Un hecho de la línea de tiempo del lote (RF-LOT-06).
class HechoLote {
  const HechoLote({
    required this.fecha,
    required this.etapa,
    required this.titulo,
    this.detalle = '',
    this.fotoId,
    this.esAlerta = false,
  });

  final DateTime fecha;
  final String etapa;
  final String titulo;
  final String detalle;
  final String? fotoId;
  final bool esAlerta;
}

class RepositorioLotes extends RepositorioBase {
  RepositorioLotes(
    super.bd,
    super.sync, {
    required this.config,
    required this.alertas,
  });

  final RepositorioConfiguracion config;
  final RepositorioAlertas alertas;

  Future<MotorReglas> _motor() async => MotorReglas(await config.umbrales());

  // ------------------------------------------------------------ lotes

  Stream<List<Lote>> observarLotes({bool incluirCerrados = true}) {
    final consulta = bd.select(bd.lotes)
      ..where((t) => t.eliminado.equals(false))
      ..orderBy([
        (t) => OrderingTerm(expression: t.fechaLlegada, mode: OrderingMode.desc)
      ]);
    if (!incluirCerrados) {
      consulta.where((t) => t.estado.isNotIn([
            EstadoLote.vendido.index,
            EstadoLote.descartado.index,
          ]));
    }
    return consulta.watch();
  }

  Future<Lote?> porId(String id) =>
      (bd.select(bd.lotes)..where((t) => t.id.equals(id))).getSingleOrNull();

  Future<Lote?> porCodigo(String codigo) =>
      (bd.select(bd.lotes)..where((t) => t.codigo.equals(codigo)))
          .getSingleOrNull();

  /// Crea un lote con código correlativo del año (RF-LOT-01).
  Future<Lote> crearLote({
    String? fincaId,
    DateTime? fechaCosecha,
    DateTime? fechaLlegada,
    String variedad = 'CCN-51',
  }) async {
    final llegada = fechaLlegada ?? DateTime.now();
    final existentes = await bd.select(bd.lotes).get();
    final codigo = siguienteCodigo(
      'L',
      llegada.year,
      existentes.map((l) => l.codigo).toList(),
    );

    final id = uuidGenerador.v4();
    await bd.into(bd.lotes).insert(
          LotesCompanion.insert(
            id: Value(id),
            codigo: codigo,
            fincaId: Value(fincaId),
            variedad: Value(variedad),
            fechaCosecha: Value(fechaCosecha),
            fechaLlegada: llegada,
          ),
        );
    await encolar('lotes', id, 'crear', carga: {'codigo': codigo});
    return (await porId(id))!;
  }

  /// Cambia el estado del lote.
  ///
  /// Si se salta una etapa obligatoria hay que dar un motivo, y queda escrito
  /// en el lote para siempre (RF-LOT-03).
  Future<void> cambiarEstado(
    String loteId,
    EstadoLote nuevo, {
    String motivoSalto = '',
  }) async {
    final lote = await porId(loteId);
    if (lote == null) throw ArgumentError('El lote $loteId no existe');

    final saltadas = _etapasSaltadas(lote.estado, nuevo);
    if (saltadas.isNotEmpty && motivoSalto.isEmpty) {
      throw EtapaSaltadaSinMotivo(saltadas);
    }

    await (bd.update(bd.lotes)..where((t) => t.id.equals(loteId))).write(
      LotesCompanion(
        estado: Value(nuevo),
        modificadoEn: Value(DateTime.now()),
        estadoSync: const Value(EstadoSync.pendiente),
        motivoSalto: motivoSalto.isEmpty
            ? const Value.absent()
            : Value('${lote.motivoSalto}\n'
                '${DateTime.now().toIso8601String()}: '
                'se saltó ${saltadas.map((e) => e.etiqueta).join(", ")} '
                'porque $motivoSalto'),
      ),
    );
    await encolar('lotes', loteId, 'actualizar', carga: {'estado': nuevo.name});
  }

  List<EstadoLote> _etapasSaltadas(EstadoLote desde, EstadoLote hasta) {
    const camino = EstadoLote.values;
    if (hasta.index <= desde.index) return const [];
    return [
      for (var i = desde.index + 1; i < hasta.index; i++)
        if (camino[i].esObligatoria) camino[i],
    ];
  }

  /// Trae el lote con todo lo colgado de él, para la pantalla de detalle.
  Future<LoteCompleto?> completo(String loteId) async {
    final lote = await porId(loteId);
    if (lote == null) return null;

    Future<T?> uno<T>(Future<T?> Function() f) async => f();

    final finca = lote.fincaId == null
        ? null
        : await (bd.select(bd.fincas)..where((t) => t.id.equals(lote.fincaId!)))
            .getSingleOrNull();

    return LoteCompleto(
      lote: lote,
      finca: finca,
      recepcion: await uno(() => (bd.select(bd.recepciones)
            ..where((t) => t.loteId.equals(loteId) & t.eliminado.equals(false)))
          .getSingleOrNull()),
      apertura: await uno(() => (bd.select(bd.aperturas)
            ..where((t) => t.loteId.equals(loteId) & t.eliminado.equals(false)))
          .getSingleOrNull()),
      fermentacion: await uno(() => (bd.select(bd.fermentaciones)
            ..where((t) => t.loteId.equals(loteId) & t.eliminado.equals(false)))
          .getSingleOrNull()),
      secado: await uno(() => (bd.select(bd.secados)
            ..where((t) => t.loteId.equals(loteId) & t.eliminado.equals(false)))
          .getSingleOrNull()),
      pruebasCorte: await (bd.select(bd.pruebasCorte)
            ..where((t) => t.loteId.equals(loteId) & t.eliminado.equals(false))
            ..orderBy([(t) => OrderingTerm(expression: t.fecha)]))
          .get(),
      alertasAbiertas: (await alertas.deLote(loteId))
          .where((a) => a.estado == 'abierta')
          .length,
    );
  }

  // ------------------------------------------------------------ recepción

  /// Registra la recepción y programa la apertura (RF-REC-01, RF-REC-05).
  Future<Recepcion> guardarRecepcion({
    required String loteId,
    required int mazorcasTotal,
    required double pesoKg,
    int sacos = 0,
    int sanas = 0,
    int monilia = 0,
    int fitoftora = 0,
    int otro = 0,
    int descartadas = 0,
    String motivoDescarte = '',
    int diasReposo = 4,
  }) async {
    final lote = await porId(loteId);
    final fechaApertura =
        (lote?.fechaLlegada ?? DateTime.now()).add(Duration(days: diasReposo));

    final existente = await (bd.select(bd.recepciones)
          ..where((t) => t.loteId.equals(loteId)))
        .getSingleOrNull();
    final id = existente?.id ?? uuidGenerador.v4();

    await bd.into(bd.recepciones).insertOnConflictUpdate(
          RecepcionesCompanion.insert(
            id: Value(id),
            loteId: loteId,
            sacos: Value(sacos),
            mazorcasTotal: Value(mazorcasTotal),
            pesoKg: Value(pesoKg),
            mazorcasSanas: Value(sanas),
            mazorcasMonilia: Value(monilia),
            mazorcasFitoftora: Value(fitoftora),
            mazorcasOtro: Value(otro),
            mazorcasDescartadas: Value(descartadas),
            motivoDescarte: Value(motivoDescarte),
            diasReposo: Value(diasReposo),
            fechaAperturaPlan: Value(fechaApertura),
            modificadoEn: Value(DateTime.now()),
          ),
        );
    await encolar('recepciones', id, 'actualizar');
    await cambiarEstado(loteId, EstadoLote.reposo);
    return (await (bd.select(bd.recepciones)..where((t) => t.id.equals(id)))
        .getSingle());
  }

  /// Revisa el reposo de todos los lotes y levanta las alertas RN-01.
  Future<List<Alerta>> revisarReposos() async {
    final motor = await _motor();
    final nuevas = <Alerta>[];
    final enReposo = await (bd.select(bd.lotes)
          ..where((t) =>
              t.estado.equals(EstadoLote.reposo.index) &
              t.eliminado.equals(false)))
        .get();

    for (final lote in enReposo) {
      final dias = DateTime.now().difference(lote.fechaLlegada).inDays;
      final generadas =
          motor.evaluarReposo(ContextoReposo(diasDesdeLlegada: dias));
      nuevas.addAll(await alertas.registrar(generadas, loteId: lote.id));
    }
    return nuevas;
  }

  // ------------------------------------------------------------ apertura

  /// Registra la apertura y evalúa RN-02 y RN-17 (RF-APE-01/02/03).
  Future<List<Alerta>> guardarApertura({
    required String loteId,
    required int mazorcasAbiertas,
    required double kgBaba,
    double kgCascaraAnadida = 0,
    String notas = '',
  }) async {
    final id = uuidGenerador.v4();
    await bd.into(bd.aperturas).insert(
          AperturasCompanion.insert(
            id: Value(id),
            loteId: loteId,
            fecha: DateTime.now(),
            mazorcasAbiertas: Value(mazorcasAbiertas),
            kgBaba: Value(kgBaba),
            kgCascaraAnadida: Value(kgCascaraAnadida),
            notas: Value(notas),
          ),
        );
    await encolar('aperturas', id, 'crear');

    final motor = await _motor();
    final generadas = motor.evaluarApertura(ContextoApertura(
      mazorcasAbiertas: mazorcasAbiertas,
      kgBaba: kgBaba,
      kgCascaraAnadida: kgCascaraAnadida,
    ));
    return alertas.registrar(generadas, loteId: loteId);
  }

  /// Cuánta cáscara hay que añadir para llegar a la masa mínima (RF-APE-03).
  Future<double> cascaraRecomendada(double kgBaba) async {
    final umbrales = await config.umbrales();
    final minima = umbrales['baba_masa_minima_kg'];
    final falta = minima - kgBaba;
    return falta > 0 ? falta : 0;
  }

  // ------------------------------------------------------------ fermentación

  Future<Fermentacion> iniciarFermentacion({
    required String loteId,
    String? equipoId,
    required double masaKg,
    String aislamiento = '',
    DateTime? inicio,
    String motivoSalto = '',
  }) async {
    final id = uuidGenerador.v4();
    await bd.into(bd.fermentaciones).insert(
          FermentacionesCompanion.insert(
            id: Value(id),
            loteId: loteId,
            equipoId: Value(equipoId),
            inicio: inicio ?? DateTime.now(),
            masaKg: Value(masaKg),
            aislamiento: Value(aislamiento),
          ),
        );
    await encolar('fermentaciones', id, 'crear');
    await cambiarEstado(loteId, EstadoLote.fermentacion,
        motivoSalto: motivoSalto);
    return (await (bd.select(bd.fermentaciones)..where((t) => t.id.equals(id)))
        .getSingle());
  }

  Future<Fermentacion?> fermentacionDe(String loteId) =>
      (bd.select(bd.fermentaciones)
            ..where((t) => t.loteId.equals(loteId) & t.eliminado.equals(false)))
          .getSingleOrNull();

  Stream<List<LecturaFermentacion>> observarLecturas(String fermentacionId) =>
      (bd.select(bd.lecturasFermentacion)
            ..where((t) =>
                t.fermentacionId.equals(fermentacionId) &
                t.eliminado.equals(false))
            ..orderBy([(t) => OrderingTerm(expression: t.fechaHora)]))
          .watch();

  Stream<List<Volteo>> observarVolteos(String fermentacionId) =>
      (bd.select(bd.volteos)
            ..where((t) =>
                t.fermentacionId.equals(fermentacionId) &
                t.eliminado.equals(false))
            ..orderBy([(t) => OrderingTerm(expression: t.fechaHora)]))
          .watch();

  /// Registra una lectura y evalúa las reglas de fermentación (RF-FER-02/03).
  Future<List<Alerta>> registrarLectura({
    required String fermentacionId,
    required String loteId,
    double? tempC,
    double? ph,
    OlorFermentacion? olor,
    String fuente = 'manual',
    String? fotoId,
    String notas = '',
    DateTime? cuando,
  }) async {
    final fermentacion = await (bd.select(bd.fermentaciones)
          ..where((t) => t.id.equals(fermentacionId)))
        .getSingle();
    final momento = cuando ?? DateTime.now();

    final id = uuidGenerador.v4();
    await bd.into(bd.lecturasFermentacion).insert(
          LecturasFermentacionCompanion.insert(
            id: Value(id),
            fermentacionId: fermentacionId,
            fechaHora: momento,
            tempC: Value(tempC),
            ph: Value(ph),
            olor: Value(olor),
            fuente: Value(fuente),
            fotoId: Value(fotoId),
            notas: Value(notas),
          ),
        );
    await encolar('lecturas_fermentacion', id, 'crear');

    final ultimoVolteo = await (bd.select(bd.volteos)
          ..where((t) => t.fermentacionId.equals(fermentacionId))
          ..orderBy([
            (t) => OrderingTerm(
                expression: t.fechaHora, mode: OrderingMode.desc)
          ])
          ..limit(1))
        .getSingleOrNull();

    final motor = await _motor();
    final generadas = motor.evaluarFermentacion(ContextoFermentacion(
      horasDesdeInicio:
          momento.difference(fermentacion.inicio).inMinutes / 60.0,
      temperaturaC: tempC,
      horasDesdeUltimoVolteo: ultimoVolteo == null
          ? null
          : momento.difference(ultimoVolteo.fechaHora).inMinutes / 60.0,
      olor: olor,
      ph: ph,
    ));
    return alertas.registrar(generadas, loteId: loteId);
  }

  /// Registra un volteo (RF-FER-04). Debe poder hacerse en un solo toque.
  Future<void> registrarVolteo(String fermentacionId, {String? fotoId}) async {
    final id = uuidGenerador.v4();
    await bd.into(bd.volteos).insert(
          VolteosCompanion.insert(
            id: Value(id),
            fermentacionId: fermentacionId,
            fechaHora: DateTime.now(),
            fotoId: Value(fotoId),
          ),
        );
    await encolar('volteos', id, 'crear');
  }

  /// Cierra la fermentación y pasa el lote a secado (RF-FER-07).
  Future<void> cerrarFermentacion(String fermentacionId, String loteId) async {
    await (bd.update(bd.fermentaciones)
          ..where((t) => t.id.equals(fermentacionId)))
        .write(FermentacionesCompanion(
      fin: Value(DateTime.now()),
      modificadoEn: Value(DateTime.now()),
      estadoSync: const Value(EstadoSync.pendiente),
    ));
    await encolar('fermentaciones', fermentacionId, 'actualizar');
    await cambiarEstado(loteId, EstadoLote.secado);
  }

  // ------------------------------------------------------------ secado

  /// Empieza el secado.
  ///
  /// [motivoSalto] hace falta si el lote no pasó por fermentación, porque es
  /// una etapa obligatoria y saltarla tiene que quedar justificado (RF-LOT-03).
  Future<Secado> iniciarSecado({
    required String loteId,
    MetodoSecado metodo = MetodoSecado.marquesina,
    String motivoSalto = '',
  }) async {
    final id = uuidGenerador.v4();
    await bd.into(bd.secados).insert(
          SecadosCompanion.insert(
            id: Value(id),
            loteId: loteId,
            metodo: Value(metodo),
            inicio: DateTime.now(),
          ),
        );
    await encolar('secados', id, 'crear');
    await cambiarEstado(loteId, EstadoLote.secado, motivoSalto: motivoSalto);
    return (await (bd.select(bd.secados)..where((t) => t.id.equals(id)))
        .getSingle());
  }

  Future<Secado?> secadoDe(String loteId) => (bd.select(bd.secados)
        ..where((t) => t.loteId.equals(loteId) & t.eliminado.equals(false)))
      .getSingleOrNull();

  Stream<List<LecturaSecado>> observarLecturasSecado(String secadoId) =>
      (bd.select(bd.lecturasSecado)
            ..where((t) =>
                t.secadoId.equals(secadoId) & t.eliminado.equals(false))
            ..orderBy([(t) => OrderingTerm(expression: t.fecha)]))
          .watch();

  /// Registra la jornada de secado y evalúa RN-09 (RF-SEC-02/03/04).
  Future<List<Alerta>> registrarLecturaSecado({
    required String secadoId,
    required String loteId,
    double? humedadGrano,
    String pruebaPunado = '',
    double? tempAmbiente,
    double? hrAmbiente,
    double? espesorCm,
    int remociones = 0,
    bool moho = false,
    String? fotoId,
  }) async {
    final id = uuidGenerador.v4();
    await bd.into(bd.lecturasSecado).insert(
          LecturasSecadoCompanion.insert(
            id: Value(id),
            secadoId: secadoId,
            fecha: DateTime.now(),
            humedadGrano: Value(humedadGrano),
            pruebaPunado: Value(pruebaPunado),
            tempAmbiente: Value(tempAmbiente),
            hrAmbiente: Value(hrAmbiente),
            espesorCm: Value(espesorCm),
            remociones: Value(remociones),
            moho: Value(moho),
            fotoId: Value(fotoId),
          ),
        );
    await encolar('lecturas_secado', id, 'crear');

    final motor = await _motor();
    final generadas = motor.evaluarSecado(
        ContextoSecado(humedadGranoPct: humedadGrano, mohoVisible: moho));
    return alertas.registrar(generadas, loteId: loteId);
  }

  /// Cierra el secado. Si la humedad es alta, RN-08 bloquea hasta confirmar.
  ///
  /// Devuelve las alertas generadas. Si alguna bloquea y [confirmado] es
  /// false, el lote NO pasa a almacenado.
  Future<List<Alerta>> cerrarSecado({
    required String secadoId,
    required String loteId,
    required double kgSeco,
    double? humedadFinal,
    bool confirmado = false,
  }) async {
    final motor = await _motor();
    final generadas = motor.evaluarSecado(ContextoSecado(
      humedadGranoPct: humedadFinal,
      cerrandoEtapa: true,
    ));
    final bloquea = generadas.any((a) => a.bloquea);
    await alertas.registrar(generadas, loteId: loteId);

    if (bloquea && !confirmado) return generadas;

    await (bd.update(bd.secados)..where((t) => t.id.equals(secadoId))).write(
      SecadosCompanion(
        fin: Value(DateTime.now()),
        kgSeco: Value(kgSeco),
        modificadoEn: Value(DateTime.now()),
        estadoSync: const Value(EstadoSync.pendiente),
      ),
    );
    await encolar('secados', secadoId, 'actualizar');
    await cambiarEstado(loteId, EstadoLote.almacenado);

    // RN-17: comparar el rendimiento baba -> seco con lo esperado.
    final apertura = await (bd.select(bd.aperturas)
          ..where((t) => t.loteId.equals(loteId)))
        .getSingleOrNull();
    if (apertura != null && apertura.kgBaba > 0) {
      const esperados = RendimientosEsperados();
      final extra = motor.evaluarRendimiento(
        etapa: 'secado',
        rendimientoReal: kgSeco / apertura.kgBaba,
        rendimientoEsperado: esperados.fraccionBabaASeco,
      );
      await alertas.registrar(extra, loteId: loteId);
      return [...generadas, ...extra];
    }
    return generadas;
  }

  // ------------------------------------------------------------ prueba de corte

  /// Guarda una prueba de corte ya calificada (RF-PRC-06, RF-PRC-10).
  Future<PruebaCorte> guardarPruebaCorte({
    required String loteId,
    required Map<String, int> conteo,
    required ResultadoCorte resultado,
    double? peso100g,
    String modeloVersion = '',
    String? fotoId,
    bool esParcial = false,
  }) async {
    final id = uuidGenerador.v4();
    final porcentajes = {
      for (final e in resultado.porcentajes.valores.entries) e.key: e.value,
    };

    await bd.into(bd.pruebasCorte).insert(
          PruebasCorteCompanion.insert(
            id: Value(id),
            loteId: loteId,
            fecha: DateTime.now(),
            perfilNorma: Value(resultado.perfil),
            granos: Value(resultado.porcentajes.granosContados),
            conteosJson: Value(jsonEncode(conteo)),
            porcentajesJson: Value(jsonEncode(porcentajes)),
            resultado: Value(resultado.resultado),
            conforme: Value(resultado.conforme),
            peso100g: Value(peso100g),
            modeloVersion: Value(modeloVersion),
            esParcial: Value(esParcial),
            fotoId: Value(fotoId),
          ),
        );
    await encolar('pruebas_corte', id, 'crear',
        carga: {'resultado': resultado.resultado});

    // RNF-11: la prueba de corte es un registro sensible.
    await auditar(
      tabla: 'pruebas_corte',
      registroId: id,
      campo: 'resultado',
      valorAnterior: '',
      valorNuevo: resultado.resultado,
      motivo: modeloVersion.isEmpty
          ? 'conteo manual'
          : 'modelo $modeloVersion con corrección del usuario',
    );

    final motor = await _motor();
    final generadas = motor.evaluarPruebaCorte(
      conforme: resultado.conforme,
      resultado: resultado.resultado,
      fallas: resultado.todasLasFallas,
      pctVioleta: resultado.porcentajes['violeta'],
      pctPizarroso: resultado.porcentajes['pizarroso'],
      pctMohoso: resultado.porcentajes['mohoso'],
    );
    await alertas.registrar(generadas, loteId: loteId);

    return (await (bd.select(bd.pruebasCorte)..where((t) => t.id.equals(id)))
        .getSingle());
  }

  // ------------------------------------------------------------ balance

  /// Balance de masa del lote con lo registrado hasta ahora (RF-LOT-08).
  Future<List<PasoBalance>> balance(String loteId) async {
    final c = await completo(loteId);
    if (c == null) return const [];

    final origen = await (bd.select(bd.lotesProduccionOrigen)
          ..where((t) => t.loteId.equals(loteId)))
        .get();
    double? kgNibs;
    double? kgChocolate;
    if (origen.isNotEmpty) {
      final ids = origen.map((o) => o.loteProduccionId).toSet().toList();
      final desc = await (bd.select(bd.descascarillados)
            ..where((t) => t.loteProduccionId.isIn(ids)))
          .get();
      if (desc.isNotEmpty) {
        kgNibs = desc.fold<double>(0, (a, d) => a + d.kgNibs);
      }
      final prods = await (bd.select(bd.lotesProduccion)
            ..where((t) => t.id.isIn(ids)))
          .get();
      final conChocolate = prods.where((p) => p.kgChocolate != null);
      if (conChocolate.isNotEmpty) {
        kgChocolate =
            conChocolate.fold<double>(0, (a, p) => a + p.kgChocolate!);
      }
    }

    return const BalanceMasa().calcular(
      mazorcas: c.recepcion?.mazorcasTotal ?? 0,
      kgBaba: c.apertura?.kgBaba,
      kgSeco: c.secado?.kgSeco,
      kgNibs: kgNibs,
      kgChocolate: kgChocolate,
    );
  }

  // ------------------------------------------------------------ línea de tiempo

  /// Todo lo que le pasó al lote, en orden (RF-LOT-06).
  Future<List<HechoLote>> lineaDeTiempo(String loteId) async {
    final hechos = <HechoLote>[];
    final c = await completo(loteId);
    if (c == null) return hechos;

    hechos.add(HechoLote(
      fecha: c.lote.fechaLlegada,
      etapa: 'Recepción',
      titulo: 'Llegó el lote ${c.lote.codigo}',
      detalle: c.finca == null ? '' : 'Desde ${c.finca!.nombre}',
    ));

    final r = c.recepcion;
    if (r != null) {
      hechos.add(HechoLote(
        fecha: r.creadoEn,
        etapa: 'Recepción',
        titulo: '${r.mazorcasTotal} mazorcas, ${r.pesoKg} kg',
        detalle: 'Sanas ${r.mazorcasSanas} · monilia ${r.mazorcasMonilia} · '
            'fitóftora ${r.mazorcasFitoftora} · descartadas '
            '${r.mazorcasDescartadas}',
      ));
    }

    final a = c.apertura;
    if (a != null) {
      hechos.add(HechoLote(
        fecha: a.fecha,
        etapa: 'Apertura',
        titulo: '${a.mazorcasAbiertas} mazorcas abiertas',
        detalle: '${a.kgBaba} kg de baba'
            '${a.kgCascaraAnadida > 0 ? ' + ${a.kgCascaraAnadida} kg de cáscara' : ''}',
      ));
    }

    final f = c.fermentacion;
    if (f != null) {
      hechos.add(HechoLote(
        fecha: f.inicio,
        etapa: 'Fermentación',
        titulo: 'Empezó la fermentación',
        detalle: '${f.masaKg} kg',
      ));
      for (final v in await (bd.select(bd.volteos)
            ..where((t) => t.fermentacionId.equals(f.id)))
          .get()) {
        hechos.add(HechoLote(
          fecha: v.fechaHora,
          etapa: 'Fermentación',
          titulo: 'Volteo',
          fotoId: v.fotoId,
        ));
      }
      for (final l in await (bd.select(bd.lecturasFermentacion)
            ..where((t) => t.fermentacionId.equals(f.id)))
          .get()) {
        hechos.add(HechoLote(
          fecha: l.fechaHora,
          etapa: 'Fermentación',
          titulo: l.tempC == null
              ? 'Lectura'
              : '${l.tempC!.toStringAsFixed(1)} °C',
          detalle: [
            if (l.ph != null) 'pH ${l.ph}',
            if (l.olor != null) 'olor ${l.olor!.etiqueta.toLowerCase()}',
          ].join(' · '),
          fotoId: l.fotoId,
        ));
      }
      if (f.fin != null) {
        hechos.add(HechoLote(
          fecha: f.fin!,
          etapa: 'Fermentación',
          titulo: 'Terminó la fermentación',
          detalle: '${f.fin!.difference(f.inicio).inHours} horas en total',
        ));
      }
    }

    final s = c.secado;
    if (s != null) {
      hechos.add(HechoLote(
        fecha: s.inicio,
        etapa: 'Secado',
        titulo: 'Empezó el secado (${s.metodo.etiqueta})',
      ));
      for (final l in await (bd.select(bd.lecturasSecado)
            ..where((t) => t.secadoId.equals(s.id)))
          .get()) {
        hechos.add(HechoLote(
          fecha: l.fecha,
          etapa: 'Secado',
          titulo: l.humedadGrano == null
              ? 'Jornada de secado'
              : 'Humedad ${l.humedadGrano!.toStringAsFixed(1)} %',
          detalle: [
            if (l.remociones > 0) '${l.remociones} remociones',
            if (l.moho) 'MOHO VISIBLE',
          ].join(' · '),
          fotoId: l.fotoId,
        ));
      }
      if (s.fin != null) {
        hechos.add(HechoLote(
          fecha: s.fin!,
          etapa: 'Secado',
          titulo: 'Terminó el secado',
          detalle: '${s.kgSeco ?? 0} kg secos',
        ));
      }
    }

    for (final p in c.pruebasCorte) {
      hechos.add(HechoLote(
        fecha: p.fecha,
        etapa: 'Prueba de corte',
        titulo: p.resultado,
        detalle: '${p.granos} granos'
            '${p.esParcial ? ' (prueba parcial)' : ''}',
        fotoId: p.fotoId,
      ));
    }

    for (final al in await alertas.deLote(loteId)) {
      hechos.add(HechoLote(
        fecha: al.fecha,
        etapa: al.regla,
        titulo: al.quePaso,
        detalle: al.queHacer,
        esAlerta: true,
      ));
    }

    hechos.sort((a, b) => a.fecha.compareTo(b.fecha));
    return hechos;
  }
}

/// Se intentó saltar una etapa obligatoria sin dar un motivo (RF-LOT-03).
class EtapaSaltadaSinMotivo implements Exception {
  const EtapaSaltadaSinMotivo(this.etapas);
  final List<EstadoLote> etapas;

  @override
  String toString() => 'Para saltar ${etapas.map((e) => e.etiqueta).join(", ")} '
      'hay que indicar el motivo, y queda registrado en el lote.';
}
