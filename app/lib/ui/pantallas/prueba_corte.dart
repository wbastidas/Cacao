/// Prueba de corte (RF-PRC-01 a RF-PRC-10).
///
/// Es la pantalla que decide el grado del lote, así que tiene dos caminos que
/// llegan al mismo sitio: la foto del tablero con el modelo M2, y el conteo
/// manual con botones grandes. El manual no es un plan B de segunda: es el
/// que se usa mientras el modelo no alcance sus metas, y tiene que ser cómodo.
library;

import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../../ia/resultado_ia.dart';
import '../../nucleo/norma/calificador_corte.dart';
import '../../nucleo/norma/tabla_norma.dart';
import '../../servicios.dart';
import '../comun/camara_ia.dart';
import '../comun/widgets.dart';
import '../tema.dart';
import 'tablero_pdf.dart';

class PantallaPruebaCorte extends StatefulWidget {
  const PantallaPruebaCorte({super.key, required this.loteId});
  final String loteId;

  @override
  State<PantallaPruebaCorte> createState() => _PantallaPruebaCorteState();
}

class _PantallaPruebaCorteState extends State<PantallaPruebaCorte> {
  TablaNorma? _tabla;
  Map<String, int> _conteo = {};
  ResultadoDeteccion? _deteccion;
  File? _foto;
  String _perfil = CalificadorCorte.perfilPorDefecto;
  final _peso100 = TextEditingController();
  bool _esParcial = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_tabla == null) _cargarNorma();
  }

  Future<void> _cargarNorma() async {
    final s = ProveedorServicios.de(context);
    final tabla = await s.config.norma();
    if (mounted) {
      setState(() {
        _tabla = tabla;
        _conteo = {for (final c in tabla.clasesContables) c: 0};
      });
    }
  }

  int get _total => _conteo.values.fold<int>(0, (a, b) => a + b);

  ResultadoCorte? get _resultado {
    if (_tabla == null || _total == 0) return null;
    try {
      return CalificadorCorte(_tabla!).calificar(_conteo, perfil: _perfil);
    } on ErrorNorma {
      return null;
    }
  }

  @override
  void dispose() {
    _peso100.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final tabla = _tabla;
    if (tabla == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('Prueba de corte'),
        actions: [
          IconButton(
            tooltip: 'Cómo se hace',
            icon: const Icon(Icons.help_outline),
            onPressed: () => _mostrarGuia(context),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          _Guia(onPlantilla: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => const PantallaTableroPdf()),
              )),
          const SizedBox(height: 8),
          _BloqueFoto(
            foto: _foto,
            deteccion: _deteccion,
            clases: tabla.clasesContables,
            onTomarFoto: _tomarFoto,
            onCorregirGrano: (i, clase) {
              setState(() {
                _deteccion = _deteccion!.conGranoCorregido(i, clase);
                _conteo = {
                  for (final c in tabla.clasesContables) c: 0,
                  ..._deteccion!.conteo,
                };
              });
            },
          ),
          const SizedBox(height: 8),
          TarjetaSeccion(
            titulo: 'Conteo por clase',
            icono: Icons.grid_on,
            accion: Text('$_total granos',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w700,
                  color: _total < 97 || _total > 103
                      ? ColoresEstado.atencion
                      : ColoresEstado.bien,
                )),
            hijos: [
              if (_total > 0 && (_total < 97 || _total > 103))
                Aviso(
                  texto: 'Llevas $_total granos y la norma usa 100. Revisa el '
                      'conteo antes de dar el resultado por bueno.',
                ),
              for (final clase in tabla.clasesContables)
                _ContadorGrano(
                  clase: clase,
                  valor: _conteo[clase] ?? 0,
                  onCambio: (v) => setState(() => _conteo[clase] = v),
                ),
              const SizedBox(height: 8),
              TextButton.icon(
                icon: const Icon(Icons.restart_alt),
                label: const Text('Empezar el conteo de cero'),
                onPressed: () => setState(() {
                  _conteo = {for (final c in tabla.clasesContables) c: 0};
                  _deteccion = null;
                }),
              ),
            ],
          ),
          _BloqueResultado(
            resultado: _resultado,
            perfil: _perfil,
            perfiles: tabla.perfiles,
            onPerfil: (p) => setState(() => _perfil = p),
          ),
          TarjetaSeccion(
            titulo: 'Datos adicionales',
            icono: Icons.scale_outlined,
            hijos: [
              CampoNumero(
                etiqueta: 'Peso de 100 granos',
                controlador: _peso100,
                unidad: 'g',
                ayuda: 'Un grano de buen tamaño pesa alrededor de 1,2 g',
                minimo: 0,
              ),
              SwitchListTile(
                value: _esParcial,
                contentPadding: EdgeInsets.zero,
                title: const Text('Es una prueba parcial'),
                subtitle: const Text(
                  'Por ejemplo, la del día 5 de fermentación con 20-50 granos. '
                  'No define el grado del lote.',
                  style: TextStyle(fontSize: 14),
                ),
                onChanged: (v) => setState(() => _esParcial = v),
              ),
            ],
          ),
          const SizedBox(height: 16),
          BotonGrande(
            texto: 'Guardar la prueba',
            icono: Icons.save_outlined,
            onPressed: _resultado == null ? null : _guardar,
          ),
          const SizedBox(height: 8),
          const Text(
            'El grado con validez comercial lo define la norma oficial y un '
            'catador certificado. Esta app te ayuda a llevar el control.',
            style: TextStyle(fontSize: 13),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  Future<void> _tomarFoto() async {
    final s = ProveedorServicios.de(context);
    final elegida = await ImagePicker().pickImage(
      source: ImageSource.camera,
      maxWidth: 1600,
      maxHeight: 1600,
      imageQuality: 90,
    );
    if (elegida == null || !mounted) return;
    final archivo = File(elegida.path);
    setState(() => _foto = archivo);

    if (!s.modelos.estaDisponible('corte')) {
      if (mounted) {
        avisar(
          context,
          'La foto se guardó. Todavía no hay modelo de prueba de corte: '
          'cuenta con los botones de abajo.',
        );
      }
      return;
    }

    try {
      final Uint8List? pixeles = await prepararParaModelo(archivo, 640);
      if (pixeles == null) return;
      final deteccion = await s.modelos.detectarGranos('corte', pixeles);
      if (!mounted) return;
      setState(() {
        _deteccion = deteccion;
        _conteo = {
          for (final c in _tabla!.clasesContables) c: 0,
          ...deteccion.conteo,
        };
      });
      avisar(
        context,
        deteccion.conteoDudoso
            ? 'Se detectaron ${deteccion.total} granos. Revisa el conteo.'
            : 'Se detectaron ${deteccion.total} granos en '
                '${deteccion.milisegundos} ms',
        error: deteccion.conteoDudoso,
      );
    } catch (e) {
      if (mounted) avisar(context, '$e', error: true);
    }
  }

  Future<void> _guardar() async {
    final s = ProveedorServicios.de(context);
    final resultado = _resultado!;

    String? fotoId;
    if (_foto != null) {
      fotoId = await s.apoyo.guardarFoto(
        rutaLocal: _foto!.path,
        etapa: 'prueba_corte',
        loteId: widget.loteId,
        analisisIa: _deteccion == null
            ? const {}
            : {
                'conteo': _deteccion!.conteo,
                'modelo': _deteccion!.modeloVersion,
              },
        // RF-PRC-09: la foto con las correcciones del usuario sirve para
        // reentrenar el modelo.
        aptaDataset: true,
      );
    }

    await s.lotes.guardarPruebaCorte(
      loteId: widget.loteId,
      conteo: _conteo,
      resultado: resultado,
      peso100g: leerNumero(_peso100),
      modeloVersion: _deteccion?.modeloVersion ?? '',
      fotoId: fotoId,
      esParcial: _esParcial,
    );

    if (!mounted) return;
    avisar(context, 'Prueba guardada: ${resultado.resultado}');
    Navigator.pop(context);
  }

  void _mostrarGuia(BuildContext context) {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (contexto) => const _GuiaCompleta(),
    );
  }
}

class _Guia extends StatelessWidget {
  const _Guia({required this.onPlantilla});
  final VoidCallback onPlantilla;

  @override
  Widget build(BuildContext context) {
    return TarjetaSeccion(
      titulo: 'Cómo se hace',
      icono: Icons.checklist,
      hijos: [
        const Text(
          '1. Toma 100 granos al azar del saco, sin escoger.\n'
          '2. Córtalos a lo largo, por la mitad.\n'
          '3. Ponlos en el tablero de 10 × 10 con la cara cortada arriba.\n'
          '4. Fotografía de frente, con buena luz y sin flash.',
          style: TextStyle(fontSize: 16),
        ),
        const SizedBox(height: 8),
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton.icon(
            icon: const Icon(Icons.print_outlined),
            label: const Text('Imprimir la plantilla del tablero'),
            onPressed: onPlantilla,
          ),
        ),
      ],
    );
  }
}

class _GuiaCompleta extends StatelessWidget {
  const _GuiaCompleta();

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('La prueba de corte, paso a paso',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 16),
            const Text(
              'Cortar el grano por la mitad deja ver el color de su interior, '
              'y ese color cuenta lo que pasó en la fermentación:\n\n'
              '· Café oscuro y con surcos: bien fermentado.\n'
              '· Café claro: ligeramente fermentado.\n'
              '· Violeta: le faltó fermentación.\n'
              '· Gris y compacto (pizarroso): no fermentó nada.\n'
              '· Con manchas blancas o verdes: moho.\n\n'
              'Los porcentajes de cada color deciden el grado del lote y, con '
              'él, el precio que te pagan.\n\n'
              'Toma los granos al azar, sin escoger los más bonitos: el '
              'resultado tiene que representar el saco entero.',
              style: TextStyle(fontSize: 16),
            ),
          ],
        ),
      ),
    );
  }
}

/// Foto del tablero con las cajas de cada grano encima (RF-PRC-03/04).
class _BloqueFoto extends StatelessWidget {
  const _BloqueFoto({
    required this.foto,
    required this.deteccion,
    required this.clases,
    required this.onTomarFoto,
    required this.onCorregirGrano,
  });

  final File? foto;
  final ResultadoDeteccion? deteccion;
  final List<String> clases;
  final VoidCallback onTomarFoto;
  final void Function(int, String) onCorregirGrano;

  @override
  Widget build(BuildContext context) {
    return TarjetaSeccion(
      titulo: 'Foto del tablero',
      icono: Icons.photo_camera_outlined,
      hijos: [
        if (foto != null)
          LayoutBuilder(
            builder: (context, restricciones) {
              final lado = restricciones.maxWidth;
              return SizedBox(
                width: lado,
                height: lado,
                child: Stack(
                  children: [
                    Positioned.fill(
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(12),
                        child: Image.file(foto!, fit: BoxFit.cover),
                      ),
                    ),
                    if (deteccion != null)
                      for (var i = 0; i < deteccion!.granos.length; i++)
                        Positioned(
                          left: deteccion!.granos[i].x * lado,
                          top: deteccion!.granos[i].y * lado,
                          width: deteccion!.granos[i].ancho * lado,
                          height: deteccion!.granos[i].alto * lado,
                          child: GestureDetector(
                            onTap: () =>
                                _corregir(context, i, deteccion!.granos[i]),
                            child: Container(
                              decoration: BoxDecoration(
                                border: Border.all(
                                  color: ColoresEstado.deGrano(
                                      deteccion!.granos[i].clase),
                                  width:
                                      deteccion!.granos[i].corregidoPorUsuario
                                          ? 3
                                          : 2,
                                ),
                                borderRadius: BorderRadius.circular(4),
                              ),
                            ),
                          ),
                        ),
                  ],
                ),
              );
            },
          ),
        if (deteccion != null) ...[
          const SizedBox(height: 8),
          const Text(
            'Toca un grano para corregir su clase. Tu corrección manda sobre '
            'lo que dijo el modelo.',
            style: TextStyle(fontSize: 14),
          ),
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            runSpacing: 4,
            children: [
              for (final e in deteccion!.conteo.entries)
                Chip(
                  avatar: CircleAvatar(
                      backgroundColor: ColoresEstado.deGrano(e.key), radius: 8),
                  label: Text('${nombreBonito(e.key)}: ${e.value}'),
                  visualDensity: VisualDensity.compact,
                ),
            ],
          ),
        ],
        const SizedBox(height: 12),
        BotonGrande(
          texto: foto == null ? 'Fotografiar el tablero' : 'Repetir la foto',
          icono: Icons.photo_camera_outlined,
          onPressed: onTomarFoto,
        ),
      ],
    );
  }

  Future<void> _corregir(
      BuildContext context, int indice, GranoDetectado grano) async {
    final nueva = await showModalBottomSheet<String>(
      context: context,
      builder: (contexto) => SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Este grano es…',
                  style: Theme.of(contexto).textTheme.titleLarge),
              Text(
                'El modelo dijo "${nombreBonito(grano.clase)}" con '
                '${(grano.confianza * 100).round()} % de seguridad.',
                style: const TextStyle(fontSize: 15),
              ),
              const SizedBox(height: 16),
              for (final c in clases)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(
                      icon: CircleAvatar(
                          backgroundColor: ColoresEstado.deGrano(c),
                          radius: 10),
                      label: Text(nombreBonito(c)),
                      onPressed: () => Navigator.pop(contexto, c),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
    if (nueva != null) onCorregirGrano(indice, nueva);
  }
}

class _ContadorGrano extends StatelessWidget {
  const _ContadorGrano({
    required this.clase,
    required this.valor,
    required this.onCambio,
  });

  final String clase;
  final int valor;
  final ValueChanged<int> onCambio;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        children: [
          CircleAvatar(
              backgroundColor: ColoresEstado.deGrano(clase), radius: 10),
          const SizedBox(width: 10),
          Expanded(
            child: Text(nombreBonito(clase),
                style: const TextStyle(fontSize: 16)),
          ),
          IconButton.filledTonal(
            onPressed: valor > 0 ? () => onCambio(valor - 1) : null,
            icon: const Icon(Icons.remove),
            iconSize: 24,
          ),
          SizedBox(
            width: 48,
            child: Text('$valor',
                textAlign: TextAlign.center,
                style: const TextStyle(
                    fontSize: 21, fontWeight: FontWeight.w700)),
          ),
          IconButton.filledTonal(
            onPressed: () => onCambio(valor + 1),
            icon: const Icon(Icons.add),
            iconSize: 24,
          ),
        ],
      ),
    );
  }
}

class _BloqueResultado extends StatelessWidget {
  const _BloqueResultado({
    required this.resultado,
    required this.perfil,
    required this.perfiles,
    required this.onPerfil,
  });

  final ResultadoCorte? resultado;
  final String perfil;
  final Map<String, PerfilNorma> perfiles;
  final ValueChanged<String> onPerfil;

  @override
  Widget build(BuildContext context) {
    final r = resultado;

    return TarjetaSeccion(
      titulo: 'Resultado',
      icono: Icons.workspace_premium_outlined,
      hijos: [
        DropdownButtonFormField<String>(
          initialValue: perfil,
          decoration: const InputDecoration(labelText: 'Tabla de la norma'),
          items: [
            for (final e in perfiles.entries)
              DropdownMenuItem(
                value: e.key,
                child: Text(e.key == 'ccn51_referencia'
                    ? 'CCN-51 (conforme / no conforme)'
                    : 'Grados 1, 2 y 3'),
              ),
          ],
          onChanged: (v) => v == null ? null : onPerfil(v),
        ),
        const SizedBox(height: 16),
        if (r == null)
          const Text(
            'Cuenta al menos un grano para ver el resultado.',
            style: TextStyle(fontSize: 16),
          )
        else ...[
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: (r.conforme ? ColoresEstado.bien : ColoresEstado.atencion)
                  // ignore: deprecated_member_use
                  .withOpacity(0.15),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Column(
              children: [
                Icon(
                  r.conforme ? Icons.verified : Icons.error_outline,
                  size: 36,
                  color: r.conforme
                      ? ColoresEstado.bien
                      : ColoresEstado.atencion,
                ),
                const SizedBox(height: 8),
                Text(
                  r.resultado,
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                      fontSize: 22, fontWeight: FontWeight.w700),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          for (final indicador in const [
            'fermentado_bueno',
            'fermentado_ligero',
            'fermentado_total',
            'violeta',
            'pizarroso',
            'mohoso',
            'defectuoso',
          ])
            FilaDato(
              nombreBonito(indicador),
              '${r.porcentajes[indicador].toStringAsFixed(1)} %',
            ),
          if (r.todasLasFallas.isNotEmpty) ...[
            const Divider(height: 24),
            const Text('Qué falta para cumplir',
                style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
            const SizedBox(height: 8),
            for (final falla in r.todasLasFallas)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 3),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(Icons.close,
                        size: 18, color: ColoresEstado.atencion),
                    const SizedBox(width: 8),
                    Expanded(
                        child: Text(falla,
                            style: const TextStyle(fontSize: 15))),
                  ],
                ),
              ),
          ],
          for (final aviso in r.avisos)
            Aviso(texto: aviso, icono: Icons.info_outline),
        ],
      ],
    );
  }
}
