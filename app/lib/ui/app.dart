/// Armazón de la app: las cinco secciones principales.
///
/// Cinco y no más: con una barra de seis o siete, los destinos se vuelven
/// demasiado estrechos para tocarlos con el dedo. Todo lo demás cuelga de
/// estas cinco pantallas.
library;

import 'package:flutter/material.dart';

import 'pantallas/ajustes.dart';
import 'pantallas/inicio.dart';
import 'pantallas/lotes.dart';
import 'pantallas/panel.dart';
import 'pantallas/produccion.dart';

class AppCacaoTrace extends StatefulWidget {
  const AppCacaoTrace({super.key});

  @override
  State<AppCacaoTrace> createState() => _AppCacaoTraceState();
}

class _AppCacaoTraceState extends State<AppCacaoTrace> {
  int _seccion = 0;

  static const _pantallas = [
    PantallaInicio(),
    PantallaLotes(),
    PantallaProduccion(),
    PantallaPanel(),
    PantallaAjustes(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(index: _seccion, children: _pantallas),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _seccion,
        height: 72,
        onDestinationSelected: (i) => setState(() => _seccion = i),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.today_outlined),
            selectedIcon: Icon(Icons.today),
            label: 'Hoy',
          ),
          NavigationDestination(
            icon: Icon(Icons.inventory_2_outlined),
            selectedIcon: Icon(Icons.inventory_2),
            label: 'Lotes',
          ),
          NavigationDestination(
            icon: Icon(Icons.cookie_outlined),
            selectedIcon: Icon(Icons.cookie),
            label: 'Chocolate',
          ),
          NavigationDestination(
            icon: Icon(Icons.insights_outlined),
            selectedIcon: Icon(Icons.insights),
            label: 'Panel',
          ),
          NavigationDestination(
            icon: Icon(Icons.settings_outlined),
            selectedIcon: Icon(Icons.settings),
            label: 'Ajustes',
          ),
        ],
      ),
    );
  }
}
