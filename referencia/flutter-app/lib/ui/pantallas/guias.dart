/// Guías por etapa y glosario (RF-GUI-01, RF-GUI-02).
///
/// Todo el contenido está dentro de la app: funciona sin internet, que es
/// justo cuando más se necesita, en la finca.
library;

import 'package:flutter/material.dart';

import '../comun/widgets.dart';

class Guia {
  const Guia({
    required this.etapa,
    required this.icono,
    required this.porQue,
    required this.pasos,
    required this.errores,
  });

  final String etapa;
  final IconData icono;

  /// Para qué sirve esta etapa. Sin esto, los pasos se olvidan.
  final String porQue;
  final List<String> pasos;

  /// Lo que suele salir mal.
  final List<String> errores;
}

const List<Guia> guias = [
  Guia(
    etapa: 'Recepción y reposo',
    icono: Icons.local_shipping_outlined,
    porQue:
        'Las mazorcas cerradas siguen madurando unos días. Ese reposo hace que '
        'la pulpa tenga más azúcar, y el azúcar es el combustible de la '
        'fermentación.',
    pasos: [
      'Recibe las mazorcas y cuéntalas.',
      'Aparta las que tengan monilia (polvo blanco) o fitóftora (mancha negra).',
      'Guárdalas a la sombra, en un sitio ventilado, sin apilarlas mucho.',
      'Déjalas de 3 a 6 días antes de abrirlas.',
    ],
    errores: [
      'Dejarlas más de una semana: empiezan a germinar por dentro.',
      'Guardar mazorcas enfermas junto a las sanas.',
      'Apilarlas al sol.',
    ],
  ),
  Guia(
    etapa: 'Apertura',
    icono: Icons.egg_outlined,
    porQue:
        'Aquí sacas el grano con su pulpa, que es lo que va a fermentar. La '
        'cantidad de masa decide si la fermentación se calienta o no.',
    pasos: [
      'Abre la mazorca con un golpe seco de machete o con un mazo de madera.',
      'Saca el grano con la mano, sin la placenta (el cordón central).',
      'Pesa la baba antes de meterla al fermentador.',
      'Si hay menos de 20 kg, añade cáscara troceada.',
    ],
    errores: [
      'Abrir con machete filoso y cortar los granos.',
      'Dejar la baba al sol más de una hora antes de fermentar.',
      'Mezclar granos de mazorcas enfermas.',
    ],
  ),
  Guia(
    etapa: 'Fermentación',
    icono: Icons.thermostat,
    porQue:
        'Es la etapa que crea el sabor a chocolate. Sin fermentación el grano '
        'sabe amargo y astringente, por muy bueno que sea el resto del '
        'proceso.',
    pasos: [
      'Pon la baba en el cajón, tápala con hojas y sacos.',
      'Los primeros dos días huele a alcohol: es normal.',
      'Voltea cada 24 horas, moviendo también las esquinas.',
      'Del día 2 al 4 huele a vinagre: también es normal.',
      'Mide la temperatura en el centro de la masa, una o dos veces al día.',
      'Termina entre el día 5 y el 6, cuando el grano se ve café por dentro.',
    ],
    errores: [
      'Poca masa: no se calienta y salen granos violetas o pizarrosos.',
      'No voltear: el lote fermenta disparejo.',
      'Pasarse de días: aparece olor a amoniaco y se arruina el sabor.',
    ],
  ),
  Guia(
    etapa: 'Secado',
    icono: Icons.wb_sunny_outlined,
    porQue:
        'Bajar la humedad al 7 % evita el moho y frena la fermentación en el '
        'punto justo. Un secado muy rápido encierra los ácidos dentro del '
        'grano y el chocolate sale ácido.',
    pasos: [
      'Extiende el grano en capa de 3 a 4 cm los primeros dos días.',
      'Remueve cada 2 o 3 horas.',
      'Protégelo de la lluvia y del rocío de la noche.',
      'Tarda entre 5 y 8 días según el clima.',
      'Está listo cuando un puñado cruje al apretarlo.',
    ],
    errores: [
      'Capa gruesa los primeros días: se cría moho por dentro.',
      'Secar al sol fuerte desde el primer día: el grano queda ácido.',
      'Guardarlo con más del 7 % de humedad.',
    ],
  ),
  Guia(
    etapa: 'Prueba de corte',
    icono: Icons.grid_on,
    porQue:
        'Es la forma estándar de saber si la fermentación salió bien. El color '
        'de adentro del grano cuenta toda la historia, y es lo que define el '
        'grado y el precio.',
    pasos: [
      'Toma 100 granos al azar, sin escoger.',
      'Córtalos a lo largo por la mitad.',
      'Ponlos en el tablero con la cara cortada arriba.',
      'Cuenta cuántos hay de cada color.',
      'La app calcula los porcentajes y el grado.',
    ],
    errores: [
      'Escoger los granos más bonitos: el resultado no representa el saco.',
      'Cortar a lo ancho: no se ve bien el interior.',
      'Hacerla con el grano todavía húmedo.',
    ],
  ),
  Guia(
    etapa: 'Tostado',
    icono: Icons.local_fire_department_outlined,
    porQue:
        'El tostado desarrolla el aroma y mata los microorganismos. Poco '
        'tostado deja sabores crudos; mucho, sabor a quemado que tapa todo.',
    pasos: [
      'Precalienta el horno.',
      'Extiende el grano en una sola capa.',
      'Entre 110 y 130 °C, de 20 a 35 minutos según el tamaño del grano.',
      'Remueve cada 5 minutos.',
      'Está listo cuando la cáscara se separa sola y huele a chocolate.',
      'Enfría rápido para que no siga tostándose.',
    ],
    errores: [
      'Capa gruesa: unos granos se queman y otros quedan crudos.',
      'No enfriar: el calor residual sigue tostando.',
    ],
  ),
  Guia(
    etapa: 'Refinado y conchado',
    icono: Icons.blender_outlined,
    porQue:
        'Muele las partículas hasta que la lengua deja de sentirlas (unas 20 '
        'micras) y deja escapar los ácidos que quedaron de la fermentación.',
    pasos: [
      'Precalienta el melanger.',
      'Añade los nibs poco a poco hasta que se haga líquido.',
      'Añade el azúcar cuando ya esté fluido, no antes.',
      'Déjalo entre 24 y 48 horas.',
      'Prueba cada tanto: ya está cuando no se siente arenoso.',
    ],
    errores: [
      'Echar el azúcar al principio: se pega y atasca las piedras.',
      'Sobrecargar el melanger.',
      'Que entre agua: el chocolate se corta.',
    ],
  ),
  Guia(
    etapa: 'Atemperado',
    icono: Icons.ac_unit,
    porQue:
        'Forma los cristales correctos de manteca de cacao. Es lo que da '
        'brillo, chasquido al partir y que no se derrita en la mano.',
    pasos: [
      'Funde todo el chocolate hasta unos 48 °C.',
      'Baja a 27 °C removiendo, o añadiendo chocolate ya atemperado.',
      'Sube a 31-32 °C para trabajar.',
      'Haz la prueba del papel antes de moldear.',
      'Moldea, golpea el molde para sacar burbujas y enfría a 16-18 °C.',
    ],
    errores: [
      'Cuarto caliente o húmedo: aparece el velo blanco.',
      'Meter al refrigerador para que cuaje rápido.',
      'Pasarse de 32 °C al trabajar: se deshacen los cristales buenos.',
    ],
  ),
];

const List<(String, String)> glosario = [
  ('Baba', 'El grano fresco con su pulpa blanca, recién sacado de la mazorca.'),
  ('Nib', 'El grano tostado y partido, ya sin cáscara. Es el cacao puro.'),
  ('Cascarilla', 'La cáscara del grano que se separa después de tostar.'),
  ('Licor de cacao',
      'El nib molido hasta volverse líquido. No lleva alcohol; se llama así '
          'por su textura.'),
  ('Conchado',
      'La última parte del refinado, cuando el chocolate se mueve muchas '
          'horas para que se vayan los ácidos y quede suave.'),
  ('Atemperar',
      'Llevar el chocolate por tres temperaturas para que la manteca '
          'cristalice bien.'),
  ('Fat bloom',
      'Velo grisáceo por manteca que subió a la superficie. Pasa cuando el '
          'atemperado falla o hay cambios de temperatura.'),
  ('Sugar bloom',
      'Superficie áspera y blanquecina por humedad que disolvió el azúcar.'),
  ('Grano pizarroso',
      'Grano gris y compacto por dentro: no fermentó nada.'),
  ('Grano violeta', 'Le faltó fermentación: quedó a medio camino.'),
  ('Prueba de corte',
      'Cortar 100 granos por la mitad y contarlos por color para saber cómo '
          'salió la fermentación.'),
  ('Monilia',
      'Hongo que cubre la mazorca de polvo blanco y pudre los granos por '
          'dentro.'),
  ('Fitóftora',
      'Hongo que deja manchas negras en la mazorca, también llamado mazorca '
          'negra.'),
  ('CCN-51',
      'Variedad de cacao muy productiva y resistente, la más sembrada en la '
          'costa ecuatoriana.'),
  ('Cadmio',
      'Metal que el cacao absorbe del suelo. Tiene límite legal para '
          'exportar a Europa y se mide en laboratorio.'),
  ('BPM',
      'Buenas Prácticas de Manufactura: las reglas de higiene y orden que '
          'pide ARCSA.'),
];

class PantallaGuias extends StatelessWidget {
  const PantallaGuias({super.key});

  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('Guías'),
          bottom: const TabBar(tabs: [
            Tab(text: 'Por etapa'),
            Tab(text: 'Glosario'),
          ]),
        ),
        body: TabBarView(
          children: [
            ListView(
              padding: const EdgeInsets.all(16),
              children: [
                for (final g in guias)
                  Card(
                    child: ListTile(
                      leading: Icon(g.icono, size: 30),
                      title: Text(g.etapa),
                      subtitle: Text(
                        g.porQue,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () => Navigator.push(
                        context,
                        MaterialPageRoute(
                            builder: (_) => _DetalleGuia(guia: g)),
                      ),
                    ),
                  ),
              ],
            ),
            ListView(
              padding: const EdgeInsets.all(16),
              children: [
                for (final (termino, definicion) in glosario)
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(termino,
                              style: const TextStyle(
                                  fontSize: 18,
                                  fontWeight: FontWeight.w700)),
                          const SizedBox(height: 4),
                          Text(definicion,
                              style: const TextStyle(fontSize: 16)),
                        ],
                      ),
                    ),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _DetalleGuia extends StatelessWidget {
  const _DetalleGuia({required this.guia});
  final Guia guia;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(guia.etapa)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TarjetaSeccion(
            titulo: 'Para qué sirve',
            icono: Icons.help_outline,
            hijos: [Text(guia.porQue, style: const TextStyle(fontSize: 16))],
          ),
          TarjetaSeccion(
            titulo: 'Cómo se hace',
            icono: Icons.checklist,
            hijos: [
              for (var i = 0; i < guia.pasos.length; i++)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      CircleAvatar(
                        radius: 14,
                        child: Text('${i + 1}',
                            style: const TextStyle(fontSize: 14)),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Text(guia.pasos[i],
                            style: const TextStyle(fontSize: 16)),
                      ),
                    ],
                  ),
                ),
            ],
          ),
          TarjetaSeccion(
            titulo: 'Lo que suele salir mal',
            icono: Icons.warning_amber_rounded,
            hijos: [
              for (final e in guia.errores)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('· ', style: TextStyle(fontSize: 16)),
                      Expanded(
                          child: Text(e,
                              style: const TextStyle(fontSize: 16))),
                    ],
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }
}
