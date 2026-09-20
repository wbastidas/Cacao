/// Detalle del lote: línea de tiempo, balance de masa y lo que toca hacer
/// en la etapa actual (RF-LOT-06, RF-LOT-08).
library;

import 'package:flutter/material.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../datos/repositorios/repositorio_lotes.dart';
import '../../nucleo/calculo/balance_masa.dart';
import '../../nucleo/modelo/etapas.dart';
import '../../servicios.dart';
import '../comun/widgets.dart';
import '../tema.dart';
import 'alertas.dart';
import 'almacen.dart';
import 'apertura.dart';
import 'fermentacion.dart';
import 'prueba_corte.dart';
import 'recepcion.dart';
import 'reporte.dart';
import 'secado.dart';

class PantallaDetalleLote extends StatefulWidget {
  const PantallaDetalleLote({super.key, required this.loteId});
  final String loteId;

  @override
  State<PantallaDetalleLote> createState() => _PantallaDetalleLoteState();
}

class _PantallaDetalleLoteState extends State<PantallaDetalleLote> {
  Future<LoteCompleto?>? _lote;
  Future<List<HechoLote>>? _historia;
  Future<List<PasoBalance>>? _balance;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _recargar();
  }

  void _recargar() {
    final s = ProveedorServicios.de(context);
    setState(() {
      _lote = s.lotes.completo(widget.loteId);
      _historia = s.lotes.lineaDeTiempo(widget.loteId);
      _balance = s.lotes.balance(widget.loteId);
    });
  }

  Future<void> _abrir(Widget pantalla) async {
    await Navigator.push(
        context, MaterialPageRoute(builder: (_) => pantalla));
    _recargar();
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<LoteCompleto?>(
      future: _lote,
      builder: (context, snapshot) {
        final c = snapshot.data;
        if (c == null) {
          return const Scaffold(
            body: Center(child: CircularProgressIndicator()),
          );
        }

        return Scaffold(
          appBar: AppBar(
            title: Text(c.lote.codigo),
            actions: [
              IconButton(
                tooltip: 'Código QR',
                icon: const Icon(Icons.qr_code_2),
                onPressed: () => _mostrarQr(context, c.lote.codigo),
              ),
              IconButton(
                tooltip: 'Reporte de trazabilidad',
                icon: const Icon(Icons.picture_as_pdf_outlined),
                onPressed: () =>
                    _abrir(PantallaReporte(loteId: widget.loteId)),
              ),
            ],
          ),
          body: DefaultTabController(
            length: 3,
            child: Column(
              children: [
                const TabBar(tabs: [
                  Tab(text: 'Qué sigue'),
                  Tab(text: 'Historia'),
                  Tab(text: 'Balance'),
                ]),
                Expanded(
                  child: TabBarView(
                    children: [
                      _QueSigue(completo: c, abrir: _abrir, recargar: _recargar),
                      _Historia(futuro: _historia),
                      _Balance(futuro: _balance),
                    ],
                  ),
                ),
              ],
            ),
          ),
        );
      },
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
              data: codigo,
              size: 220,
              backgroundColor: Colors.white,
            ),
            const SizedBox(height: 16),
            const Text(
              'Imprime este código y pégalo en el saco. Al escanearlo se abre '
              'la historia completa del lote.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 15),
            ),
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
}

class _QueSigue extends StatelessWidget {
  const _QueSigue({
    required this.completo,
    required this.abrir,
    required this.recargar,
  });

  final LoteCompleto completo;
  final Future<void> Function(Widget) abrir;
  final VoidCallback recargar;

  @override
  Widget build(BuildContext context) {
    final lote = completo.lote;
    final loteId = lote.id;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        if (lote.ventaBloqueada)
          Aviso(
            color: ColoresEstado.problema,
            icono: Icons.block,
            titulo: 'Venta bloqueada',
            texto: lote.motivoBloqueo.isEmpty
                ? 'Este lote no se puede vender hasta resolverlo.'
                : lote.motivoBloqueo,
          ),
        _CabeceraEstado(completo: completo),
        const SizedBox(height: 8),
        if (completo.alertasAbiertas > 0)
          Aviso(
            texto: completo.alertasAbiertas == 1
                ? 'Hay 1 alerta abierta en este lote'
                : 'Hay ${completo.alertasAbiertas} alertas abiertas en '
                    'este lote',
            accion: FilledButton(
              onPressed: () => abrir(PantallaAlertas(loteId: loteId)),
              child: const Text('Ver'),
            ),
          ),
        const SizedBox(height: 8),

        // Acciones de la etapa actual, primero la que toca.
        if (completo.recepcion == null)
          BotonGrande(
            texto: 'Registrar la recepción',
            subtitulo: 'Mazorcas, peso y días de reposo',
            icono: Icons.local_shipping_outlined,
            onPressed: () => abrir(PantallaRecepcion(loteId: loteId)),
          )
        else ...[
          BotonGrande(
            texto: completo.apertura == null
                ? 'Abrir las mazorcas'
                : 'Ver la apertura',
            subtitulo: completo.apertura == null
                ? 'Registrar kg de baba'
                : '${completo.apertura!.kgBaba} kg de baba',
            icono: Icons.egg_outlined,
            onPressed: () => abrir(PantallaApertura(loteId: loteId)),
          ),
          const SizedBox(height: 12),
          BotonGrande(
            texto: completo.fermentacion == null
                ? 'Empezar la fermentación'
                : 'Fermentación',
            subtitulo: completo.fermentacion == null
                ? 'Elegir fermentador y masa'
                : 'Temperatura, volteos y olor',
            icono: Icons.thermostat,
            onPressed: () => abrir(PantallaFermentacion(loteId: loteId)),
          ),
          const SizedBox(height: 12),
          BotonGrande(
            texto: completo.secado == null ? 'Empezar el secado' : 'Secado',
            subtitulo: completo.secado?.fin != null
                ? '${completo.secado!.kgSeco ?? 0} kg secos'
                : 'Humedad, remociones y moho',
            icono: Icons.wb_sunny_outlined,
            onPressed: () => abrir(PantallaSecado(loteId: loteId)),
          ),
          const SizedBox(height: 12),
          BotonGrande(
            texto: 'Prueba de corte',
            subtitulo: completo.pruebaCorteFinal?.resultado ??
                'Cortar 100 granos y calificar',
            icono: Icons.grid_on,
            onPressed: () => abrir(PantallaPruebaCorte(loteId: loteId)),
          ),
          const SizedBox(height: 12),
          BotonGrande(
            texto: 'Almacenamiento',
            subtitulo: 'Sacos, QR e inspecciones',
            icono: Icons.warehouse_outlined,
            onPressed: () => abrir(PantallaAlmacen(loteId: loteId)),
          ),
        ],
        const SizedBox(height: 24),
        OutlinedButton.icon(
          icon: const Icon(Icons.notifications_outlined),
          label: const Text('Alertas y correcciones de este lote'),
          onPressed: () => abrir(PantallaAlertas(loteId: loteId)),
        ),
      ],
    );
  }
}

class _CabeceraEstado extends StatelessWidget {
  const _CabeceraEstado({required this.completo});
  final LoteCompleto completo;

  @override
  Widget build(BuildContext context) {
    final c = completo;
    final prueba = c.pruebaCorteFinal;

    return TarjetaSeccion(
      titulo: c.lote.estado.etiqueta,
      icono: Icons.timeline,
      hijos: [
        Text(c.lote.estado.queSigue, style: const TextStyle(fontSize: 16)),
        const Divider(height: 24),
        if (c.finca != null) FilaDato('Finca', c.finca!.nombre),
        FilaDato('Variedad', c.lote.variedad),
        if (c.recepcion != null)
          FilaDato('Mazorcas', '${c.recepcion!.mazorcasTotal}'),
        if (c.apertura != null)
          FilaDato('Baba', '${c.apertura!.kgBaba} kg'),
        if (c.secado?.kgSeco != null)
          FilaDato('Grano seco', '${c.secado!.kgSeco} kg'),
        if (prueba != null)
          FilaDato(
            'Prueba de corte',
            prueba.resultado,
            color: prueba.conforme
                ? ColoresEstado.bien
                : ColoresEstado.atencion,
          ),
      ],
    );
  }
}

class _Historia extends StatelessWidget {
  const _Historia({required this.futuro});
  final Future<List<HechoLote>>? futuro;

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<HechoLote>>(
      future: futuro,
      builder: (context, snapshot) {
        final hechos = snapshot.data ?? const [];
        if (hechos.isEmpty) {
          return const EstadoVacio(
            icono: Icons.history,
            titulo: 'Todavía no hay historia',
            explicacion: 'Aquí se irá anotando todo lo que le pase al lote: '
                'lecturas, volteos, fotos y alertas.',
          );
        }
        return ListView.builder(
          padding: const EdgeInsets.all(16),
          itemCount: hechos.length,
          itemBuilder: (context, i) {
            final h = hechos[i];
            return Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Column(
                  children: [
                    Container(
                      width: 14,
                      height: 14,
                      margin: const EdgeInsets.only(top: 6),
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: h.esAlerta
                            ? ColoresEstado.atencion
                            : Theme.of(context).colorScheme.primary,
                      ),
                    ),
                    if (i < hechos.length - 1)
                      Container(
                        width: 2,
                        height: 52,
                        color: Theme.of(context).colorScheme.outlineVariant,
                      ),
                  ],
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.only(bottom: 16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '${_fechaHora(h.fecha)} · ${h.etapa}',
                          style: TextStyle(
                            fontSize: 13,
                            color: Theme.of(context)
                                .colorScheme
                                .onSurfaceVariant,
                          ),
                        ),
                        Text(h.titulo,
                            style: const TextStyle(
                                fontSize: 17, fontWeight: FontWeight.w600)),
                        if (h.detalle.isNotEmpty)
                          Text(h.detalle,
                              style: const TextStyle(fontSize: 15)),
                      ],
                    ),
                  ),
                ),
              ],
            );
          },
        );
      },
    );
  }
}

class _Balance extends StatelessWidget {
  const _Balance({required this.futuro});
  final Future<List<PasoBalance>>? futuro;

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<PasoBalance>>(
      future: futuro,
      builder: (context, snapshot) {
        final pasos = snapshot.data ?? const [];
        if (pasos.isEmpty) {
          return const EstadoVacio(
            icono: Icons.balance,
            titulo: 'Sin datos para el balance',
            explicacion: 'Registra la recepción para empezar a ver cuánto '
                'debería rendir el lote en cada etapa.',
          );
        }
        return ListView(
          padding: const EdgeInsets.all(16),
          children: [
            const Text(
              'Compara lo que obtuviste con lo que era esperable. Una '
              'diferencia grande casi siempre es un error al anotar un peso.',
              style: TextStyle(fontSize: 15),
            ),
            const SizedBox(height: 16),
            for (final p in pasos)
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(p.etapa,
                          style: const TextStyle(
                              fontSize: 17, fontWeight: FontWeight.w600)),
                      const SizedBox(height: 8),
                      FilaDato('Entró',
                          '${p.entrada.toStringAsFixed(1)} ${p.unidadEntrada}'),
                      FilaDato(
                        'Esperado',
                        '${p.salidaEsperada.toStringAsFixed(1)} '
                            '${p.unidadSalida}',
                      ),
                      if (p.registrado)
                        FilaDato(
                          'Obtenido',
                          '${p.salidaReal!.toStringAsFixed(1)} '
                              '${p.unidadSalida}',
                          color: p.desvioPct!.abs() > 20
                              ? ColoresEstado.atencion
                              : ColoresEstado.bien,
                        )
                      else
                        const FilaDato('Obtenido', 'sin registrar',
                            color: ColoresEstado.neutro),
                      if (p.registrado)
                        Text(
                          p.desvioPct! >= 0
                              ? '${p.desvioPct!.toStringAsFixed(0)} % por '
                                  'encima de lo esperado'
                              : '${p.desvioPct!.abs().toStringAsFixed(0)} % '
                                  'por debajo de lo esperado',
                          style: const TextStyle(fontSize: 14),
                        ),
                    ],
                  ),
                ),
              ),
          ],
        );
      },
    );
  }
}

String _fechaHora(DateTime f) =>
    '${f.day.toString().padLeft(2, '0')}/'
    '${f.month.toString().padLeft(2, '0')} '
    '${f.hour.toString().padLeft(2, '0')}:'
    '${f.minute.toString().padLeft(2, '0')}';
