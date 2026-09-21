/// Panel de indicadores y acceso a inventario, costos, BPM y laboratorio
/// (RF-TAB-02, RF-REP-01).
library;

import 'package:flutter/material.dart';

import '../../servicios.dart';
import '../comun/widgets.dart';
import 'bpm.dart';
import 'guias.dart';
import 'inventario.dart';
import 'laboratorio.dart';

class PantallaPanel extends StatefulWidget {
  const PantallaPanel({super.key});

  @override
  State<PantallaPanel> createState() => _PantallaPanelState();
}

class _PantallaPanelState extends State<PantallaPanel> {
  Future<Map<String, String>>? _indicadores;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final s = ProveedorServicios.de(context);
    setState(() {
      _indicadores = s.apoyo.indicadores();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Panel')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          FutureBuilder<Map<String, String>>(
            future: _indicadores,
            builder: (context, snapshot) {
              final datos = snapshot.data ?? const {};
              if (datos.isEmpty) {
                return const Card(
                  child: Padding(
                    padding: EdgeInsets.all(24),
                    child: Text(
                      'Cuando registres lotes, aquí verás tus números: '
                      'rendimientos, porcentaje de fermentación, temperatura '
                      'máxima y alertas.',
                      style: TextStyle(fontSize: 16),
                    ),
                  ),
                );
              }
              return TarjetaSeccion(
                titulo: 'Tus números',
                icono: Icons.insights,
                hijos: [
                  for (final e in datos.entries) FilaDato(e.key, e.value),
                ],
              );
            },
          ),
          const SizedBox(height: 8),
          _Acceso(
            titulo: 'Inventario, costos y ventas',
            subtitulo: 'Qué tienes, qué te costó y qué vendiste',
            icono: Icons.calculate_outlined,
            destino: const PantallaInventario(),
          ),
          _Acceso(
            titulo: 'Buenas prácticas (BPM)',
            subtitulo: 'Checklists diarios y su registro firmado',
            icono: Icons.checklist_rtl,
            destino: const PantallaBpm(),
          ),
          _Acceso(
            titulo: 'Laboratorio',
            subtitulo: 'Cadmio, humedad y microbiología',
            icono: Icons.science_outlined,
            destino: const PantallaLaboratorio(),
          ),
          _Acceso(
            titulo: 'Guías y glosario',
            subtitulo: 'Cómo se hace cada etapa, sin internet',
            icono: Icons.menu_book_outlined,
            destino: const PantallaGuias(),
          ),
        ],
      ),
    );
  }
}

class _Acceso extends StatelessWidget {
  const _Acceso({
    required this.titulo,
    required this.subtitulo,
    required this.icono,
    required this.destino,
  });

  final String titulo;
  final String subtitulo;
  final IconData icono;
  final Widget destino;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: Icon(icono, size: 30),
        title: Text(titulo),
        subtitle: Text(subtitulo),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => Navigator.push(
            context, MaterialPageRoute(builder: (_) => destino)),
      ),
    );
  }
}
