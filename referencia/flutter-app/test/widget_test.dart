// Prueba de humo de la app completa: arranca con una base en memoria y
// comprueba que la pantalla de inicio se dibuja sin explotar.
//
// Es la prueba que detecta los errores tontos y caros: un provider que falta,
// un asset mal declarado, una pantalla que se cae al no haber datos.

import 'package:cacaotrace/datos/bd/base_datos.dart';
import 'package:cacaotrace/servicios.dart';
import 'package:cacaotrace/ui/app.dart';
import 'package:cacaotrace/ui/tema.dart';
import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('la app arranca y muestra la pantalla de hoy', (tester) async {
    final bd = BaseDatos(NativeDatabase.memory());
    final servicios = await Servicios.arrancar(baseDatos: bd);

    await tester.pumpWidget(
      MaterialApp(
        theme: temaClaro(),
        home: ProveedorServicios(
          servicios: servicios,
          child: const AppCacaoTrace(),
        ),
      ),
    );
    await tester.pump();

    expect(find.text('¿Qué hago hoy?'), findsOneWidget);
    expect(find.text('Hoy'), findsOneWidget);
    expect(find.text('Lotes'), findsOneWidget);
    expect(find.text('Chocolate'), findsOneWidget);
    expect(find.text('Panel'), findsOneWidget);
    expect(find.text('Ajustes'), findsOneWidget);

    await servicios.cerrar();
  });

  testWidgets('sin lotes, la pantalla de lotes invita a crear el primero',
      (tester) async {
    final bd = BaseDatos(NativeDatabase.memory());
    final servicios = await Servicios.arrancar(baseDatos: bd);

    await tester.pumpWidget(
      MaterialApp(
        theme: temaClaro(),
        home: ProveedorServicios(
          servicios: servicios,
          child: const AppCacaoTrace(),
        ),
      ),
    );
    await tester.pump();

    await tester.tap(find.text('Lotes'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 100));

    expect(find.text('Todavía no hay lotes'), findsOneWidget);

    await servicios.cerrar();
  });
}
