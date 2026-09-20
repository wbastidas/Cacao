/// Buenas prácticas de manufactura (RF-BPM-01 a RF-BPM-03).
///
/// Un registro de BPM solo sirve ante una inspección si no se puede reescribir
/// después. Por eso, pasadas 24 horas, el checklist se cierra y únicamente
/// admite anotaciones, que quedan fechadas.
library;

import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:printing/printing.dart';

import '../../datos/bd/base_datos.dart';
import '../../servicios.dart';
import '../comun/widgets.dart';
import '../tema.dart';

class PantallaBpm extends StatelessWidget {
  const PantallaBpm({super.key});

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Buenas prácticas'),
        actions: [
          IconButton(
            tooltip: 'Exportar en PDF',
            icon: const Icon(Icons.picture_as_pdf_outlined),
            onPressed: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const PantallaBpmPdf()),
            ),
          ),
        ],
      ),
      body: StreamBuilder<List<ChecklistBpm>>(
        stream: s.apoyo.observarChecklists(),
        builder: (context, snapshot) {
          final checklists = snapshot.data ?? const [];
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              const Text(
                'Estos registros son los que pide ARCSA en una inspección. '
                'Márcalos el mismo día: pasadas 24 horas quedan cerrados.',
                style: TextStyle(fontSize: 15),
              ),
              const SizedBox(height: 16),
              for (final c in checklists)
                _TarjetaChecklist(checklist: c),
            ],
          );
        },
      ),
    );
  }
}

class _TarjetaChecklist extends StatelessWidget {
  const _TarjetaChecklist({required this.checklist});
  final ChecklistBpm checklist;

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);
    final items = (jsonDecode(checklist.itemsJson) as List).cast<String>();

    return FutureBuilder<RegistroBpm?>(
      future: s.apoyo.registroDeHoy(checklist.id),
      builder: (context, snapshot) {
        final registro = snapshot.data;
        final respuestas = registro == null
            ? <String, bool>{}
            : (jsonDecode(registro.respuestasJson) as Map)
                .map((k, v) => MapEntry(k as String, v as bool));
        final marcados = respuestas.values.where((v) => v).length;

        return Card(
          child: ListTile(
            leading: Icon(
              registro == null
                  ? Icons.radio_button_unchecked
                  : (marcados == items.length
                      ? Icons.check_circle
                      : Icons.pending_outlined),
              color: registro != null && marcados == items.length
                  ? ColoresEstado.bien
                  : null,
              size: 30,
            ),
            title: Text(checklist.nombre),
            subtitle: Text(registro == null
                ? '${items.length} puntos · sin hacer hoy'
                : '$marcados de ${items.length} · ${registro.responsable}'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => _PantallaChecklist(checklist: checklist),
              ),
            ),
          ),
        );
      },
    );
  }
}

class _PantallaChecklist extends StatefulWidget {
  const _PantallaChecklist({required this.checklist});
  final ChecklistBpm checklist;

  @override
  State<_PantallaChecklist> createState() => _PantallaChecklistState();
}

class _PantallaChecklistState extends State<_PantallaChecklist> {
  Map<String, bool> _respuestas = {};
  final _responsable = TextEditingController();
  final _observaciones = TextEditingController();
  RegistroBpm? _registro;
  bool _cargando = true;

  List<String> get _items =>
      (jsonDecode(widget.checklist.itemsJson) as List).cast<String>();

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_cargando) _cargar();
  }

  Future<void> _cargar() async {
    final s = ProveedorServicios.de(context);
    final registro = await s.apoyo.registroDeHoy(widget.checklist.id);
    if (!mounted) return;
    setState(() {
      _registro = registro;
      _respuestas = registro == null
          ? {for (final i in _items) i: false}
          : {
              for (final i in _items) i: false,
              ...(jsonDecode(registro.respuestasJson) as Map)
                  .map((k, v) => MapEntry(k as String, v as bool)),
            };
      _responsable.text = registro?.responsable ?? '';
      _observaciones.text = registro?.observaciones ?? '';
      _cargando = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);
    final cerrado = _registro?.cerrado ?? false;

    return Scaffold(
      appBar: AppBar(title: Text(widget.checklist.nombre)),
      body: _cargando
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                if (cerrado)
                  const Aviso(
                    icono: Icons.lock_outline,
                    titulo: 'Registro cerrado',
                    texto:
                        'Pasaron más de 24 horas. Ya no se puede cambiar, '
                        'pero puedes añadir una anotación.',
                  ),
                for (final item in _items)
                  CheckboxListTile(
                    value: _respuestas[item] ?? false,
                    title: Text(item, style: const TextStyle(fontSize: 16)),
                    contentPadding: EdgeInsets.zero,
                    onChanged: cerrado
                        ? null
                        : (v) =>
                            setState(() => _respuestas[item] = v ?? false),
                  ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _responsable,
                  enabled: !cerrado,
                  decoration: const InputDecoration(
                    labelText: 'Quién lo hizo',
                    helperText: 'Tu nombre queda en el registro',
                  ),
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _observaciones,
                  enabled: !cerrado,
                  maxLines: 3,
                  decoration:
                      const InputDecoration(labelText: 'Observaciones'),
                ),
                const SizedBox(height: 20),
                if (cerrado)
                  BotonGrande(
                    texto: 'Añadir una anotación',
                    icono: Icons.note_add_outlined,
                    onPressed: () => _anotar(context),
                  )
                else
                  BotonGrande(
                    texto: 'Guardar el registro',
                    icono: Icons.save_outlined,
                    onPressed: () async {
                      if (_responsable.text.trim().isEmpty) {
                        avisar(context, 'Escribe quién lo hizo', error: true);
                        return;
                      }
                      try {
                        await s.apoyo.guardarRegistroBpm(
                          checklistId: widget.checklist.id,
                          respuestas: _respuestas,
                          responsable: _responsable.text.trim(),
                          observaciones: _observaciones.text.trim(),
                        );
                        if (!context.mounted) return;
                        avisar(context, 'Registro guardado');
                        Navigator.pop(context);
                      } catch (e) {
                        if (context.mounted) {
                          avisar(context, '$e', error: true);
                        }
                      }
                    },
                  ),
              ],
            ),
    );
  }

  Future<void> _anotar(BuildContext context) async {
    final s = ProveedorServicios.de(context);
    final texto = TextEditingController();
    final guardar = await showDialog<bool>(
      context: context,
      builder: (contexto) => AlertDialog(
        title: const Text('Anotación'),
        content: TextField(
          controller: texto,
          maxLines: 3,
          autofocus: true,
          decoration: const InputDecoration(
              hintText: 'Lo que quieras dejar aclarado'),
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
    if (guardar != true || _registro == null) return;
    await s.apoyo.anotarEnRegistroBpm(_registro!.id, texto.text.trim());
    if (context.mounted) avisar(context, 'Anotación guardada');
  }
}

/// Exportar los registros de BPM en PDF por rango de fechas (RF-BPM-03).
class PantallaBpmPdf extends StatelessWidget {
  const PantallaBpmPdf({super.key});

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Registros de BPM')),
      body: FutureBuilder<List<RegistroBpm>>(
        future: s.bd.select(s.bd.registrosBpm).get(),
        builder: (context, snapshot) {
          final registros = snapshot.data ?? const [];
          if (registros.isEmpty) {
            return const EstadoVacio(
              icono: Icons.picture_as_pdf_outlined,
              titulo: 'Sin registros todavía',
              explicacion:
                  'Marca los checklists del día y podrás exportarlos aquí.',
            );
          }
          return FutureBuilder<List<ChecklistBpm>>(
            future: s.bd.select(s.bd.checklistsBpm).get(),
            builder: (context, snapChecklists) {
              final checklists = {
                for (final c in snapChecklists.data ?? const <ChecklistBpm>[])
                  c.id: c.nombre,
              };
              return PdfPreview(
                build: (formato) => _construir(formato, registros, checklists),
                canChangeOrientation: false,
                canDebug: false,
                pdfFileName: 'bpm_cacaotrace.pdf',
              );
            },
          );
        },
      ),
    );
  }
}

Future<Uint8List> _construir(
  PdfPageFormat formato,
  List<RegistroBpm> registros,
  Map<String, String> checklists,
) async {
  final documento = pw.Document();
  final ordenados = [...registros]..sort((a, b) => a.fecha.compareTo(b.fecha));

  documento.addPage(
    pw.MultiPage(
      pageFormat: formato,
      build: (contexto) => [
        pw.Text('Registros de buenas prácticas de manufactura',
            style:
                pw.TextStyle(fontSize: 16, fontWeight: pw.FontWeight.bold)),
        pw.SizedBox(height: 12),
        pw.TableHelper.fromTextArray(
          headerStyle: pw.TextStyle(
              fontSize: 9, fontWeight: pw.FontWeight.bold),
          cellStyle: const pw.TextStyle(fontSize: 8),
          headers: const ['Fecha', 'Checklist', 'Responsable', 'Cumplidos',
              'Observaciones'],
          data: [
            for (final r in ordenados)
              [
                '${r.fecha.day}/${r.fecha.month}/${r.fecha.year}',
                checklists[r.checklistId] ?? r.checklistId,
                r.responsable,
                () {
                  final m = (jsonDecode(r.respuestasJson) as Map);
                  final si = m.values.where((v) => v == true).length;
                  return '$si de ${m.length}';
                }(),
                r.observaciones,
              ],
          ],
        ),
      ],
    ),
  );
  return documento.save();
}
