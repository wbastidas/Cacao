/// Lista de lotes, creación y escaneo de QR (RF-LOT-01, RF-LOT-04).
library;

import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../../datos/bd/base_datos.dart';
import '../../nucleo/modelo/etapas.dart';
import '../../servicios.dart';
import '../comun/widgets.dart';
import '../tema.dart';
import 'detalle_lote.dart';

class PantallaLotes extends StatelessWidget {
  const PantallaLotes({super.key});

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Lotes de cacao'),
        actions: [
          IconButton(
            tooltip: 'Escanear QR',
            icon: const Icon(Icons.qr_code_scanner),
            onPressed: () => _escanear(context),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _nuevoLote(context),
        icon: const Icon(Icons.add),
        label: const Text('Nuevo lote'),
      ),
      body: StreamBuilder<List<Lote>>(
        stream: s.lotes.observarLotes(),
        builder: (context, snapshot) {
          final lotes = snapshot.data ?? const [];
          if (lotes.isEmpty) {
            return EstadoVacio(
              icono: Icons.inventory_2_outlined,
              titulo: 'Todavía no hay lotes',
              explicacion:
                  'Un lote es el cacao que llega junto y se procesa junto. '
                  'Crea el primero cuando recibas las mazorcas.',
              accion: FilledButton.icon(
                icon: const Icon(Icons.add),
                label: const Text('Crear el primer lote'),
                onPressed: () => _nuevoLote(context),
              ),
            );
          }
          return ListView.builder(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
            itemCount: lotes.length,
            itemBuilder: (context, i) => _TarjetaLote(lote: lotes[i]),
          );
        },
      ),
    );
  }

  Future<void> _nuevoLote(BuildContext context) async {
    final s = ProveedorServicios.de(context);
    final fincas = await s.config.observarFincas().first;
    if (!context.mounted) return;

    String? fincaId = fincas.isEmpty ? null : fincas.first.id;
    DateTime fechaCosecha = DateTime.now();

    final crear = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (contexto) => StatefulBuilder(
        builder: (contexto, actualizar) => Padding(
          padding: EdgeInsets.fromLTRB(
              16, 24, 16, MediaQuery.of(contexto).viewInsets.bottom + 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Nuevo lote',
                  style: Theme.of(contexto).textTheme.titleLarge),
              const SizedBox(height: 8),
              const Text(
                'El código se genera solo. Después registras las mazorcas.',
                style: TextStyle(fontSize: 15),
              ),
              const SizedBox(height: 20),
              if (fincas.isEmpty)
                const Aviso(
                  texto: 'No has registrado ninguna finca. Puedes crear el '
                      'lote igual y asignarle la finca después, desde Ajustes.',
                  icono: Icons.info_outline,
                  color: ColoresEstado.neutro,
                )
              else
                DropdownButtonFormField<String>(
                  initialValue: fincaId,
                  decoration: const InputDecoration(labelText: 'Finca'),
                  items: [
                    for (final f in fincas)
                      DropdownMenuItem(value: f.id, child: Text(f.nombre)),
                  ],
                  onChanged: (v) => actualizar(() => fincaId = v),
                ),
              const SizedBox(height: 16),
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.event),
                title: const Text('Fecha de cosecha'),
                subtitle: Text(_fecha(fechaCosecha)),
                trailing: const Icon(Icons.edit),
                onTap: () async {
                  final elegida = await showDatePicker(
                    context: contexto,
                    initialDate: fechaCosecha,
                    firstDate: DateTime.now()
                        .subtract(const Duration(days: 60)),
                    lastDate: DateTime.now(),
                  );
                  if (elegida != null) {
                    actualizar(() => fechaCosecha = elegida);
                  }
                },
              ),
              const SizedBox(height: 20),
              BotonGrande(
                texto: 'Crear el lote',
                icono: Icons.check,
                onPressed: () => Navigator.pop(contexto, true),
              ),
            ],
          ),
        ),
      ),
    );

    if (crear != true || !context.mounted) return;
    final lote = await s.lotes.crearLote(
      fincaId: fincaId,
      fechaCosecha: fechaCosecha,
    );
    if (!context.mounted) return;
    avisar(context, 'Lote ${lote.codigo} creado');
    await Navigator.push(
      context,
      MaterialPageRoute(
          builder: (_) => PantallaDetalleLote(loteId: lote.id)),
    );
  }

  Future<void> _escanear(BuildContext context) async {
    final codigo = await Navigator.push<String>(
      context,
      MaterialPageRoute(builder: (_) => const PantallaEscaner()),
    );
    if (codigo == null || !context.mounted) return;

    final s = ProveedorServicios.de(context);
    // Un QR puede ser de lote (L-...), de saco (L-...-Sxx) o de tanda (P-...).
    final codigoLote = codigo.split('-S').first;
    final lote = await s.lotes.porCodigo(codigoLote);
    if (!context.mounted) return;

    if (lote == null) {
      avisar(context, 'No se encontró ningún lote con el código $codigo',
          error: true);
      return;
    }
    await Navigator.push(
      context,
      MaterialPageRoute(
          builder: (_) => PantallaDetalleLote(loteId: lote.id)),
    );
  }
}

class _TarjetaLote extends StatelessWidget {
  const _TarjetaLote({required this.lote});
  final Lote lote;

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);

    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: () => Navigator.push(
          context,
          MaterialPageRoute(
              builder: (_) => PantallaDetalleLote(loteId: lote.id)),
        ),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(lote.codigo,
                        style: const TextStyle(
                            fontSize: 20, fontWeight: FontWeight.w700)),
                  ),
                  if (lote.ventaBloqueada)
                    const Chip(
                      avatar: Icon(Icons.block,
                          size: 18, color: ColoresEstado.problema),
                      label: Text('Venta bloqueada'),
                      visualDensity: VisualDensity.compact,
                    ),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Icon(Icons.timeline,
                      size: 18,
                      color: Theme.of(context).colorScheme.onSurfaceVariant),
                  const SizedBox(width: 6),
                  Text(lote.estado.etiqueta,
                      style: const TextStyle(
                          fontSize: 16, fontWeight: FontWeight.w600)),
                ],
              ),
              const SizedBox(height: 4),
              Text(lote.estado.queSigue,
                  style: TextStyle(
                      fontSize: 15,
                      color: Theme.of(context).colorScheme.onSurfaceVariant)),
              const SizedBox(height: 8),
              Row(
                children: [
                  Text('Llegó el ${_fecha(lote.fechaLlegada)}',
                      style: const TextStyle(fontSize: 14)),
                  const Spacer(),
                  StreamBuilder(
                    stream: s.alertas.observarAbiertas(loteId: lote.id),
                    builder: (context, snapshot) {
                      final n = snapshot.data?.length ?? 0;
                      if (n == 0) return const SizedBox.shrink();
                      return Chip(
                        avatar: const Icon(Icons.warning_amber_rounded,
                            size: 18, color: ColoresEstado.atencion),
                        label: Text('$n'),
                        visualDensity: VisualDensity.compact,
                      );
                    },
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Escáner de QR con guía visual (RF-LOT-04).
class PantallaEscaner extends StatelessWidget {
  const PantallaEscaner({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Escanear QR')),
      body: Stack(
        children: [
          MobileScanner(
            onDetect: (captura) {
              final codigo = captura.barcodes.firstOrNull?.rawValue;
              if (codigo != null && codigo.isNotEmpty) {
                Navigator.pop(context, codigo);
              }
            },
          ),
          Align(
            alignment: Alignment.bottomCenter,
            child: Container(
              width: double.infinity,
              color: Colors.black54,
              padding: const EdgeInsets.all(24),
              child: const Text(
                'Apunta al código QR del saco o de la barra.\n'
                'Se abrirá la historia completa del lote.',
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.white, fontSize: 16),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

String _fecha(DateTime f) =>
    '${f.day.toString().padLeft(2, '0')}/'
    '${f.month.toString().padLeft(2, '0')}/${f.year}';
