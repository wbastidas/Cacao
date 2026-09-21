// Tests de la base de datos, los repositorios y la cola de sincronización.
//
// Corren contra una base SQLite EN MEMORIA: cada test arranca con la base
// recién creada, así que las pruebas no dependen unas de otras ni dejan
// basura. Es también la forma de comprobar que el esquema, los índices y la
// siembra inicial funcionan de verdad, no solo que compilan.

import 'package:cacaotrace/datos/bd/base_datos.dart';
import 'package:cacaotrace/datos/bd/tablas/comunes.dart';
import 'package:cacaotrace/datos/repositorios/repositorio_alertas.dart';
import 'package:cacaotrace/datos/repositorios/repositorio_base.dart';
import 'package:cacaotrace/datos/repositorios/repositorio_configuracion.dart';
import 'package:cacaotrace/datos/repositorios/repositorio_lotes.dart';
import 'package:cacaotrace/datos/sync/servicio_sincronizacion.dart';
import 'package:cacaotrace/datos/sync/sincronizador_remoto.dart';
import 'package:cacaotrace/nucleo/modelo/etapas.dart';
import 'package:drift/drift.dart' hide isNotNull, isNull;
import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';

/// Sincronizador de pruebas: deja fallar a voluntad para probar los reintentos.
class SincronizadorFalso implements SincronizadorRemoto {
  SincronizadorFalso({this.resultado = ResultadoSync.exito});

  ResultadoSync resultado;
  bool disponible = true;
  final List<OperacionSync> enviados = [];
  final List<OperacionSync> archivos = [];

  @override
  String get nombre => 'Falso';

  @override
  Future<bool> estaDisponible() async => disponible;

  @override
  Future<ResultadoSync> enviarRegistro(OperacionSync op) async {
    enviados.add(op);
    return resultado;
  }

  @override
  Future<ResultadoSync> subirArchivo(OperacionSync op,
      {required bool soloWifi}) async {
    archivos.add(op);
    return resultado;
  }

  @override
  Future<ResultadoSync> respaldoCompleto() async => resultado;

  @override
  Future<ResultadoSync> descargarCambios() async => resultado;
}

void main() {
  late BaseDatos bd;
  late SincronizadorFalso remoto;
  late ServicioSincronizacion sync;
  late RepositorioConfiguracion config;
  late RepositorioAlertas repoAlertas;
  late RepositorioLotes lotes;
  var conectado = true;
  var conWifi = true;

  setUp(() async {
    bd = BaseDatos(NativeDatabase.memory());
    remoto = SincronizadorFalso();
    conectado = true;
    conWifi = true;
    sync = ServicioSincronizacion(
      bd: bd,
      remoto: remoto,
      hayConexion: () async => conectado,
      hayWifi: () async => conWifi,
    );
    config = RepositorioConfiguracion(bd, sync);
    repoAlertas = RepositorioAlertas(bd, sync);
    lotes = RepositorioLotes(bd, sync,
        config: config, alertas: repoAlertas);
  });

  tearDown(() async {
    await sync.cerrar();
    await bd.close();
  });

  group('esquema y siembra', () {
    test('la base se crea con los checklists de BPM listos', () async {
      final checklists = await bd.select(bd.checklistsBpm).get();
      expect(checklists.length, 4);
      expect(checklists.map((c) => c.nombre),
          contains('Limpieza diaria del taller'));
    });

    test('la base trae los stocks mínimos sembrados', () async {
      final stocks = await bd.select(bd.stocksMinimos).get();
      expect(stocks.map((s) => s.item), contains('azucar'));
    });

    test('las claves foráneas están activas', () async {
      final fila = await bd.customSelect('PRAGMA foreign_keys').getSingle();
      expect(fila.data.values.first, 1);
    });

    test('toda fila nace con id UUID y pendiente de sincronizar', () async {
      final lote = await lotes.crearLote();
      expect(lote.id.length, 36);
      expect(lote.estadoSync, EstadoSync.pendiente);
      expect(lote.eliminado, isFalse);
    });
  });

  group('códigos de lote', () {
    test('el primero del año es L-AAAA-001', () {
      expect(siguienteCodigo('L', 2026, []), 'L-2026-001');
    });

    test('sigue la numeración del año en curso', () {
      expect(
        siguienteCodigo('L', 2026, ['L-2026-001', 'L-2026-002', 'L-2025-009']),
        'L-2026-003',
      );
    });

    test('no se confunde con otro prefijo', () {
      expect(siguienteCodigo('P', 2026, ['L-2026-007']), 'P-2026-001');
    });

    test('crear dos lotes da códigos distintos', () async {
      final a = await lotes.crearLote();
      final b = await lotes.crearLote();
      expect(a.codigo, isNot(b.codigo));
    });
  });

  group('flujo completo del lote', () {
    test('de la recepción al secado, con las alertas que tocan', () async {
      final lote = await lotes.crearLote();

      await lotes.guardarRecepcion(
        loteId: lote.id,
        mazorcasTotal: 100,
        pesoKg: 45,
        sanas: 88,
        monilia: 8,
        fitoftora: 3,
        descartadas: 1,
        diasReposo: 4,
      );
      expect((await lotes.porId(lote.id))!.estado, EstadoLote.reposo);

      // Poca baba: debe saltar RN-02 con la corrección C-01.
      final alertasApertura = await lotes.guardarApertura(
        loteId: lote.id,
        mazorcasAbiertas: 100,
        kgBaba: 17,
      );
      expect(alertasApertura.any((a) => a.regla == 'RN-02'), isTrue);
      expect(
        alertasApertura.firstWhere((a) => a.regla == 'RN-02').correccion,
        'C-01',
      );

      final ferm = await lotes.iniciarFermentacion(
        loteId: lote.id,
        masaKg: 17,
        aislamiento: 'hojas y sacos',
      );
      expect((await lotes.porId(lote.id))!.estado, EstadoLote.fermentacion);

      // Lectura fría a las 72 h: RN-04.
      final alertasFerm = await lotes.registrarLectura(
        fermentacionId: ferm.id,
        loteId: lote.id,
        tempC: 38,
        cuando: ferm.inicio.add(const Duration(hours: 72)),
      );
      expect(alertasFerm.any((a) => a.regla == 'RN-04'), isTrue);

      await lotes.registrarVolteo(ferm.id);
      await lotes.cerrarFermentacion(ferm.id, lote.id);
      expect((await lotes.porId(lote.id))!.estado, EstadoLote.secado);

      final secado = await lotes.iniciarSecado(loteId: lote.id);
      await lotes.registrarLecturaSecado(
        secadoId: secado.id,
        loteId: lote.id,
        humedadGrano: 15,
        remociones: 4,
      );

      // Cerrar con humedad alta debe BLOQUEAR mientras no se confirme.
      final bloqueo = await lotes.cerrarSecado(
        secadoId: secado.id,
        loteId: lote.id,
        kgSeco: 6.1,
        humedadFinal: 9,
      );
      expect(bloqueo.any((a) => a.bloquea), isTrue);
      expect((await lotes.porId(lote.id))!.estado, EstadoLote.secado,
          reason: 'no debía avanzar con el bloqueo activo');

      // Confirmado por el usuario, ya pasa.
      await lotes.cerrarSecado(
        secadoId: secado.id,
        loteId: lote.id,
        kgSeco: 6.1,
        humedadFinal: 9,
        confirmado: true,
      );
      expect((await lotes.porId(lote.id))!.estado, EstadoLote.almacenado);
    });

    test('la línea de tiempo recoge todo en orden', () async {
      final lote = await lotes.crearLote();
      await lotes.guardarRecepcion(
          loteId: lote.id, mazorcasTotal: 100, pesoKg: 45);
      await lotes.guardarApertura(
          loteId: lote.id, mazorcasAbiertas: 100, kgBaba: 22);
      final ferm =
          await lotes.iniciarFermentacion(loteId: lote.id, masaKg: 22);
      await lotes.registrarVolteo(ferm.id);
      await lotes.registrarLectura(
          fermentacionId: ferm.id, loteId: lote.id, tempC: 46);

      final hechos = await lotes.lineaDeTiempo(lote.id);
      expect(hechos.length, greaterThanOrEqualTo(5));
      for (var i = 1; i < hechos.length; i++) {
        expect(
          hechos[i].fecha.isBefore(hechos[i - 1].fecha),
          isFalse,
          reason: 'la línea de tiempo debe ir en orden',
        );
      }
    });

    test('el balance de masa usa lo registrado', () async {
      final lote = await lotes.crearLote();
      await lotes.guardarRecepcion(
          loteId: lote.id, mazorcasTotal: 100, pesoKg: 45);
      await lotes.guardarApertura(
          loteId: lote.id, mazorcasAbiertas: 100, kgBaba: 17);

      final pasos = await lotes.balance(lote.id);
      expect(pasos.first.salidaReal, 17);
      expect(pasos.first.desvioPct!.abs(), lessThan(5));
      expect(pasos[1].registrado, isFalse);
    });
  });

  group('RF-LOT-03 no se salta una etapa obligatoria sin motivo', () {
    test('saltar de recepción a almacenado sin motivo falla', () async {
      final lote = await lotes.crearLote();
      expect(
        () => lotes.cambiarEstado(lote.id, EstadoLote.almacenado),
        throwsA(isA<EtapaSaltadaSinMotivo>()),
      );
    });

    test('con motivo pasa y el motivo queda escrito', () async {
      final lote = await lotes.crearLote();
      await lotes.cambiarEstado(
        lote.id,
        EstadoLote.almacenado,
        motivoSalto: 'el grano llegó ya seco y fermentado',
      );
      final guardado = await lotes.porId(lote.id);
      expect(guardado!.estado, EstadoLote.almacenado);
      expect(guardado.motivoSalto, contains('ya seco'));
    });

    test('avanzar de a una etapa nunca pide motivo', () async {
      final lote = await lotes.crearLote();
      await lotes.cambiarEstado(lote.id, EstadoLote.reposo);
      await lotes.cambiarEstado(lote.id, EstadoLote.fermentacion);
      expect((await lotes.porId(lote.id))!.estado, EstadoLote.fermentacion);
    });
  });

  group('alertas', () {
    test('no se repite una alerta que ya está abierta', () async {
      final lote = await lotes.crearLote();
      final ferm =
          await lotes.iniciarFermentacion(loteId: lote.id, masaKg: 25);

      for (var i = 0; i < 5; i++) {
        await lotes.registrarLectura(
          fermentacionId: ferm.id,
          loteId: lote.id,
          tempC: 35,
          cuando: ferm.inicio.add(Duration(hours: 72 + i)),
        );
      }
      final abiertas = await repoAlertas.deLote(lote.id);
      expect(abiertas.where((a) => a.regla == 'RN-04').length, 1);
    });

    test('atender una alerta guarda la corrección aplicada', () async {
      final lote = await lotes.crearLote();
      await lotes.guardarApertura(
          loteId: lote.id, mazorcasAbiertas: 100, kgBaba: 10);
      final abiertas = await repoAlertas.deLote(lote.id);
      final rn02 = abiertas.firstWhere((a) => a.regla == 'RN-02');

      await repoAlertas.atender(
        rn02.id,
        correccionCodigo: 'C-01',
        resultado: 'Añadí 12 kg de cáscara y subió a 45 °C',
      );

      final despues = await repoAlertas.deLote(lote.id);
      expect(
        despues.firstWhere((a) => a.id == rn02.id).estado,
        'atendida',
      );
      final aplicadas = await repoAlertas.correccionesDeLote(lote.id);
      expect(aplicadas.single.correccionCodigo, 'C-01');
      expect(aplicadas.single.resultado, contains('45 °C'));
    });

    test('detecta un bloqueo activo sobre el lote', () async {
      final lote = await lotes.crearLote();
      final secado = await lotes.iniciarSecado(
        loteId: lote.id,
        motivoSalto: 'el grano llegó ya fermentado',
      );
      await lotes.cerrarSecado(
        secadoId: secado.id,
        loteId: lote.id,
        kgSeco: 6,
        humedadFinal: 12,
      );
      expect(await repoAlertas.bloqueoActivo(lote.id), isNotNull);
    });
  });

  group('umbrales editables', () {
    test('cambiar un umbral cambia lo que alerta el motor', () async {
      await config.guardarUmbral('baba_masa_minima_kg', 10);
      final lote = await lotes.crearLote();
      final alertas = await lotes.guardarApertura(
          loteId: lote.id, mazorcasAbiertas: 100, kgBaba: 17);
      expect(alertas.any((a) => a.regla == 'RN-02'), isFalse,
          reason: 'con el mínimo bajado a 10 kg, 17 kg ya no debe alertar');
    });

    test('rechaza un valor fuera de rango', () async {
      expect(
        () => config.guardarUmbral('ferm_temp_maxima_c', 500),
        throwsArgumentError,
      );
    });

    test('restaurar devuelve el valor de fábrica', () async {
      await config.guardarUmbral('baba_masa_minima_kg', 10);
      expect((await config.umbrales())['baba_masa_minima_kg'], 10);
      await config.restaurarUmbral('baba_masa_minima_kg');
      expect((await config.umbrales())['baba_masa_minima_kg'], 20);
    });

    test('la preferencia de subir fotos solo con WiFi viene activada',
        () async {
      expect(await config.subirFotosSoloConWifi(), isTrue);
      await config.cambiarSubirFotosSoloConWifi(false);
      expect(await config.subirFotosSoloConWifi(), isFalse);
    });
  });

  group('cola de sincronización', () {
    test('cada escritura encola una operación', () async {
      expect(await sync.pendientes(), 0);
      await lotes.crearLote();
      expect(await sync.pendientes(), greaterThan(0));
    });

    test('sincronizar vacía la cola y marca los registros', () async {
      final lote = await lotes.crearLote();
      expect(await sync.pendientes(), greaterThan(0));

      await sync.sincronizar();
      expect(await sync.pendientes(), 0);
      expect(remoto.enviados, isNotEmpty);

      final guardado = await lotes.porId(lote.id);
      expect(guardado!.estadoSync, EstadoSync.sincronizado);
    });

    test('sin conexión no se intenta nada y la cola se conserva', () async {
      await lotes.crearLote();
      final antes = await sync.pendientes();
      conectado = false;

      expect(await sync.sincronizar(), 0);
      expect(await sync.pendientes(), antes);
      expect(remoto.enviados, isEmpty);
    });

    test('un fallo temporal programa un reintento, no pierde el dato',
        () async {
      await lotes.crearLote();
      remoto.resultado = ResultadoSync.reintentar;

      await sync.sincronizar();
      expect(await sync.pendientes(), greaterThan(0));

      final op = (await bd.select(bd.colaSync).get()).first;
      expect(op.intentos, 1);
      expect(op.proximoIntento!.isAfter(DateTime.now()), isTrue);
    });

    test('una falla permanente no borra el dato del usuario', () async {
      await lotes.crearLote();
      remoto.resultado = ResultadoSync.fallaPermanente;

      await sync.sincronizar();
      final conError = await sync.conError();
      expect(conError, isNotEmpty);
      expect(await sync.pendientes(), greaterThan(0));
    });

    test('reintentarTodo vuelve a intentar lo que quedó con error', () async {
      await lotes.crearLote();
      remoto.resultado = ResultadoSync.fallaPermanente;
      await sync.sincronizar();
      expect(await sync.conError(), isNotEmpty);

      remoto.resultado = ResultadoSync.exito;
      await sync.reintentarTodo();
      expect(await sync.pendientes(), 0);
    });

    test('RF-SYN-04: las fotos esperan al WiFi pero no frenan los registros',
        () async {
      await lotes.crearLote();
      await sync.encolar(
        tabla: 'fotos',
        registroId: 'foto-1',
        operacion: 'subir_foto',
        esArchivo: true,
      );
      conWifi = false;

      await sync.sincronizar();
      expect(remoto.archivos, isEmpty, reason: 'la foto debía esperar');
      expect(remoto.enviados, isNotEmpty, reason: 'los registros no esperan');
      expect(await sync.pendientes(), 1);

      conWifi = true;
      await sync.sincronizar();
      expect(remoto.archivos, isNotEmpty);
      expect(await sync.pendientes(), 0);
    });

    test('el indicador dice lo que pide RF-SYN-06', () async {
      expect((await sync.estadoActual()).etiqueta, 'Todo sincronizado');

      await lotes.crearLote();
      expect((await sync.estadoActual()).etiqueta, contains('pendientes'));

      conectado = false;
      expect((await sync.estadoActual()).etiqueta, 'Sin conexión');
    });

    test('dos sincronizaciones a la vez no duplican envíos', () async {
      await lotes.crearLote();
      final a = sync.sincronizar();
      final b = sync.sincronizar();
      final resultados = await Future.wait([a, b]);
      expect(resultados.where((n) => n > 0).length, 1,
          reason: 'solo una de las dos debía hacer trabajo');
    });
  });

  group('borrado lógico', () {
    test('una finca borrada desaparece de la lista pero sigue en la base',
        () async {
      final id = uuidGenerador.v4();
      await bd.into(bd.fincas).insert(
            FincasCompanion.insert(id: Value(id), nombre: 'Catarama'),
          );
      expect((await config.observarFincas().first).length, 1);

      await config.eliminarFinca(id);
      expect((await config.observarFincas().first), isEmpty);
      expect((await bd.select(bd.fincas).get()).length, 1);
    });
  });
}
