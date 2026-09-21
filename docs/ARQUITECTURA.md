# Arquitectura de CacaoTrace

> La app es **Android nativo en Kotlin**. La versión Flutter que se construyó primero se
> conserva en `referencia/flutter-app/` como referencia funcional; ver la §8.

## 1. Idea central

La app es **la fuente de verdad**, no la nube. Cada dato se escribe en SQLite dentro del
teléfono y queda marcado como *pendiente de sincronizar*. Si hay internet, un proceso
aparte lo copia a la nube; si no lo hay, la app sigue funcionando igual. Esto cumple
RNF-01 (el 100 % de las funciones obligatorias trabajan sin conexión) y RF-SYN-01.

```
┌──────────────────────── Teléfono ────────────────────────┐
│                                                          │
│  :app  ui/      Pantallas (Compose + Material 3,         │
│   │             español, ≥48 dp, texto ≥16 sp)           │
│   ▼                                                      │
│  :app  datos/repositorios/  Casos de uso + reglas        │
│   │          │                                           │
│   │          └──► :nucleo   Reglas PURAS, sin I/O        │
│   │                 · norma INEN 176 (prueba de corte)   │
│   │                 · motor de reglas RN-01..RN-17       │
│   │                 · balance de masa, recetas           │
│   ▼                                                      │
│  :app  datos/bd/   Room (SQLite) ◄── fuente de verdad    │
│   │                                                      │
│   ├──► datos/sync/   Cola de salida (outbox)             │
│   ├──► ia/           LiteRT: M1 mazorca, M2 corte,       │
│   │                  M3 tostado, M4 chocolate            │
│   ├──► trabajo/      WorkManager: avisos y sincronización│
│   └──► informes/     PDF y CSV (PdfDocument del sistema) │
└──────────────────────────────────────────────────────────┘
                 │ (cuando hay internet)
                 ▼
      SincronizadorRemoto  →  Firestore (registros)
                           →  Google Drive (fotos, respaldos, PDF)
```

## 2. Dos módulos Gradle, y por qué

| Módulo | Qué es | Dependencias |
|---|---|---|
| `:nucleo` | Kotlin/JVM puro. Norma, reglas, cálculos, tipos de dominio | Solo `kotlinx-serialization` y JUnit 5 |
| `:app` | Android: Room, Compose, CameraX, LiteRT, WorkManager | AndroidX y el resto |

La separación no es decorativa: `:nucleo` **no conoce Android**, así que sus 131 pruebas
corren en la JVM en segundos, sin emulador ni SDK. Toda la lógica que decide *si un lote
cumple la norma* o *si hay que lanzar una alerta* vive ahí, no en las pantallas.

Cada módulo declara sus propios plugins; el `build.gradle.kts` raíz no declara ninguno.
Eso permite construir y probar `:nucleo` con `--configure-on-demand` sin que Gradle
tenga que resolver el plugin de Android, lo cual importa en entornos donde
`dl.google.com` no es accesible.

### Regla de dependencia

| Capa | Paquete | Puede depender de | Nunca depende de |
|---|---|---|---|
| Núcleo de dominio | `:nucleo` `ec.cacaotrace.nucleo` | nada | Android, base de datos, red |
| Datos | `:app` `datos/` | núcleo | UI |
| IA | `:app` `ia/` | núcleo | UI, datos |
| Informes | `:app` `informes/` | núcleo | UI, datos |
| UI | `:app` `ui/` | todas | — |

## 3. Por qué el núcleo replica `calificar_corte.py`

El script Python `entrenamiento/calificar_corte.py` y la clase Kotlin
`nucleo/norma/CalificadorCorte.kt` implementan **el mismo algoritmo** y leen **el mismo
JSON** (`norma_inen176.json`). Así:

- en la computadora se puede validar la norma contra tableros contados por un experto;
- en el teléfono se obtiene idéntico resultado sin internet (RF-PRC-06);
- cambiar la tabla de la norma no requiere tocar código ni en un lado ni en el otro
  (RNF-12, RF-CFG-03).

El invariante se sostiene con pruebas compartidas: `entrenamiento/pruebas/casos_norma.json`
lo leen **las dos** implementaciones. En Kotlin, `CalificadorCorteTest` genera un test
dinámico por caso con `@TestFactory`. Si una de las dos se desvía, su suite falla.

Un detalle que parece menor y no lo es: `nucleo/norma/TablaNorma.kt` formatea los números
a mano en vez de usar `String.format`, porque este último usa la configuración regional de
la JVM. En un teléfono configurado con coma decimal, el texto de las alertas saldría
distinto al de Python y los casos compartidos fallarían. Toda la app formatea números por
ahí.

## 4. Sincronización (RF-SYN-01..07)

Toda entidad lleva `@Embedded Comunes`, con las columnas que exige el §6 de la ERS:

| Columna | Para qué |
|---|---|
| `id` | UUID v4 generado en el teléfono; evita colisiones sin servidor |
| `creadoEn`, `modificadoEn` | resolución de conflictos "gana la última modificación" |
| `usuarioId`, `dispositivoId` | auditoría (RNF-11) y origen del cambio |
| `estadoSync` | `PENDIENTE` / `ENVIANDO` / `SINCRONIZADO` / `ERROR` |
| `eliminado` | borrado lógico; nunca se borra una fila físicamente |

Los enums se guardan por **nombre**, no por posición: guardar el ordinal es más compacto
pero convierte cualquier reordenación del enum en una corrupción silenciosa de los datos
ya escritos.

Cada escritura encola una operación en `cola_sync`. `ServicioSincronizacion` la vacía en
orden (primero registros, luego fotos) con reintentos y retroceso exponencial, y respeta
"subir fotos solo con WiFi" (RF-SYN-04), activada por defecto. El vaciado está protegido
por un `Mutex`: sin él, dos llamadas concurrentes pueden pasar ambas la comprobación de
"ya estoy sincronizando" antes de la primera suspensión y subir todo dos veces.

`SincronizadorRemoto` es una **interfaz**. Hoy existe `SincronizadorLocal` (marca todo
como sincronizado, útil para desarrollo y pruebas). Para producción se implementa la
versión Firestore + Drive sin tocar ni la UI ni la base; ver [`NUBE.md`](NUBE.md).

## 5. La IA como apoyo, nunca como autoridad

`ia/ServicioModelos.kt` carga los `.tflite` desde `assets/modelos/`. Si un modelo **no
está instalado** la app no falla: marca esa función como "conteo manual" y el usuario
trabaja igual. Esto es deliberado —la ERS (§12, riesgos) prevé arrancar con conteo manual
mientras los modelos alcanzan sus metas de la §8.1.

En la prueba de corte esto se nota en la pantalla: el conteo manual **no es un plan B de
segunda**. Tiene botones grandes de +/- y es el camino que se usa mientras el modelo no
llegue a sus metas, así que tiene que ser cómodo.

Cada análisis guarda la **versión del modelo** usada (RF-IA-05). Si la confianza máxima
queda por debajo del umbral configurable (0,60 por defecto, RN-16) la app muestra "No
estoy seguro" y pide decisión del usuario. La corrección del usuario **siempre** prevalece,
se guarda en el registro y marca la foto como apta para reentrenar (RF-IA-03, RF-PRC-09).

## 6. Motor de alertas

`nucleo/reglas/MotorReglas.kt` recibe un *contexto* (los valores de la etapa que se acaba
de registrar) y devuelve una lista de `Alerta`. Cada regla RN-xx tiene umbral editable,
mensaje en español simple ("qué pasó / por qué importa / qué hacer") y el código de
corrección C-xx asociado (RF-ALE-03). Todo corre en el teléfono, sin Cloud Functions, para
poder operar con el plan gratuito de Firebase (§3 de la ERS).

Las alertas no se repiten: cada una lleva una clave de deduplicación. Si la fermentación
lleva tres días fría, el usuario no necesita doce alertas idénticas, necesita una que siga
abierta.

## 7. Inyección de dependencias a mano

`ContenedorApp` arma la app entera en unas sesenta líneas, sin Hilt ni Koin. Para un
módulo único hace el mismo trabajo sin añadir procesamiento de anotaciones, y se lee de
arriba abajo: no hay que ir a buscar qué módulo provee qué. Además evita que dos pantallas
creen cada una su instancia de Room y acaben peleándose por el mismo archivo.

## 8. Decisiones tomadas y por qué

| Decisión | Motivo |
|---|---|
| Kotlin + Compose + Room | pedido explícito del cliente; acceso directo a CameraX, WorkManager y LiteRT sin capa intermedia |
| Dos módulos (`:nucleo` puro + `:app`) | las reglas se prueban en la JVM en segundos y son reutilizables fuera de Android |
| Gráficos del panel con `Canvas` | cuatro gráficos sencillos no justifican una dependencia que se rompa al actualizar |
| PDF con `android.graphics.pdf.PdfDocument` | viene en el sistema desde Android 4.4; una librería externa añadiría megas al APK |
| Fotos en Google Drive del usuario, no en Firebase Storage | Storage exige plan Blaze; Drive da 15 GB gratis y los datos quedan del productor |
| Alertas en el teléfono, no en Cloud Functions | funcionan sin internet y sin plan de pago |
| Umbrales en base de datos, no en constantes | el usuario los edita sin reinstalar (RF-CFG-03) |
| Modelos opcionales | la app es útil desde el día uno aunque los modelos aún no existan |
| Borrado lógico | permite sincronizar borrados entre teléfonos sin perder auditoría |

### Desviación deliberada respecto al §3 de la ERS

La ERS v1.0, en su §3, propone **Flutter + Drift** como plataforma. La implementación
entregada es **Android nativo en Kotlin**, por indicación expresa del cliente posterior a
la redacción de la ERS ("Debe ser Android y pensado en Kotlin").

Queda constancia de que:

- **No cambia ningún requisito funcional ni no funcional.** Las capas, el modelo de datos,
  la estrategia offline-first, la cola de sincronización y los umbrales configurables son
  los mismos; solo cambia la tecnología que los implementa.
- El pipeline de entrenamiento (`entrenamiento/`) es **independiente del lenguaje de la
  app** y no se tocó: sigue produciendo los mismos `.tflite` y la misma tabla de norma.
- La app Flutter completa se conserva en `referencia/flutter-app/`. No se compila ni se
  publica; sirve para contrastar comportamiento cuando haya dudas sobre qué hacía una
  pantalla.
