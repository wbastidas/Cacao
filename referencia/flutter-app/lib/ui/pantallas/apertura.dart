/// Apertura de mazorcas (RF-APE-01 a RF-APE-03).
library;

import 'package:flutter/material.dart';

import '../../servicios.dart';
import '../comun/widgets.dart';
import '../tema.dart';

class PantallaApertura extends StatefulWidget {
  const PantallaApertura({super.key, required this.loteId});
  final String loteId;

  @override
  State<PantallaApertura> createState() => _PantallaAperturaState();
}

class _PantallaAperturaState extends State<PantallaApertura> {
  final _formulario = GlobalKey<FormState>();
  final _mazorcas = TextEditingController();
  final _baba = TextEditingController();
  final _cascara = TextEditingController(text: '0');
  final _notas = TextEditingController();

  double _cascaraRecomendada = 0;
  bool _guardando = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _precargar());
  }

  Future<void> _precargar() async {
    final s = ProveedorServicios.de(context);
    final c = await s.lotes.completo(widget.loteId);
    if (!mounted) return;
    if (c?.apertura != null) {
      final a = c!.apertura!;
      setState(() {
        _mazorcas.text = '${a.mazorcasAbiertas}';
        _baba.text = '${a.kgBaba}';
        _cascara.text = '${a.kgCascaraAnadida}';
        _notas.text = a.notas;
      });
      await _recalcular();
    } else if (c?.recepcion != null) {
      setState(() {
        _mazorcas.text =
            '${c!.recepcion!.mazorcasTotal - c.recepcion!.mazorcasDescartadas}';
      });
    }
  }

  Future<void> _recalcular() async {
    final s = ProveedorServicios.de(context);
    final baba = leerNumero(_baba) ?? 0;
    final recomendada = await s.lotes.cascaraRecomendada(baba);
    if (mounted) setState(() => _cascaraRecomendada = recomendada);
  }

  @override
  void dispose() {
    for (final c in [_mazorcas, _baba, _cascara, _notas]) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final mazorcas = leerNumero(_mazorcas) ?? 0;
    final baba = leerNumero(_baba) ?? 0;
    final porMazorca = mazorcas > 0 ? baba / mazorcas : null;

    return Scaffold(
      appBar: AppBar(title: const Text('Apertura de mazorcas')),
      body: Form(
        key: _formulario,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            TarjetaSeccion(
              titulo: 'Lo que se abrió',
              icono: Icons.egg_outlined,
              hijos: [
                CampoNumero(
                  etiqueta: 'Mazorcas abiertas',
                  controlador: _mazorcas,
                  decimales: false,
                  obligatorio: true,
                  minimo: 1,
                  onChanged: (_) => setState(() {}),
                ),
                CampoNumero(
                  etiqueta: 'Baba obtenida',
                  controlador: _baba,
                  unidad: 'kg',
                  obligatorio: true,
                  minimo: 0,
                  ayuda: 'El grano fresco con pulpa, recién sacado',
                  onChanged: (_) {
                    setState(() {});
                    _recalcular();
                  },
                ),
                if (porMazorca != null)
                  FilaDato(
                    'Rendimiento por mazorca',
                    '${porMazorca.toStringAsFixed(3)} kg',
                    color: (porMazorca - 0.17).abs() / 0.17 > 0.2
                        ? ColoresEstado.atencion
                        : ColoresEstado.bien,
                  ),
                if (porMazorca != null)
                  const Text(
                    'Lo normal en CCN-51 son unos 0,170 kg por mazorca.',
                    style: TextStyle(fontSize: 14),
                  ),
              ],
            ),
            TarjetaSeccion(
              titulo: 'Cáscara añadida',
              icono: Icons.add_circle_outline,
              hijos: [
                const Text(
                  'Si hay poca baba, el montón no conserva el calor y la '
                  'fermentación se enfría. Añadir cáscara de mazorca troceada '
                  'aumenta la masa y el azúcar disponible.',
                  style: TextStyle(fontSize: 15),
                ),
                const SizedBox(height: 12),
                if (_cascaraRecomendada > 0)
                  Aviso(
                    icono: Icons.lightbulb_outline,
                    titulo: 'Te recomiendo añadir cáscara',
                    texto:
                        'Con ${baba.toStringAsFixed(1)} kg de baba te faltan '
                        'unos ${_cascaraRecomendada.toStringAsFixed(1)} kg '
                        'para llegar a la masa mínima.',
                    accion: OutlinedButton(
                      onPressed: () {
                        _cascara.text =
                            _cascaraRecomendada.toStringAsFixed(1);
                        setState(() {});
                      },
                      child: const Text('Usar esa cantidad'),
                    ),
                  ),
                CampoNumero(
                  etiqueta: 'Cáscara añadida',
                  controlador: _cascara,
                  unidad: 'kg',
                  minimo: 0,
                  onChanged: (_) => setState(() {}),
                ),
                FilaDato(
                  'Masa total al fermentador',
                  '${(baba + (leerNumero(_cascara) ?? 0)).toStringAsFixed(1)} kg',
                ),
              ],
            ),
            TarjetaSeccion(
              titulo: 'Notas',
              icono: Icons.edit_note,
              hijos: [
                TextFormField(
                  controller: _notas,
                  maxLines: 3,
                  decoration: const InputDecoration(
                    hintText: 'Lo que quieras recordar de esta apertura',
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            BotonGrande(
              texto: _guardando ? 'Guardando…' : 'Guardar la apertura',
              icono: Icons.save_outlined,
              onPressed: _guardando ? null : _guardar,
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _guardar() async {
    if (!_formulario.currentState!.validate()) return;
    final s = ProveedorServicios.de(context);
    setState(() => _guardando = true);

    try {
      final alertas = await s.lotes.guardarApertura(
        loteId: widget.loteId,
        mazorcasAbiertas: leerNumero(_mazorcas)!.round(),
        kgBaba: leerNumero(_baba)!,
        kgCascaraAnadida: leerNumero(_cascara) ?? 0,
        notas: _notas.text.trim(),
      );
      if (!mounted) return;
      avisar(
        context,
        alertas.isEmpty
            ? 'Apertura guardada'
            : 'Apertura guardada, con ${alertas.length} aviso(s)',
      );
      Navigator.pop(context);
    } catch (e) {
      if (mounted) avisar(context, '$e', error: true);
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }
}
