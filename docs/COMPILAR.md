# Compilar e instalar CacaoTrace

La app es **Android nativo en Kotlin**, con Gradle. Todo lo de esta guía se hace desde
la carpeta `android/`.

## Requisitos

| Herramienta | Versión probada |
|---|---|
| JDK | 17 o 21 |
| Gradle | 8.10+ (o el wrapper del proyecto) |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| Android SDK | compileSdk 35, build-tools 35 |
| Android mínimo del dispositivo | 8.0 (API 26) — RNF-13 |

La forma más cómoda es abrir la carpeta `android/` con Android Studio (Ladybug o más
reciente), que instala el SDK y las build-tools que el proyecto pide. Si prefieres la
línea de comandos, basta con `ANDROID_HOME` apuntando a un SDK con la API 35.

## Probar el núcleo (no necesita el SDK de Android)

```bash
cd android
gradle :nucleo:test --configure-on-demand
```

`:nucleo` es Kotlin/JVM puro: no depende de AndroidX ni del SDK, así que estas pruebas
corren en cualquier máquina con un JDK, en segundos y sin emulador. Ahí vive toda la
lógica de la norma INEN 176, el motor de reglas RN-01..RN-17, el balance de masa y las
recetas. **131 pruebas, en verde.**

El `--configure-on-demand` no es un capricho: hace que Gradle configure solo el módulo
pedido y no tenga que resolver el plugin de Android, lo cual permite correr estas
pruebas en entornos donde `dl.google.com` no es accesible.

## Compilar la app

```bash
cd android
gradle :app:assembleDebug
```

Room genera el código de acceso a datos con KSP durante la compilación; no hay ningún
paso previo que lanzar a mano. Los esquemas de la base quedan en
`app/schemas/`, versionados, que es lo que permite escribir migraciones más adelante.

## Correr en un teléfono conectado

```bash
gradle :app:installDebug
```

o directamente el botón ▶ de Android Studio con el teléfono en modo depuración USB.

## Generar el APK de release

```bash
gradle :app:assembleRelease
```

Queda en `android/app/build/outputs/apk/release/`. Para instalarlo a mano hay que
permitir en el teléfono "instalar apps de origen desconocido".

## Firmar para Google Play

```bash
keytool -genkey -v -keystore ~/cacaotrace.jks -keyalg RSA \
  -keysize 2048 -validity 10000 -alias cacaotrace
```

Crea `android/key.properties` (está en `.gitignore` y **no se sube nunca**):

```properties
storePassword=...
keyPassword=...
keyAlias=cacaotrace
storeFile=/home/tu_usuario/cacaotrace.jks
```

y luego:

```bash
gradle :app:bundleRelease
```

**Guarda el archivo `.jks` y sus contraseñas en un lugar seguro.** Si los pierdes no
podrás publicar actualizaciones de la misma app en Google Play.

## Verificaciones antes de publicar

```bash
cd android
gradle :nucleo:test        # 131 pruebas del núcleo
gradle :app:lint           # lint de Android
gradle :app:testDebugUnitTest
```

## Instalar los modelos de IA

Los `.tflite` no están en el repositorio (pesan demasiado y se regeneran). Después de
entrenar:

```bash
cd entrenamiento
python herramientas/instalar_modelo.py --tarea mazorca
```

Eso copia `modelo.tflite`, `etiquetas.txt` y `metadatos.json` a
`android/app/src/main/assets/modelos/mazorca/`. Si la carpeta está vacía la app arranca
igual y muestra "sin modelo instalado" en las pantallas de análisis; el conteo y el
registro manual siguen funcionando.

Los `.tflite` van sin comprimir en el APK (`noCompress += "tflite"` en
`app/build.gradle.kts`): LiteRT los mapea en memoria directamente desde el APK, y un
archivo comprimido tendría que descomprimirse antes, gastando RAM y tiempo de arranque.

## Nota sobre el entorno de construcción de esta entrega

El módulo `:app` **no se ha compilado** en el entorno donde se escribió el código: no
tiene el SDK de Android y el proxy de salida bloquea `dl.google.com`, del que depende
`maven.google.com` para resolver AndroidX, Compose y Room. Lo que sí está verificado
ejecutándose es `:nucleo` con sus 131 pruebas. La primera compilación de `:app` hay que
hacerla en una máquina con el SDK, y es razonable esperar ajustes menores.
