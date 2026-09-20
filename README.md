# CacaoTrace

Aplicación **Android nativa** y **offline-first** para la producción *bean-to-bar* de
chocolate 90 % con cacao CCN-51, desde la recepción de mazorcas hasta la venta, con
análisis de imágenes por inteligencia artificial que corre **dentro del teléfono** y
trazabilidad para trámites ARCSA.

Implementación de la *Especificación de Requerimientos de Software (ERS) v1.0*, basada en
ISO/IEC/IEEE 29148. Ver [`docs/ERS.md`](docs/ERS.md) para el documento completo y
[`docs/TRAZABILIDAD.md`](docs/TRAZABILIDAD.md) para el mapa requerimiento → código.

---

## Qué hay en este repositorio

```
Cacao/
├── android/                  App Android (Kotlin, minSdk 26)
│   ├── nucleo/                 Módulo Kotlin/JVM PURO, sin Android
│   │   └── …/nucleo/             · norma INEN 176 (prueba de corte)
│   │                             · motor de reglas RN-01..RN-17
│   │                             · balance de masa, recetas, códigos
│   └── app/                    Módulo Android
│       └── …/ec/cacaotrace/      · datos/    Room + cola de sincronización
│                                 · ia/       LiteRT (modelos M1–M4)
│                                 · ui/       Jetpack Compose, Material 3
│                                 · informes/ PDF y CSV
│                                 · trabajo/  WorkManager (avisos, sync)
├── entrenamiento/            Pipeline Python para entrenar y exportar M1–M4
├── referencia/flutter-app/   Primera implementación en Flutter, como referencia
└── docs/                     ERS, arquitectura, trazabilidad, guías de operación
```

> **Nota sobre la plataforma.** La ERS v1.0 proponía Flutter en su §3. El cliente pidió
> después **Android nativo en Kotlin**, y eso es lo que se entrega. No cambia ningún
> requisito: las capas, el modelo de datos y la estrategia offline-first son los mismos.
> La app Flutter completa se conserva en `referencia/flutter-app/` para contrastar
> comportamiento; no se compila ni se publica. Ver §8 de
> [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md).

## Estado por fases

| Fase | Contenido | Estado |
|---|---|---|
| 0. Datos y modelos | Pipeline de entrenamiento, protocolo de fotos, notebook Colab | ✅ Listo para cargar fotos |
| 1. MVP | Lotes+QR, recepción (M1), apertura, fermentación, secado, prueba de corte, alertas, guías, sincronización local | ✅ Implementado |
| 2. Calidad | M2 en prueba de corte, almacenamiento, tostado, refinado, atemperado, reportes PDF, panel | ✅ Implementado |
| 3. Venta | Inventario, costos, ventas, BPM, laboratorio, notificaciones, M3/M4 | ✅ Implementado |
| — | Firebase/Drive, sensor ESP32 por Bluetooth, segunda opinión con Gemini | ⛔ Pendientes (necesitan credenciales o el dispositivo) |

El detalle requerimiento por requerimiento está en
[`docs/TRAZABILIDAD.md`](docs/TRAZABILIDAD.md).

> La **capa de nube** (Firebase Auth/Firestore, Google Drive) está implementada como
> interfaz + cola de sincronización con un adaptador local. Para activarla en producción
> basta implementar `SincronizadorRemoto` y añadir `google-services.json`; ver
> [`docs/NUBE.md`](docs/NUBE.md). La app funciona al 100 % sin ninguna de esas
> credenciales.

## Cómo empezar

### 1. Probar las reglas de negocio (no hace falta el SDK de Android)

```bash
cd android
gradle :nucleo:test --configure-on-demand
```

`:nucleo` es Kotlin puro: la norma INEN 176, las 17 reglas de negocio, el balance de masa
y las recetas corren en la JVM, en segundos, sin emulador.

### 2. Compilar la app

```bash
cd android
gradle :app:assembleDebug     # o abrir la carpeta android/ en Android Studio
```

Detalles, firma y publicación en [`docs/COMPILAR.md`](docs/COMPILAR.md).

### 3. Entrenar los modelos de IA

Todo el proceso, paso a paso y sin instalar nada en tu computadora, está en
[`entrenamiento/README.md`](entrenamiento/README.md). Resumen:

```bash
cd entrenamiento
pip install -r requirements.txt

# 1) unir tus fotos + datasets abiertos y dividir en train/val/test
python preparar_dataset.py --fuentes fuentes --mapeo mapeo_mazorca.json --salida datos/mazorca

# 2) entrenar y exportar a TFLite
python entrenar_clasificador.py --datos datos/mazorca --tarea mazorca

# 3) copiar a la app
python herramientas/instalar_modelo.py --tarea mazorca
```

### 4. Verificar que todo funciona sin fotos reales

```bash
cd entrenamiento
python herramientas/generar_datos_sinteticos.py --salida fuentes --tarea mazorca
bash herramientas/smoke_test.sh
```

## Qué está verificado, y qué no

**Ejecutado y en verde:**

```
gradle :nucleo:test    131 pruebas del núcleo Kotlin
pytest                  35 pruebas de la norma INEN 176 en Python
smoke_test.sh           pipeline de entrenamiento completo, incluido YOLO
```

Los casos de la norma están **compartidos entre Python y Kotlin**: los dos leen
`entrenamiento/pruebas/casos_norma.json`, así que si una implementación se desvía de la
otra, sus pruebas fallan.

**Escrito pero no compilado:** el módulo `:app`. El entorno donde se desarrolló no tiene
el SDK de Android y su proxy de salida bloquea `dl.google.com`, del que depende
`maven.google.com` para resolver AndroidX, Compose y Room. La primera compilación hay que
hacerla en una máquina con el SDK, y es razonable esperar ajustes menores. Los pasos están
en [`docs/COMPILAR.md`](docs/COMPILAR.md).

## Principios de diseño

1. **Offline-first real.** Cada registro se escribe primero en SQLite y se marca
   `PENDIENTE`. La nube es un espejo, nunca la fuente de verdad.
2. **La IA es de apoyo, no autoridad.** El usuario siempre puede corregir; la corrección
   manda y se guarda para reentrenar (RF-IA-03, RF-IA-07). Sin modelos instalados, la app
   funciona entera a mano.
3. **Nada queda cableado.** Umbrales, tabla de la norma y versiones de modelo son datos
   editables, no constantes en el código (RNF-12).
4. **Español simple.** Sin tecnicismos sin explicar; botones ≥ 48 dp, texto ≥ 16 sp, alto
   contraste para uso al sol, y el color nunca solo: siempre con icono y texto
   (RNF-09, RNF-10).

## Aviso sobre la norma y los análisis

Los valores de la NTE INEN 176 incluidos en `entrenamiento/norma_inen176.json` son de
**referencia** y deben verificarse contra el texto oficial vigente antes de usarse con
fines comerciales. La app permite editarlos sin reinstalar. El grado de calidad y el
contenido de cadmio con validez legal dependen de la norma oficial y de un laboratorio
acreditado, no de esta aplicación.

## Licencia

Ver [`LICENSE`](LICENSE). Los datasets de terceros mantienen sus propias licencias; ver
las advertencias en [`entrenamiento/README.md`](entrenamiento/README.md).
