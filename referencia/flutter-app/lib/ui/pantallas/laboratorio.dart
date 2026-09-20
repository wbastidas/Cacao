/// Resultados de laboratorio (RF-LAB-01, RF-LAB-02).
///
/// El cadmio es el análisis que decide si un lote se puede vender a Europa.
/// Por eso, si supera el límite, la app bloquea la venta del lote hasta que
/// el usuario lo desbloquee a conciencia, y el desbloqueo queda auditado.
library;

import 'package:flutter/material.dart';

import '../../datos/bd/base_datos.dart';
import '../../servicios.dart';
import '../comun/camara_ia.dart';
import '../comun/widgets.dart';
import '../tema.dart';

const List<String> analisisComunes = [
  'cadmio',
  'humedad',
  'microbiologia',
  'plomo',
  'grasa',
];

class PantallaLaboratorio extends StatefulWidget {
  const PantallaLaboratorio({super.key});

  @override
  State<PantallaLaboratorio> createState() => _PantallaLaboratorioState();
}

class _PantallaLaboratorioState extends State<PantallaLaboratorio> {
  Future<List<Laboratorio>>? _resultados;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _recargar();
  }

  void _recargar() {
    final s = ProveedorServicios.de(context);
    // El paréntesis con flecha devolvería el Future de la asignación, y
    // setState no admite un callback asíncrono: por eso va en bloque.
    setState(() {
      _resultados = s.bd.select(s.bd.laboratorios).get();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Laboratorio')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _nuevo,
        icon: const Icon(Icons.add),
        label: const Text('Nuevo resultado'),
      ),
      body: FutureBuilder<List<Laboratorio>>(
        future: _resultados,
        builder: (context, snapshot) {
          final resultados = snapshot.data ?? const [];
          if (resultados.isEmpty) {
            return const EstadoVacio(
              icono: Icons.science_outlined,
              titulo: 'Sin análisis registrados',
              explicacion:
                  'La humedad y el cadmio se miden con medidor y laboratorio '
                  'acreditado, no con fotos. Aquí guardas esos resultados y '
                  'la app te avisa si el cadmio pasa del límite.',
            );
          }
          return ListView(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
            children: [
              for (final r in resultados)
                Card(
                  child: ListTile(
                    leading: Icon(
                      r.limite != null && r.valor > r.limite!
                          ? Icons.dangerous_outlined
                          : Icons.check_circle_outline,
                      color: r.limite != null && r.valor > r.limite!
                          ? ColoresEstado.problema
                          : ColoresEstado.bien,
                      size: 30,
                    ),
                    title: Text(
                      '${nombreBonito(r.analisis)}: ${r.valor} ${r.unidad}',
                    ),
                    subtitle: Text([
                      '${r.fecha.day}/${r.fecha.month}/${r.fecha.year}',
                      if (r.laboratorio.isNotEmpty) r.laboratorio,
                      if (r.limite != null) 'límite ${r.limite}',
                    ].join(' · ')),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }

  Future<void> _nuevo() async {
    final s = ProveedorServicios.de(context);
    final lotes = await s.lotes.observarLotes().first;
    if (!mounted) return;

    final valor = TextEditingController();
    final laboratorio = TextEditingController();
    var analisis = 'cadmio';
    String? loteId = lotes.isEmpty ? null : lotes.first.id;

    final guardar = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (contexto) => StatefulBuilder(
        builder: (contexto, actualizar) => Padding(
          padding: EdgeInsets.fromLTRB(
              16, 24, 16, MediaQuery.of(contexto).viewInsets.bottom + 24),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Resultado de laboratorio',
                    style: Theme.of(contexto).textTheme.titleLarge),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  children: [
                    for (final a in analisisComunes)
                      ChoiceChip(
                        label: Text(nombreBonito(a)),
                        selected: analisis == a,
                        onSelected: (_) => actualizar(() => analisis = a),
                      ),
                  ],
                ),
                const SizedBox(height: 12),
                if (lotes.isNotEmpty)
                  DropdownButtonFormField<String>(
                    initialValue: loteId,
                    decoration: const InputDecoration(labelText: 'Lote'),
                    items: [
                      for (final l in lotes)
                        DropdownMenuItem(
                            value: l.id, child: Text(l.codigo)),
                    ],
                    onChanged: (v) => actualizar(() => loteId = v),
                  ),
                CampoNumero(
                  etiqueta: 'Valor',
                  controlador: valor,
                  unidad: analisis == 'cadmio' ? 'mg/kg' : '',
                  obligatorio: true,
                ),
                TextFormField(
                  controller: laboratorio,
                  decoration:
                      const InputDecoration(labelText: 'Laboratorio'),
                ),
                if (analisis == 'cadmio')
                  const Aviso(
                    icono: Icons.info_outline,
                    color: ColoresEstado.neutro,
                    texto:
                        'Si el cadmio supera el límite configurado, la app '
                        'bloqueará la venta de ese lote hasta que lo '
                        'desbloquees. El límite se edita en Ajustes.',
                  ),
                const SizedBox(height: 16),
                BotonGrande(
                  texto: 'Guardar',
                  icono: Icons.save_outlined,
                  onPressed: () => Navigator.pop(contexto, true),
                ),
              ],
            ),
          ),
        ),
      ),
    );

    if (guardar != true || !mounted) return;
    final n = leerNumero(valor);
    if (n == null) return;

    final umbrales = await s.config.umbrales();
    final alertas = await s.apoyo.guardarLaboratorio(
      loteId: loteId,
      analisis: analisis,
      valor: n,
      unidad: analisis == 'cadmio' ? 'mg/kg' : '',
      limite: analisis == 'cadmio' ? umbrales['cadmio_limite_mg_kg'] : null,
      laboratorio: laboratorio.text.trim(),
    );
    if (!mounted) return;
    avisar(
      context,
      alertas.isEmpty
          ? 'Resultado guardado'
          : '${alertas.first.quePaso}. ${alertas.first.queHacer}',
      error: alertas.isNotEmpty,
    );
    _recargar();
  }
}
