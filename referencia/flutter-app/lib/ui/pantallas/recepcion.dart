/// Recepción de mazorcas (RF-REC-01 a RF-REC-06).
///
/// El modo ráfaga es la parte importante: fotografiar mazorca por mazorca y
/// que la app lleve el conteo sola, en vez de que el usuario recuerde cuántas
/// tenían monilia mientras tiene las manos ocupadas.
library;

import 'package:flutter/material.dart';

import '../../servicios.dart';
import '../comun/camara_ia.dart';
import '../comun/widgets.dart';
import '../tema.dart';

const List<String> clasesMazorca = ['sana', 'monilia', 'fitoftora', 'otro'];

class PantallaRecepcion extends StatefulWidget {
  const PantallaRecepcion({super.key, required this.loteId});
  final String loteId;

  @override
  State<PantallaRecepcion> createState() => _PantallaRecepcionState();
}

class _PantallaRecepcionState extends State<PantallaRecepcion> {
  final _formulario = GlobalKey<FormState>();
  final _sacos = TextEditingController();
  final _mazorcas = TextEditingController();
  final _peso = TextEditingController();
  final _descartadas = TextEditingController(text: '0');
  final _motivo = TextEditingController();

  final Map<String, int> _conteoIa = {for (final c in clasesMazorca) c: 0};
  int _diasReposo = 4;
  bool _guardando = false;

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  Future<void> _cargar() async {
    final s = ProveedorServicios.de(context);
    final c = await s.lotes.completo(widget.loteId);
    final r = c?.recepcion;
    if (r == null || !mounted) return;
    setState(() {
      _sacos.text = '${r.sacos}';
      _mazorcas.text = '${r.mazorcasTotal}';
      _peso.text = '${r.pesoKg}';
      _descartadas.text = '${r.mazorcasDescartadas}';
      _motivo.text = r.motivoDescarte;
      _diasReposo = r.diasReposo;
      _conteoIa['sana'] = r.mazorcasSanas;
      _conteoIa['monilia'] = r.mazorcasMonilia;
      _conteoIa['fitoftora'] = r.mazorcasFitoftora;
      _conteoIa['otro'] = r.mazorcasOtro;
    });
  }

  int get _totalClasificadas =>
      _conteoIa.values.fold<int>(0, (a, b) => a + b);

  @override
  void dispose() {
    for (final c in [_sacos, _mazorcas, _peso, _descartadas, _motivo]) {
      c.dispose();
    }
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);
    final hayModelo = s.modelos.estaDisponible('mazorca');

    return Scaffold(
      appBar: AppBar(title: const Text('Recepción de mazorcas')),
      body: Form(
        key: _formulario,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            TarjetaSeccion(
              titulo: 'Lo que llegó',
              icono: Icons.local_shipping_outlined,
              hijos: [
                CampoNumero(
                  etiqueta: 'Mazorcas en total',
                  controlador: _mazorcas,
                  decimales: false,
                  obligatorio: true,
                  minimo: 1,
                ),
                CampoNumero(
                  etiqueta: 'Peso total',
                  controlador: _peso,
                  unidad: 'kg',
                  obligatorio: true,
                  minimo: 0,
                ),
                CampoNumero(
                  etiqueta: 'Sacos',
                  controlador: _sacos,
                  decimales: false,
                ),
              ],
            ),
            TarjetaSeccion(
              titulo: 'Estado sanitario',
              icono: Icons.health_and_safety_outlined,
              accion: Text('$_totalClasificadas clasificadas',
                  style: const TextStyle(fontSize: 14)),
              hijos: [
                Text(
                  hayModelo
                      ? 'Fotografía mazorca por mazorca: la app la clasifica '
                          'y lleva el conteo. Puedes corregir cada resultado.'
                      : 'Todavía no hay modelo instalado. Cuenta a mano con '
                          'los botones, o toma la foto y elige tú la clase.',
                  style: const TextStyle(fontSize: 15),
                ),
                const SizedBox(height: 12),
                BotonFotoIa(
                  tarea: 'mazorca',
                  clases: clasesMazorca,
                  texto: 'Fotografiar una mazorca',
                  consejo: 'Fondo claro, sin flash, a unos 30 cm y de frente.',
                  onCaptura: (captura) async {
                    // El mensajero se toma ANTES del await: después, el
                    // context de este widget puede haber dejado de ser válido.
                    final mensajero = ScaffoldMessenger.of(context);
                    setState(() {
                      _conteoIa[captura.decisionUsuario] =
                          (_conteoIa[captura.decisionUsuario] ?? 0) + 1;
                    });
                    await s.apoyo.guardarFoto(
                      rutaLocal: captura.archivo.path,
                      etapa: 'recepcion',
                      loteId: widget.loteId,
                      analisisIa: captura.analisisJson,
                      etiquetaUsuario: captura.decisionUsuario,
                      aptaDataset: captura.aptaParaDataset,
                    );
                    mensajero
                      ..hideCurrentSnackBar()
                      ..showSnackBar(SnackBar(
                        content: Text('Contada como '
                            '${nombreBonito(captura.decisionUsuario)}'),
                      ));
                  },
                ),
                const SizedBox(height: 16),
                for (final clase in clasesMazorca)
                  _ContadorClase(
                    clase: clase,
                    valor: _conteoIa[clase] ?? 0,
                    onCambio: (v) => setState(() => _conteoIa[clase] = v),
                  ),
              ],
            ),
            TarjetaSeccion(
              titulo: 'Descartes',
              icono: Icons.delete_outline,
              hijos: [
                CampoNumero(
                  etiqueta: 'Mazorcas descartadas',
                  controlador: _descartadas,
                  decimales: false,
                  ayuda: 'Las que no entran al proceso',
                ),
                TextFormField(
                  controller: _motivo,
                  decoration: const InputDecoration(
                    labelText: 'Motivo del descarte',
                    hintText: 'Monilia avanzada, mazorcas verdes…',
                  ),
                ),
              ],
            ),
            TarjetaSeccion(
              titulo: 'Reposo antes de abrir',
              icono: Icons.hotel_outlined,
              hijos: [
                const Text(
                  'El reposo de la mazorca cerrada ayuda a que la fermentación '
                  'arranque mejor. La app te avisará el día de la apertura.',
                  style: TextStyle(fontSize: 15),
                ),
                const SizedBox(height: 12),
                Row(
                  children: [
                    Expanded(
                      child: Slider(
                        value: _diasReposo.toDouble(),
                        min: 0,
                        max: 10,
                        divisions: 10,
                        label: '$_diasReposo días',
                        onChanged: (v) =>
                            setState(() => _diasReposo = v.round()),
                      ),
                    ),
                    SizedBox(
                      width: 72,
                      child: Text('$_diasReposo días',
                          style: const TextStyle(
                              fontSize: 17, fontWeight: FontWeight.w600)),
                    ),
                  ],
                ),
                if (_diasReposo < 3 || _diasReposo > 6)
                  const Aviso(
                    texto: 'Lo habitual son entre 3 y 6 días. Fuera de ese '
                        'rango, la fermentación suele arrancar peor.',
                    icono: Icons.info_outline,
                    color: ColoresEstado.neutro,
                  ),
              ],
            ),
            const SizedBox(height: 16),
            BotonGrande(
              texto: _guardando ? 'Guardando…' : 'Guardar la recepción',
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
      await s.lotes.guardarRecepcion(
        loteId: widget.loteId,
        mazorcasTotal: leerNumero(_mazorcas)!.round(),
        pesoKg: leerNumero(_peso) ?? 0,
        sacos: leerNumero(_sacos)?.round() ?? 0,
        sanas: _conteoIa['sana'] ?? 0,
        monilia: _conteoIa['monilia'] ?? 0,
        fitoftora: _conteoIa['fitoftora'] ?? 0,
        otro: _conteoIa['otro'] ?? 0,
        descartadas: leerNumero(_descartadas)?.round() ?? 0,
        motivoDescarte: _motivo.text.trim(),
        diasReposo: _diasReposo,
      );
      if (!mounted) return;
      avisar(context, 'Recepción guardada');
      Navigator.pop(context);
    } catch (e) {
      if (mounted) avisar(context, '$e', error: true);
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }
}

/// Contador con botones grandes, para usar con las manos ocupadas.
class _ContadorClase extends StatelessWidget {
  const _ContadorClase({
    required this.clase,
    required this.valor,
    required this.onCambio,
  });

  final String clase;
  final int valor;
  final ValueChanged<int> onCambio;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          Expanded(
            child: Text(nombreBonito(clase),
                style: const TextStyle(fontSize: 17)),
          ),
          IconButton.filledTonal(
            onPressed: valor > 0 ? () => onCambio(valor - 1) : null,
            icon: const Icon(Icons.remove),
            iconSize: 26,
          ),
          SizedBox(
            width: 56,
            child: Text('$valor',
                textAlign: TextAlign.center,
                style: const TextStyle(
                    fontSize: 22, fontWeight: FontWeight.w700)),
          ),
          IconButton.filledTonal(
            onPressed: () => onCambio(valor + 1),
            icon: const Icon(Icons.add),
            iconSize: 26,
          ),
        ],
      ),
    );
  }
}
