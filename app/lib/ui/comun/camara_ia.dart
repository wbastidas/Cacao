/// Tomar una foto y analizarla con un modelo, con la guía de encuadre que
/// pide RF-IA-04 y la corrección manual que manda RF-IA-03.
///
/// Si no hay modelo instalado, esto NO desaparece: sigue sirviendo para tomar
/// la foto y elegir la respuesta a mano. Esa es la diferencia entre una app
/// que depende de la IA y una que se apoya en ella.
library;

import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image/image.dart' as img;
import 'package:image_picker/image_picker.dart';

import '../../ia/resultado_ia.dart';
import '../../servicios.dart';
import '../tema.dart';
import 'widgets.dart';

/// Lo que devuelve la pantalla de captura: la foto, lo que dijo el modelo y
/// lo que decidió el usuario (que es lo que manda).
class CapturaAnalizada {
  const CapturaAnalizada({
    required this.archivo,
    required this.decisionUsuario,
    this.resultado,
  });

  final File archivo;
  final String decisionUsuario;
  final ResultadoClasificacion? resultado;

  bool get laIaAcerto =>
      resultado?.mejor != null && resultado!.mejor!.clase == decisionUsuario;

  /// Sirve para reentrenar si el usuario la revisó (§8.3 de la ERS).
  bool get aptaParaDataset => decisionUsuario.isNotEmpty;

  Map<String, dynamic> get analisisJson => {
        if (resultado?.mejor != null) ...{
          'clase': resultado!.mejor!.clase,
          'confianza': resultado!.mejor!.confianza,
          'modelo': resultado!.modeloVersion,
          'ms': resultado!.milisegundos,
        },
      };
}

/// Redimensiona la foto al lado que pide el modelo y la entrega como
/// píxeles RGB 0-255, que es el contrato del §8.2 de la ERS.
Future<Uint8List?> prepararParaModelo(File archivo, int lado) async {
  final bytes = await archivo.readAsBytes();
  final original = img.decodeImage(bytes);
  if (original == null) return null;
  // bakeOrientation corrige la rotación EXIF: sin esto, una foto vertical del
  // teléfono entra acostada y el modelo ve otra cosa.
  final derecha = img.bakeOrientation(original);
  final escalada =
      img.copyResize(derecha, width: lado, height: lado, interpolation: img.Interpolation.linear);

  final salida = Uint8List(lado * lado * 3);
  var i = 0;
  for (var y = 0; y < lado; y++) {
    for (var x = 0; x < lado; x++) {
      final p = escalada.getPixel(x, y);
      salida[i++] = p.r.toInt();
      salida[i++] = p.g.toInt();
      salida[i++] = p.b.toInt();
    }
  }
  return salida;
}

/// Botón que abre la cámara, analiza y pide la confirmación del usuario.
class BotonFotoIa extends StatelessWidget {
  const BotonFotoIa({
    super.key,
    required this.tarea,
    required this.clases,
    required this.onCaptura,
    this.texto = 'Tomar foto',
    this.consejo,
  });

  /// `mazorca`, `tostado`, `chocolate`.
  final String tarea;

  /// Las opciones entre las que el usuario puede elegir.
  final List<String> clases;
  final ValueChanged<CapturaAnalizada> onCaptura;
  final String texto;

  /// Cómo encuadrar bien esta foto en concreto.
  final String? consejo;

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);
    final disponible = s.modelos.estaDisponible(tarea);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        BotonGrande(
          texto: texto,
          subtitulo: disponible
              ? 'Se analiza en el teléfono, sin internet'
              : 'Sin modelo instalado: eliges tú la respuesta',
          icono: Icons.photo_camera_outlined,
          onPressed: () => _capturar(context),
        ),
        if (consejo != null)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(Icons.info_outline,
                    size: 18,
                    color: Theme.of(context).colorScheme.onSurfaceVariant),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(consejo!,
                      style: TextStyle(
                        fontSize: 14,
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      )),
                ),
              ],
            ),
          ),
      ],
    );
  }

  Future<void> _capturar(BuildContext context) async {
    final s = ProveedorServicios.de(context);
    final elegida = await ImagePicker().pickImage(
      source: ImageSource.camera,
      // §3.1 de la ERS: máximo 1600 px y calidad 85 para no llenar el teléfono.
      maxWidth: 1600,
      maxHeight: 1600,
      imageQuality: 85,
    );
    if (elegida == null || !context.mounted) return;

    final archivo = File(elegida.path);
    ResultadoClasificacion? resultado;

    if (s.modelos.estaDisponible(tarea)) {
      try {
        final pixeles = await prepararParaModelo(archivo, 224);
        if (pixeles != null) {
          resultado = await s.modelos.clasificar(tarea, pixeles);
        }
      } catch (e) {
        if (context.mounted) {
          avisar(context, 'No se pudo analizar la foto: $e', error: true);
        }
      }
    }

    if (!context.mounted) return;
    final decision = await _pedirConfirmacion(context, archivo, resultado);
    if (decision == null) return;

    onCaptura(CapturaAnalizada(
      archivo: archivo,
      decisionUsuario: decision,
      resultado: resultado,
    ));
  }

  Future<String?> _pedirConfirmacion(
    BuildContext context,
    File archivo,
    ResultadoClasificacion? resultado,
  ) {
    final s = ProveedorServicios.de(context);
    final sinModelo = s.modelos.porQueNoEstaDisponible(tarea);

    return showModalBottomSheet<String>(
      context: context,
      isScrollControlled: true,
      builder: (contexto) => SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(12),
                child: Image.file(archivo, height: 180, fit: BoxFit.cover),
              ),
              const SizedBox(height: 16),
              if (resultado != null) ...[
                Text(
                  resultado.estaSeguro
                      ? 'La app ve: ${resultado.texto}'
                      : 'No estoy seguro',
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.w700,
                    color: resultado.estaSeguro
                        ? ColoresEstado.bien
                        : ColoresEstado.atencion,
                  ),
                ),
                if (resultado.pistaBajaConfianza != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Text(resultado.pistaBajaConfianza!,
                        style: const TextStyle(fontSize: 15)),
                  ),
                const SizedBox(height: 4),
                Text(
                  'Modelo ${resultado.modeloVersion} · '
                  '${resultado.milisegundos} ms',
                  style: const TextStyle(fontSize: 13),
                ),
              ] else
                Text(
                  sinModelo?.mensaje ??
                      'Elige tú qué muestra la foto.',
                  style: const TextStyle(fontSize: 16),
                ),
              const SizedBox(height: 20),
              const Text('¿Qué es?',
                  style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
              const SizedBox(height: 4),
              const Text(
                'Tu respuesta es la que se guarda, y sirve para que el modelo '
                'mejore.',
                style: TextStyle(fontSize: 14),
              ),
              const SizedBox(height: 12),
              for (final clase in clases)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: SizedBox(
                    width: double.infinity,
                    child: OutlinedButton(
                      onPressed: () => Navigator.pop(contexto, clase),
                      style: OutlinedButton.styleFrom(
                        side: BorderSide(
                          width: resultado?.mejor?.clase == clase ? 2.5 : 1,
                          color: resultado?.mejor?.clase == clase
                              ? ColoresEstado.bien
                              : Theme.of(contexto).colorScheme.outline,
                        ),
                      ),
                      child: Text(_bonito(clase)),
                    ),
                  ),
                ),
              TextButton(
                onPressed: () => Navigator.pop(contexto),
                child: const Text('Cancelar'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// "dano_insectos" -> "Daño insectos"
String _bonito(String clase) {
  final t = clase.replaceAll('_', ' ');
  return t.isEmpty ? t : t[0].toUpperCase() + t.substring(1);
}

String nombreBonito(String clase) => _bonito(clase);
