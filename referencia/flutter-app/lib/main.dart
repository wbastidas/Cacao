/// CacaoTrace — punto de entrada.
library;

import 'package:flutter/material.dart';

import 'servicios.dart';
import 'ui/app.dart';
import 'ui/tema.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const Arranque());
}

/// Muestra la app en cuanto los servicios están listos.
///
/// Abrir la base y leer los umbrales tarda unos milisegundos; se hace aquí
/// para que ninguna pantalla tenga que preocuparse de si ya está todo listo.
class Arranque extends StatefulWidget {
  const Arranque({super.key});

  @override
  State<Arranque> createState() => _ArranqueState();
}

class _ArranqueState extends State<Arranque> {
  late final Future<Servicios> _servicios = Servicios.arrancar();

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'CacaoTrace',
      theme: temaClaro(),
      darkTheme: temaOscuro(),
      debugShowCheckedModeBanner: false,
      home: FutureBuilder<Servicios>(
        future: _servicios,
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return _PantallaError(error: snapshot.error!);
          }
          if (!snapshot.hasData) {
            return const Scaffold(
              body: Center(child: CircularProgressIndicator()),
            );
          }
          return ProveedorServicios(
            servicios: snapshot.data!,
            child: const AppCacaoTrace(),
          );
        },
      ),
    );
  }
}

class _PantallaError extends StatelessWidget {
  const _PantallaError({required this.error});
  final Object error;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline, size: 64),
            const SizedBox(height: 16),
            Text(
              'No se pudo abrir la aplicación',
              style: Theme.of(context).textTheme.titleLarge,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            const Text(
              'Esto suele pasar si no queda espacio en el teléfono. '
              'Libera espacio y vuelve a abrir la app. Tus datos no se pierden.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 16),
            ),
            const SizedBox(height: 24),
            Text('$error', style: const TextStyle(fontSize: 13)),
          ],
        ),
      ),
    );
  }
}
