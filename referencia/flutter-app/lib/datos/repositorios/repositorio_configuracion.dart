/// Configuración: umbrales, tabla de la norma, fincas y equipos.
///
/// Aquí se cumple el RNF-12: nada de esto está cableado en el código, todo es
/// dato editable que se puede cambiar sin publicar una versión nueva.
library;

import 'package:drift/drift.dart';
import 'package:flutter/services.dart' show rootBundle;

import '../../nucleo/norma/tabla_norma.dart';
import '../../nucleo/reglas/umbrales.dart';
import '../bd/base_datos.dart';
import 'repositorio_base.dart';

class RepositorioConfiguracion extends RepositorioBase {
  RepositorioConfiguracion(super.bd, super.sync);

  TablaNorma? _normaEnCache;

  // ----------------------------------------------------------- umbrales

  /// Los umbrales vigentes: los de fábrica con encima los que el usuario cambió.
  Future<Umbrales> umbrales() async {
    final guardados = await bd.select(bd.umbralesGuardados).get();
    return Umbrales({
      for (final u in guardados.where((u) => !u.eliminado)) u.clave: u.valor,
    });
  }

  /// Guarda un umbral cambiado por el usuario (RF-CFG-03).
  ///
  /// Solo se guarda lo que difiere de fábrica: así, si un valor por defecto
  /// mejora en una versión futura, lo hereda quien no lo haya tocado.
  Future<void> guardarUmbral(String clave, double valor) async {
    // Valida el rango antes de escribir: mejor un error claro aquí que una
    // alerta absurda tres días después.
    Umbrales.porDefecto().con(clave, valor);

    final existente = await (bd.select(bd.umbralesGuardados)
          ..where((t) => t.clave.equals(clave)))
        .getSingleOrNull();

    if (existente == null) {
      await bd.into(bd.umbralesGuardados).insert(
            UmbralesGuardadosCompanion.insert(clave: clave, valor: valor),
          );
    } else {
      await (bd.update(bd.umbralesGuardados)
            ..where((t) => t.clave.equals(clave)))
          .write(UmbralesGuardadosCompanion(
        valor: Value(valor),
        modificadoEn: Value(DateTime.now()),
        eliminado: const Value(false),
      ));
    }
    await encolar('umbrales_guardados', clave, 'actualizar',
        carga: {'clave': clave, 'valor': valor});
  }

  /// Devuelve un umbral a su valor de fábrica.
  Future<void> restaurarUmbral(String clave) async {
    await (bd.delete(bd.umbralesGuardados)..where((t) => t.clave.equals(clave)))
        .go();
  }

  // ----------------------------------------------------------- norma

  /// La tabla de la norma vigente.
  ///
  /// Primero la copia que editó el usuario o que llegó por Remote Config;
  /// si no hay ninguna, la que viene dentro de la app. Nunca falla por falta
  /// de internet: usa siempre la última copia válida (§3.1 de la ERS).
  Future<TablaNorma> norma() async {
    if (_normaEnCache != null) return _normaEnCache!;
    final editada = await leerAjuste(claveNorma);
    if (editada != null && editada.isNotEmpty) {
      try {
        return _normaEnCache = TablaNorma.desdeJsonTexto(editada);
      } catch (_) {
        // Una tabla editada mal no puede dejar la app sin calificar: se cae a
        // la que viene de fábrica y se avisa al usuario desde Ajustes.
      }
    }
    final texto = await rootBundle.loadString('assets/norma/norma_inen176.json');
    return _normaEnCache = TablaNorma.desdeJsonTexto(texto);
  }

  /// Guarda una tabla de norma editada. Valida antes de aceptarla.
  Future<void> guardarNorma(TablaNorma tabla) async {
    final texto = tabla.aJsonTexto();
    TablaNorma.desdeJsonTexto(texto); // si no es válida, revienta aquí
    await guardarAjuste(claveNorma, texto);
    _normaEnCache = tabla;
  }

  /// Vuelve a la tabla de la norma que viene con la app.
  Future<void> restaurarNorma() async {
    await guardarAjuste(claveNorma, '');
    _normaEnCache = null;
  }

  void olvidarNormaEnCache() => _normaEnCache = null;

  // ----------------------------------------------------------- fincas

  Stream<List<Finca>> observarFincas() => (bd.select(bd.fincas)
        ..where((t) => t.eliminado.equals(false))
        ..orderBy([(t) => OrderingTerm(expression: t.nombre)]))
      .watch();

  Future<String> guardarFinca(FincasCompanion finca) async {
    final id = await bd.into(bd.fincas).insertOnConflictUpdate(finca);
    final codigo = finca.id.present ? finca.id.value : id.toString();
    await encolar('fincas', codigo, 'actualizar');
    return codigo;
  }

  /// Borrado lógico de una finca: desaparece de las listas pero los lotes que
  /// la referencian siguen contando de dónde vino su cacao.
  Future<void> eliminarFinca(String id) => marcarEliminado(bd.fincas, id);

  // ----------------------------------------------------------- equipos

  Stream<List<Equipo>> observarEquipos({String? tipo}) {
    final consulta = bd.select(bd.equipos)
      ..where((t) => t.eliminado.equals(false))
      ..orderBy([(t) => OrderingTerm(expression: t.nombre)]);
    if (tipo != null) consulta.where((t) => t.tipo.equals(tipo));
    return consulta.watch();
  }

  Future<void> eliminarEquipo(String id) => marcarEliminado(bd.equipos, id);

  Future<String> guardarEquipo(EquiposCompanion equipo) async {
    await bd.into(bd.equipos).insertOnConflictUpdate(equipo);
    final codigo = equipo.id.present ? equipo.id.value : '';
    await encolar('equipos', codigo, 'actualizar');
    return codigo;
  }

  // ----------------------------------------------------------- modelos IA

  /// El modelo activo para una tarea, o null si no hay ninguno instalado.
  Future<ModeloIa?> modeloActivo(String nombre) =>
      (bd.select(bd.modelosIa)
            ..where((t) =>
                t.nombre.equals(nombre) &
                t.activo.equals(true) &
                t.eliminado.equals(false)))
          .getSingleOrNull();

  /// Registra un modelo y lo deja activo, desactivando el anterior.
  ///
  /// El anterior no se borra: permite volver atrás si el nuevo resulta peor
  /// en el uso real (RF-IA-06).
  Future<void> activarModelo(ModelosIaCompanion modelo, String nombre) async {
    await bd.transaction(() async {
      await (bd.update(bd.modelosIa)..where((t) => t.nombre.equals(nombre)))
          .write(const ModelosIaCompanion(activo: Value(false)));
      await bd.into(bd.modelosIa).insertOnConflictUpdate(modelo);
    });
  }

  // ----------------------------------------------------------- ajustes

  static const String claveNorma = 'norma_json';
  static const String claveSoloWifi = 'subir_fotos_solo_wifi';
  static const String claveDispositivo = 'dispositivo_id';
  static const String claveUsuario = 'usuario_id';
  static const String claveUnidadPeso = 'unidad_peso';

  Future<String?> leerAjuste(String clave) async {
    final fila = await (bd.select(bd.configuracion)
          ..where((t) => t.clave.equals(clave)))
        .getSingleOrNull();
    return fila?.valor;
  }

  Future<void> guardarAjuste(String clave, String valor) async {
    final existente = await (bd.select(bd.configuracion)
          ..where((t) => t.clave.equals(clave)))
        .getSingleOrNull();
    if (existente == null) {
      await bd.into(bd.configuracion).insert(
            ConfiguracionCompanion.insert(clave: clave, valor: Value(valor)),
          );
    } else {
      await (bd.update(bd.configuracion)..where((t) => t.clave.equals(clave)))
          .write(ConfiguracionCompanion(
        valor: Value(valor),
        modificadoEn: Value(DateTime.now()),
      ));
    }
    await encolar('configuracion', clave, 'actualizar');
  }

  /// RF-SYN-04: activada por defecto, y así se queda mientras no la cambien.
  Future<bool> subirFotosSoloConWifi() async =>
      (await leerAjuste(claveSoloWifi)) != 'no';

  Future<void> cambiarSubirFotosSoloConWifi(bool soloWifi) =>
      guardarAjuste(claveSoloWifi, soloWifi ? 'si' : 'no');
}
