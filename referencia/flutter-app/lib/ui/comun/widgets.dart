/// Piezas de interfaz que se repiten en toda la app.
///
/// Tenerlas en un solo sitio es lo que hace que la app se sienta coherente:
/// el mismo botón grande, la misma forma de pedir un número, el mismo aviso
/// cuando algo no está.
library;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../tema.dart';

/// Botón grande de acción principal, con icono Y texto (§7 de la ERS).
class BotonGrande extends StatelessWidget {
  const BotonGrande({
    super.key,
    required this.texto,
    required this.icono,
    this.onPressed,
    this.color,
    this.subtitulo,
  });

  final String texto;
  final IconData icono;
  final VoidCallback? onPressed;
  final Color? color;
  final String? subtitulo;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      child: FilledButton.icon(
        onPressed: onPressed,
        icon: Icon(icono, size: 26),
        style: color == null
            ? null
            : FilledButton.styleFrom(backgroundColor: color),
        label: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(texto, textAlign: TextAlign.left),
            if (subtitulo != null)
              Text(
                subtitulo!,
                style: const TextStyle(
                    fontSize: 13, fontWeight: FontWeight.normal),
              ),
          ],
        ),
      ),
    );
  }
}

/// Campo para pedir un número, con teclado numérico y validación clara.
class CampoNumero extends StatelessWidget {
  const CampoNumero({
    super.key,
    required this.etiqueta,
    required this.controlador,
    this.unidad = '',
    this.ayuda,
    this.decimales = true,
    this.obligatorio = false,
    this.minimo,
    this.maximo,
    this.onChanged,
  });

  final String etiqueta;
  final TextEditingController controlador;
  final String unidad;
  final String? ayuda;
  final bool decimales;
  final bool obligatorio;
  final double? minimo;
  final double? maximo;
  final ValueChanged<String>? onChanged;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: TextFormField(
        controller: controlador,
        keyboardType:
            TextInputType.numberWithOptions(decimal: decimales, signed: false),
        inputFormatters: [
          FilteringTextInputFormatter.allow(
              decimales ? RegExp(r'[0-9.,]') : RegExp(r'[0-9]')),
        ],
        style: const TextStyle(fontSize: 20),
        decoration: InputDecoration(
          labelText: etiqueta,
          suffixText: unidad.isEmpty ? null : unidad,
          helperText: ayuda,
        ),
        onChanged: onChanged,
        validator: (v) {
          final texto = (v ?? '').trim().replaceAll(',', '.');
          if (texto.isEmpty) {
            return obligatorio ? 'Escribe $etiqueta' : null;
          }
          final n = double.tryParse(texto);
          if (n == null) return 'Escribe solo números';
          if (minimo != null && n < minimo!) {
            return 'No puede ser menor que $minimo';
          }
          if (maximo != null && n > maximo!) {
            return 'No puede ser mayor que $maximo';
          }
          return null;
        },
      ),
    );
  }
}

/// Lee un controlador de número, tolerando la coma decimal.
double? leerNumero(TextEditingController c) =>
    double.tryParse(c.text.trim().replaceAll(',', '.'));

/// Lo que se muestra cuando una lista está vacía.
///
/// Nunca se deja una pantalla en blanco: siempre se dice qué falta y cuál es
/// el siguiente paso.
class EstadoVacio extends StatelessWidget {
  const EstadoVacio({
    super.key,
    required this.icono,
    required this.titulo,
    required this.explicacion,
    this.accion,
  });

  final IconData icono;
  final String titulo;
  final String explicacion;
  final Widget? accion;

  @override
  Widget build(BuildContext context) {
    final colores = Theme.of(context).colorScheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icono, size: 64, color: colores.outline),
            const SizedBox(height: 16),
            Text(titulo,
                style: Theme.of(context).textTheme.titleLarge,
                textAlign: TextAlign.center),
            const SizedBox(height: 8),
            Text(
              explicacion,
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 16, color: colores.onSurfaceVariant),
            ),
            if (accion != null) ...[const SizedBox(height: 24), accion!],
          ],
        ),
      ),
    );
  }
}

/// Tarjeta con título, para agrupar información.
class TarjetaSeccion extends StatelessWidget {
  const TarjetaSeccion({
    super.key,
    required this.titulo,
    required this.hijos,
    this.icono,
    this.accion,
    this.color,
  });

  final String titulo;
  final List<Widget> hijos;
  final IconData? icono;
  final Widget? accion;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: color,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                if (icono != null) ...[
                  Icon(icono, size: 22),
                  const SizedBox(width: 8),
                ],
                Expanded(
                  child: Text(titulo,
                      style: Theme.of(context).textTheme.titleMedium),
                ),
                if (accion != null) accion!,
              ],
            ),
            const SizedBox(height: 12),
            ...hijos,
          ],
        ),
      ),
    );
  }
}

/// Una fila "etiqueta ....... valor", que es como se lee un dato de un vistazo.
class FilaDato extends StatelessWidget {
  const FilaDato(this.etiqueta, this.valor, {super.key, this.color, this.icono});

  final String etiqueta;
  final String valor;
  final Color? color;
  final IconData? icono;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (icono != null) ...[
            Icon(icono, size: 18, color: color),
            const SizedBox(width: 6),
          ],
          Expanded(
            child: Text(etiqueta, style: const TextStyle(fontSize: 16)),
          ),
          const SizedBox(width: 12),
          Text(
            valor,
            style: TextStyle(
                fontSize: 17, fontWeight: FontWeight.w600, color: color),
          ),
        ],
      ),
    );
  }
}

/// Aviso en color según su gravedad, con icono y texto.
class Aviso extends StatelessWidget {
  const Aviso({
    super.key,
    required this.texto,
    this.titulo,
    this.color = ColoresEstado.atencion,
    this.icono = Icons.warning_amber_rounded,
    this.accion,
  });

  final String texto;
  final String? titulo;
  final Color color;
  final IconData icono;
  final Widget? accion;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.symmetric(vertical: 8),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        // ignore: deprecated_member_use
        color: color.withOpacity(0.12),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color, width: 1.5),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icono, color: color, size: 26),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (titulo != null)
                  Text(titulo!,
                      style: TextStyle(
                          fontSize: 17,
                          fontWeight: FontWeight.w700,
                          color: color)),
                Text(texto, style: const TextStyle(fontSize: 16)),
                if (accion != null) ...[const SizedBox(height: 8), accion!],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Pregunta de sí o no con botones grandes.
Future<bool> confirmar(
  BuildContext context, {
  required String titulo,
  required String mensaje,
  String si = 'Sí',
  String no = 'Cancelar',
  bool peligroso = false,
}) async {
  final respuesta = await showDialog<bool>(
    context: context,
    builder: (contexto) => AlertDialog(
      title: Text(titulo),
      content: Text(mensaje, style: const TextStyle(fontSize: 16)),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(contexto, false),
          child: Text(no),
        ),
        FilledButton(
          onPressed: () => Navigator.pop(contexto, true),
          style: peligroso
              ? FilledButton.styleFrom(
                  backgroundColor: ColoresEstado.problema)
              : null,
          child: Text(si),
        ),
      ],
    ),
  );
  return respuesta ?? false;
}

/// Mensaje corto al pie de la pantalla.
void avisar(BuildContext context, String mensaje, {bool error = false}) {
  ScaffoldMessenger.of(context)
    ..hideCurrentSnackBar()
    ..showSnackBar(
      SnackBar(
        content: Text(mensaje),
        backgroundColor: error ? ColoresEstado.problema : null,
        duration: Duration(seconds: error ? 5 : 3),
      ),
    );
}
