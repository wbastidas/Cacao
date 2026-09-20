/// Balance de masa del lote (RF-LOT-08).
///
/// Compara lo que se obtuvo en cada etapa con lo que era esperable, para
/// detectar errores al anotar pesos y para saber cuánto chocolate va a salir.
///
/// Referencia del §2.5 de la ERS para un lote típico:
///   ~100 mazorcas -> 16–18 kg de baba -> ~6 kg de grano seco -> ~5 kg de chocolate
library;

/// Rendimientos esperados de cada paso, en fracción (0–1) o kg por unidad.
///
/// Son valores por defecto para CCN-51 en la costa ecuatoriana. Se pueden
/// ajustar cuando el usuario tenga historial propio: después de 5 o 6 lotes,
/// su promedio real vale más que cualquier referencia.
class RendimientosEsperados {
  const RendimientosEsperados({
    this.kgBabaPorMazorca = 0.17,
    this.fraccionBabaASeco = 0.36,
    this.fraccionSecoANibs = 0.85,
    this.fraccionNibsAChocolate = 1.0,
  });

  /// kg de baba que da una mazorca.
  final double kgBabaPorMazorca;

  /// Cuánto queda al secar: la baba pierde ~64 % de su peso en agua y pulpa.
  final double fraccionBabaASeco;

  /// Cuánto queda al tostar y descascarillar: se va la cascarilla y humedad.
  final double fraccionSecoANibs;

  /// En un chocolate 90 %, los nibs son casi todo el producto; el azúcar y la
  /// manteca añaden peso, así que el factor real se calcula con la receta.
  final double fraccionNibsAChocolate;
}

/// Un paso del balance: lo esperado frente a lo real.
class PasoBalance {
  const PasoBalance({
    required this.etapa,
    required this.entrada,
    required this.unidadEntrada,
    required this.salidaReal,
    required this.salidaEsperada,
    required this.unidadSalida,
  });

  final String etapa;
  final double entrada;
  final String unidadEntrada;
  final double? salidaReal;
  final double salidaEsperada;
  final String unidadSalida;

  bool get registrado => salidaReal != null;

  /// Cuántos puntos porcentuales se desvía lo real de lo esperado.
  double? get desvioPct {
    final real = salidaReal;
    if (real == null || salidaEsperada <= 0) return null;
    return 100.0 * (real - salidaEsperada) / salidaEsperada;
  }

  /// Rendimiento real de este paso, en fracción.
  double? get rendimiento {
    final real = salidaReal;
    if (real == null || entrada <= 0) return null;
    return real / entrada;
  }
}

/// Calcula el balance de masa de un lote con los datos que haya hasta ahora.
class BalanceMasa {
  const BalanceMasa({this.esperados = const RendimientosEsperados()});

  final RendimientosEsperados esperados;

  /// Construye el balance con lo registrado hasta el momento.
  ///
  /// Cada parámetro puede ser nulo si esa etapa aún no ocurrió: el balance se
  /// muestra igual, con los pasos futuros marcados como "sin registrar".
  List<PasoBalance> calcular({
    required int mazorcas,
    double? kgBaba,
    double? kgSeco,
    double? kgNibs,
    double? kgChocolate,
  }) {
    final pasos = <PasoBalance>[];

    pasos.add(
      PasoBalance(
        etapa: 'Mazorcas a baba',
        entrada: mazorcas.toDouble(),
        unidadEntrada: 'mazorcas',
        salidaReal: kgBaba,
        salidaEsperada: mazorcas * esperados.kgBabaPorMazorca,
        unidadSalida: 'kg',
      ),
    );

    // Cada paso se compara con lo REAL del paso anterior cuando existe, no con
    // lo esperado: si no, un error temprano contamina todos los siguientes.
    final baseSeco = kgBaba ?? (mazorcas * esperados.kgBabaPorMazorca);
    pasos.add(
      PasoBalance(
        etapa: 'Baba a grano seco',
        entrada: baseSeco,
        unidadEntrada: 'kg',
        salidaReal: kgSeco,
        salidaEsperada: baseSeco * esperados.fraccionBabaASeco,
        unidadSalida: 'kg',
      ),
    );

    final baseNibs = kgSeco ?? (baseSeco * esperados.fraccionBabaASeco);
    pasos.add(
      PasoBalance(
        etapa: 'Grano seco a nibs',
        entrada: baseNibs,
        unidadEntrada: 'kg',
        salidaReal: kgNibs,
        salidaEsperada: baseNibs * esperados.fraccionSecoANibs,
        unidadSalida: 'kg',
      ),
    );

    final baseChocolate = kgNibs ?? (baseNibs * esperados.fraccionSecoANibs);
    pasos.add(
      PasoBalance(
        etapa: 'Nibs a chocolate',
        entrada: baseChocolate,
        unidadEntrada: 'kg',
        salidaReal: kgChocolate,
        salidaEsperada: baseChocolate * esperados.fraccionNibsAChocolate,
        unidadSalida: 'kg',
      ),
    );

    return pasos;
  }

  /// Cuánto chocolate cabe esperar del lote con lo que se sabe hasta ahora.
  double proyectarChocolate({
    required int mazorcas,
    double? kgBaba,
    double? kgSeco,
    double? kgNibs,
    double porcentajeCacao = 90,
  }) {
    final baba = kgBaba ?? mazorcas * esperados.kgBabaPorMazorca;
    final seco = kgSeco ?? baba * esperados.fraccionBabaASeco;
    final nibs = kgNibs ?? seco * esperados.fraccionSecoANibs;
    // En un chocolate al 90 %, los nibs son el 90 % del producto final.
    return porcentajeCacao <= 0 ? 0 : nibs * 100.0 / porcentajeCacao;
  }
}
