/// Piezas comunes a todos los repositorios.
///
/// Un repositorio es lo que junta las tres cosas que una pantalla necesita:
/// escribir en la base, encolar la subida a la nube y aplicar las reglas de
/// negocio. Las pantallas nunca hablan con Drift directamente.
library;

import 'dart:convert';

import 'package:drift/drift.dart';

import '../bd/base_datos.dart';
import '../sync/servicio_sincronizacion.dart';

abstract class RepositorioBase {
  const RepositorioBase(this.bd, this.sync);

  final BaseDatos bd;
  final ServicioSincronizacion sync;

  /// Encola la subida de un registro recién escrito.
  Future<void> encolar(
    String tabla,
    String registroId,
    String operacion, {
    Map<String, dynamic>? carga,
    bool esArchivo = false,
  }) =>
      sync.encolar(
        tabla: tabla,
        registroId: registroId,
        operacion: operacion,
        cargaJson: jsonEncode(carga ?? const {}),
        esArchivo: esArchivo,
      );

  /// Deja constancia de un cambio en un registro sensible (RNF-11).
  ///
  /// Se usa en BPM, prueba de corte y laboratorio: son los registros que un
  /// inspector puede pedir y donde importa poder decir quién cambió qué.
  Future<void> auditar({
    required String tabla,
    required String registroId,
    required String campo,
    required String valorAnterior,
    required String valorNuevo,
    String motivo = '',
  }) async {
    if (valorAnterior == valorNuevo) return;
    await bd.into(bd.auditoria).insert(
          AuditoriaCompanion.insert(
            tabla: tabla,
            registroId: registroId,
            campo: campo,
            valorAnterior: Value(valorAnterior),
            valorNuevo: Value(valorNuevo),
            fecha: DateTime.now(),
            motivo: Value(motivo),
          ),
        );
  }

  /// Marca un registro como borrado sin quitarlo de la base.
  ///
  /// Borrar de verdad rompería la sincronización (el otro teléfono no se
  /// enteraría) y dejaría huecos en la trazabilidad de un lote.
  Future<void> marcarEliminado(TableInfo tabla, String id) async {
    final nombreTabla = tabla.actualTableName;
    await bd.customStatement(
      'UPDATE $nombreTabla SET eliminado = 1, modificado_en = ?, '
      'estado_sync = 0 WHERE id = ?',
      [DateTime.now().millisecondsSinceEpoch ~/ 1000, id],
    );
    // Drift no rastrea lo que toca un customStatement: si no se le avisa, la
    // lista que el usuario tiene abierta seguiría mostrando el registro.
    bd.notifyUpdates({TableUpdate.onTable(tabla, kind: UpdateKind.update)});
    await encolar(nombreTabla, id, 'eliminar');
  }
}

/// Genera los códigos visibles de lote (L-AAAA-NNN) y producción (P-AAAA-NNN).
///
/// Se numera por año y de forma correlativa dentro del año, que es como el
/// usuario los nombra al hablar: "el lote 3 del año pasado".
String siguienteCodigo(String prefijo, int anio, List<String> existentes) {
  final patron = RegExp('^$prefijo-$anio-(\\d+)\$');
  var mayor = 0;
  for (final c in existentes) {
    final m = patron.firstMatch(c);
    if (m != null) {
      final n = int.tryParse(m.group(1)!) ?? 0;
      if (n > mayor) mayor = n;
    }
  }
  return '$prefijo-$anio-${(mayor + 1).toString().padLeft(3, '0')}';
}
