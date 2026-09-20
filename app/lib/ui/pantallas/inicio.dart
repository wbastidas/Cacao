/// Pantalla de inicio: "¿Qué hago hoy?" (RF-TAB-01).
///
/// Es la pantalla que el usuario abre veinte veces al día. Tiene que
/// responder de un vistazo a tres preguntas: qué me toca hacer, qué está
/// fallando y si mis datos están a salvo.
library;

import 'package:flutter/material.dart';

import '../../datos/repositorios/repositorio_apoyo.dart';
import '../../datos/sync/sincronizador_remoto.dart';
import '../../nucleo/reglas/alerta.dart';
import '../../servicios.dart';
import '../comun/widgets.dart';
import '../tema.dart';
import 'alertas.dart';
import 'detalle_lote.dart';

class PantallaInicio extends StatefulWidget {
  const PantallaInicio({super.key});

  @override
  State<PantallaInicio> createState() => _PantallaInicioState();
}

class _PantallaInicioState extends State<PantallaInicio> {
  Future<List<TareaDelDia>>? _tareas;
  Future<EstadoSincronizacion>? _estadoSync;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _recargar();
  }

  void _recargar() {
    final s = ProveedorServicios.de(context);
    setState(() {
      _tareas = () async {
        // Al abrir, se revisan los reposos vencidos: así el aviso de "toca
        // abrir las mazorcas" aparece aunque el teléfono estuviera apagado.
        await s.lotes.revisarReposos();
        await s.apoyo.cerrarRegistrosBpmVencidos();
        return s.apoyo.tareasDeHoy();
      }();
      _estadoSync = s.sync.estadoActual();
    });
  }

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('¿Qué hago hoy?'),
        actions: [
          IconButton(
            onPressed: _recargar,
            icon: const Icon(Icons.refresh),
            tooltip: 'Actualizar',
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async => _recargar(),
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
          children: [
            _IndicadorSync(futuro: _estadoSync, alSincronizar: () async {
              await s.sync.sincronizar();
              _recargar();
            }),
            const SizedBox(height: 8),
            _Alertas(alCambiar: _recargar),
            const SizedBox(height: 8),
            Text('Tareas de hoy',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 8),
            _Tareas(futuro: _tareas, alCambiar: _recargar),
          ],
        ),
      ),
    );
  }
}

/// RF-SYN-06: "Todo sincronizado" / "N pendientes" / "Sin conexión".
class _IndicadorSync extends StatelessWidget {
  const _IndicadorSync({required this.futuro, required this.alSincronizar});

  final Future<EstadoSincronizacion>? futuro;
  final Future<void> Function() alSincronizar;

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<EstadoSincronizacion>(
      future: futuro,
      builder: (context, snapshot) {
        final estado = snapshot.data;
        if (estado == null) return const SizedBox(height: 8);

        final (color, icono) = switch (estado) {
          _ when !estado.hayConexion => (ColoresEstado.neutro, Icons.cloud_off),
          _ when estado.todoAlDia => (ColoresEstado.bien, Icons.cloud_done),
          _ => (ColoresEstado.atencion, Icons.cloud_upload),
        };

        return Card(
          child: ListTile(
            leading: Icon(icono, color: color, size: 30),
            title: Text(estado.etiqueta,
                style: const TextStyle(fontWeight: FontWeight.w600)),
            subtitle: Text(
              estado.hayConexion
                  ? (estado.todoAlDia
                      ? 'Tus registros están respaldados'
                      : 'Se subirán en cuanto se pueda')
                  : 'Puedes seguir trabajando; se subirá al volver la señal',
            ),
            trailing: estado.pendientes > 0 && estado.hayConexion
                ? TextButton(
                    onPressed: alSincronizar,
                    child: const Text('Subir ahora'),
                  )
                : null,
          ),
        );
      },
    );
  }
}

class _Alertas extends StatelessWidget {
  const _Alertas({required this.alCambiar});
  final VoidCallback alCambiar;

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);
    return StreamBuilder(
      stream: s.alertas.observarAbiertas(),
      builder: (context, snapshot) {
        final alertas = snapshot.data ?? const [];
        if (alertas.isEmpty) return const SizedBox.shrink();

        final bloqueantes = alertas
            .where((a) => a.severidad == Severidad.bloqueante.index)
            .length;
        final urgentes = alertas
            .where((a) => a.severidad == Severidad.urgente.index)
            .length;

        final color = bloqueantes > 0
            ? ColoresEstado.problema
            : urgentes > 0
                ? ColoresEstado.atencion
                : ColoresEstado.neutro;

        return Aviso(
          color: color,
          icono: Icons.notifications_active,
          titulo: alertas.length == 1
              ? '1 alerta abierta'
              : '${alertas.length} alertas abiertas',
          texto: alertas.first.quePaso,
          accion: FilledButton(
            style: FilledButton.styleFrom(backgroundColor: color),
            onPressed: () async {
              await Navigator.push(
                context,
                MaterialPageRoute(
                    builder: (_) => const PantallaAlertas()),
              );
              alCambiar();
            },
            child: const Text('Ver las alertas'),
          ),
        );
      },
    );
  }
}

class _Tareas extends StatelessWidget {
  const _Tareas({required this.futuro, required this.alCambiar});

  final Future<List<TareaDelDia>>? futuro;
  final VoidCallback alCambiar;

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<List<TareaDelDia>>(
      future: futuro,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Padding(
            padding: EdgeInsets.all(32),
            child: Center(child: CircularProgressIndicator()),
          );
        }
        final tareas = snapshot.data ?? const [];
        if (tareas.isEmpty) {
          return const Card(
            child: Padding(
              padding: EdgeInsets.all(24),
              child: Column(
                children: [
                  Icon(Icons.check_circle_outline,
                      size: 48, color: ColoresEstado.bien),
                  SizedBox(height: 12),
                  Text('Nada pendiente por ahora',
                      style: TextStyle(
                          fontSize: 18, fontWeight: FontWeight.w600)),
                  SizedBox(height: 4),
                  Text(
                    'Cuando haya un lote en fermentación o en secado, aquí '
                    'aparecerá lo que toca hacer cada día.',
                    textAlign: TextAlign.center,
                    style: TextStyle(fontSize: 15),
                  ),
                ],
              ),
            ),
          );
        }

        return Column(
          children: [
            for (final t in tareas)
              Card(
                color: t.urgente
                    // ignore: deprecated_member_use
                    ? ColoresEstado.atencion.withOpacity(0.10)
                    : null,
                child: ListTile(
                  contentPadding:
                      const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  leading: Icon(
                    t.urgente
                        ? Icons.priority_high
                        : Icons.radio_button_unchecked,
                    color: t.urgente ? ColoresEstado.atencion : null,
                    size: 28,
                  ),
                  title: Text(t.titulo),
                  subtitle: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(t.detalle),
                      const SizedBox(height: 4),
                      Chip(
                        label: Text(t.loteCodigo),
                        visualDensity: VisualDensity.compact,
                        padding: EdgeInsets.zero,
                      ),
                    ],
                  ),
                  trailing: t.loteId == null
                      ? null
                      : const Icon(Icons.chevron_right),
                  onTap: t.loteId == null
                      ? null
                      : () async {
                          await Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) =>
                                  PantallaDetalleLote(loteId: t.loteId!),
                            ),
                          );
                          alCambiar();
                        },
                ),
              ),
          ],
        );
      },
    );
  }
}
