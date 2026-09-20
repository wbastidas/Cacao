/// Ajustes: umbrales, norma, fincas, equipos, sincronización y modelos de IA
/// (RF-CFG-01 a RF-CFG-04, RF-IA-06, RNF-12).
library;

import 'package:drift/drift.dart' show Value;
import 'package:flutter/material.dart';

import '../../datos/bd/base_datos.dart';
import '../../datos/bd/tablas/comunes.dart';
import '../../datos/sync/sincronizador_remoto.dart';
import '../../ia/servicio_modelos.dart';
import '../../nucleo/reglas/umbrales.dart';
import '../../servicios.dart';
import '../comun/camara_ia.dart';
import '../comun/widgets.dart';
import '../tema.dart';

class PantallaAjustes extends StatelessWidget {
  const PantallaAjustes({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Ajustes')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: const [
          _Acceso(
            titulo: 'Umbrales',
            subtitulo: 'Cuándo debe avisarte la app',
            icono: Icons.tune,
            destino: PantallaUmbrales(),
          ),
          _Acceso(
            titulo: 'Fincas',
            subtitulo: 'De dónde viene el cacao',
            icono: Icons.agriculture_outlined,
            destino: PantallaFincas(),
          ),
          _Acceso(
            titulo: 'Equipos',
            subtitulo: 'Fermentador, marquesina, horno, melanger',
            icono: Icons.precision_manufacturing_outlined,
            destino: PantallaEquipos(),
          ),
          _Acceso(
            titulo: 'Sincronización y respaldo',
            subtitulo: 'Dónde se guardan tus datos',
            icono: Icons.cloud_outlined,
            destino: PantallaSincronizacion(),
          ),
          _Acceso(
            titulo: 'Modelos de inteligencia artificial',
            subtitulo: 'Qué está instalado y qué tan bueno es',
            icono: Icons.psychology_outlined,
            destino: PantallaModelos(),
          ),
          _Acceso(
            titulo: 'Acerca de CacaoTrace',
            subtitulo: 'Versión, norma y avisos legales',
            icono: Icons.info_outline,
            destino: PantallaAcercaDe(),
          ),
        ],
      ),
    );
  }
}

class _Acceso extends StatelessWidget {
  const _Acceso({
    required this.titulo,
    required this.subtitulo,
    required this.icono,
    required this.destino,
  });

  final String titulo;
  final String subtitulo;
  final IconData icono;
  final Widget destino;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: Icon(icono, size: 30),
        title: Text(titulo),
        subtitle: Text(subtitulo),
        trailing: const Icon(Icons.chevron_right),
        onTap: () => Navigator.push(
            context, MaterialPageRoute(builder: (_) => destino)),
      ),
    );
  }
}

/// RF-CFG-03: todos los umbrales de la tabla 5.1, editables.
class PantallaUmbrales extends StatefulWidget {
  const PantallaUmbrales({super.key});

  @override
  State<PantallaUmbrales> createState() => _PantallaUmbralesState();
}

class _PantallaUmbralesState extends State<PantallaUmbrales> {
  Umbrales? _vigentes;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _cargar();
  }

  Future<void> _cargar() async {
    final s = ProveedorServicios.de(context);
    final u = await s.config.umbrales();
    if (mounted) setState(() => _vigentes = u);
  }

  @override
  Widget build(BuildContext context) {
    final vigentes = _vigentes;

    return Scaffold(
      appBar: AppBar(title: const Text('Umbrales')),
      body: vigentes == null
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                const Text(
                  'Estos valores deciden cuándo la app te avisa. Cámbialos '
                  'cuando tu experiencia te diga otra cosa: la app se adapta '
                  'a ti, no al revés.',
                  style: TextStyle(fontSize: 15),
                ),
                const SizedBox(height: 16),
                for (final u in CatalogoUmbrales.todos)
                  _FilaUmbral(
                    definicion: u,
                    valor: vigentes[u.clave],
                    alCambiar: _cargar,
                  ),
              ],
            ),
    );
  }
}

class _FilaUmbral extends StatelessWidget {
  const _FilaUmbral({
    required this.definicion,
    required this.valor,
    required this.alCambiar,
  });

  final Umbral definicion;
  final double valor;
  final VoidCallback alCambiar;

  @override
  Widget build(BuildContext context) {
    final modificado = valor != definicion.porDefecto;

    return Card(
      child: ListTile(
        title: Text(definicion.etiqueta),
        subtitle: Text(definicion.explicacion,
            style: const TextStyle(fontSize: 14)),
        trailing: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text(
              '${_formatear(valor)} ${definicion.unidad}',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w700,
                color: modificado ? ColoresEstado.atencion : null,
              ),
            ),
            if (modificado)
              const Text('modificado', style: TextStyle(fontSize: 11)),
          ],
        ),
        onTap: () => _editar(context),
      ),
    );
  }

  Future<void> _editar(BuildContext context) async {
    final s = ProveedorServicios.de(context);
    final controlador = TextEditingController(text: _formatear(valor));

    final accion = await showDialog<String>(
      context: context,
      builder: (contexto) => AlertDialog(
        title: Text(definicion.etiqueta),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(definicion.explicacion,
                style: const TextStyle(fontSize: 15)),
            const SizedBox(height: 16),
            CampoNumero(
              etiqueta: 'Valor',
              controlador: controlador,
              unidad: definicion.unidad,
              minimo: definicion.minimo,
              maximo: definicion.maximo,
            ),
            Text(
              'De fábrica: ${_formatear(definicion.porDefecto)} '
              '${definicion.unidad}',
              style: const TextStyle(fontSize: 13),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(contexto, 'restaurar'),
            child: const Text('De fábrica'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(contexto),
            child: const Text('Cancelar'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(contexto, 'guardar'),
            child: const Text('Guardar'),
          ),
        ],
      ),
    );

    if (accion == 'restaurar') {
      await s.config.restaurarUmbral(definicion.clave);
      alCambiar();
      return;
    }
    if (accion != 'guardar') return;

    final nuevo = leerNumero(controlador);
    if (nuevo == null) return;
    try {
      await s.config.guardarUmbral(definicion.clave, nuevo);
      alCambiar();
      if (context.mounted) avisar(context, 'Umbral actualizado');
    } catch (e) {
      if (context.mounted) avisar(context, '$e', error: true);
    }
  }
}

String _formatear(double v) =>
    v == v.roundToDouble() ? v.toStringAsFixed(0) : v.toString();

/// RF-CFG-01: fincas de origen.
class PantallaFincas extends StatelessWidget {
  const PantallaFincas({super.key});

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Fincas')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _editar(context, null),
        icon: const Icon(Icons.add),
        label: const Text('Nueva finca'),
      ),
      body: StreamBuilder<List<Finca>>(
        stream: s.config.observarFincas(),
        builder: (context, snapshot) {
          final fincas = snapshot.data ?? const [];
          if (fincas.isEmpty) {
            return const EstadoVacio(
              icono: Icons.agriculture_outlined,
              titulo: 'Sin fincas registradas',
              explicacion:
                  'Registrar la finca de origen es lo que permite responder '
                  '"de dónde vino este chocolate" cuando un cliente lo '
                  'pregunte.',
            );
          }
          return ListView(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
            children: [
              for (final f in fincas)
                Card(
                  child: ListTile(
                    leading: const Icon(Icons.place_outlined),
                    title: Text(f.nombre),
                    subtitle: Text([
                      if (f.provincia.isNotEmpty) f.provincia,
                      if (f.canton.isNotEmpty) f.canton,
                      f.variedad,
                    ].join(' · ')),
                    trailing: IconButton(
                      icon: const Icon(Icons.edit_outlined),
                      onPressed: () => _editar(context, f),
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }

  Future<void> _editar(BuildContext context, Finca? finca) async {
    final s = ProveedorServicios.de(context);
    final nombre = TextEditingController(text: finca?.nombre ?? '');
    final provincia = TextEditingController(text: finca?.provincia ?? '');
    final canton = TextEditingController(text: finca?.canton ?? '');
    final contacto = TextEditingController(text: finca?.contacto ?? '');
    final variedad =
        TextEditingController(text: finca?.variedad ?? 'CCN-51');

    final guardar = await showModalBottomSheet<bool>(
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
              Text(finca == null ? 'Nueva finca' : 'Editar finca',
                  style: Theme.of(contexto).textTheme.titleLarge),
              TextFormField(
                controller: nombre,
                decoration: const InputDecoration(labelText: 'Nombre'),
              ),
              TextFormField(
                controller: provincia,
                decoration: const InputDecoration(labelText: 'Provincia'),
              ),
              TextFormField(
                controller: canton,
                decoration: const InputDecoration(labelText: 'Cantón'),
              ),
              TextFormField(
                controller: variedad,
                decoration: const InputDecoration(labelText: 'Variedad'),
              ),
              TextFormField(
                controller: contacto,
                decoration: const InputDecoration(labelText: 'Contacto'),
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
    );

    if (guardar != true || nombre.text.trim().isEmpty) return;
    await s.config.guardarFinca(FincasCompanion.insert(
      id: Value(finca?.id ?? uuidGenerador.v4()),
      nombre: nombre.text.trim(),
      provincia: Value(provincia.text.trim()),
      canton: Value(canton.text.trim()),
      variedad: Value(variedad.text.trim()),
      contacto: Value(contacto.text.trim()),
      modificadoEn: Value(DateTime.now()),
    ));
    if (context.mounted) avisar(context, 'Finca guardada');
  }
}

/// RF-CFG-02: equipos del taller.
class PantallaEquipos extends StatelessWidget {
  const PantallaEquipos({super.key});

  static const tipos = [
    'fermentador',
    'marquesina',
    'horno',
    'melanger',
    'molde',
    'otro',
  ];

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Equipos')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _editar(context),
        icon: const Icon(Icons.add),
        label: const Text('Nuevo equipo'),
      ),
      body: StreamBuilder<List<Equipo>>(
        stream: s.config.observarEquipos(),
        builder: (context, snapshot) {
          final equipos = snapshot.data ?? const [];
          if (equipos.isEmpty) {
            return const EstadoVacio(
              icono: Icons.precision_manufacturing_outlined,
              titulo: 'Sin equipos registrados',
              explicacion:
                  'Las medidas del fermentador importan: uno demasiado grande '
                  'para poca baba enfría la masa y arruina la fermentación.',
            );
          }
          return ListView(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 96),
            children: [
              for (final e in equipos)
                Card(
                  child: ListTile(
                    leading: const Icon(Icons.build_outlined),
                    title: Text(e.nombre),
                    subtitle: Text([
                      nombreBonito(e.tipo),
                      if (e.capacidadKg != null) '${e.capacidadKg} kg',
                      if (e.largoCm != null)
                        '${e.largoCm}×${e.anchoCm}×${e.altoCm} cm',
                    ].join(' · ')),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }

  Future<void> _editar(BuildContext context) async {
    final s = ProveedorServicios.de(context);
    final nombre = TextEditingController();
    final capacidad = TextEditingController();
    final largo = TextEditingController();
    final ancho = TextEditingController();
    final alto = TextEditingController();
    final material = TextEditingController();
    var tipo = tipos.first;

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
                Text('Nuevo equipo',
                    style: Theme.of(contexto).textTheme.titleLarge),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  children: [
                    for (final t in tipos)
                      ChoiceChip(
                        label: Text(nombreBonito(t)),
                        selected: tipo == t,
                        onSelected: (_) => actualizar(() => tipo = t),
                      ),
                  ],
                ),
                TextFormField(
                  controller: nombre,
                  decoration: const InputDecoration(labelText: 'Nombre'),
                ),
                CampoNumero(
                    etiqueta: 'Capacidad',
                    controlador: capacidad,
                    unidad: 'kg'),
                Row(
                  children: [
                    Expanded(
                        child: CampoNumero(
                            etiqueta: 'Largo',
                            controlador: largo,
                            unidad: 'cm')),
                    const SizedBox(width: 8),
                    Expanded(
                        child: CampoNumero(
                            etiqueta: 'Ancho',
                            controlador: ancho,
                            unidad: 'cm')),
                    const SizedBox(width: 8),
                    Expanded(
                        child: CampoNumero(
                            etiqueta: 'Alto',
                            controlador: alto,
                            unidad: 'cm')),
                  ],
                ),
                TextFormField(
                  controller: material,
                  decoration: const InputDecoration(
                      labelText: 'Material',
                      hintText: 'Madera de laurel, acero inoxidable…'),
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

    if (guardar != true || nombre.text.trim().isEmpty) return;
    await s.config.guardarEquipo(EquiposCompanion.insert(
      id: Value(uuidGenerador.v4()),
      tipo: tipo,
      nombre: nombre.text.trim(),
      capacidadKg: Value(leerNumero(capacidad)),
      largoCm: Value(leerNumero(largo)),
      anchoCm: Value(leerNumero(ancho)),
      altoCm: Value(leerNumero(alto)),
      material: Value(material.text.trim()),
    ));
    if (context.mounted) avisar(context, 'Equipo guardado');
  }
}

class PantallaSincronizacion extends StatefulWidget {
  const PantallaSincronizacion({super.key});

  @override
  State<PantallaSincronizacion> createState() =>
      _PantallaSincronizacionState();
}

class _PantallaSincronizacionState extends State<PantallaSincronizacion> {
  Future<EstadoSincronizacion>? _estado;
  bool? _soloWifi;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _cargar();
  }

  Future<void> _cargar() async {
    final s = ProveedorServicios.de(context);
    final soloWifi = await s.config.subirFotosSoloConWifi();
    if (!mounted) return;
    setState(() {
      _estado = s.sync.estadoActual();
      _soloWifi = soloWifi;
    });
  }

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Sincronización')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          FutureBuilder<EstadoSincronizacion>(
            future: _estado,
            builder: (context, snapshot) {
              final estado = snapshot.data;
              return TarjetaSeccion(
                titulo: 'Estado',
                icono: Icons.cloud_outlined,
                hijos: [
                  FilaDato('Ahora mismo', estado?.etiqueta ?? '…'),
                  FilaDato('Pendientes', '${estado?.pendientes ?? 0}'),
                  FilaDato('Destino', s.sync.remoto.nombre),
                  if (estado?.ultimaSincronizacion != null)
                    FilaDato(
                      'Última vez',
                      '${estado!.ultimaSincronizacion!.hour}:'
                          '${estado.ultimaSincronizacion!.minute.toString().padLeft(2, '0')}',
                    ),
                ],
              );
            },
          ),
          const Aviso(
            icono: Icons.info_outline,
            color: ColoresEstado.neutro,
            titulo: 'Todo se guarda primero en el teléfono',
            texto:
                'La app funciona completa sin internet. Cuando haya señal, '
                'los registros se suben solos. Mientras no configures una '
                'cuenta de nube, tus datos viven únicamente en este teléfono: '
                'si lo pierdes, se pierden.',
          ),
          SwitchListTile(
            value: _soloWifi ?? true,
            title: const Text('Subir fotos solo con WiFi'),
            subtitle: const Text(
              'Las fotos pesan mucho más que los registros. Con esto activado, '
              'esperan al WiFi y no gastan tus datos móviles.',
              style: TextStyle(fontSize: 14),
            ),
            onChanged: (v) async {
              await s.config.cambiarSubirFotosSoloConWifi(v);
              s.sync.soloWifiParaFotos = v;
              _cargar();
            },
          ),
          const SizedBox(height: 16),
          BotonGrande(
            texto: 'Subir ahora lo pendiente',
            icono: Icons.cloud_upload_outlined,
            onPressed: () async {
              final n = await s.sync.sincronizar();
              if (!context.mounted) return;
              avisar(context,
                  n == 0 ? 'No había nada pendiente' : 'Se subieron $n');
              _cargar();
            },
          ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            icon: const Icon(Icons.refresh),
            label: const Text('Reintentar lo que falló'),
            onPressed: () async {
              await s.sync.reintentarTodo();
              _cargar();
            },
          ),
        ],
      ),
    );
  }
}

class PantallaModelos extends StatelessWidget {
  const PantallaModelos({super.key});

  @override
  Widget build(BuildContext context) {
    final s = ProveedorServicios.de(context);

    return Scaffold(
      appBar: AppBar(title: const Text('Modelos de IA')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const Aviso(
            icono: Icons.info_outline,
            color: ColoresEstado.neutro,
            titulo: 'La IA es de apoyo, no decide',
            texto:
                'Los modelos corren dentro del teléfono, sin internet. Tú '
                'siempre puedes corregir el resultado, y tu corrección es la '
                'que se guarda. El grado de calidad y el cadmio con validez '
                'legal dependen de la norma oficial y del laboratorio.',
          ),
          const SizedBox(height: 8),
          for (final entrada in ServicioModelos.tareas.entries)
            Card(
              child: ListTile(
                leading: Icon(
                  s.modelos.estaDisponible(entrada.key)
                      ? Icons.check_circle
                      : Icons.radio_button_unchecked,
                  color: s.modelos.estaDisponible(entrada.key)
                      ? ColoresEstado.bien
                      : ColoresEstado.neutro,
                  size: 30,
                ),
                title: Text(nombreBonito(entrada.value)),
                subtitle: Text(
                  s.modelos.estaDisponible(entrada.key)
                      ? 'Versión ${s.modelos.versionDe(entrada.key)} · '
                          '${s.modelos.clasesDe(entrada.key).length} clases'
                      : s.modelos
                              .porQueNoEstaDisponible(entrada.key)
                              ?.mensaje ??
                          'No instalado',
                ),
                isThreeLine: !s.modelos.estaDisponible(entrada.key),
              ),
            ),
          const SizedBox(height: 16),
          const TarjetaSeccion(
            titulo: 'Cómo se instalan',
            icono: Icons.download_outlined,
            hijos: [
              Text(
                'Los modelos se entrenan en una computadora con las fotos que '
                'tú vas tomando y corrigiendo en la app. El proceso completo '
                'está en la carpeta "entrenamiento" del proyecto.\n\n'
                'Mientras no haya modelo, la app funciona igual: registras y '
                'cuentas a mano, que es exactamente lo que se hace sin app.',
                style: TextStyle(fontSize: 16),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class PantallaAcercaDe extends StatelessWidget {
  const PantallaAcercaDe({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Acerca de')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: const [
          TarjetaSeccion(
            titulo: 'CacaoTrace',
            icono: Icons.eco_outlined,
            hijos: [
              Text(
                'Trazabilidad y control de calidad para la producción '
                'bean-to-bar de chocolate con cacao CCN-51.',
                style: TextStyle(fontSize: 16),
              ),
              SizedBox(height: 8),
              FilaDato('Versión', '1.0.0'),
            ],
          ),
          TarjetaSeccion(
            titulo: 'Sobre la norma',
            icono: Icons.gavel_outlined,
            hijos: [
              Text(
                'Los valores de la NTE INEN 176 incluidos en la app son de '
                'referencia y deben verificarse contra el texto oficial '
                'vigente antes de usarlos con fines comerciales. Puedes '
                'editarlos sin reinstalar la app.\n\n'
                'El grado de calidad y el contenido de cadmio con validez '
                'legal dependen de la norma oficial y de un laboratorio '
                'acreditado, no de esta aplicación.',
                style: TextStyle(fontSize: 16),
              ),
            ],
          ),
          TarjetaSeccion(
            titulo: 'Tus datos',
            icono: Icons.lock_outline,
            hijos: [
              Text(
                'Todo se guarda primero en este teléfono. Las fotos, cuando '
                'configures la nube, van a tu propio Google Drive, no a un '
                'servidor del desarrollador.\n\n'
                'Puedes borrar tu cuenta y tus datos cuando quieras.',
                style: TextStyle(fontSize: 16),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
