# Entrenamiento de los modelos de visión — CacaoTrace

Guía completa para entrenar los cuatro modelos que la app usa **dentro del teléfono,
sin internet**, sin instalar nada en tu computadora.

---

## 1. Qué modelos hay y qué tan buenos deben ser

| Modelo | Qué responde | Tipo | Meta mínima para activarlo en la app |
|---|---|---|---|
| **M1 mazorca** | ¿está sana, tiene monilia, tiene fitóftora u otra cosa? | Clasificación 224 px | exactitud ≥ 90 % **y** recall de monilia y fitóftora ≥ 90 % |
| **M2 prueba de corte** | ¿qué es cada uno de los 100 granos del tablero? | Detección YOLO11n 640 px | mAP50 ≥ 0,80, error de conteo ≤ 3 granos, error del % fermentado ≤ 5 puntos |
| **M3 tostado** | ¿crudo, ligero, medio, oscuro o quemado? | Clasificación 224 px | exactitud ≥ 85 % |
| **M4 chocolate** | ¿atemperado bien, fat bloom, sugar bloom o sin brillo? | Clasificación 224 px | exactitud ≥ 85 % |

Estas metas están escritas dentro de `entrenar_clasificador.py` y
`entrenar_detector_corte.py`. **Los scripts las comprueban solos** y terminan con
código 2 si el modelo no las alcanza, para que no puedas instalar por error un modelo
que todavía no sirve.

> La humedad del grano y el cadmio **no** se estiman con fotos. Se miden con medidor y
> con laboratorio acreditado. Ningún modelo de este repositorio intenta adivinarlos.

---

## 2. Archivos de esta carpeta

| Archivo | Para qué sirve |
|---|---|
| `preparar_dataset.py` | Une tus fotos con datasets abiertos, unifica los nombres de clase, quita duplicados y divide en entrenamiento / validación / prueba |
| `entrenar_clasificador.py` | Entrena M1, M3 y M4; exporta a `.tflite` y verifica la conversión |
| `entrenar_detector_corte.py` | Entrena M2 (YOLO11) y mide el error de conteo real |
| `comparar_modelos.py` | Compara el modelo nuevo contra el que ya tiene la app; dice si vale la pena publicarlo |
| `calificar_corte.py` + `norma_inen176.json` | Convierte el conteo de granos en % y en grado según la norma |
| `herramientas/generar_datos_sinteticos.py` | Imágenes falsas para probar el pipeline sin tener fotos |
| `herramientas/instalar_modelo.py` | Copia el modelo entrenado a la carpeta de recursos de la app |
| `herramientas/smoke_test.sh` | Corre todo de punta a punta en ~5 minutos y avisa si algo se rompió |
| `pruebas/` | Tests de la norma, compartidos con la implementación Dart de la app |
| `CacaoTrace_Colab.ipynb` | El mismo proceso, en un cuaderno para Google Colab |

---

## 3. El camino corto: probar que todo funciona antes de tener fotos

Esto no entrena un modelo útil. Comprueba que el entorno está bien y que las piezas
encajan, para que cuando tengas fotos reales sepas que cualquier problema es de tus
datos y no del código.

```bash
cd entrenamiento
pip install -r requirements.txt
bash herramientas/smoke_test.sh
```

Debe terminar con **TODO EN ORDEN**. Si falla ahí, no sigas: arregla el entorno primero.

---

## 4. El proceso completo, paso a paso

### Paso 0 — Tomar las fotos bien

Esto decide más el resultado que cualquier ajuste del entrenamiento. Un modelo
entrenado con fotos descuidadas falla en cuanto cambia la luz.

- **Fondo blanco mate.** Una cartulina sirve. Nada de fondos con dibujos.
- **Luz LED de 5000–6500 K**, idealmente una caja de luz casera. **Sin flash**: el flash
  crea brillos que el modelo confunde con el velo blanco de la monilia.
- **Teléfono a 30 cm y perpendicular** al objeto, no en diagonal.
- **Tarjeta de color de referencia visible en cada foto.** Es lo que permite corregir
  después las diferencias de color entre teléfonos y entre horas del día.
- Para la prueba de corte: **tablero de 10 × 10**, granos cortados a lo largo y con la
  cara interna hacia arriba. La app imprime esta plantilla (RF-PRC-02).
- **Varía a propósito** lo que va a variar en la vida real: hora del día, mazorcas de
  distintos tamaños, algo de suciedad. Un dataset demasiado limpio produce un modelo
  que solo funciona en la foto de estudio.

**Cuántas fotos necesitas:**

| Modelo | Mínimo para empezar | Recomendado |
|---|---|---|
| M1 mazorca | 100 por clase (los datasets abiertos ya cubren buena parte) + 30 tuyas | 300+ por clase |
| M2 prueba de corte | 40 tableros etiquetados (≈ 4.000 granos) | 100+ tableros |
| M3 tostado | 50 por clase | 200 por clase |
| M4 chocolate | 50 por clase | 200 por clase |

La clase más escasa marca el techo: si tienes 400 fotos de mazorca sana y 20 de
fitóftora, el modelo no va a reconocer la fitóftora por mucho que entrenes.

### Paso 1 — Organizar las fotos

Una carpeta por **origen**, y dentro una carpeta por **clase**:

```
fuentes/
  fotos_app/                 ← lo que exportaste desde CacaoTrace
    sana/  monilia/  fitoftora/  otro/
  CocoaMoniliaDataSet/       ← dataset abierto
    h0/  m1/  m2/  m3/
```

Las fotos que exporta la app ya vienen con esa estructura desde
`Drive/CacaoTrace/Dataset/mazorca/`.

### Paso 2 — Escribir el mapeo de clases

Cada dataset llama distinto a lo mismo. `mapeo_mazorca_ejemplo.json` muestra el formato:

```json
{
  "CocoaMoniliaDataSet": {"h0": "sana", "m1": "monilia", "m2": "monilia", "m3": "monilia"},
  "Cacao_Diseases_Pests": {"HEALTHY": "sana", "FROSTYPOD": "monilia",
                           "BLACKPOD": "fitoftora", "MIRID": "otro"},
  "fotos_app": {"sana": "sana", "monilia": "monilia", "fitoftora": "fitoftora", "otro": "otro"}
}
```

Pon `null` en vez del nombre para descartar una clase que no te interesa.

### Paso 3 — Preparar el dataset

```bash
python preparar_dataset.py \
    --fuentes fuentes \
    --mapeo mapeo_mazorca.json \
    --salida datos/mazorca \
    --lado_max 1024 \
    --agrupar_por carpeta
```

Qué hace, y por qué importa cada cosa:

- **Corrige la rotación EXIF.** Sin esto, las fotos verticales del teléfono entran
  acostadas y el modelo aprende basura.
- **Elimina duplicados exactos** por MD5. Al mezclar datasets abiertos es muy común que
  la misma imagen aparezca dos veces; si una copia queda en entrenamiento y otra en
  prueba, las métricas mienten.
- **`--agrupar_por`** mantiene juntas las fotos de una misma toma. Si fotografías la
  misma mazorca desde tres ángulos y una foto va a entrenamiento y otra a prueba, el
  modelo parecerá mucho mejor de lo que es. Esto se llama *fuga de datos* y es el error
  más común al entrenar con datasets pequeños.
- **Divide 70 / 15 / 15** manteniendo la proporción de cada clase en cada partición.
- Escribe `manifiesto.csv` e `informe.json` para que la división sea reproducible.

Al final te avisa si alguna clase quedó con menos de 50 imágenes de entrenamiento.
Hazle caso.

### Paso 4 — Entrenar

```bash
python entrenar_clasificador.py \
    --datos datos/mazorca --tarea mazorca \
    --arquitectura efficientnetv2b0 \
    --epocas 15 --epocas_ajuste 15
```

Ocurren dos fases, automáticas:

1. **Cabeza nueva (15 épocas).** Se parte de una red que ya aprendió a ver bordes,
   texturas y formas con millones de fotos (ImageNet). Se congela toda esa parte y solo
   se entrena la última capa, la que separa tus cuatro clases. Es rápido y estable.
2. **Ajuste fino (15 épocas).** Se descongelan las últimas 40 capas y se reentrenan con
   un paso de aprendizaje 100 veces más pequeño (1e-5 en vez de 1e-3), para afinar sin
   destruir lo aprendido. Las capas de *BatchNorm* quedan congeladas: con lotes
   pequeños, descongelarlas desestabiliza el entrenamiento.

Además, sin que tengas que pedirlo:

- **Compensa clases desbalanceadas.** Si tienes 400 fotos de sana y 60 de fitóftora, los
  errores en fitóftora pesan más, para que el modelo no aprenda a decir "sana" siempre.
- **Aumentación de datos**: giros, zoom, recortes, brillo y contraste. Simula las
  variaciones reales de luz y encuadre. Solo actúa al entrenar; en el teléfono no se
  ejecuta.
- **Para temprano** si la validación deja de mejorar, y se queda con el mejor momento.

### Paso 5 — Leer el resultado

El script imprime un reporte por clase y guarda:

| Archivo | Qué mirar |
|---|---|
| `reporte.txt` | El **recall** de monilia y fitóftora. Es lo que importa |
| `matriz_confusion.png` | Con qué clase se confunde cada una |
| `curvas.png` | Si la curva de validación se separa de la de entrenamiento, hay sobreajuste |
| `metadatos.json` | Versión, clases, umbral, y si **cumple las metas de liberación** |

**Por qué el recall importa más que la exactitud.** Si el modelo dice "sana" a una
mazorca con monilia, esa mazorca entra al fermentador y puede arruinar el lote. Si dice
"monilia" a una sana, pierdes una mazorca. Los dos errores no cuestan lo mismo. Por eso
la meta de M1 exige recall ≥ 90 % en monilia y fitóftora, no solo exactitud general.

El script termina diciendo **CUMPLE** o **NO CUMPLE**. Si no cumple, te dice exactamente
qué falla. Mientras tanto la app funciona perfectamente con registro y conteo manual.

### Paso 6 — Verificar la conversión a `.tflite`

Esto lo hace el script solo, pero conviene entender qué comprueba.

El modelo entrenado (Keras) se convierte a dos versiones:

| Variante | Tamaño | Cuándo usarla |
|---|---|---|
| `fp16` | ~12 MB | Siempre segura. Es la de referencia |
| `int8` | ~7 MB | Más rápida y pequeña. Solo si no pierde más de 2 puntos |

Luego mide la **concordancia**: ¿el `.tflite` responde lo mismo que el modelo Keras
sobre las mismas imágenes? Si la concordancia baja de 0,98, la conversión alteró el
modelo y el script se niega a recomendarlo.

> **Hallazgo de nuestras pruebas:** con `efficientnetv2b0` la concordancia fue **1,000**
> (conversión exacta). Con `mobilenetv3` el `.tflite` int8 **no llegó ni a cargar**
> (el acelerador XNNPACK falla al preparar el grafo por la capa *hard-swish*) y el fp16
> discrepó del original. **Usa `efficientnetv2b0`**, que además es la primera opción de
> la ERS. Si aun así prefieres MobileNetV3, mira la concordancia que reporta el script
> antes de instalar nada.

### Paso 7 — Comparar contra el modelo que ya tiene la app

```bash
python comparar_modelos.py \
    --actual ../app/assets/modelos/mazorca/modelo.tflite \
    --nuevo salidas/mazorca/mazorca_int8.tflite \
    --datos datos/mazorca/test \
    --etiquetas salidas/mazorca/etiquetas.txt \
    --criticas monilia fitoftora
```

Mide los dos modelos con **las mismas imágenes**. Sin esto es fácil engañarse: un modelo
entrenado con más fotos casi siempre luce mejor en *su propio* conjunto de prueba,
porque el conjunto también cambió.

Y si el modelo nuevo detecta **peor** la monilia, dice **NO PUBLICAR** aunque la
exactitud general haya subido.

### Paso 8 — Instalar en la app

```bash
python herramientas/instalar_modelo.py --tarea mazorca
```

Copia `modelo.tflite`, `etiquetas.txt` y `metadatos.json` a
`app/assets/modelos/mazorca/`. **Se niega a instalar un modelo que no cumple las metas**
a menos que pases `--forzar`.

---

## 5. El modelo M2 (prueba de corte) es distinto

M1, M3 y M4 clasifican la foto entera. M2 tiene que **encontrar cada grano** dentro de
la foto del tablero y clasificarlo, uno por uno. Eso exige etiquetar caja por caja.

### Etiquetar

Usa **Roboflow** (gratis para proyectos públicos) o **CVAT** (gratis, se instala).
Dibuja una caja alrededor de cada grano y elige su clase. Son 100 cajas por tablero:
los primeros 5 tableros toman una tarde, después se vuelve rápido.

Exporta en formato **YOLOv8/YOLO11** y descomprime en `datos/corte/`. El orden de
`names` en `data.yaml` **no puede cambiar nunca**, porque la app guarda los índices, no
los nombres.

### Entrenar y medir

```bash
python entrenar_detector_corte.py --datos datos/corte/data.yaml --epocas 100
python entrenar_detector_corte.py --evaluar_conteo datos/corte/data.yaml
```

El segundo comando es el importante. El mAP50 puede verse bien y el conteo seguir mal
—por ejemplo si el modelo parte un grano en dos cajas o junta dos en una—. `--evaluar_conteo`
mide directamente las dos cosas que decide la app:

- **error de conteo**: cuántos granos de más o de menos detecta por tablero (meta: ≤ 3);
- **error del % de fermentados**: cuántos puntos se desvía del conteo del experto
  (meta: ≤ 5). Ese porcentaje es el que decide el grado del lote.

**Sobre el archivo exportado.** Ultralytics cambió su API de exportación entre la
versión 8.3 y la 8.4: `half=True` con `format="tflite"` pasó a ser `quantize=` con
`format="litert"`, y **LiteRT ya no admite FP16** (solo INT8, `w8a16`, `w8a32` o FP32).
El script prueba las dos formas automáticamente, así que funciona con cualquiera de las
dos versiones; exporta en FP32, que es la referencia segura, y en INT8 si pasas `--int8`.

Probar con una foto suelta:

```bash
python entrenar_detector_corte.py --probar mi_tablero.jpg
```

Dibuja las cajas y además califica el resultado según la norma, igual que hará la app.

---

## 6. La tabla de la norma INEN 176

`norma_inen176.json` contiene dos perfiles:

- **`ccn51_referencia`** — requisitos para CCN-51: fermentado bueno ≥ 65 %,
  fermentado total ≥ 76 %, violeta ≤ 18 %, pizarroso ≤ 5 %, mohoso ≤ 1 %.
- **`grados_1_2_3`** — la tabla por grados: se prueba Grado 1, luego 2, luego 3, y gana
  el primero que cumpla; si no cumple ninguno, "Fuera de grado".

> **Estos valores son de referencia y hay que verificarlos contra el texto oficial
> vigente de la NTE INEN 176 antes de usarlos con fines comerciales.** Se omitió a
> propósito el mínimo de 11 % de "ligera fermentación" porque penalizaría lotes mejor
> fermentados; confirma su interpretación con un catador certificado. La app permite
> editar esta tabla sin reinstalar (RF-CFG-03, RNF-12).

`calificar_corte.py` implementa el cálculo. La app tiene **la misma lógica en Dart**
leyendo **el mismo JSON**, y las dos comparten los casos de prueba de
`pruebas/casos_norma.json`: si una se desvía de la otra, sus tests fallan.

```bash
python calificar_corte.py '{"bien_fermentado":70,"violeta":12,"pizarroso":6,"mohoso":1}'
python calificar_corte.py --listar-perfiles
pytest pruebas -v
```

---

## 7. El ciclo de mejora continua

```
  usuario toma fotos y corrige en la app
            ↓
  Drive/CacaoTrace/Dataset/<tarea>/<clase>/
            ↓  (cada ~200 fotos nuevas)
  preparar_dataset.py  →  entrenar_*.py  →  comparar_modelos.py
            ↓  (solo si mejora)
  instalar_modelo.py  →  la app descarga la versión nueva con WiFi
            ↓
  se conserva la anterior para poder volver atrás
```

Cada corrección que hace el usuario en la app es una etiqueta gratis y **de tu propio
entorno de trabajo**, que es justo lo que más le falta a los datasets abiertos.

---

## 8. Licencias de los datos — léelo antes de vender

- **Santos et al. 2019 (Mendeley, prueba de corte)**: licencia **CC BY-NC-ND 4.0**. No
  permite uso comercial ni obras derivadas. Úsalo solo para aprender, o pide permiso a
  los autores. Para una app que venda o apoye ventas, entrena con tus propias fotos.
- **Roboflow "Cacao Diseases and Pests Attack"**: **CC BY 4.0**, uso comercial permitido
  citando a los autores.
- **Ultralytics YOLO11**: **AGPL-3.0**. Afecta a la distribución de modelos entrenados
  con él dentro de un producto comercial. Revisa sus condiciones o adquiere su licencia
  empresarial.
- Verifica la licencia de cada dataset en su propia página antes de usarlo. Cambian.

---

## 9. Problemas frecuentes

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| Exactitud altísima (99 %) con pocas fotos | Fuga de datos: fotos casi idénticas en entrenamiento y prueba | Usa `--agrupar_por` |
| El modelo acierta en Colab y falla en el teléfono | Luz distinta a la del entrenamiento | Caja de luz, tarjeta de color, y añade fotos de la condición que falla |
| Recall bajo en una sola clase | Muy pocas fotos de esa clase | Más fotos de esa clase; el balanceo automático no hace milagros |
| `int8` pierde muchos puntos | La cuantización no le sienta a esa arquitectura | Usa `fp16`; el script ya lo recomienda solo |
| El `.tflite` no carga | Conversión rota (típico de MobileNetV3 int8) | Usa `efficientnetv2b0` |
| `quantize=16 (FP16) is not supported` al exportar M2 | Ultralytics 8.4 quitó el FP16 de LiteRT | Ya está resuelto en el script; si lo ves, actualiza el repositorio |
| La curva de validación sube y luego baja | Sobreajuste | Menos épocas de ajuste fino, o más fotos |
| `CUDA out of memory` en Colab | Lote demasiado grande | `--lote 16` o `--lote 8` |
