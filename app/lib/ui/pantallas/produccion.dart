/// Lote de producción: del grano seco a la barra empacada.
///
/// Reúne tostado, descascarillado, refinado, atemperado y empaque en una sola
/// pantalla con secciones, porque son pasos de la misma tanda y el usuario
/// pasa de uno a otro el mismo día.
library;

import 'package:flutter/material.dart';

import '../../datos/bd/base_datos.dart';
import '../../datos/repositorios/repositorio_produccion.dart';
import '../../nucleo/calculo/receta.dart';
import '../../nucleo/modelo/etapas.dart';
import '../../servicios.dart';
import '../comun/camara_ia.dart';
import '../comun/widgets.dart';
import '../tema.dart';

const List<String> clasesTostado = [
  'crudo',
  'ligero',
  'medio',
  'oscuro',
  'quemado'
];
const List<String> clasesChocolate = [
  'atemperado_ok',
  'fat_bloom',
  'sugar_bloom',
  'sin_brillo'
];

class PantallaProduccion extends StatelessWidget {
  const PantallaProduccion({super.key});

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Chocolate')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _nuevaTanda(context),
        icon: const Icon(Icons.add),
        label: const Text('Nueva tanda'),
      ),
      body: StreamBuilder<List<LoteProduccion>>(
        stream: s.produccion.observar(),
        builder: (context, snapshot) {
          final tandas = snapshot.data ?? const [];
          if (tandas.isEmpty) {
            return EstadoVacio(
              icono: Icons.cookie_outlined,
              titulo: 'Todavía no hay tandas',
              explicacion:
                  'Una tanda es el chocolate que haces con uno o varios lotes '
                  'de grano seco. Crea la primera cuando vayas a tostar.',
              accion: FilledButton.icon(
                icon: const Icon(Icons.add),
                label: const Text('Crear la primera tanda'),
                onPressed: () => _nuevaTanda(context),
              ),
            );
          }
          return ListView.builder(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
            itemCount: tandas.length,
            itemBuilder: (context, i) {
              final t = tandas[i];
              return Card(
                child: ListTile(
                  leading: const Icon(Icons.cookie_outlined, size: 32),
                  title: Text(t.codigo,
                      style: const TextStyle(
                          fontSize: 19, fontWeight: FontWeight.w700)),
                  subtitle: Text(
                    '${t.porcentajeCacao.toStringAsFixed(0)} % cacao · '
                    '${t.estado}'
                    '${t.kgChocolate == null ? '' : ' · ${t.kgChocolate} kg'}',
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => Navigator.push(
                    context,
                    MaterialPageRoute(
                        builder: (_) => PantallaDetalleTanda(tandaId: t.id)),
                  ),
                ),
              );
            },
          );
        },
      ),
    );
  }

  Future<void> _nuevaTanda(BuildContext context) async {
    final s = ProveedorServicios.de(context);
    final lotes = await s.lotes.observarLotes().first;
    final almacenados = lotes
        .where((l) =>
            l.estado.index >= EstadoLote.almacenado.index &&
            l.estado != EstadoLote.descartado)
        .toList();
    if (!context.mounted) return;

    if (almacenados.isEmpty) {
      avisar(
        context,
        'No hay lotes de grano seco almacenados todavía. Termina el secado de '
        'un lote primero.',
        error: true,
      );
      return;
    }

    final seleccion = <String, TextEditingController>{
      for (final l in almacenados) l.id: TextEditingController(),
    };

    final crear = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (contexto) => Padding(
        padding: EdgeInsets.fromLTRB(
            16, 24, 16, MediaQuery.of(contexto).viewInsets.bottom + 24),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Nueva tanda',
                  style: Theme.of(contexto).textTheme.titleLarge),
              const SizedBox(height: 8),
              const Text(
                'Escribe cuántos kg usas de cada lote. Puedes mezclar varios; '
                'la trazabilidad se conserva.',
                style: TextStyle(fontSize: 15),
              ),
              const SizedBox(height: 16),
              for (final l in almacenados)
                Row(
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(l.codigo,
                              style: const TextStyle(
                                  fontSize: 16, fontWeight: FontWeight.w600)),
                          if (l.ventaBloqueada)
                            const Text('Bloqueado por laboratorio',
                                style: TextStyle(
                                    fontSize: 13,
                                    color: ColoresEstado.problema)),
                        ],
                      ),
                    ),
                    SizedBox(
                      width: 120,
                      child: CampoNumero(
                        etiqueta: 'kg',
                        controlador: seleccion[l.id]!,
                      ),
                    ),
                  ],
                ),
              const SizedBox(height: 16),
              BotonGrande(
                texto: 'Crear la tanda',
                icono: Icons.check,
                onPressed: () => Navigator.pop(contexto, true),
              ),
            ],
          ),
        ),
      ),
    );

    if (crear != true || !context.mounted) return;
    final kgPorLote = <String, double>{};
    seleccion.forEach((id, controlador) {
      final kg = leerNumero(controlador);
      if (kg != null && kg > 0) kgPorLote[id] = kg;
    });

    if (kgPorLote.isEmpty) {
      avisar(context, 'Escribe cuántos kg usas de al menos un lote',
          error: true);
      return;
    }

    try {
      final tanda = await s.produccion.crear(kgPorLote: kgPorLote);
      if (!context.mounted) return;
      avisar(context, 'Tanda ${tanda.codigo} creada');
      await Navigator.push(
        context,
        MaterialPageRoute(
            builder: (_) => PantallaDetalleTanda(tandaId: tanda.id)),
      );
    } catch (e) {
      if (context.mounted) avisar(context, '$e', error: true);
    }
  }
}

class PantallaDetalleTanda extends StatefulWidget {
  const PantallaDetalleTanda({super.key, required this.tandaId});
  final String tandaId;

  @override
  State<PantallaDetalleTanda> createState() => _PantallaDetalleTandaState();
}

class _PantallaDetalleTandaState extends State<PantallaDetalleTanda> {
  Future<ProduccionCompleta?>? _tanda;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _recargar();
  }

  void _recargar() {
    final s = ProveedorServicios.de(context);
    setState(() {
      _tanda = s.produccion.completa(widget.tandaId);
    });
  }

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);

    return FutureBuilder<ProduccionCompleta?>(
      future: _tanda,
      builder: (context, snapshot) {
        final c = snapshot.data;
        if (c == null) {
          return const Scaffold(
              body: Center(child: CircularProgressIndicator()));
        }

        return Scaffold(
          appBar: AppBar(title: Text(c.produccion.codigo)),
          body: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              TarjetaSeccion(
                titulo: 'De dónde viene',
                icono: Icons.account_tree_outlined,
                hijos: [
                  FutureBuilder<List<String>>(
                    future: s.produccion.trazabilidadDe(widget.tandaId),
                    builder: (context, snap) => Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        for (final linea in snap.data ?? const [])
                          Padding(
                            padding: const EdgeInsets.symmetric(vertical: 2),
                            child: Text(linea,
                                style: const TextStyle(fontSize: 16)),
                          ),
                      ],
                    ),
                  ),
                  const Divider(height: 24),
                  FilaDato('Grano usado',
                      '${c.kgGranoUsado.toStringAsFixed(1)} kg'),
                  FilaDato('Cacao objetivo',
                      '${c.produccion.porcentajeCacao.toStringAsFixed(0)} %'),
                ],
              ),
              _SeccionTostado(completa: c, alCambiar: _recargar),
              _SeccionDescascarillado(completa: c, alCambiar: _recargar),
              _SeccionRefinado(completa: c, alCambiar: _recargar),
              _SeccionAtemperado(completa: c, alCambiar: _recargar),
              _SeccionEmpaque(completa: c, alCambiar: _recargar),
            ],
          ),
        );
      },
    );
  }
}

class _SeccionTostado extends StatelessWidget {
  const _SeccionTostado({required this.completa, required this.alCambiar});
  final ProduccionCompleta completa;
  final VoidCallback alCambiar;

  @override
  Widget build(BuildContext context) {
    final t = completa.tostado;
    final merma = t == null || t.kgSalida == null || t.kgEntrada <= 0
        ? null
        : 100 * (t.kgEntrada - t.kgSalida!) / t.kgEntrada;

    return TarjetaSeccion(
      titulo: 'Tostado',
      icono: Icons.local_fire_department_outlined,
      hijos: [
        if (t == null)
          const Text(
            'El tostado desarrolla el aroma y mata los microorganismos. '
            'Anota temperatura, tiempo y los kg que entran y salen.',
            style: TextStyle(fontSize: 15),
          )
        else ...[
          FilaDato('Entró', '${t.kgEntrada} kg'),
          if (t.kgSalida != null) FilaDato('Salió', '${t.kgSalida} kg'),
          if (merma != null)
            FilaDato('Merma', '${merma.toStringAsFixed(1)} %',
                color: merma < 3 || merma > 12
                    ? ColoresEstado.atencion
                    : ColoresEstado.bien),
          if (t.tempC != null) FilaDato('Temperatura', '${t.tempC} °C'),
          if (t.minutos != null) FilaDato('Tiempo', '${t.minutos} min'),
          if (t.gradoUsuario.isNotEmpty)
            FilaDato('Grado', nombreBonito(t.gradoUsuario)),
        ],
        const SizedBox(height: 12),
        BotonGrande(
          texto: t == null ? 'Registrar el tostado' : 'Editar el tostado',
          icono: Icons.edit_outlined,
          onPressed: () => _editar(context),
        ),
      ],
    );
  }

  Future<void> _editar(BuildContext context) async {
    final s = ProveedorServicios.de(context);
    final t = completa.tostado;
    final entrada = TextEditingController(
        text: t?.kgEntrada.toString() ??
            completa.kgGranoUsado.toStringAsFixed(1));
    final salida = TextEditingController(text: t?.kgSalida?.toString() ?? '');
    final temp = TextEditingController(text: t?.tempC?.toString() ?? '');
    final minutos = TextEditingController(text: t?.minutos?.toString() ?? '');
    var grado = t?.gradoUsuario ?? '';
    String? fotoRuta;
    String gradoIa = t?.gradoIa ?? '';
    double? confianzaIa = t?.confianzaIa;
    String modeloVersion = t?.modeloVersion ?? '';

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
                Text('Tostado',
                    style: Theme.of(contexto).textTheme.titleLarge),
                CampoNumero(
                  etiqueta: 'Grano que entra',
                  controlador: entrada,
                  unidad: 'kg',
                  obligatorio: true,
                ),
                CampoNumero(
                    etiqueta: 'Grano que sale',
                    controlador: salida,
                    unidad: 'kg'),
                CampoNumero(
                    etiqueta: 'Temperatura', controlador: temp, unidad: '°C'),
                CampoNumero(
                    etiqueta: 'Tiempo',
                    controlador: minutos,
                    unidad: 'min',
                    decimales: false),
                const SizedBox(height: 12),
                BotonFotoIa(
                  tarea: 'tostado',
                  clases: clasesTostado,
                  texto: 'Fotografiar los nibs',
                  consejo: 'Extiende los nibs sobre fondo blanco, sin flash.',
                  onCaptura: (captura) => actualizar(() {
                    grado = captura.decisionUsuario;
                    fotoRuta = captura.archivo.path;
                    gradoIa = captura.resultado?.mejor?.clase ?? '';
                    confianzaIa = captura.resultado?.mejor?.confianza;
                    modeloVersion = captura.resultado?.modeloVersion ?? '';
                  }),
                ),
                const SizedBox(height: 12),
                const Text('Grado de tostado',
                    style:
                        TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
                Wrap(
                  spacing: 8,
                  children: [
                    for (final g in clasesTostado)
                      ChoiceChip(
                        label: Text(nombreBonito(g)),
                        selected: grado == g,
                        onSelected: (_) => actualizar(() => grado = g),
                      ),
                  ],
                ),
                const SizedBox(height: 16),
                BotonGrande(
                  texto: 'Guardar',
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
    String? fotoId;
    if (fotoRuta != null) {
      fotoId = await s.apoyo.guardarFoto(
        rutaLocal: fotoRuta!,
        etapa: 'tostado',
        loteProduccionId: completa.produccion.id,
        analisisIa: {'clase': gradoIa, 'confianza': confianzaIa},
        etiquetaUsuario: grado,
        aptaDataset: grado.isNotEmpty,
      );
    }

    final alertas = await s.produccion.guardarTostado(
      loteProduccionId: completa.produccion.id,
      kgEntrada: leerNumero(entrada) ?? 0,
      kgSalida: leerNumero(salida),
      tempC: leerNumero(temp),
      minutos: leerNumero(minutos)?.round(),
      gradoIa: gradoIa,
      confianzaIa: confianzaIa,
      gradoUsuario: grado,
      modeloVersion: modeloVersion,
      fotoId: fotoId,
    );
    if (!context.mounted) return;
    avisar(
      context,
      alertas.isEmpty
          ? 'Tostado guardado'
          : '${alertas.first.quePaso}. ${alertas.first.queHacer}',
      error: alertas.isNotEmpty,
    );
    alCambiar();
  }
}

class _SeccionDescascarillado extends StatelessWidget {
  const _SeccionDescascarillado(
      {required this.completa, required this.alCambiar});
  final ProduccionCompleta completa;
  final VoidCallback alCambiar;

  @override
  Widget build(BuildContext context) {
    final d = completa.descascarillado;
    final total = d == null ? 0.0 : d.kgNibs + d.kgCascarilla;
    final pct = total > 0 ? 100 * d!.kgCascarilla / total : null;

    return TarjetaSeccion(
      titulo: 'Descascarillado',
      icono: Icons.grain,
      hijos: [
        if (d == null)
          const Text(
            'Separar la cascarilla del nib. Lo normal es que la cascarilla '
            'sea alrededor del 15 % del peso.',
            style: TextStyle(fontSize: 15),
          )
        else ...[
          FilaDato('Nibs', '${d.kgNibs} kg'),
          FilaDato('Cascarilla', '${d.kgCascarilla} kg'),
          if (pct != null)
            FilaDato('Cascarilla', '${pct.toStringAsFixed(1)} %',
                color: (pct - 15).abs() > 5
                    ? ColoresEstado.atencion
                    : ColoresEstado.bien),
        ],
        const SizedBox(height: 12),
        BotonGrande(
          texto: d == null ? 'Registrar' : 'Editar',
          icono: Icons.edit_outlined,
          onPressed: () => _editar(context),
        ),
      ],
    );
  }

  Future<void> _editar(BuildContext context) async {
    final s = ProveedorServicios.de(context);
    final nibs = TextEditingController(
        text: completa.descascarillado?.kgNibs.toString() ?? '');
    final cascarilla = TextEditingController(
        text: completa.descascarillado?.kgCascarilla.toString() ?? '');

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
            Text('Descascarillado',
                style: Theme.of(contexto).textTheme.titleLarge),
            CampoNumero(
                etiqueta: 'Nibs',
                controlador: nibs,
                unidad: 'kg',
                obligatorio: true),
            CampoNumero(
                etiqueta: 'Cascarilla',
                controlador: cascarilla,
                unidad: 'kg',
                obligatorio: true),
            const SizedBox(height: 16),
            BotonGrande(
              texto: 'Guardar',
              icono: Icons.save_outlined,
              onPressed: () => Navigator.pop(contexto, true),
            ),
          ],
        ),
      ),
    );

    if (guardar != true || !context.mounted) return;
    final alertas = await s.produccion.guardarDescascarillado(
      loteProduccionId: completa.produccion.id,
      kgNibs: leerNumero(nibs) ?? 0,
      kgCascarilla: leerNumero(cascarilla) ?? 0,
    );
    if (!context.mounted) return;
    avisar(
      context,
      alertas.isEmpty
          ? 'Guardado'
          : '${alertas.first.quePaso}. ${alertas.first.queHacer}',
      error: alertas.isNotEmpty,
    );
    alCambiar();
  }
}

class _SeccionRefinado extends StatelessWidget {
  const _SeccionRefinado({required this.completa, required this.alCambiar});
  final ProduccionCompleta completa;
  final VoidCallback alCambiar;

  @override
  Widget build(BuildContext context) {
    final r = completa.refinado;
    return TarjetaSeccion(
      titulo: 'Refinado y conchado',
      icono: Icons.blender_outlined,
      hijos: [
        if (r == null)
          const Text(
            'El melanger muele los nibs con el azúcar hasta que las '
            'partículas dejan de sentirse en la lengua. Suelen ser entre 24 y '
            '48 horas.',
            style: TextStyle(fontSize: 15),
          )
        else ...[
          FilaDato('Nibs', '${r.nibsKg} kg'),
          FilaDato('Azúcar', '${r.azucarKg} kg'),
          if (r.mantecaKg > 0) FilaDato('Manteca', '${r.mantecaKg} kg'),
          if (r.lecitinaKg > 0) FilaDato('Lecitina', '${r.lecitinaKg} kg'),
          if (r.horas != null) FilaDato('Horas', '${r.horas}'),
        ],
        const SizedBox(height: 12),
        BotonGrande(
          texto: r == null ? 'Calcular la receta' : 'Editar',
          icono: Icons.calculate_outlined,
          onPressed: () => _editar(context),
        ),
      ],
    );
  }

  Future<void> _editar(BuildContext context) async {
    final s = ProveedorServicios.de(context);
    final nibsDisponibles = completa.descascarillado?.kgNibs ?? 0;
    final nibs = TextEditingController(
        text: (completa.refinado?.nibsKg ?? nibsDisponibles).toString());
    final horas = TextEditingController(
        text: completa.refinado?.horas?.toString() ?? '');
    var porcentaje = completa.produccion.porcentajeCacao;
    var mantecaExtra = 0.0;
    Receta? receta;

    void recalcular(void Function(void Function()) actualizar) {
      final kg = leerNumero(nibs);
      if (kg == null || kg <= 0) return;
      try {
        final nueva = s.produccion.calcularReceta(
          kgNibs: kg,
          porcentajeCacao: porcentaje,
          mantecaExtraPct: mantecaExtra,
        );
        actualizar(() => receta = nueva);
      } catch (_) {
        actualizar(() => receta = null);
      }
    }

    final guardar = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (contexto) => StatefulBuilder(
        builder: (contexto, actualizar) {
          receta ??= (() {
            try {
              return s.produccion.calcularReceta(
                kgNibs: leerNumero(nibs) ?? 1,
                porcentajeCacao: porcentaje,
              );
            } catch (_) {
              return null;
            }
          })();

          return Padding(
            padding: EdgeInsets.fromLTRB(
                16, 24, 16, MediaQuery.of(contexto).viewInsets.bottom + 24),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Receta del chocolate',
                      style: Theme.of(contexto).textTheme.titleLarge),
                  CampoNumero(
                    etiqueta: 'Nibs disponibles',
                    controlador: nibs,
                    unidad: 'kg',
                    obligatorio: true,
                    onChanged: (_) => recalcular(actualizar),
                  ),
                  const SizedBox(height: 8),
                  Text('Cacao: ${porcentaje.toStringAsFixed(0)} %',
                      style: const TextStyle(
                          fontSize: 17, fontWeight: FontWeight.w600)),
                  Slider(
                    value: porcentaje,
                    min: 50,
                    max: 100,
                    divisions: 50,
                    label: '${porcentaje.toStringAsFixed(0)} %',
                    onChanged: (v) {
                      porcentaje = v;
                      recalcular(actualizar);
                    },
                  ),
                  Text('Manteca añadida: ${mantecaExtra.toStringAsFixed(0)} %',
                      style: const TextStyle(fontSize: 15)),
                  Slider(
                    value: mantecaExtra,
                    min: 0,
                    max: 15,
                    divisions: 15,
                    label: '${mantecaExtra.toStringAsFixed(0)} %',
                    onChanged: (v) {
                      mantecaExtra = v;
                      recalcular(actualizar);
                    },
                  ),
                  if (receta != null)
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          children: [
                            FilaDato('Nibs',
                                '${receta!.kgNibs.toStringAsFixed(2)} kg'),
                            FilaDato('Azúcar',
                                '${receta!.kgAzucar.toStringAsFixed(2)} kg'),
                            FilaDato('Manteca',
                                '${receta!.kgMantecaAnadida.toStringAsFixed(2)} kg'),
                            FilaDato('Lecitina',
                                '${receta!.kgLecitina.toStringAsFixed(3)} kg'),
                            const Divider(),
                            FilaDato('Total',
                                '${receta!.kgTotal.toStringAsFixed(2)} kg'),
                            FilaDato('Barras de 50 g',
                                '~${receta!.barrasEstimadas()}'),
                          ],
                        ),
                      ),
                    ),
                  CampoNumero(
                      etiqueta: 'Horas de refinado',
                      controlador: horas,
                      unidad: 'h'),
                  const SizedBox(height: 16),
                  BotonGrande(
                    texto: 'Guardar la receta',
                    icono: Icons.save_outlined,
                    onPressed: receta == null
                        ? null
                        : () => Navigator.pop(contexto, true),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );

    if (guardar != true || receta == null || !context.mounted) return;
    await s.produccion.guardarRefinado(
      loteProduccionId: completa.produccion.id,
      inicio: completa.refinado?.inicio ?? DateTime.now(),
      horas: leerNumero(horas),
      nibsKg: receta!.kgNibs,
      azucarKg: receta!.kgAzucar,
      mantecaKg: receta!.kgMantecaAnadida,
      lecitinaKg: receta!.kgLecitina,
    );
    if (!context.mounted) return;
    avisar(context, 'Receta guardada');
    alCambiar();
  }
}

class _SeccionAtemperado extends StatelessWidget {
  const _SeccionAtemperado({required this.completa, required this.alCambiar});
  final ProduccionCompleta completa;
  final VoidCallback alCambiar;

  @override
  Widget build(BuildContext context) {
    final ultimo =
        completa.atemperados.isEmpty ? null : completa.atemperados.last;

    return TarjetaSeccion(
      titulo: 'Atemperado y moldeado',
      icono: Icons.ac_unit,
      hijos: [
        const Text(
          'Atemperar es formar los cristales de manteca correctos: es lo que '
          'da brillo, chasquido y que no se derrita en la mano.',
          style: TextStyle(fontSize: 15),
        ),
        if (ultimo != null) ...[
          const Divider(height: 24),
          FilaDato('Método', ultimo.metodo.etiqueta),
          if (ultimo.tempCuarto != null)
            FilaDato('Cuarto', '${ultimo.tempCuarto} °C'),
          if (ultimo.hrCuarto != null)
            FilaDato('Humedad', '${ultimo.hrCuarto} %'),
          if (ultimo.pruebaPapel != null)
            FilaDato('Prueba del papel',
                ultimo.pruebaPapel! ? 'Pasó' : 'No pasó',
                color: ultimo.pruebaPapel!
                    ? ColoresEstado.bien
                    : ColoresEstado.atencion),
          if (ultimo.resultadoUsuario.isNotEmpty)
            FilaDato('Barras', nombreBonito(ultimo.resultadoUsuario)),
        ],
        const SizedBox(height: 12),
        BotonGrande(
          texto: 'Asistente de atemperado',
          icono: Icons.play_circle_outline,
          onPressed: () => _asistente(context),
        ),
      ],
    );
  }

  Future<void> _asistente(BuildContext context) async {
    final s = ProveedorServicios.de(context);
    final tempCuarto = TextEditingController();
    final hrCuarto = TextEditingController();
    final tempFundido = TextEditingController(text: '48');
    final tempEnfriado = TextEditingController(text: '27');
    final tempTrabajo = TextEditingController(text: '31.5');
    var metodo = MetodoAtemperado.siembra;
    bool? pruebaPapel;
    String? fotoRuta;
    var resultadoUsuario = '';
    var resultadoIa = '';
    double? confianzaIa;
    var modeloVersion = '';
    String? advertencia;

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
                Text('Asistente de atemperado',
                    style: Theme.of(contexto).textTheme.titleLarge),
                const SizedBox(height: 12),
                const Text('Condiciones del cuarto',
                    style:
                        TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
                CampoNumero(
                  etiqueta: 'Temperatura del cuarto',
                  controlador: tempCuarto,
                  unidad: '°C',
                  onChanged: (_) async {
                    final aviso = await s.produccion.advertenciaDelCuarto(
                      tempCuarto: leerNumero(tempCuarto),
                      hrCuarto: leerNumero(hrCuarto),
                    );
                    actualizar(() => advertencia = aviso);
                  },
                ),
                CampoNumero(
                  etiqueta: 'Humedad del cuarto',
                  controlador: hrCuarto,
                  unidad: '%',
                  maximo: 100,
                  onChanged: (_) async {
                    final aviso = await s.produccion.advertenciaDelCuarto(
                      tempCuarto: leerNumero(tempCuarto),
                      hrCuarto: leerNumero(hrCuarto),
                    );
                    actualizar(() => advertencia = aviso);
                  },
                ),
                if (advertencia != null) Aviso(texto: advertencia!),
                const SizedBox(height: 12),
                const Text('Método',
                    style:
                        TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
                Wrap(
                  spacing: 8,
                  children: [
                    for (final m in MetodoAtemperado.values)
                      ChoiceChip(
                        label: Text(m.etiqueta),
                        selected: metodo == m,
                        onSelected: (_) => actualizar(() => metodo = m),
                      ),
                  ],
                ),
                const SizedBox(height: 12),
                const Text('Las tres temperaturas',
                    style:
                        TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
                const Text(
                  '1) Funde todo. 2) Baja la temperatura removiendo. '
                  '3) Sube un poco para trabajar.',
                  style: TextStyle(fontSize: 14),
                ),
                CampoNumero(
                    etiqueta: '1. Fundido',
                    controlador: tempFundido,
                    unidad: '°C'),
                CampoNumero(
                    etiqueta: '2. Enfriado',
                    controlador: tempEnfriado,
                    unidad: '°C'),
                CampoNumero(
                  etiqueta: '3. Trabajo',
                  controlador: tempTrabajo,
                  unidad: '°C',
                  ayuda: 'El chocolate negro se moldea entre 31 y 32 °C',
                ),
                const SizedBox(height: 12),
                const Text('Prueba del papel',
                    style:
                        TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
                const Text(
                  'Moja la punta de un papel y déjalo a temperatura ambiente: '
                  'si endurece con brillo en unos 3 minutos, está bien.',
                  style: TextStyle(fontSize: 14),
                ),
                Row(
                  children: [
                    Expanded(
                      child: RadioListTile<bool>(
                        value: true,
                        // ignore: deprecated_member_use
                        groupValue: pruebaPapel,
                        title: const Text('Pasó'),
                        contentPadding: EdgeInsets.zero,
                        // ignore: deprecated_member_use
                        onChanged: (v) => actualizar(() => pruebaPapel = v),
                      ),
                    ),
                    Expanded(
                      child: RadioListTile<bool>(
                        value: false,
                        // ignore: deprecated_member_use
                        groupValue: pruebaPapel,
                        title: const Text('No pasó'),
                        contentPadding: EdgeInsets.zero,
                        // ignore: deprecated_member_use
                        onChanged: (v) => actualizar(() => pruebaPapel = v),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                BotonFotoIa(
                  tarea: 'chocolate',
                  clases: clasesChocolate,
                  texto: 'Fotografiar las barras',
                  consejo:
                      'Con luz lateral se ve mejor el brillo y el velo blanco.',
                  onCaptura: (captura) => actualizar(() {
                    resultadoUsuario = captura.decisionUsuario;
                    fotoRuta = captura.archivo.path;
                    resultadoIa = captura.resultado?.mejor?.clase ?? '';
                    confianzaIa = captura.resultado?.mejor?.confianza;
                    modeloVersion = captura.resultado?.modeloVersion ?? '';
                  }),
                ),
                const SizedBox(height: 16),
                BotonGrande(
                  texto: 'Guardar el atemperado',
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
    String? fotoId;
    if (fotoRuta != null) {
      fotoId = await s.apoyo.guardarFoto(
        rutaLocal: fotoRuta!,
        etapa: 'atemperado',
        loteProduccionId: completa.produccion.id,
        analisisIa: {'clase': resultadoIa, 'confianza': confianzaIa},
        etiquetaUsuario: resultadoUsuario,
        aptaDataset: resultadoUsuario.isNotEmpty,
      );
    }

    final alertas = await s.produccion.guardarAtemperado(
      loteProduccionId: completa.produccion.id,
      metodo: metodo,
      temperaturas: {
        'fundido': leerNumero(tempFundido) ?? 0,
        'enfriado': leerNumero(tempEnfriado) ?? 0,
        'trabajo': leerNumero(tempTrabajo) ?? 0,
      },
      tempCuarto: leerNumero(tempCuarto),
      hrCuarto: leerNumero(hrCuarto),
      pruebaPapel: pruebaPapel,
      resultadoIa: resultadoIa,
      confianzaIa: confianzaIa,
      resultadoUsuario: resultadoUsuario,
      modeloVersion: modeloVersion,
      fotoId: fotoId,
    );
    if (!context.mounted) return;
    avisar(
      context,
      alertas.isEmpty
          ? 'Atemperado guardado'
          : '${alertas.first.quePaso}. ${alertas.first.queHacer}',
      error: alertas.isNotEmpty,
    );
    alCambiar();
  }
}

class _SeccionEmpaque extends StatelessWidget {
  const _SeccionEmpaque({required this.completa, required this.alCambiar});
  final ProduccionCompleta completa;
  final VoidCallback alCambiar;

  @override
  Widget build(BuildContext context) {
    final e = completa.empaque;
    return TarjetaSeccion(
      titulo: 'Empaque',
      icono: Icons.inventory_outlined,
      hijos: [
        if (e == null)
          const Text(
            'Registra cuántas barras salieron y de qué peso. La fecha de '
            'vencimiento se calcula sola.',
            style: TextStyle(fontSize: 15),
          )
        else ...[
          FilaDato('Barras', '${e.barras}'),
          FilaDato('Peso unitario', '${e.pesoUnitarioG} g'),
          FilaDato('Elaboración', _fecha(e.fechaElaboracion)),
          FilaDato('Vencimiento', _fecha(e.fechaVencimiento)),
        ],
        const SizedBox(height: 12),
        BotonGrande(
          texto: e == null ? 'Registrar el empaque' : 'Editar',
          icono: Icons.edit_outlined,
          onPressed: () => _editar(context),
        ),
      ],
    );
  }

  Future<void> _editar(BuildContext context) async {
    final s = ProveedorServicios.de(context);
    final barras = TextEditingController(
        text: completa.empaque?.barras.toString() ?? '');
    final peso = TextEditingController(
        text: completa.empaque?.pesoUnitarioG.toString() ?? '50');
    final vida = TextEditingController(
        text: completa.empaque?.vidaUtilMeses.toString() ?? '12');

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
            Text('Empaque', style: Theme.of(contexto).textTheme.titleLarge),
            CampoNumero(
                etiqueta: 'Barras',
                controlador: barras,
                decimales: false,
                obligatorio: true),
            CampoNumero(
                etiqueta: 'Peso por barra',
                controlador: peso,
                unidad: 'g',
                obligatorio: true),
            CampoNumero(
              etiqueta: 'Vida útil',
              controlador: vida,
              unidad: 'meses',
              decimales: false,
              ayuda: 'Un chocolate negro bien empacado suele durar 12 meses',
            ),
            const SizedBox(height: 16),
            BotonGrande(
              texto: 'Guardar',
              icono: Icons.save_outlined,
              onPressed: () => Navigator.pop(contexto, true),
            ),
          ],
        ),
      ),
    );

    if (guardar != true || !context.mounted) return;
    await s.produccion.guardarEmpaque(
      loteProduccionId: completa.produccion.id,
      barras: leerNumero(barras)?.round() ?? 0,
      pesoUnitarioG: leerNumero(peso) ?? 50,
      vidaUtilMeses: leerNumero(vida)?.round() ?? 12,
    );
    if (!context.mounted) return;
    avisar(context, 'Empaque guardado');
    alCambiar();
  }
}

String _fecha(DateTime f) =>
    '${f.day.toString().padLeft(2, '0')}/'
    '${f.month.toString().padLeft(2, '0')}/${f.year}';
