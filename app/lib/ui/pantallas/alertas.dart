/// Alertas abiertas y biblioteca de correcciones (RF-ALE-03, RF-COR-01/02).
///
/// Cada alerta dice qué pasó, por qué importa y qué hacer. La corrección no
/// es un texto suelto: se puede marcar como aplicada y anotar qué resultó,
/// que es lo que permite aprender de un lote al siguiente.
library;

import 'package:flutter/material.dart';

import '../../datos/bd/base_datos.dart';
import '../../nucleo/reglas/correcciones.dart';
import '../../servicios.dart';
import '../comun/widgets.dart';
import '../tema.dart';

Color colorDeSeveridad(int severidad) => switch (severidad) {
      2 => ColoresEstado.problema,
      1 => ColoresEstado.atencion,
      _ => ColoresEstado.neutro,
    };

IconData iconoDeSeveridad(int severidad) => switch (severidad) {
      2 => Icons.block,
      1 => Icons.warning_amber_rounded,
      _ => Icons.info_outline,
    };

class PantallaAlertas extends StatelessWidget {
  const PantallaAlertas({super.key, this.loteId});

  final String? loteId;

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Alertas'),
        actions: [
          IconButton(
            tooltip: 'Biblioteca de correcciones',
            icon: const Icon(Icons.menu_book_outlined),
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(
                  builder: (_) => const PantallaCorrecciones()),
            ),
          ),
        ],
      ),
      body: StreamBuilder<List<AlertaGuardada>>(
        stream: s.alertas.observarAbiertas(loteId: loteId),
        builder: (context, snapshot) {
          final alertas = snapshot.data ?? const [];
          if (alertas.isEmpty) {
            return EstadoVacio(
              icono: Icons.check_circle_outline,
              titulo: 'No hay alertas abiertas',
              explicacion:
                  'Cuando una lectura se salga de los umbrales, aparecerá '
                  'aquí con la corrección que toca.',
              accion: OutlinedButton.icon(
                icon: const Icon(Icons.menu_book_outlined),
                label: const Text('Ver las correcciones'),
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute(
                      builder: (_) => const PantallaCorrecciones()),
                ),
              ),
            );
          }

          return ListView.builder(
            padding: const EdgeInsets.all(16),
            itemCount: alertas.length,
            itemBuilder: (context, i) => _TarjetaAlerta(alerta: alertas[i]),
          );
        },
      ),
    );
  }
}

class _TarjetaAlerta extends StatelessWidget {
  const _TarjetaAlerta({required this.alerta});
  final AlertaGuardada alerta;

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);
    final color = colorDeSeveridad(alerta.severidad);
    final correccion = Correcciones.buscar(
        alerta.correccionCodigo.isEmpty ? null : alerta.correccionCodigo);

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(iconoDeSeveridad(alerta.severidad), color: color, size: 28),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(alerta.quePaso,
                          style: const TextStyle(
                              fontSize: 18, fontWeight: FontWeight.w700)),
                      const SizedBox(height: 2),
                      Text(
                        '${alerta.regla} · '
                        '${_haceCuanto(alerta.fecha)}',
                        style: TextStyle(fontSize: 13, color: color),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            _Bloque('Por qué importa', alerta.porQueImporta),
            _Bloque('Qué hacer', alerta.queHacer),
            if (alerta.valorMedido != null && alerta.valorEsperado != null)
              Padding(
                padding: const EdgeInsets.only(top: 8),
                child: Text(
                  'Medido ${alerta.valorMedido!.toStringAsFixed(1)} · '
                  'esperado ${alerta.valorEsperado!.toStringAsFixed(1)}',
                  style: const TextStyle(fontSize: 15),
                ),
              ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                if (correccion != null)
                  OutlinedButton.icon(
                    icon: const Icon(Icons.menu_book_outlined),
                    label: Text('Ver ${correccion.codigo}'),
                    onPressed: () => Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) =>
                            PantallaDetalleCorreccion(correccion: correccion),
                      ),
                    ),
                  ),
                FilledButton.icon(
                  icon: const Icon(Icons.check),
                  label: const Text('Ya lo resolví'),
                  onPressed: () => _atender(context, s, correccion?.codigo),
                ),
                TextButton(
                  onPressed: () async {
                    if (await confirmar(
                      context,
                      titulo: 'Descartar la alerta',
                      mensaje:
                          'La alerta se guarda en el historial del lote, pero '
                          'deja de aparecer como pendiente. ¿Descartarla?',
                    )) {
                      await s.alertas.descartar(alerta.id);
                    }
                  },
                  child: const Text('No aplica'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _atender(
      BuildContext context, Servicios s, String? codigo) async {
    final controlador = TextEditingController();
    final guardar = await showDialog<bool>(
      context: context,
      builder: (contexto) => AlertDialog(
        title: const Text('¿Qué hiciste?'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text(
              'Anotar el resultado sirve para saber qué funcionó cuando '
              'vuelva a pasar.',
              style: TextStyle(fontSize: 15),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: controlador,
              maxLines: 3,
              autofocus: true,
              decoration: const InputDecoration(
                labelText: 'Qué hiciste y cómo salió',
                hintText: 'Añadí 12 kg de cáscara y subió a 45 °C',
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(contexto, false),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(contexto, true),
            child: const Text('Guardar'),
          ),
        ],
      ),
    );

    if (guardar != true) return;
    await s.alertas.atender(
      alerta.id,
      correccionCodigo: codigo,
      resultado: controlador.text.trim(),
    );
    if (context.mounted) avisar(context, 'Alerta marcada como atendida');
  }
}

class _Bloque extends StatelessWidget {
  const _Bloque(this.titulo, this.texto);
  final String titulo;
  final String texto;

  @override
  Widget build(BuildContext context) {
    if (texto.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(titulo,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w700,
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              )),
          Text(texto, style: const TextStyle(fontSize: 16)),
        ],
      ),
    );
  }
}

String _haceCuanto(DateTime fecha) {
  final d = DateTime.now().difference(fecha);
  if (d.inMinutes < 60) return 'hace ${d.inMinutes} min';
  if (d.inHours < 24) return 'hace ${d.inHours} h';
  return 'hace ${d.inDays} día(s)';
}

/// Biblioteca de correcciones, disponible sin internet (RF-COR-01).
class PantallaCorrecciones extends StatelessWidget {
  const PantallaCorrecciones({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Qué hacer si…')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Text(
            'Problemas frecuentes y qué hacer en cada caso. Funciona sin '
            'internet.',
            style: TextStyle(fontSize: 16),
          ),
          const SizedBox(height: 16),
          for (final c in Correcciones.todas)
            Card(
              child: ListTile(
                leading: CircleAvatar(child: Text(c.codigo.split('-').last)),
                title: Text(c.problema),
                subtitle: Text(
                  c.pasos.first,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                trailing: const Icon(Icons.chevron_right),
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => PantallaDetalleCorreccion(correccion: c),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class PantallaDetalleCorreccion extends StatelessWidget {
  const PantallaDetalleCorreccion({super.key, required this.correccion});
  final Correccion correccion;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(correccion.problema)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TarjetaSeccion(
            titulo: 'Por qué pasa',
            icono: Icons.help_outline,
            hijos: [
              Text(correccion.porQuePasa, style: const TextStyle(fontSize: 16)),
            ],
          ),
          TarjetaSeccion(
            titulo: 'Qué hacer ahora',
            icono: Icons.build_outlined,
            hijos: [
              for (var i = 0; i < correccion.pasos.length; i++)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      CircleAvatar(
                        radius: 14,
                        child: Text('${i + 1}',
                            style: const TextStyle(fontSize: 14)),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(correccion.pasos[i],
                            style: const TextStyle(fontSize: 16)),
                      ),
                    ],
                  ),
                ),
            ],
          ),
          TarjetaSeccion(
            titulo: 'Para la próxima vez',
            icono: Icons.lightbulb_outline,
            hijos: [
              Text(correccion.paraLaProximaVez,
                  style: const TextStyle(fontSize: 16)),
            ],
          ),
        ],
      ),
    );
  }
}
