/// Fermentación: curva de temperatura, volteos y lecturas (RF-FER-01 a 07).
///
/// El botón "Registré volteo" está arriba y ocupa todo el ancho a propósito:
/// es la acción que se hace todos los días, a veces con las manos sucias, y
/// tiene que poder hacerse en un toque (RNF-09).
library;

import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../../datos/bd/base_datos.dart';
import '../../nucleo/modelo/etapas.dart';
import '../../servicios.dart';
import '../comun/widgets.dart';
import '../tema.dart';

class PantallaFermentacion extends StatefulWidget {
  const PantallaFermentacion({super.key, required this.loteId});
  final String loteId;

  @override
  State<PantallaFermentacion> createState() => _PantallaFermentacionState();
}

class _PantallaFermentacionState extends State<PantallaFermentacion> {
  Fermentacion? _fermentacion;
  bool _cargando = true;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _cargar();
  }

  Future<void> _cargar() async {
    final s = ProveedorServicios.de(context);
    final f = await s.lotes.fermentacionDe(widget.loteId);
    if (mounted) {
      setState(() {
        _fermentacion = f;
        _cargando = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_cargando) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    return Scaffold(
      appBar: AppBar(title: const Text('Fermentación')),
      body: _fermentacion == null
          ? _Iniciar(loteId: widget.loteId, alIniciar: _cargar)
          : _EnCurso(
              loteId: widget.loteId,
              fermentacion: _fermentacion!,
              alCambiar: _cargar,
            ),
    );
  }
}

class _Iniciar extends StatefulWidget {
  const _Iniciar({required this.loteId, required this.alIniciar});
  final String loteId;
  final VoidCallback alIniciar;

  @override
  State<_Iniciar> createState() => _IniciarState();
}

class _IniciarState extends State<_Iniciar> {
  final _masa = TextEditingController();
  final _aislamiento = TextEditingController(text: 'Hojas de plátano y sacos');
  String? _equipoId;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final s = ProveedorServicios.de(context);
      final c = await s.lotes.completo(widget.loteId);
      final a = c?.apertura;
      if (a != null && mounted) {
        setState(() =>
            _masa.text = (a.kgBaba + a.kgCascaraAnadida).toStringAsFixed(1));
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const EstadoVacio(
          icono: Icons.thermostat,
          titulo: 'Empezar la fermentación',
          explicacion:
              'Aquí es donde el grano desarrolla el sabor a chocolate. '
              'Durante los próximos días medirás la temperatura y voltearás '
              'la masa cada 24 horas.',
        ),
        StreamBuilder<List<Equipo>>(
          stream: s.config.observarEquipos(tipo: 'fermentador'),
          builder: (context, snapshot) {
            final equipos = snapshot.data ?? const [];
            if (equipos.isEmpty) {
              return const Aviso(
                icono: Icons.info_outline,
                color: ColoresEstado.neutro,
                texto: 'No has registrado ningún fermentador. Puedes empezar '
                    'igual y registrarlo después desde Ajustes.',
              );
            }
            return DropdownButtonFormField<String>(
              initialValue: _equipoId ?? equipos.first.id,
              decoration: const InputDecoration(labelText: 'Fermentador'),
              items: [
                for (final e in equipos)
                  DropdownMenuItem(value: e.id, child: Text(e.nombre)),
              ],
              onChanged: (v) => setState(() => _equipoId = v),
            );
          },
        ),
        CampoNumero(
          etiqueta: 'Masa que entra',
          controlador: _masa,
          unidad: 'kg',
          obligatorio: true,
          minimo: 0.1,
        ),
        TextFormField(
          controller: _aislamiento,
          decoration: const InputDecoration(
            labelText: 'Aislamiento',
            helperText: 'Qué le pusiste encima para conservar el calor',
          ),
        ),
        const SizedBox(height: 20),
        BotonGrande(
          texto: 'Empezar ahora',
          icono: Icons.play_arrow,
          onPressed: () async {
            final masa = leerNumero(_masa);
            if (masa == null || masa <= 0) {
              avisar(context, 'Escribe cuántos kg entran', error: true);
              return;
            }
            await s.lotes.iniciarFermentacion(
              loteId: widget.loteId,
              equipoId: _equipoId,
              masaKg: masa,
              aislamiento: _aislamiento.text.trim(),
            );
            widget.alIniciar();
          },
        ),
      ],
    );
  }
}

class _EnCurso extends StatelessWidget {
  const _EnCurso({
    required this.loteId,
    required this.fermentacion,
    required this.alCambiar,
  });

  final String loteId;
  final Fermentacion fermentacion;
  final VoidCallback alCambiar;

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);
    final horas = DateTime.now().difference(fermentacion.inicio).inHours;
    final terminada = fermentacion.fin != null;

    return StreamBuilder<List<LecturaFermentacion>>(
      stream: s.lotes.observarLecturas(fermentacion.id),
      builder: (context, snapLecturas) {
        final lecturas = snapLecturas.data ?? const [];
        return StreamBuilder<List<Volteo>>(
          stream: s.lotes.observarVolteos(fermentacion.id),
          builder: (context, snapVolteos) {
            final volteos = snapVolteos.data ?? const [];
            final ultimoVolteo = volteos.isEmpty ? null : volteos.last;
            final horasSinVoltear = DateTime.now()
                .difference(ultimoVolteo?.fechaHora ?? fermentacion.inicio)
                .inHours;

            return ListView(
              padding: const EdgeInsets.all(16),
              children: [
                if (!terminada)
                  _BotonVolteo(
                    fermentacionId: fermentacion.id,
                    horasSinVoltear: horasSinVoltear,
                    alRegistrar: alCambiar,
                  ),
                const SizedBox(height: 12),
                TarjetaSeccion(
                  titulo: terminada ? 'Fermentación terminada' : 'En curso',
                  icono: Icons.timer_outlined,
                  hijos: [
                    FilaDato('Tiempo',
                        terminada
                            ? '${fermentacion.fin!.difference(fermentacion.inicio).inHours} h'
                            : '$horas h (${(horas / 24).toStringAsFixed(1)} días)'),
                    FilaDato('Masa', '${fermentacion.masaKg} kg'),
                    FilaDato('Volteos', '${volteos.length}'),
                    FilaDato('Lecturas', '${lecturas.length}'),
                    if (lecturas.isNotEmpty)
                      FilaDato(
                        'Última temperatura',
                        lecturas.last.tempC == null
                            ? 'sin dato'
                            : '${lecturas.last.tempC!.toStringAsFixed(1)} °C',
                      ),
                  ],
                ),
                _Grafica(fermentacion: fermentacion, lecturas: lecturas),
                const SizedBox(height: 8),
                if (!terminada)
                  BotonGrande(
                    texto: 'Registrar una lectura',
                    subtitulo: 'Temperatura, pH y olor',
                    icono: Icons.thermostat,
                    onPressed: () => _nuevaLectura(context),
                  ),
                const SizedBox(height: 12),
                if (!terminada)
                  OutlinedButton.icon(
                    icon: const Icon(Icons.stop_circle_outlined),
                    label: const Text('Cerrar la fermentación'),
                    onPressed: () async {
                      if (await confirmar(
                        context,
                        titulo: 'Cerrar la fermentación',
                        mensaje:
                            'El lote pasará a secado. Lleva $horas horas '
                            'fermentando. ¿Seguro?',
                      )) {
                        await s.lotes
                            .cerrarFermentacion(fermentacion.id, loteId);
                        alCambiar();
                      }
                    },
                  ),
                const SizedBox(height: 16),
                if (lecturas.isNotEmpty) ...[
                  Text('Lecturas',
                      style: Theme.of(context).textTheme.titleMedium),
                  for (final l in lecturas.reversed)
                    Card(
                      child: ListTile(
                        leading: Icon(
                          l.fuente == 'sensor'
                              ? Icons.sensors
                              : Icons.touch_app_outlined,
                        ),
                        title: Text(l.tempC == null
                            ? 'Lectura'
                            : '${l.tempC!.toStringAsFixed(1)} °C'),
                        subtitle: Text([
                          _fechaHora(l.fechaHora),
                          if (l.ph != null) 'pH ${l.ph}',
                          if (l.olor != null) l.olor!.etiqueta,
                        ].join(' · ')),
                      ),
                    ),
                ],
              ],
            );
          },
        );
      },
    );
  }

  Future<void> _nuevaLectura(BuildContext context) async {
    final s = ProveedorServicios.de(context);
    final temp = TextEditingController();
    final ph = TextEditingController();
    OlorFermentacion? olor;

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
                Text('Nueva lectura',
                    style: Theme.of(contexto).textTheme.titleLarge),
                const SizedBox(height: 16),
                CampoNumero(
                  etiqueta: 'Temperatura',
                  controlador: temp,
                  unidad: '°C',
                  ayuda: 'Clava el termómetro en el centro de la masa',
                  minimo: 0,
                  maximo: 100,
                ),
                CampoNumero(
                  etiqueta: 'pH (si lo mides)',
                  controlador: ph,
                  minimo: 0,
                  maximo: 14,
                ),
                const SizedBox(height: 12),
                const Text('¿A qué huele?',
                    style: TextStyle(
                        fontSize: 17, fontWeight: FontWeight.w600)),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  children: [
                    for (final o in OlorFermentacion.values)
                      ChoiceChip(
                        label: Text(o.etiqueta),
                        selected: olor == o,
                        onSelected: (_) =>
                            actualizar(() => olor = olor == o ? null : o),
                      ),
                  ],
                ),
                if (olor != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Text(
                      olor!.significado,
                      style: TextStyle(
                        fontSize: 15,
                        color: olor!.esMalaSenal
                            ? ColoresEstado.problema
                            : ColoresEstado.bien,
                      ),
                    ),
                  ),
                const SizedBox(height: 20),
                BotonGrande(
                  texto: 'Guardar la lectura',
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
    final alertas = await s.lotes.registrarLectura(
      fermentacionId: fermentacion.id,
      loteId: loteId,
      tempC: leerNumero(temp),
      ph: leerNumero(ph),
      olor: olor,
    );
    if (!context.mounted) return;
    avisar(
      context,
      alertas.isEmpty
          ? 'Lectura guardada'
          : '${alertas.first.quePaso}. ${alertas.first.queHacer}',
      error: alertas.isNotEmpty,
    );
    alCambiar();
  }
}

class _BotonVolteo extends StatelessWidget {
  const _BotonVolteo({
    required this.fermentacionId,
    required this.horasSinVoltear,
    required this.alRegistrar,
  });

  final String fermentacionId;
  final int horasSinVoltear;
  final VoidCallback alRegistrar;

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);
    final toca = horasSinVoltear >= 24;

    return Column(
      children: [
        BotonGrande(
          texto: 'Registré un volteo',
          subtitulo: 'Van $horasSinVoltear horas desde el último',
          icono: Icons.rotate_right,
          color: toca ? ColoresEstado.atencion : null,
          onPressed: () async {
            await s.lotes.registrarVolteo(fermentacionId);
            if (context.mounted) avisar(context, 'Volteo registrado');
            alRegistrar();
          },
        ),
      ],
    );
  }
}

/// Curva de temperatura frente a la curva objetivo (RF-FER-05).
class _Grafica extends StatelessWidget {
  const _Grafica({required this.fermentacion, required this.lecturas});

  final Fermentacion fermentacion;
  final List<LecturaFermentacion> lecturas;

  /// Curva típica de una fermentación que va bien: sube hasta ~48 °C hacia el
  /// tercer día y baja despacio. Sirve de referencia visual, no de regla.
  static const List<FlSpot> objetivo = [
    FlSpot(0, 26),
    FlSpot(24, 38),
    FlSpot(48, 46),
    FlSpot(72, 48),
    FlSpot(96, 47),
    FlSpot(120, 44),
    FlSpot(144, 41),
  ];

  @override
  Widget build(BuildContext context) {
    final puntos = <FlSpot>[
      for (final l in lecturas)
        if (l.tempC != null)
          FlSpot(
            l.fechaHora.difference(fermentacion.inicio).inMinutes / 60.0,
            l.tempC!,
          ),
    ];

    if (puntos.isEmpty) {
      return const TarjetaSeccion(
        titulo: 'Curva de temperatura',
        icono: Icons.show_chart,
        hijos: [
          Text(
            'Cuando registres temperaturas, aquí verás tu curva comparada con '
            'la de una fermentación que va bien.',
            style: TextStyle(fontSize: 15),
          ),
        ],
      );
    }

    final maxX = [
      puntos.last.x,
      objetivo.last.x,
    ].reduce((a, b) => a > b ? a : b);

    return TarjetaSeccion(
      titulo: 'Curva de temperatura',
      icono: Icons.show_chart,
      hijos: [
        SizedBox(
          height: 240,
          child: LineChart(
            LineChartData(
              minY: 20,
              maxY: 55,
              maxX: maxX,
              gridData: const FlGridData(horizontalInterval: 5),
              titlesData: FlTitlesData(
                rightTitles: const AxisTitles(),
                topTitles: const AxisTitles(),
                bottomTitles: AxisTitles(
                  axisNameWidget: const Text('horas'),
                  sideTitles: SideTitles(
                    showTitles: true,
                    interval: 24,
                    getTitlesWidget: (v, _) => Text('${v.toInt()}',
                        style: const TextStyle(fontSize: 12)),
                  ),
                ),
                leftTitles: AxisTitles(
                  axisNameWidget: const Text('°C'),
                  sideTitles: SideTitles(
                    showTitles: true,
                    interval: 10,
                    reservedSize: 34,
                    getTitlesWidget: (v, _) => Text('${v.toInt()}',
                        style: const TextStyle(fontSize: 12)),
                  ),
                ),
              ),
              lineBarsData: [
                LineChartBarData(
                  spots: objetivo,
                  isCurved: true,
                  barWidth: 2,
                  dashArray: const [6, 4],
                  color: ColoresEstado.neutro,
                  dotData: const FlDotData(show: false),
                ),
                LineChartBarData(
                  spots: puntos,
                  isCurved: true,
                  barWidth: 3,
                  color: Theme.of(context).colorScheme.primary,
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 8),
        const Row(
          children: [
            Icon(Icons.remove, color: ColoresEstado.neutro),
            SizedBox(width: 4),
            Text('Referencia', style: TextStyle(fontSize: 14)),
            SizedBox(width: 16),
            Icon(Icons.remove),
            SizedBox(width: 4),
            Text('Tu lote', style: TextStyle(fontSize: 14)),
          ],
        ),
      ],
    );
  }
}

String _fechaHora(DateTime f) =>
    '${f.day.toString().padLeft(2, '0')}/'
    '${f.month.toString().padLeft(2, '0')} '
    '${f.hour.toString().padLeft(2, '0')}:'
    '${f.minute.toString().padLeft(2, '0')}';
