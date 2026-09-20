# Arquitectura de CacaoTrace

## 1. Idea central

La app es **la fuente de verdad**, no la nube. Cada dato se escribe en SQLite dentro del
teléfono y queda marcado como *pendiente de sincronizar*. Si hay internet, un proceso
aparte lo copia a la nube; si no lo hay, la app sigue funcionando igual. Esto cumple
RNF-01 (el 100 % de las funciones obligatorias trabajan sin conexión) y RF-SYN-01.

```
┌──────────────────────── Teléfono ────────────────────────┐
│                                                          │
│  ui/          Pantallas (Material 3, español, ≥48 dp)    │
│   │                                                      │
│   ▼                                                      │
│  datos/repositorios/   Casos de uso + reglas aplicadas   │
│   │          │                                           │
│   │          └──► nucleo/   Reglas PURAS, sin I/O        │
│   │                 · norma INEN 176 (prueba de corte)   │
│   │                 · motor de reglas RN-01..RN-17       │
│   │                 · balance de masa, recetas           │
│   ▼                                                      │
│  datos/bd/     Drift (SQLite)  ◄── única fuente de verdad│
│   │                                                      │
│   ├──► datos/sync/   Cola de salida (outbox)             │
│   │                                                      │
│   └──► ia/           TFLite: M1 mazorca, M2 corte,       │
│                      M3 tostado, M4 chocolate            │
└──────────────────────────────────────────────────────────┘
                 │ (cuando hay internet)
                 ▼
      SincronizadorRemoto  →  Firestore (registros)
                           →  Google Drive (fotos, respaldos, PDF)
```

## 2. Capas y su regla de dependencia

| Capa | Carpeta | Puede depender de | Nunca depende de |
|---|---|---|---|
| Núcleo de dominio | `lib/nucleo/` | nada (Dart puro) | Flutter, base de datos, red |
| Datos | `lib/datos/` | núcleo | UI |
| IA | `lib/ia/` | núcleo | UI, datos |
| UI | `lib/ui/` | todas | — |

El núcleo es **Dart puro y sin dependencias**: por eso sus reglas se pueden probar con
`flutter test` en segundos y se pueden reutilizar tal cual en una futura versión web o
en un backend. Toda la lógica que decide *si un lote cumple la norma* o *si hay que
lanzar una alerta* vive ahí, no en las pantallas.

## 3. Por qué el núcleo replica `calificar_corte.py`

El script Python `entrenamiento/calificar_corte.py` y la clase Dart
`lib/nucleo/norma/calificador_corte.dart` implementan **el mismo algoritmo** y leen **el
mismo JSON** (`norma_inen176.json`). Así:

- en la computadora se puede validar la norma contra tableros contados por un experto;
- en el teléfono se obtiene idéntico resultado sin internet (RF-PRC-06);
- cambiar la tabla de la norma no requiere tocar código ni en un lado ni en el otro
  (RNF-12, RF-CFG-03).

`app/test/nucleo/calificador_corte_test.dart` incluye los mismos casos que el script
Python, de modo que si una de las dos implementaciones se desvía, el test falla.

## 4. Sincronización (RF-SYN-01..07)

Toda tabla lleva las columnas comunes exigidas por el §6 del ERS:

| Columna | Para qué |
|---|---|
| `id` | UUID v4 generado en el teléfono; evita colisiones sin servidor |
| `creadoEn`, `modificadoEn` | resolución de conflictos "gana la última modificación" |
| `usuarioId`, `dispositivoId` | auditoría (RNF-11) y origen del cambio |
| `estadoSync` | `pendiente` / `enviando` / `sincronizado` / `error` |
| `eliminado` | borrado lógico; nunca se borra una fila físicamente |

Cada escritura encola una `OperacionSync` en la tabla `cola_sync`. El
`ServicioSincronizacion` la vacía en orden (primero registros, luego fotos) con
reintentos y retroceso exponencial, y respeta la opción "subir fotos solo con WiFi"
(RF-SYN-04), activada por defecto.

`SincronizadorRemoto` es una **interfaz**. Hoy existe `SincronizadorLocalSimulado`
(marca todo como sincronizado, útil para desarrollo y pruebas). Para producción se
implementa la versión Firestore + Drive sin tocar ni la UI ni la base; ver
[`NUBE.md`](NUBE.md).

## 5. La IA como apoyo, nunca como autoridad

`ia/servicio_modelos.dart` carga los `.tflite` desde `assets/modelos/`. Si un modelo
**no está instalado** la app no falla: marca esa función como "conteo manual" y el
usuario trabaja igual. Esto es deliberado —el ERS (§12, riesgos) prevé arrancar con
conteo manual mientras los modelos alcanzan sus metas de la §8.1.

Cada análisis guarda la **versión del modelo** usada (RF-IA-05). Si la confianza máxima
queda por debajo del umbral configurable (0,60 por defecto, RN-16) la app muestra
"No estoy seguro" y pide decisión del usuario. La corrección del usuario **siempre**
prevalece, se guarda en el registro y marca la foto como apta para reentrenar
(RF-IA-03, RF-PRC-09).

## 6. Motor de alertas

`nucleo/reglas/motor_reglas.dart` recibe un *contexto* (los valores de la etapa que se
acaba de registrar) y devuelve una lista de `Alerta`. Cada regla RN-xx es un objeto con
umbral editable, mensaje en español simple ("qué pasó / por qué importa / qué hacer") y
el código de corrección C-xx asociado (RF-ALE-03). Todo corre en el teléfono, sin Cloud
Functions, para poder operar con el plan gratuito de Firebase (§3 del ERS).

## 7. Decisiones tomadas y por qué

| Decisión | Motivo |
|---|---|
| Flutter + Drift | una sola base de código, SQLite relacional con tipos verificados en compilación |
| Fotos en Google Drive del usuario, no en Firebase Storage | Storage exige plan Blaze; Drive da 15 GB gratis y los datos quedan del productor |
| Alertas en el teléfono, no en Cloud Functions | funcionan sin internet y sin plan de pago |
| Umbrales en base de datos, no en constantes | el usuario los edita sin reinstalar (RF-CFG-03) |
| Modelos opcionales | la app es útil desde el día uno aunque los modelos aún no existan |
| Borrado lógico | permite sincronizar borrados entre teléfonos sin perder auditoría |
