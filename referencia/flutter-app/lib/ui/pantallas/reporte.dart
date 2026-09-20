/// Reporte PDF de trazabilidad por lote (RF-LOT-07).
///
/// Es el documento que se le entrega a un cliente mayorista o que se muestra
/// en una inspección: de dónde vino el cacao, qué se le hizo, qué dio la
/// prueba de corte y qué dijo el laboratorio.
library;

import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:printing/printing.dart';

import '../../datos/repositorios/repositorio_lotes.dart';
import '../../servicios.dart';

class PantallaReporte extends StatelessWidget {
  const PantallaReporte({super.key, required this.loteId});
  final String loteId;

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Reporte de trazabilidad')),
      body: FutureBuilder<LoteCompleto?>(
        future: s.lotes.completo(loteId),
        builder: (context, snapshot) {
          final c = snapshot.data;
          if (c == null) {
            return const Center(child: CircularProgressIndicator());
          }
          return FutureBuilder<List<HechoLote>>(
            future: s.lotes.lineaDeTiempo(loteId),
            builder: (context, snapHistoria) {
              final historia = snapHistoria.data ?? const [];
              return PdfPreview(
                build: (formato) => _construir(formato, c, historia),
                canChangeOrientation: false,
                canDebug: false,
                pdfFileName: 'trazabilidad_${c.lote.codigo}.pdf',
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
  LoteCompleto c,
  List<HechoLote> historia,
) async {
  final documento = pw.Document();
  final lote = c.lote;
  final prueba = c.pruebaCorteFinal;

  pw.Widget dato(String etiqueta, String valor) => pw.Padding(
        padding: const pw.EdgeInsets.symmetric(vertical: 2),
        child: pw.Row(
          children: [
            pw.SizedBox(
              width: 150,
              child: pw.Text(etiqueta,
                  style: const pw.TextStyle(
                      fontSize: 10, color: PdfColors.grey700)),
            ),
            pw.Expanded(
                child: pw.Text(valor,
                    style: pw.TextStyle(
                        fontSize: 11, fontWeight: pw.FontWeight.bold))),
          ],
        ),
      );

  pw.Widget seccion(String titulo, List<pw.Widget> hijos) => pw.Column(
        crossAxisAlignment: pw.CrossAxisAlignment.start,
        children: [
          pw.SizedBox(height: 12),
          pw.Text(titulo,
              style: pw.TextStyle(
                  fontSize: 13, fontWeight: pw.FontWeight.bold)),
          pw.Divider(height: 6),
          ...hijos,
        ],
      );

  documento.addPage(
    pw.MultiPage(
      pageFormat: formato,
      header: (contexto) => pw.Container(
        alignment: pw.Alignment.centerLeft,
        margin: const pw.EdgeInsets.only(bottom: 12),
        child: pw.Text('CacaoTrace · Trazabilidad del lote ${lote.codigo}',
            style: const pw.TextStyle(fontSize: 9, color: PdfColors.grey600)),
      ),
      footer: (contexto) => pw.Row(
        mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
        children: [
          pw.Text(
            'Emitido el ${_fecha(DateTime.now())}',
            style: const pw.TextStyle(fontSize: 8, color: PdfColors.grey600),
          ),
          pw.Text('Página ${contexto.pageNumber} de ${contexto.pagesCount}',
              style:
                  const pw.TextStyle(fontSize: 8, color: PdfColors.grey600)),
        ],
      ),
      build: (contexto) => [
        pw.Text('Reporte de trazabilidad',
            style:
                pw.TextStyle(fontSize: 20, fontWeight: pw.FontWeight.bold)),
        pw.Text(lote.codigo,
            style: const pw.TextStyle(fontSize: 16, color: PdfColors.grey700)),

        seccion('Origen', [
          dato('Finca', c.finca?.nombre ?? 'sin registrar'),
          if (c.finca != null && c.finca!.provincia.isNotEmpty)
            dato('Provincia / cantón',
                '${c.finca!.provincia} / ${c.finca!.canton}'),
          dato('Variedad', lote.variedad),
          if (lote.fechaCosecha != null)
            dato('Fecha de cosecha', _fecha(lote.fechaCosecha!)),
          dato('Fecha de llegada', _fecha(lote.fechaLlegada)),
        ]),

        if (c.recepcion != null)
          seccion('Recepción', [
            dato('Mazorcas', '${c.recepcion!.mazorcasTotal}'),
            dato('Peso', '${c.recepcion!.pesoKg} kg'),
            dato('Sanas', '${c.recepcion!.mazorcasSanas}'),
            dato('Con monilia', '${c.recepcion!.mazorcasMonilia}'),
            dato('Con fitóftora', '${c.recepcion!.mazorcasFitoftora}'),
            dato('Descartadas', '${c.recepcion!.mazorcasDescartadas}'),
            dato('Días de reposo', '${c.recepcion!.diasReposo}'),
          ]),

        if (c.apertura != null)
          seccion('Apertura', [
            dato('Fecha', _fecha(c.apertura!.fecha)),
            dato('Mazorcas abiertas', '${c.apertura!.mazorcasAbiertas}'),
            dato('Baba', '${c.apertura!.kgBaba} kg'),
            dato('Cáscara añadida', '${c.apertura!.kgCascaraAnadida} kg'),
          ]),

        if (c.fermentacion != null)
          seccion('Fermentación', [
            dato('Inicio', _fechaHora(c.fermentacion!.inicio)),
            if (c.fermentacion!.fin != null)
              dato('Fin', _fechaHora(c.fermentacion!.fin!)),
            if (c.fermentacion!.fin != null)
              dato(
                'Duración',
                '${c.fermentacion!.fin!.difference(c.fermentacion!.inicio).inHours} horas',
              ),
            dato('Masa', '${c.fermentacion!.masaKg} kg'),
            dato('Aislamiento', c.fermentacion!.aislamiento),
          ]),

        if (c.secado != null)
          seccion('Secado', [
            dato('Inicio', _fecha(c.secado!.inicio)),
            if (c.secado!.fin != null) dato('Fin', _fecha(c.secado!.fin!)),
            if (c.secado!.kgSeco != null)
              dato('Grano seco', '${c.secado!.kgSeco} kg'),
          ]),

        if (prueba != null)
          seccion('Prueba de corte (NTE INEN 176)', [
            dato('Fecha', _fecha(prueba.fecha)),
            dato('Granos evaluados', '${prueba.granos}'),
            dato('Perfil', prueba.perfilNorma),
            dato('Resultado', prueba.resultado),
            if (prueba.peso100g != null)
              dato('Peso de 100 granos', '${prueba.peso100g} g'),
            dato('Método',
                prueba.modeloVersion.isEmpty
                    ? 'conteo manual'
                    : 'modelo ${prueba.modeloVersion} con revisión del usuario'),
          ]),

        seccion('Historia del lote', [
          for (final h in historia)
            pw.Padding(
              padding: const pw.EdgeInsets.symmetric(vertical: 1.5),
              child: pw.Row(
                crossAxisAlignment: pw.CrossAxisAlignment.start,
                children: [
                  pw.SizedBox(
                    width: 80,
                    child: pw.Text(_fechaHora(h.fecha),
                        style: const pw.TextStyle(
                            fontSize: 8, color: PdfColors.grey700)),
                  ),
                  pw.SizedBox(
                    width: 80,
                    child: pw.Text(h.etapa,
                        style: const pw.TextStyle(fontSize: 8)),
                  ),
                  pw.Expanded(
                    child: pw.Text(
                      h.detalle.isEmpty ? h.titulo : '${h.titulo} — ${h.detalle}',
                      style: pw.TextStyle(
                        fontSize: 9,
                        color: h.esAlerta ? PdfColors.orange800 : null,
                      ),
                    ),
                  ),
                ],
              ),
            ),
        ]),

        pw.SizedBox(height: 20),
        pw.Container(
          padding: const pw.EdgeInsets.all(8),
          decoration: pw.BoxDecoration(
            border: pw.Border.all(color: PdfColors.grey400, width: 0.5),
          ),
          child: pw.Text(
            'Los resultados de calidad con validez comercial dependen de la '
            'norma oficial vigente y de un laboratorio acreditado. Este '
            'reporte recoge los registros del productor y no sustituye un '
            'análisis de laboratorio ni una certificación.',
            style: const pw.TextStyle(fontSize: 8, color: PdfColors.grey700),
          ),
        ),
      ],
    ),
  );

  return documento.save();
}

String _fecha(DateTime f) =>
    '${f.day.toString().padLeft(2, '0')}/'
    '${f.month.toString().padLeft(2, '0')}/${f.year}';

String _fechaHora(DateTime f) =>
    '${_fecha(f)} ${f.hour.toString().padLeft(2, '0')}:'
    '${f.minute.toString().padLeft(2, '0')}';
