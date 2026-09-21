/// Almacenamiento: sacos con QR e inspecciones periódicas (RF-ALM-01/02/03).
library;

import 'package:flutter/material.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../datos/bd/base_datos.dart';
import '../../servicios.dart';
import '../comun/widgets.dart';

class PantallaAlmacen extends StatelessWidget {
  const PantallaAlmacen({super.key, this.loteId});
  final String? loteId;

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Almacenamiento')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          BotonGrande(
            texto: 'Registrar una inspección',
            subtitulo: 'Humedad, olor, plagas y moho',
            icono: Icons.fact_check_outlined,
            onPressed: () => _inspeccionar(context),
          ),
          const SizedBox(height: 12),
          if (loteId != null)
            OutlinedButton.icon(
              icon: const Icon(Icons.add),
              label: const Text('Añadir un saco'),
              onPressed: () => _nuevoSaco(context, loteId!),
            ),
          const SizedBox(height: 16),
          Text('Sacos', style: Theme.of(context).textTheme.titleLarge),
          StreamBuilder<List<Saco>>(
            stream: s.apoyo.observarSacos(loteId: loteId),
            builder: (context, snapshot) {
              final sacos = snapshot.data ?? const [];
              if (sacos.isEmpty) {
                return const Padding(
                  padding: EdgeInsets.symmetric(vertical: 24),
                  child: Text(
                    'Todavía no hay sacos registrados. Cada saco lleva su '
                    'propio QR: al escanearlo se abre la historia del lote.',
                    style: TextStyle(fontSize: 16),
                  ),
                );
              }
              return Column(
                children: [
                  for (final saco in sacos)
                    Card(
                      child: ListTile(
                        leading: const Icon(Icons.inventory_2_outlined),
                        title: Text(saco.codigoQr),
                        subtitle: Text(
                          '${saco.pesoKg} kg'
                          '${saco.ubicacion.isEmpty ? '' : ' · ${saco.ubicacion}'}',
                        ),
                        trailing: IconButton(
                          icon: const Icon(Icons.qr_code_2),
                          onPressed: () => _mostrarQr(context, saco.codigoQr),
                        ),
                      ),
                    ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }

  void _mostrarQr(BuildContext context, String codigo) {
    showDialog<void>(
      context: context,
      builder: (contexto) => AlertDialog(
        title: Text(codigo),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            QrImageView(
                data: codigo, size: 220, backgroundColor: Colors.white),
            const SizedBox(height: 12),
            const Text('Imprime y pega en el saco.',
                style: TextStyle(fontSize: 15)),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(contexto),
            child: const Text('Cerrar'),
          ),
        ],
      ),
    );
  }

  Future<void> _nuevoSaco(BuildContext context, String loteId) async {
    final s = ProveedorServicios.de(context);
    final peso = TextEditingController();
    final ubicacion = TextEditingController();

    final crear = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (contexto) => Padding(
        padding: EdgeInsets.fromLTRB(
            16, 24, 16, MediaQuery.of(contexto).viewInsets.bottom + 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Nuevo saco',
                style: Theme.of(contexto).textTheme.titleLarge),
            CampoNumero(
              etiqueta: 'Peso',
              controlador: peso,
              unidad: 'kg',
              obligatorio: true,
              minimo: 0,
            ),
            TextFormField(
              controller: ubicacion,
              decoration: const InputDecoration(
                labelText: 'Dónde queda',
                hintText: 'Bodega, estante 2',
              ),
            ),
            const SizedBox(height: 16),
            BotonGrande(
              texto: 'Crear el saco',
              icono: Icons.check,
              onPressed: () => Navigator.pop(contexto, true),
            ),
          ],
        ),
      ),
    );

    if (crear != true || !context.mounted) return;
    final kg = leerNumero(peso);
    if (kg == null) return;
    final codigo =
        await s.apoyo.crearSaco(loteId: loteId, pesoKg: kg,
            ubicacion: ubicacion.text.trim());
    if (context.mounted) avisar(context, 'Saco $codigo creado');
  }

  Future<void> _inspeccionar(BuildContext context) async {
    final s = ProveedorServicios.de(context);
    final hr = TextEditingController();
    final temp = TextEditingController();
    final olor = TextEditingController();
    var plagas = false;
    var moho = false;

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
                Text('Inspección del almacén',
                    style: Theme.of(contexto).textTheme.titleLarge),
                const SizedBox(height: 8),
                const Text(
                  'Los sacos no deben tocar el piso ni las paredes, y el '
                  'lugar tiene que estar seco y ventilado.',
                  style: TextStyle(fontSize: 15),
                ),
                CampoNumero(
                  etiqueta: 'Humedad relativa',
                  controlador: hr,
                  unidad: '%',
                  maximo: 100,
                ),
                CampoNumero(
                  etiqueta: 'Temperatura',
                  controlador: temp,
                  unidad: '°C',
                ),
                TextFormField(
                  controller: olor,
                  decoration: const InputDecoration(
                    labelText: 'Olor',
                    hintText: 'Normal, a humedad, a moho…',
                  ),
                ),
                SwitchListTile(
                  value: plagas,
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Vi plagas'),
                  onChanged: (v) => actualizar(() => plagas = v),
                ),
                SwitchListTile(
                  value: moho,
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Vi moho'),
                  onChanged: (v) => actualizar(() => moho = v),
                ),
                const SizedBox(height: 16),
                BotonGrande(
                  texto: 'Guardar la inspección',
                  icono: Icons.save_outlined,
                  onPressed: () => Navigator.pop(contexto, true),
                ),
              ],
            ),
          ),
        ),
      ),
    );

    if (guardar != true || !context.mounted) return;
    final alertas = await s.apoyo.inspeccionarAlmacen(
      loteId: loteId,
      humedadRelativa: leerNumero(hr),
      temperatura: leerNumero(temp),
      plagas: plagas,
      moho: moho,
      olor: olor.text.trim(),
    );
    if (!context.mounted) return;
    avisar(
      context,
      alertas.isEmpty
          ? 'Inspección guardada'
          : '${alertas.first.quePaso}. ${alertas.first.queHacer}',
      error: alertas.isNotEmpty,
    );
  }
}
