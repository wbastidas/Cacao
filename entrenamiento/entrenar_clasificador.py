"""
entrenar_clasificador.py — CacaoTrace

Entrena un clasificador de imágenes por transferencia de aprendizaje y lo exporta a
TensorFlow Lite (LiteRT) para que corra SIN INTERNET dentro del teléfono.

Sirve para los modelos de la §8.1 de la ERS:
  M1  mazorca    sana / monilia / fitoftora / otro          meta: exactitud ≥ 90 %,
                                                            recall de monilia y
                                                            fitoftora ≥ 90 %
  M3  tostado    crudo / ligero / medio / oscuro / quemado  meta: exactitud ≥ 85 %
  M4  chocolate  atemperado_ok / fat_bloom / sugar_bloom /
                 sin_brillo                                 meta: exactitud ≥ 85 %
  M2b grano      clasifica un grano ya recortado (opcional)

Entrada: la carpeta que produce preparar_dataset.py
         datos/<tarea>/{train,val,test}/<clase>/*.jpg

Uso típico (Google Colab con GPU):
    python entrenar_clasificador.py --datos datos/mazorca --tarea mazorca \
        --arquitectura efficientnetv2b0 --epocas 15 --epocas_ajuste 15

Salidas en salidas/<tarea>/:
    modelo.keras, <tarea>_fp16.tflite, <tarea>_int8.tflite, etiquetas.txt,
    metadatos.json, reporte.txt, matriz_confusion.png, curvas.png

El script termina con código 0 si el modelo alcanza las metas de liberación y con
código 2 si no las alcanza, para poder encadenarlo en un script sin revisar a ojo.
"""
from __future__ import annotations

import argparse
import json
from datetime import datetime
from pathlib import Path

import numpy as np
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers

AUTOTUNE = tf.data.AUTOTUNE

# Metas mínimas de liberación por tarea (§8.1 de la ERS). 'criticas' son las clases
# cuyo fallo tiene consecuencias caras: no detectar monilia arruina el lote entero.
METAS = {
    "mazorca": {"exactitud": 0.90, "recall_critico": 0.90,
                "criticas": ["monilia", "fitoftora"]},
    "tostado": {"exactitud": 0.85, "recall_critico": 0.0, "criticas": []},
    "chocolate": {"exactitud": 0.85, "recall_critico": 0.0, "criticas": []},
    "grano": {"exactitud": 0.85, "recall_critico": 0.0, "criticas": []},
}
# Cuántos puntos de exactitud se acepta perder al cuantizar a int8 (§ COMPILAR.md).
TOLERANCIA_INT8 = 0.02
# Concordancia mínima entre el .tflite y el modelo Keras original. Por debajo de
# esto la conversión alteró el modelo y no debe llevarse a la app.
UMBRAL_CONCORDANCIA = 0.98


# ---------------------------------------------------------------- datos
def cargar_datos(carpeta: Path, img: int, lote: int, semilla: int):
    if not (carpeta / "train").exists():
        raise SystemExit(f"[ERROR] No existe {carpeta/'train'}. "
                         "¿Corriste preparar_dataset.py primero?")
    comunes = dict(image_size=(img, img), batch_size=lote, label_mode="int", seed=semilla)
    train = keras.utils.image_dataset_from_directory(carpeta / "train", shuffle=True, **comunes)
    clases = train.class_names
    val = keras.utils.image_dataset_from_directory(carpeta / "val", shuffle=False, **comunes)
    test = None
    ruta_test = carpeta / "test"
    if ruta_test.exists() and any(ruta_test.iterdir()):
        test = keras.utils.image_dataset_from_directory(ruta_test, shuffle=False, **comunes)
    else:
        print("[AVISO] No hay conjunto de prueba; se evaluará con el de validación, "
              "que el entrenamiento ya vio indirectamente. Las cifras serán optimistas.")
    return train, val, test, clases


def pesos_por_clase(train_ds, n_clases: int):
    """Compensa clases desbalanceadas (por ejemplo, pocas mazorcas con fitóftora)."""
    conteo = np.zeros(n_clases)
    for _, y in train_ds.unbatch():
        conteo[int(y)] += 1
    total = conteo.sum()
    pesos = {i: float(total / (n_clases * c)) if c > 0 else 1.0
             for i, c in enumerate(conteo)}
    return pesos, conteo


def aumentacion():
    """Simula las variaciones reales: luz, ángulo, distancia, orientación."""
    return keras.Sequential([
        layers.RandomFlip("horizontal_and_vertical"),
        layers.RandomRotation(0.15),
        layers.RandomZoom(0.15),
        layers.RandomTranslation(0.1, 0.1),
        layers.RandomContrast(0.2),
        layers.RandomBrightness(0.2, value_range=(0, 255)),
    ], name="aumentacion")


# ---------------------------------------------------------------- modelo
def construir_modelo(arquitectura: str, img: int, n_clases: int, pesos_imagenet: bool):
    pesos = "imagenet" if pesos_imagenet else None
    forma = (img, img, 3)
    if arquitectura == "mobilenetv3":
        base = keras.applications.MobileNetV3Large(
            input_shape=forma, include_top=False, weights=pesos, include_preprocessing=True)
    elif arquitectura == "efficientnetv2b0":
        base = keras.applications.EfficientNetV2B0(
            input_shape=forma, include_top=False, weights=pesos, include_preprocessing=True)
    else:
        raise ValueError("arquitectura debe ser mobilenetv3 o efficientnetv2b0")
    base.trainable = False

    # La entrada son píxeles 0-255 y el preprocesado va DENTRO del modelo: así la app
    # solo tiene que redimensionar la foto, sin replicar ninguna normalización.
    entrada = keras.Input(shape=forma, name="imagen")
    x = aumentacion()(entrada)
    x = base(x, training=False)
    x = layers.GlobalAveragePooling2D()(x)
    x = layers.Dropout(0.3)(x)
    salida = layers.Dense(n_clases, activation="softmax", name="probabilidades")(x)
    return keras.Model(entrada, salida), base


def compilar(modelo, lr):
    modelo.compile(optimizer=keras.optimizers.Adam(lr),
                   loss="sparse_categorical_crossentropy",
                   metrics=["accuracy"])


# ---------------------------------------------------------------- evaluación
def predecir(modelo, ds):
    y_real, y_pred, confianzas = [], [], []
    for x, y in ds:
        p = modelo.predict(x, verbose=0)
        y_real.extend(y.numpy().tolist())
        y_pred.extend(np.argmax(p, axis=1).tolist())
        confianzas.extend(np.max(p, axis=1).tolist())
    return np.array(y_real), np.array(y_pred), np.array(confianzas)


def evaluar(modelo, ds, clases, carpeta_salida: Path):
    from sklearn.metrics import classification_report, confusion_matrix, recall_score
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    y_real, y_pred, _ = predecir(modelo, ds)
    etiquetas = list(range(len(clases)))
    reporte = classification_report(y_real, y_pred, labels=etiquetas,
                                    target_names=clases, digits=3, zero_division=0)
    cm = confusion_matrix(y_real, y_pred, labels=etiquetas)
    (carpeta_salida / "reporte.txt").write_text(reporte, encoding="utf-8")
    print(reporte)

    fig, ax = plt.subplots(figsize=(1.2 * len(clases) + 3, 1.2 * len(clases) + 2))
    ax.imshow(cm, cmap="Blues")
    ax.set_xticks(etiquetas, clases, rotation=45, ha="right")
    ax.set_yticks(etiquetas, clases)
    for i in etiquetas:
        for j in etiquetas:
            ax.text(j, i, cm[i, j], ha="center", va="center")
    ax.set_xlabel("Predicción")
    ax.set_ylabel("Real")
    ax.set_title("Matriz de confusión")
    fig.tight_layout()
    fig.savefig(carpeta_salida / "matriz_confusion.png", dpi=120)
    plt.close(fig)

    exactitud = float(np.mean(y_real == y_pred)) if len(y_real) else 0.0
    recalls = recall_score(y_real, y_pred, labels=etiquetas, average=None, zero_division=0)
    por_clase = {c: round(float(r), 4) for c, r in zip(clases, recalls)}
    return exactitud, por_clase


def graficar_historial(historiales, carpeta_salida: Path):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    acc, val_acc, loss, val_loss = [], [], [], []
    for h in historiales:
        acc += h.history["accuracy"]
        val_acc += h.history["val_accuracy"]
        loss += h.history["loss"]
        val_loss += h.history["val_loss"]
    fig, (a1, a2) = plt.subplots(1, 2, figsize=(11, 4))
    a1.plot(acc, label="entrenamiento"); a1.plot(val_acc, label="validación")
    a1.set_title("Exactitud"); a1.set_xlabel("época"); a1.legend()
    a2.plot(loss, label="entrenamiento"); a2.plot(val_loss, label="validación")
    a2.set_title("Pérdida"); a2.set_xlabel("época"); a2.legend()
    fig.tight_layout(); fig.savefig(carpeta_salida / "curvas.png", dpi=110); plt.close(fig)


# ---------------------------------------------------------------- exportación
def exportar_tflite(modelo, train_ds, tarea: str, carpeta_salida: Path):
    """
    Exporta a fp16 (seguro) y a int8 (más rápido y pequeño, pero puede perder exactitud).
    Las capas de aumentación solo actúan al entrenar; en inferencia son identidad, así
    que el .tflite no las ejecuta.
    """
    rutas = {}

    conv = tf.lite.TFLiteConverter.from_keras_model(modelo)
    conv.optimizations = [tf.lite.Optimize.DEFAULT]
    conv.target_spec.supported_types = [tf.float16]
    ruta = carpeta_salida / f"{tarea}_fp16.tflite"
    ruta.write_bytes(conv.convert())
    rutas["fp16"] = ruta

    def representativo():
        for x, _ in train_ds.unbatch().take(200):
            yield [tf.expand_dims(tf.cast(x, tf.float32), 0)]

    try:
        conv = tf.lite.TFLiteConverter.from_keras_model(modelo)
        conv.optimizations = [tf.lite.Optimize.DEFAULT]
        conv.representative_dataset = representativo
        conv.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
        conv.inference_input_type = tf.uint8    # la app pasa píxeles 0-255
        conv.inference_output_type = tf.float32
        ruta = carpeta_salida / f"{tarea}_int8.tflite"
        ruta.write_bytes(conv.convert())
        rutas["int8"] = ruta
    except Exception as e:
        print(f"[AVISO] No se pudo cuantizar a int8 ({e}). Usa la versión fp16.")

    for k, r in rutas.items():
        print(f"TFLite {k}: {r}  ({r.stat().st_size / 1e6:.1f} MB)")
    return rutas


def _abrir_interprete(ruta: Path):
    """
    Abre el .tflite. Algunos modelos int8 (sobre todo MobileNetV3, por su capa
    hard-swish) rompen el acelerador XNNPACK al preparar el grafo; en ese caso se
    reintenta con el intérprete de referencia. Si ni así carga, el archivo no sirve
    para la app y hay que saberlo AQUÍ, no en el teléfono del usuario.
    """
    try:
        interp = tf.lite.Interpreter(model_path=str(ruta))
        interp.allocate_tensors()
        return interp, "normal"
    except RuntimeError as e:
        print(f"  [AVISO] {ruta.name} no carga con el acelerador por defecto ({e}). "
              "Se reintenta sin XNNPACK.")
    try:
        interp = tf.lite.Interpreter(
            model_path=str(ruta),
            experimental_op_resolver_type=tf.lite.experimental.OpResolverType.BUILTIN_REF)
        interp.allocate_tensors()
        return interp, "sin_xnnpack"
    except Exception as e:
        print(f"  [ERROR] {ruta.name} NO SE PUEDE CARGAR ({e}). "
              "No lo lleves a la app; usa la otra variante.")
        return None, "no_carga"


def verificar_tflite(ruta: Path, modelo, ds, n_max: int = 200) -> dict:
    """
    Comprueba que el .tflite responde LO MISMO que el modelo Keras del que salió.

    Lo importante aquí no es la exactitud (eso ya lo midió `evaluar`), sino la
    CONCORDANCIA: si la conversión o la cuantización rompieron algo, el .tflite
    discrepará del Keras aunque su exactitud siga pareciendo razonable. Una
    concordancia por debajo de ~0,98 en fp16 indica un problema de conversión.
    """
    interp, modo = _abrir_interprete(ruta)
    if interp is None:
        return {"exactitud": 0.0, "concordancia_keras": 0.0, "imagenes": 0,
                "carga": "no_carga", "usable": False}

    ent, sal = interp.get_input_details()[0], interp.get_output_details()[0]

    imagenes, reales = [], []
    for x, y in ds.unbatch().take(n_max):
        imagenes.append(x.numpy())
        reales.append(int(y))
    if not imagenes:
        return {"exactitud": 0.0, "concordancia_keras": 0.0, "imagenes": 0,
                "carga": modo, "usable": False}

    lote = np.stack(imagenes)
    pred_keras = np.argmax(modelo.predict(lote, verbose=0), axis=1)

    pred_tflite = []
    for i in range(len(lote)):
        dato = lote[i:i + 1]
        if ent["dtype"] == np.uint8:
            escala, cero = ent["quantization"]
            dato = (dato / escala + cero) if escala else dato
        interp.set_tensor(ent["index"], dato.astype(ent["dtype"]))
        interp.invoke()
        pred_tflite.append(int(np.argmax(interp.get_tensor(sal["index"])[0])))
    pred_tflite = np.array(pred_tflite)

    exactitud = float((pred_tflite == np.array(reales)).mean())
    concordancia = float((pred_tflite == pred_keras).mean())
    print(f"Verificación {ruta.name}: {len(lote)} imágenes | "
          f"exactitud {exactitud:.3f} | concordancia con Keras {concordancia:.3f}"
          + ("" if modo == "normal" else f" | carga {modo}"))
    if concordancia < UMBRAL_CONCORDANCIA:
        print(f"  [AVISO] Concordancia baja (< {UMBRAL_CONCORDANCIA:.2f}): la conversión "
              "pudo alterar el modelo. No lo lleves a la app sin revisarlo.")
    return {"exactitud": round(exactitud, 4),
            "concordancia_keras": round(concordancia, 4),
            "imagenes": len(lote),
            "carga": modo,
            "usable": modo == "normal" and concordancia >= UMBRAL_CONCORDANCIA}


def revisar_metas(tarea: str, exactitud: float, recall_por_clase: dict) -> tuple[bool, list]:
    """Compara los resultados contra las metas de liberación de la §8.1 de la ERS."""
    meta = METAS.get(tarea, {"exactitud": 0.85, "recall_critico": 0.0, "criticas": []})
    problemas = []
    if exactitud < meta["exactitud"]:
        problemas.append(f"exactitud {exactitud:.3f} < meta {meta['exactitud']:.2f}")
    for clase in meta["criticas"]:
        if clase not in recall_por_clase:
            problemas.append(f"falta la clase crítica '{clase}' en el dataset")
            continue
        if recall_por_clase[clase] < meta["recall_critico"]:
            problemas.append(
                f"recall de '{clase}' {recall_por_clase[clase]:.3f} "
                f"< meta {meta['recall_critico']:.2f}")
    return not problemas, problemas


# ---------------------------------------------------------------- principal
def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--datos", required=True, type=Path)
    p.add_argument("--tarea", required=True, help="mazorca | tostado | chocolate | grano")
    p.add_argument("--arquitectura", default="efficientnetv2b0",
                   choices=["efficientnetv2b0", "mobilenetv3"])
    p.add_argument("--img", type=int, default=224)
    p.add_argument("--lote", type=int, default=32)
    p.add_argument("--epocas", type=int, default=15, help="fase 1: red base congelada")
    p.add_argument("--epocas_ajuste", type=int, default=15, help="fase 2: ajuste fino")
    p.add_argument("--capas_ajuste", type=int, default=40,
                   help="capas finales de la red base que se descongelan en la fase 2")
    p.add_argument("--paciencia", type=int, default=5)
    p.add_argument("--umbral_confianza", type=float, default=0.60,
                   help="por debajo de esto la app dirá 'No estoy seguro' (RN-16)")
    p.add_argument("--version", default=datetime.now().strftime("%Y.%m.%d"))
    p.add_argument("--salida", type=Path, default=None)
    p.add_argument("--sin_pesos_imagenet", action="store_true",
                   help="solo para probar el pipeline sin internet; en Colab NO lo uses")
    p.add_argument("--semilla", type=int, default=42)
    a = p.parse_args(argv)

    tf.keras.utils.set_random_seed(a.semilla)
    salida = a.salida or (Path("salidas") / a.tarea)
    salida.mkdir(parents=True, exist_ok=True)

    train, val, test, clases = cargar_datos(a.datos, a.img, a.lote, a.semilla)
    n = len(clases)
    if n < 2:
        raise SystemExit("[ERROR] Se necesitan al menos 2 clases para entrenar.")
    pesos, conteo = pesos_por_clase(train, n)
    print("Clases:", dict(zip(clases, conteo.astype(int).tolist())))
    train_p, val_p = train.prefetch(AUTOTUNE), val.prefetch(AUTOTUNE)

    modelo, base = construir_modelo(a.arquitectura, a.img, n, not a.sin_pesos_imagenet)
    callbacks = [
        keras.callbacks.EarlyStopping(monitor="val_loss", patience=a.paciencia,
                                      restore_best_weights=True),
        keras.callbacks.ModelCheckpoint(salida / "mejor.keras", monitor="val_loss",
                                        save_best_only=True),
        keras.callbacks.ReduceLROnPlateau(monitor="val_loss", factor=0.3, patience=2),
    ]

    # Fase 1: solo se entrena la "cabeza" nueva sobre características ya aprendidas.
    print("\n=== Fase 1: cabeza nueva, red base congelada ===")
    compilar(modelo, 1e-3)
    h1 = modelo.fit(train_p, validation_data=val_p, epochs=a.epocas,
                    class_weight=pesos, callbacks=callbacks)
    historiales = [h1]

    # Fase 2: ajuste fino de las últimas capas, con paso de aprendizaje muy pequeño.
    if a.epocas_ajuste > 0:
        print("\n=== Fase 2: ajuste fino de las últimas capas ===")
        base.trainable = True
        for capa in base.layers[:-a.capas_ajuste]:
            capa.trainable = False
        for capa in base.layers:
            # BatchNorm congelado = entrenamiento estable con lotes pequeños
            if isinstance(capa, layers.BatchNormalization):
                capa.trainable = False
        compilar(modelo, 1e-5)
        h2 = modelo.fit(train_p, validation_data=val_p,
                        epochs=len(h1.epoch) + a.epocas_ajuste,
                        initial_epoch=len(h1.epoch),
                        class_weight=pesos, callbacks=callbacks)
        historiales.append(h2)

    graficar_historial(historiales, salida)
    modelo.save(salida / "modelo.keras")

    ds_eval = test if test is not None else val
    exactitud, recall_por_clase = evaluar(modelo, ds_eval, clases, salida)
    rutas = exportar_tflite(modelo, train, a.tarea, salida)
    verificacion = {k: verificar_tflite(r, modelo, ds_eval) for k, r in rutas.items()}

    # Elegir qué archivo debe llevarse la app.
    usables = [k for k, v in verificacion.items() if v["usable"]]
    if not usables:
        print("[ERROR] Ninguna variante TFLite quedó usable. Revisa los avisos de arriba.")
        recomendado = None
    elif "int8" in usables and "fp16" in usables:
        perdida = verificacion["fp16"]["exactitud"] - verificacion["int8"]["exactitud"]
        if perdida <= TOLERANCIA_INT8:
            recomendado = "int8"   # mismo resultado, la mitad de peso y más rápido
        else:
            recomendado = "fp16"
            print(f"[AVISO] int8 pierde {perdida*100:.1f} puntos de exactitud frente a "
                  f"fp16 (tolerancia {TOLERANCIA_INT8*100:.0f}); se recomienda fp16.")
    else:
        recomendado = usables[0]
        print(f"Solo la variante {recomendado} quedó usable.")

    cumple, problemas = revisar_metas(a.tarea, exactitud, recall_por_clase)
    if recomendado is None:
        cumple = False
        problemas.append("ninguna variante TFLite se pudo cargar y verificar")

    (salida / "etiquetas.txt").write_text("\n".join(clases), encoding="utf-8")
    meta = {
        "tarea": a.tarea,
        "version": a.version,
        "arquitectura": a.arquitectura,
        "entrada": {"alto": a.img, "ancho": a.img, "canales": 3,
                    "formato": "RGB, píxeles 0-255 (el modelo normaliza internamente)"},
        "clases": clases,
        "umbral_confianza": a.umbral_confianza,
        "regla_app": "Si la probabilidad máxima < umbral_confianza, mostrar "
                     "'No estoy seguro' y pedir confirmación manual (RN-16).",
        "exactitud_test": round(exactitud, 4),
        "recall_por_clase": recall_por_clase,
        "verificacion_tflite": verificacion,
        "archivo_recomendado": (f"{a.tarea}_{recomendado}.tflite"
                                if recomendado else None),
        "cumple_metas_liberacion": cumple,
        "problemas": problemas,
        "evaluado_con": "test" if test is not None else "val",
        "imagenes_entrenamiento": dict(zip(clases, conteo.astype(int).tolist())),
        "fecha": datetime.now().isoformat(timespec="seconds"),
    }
    (salida / "metadatos.json").write_text(
        json.dumps(meta, indent=2, ensure_ascii=False), encoding="utf-8")

    print(f"\nArchivos para la app en: {salida.resolve()}")
    print(f"Archivo recomendado: {meta['archivo_recomendado']}")
    if cumple:
        print("METAS DE LIBERACIÓN: CUMPLE. El modelo puede activarse en la app.")
        return 0
    print("METAS DE LIBERACIÓN: NO CUMPLE todavía:")
    for x in problemas:
        print("  -", x)
    print("Toma más fotos de las clases flojas y vuelve a entrenar. "
          "Mientras tanto, la app funciona con conteo manual.")
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
