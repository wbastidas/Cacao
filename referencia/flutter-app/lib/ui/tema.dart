/// Tema visual de CacaoTrace.
///
/// Las decisiones de aquí vienen del §7 de la ERS y del entorno real de uso:
/// un taller caluroso, con las manos húmedas y a veces bajo el sol.
///
///  · botones de al menos 48 dp: se tocan con el dedo mojado;
///  · texto desde 16 sp: se lee sin acercarse el teléfono;
///  · alto contraste: se ve con reflejo del sol;
///  · iconos SIEMPRE con texto: el icono solo no se entiende;
///  · modo claro y oscuro (RNF-13).
library;

import 'package:flutter/material.dart';

/// Marrón del cacao tostado. Es el color de la marca y da buen contraste
/// tanto en claro como en oscuro.
const Color semilla = Color(0xFF6B4226);

/// Tamaño mínimo de cualquier cosa que se pueda tocar.
const double areaTactilMinima = 48;

/// Colores de estado, pensados para que se distingan también en escala de
/// grises y por quien confunde el rojo y el verde.
class ColoresEstado {
  static const Color bien = Color(0xFF2E7D32);
  static const Color atencion = Color(0xFFE65100);
  static const Color problema = Color(0xFFC62828);
  static const Color neutro = Color(0xFF546E7A);

  /// Colores de las clases de grano en la prueba de corte. Se eligieron para
  /// parecerse al color real del grano y ser distinguibles entre sí.
  static const Map<String, Color> clasesGrano = {
    'bien_fermentado': Color(0xFF8D5524),
    'ligeramente_fermentado': Color(0xFFB07D4F),
    'violeta': Color(0xFF7B5EA7),
    'pizarroso': Color(0xFF546E7A),
    'mohoso': Color(0xFFB0BEC5),
    'dano_insectos': Color(0xFFD84315),
    'germinado': Color(0xFF00897B),
    'vano_plano': Color(0xFFBDB76B),
    'otro': Color(0xFF9E9E9E),
  };

  static Color deGrano(String clase) =>
      clasesGrano[clase] ?? const Color(0xFF9E9E9E);
}

ThemeData _construir(Brightness brillo) {
  final esquema = ColorScheme.fromSeed(seedColor: semilla, brightness: brillo);

  return ThemeData(
    colorScheme: esquema,
    useMaterial3: true,
    // El texto base sube a 16 para que nada quede por debajo del mínimo.
    textTheme: const TextTheme(
      bodySmall: TextStyle(fontSize: 14),
      bodyMedium: TextStyle(fontSize: 16),
      bodyLarge: TextStyle(fontSize: 18),
      titleMedium: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
      titleLarge: TextStyle(fontSize: 22, fontWeight: FontWeight.w600),
      headlineSmall: TextStyle(fontSize: 26, fontWeight: FontWeight.w600),
      labelLarge: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        minimumSize: const Size(0, 56),
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 14),
        textStyle: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        minimumSize: const Size(0, 52),
        textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        minimumSize: const Size(0, areaTactilMinima),
        textStyle: const TextStyle(fontSize: 16),
      ),
    ),
    inputDecorationTheme: const InputDecorationTheme(
      border: OutlineInputBorder(),
      contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 18),
      labelStyle: TextStyle(fontSize: 16),
      helperMaxLines: 3,
      errorMaxLines: 3,
    ),
    listTileTheme: const ListTileThemeData(
      minVerticalPadding: 12,
      titleTextStyle: TextStyle(fontSize: 17, fontWeight: FontWeight.w500),
      subtitleTextStyle: TextStyle(fontSize: 15),
    ),
    cardTheme: CardThemeData(
      elevation: 0,
      margin: const EdgeInsets.symmetric(vertical: 6),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: esquema.outlineVariant),
      ),
    ),
    chipTheme: const ChipThemeData(
      padding: EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      labelStyle: TextStyle(fontSize: 15),
    ),
    snackBarTheme: const SnackBarThemeData(
      contentTextStyle: TextStyle(fontSize: 16),
      behavior: SnackBarBehavior.floating,
    ),
    appBarTheme: const AppBarTheme(
      centerTitle: false,
      titleTextStyle: TextStyle(fontSize: 22, fontWeight: FontWeight.w600),
    ),
  );
}

ThemeData temaClaro() => _construir(Brightness.light);
ThemeData temaOscuro() => _construir(Brightness.dark);
