/// Plantilla imprimible del tablero de 10 × 10 (RF-PRC-02).
///
/// Incluye una tarjeta de color de referencia: fotografiarla junto a los
/// granos es lo que permite comparar fotos tomadas con distinta luz, que es
/// la primera causa de que un modelo entrenado en casa falle en el taller.
library;

import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:printing/printing.dart';

class PantallaTableroPdf extends StatelessWidget {
  const PantallaTableroPdf({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Plantilla del tablero')),
      body: PdfPreview(
        build: (formato) => _construir(formato),
        canChangeOrientation: false,
        canDebug: false,
        pdfFileName: 'tablero_prueba_corte.pdf',
      ),
    );
  }
}

Future<Uint8List> _construir(PdfPageFormat formato) async {
  final documento = pw.Document();

  documento.addPage(
    pw.Page(
      pageFormat: formato,
      build: (contexto) => pw.Column(
        crossAxisAlignment: pw.CrossAxisAlignment.start,
        children: [
          pw.Text('Tablero para la prueba de corte',
              style: pw.TextStyle(
                  fontSize: 18, fontWeight: pw.FontWeight.bold)),
          pw.SizedBox(height: 4),
          pw.Text(
            'Coloca un grano cortado por casilla, con la cara interna hacia '
            'arriba. Fotografía de frente, a unos 30 cm, con luz pareja y sin '
            'flash. La tarjeta de color debe salir en la foto.',
            style: const pw.TextStyle(fontSize: 10),
          ),
          pw.SizedBox(height: 12),
          pw.Expanded(
            child: pw.AspectRatio(
              aspectRatio: 1,
              child: pw.Table(
                border: pw.TableBorder.all(width: 0.8),
                children: [
                  for (var fila = 0; fila < 10; fila++)
                    pw.TableRow(
                      children: [
                        for (var col = 0; col < 10; col++)
                          pw.Container(
                            height: 42,
                            alignment: pw.Alignment.topLeft,
                            padding: const pw.EdgeInsets.all(2),
                            child: pw.Text(
                              '${fila * 10 + col + 1}',
                              style: const pw.TextStyle(
                                  fontSize: 6, color: PdfColors.grey500),
                            ),
                          ),
                      ],
                    ),
                ],
              ),
            ),
          ),
          pw.SizedBox(height: 12),
          pw.Text('Tarjeta de color de referencia',
              style: pw.TextStyle(
                  fontSize: 11, fontWeight: pw.FontWeight.bold)),
          pw.SizedBox(height: 4),
          pw.Row(
            children: [
              for (final par in const [
                ('Blanco', PdfColors.white),
                ('Gris 25', PdfColor.fromInt(0xFFBFBFBF)),
                ('Gris 50', PdfColor.fromInt(0xFF808080)),
                ('Negro', PdfColors.black),
                ('Café bien ferm.', PdfColor.fromInt(0xFF8D5524)),
                ('Café claro', PdfColor.fromInt(0xFFB07D4F)),
                ('Violeta', PdfColor.fromInt(0xFF7B5EA7)),
                ('Pizarra', PdfColor.fromInt(0xFF546E7A)),
              ])
                pw.Expanded(
                  child: pw.Column(
                    children: [
                      pw.Container(
                        height: 34,
                        margin: const pw.EdgeInsets.all(1),
                        decoration: pw.BoxDecoration(
                          color: par.$2,
                          border: pw.Border.all(width: 0.5),
                        ),
                      ),
                      pw.Text(par.$1,
                          style: const pw.TextStyle(fontSize: 6),
                          textAlign: pw.TextAlign.center),
                    ],
                  ),
                ),
            ],
          ),
          pw.SizedBox(height: 8),
          pw.Text(
            'CacaoTrace · Los colores impresos varían según la impresora; '
            'úsalos como referencia relativa, no absoluta.',
            style: const pw.TextStyle(fontSize: 8, color: PdfColors.grey600),
          ),
        ],
      ),
    ),
  );

  return documento.save();
}
