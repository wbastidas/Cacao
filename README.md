# CacaoTrace

Aplicación móvil **offline-first** para la producción *bean-to-bar* de chocolate 90 % con cacao
CCN-51, desde la recepción de mazorcas hasta la venta, con análisis de imágenes por
inteligencia artificial que corre **dentro del teléfono** y trazabilidad para trámites ARCSA.

Implementación de la *Especificación de Requerimientos de Software (ERS) v1.0*, basada en
ISO/IEC/IEEE 29148. Ver [`docs/ERS.md`](docs/ERS.md) para el documento completo y
[`docs/TRAZABILIDAD.md`](docs/TRAZABILIDAD.md) para el mapa requerimiento → código.

---

## Qué hay en este repositorio

```
Cacao/
├── app/              Aplicación Flutter (Android 8.0+, iOS 15+ opcional)
│   ├── lib/
│   │   ├── nucleo/       Reglas de negocio puras (norma INEN 176, RN-01..RN-17)
│   │   ├── datos/        Base SQLite con Drift + cola de sincronización
│   │   ├── ia/           Inferencia TFLite (modelos M1–M4)
│   │   └── ui/           Pantallas
│   └── test/         Tests unitarios del núcleo
├── entrenamiento/    Pipeline Python para entrenar y exportar los modelos M1–M4
└── docs/             ERS, arquitectura, trazabilidad, guías de operación
```

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
> [`docs/NUBE.md`](docs/NUBE.md). La app funciona al 100 % sin ninguna de esas credenciales.

## Cómo empezar

### 1. Correr la app

```bash
cd app
flutter pub get
dart run build_runner build --delete-conflicting-outputs   # genera el código de Drift
flutter run
```

Para generar el APK de instalación:

```bash
flutter build apk --release      # app/build/app/outputs/flutter-apk/app-release.apk
```

Detalles y requisitos en [`docs/COMPILAR.md`](docs/COMPILAR.md).

### 2. Entrenar los modelos de IA

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

### 3. Verificar que todo funciona sin fotos reales

```bash
cd entrenamiento
python herramientas/generar_datos_sinteticos.py --salida fuentes --tarea mazorca
bash herramientas/smoke_test.sh
```

## Qué está verificado

```
flutter analyze     sin observaciones
flutter test        118 tests en verde
pytest              35 tests de la norma INEN 176
smoke_test.sh       pipeline de entrenamiento completo, incluido YOLO
```

Los tests de la norma están **compartidos entre Python y Dart**: los dos leen
`entrenamiento/pruebas/casos_norma.json`, así que si una implementación se
desvía de la otra, sus tests fallan.

No se ha compilado el APK en este entorno porque no tiene el Android SDK
instalado; los pasos están en [`docs/COMPILAR.md`](docs/COMPILAR.md).

## Principios de diseño

1. **Offline-first real.** Cada registro se escribe primero en SQLite y se marca
   `pendiente`. La nube es un espejo, nunca la fuente de verdad.
2. **La IA es de apoyo, no autoridad.** El usuario siempre puede corregir; la corrección
   manda y se guarda para reentrenar (RF-IA-03, RF-IA-07).
3. **Nada queda cableado.** Umbrales, tabla de la norma y versiones de modelo son datos
   editables, no constantes en el código (RNF-12).
4. **Español simple.** Sin tecnicismos sin explicar; botones ≥ 48 dp, texto ≥ 16 sp,
   alto contraste para uso al sol (RNF-09, RNF-10).

## Aviso sobre la norma y los análisis

Los valores de la NTE INEN 176 incluidos en `entrenamiento/norma_inen176.json` son de
**referencia** y deben verificarse contra el texto oficial vigente antes de usarse con
fines comerciales. La app permite editarlos sin reinstalar. El grado de calidad y el
contenido de cadmio con validez legal dependen de la norma oficial y de un laboratorio
acreditado, no de esta aplicación.

## Licencia

Ver [`LICENSE`](LICENSE). Los datasets de terceros mantienen sus propias licencias; ver
las advertencias en [`entrenamiento/README.md`](entrenamiento/README.md).
