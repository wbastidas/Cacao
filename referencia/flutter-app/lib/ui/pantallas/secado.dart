/// Secado del grano (RF-SEC-01 a RF-SEC-05).
library;

import 'package:flutter/material.dart';

import '../../datos/bd/base_datos.dart';
import '../../nucleo/modelo/etapas.dart';
import '../../servicios.dart';
import '../comun/widgets.dart';
import '../tema.dart';

/// Guía de la prueba del puñado, para cuando no hay medidor (RF-SEC-03).
const List<(String, String)> pruebaPunado = [
  ('Suena a quebradizo', 'Al apretar un puñado, los granos crujen y se '
      'separan solos. Está listo.'),
  ('Suena sordo', 'Los granos se pegan entre sí y no crujen. Le falta secado.'),
  ('Se aplasta', 'El grano cede al apretarlo. Le falta bastante.'),
];

class PantallaSecado extends StatefulWidget {
  const PantallaSecado({super.key, required this.loteId});
  final String loteId;

  @override
  State<PantallaSecado> createState() => _PantallaSecadoState();
}

class _PantallaSecadoState extends State<PantallaSecado> {
  Secado? _secado;
  bool _cargando = true;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _cargar();
  }

  Future<void> _cargar() async {
    final s = ProveedorServicios.de(context);
    final secado = await s.lotes.secadoDe(widget.loteId);
    if (mounted) {
      setState(() {
        _secado = secado;
        _cargando = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);
    if (_cargando) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    if (_secado == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Secado')),
        body: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            const EstadoVacio(
              icono: Icons.wb_sunny_outlined,
              titulo: 'Empezar el secado',
              explicacion:
                  'El secado baja la humedad del grano hasta un 7 % para que '
                  'no críe moho. Los primeros dos días son los que más agua '
                  'sueltan: extiende en capa delgada y remueve seguido.',
            ),
            for (final m in MetodoSecado.values)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: BotonGrande(
                  texto: m.etiqueta,
                  icono: switch (m) {
                    MetodoSecado.marquesina => Icons.roofing,
                    MetodoSecado.sol => Icons.wb_sunny,
                    MetodoSecado.secador => Icons.air,
                  },
                  onPressed: () async {
                    await s.lotes
                        .iniciarSecado(loteId: widget.loteId, metodo: m);
                    _cargar();
                  },
                ),
              ),
          ],
        ),
      );
    }

    final secado = _secado!;
    final terminado = secado.fin != null;
    final dias = DateTime.now().difference(secado.inicio).inDays + 1;

    return Scaffold(
      appBar: AppBar(title: Text('Secado · ${secado.metodo.etiqueta}')),
      body: StreamBuilder<List<LecturaSecado>>(
        stream: s.lotes.observarLecturasSecado(secado.id),
        builder: (context, snapshot) {
          final lecturas = snapshot.data ?? const [];
          final ultima = lecturas.isEmpty ? null : lecturas.last;

          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              TarjetaSeccion(
                titulo: terminado ? 'Secado terminado' : 'Día $dias de secado',
                icono: Icons.calendar_today_outlined,
                hijos: [
                  FilaDato('Método', secado.metodo.etiqueta),
                  FilaDato('Jornadas registradas', '${lecturas.length}'),
                  if (ultima?.humedadGrano != null)
                    FilaDato(
                      'Última humedad',
                      '${ultima!.humedadGrano!.toStringAsFixed(1)} %',
                      color: ultima.humedadGrano! > 7
                          ? ColoresEstado.atencion
                          : ColoresEstado.bien,
                    ),
                  if (secado.kgSeco != null)
                    FilaDato('Grano seco', '${secado.kgSeco} kg'),
                ],
              ),
              if (!terminado) ...[
                BotonGrande(
                  texto: 'Registrar la jornada de hoy',
                  subtitulo: 'Humedad, remociones y estado del grano',
                  icono: Icons.add_task,
                  onPressed: () => _nuevaJornada(context, secado),
                ),
                const SizedBox(height: 12),
                OutlinedButton.icon(
                  icon: const Icon(Icons.stop_circle_outlined),
                  label: const Text('Cerrar el secado'),
                  onPressed: () => _cerrar(context, secado),
                ),
              ],
              const SizedBox(height: 16),
              if (lecturas.isNotEmpty) ...[
                Text('Jornadas', style: Theme.of(context).textTheme.titleMedium),
                for (final l in lecturas.reversed)
                  Card(
                    child: ListTile(
                      leading: Icon(
                        l.moho ? Icons.warning : Icons.wb_sunny_outlined,
                        color: l.moho ? ColoresEstado.problema : null,
                      ),
                      title: Text(l.humedadGrano == null
                          ? (l.pruebaPunado.isEmpty
                              ? 'Jornada'
                              : l.pruebaPunado)
                          : 'Humedad ${l.humedadGrano!.toStringAsFixed(1)} %'),
                      subtitle: Text([
                        _fecha(l.fecha),
                        '${l.remociones} remociones',
                        if (l.espesorCm != null) 'capa ${l.espesorCm} cm',
                        if (l.moho) 'MOHO',
                      ].join(' · ')),
                    ),
                  ),
              ],
            ],
          );
        },
      ),
    );
  }

  Future<void> _nuevaJornada(BuildContext context, Secado secado) async {
    final s = ProveedorServicios.de(context);
    final humedad = TextEditingController();
    final espesor = TextEditingController();
    final remociones = TextEditingController(text: '3');
    final tempAmb = TextEditingController();
    final hrAmb = TextEditingController();
    var moho = false;
    String? punado;

    final guardar = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (contexto) => StatefulBuilder(
        builder: (contexto, actualizar) => Padding(
          padding: EdgeInsets.fromLTRB(
              16, 24, 16, MediaQuery.of(contexto).viewInsets.bottom + 24),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Jornada de secado',
                    style: Theme.of(contexto).textTheme.titleLarge),
                const SizedBox(height: 16),
                CampoNumero(
                  etiqueta: 'Humedad del grano',
                  controlador: humedad,
                  unidad: '%',
                  ayuda: 'Si tienes medidor. Si no, usa la prueba del puñado',
                  minimo: 0,
                  maximo: 60,
                ),
                const SizedBox(height: 8),
                const Text('Prueba del puñado',
                    style: TextStyle(
                        fontSize: 17, fontWeight: FontWeight.w600)),
                for (final p in pruebaPunado)
                  RadioListTile<String>(
                    value: p.$1,
                    // ignore: deprecated_member_use
                    groupValue: punado,
                    contentPadding: EdgeInsets.zero,
                    title: Text(p.$1),
                    subtitle: Text(p.$2, style: const TextStyle(fontSize: 14)),
                    // ignore: deprecated_member_use
                    onChanged: (v) => actualizar(() => punado = v),
                  ),
                const SizedBox(height: 8),
                CampoNumero(
                  etiqueta: 'Espesor de la capa',
                  controlador: espesor,
                  unidad: 'cm',
                  ayuda: 'Los primeros días, 3-4 cm',
                  minimo: 0,
                ),
                CampoNumero(
                  etiqueta: 'Remociones del día',
                  controlador: remociones,
                  decimales: false,
                ),
                CampoNumero(
                  etiqueta: 'Temperatura ambiente',
                  controlador: tempAmb,
                  unidad: '°C',
                ),
                CampoNumero(
                  etiqueta: 'Humedad ambiente',
                  controlador: hrAmb,
                  unidad: '%',
                  maximo: 100,
                ),
                SwitchListTile(
                  value: moho,
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Vi moho en el grano'),
                  subtitle: const Text(
                    'Separa ahora los granos afectados: el moho da un sabor '
                    'que no se quita después.',
                    style: TextStyle(fontSize: 14),
                  ),
                  onChanged: (v) => actualizar(() => moho = v),
                ),
                const SizedBox(height: 16),
                BotonGrande(
                  texto: 'Guardar la jornada',
                  icono: Icons.save_outlined,
                  onPressed: () => Navigator.pop(contexto, true),
                ),
              ],
            ),
          ),
        ),
      ),
    );

    if (guardar != true || !context.mounted) return;
    final alertas = await s.lotes.registrarLecturaSecado(
      secadoId: secado.id,
      loteId: widget.loteId,
      humedadGrano: leerNumero(humedad),
      pruebaPunado: punado ?? '',
      tempAmbiente: leerNumero(tempAmb),
      hrAmbiente: leerNumero(hrAmb),
      espesorCm: leerNumero(espesor),
      remociones: leerNumero(remociones)?.round() ?? 0,
      moho: moho,
    );
    if (!context.mounted) return;
    avisar(
      context,
      alertas.isEmpty
          ? 'Jornada guardada'
          : '${alertas.first.quePaso}. ${alertas.first.queHacer}',
      error: alertas.isNotEmpty,
    );
  }

  Future<void> _cerrar(BuildContext context, Secado secado) async {
    final s = ProveedorServicios.de(context);
    final kgSeco = TextEditingController();
    final humedad = TextEditingController();

    final guardar = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (contexto) => Padding(
        padding: EdgeInsets.fromLTRB(
            16, 24, 16, MediaQuery.of(contexto).viewInsets.bottom + 24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Cerrar el secado',
                style: Theme.of(contexto).textTheme.titleLarge),
            const SizedBox(height: 8),
            const Text(
              'Con más del 7 % de humedad el grano cría moho en el almacén, '
              'así que la app te pedirá confirmación.',
              style: TextStyle(fontSize: 15),
            ),
            const SizedBox(height: 16),
            CampoNumero(
              etiqueta: 'Peso del grano seco',
              controlador: kgSeco,
              unidad: 'kg',
              obligatorio: true,
              minimo: 0,
            ),
            CampoNumero(
              etiqueta: 'Humedad final',
              controlador: humedad,
              unidad: '%',
              minimo: 0,
              maximo: 60,
            ),
            const SizedBox(height: 16),
            BotonGrande(
              texto: 'Cerrar el secado',
              icono: Icons.check,
              onPressed: () => Navigator.pop(contexto, true),
            ),
          ],
        ),
      ),
    );

    if (guardar != true || !context.mounted) return;
    final kg = leerNumero(kgSeco);
    if (kg == null) {
      avisar(context, 'Escribe el peso del grano seco', error: true);
      return;
    }

    var alertas = await s.lotes.cerrarSecado(
      secadoId: secado.id,
      loteId: widget.loteId,
      kgSeco: kg,
      humedadFinal: leerNumero(humedad),
    );

    final bloqueo = alertas.where((a) => a.bloquea).firstOrNull;
    if (bloqueo != null && context.mounted) {
      final seguir = await confirmar(
        context,
        titulo: bloqueo.quePaso,
        mensaje: '${bloqueo.porQueImporta}\n\n'
            '¿Quieres almacenarlo de todos modos? Quedará registrado.',
        si: 'Almacenar igual',
        peligroso: true,
      );
      if (!seguir) return;
      alertas = await s.lotes.cerrarSecado(
        secadoId: secado.id,
        loteId: widget.loteId,
        kgSeco: kg,
        humedadFinal: leerNumero(humedad),
        confirmado: true,
      );
    }

    if (!context.mounted) return;
    avisar(context, 'Secado cerrado con $kg kg');
    _cargar();
  }
}

String _fecha(DateTime f) =>
    '${f.day.toString().padLeft(2, '0')}/'
    '${f.month.toString().padLeft(2, '0')}';
