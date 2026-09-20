# Compilar e instalar CacaoTrace

## Requisitos

| Herramienta | Versión probada |
|---|---|
| Flutter SDK | 3.35.5 (canal stable) |
| Dart | 3.9.2 (viene con Flutter) |
| JDK | 21 |
| Android SDK | API 36, build-tools 34+ |
| Android mínimo del dispositivo | 8.0 (API 26) — RNF-13 |

Instala Flutter siguiendo <https://docs.flutter.dev/get-started/install/linux> y
comprueba con `flutter doctor` que Android toolchain aparece en verde.

## Primera compilación

```bash
cd app
flutter pub get
dart run build_runner build --delete-conflicting-outputs
```

El segundo comando **es obligatorio**: genera `base_datos.g.dart` a partir de las
definiciones de tablas de Drift. Si lo olvidas verás cientos de errores de "clase no
encontrada". Vuélvelo a correr cada vez que cambies una tabla.

## Correr en un teléfono conectado

```bash
flutter devices
flutter run
```

## Generar el APK

```bash
# APK único, sirve para instalar a mano o repartir por WhatsApp
flutter build apk --release

# APK separados por arquitectura: cada uno pesa ~40 % menos
flutter build apk --release --split-per-abi
```

El archivo queda en `app/build/app/outputs/flutter-apk/app-release.apk`.

Para instalarlo en el teléfono hay que permitir "instalar apps de origen desconocido".

## Firmar para Google Play

```bash
keytool -genkey -v -keystore ~/cacaotrace.jks -keyalg RSA \
  -keysize 2048 -validity 10000 -alias cacaotrace
```

Crea `app/android/key.properties` (está en `.gitignore`):

```properties
storePassword=...
keyPassword=...
keyAlias=cacaotrace
storeFile=/home/tu_usuario/cacaotrace.jks
```

y luego:

```bash
flutter build appbundle --release
```

**Guarda el archivo `.jks` y sus contraseñas en un lugar seguro.** Si los pierdes no
podrás publicar actualizaciones de la misma app en Google Play.

## Verificaciones antes de publicar

```bash
cd app
flutter analyze          # no debe reportar nada
flutter test             # todos los tests del núcleo en verde
```

## Instalar los modelos de IA

Los `.tflite` no están en el repositorio (pesan demasiado y se regeneran). Después de
entrenar:

```bash
cd entrenamiento
python herramientas/instalar_modelo.py --tarea mazorca
```

Eso copia `modelo.tflite`, `etiquetas.txt` y `metadatos.json` a
`app/assets/modelos/mazorca/`. Si la carpeta está vacía la app arranca igual y muestra
"modelo no instalado" en las pantallas de análisis; el conteo y el registro manual
siguen funcionando.
