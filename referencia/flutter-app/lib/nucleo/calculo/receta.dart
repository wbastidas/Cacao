/// Calculadora de receta de chocolate (RF-REF-01).
///
/// El usuario entra los kg de nibs que tiene y el porcentaje de cacao que
/// quiere (90 %), y la app le dice cuánto azúcar, manteca y lecitina poner.
library;

/// Los ingredientes de una tanda, en kg.
class Receta {
  const Receta({
    required this.kgNibs,
    required this.kgAzucar,
    required this.kgMantecaAnadida,
    required this.kgLecitina,
    required this.porcentajeCacao,
  });

  final double kgNibs;
  final double kgAzucar;

  /// Manteca de cacao añadida aparte de la que ya traen los nibs.
  final double kgMantecaAnadida;
  final double kgLecitina;
  final double porcentajeCacao;

  /// Peso total de la tanda.
  double get kgTotal => kgNibs + kgAzucar + kgMantecaAnadida + kgLecitina;

  /// Porcentaje de cacao que realmente sale, contando la manteca añadida como
  /// parte del cacao (que lo es).
  double get porcentajeCacaoReal =>
      kgTotal <= 0 ? 0 : 100.0 * (kgNibs + kgMantecaAnadida) / kgTotal;

  /// Cuántas barras salen, con la merma de moldeado.
  int barrasEstimadas({double pesoBarraG = 50, double mermaPct = 5}) {
    if (pesoBarraG <= 0) return 0;
    final kgUtiles = kgTotal * (1 - mermaPct / 100);
    return (kgUtiles * 1000 / pesoBarraG).floor();
  }
}

/// Calcula la receta a partir de los nibs disponibles.
class CalculadoraReceta {
  const CalculadoraReceta();

  /// Proporción de lecitina sobre el total. Es un emulsionante: con muy poco
  /// basta, y de más deja sabor.
  static const double proporcionLecitina = 0.005;

  /// Calcula la receta.
  ///
  /// [porcentajeCacao] es el objetivo (90 para un chocolate 90 %).
  /// [mantecaExtraPct] es manteca añadida sobre el total, para que el chocolate
  /// fluya mejor al moldear; en un 90 % suele hacer falta algo.
  Receta calcular({
    required double kgNibs,
    double porcentajeCacao = 90,
    double mantecaExtraPct = 0,
    bool usarLecitina = true,
  }) {
    if (kgNibs <= 0) {
      throw ArgumentError('Los kg de nibs deben ser mayores que cero');
    }
    if (porcentajeCacao <= 0 || porcentajeCacao > 100) {
      throw ArgumentError('El porcentaje de cacao debe estar entre 1 y 100');
    }
    if (mantecaExtraPct < 0 || mantecaExtraPct >= porcentajeCacao) {
      throw ArgumentError(
        'La manteca añadida debe ser menor que el porcentaje de cacao',
      );
    }

    // El total sale de que nibs + manteca = porcentajeCacao % del total.
    // total = kgNibs / ((porcentajeCacao - mantecaExtraPct) / 100)
    final fraccionNibs = (porcentajeCacao - mantecaExtraPct) / 100.0;
    final total = kgNibs / fraccionNibs;

    final manteca = total * mantecaExtraPct / 100.0;
    final lecitina = usarLecitina ? total * proporcionLecitina : 0.0;
    final azucar = total - kgNibs - manteca - lecitina;

    if (azucar < 0) {
      throw ArgumentError(
        'Con esos valores no queda espacio para el azúcar. Baja el porcentaje '
        'de cacao o la manteca añadida.',
      );
    }

    return Receta(
      kgNibs: kgNibs,
      kgAzucar: azucar,
      kgMantecaAnadida: manteca,
      kgLecitina: lecitina,
      porcentajeCacao: porcentajeCacao,
    );
  }

  /// El camino inverso: cuántos nibs hacen falta para un total dado.
  double nibsNecesarios({
    required double kgTotalDeseado,
    double porcentajeCacao = 90,
    double mantecaExtraPct = 0,
  }) =>
      kgTotalDeseado * (porcentajeCacao - mantecaExtraPct) / 100.0;
}
