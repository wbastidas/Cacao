/// Inventario, costos y ventas (RF-INV, RF-COS, RF-VEN, RF-REP-01).
library;

import 'dart:io';

import 'package:csv/csv.dart';
import 'package:flutter/material.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

import '../../datos/bd/base_datos.dart';
import '../../datos/repositorios/repositorio_apoyo.dart';
import '../../servicios.dart';
import '../comun/camara_ia.dart';
import '../comun/widgets.dart';
import '../tema.dart';

class PantallaInventario extends StatefulWidget {
  const PantallaInventario({super.key});

  @override
  State<PantallaInventario> createState() => _PantallaInventarioState();
}

class _PantallaInventarioState extends State<PantallaInventario> {
  Future<List<Existencia>>? _existencias;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _recargar();
  }

  void _recargar() {
    final s = ProveedorServicios.de(context);
    setState(() {
      _existencias = s.apoyo.existencias();
    });
  }

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 3,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Inventario y dinero'),
          bottom: const TabBar(tabs: [
            Tab(text: 'Existencias'),
            Tab(text: 'Costos'),
            Tab(text: 'Ventas'),
          ]),
          actions: [
            IconButton(
              tooltip: 'Exportar a CSV',
              icon: const Icon(Icons.ios_share),
              onPressed: _exportar,
            ),
          ],
        ),
        body: TabBarView(
          children: [
            _Existencias(futuro: _existencias, alCambiar: _recargar),
            const _Costos(),
            const _Ventas(),
          ],
        ),
      ),
    );
  }

  /// RF-REP-01: exportar a CSV para abrirlo en Sheets o Looker Studio.
  Future<void> _exportar() async {
    final s = ProveedorServicios.de(context);
    final existencias = await s.apoyo.existencias();
    final filas = <List<dynamic>>[
      ['item', 'cantidad', 'unidad', 'minimo', 'bajo_minimo'],
      for (final e in existencias)
        [e.item, e.cantidad, e.unidad, e.minimo ?? '', e.bajoMinimo],
    ];
    final csv = const ListToCsvConverter().convert(filas);
    final directorio = await getTemporaryDirectory();
    final archivo = File('${directorio.path}/inventario_cacaotrace.csv');
    await archivo.writeAsString(csv);
    if (!mounted) return;
    await Share.shareXFiles(
      [XFile(archivo.path)],
      text: 'Inventario de CacaoTrace',
    );
  }
}

class _Existencias extends StatelessWidget {
  const _Existencias({required this.futuro, required this.alCambiar});
  final Future<List<Existencia>>? futuro;
  final VoidCallback alCambiar;

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);

    return FutureBuilder<List<Existencia>>(
      future: futuro,
      builder: (context, snapshot) {
        final existencias = snapshot.data ?? const [];
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            const Text(
              'El inventario se calcula sumando los movimientos, no editando '
              'un total. Así siempre se puede explicar de dónde salió cada kg.',
              style: TextStyle(fontSize: 15),
            ),
            const SizedBox(height: 16),
            for (final e in existencias)
              Card(
                child: ListTile(
                  leading: Icon(
                    e.bajoMinimo
                        ? Icons.warning_amber_rounded
                        : Icons.inventory_2_outlined,
                    color: e.bajoMinimo ? ColoresEstado.atencion : null,
                  ),
                  title: Text(nombreBonito(e.item)),
                  subtitle: e.minimo == null
                      ? null
                      : Text('Mínimo ${e.minimo} ${e.unidad}'),
                  trailing: Text(
                    '${e.cantidad.toStringAsFixed(2)} ${e.unidad}',
                    style: TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.w700,
                      color: e.bajoMinimo ? ColoresEstado.atencion : null,
                    ),
                  ),
                ),
              ),
            const SizedBox(height: 16),
            BotonGrande(
              texto: 'Registrar un movimiento',
              subtitulo: 'Compra de azúcar, empaques…',
              icono: Icons.add,
              onPressed: () async {
                final item = TextEditingController();
                final cantidad = TextEditingController();
                var tipo = 'entrada';

                final guardar = await showModalBottomSheet<bool>(
                  context: context,
                  isScrollControlled: true,
                  builder: (contexto) => StatefulBuilder(
                    builder: (contexto, actualizar) => Padding(
                      padding: EdgeInsets.fromLTRB(16, 24, 16,
                          MediaQuery.of(contexto).viewInsets.bottom + 24),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('Movimiento de inventario',
                              style:
                                  Theme.of(contexto).textTheme.titleLarge),
                          const SizedBox(height: 12),
                          SegmentedButton<String>(
                            segments: const [
                              ButtonSegment(
                                  value: 'entrada', label: Text('Entrada')),
                              ButtonSegment(
                                  value: 'salida', label: Text('Salida')),
                            ],
                            selected: {tipo},
                            onSelectionChanged: (v) =>
                                actualizar(() => tipo = v.first),
                          ),
                          const SizedBox(height: 12),
                          TextFormField(
                            controller: item,
                            decoration: const InputDecoration(
                              labelText: 'Artículo',
                              hintText: 'azucar, manteca, empaques…',
                            ),
                          ),
                          CampoNumero(
                            etiqueta: 'Cantidad',
                            controlador: cantidad,
                            unidad: 'kg',
                            obligatorio: true,
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
                );

                if (guardar != true || !context.mounted) return;
                final n = leerNumero(cantidad);
                if (n == null || item.text.trim().isEmpty) return;
                await s.apoyo.registrarMovimiento(
                  item: item.text.trim().toLowerCase(),
                  tipo: tipo,
                  cantidad: n,
                );
                alCambiar();
              },
            ),
          ],
        );
      },
    );
  }
}

class _Costos extends StatelessWidget {
  const _Costos();

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);

    return StreamBuilder<List<LoteProduccion>>(
      stream: s.produccion.observar(),
      builder: (context, snapshot) {
        final tandas = snapshot.data ?? const [];
        if (tandas.isEmpty) {
          return const EstadoVacio(
            icono: Icons.attach_money,
            titulo: 'Sin tandas todavía',
            explicacion:
                'Cuando tengas una tanda de chocolate podrás ver cuánto te '
                'costó cada barra.',
          );
        }
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            for (final t in tandas)
              FutureBuilder<ResumenCostos>(
                future: s.apoyo.costosDeProduccion(t.id),
                builder: (context, snap) {
                  final r = snap.data;
                  return TarjetaSeccion(
                    titulo: t.codigo,
                    icono: Icons.receipt_long_outlined,
                    accion: TextButton(
                      onPressed: () => _anadirCosto(context, t.id),
                      child: const Text('Añadir'),
                    ),
                    hijos: [
                      if (r == null)
                        const Text('Calculando…')
                      else ...[
                        for (final e in r.porConcepto.entries)
                          FilaDato(nombreBonito(e.key),
                              '\$ ${e.value.toStringAsFixed(2)}'),
                        const Divider(),
                        FilaDato('Total',
                            '\$ ${r.totalUsd.toStringAsFixed(2)}'),
                        if (r.costoPorBarra != null)
                          FilaDato('Costo por barra',
                              '\$ ${r.costoPorBarra!.toStringAsFixed(2)}'),
                        if (r.costoPorKgGrano != null)
                          FilaDato('Costo por kg de grano',
                              '\$ ${r.costoPorKgGrano!.toStringAsFixed(2)}'),
                        FutureBuilder<double>(
                          future: s.apoyo.margenDeProduccion(t.id),
                          builder: (context, snapMargen) {
                            final margen = snapMargen.data;
                            if (margen == null) return const SizedBox.shrink();
                            return FilaDato(
                              'Margen',
                              '\$ ${margen.toStringAsFixed(2)}',
                              color: margen >= 0
                                  ? ColoresEstado.bien
                                  : ColoresEstado.problema,
                            );
                          },
                        ),
                      ],
                    ],
                  );
                },
              ),
          ],
        );
      },
    );
  }

  Future<void> _anadirCosto(BuildContext context, String tandaId) async {
    final s = ProveedorServicios.de(context);
    final monto = TextEditingController();
    var concepto = 'insumos';

    const conceptos = [
      'mazorcas',
      'transporte',
      'insumos',
      'energia',
      'empaques',
      'tramites',
    ];

    final guardar = await showModalBottomSheet<bool>(
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
              Text('Nuevo costo',
                  style: Theme.of(contexto).textTheme.titleLarge),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                children: [
                  for (final c in conceptos)
                    ChoiceChip(
                      label: Text(nombreBonito(c)),
                      selected: concepto == c,
                      onSelected: (_) => actualizar(() => concepto = c),
                    ),
                ],
              ),
              CampoNumero(
                etiqueta: 'Monto',
                controlador: monto,
                unidad: 'USD',
                obligatorio: true,
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
    );

    if (guardar != true || !context.mounted) return;
    final n = leerNumero(monto);
    if (n == null) return;
    await s.apoyo.registrarCosto(
      loteProduccionId: tandaId,
      concepto: concepto,
      montoUsd: n,
    );
    if (context.mounted) avisar(context, 'Costo registrado');
  }
}

class _Ventas extends StatelessWidget {
  const _Ventas();

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);

    return StreamBuilder<List<LoteProduccion>>(
      stream: s.produccion.observar(),
      builder: (context, snapshot) {
        final tandas =
            (snapshot.data ?? const []).where((t) => t.kgChocolate != null);
        if (tandas.isEmpty) {
          return const EstadoVacio(
            icono: Icons.storefront_outlined,
            titulo: 'Nada que vender todavía',
            explicacion:
                'Cuando empaques una tanda podrás registrar las ventas y ver '
                'el margen.',
          );
        }
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            for (final t in tandas)
              Card(
                child: ListTile(
                  title: Text(t.codigo),
                  subtitle: Text('${t.kgChocolate} kg de chocolate'),
                  trailing: FilledButton(
                    onPressed: () => _vender(context, t.id),
                    child: const Text('Vender'),
                  ),
                ),
              ),
          ],
        );
      },
    );
  }

  Future<void> _vender(BuildContext context, String tandaId) async {
    final s = ProveedorServicios.de(context);
    final barras = TextEditingController();
    final precio = TextEditingController();
    final cliente = TextEditingController();
    var consumoPropio = false;

    final guardar = await showModalBottomSheet<bool>(
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
              Text('Registrar venta',
                  style: Theme.of(contexto).textTheme.titleLarge),
              TextFormField(
                controller: cliente,
                decoration: const InputDecoration(labelText: 'Cliente'),
              ),
              CampoNumero(
                  etiqueta: 'Barras',
                  controlador: barras,
                  decimales: false,
                  obligatorio: true),
              CampoNumero(
                  etiqueta: 'Precio por barra',
                  controlador: precio,
                  unidad: 'USD'),
              SwitchListTile(
                value: consumoPropio,
                contentPadding: EdgeInsets.zero,
                title: const Text('Consumo propio o regalo'),
                subtitle: const Text('No entra en el margen',
                    style: TextStyle(fontSize: 14)),
                onChanged: (v) => actualizar(() => consumoPropio = v),
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
    );

    if (guardar != true || !context.mounted) return;
    final n = leerNumero(barras)?.round();
    if (n == null) return;
    await s.apoyo.registrarVenta(
      loteProduccionId: tandaId,
      barras: n,
      cliente: cliente.text.trim(),
      precioUnitarioUsd: leerNumero(precio) ?? 0,
      consumoPropio: consumoPropio,
    );
    if (context.mounted) avisar(context, 'Venta registrada');
  }
}
