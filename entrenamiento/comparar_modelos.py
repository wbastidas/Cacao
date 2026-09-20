"""
comparar_modelos.py — CacaoTrace

Compara un modelo NUEVO contra el que ya está en la app, sobre EL MISMO conjunto de
prueba, y dice si conviene publicarlo. Implementa el paso 4 del ciclo de mejora
continua (§8.3 de la ERS): "solo se publica si mejora".

Sin esto es fácil engañarse: un modelo entrenado con más fotos casi siempre luce mejor
en SU propio conjunto de prueba, porque el conjunto también cambió. Aquí los dos se
miden con las mismas imágenes.

Uso:
    python comparar_modelos.py \
        --actual android/app/src/main/assets/modelos/mazorca/modelo.tflite \
        --nuevo salidas/mazorca/mazorca_int8.tflite \
        --datos datos/mazorca/test \
        --etiquetas salidas/mazorca/etiquetas.txt
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import tensorflow as tf
from tensorflow import keras

# Cuánto tiene que mejorar para que valga la pena publicar y que el usuario descargue
# un archivo nuevo. Por debajo de esto la diferencia es ruido del conjunto de prueba.
MEJORA_MINIMA = 0.01


def predecir_tflite(ruta: Path, imagenes: np.ndarray) -> np.ndarray:
    try:
        interp = tf.lite.Interpreter(model_path=str(ruta))
        interp.allocate_tensors()
    except RuntimeError:
        interp = tf.lite.Interpreter(
            model_path=str(ruta),
            experimental_op_resolver_type=tf.lite.experimental.OpResolverType.BUILTIN_REF)
        interp.allocate_tensors()
    ent, sal = interp.get_input_details()[0], interp.get_output_details()[0]
    preds = []
    for i in range(len(imagenes)):
        dato = imagenes[i:i + 1]
        if ent["dtype"] == np.uint8:
            escala, cero = ent["quantization"]
            dato = (dato / escala + cero) if escala else dato
        interp.set_tensor(ent["index"], dato.astype(ent["dtype"]))
        interp.invoke()
        preds.append(int(np.argmax(interp.get_tensor(sal["index"])[0])))
    return np.array(preds)


def metricas(y_real, y_pred, clases):
    from sklearn.metrics import recall_score
    exactitud = float((y_pred == y_real).mean())
    recalls = recall_score(y_real, y_pred, labels=list(range(len(clases))),
                           average=None, zero_division=0)
    return exactitud, {c: float(r) for c, r in zip(clases, recalls)}


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--actual", type=Path, help="modelo que hoy tiene la app (opcional)")
    p.add_argument("--nuevo", required=True, type=Path)
    p.add_argument("--datos", required=True, type=Path, help="carpeta test/<clase>/*.jpg")
    p.add_argument("--etiquetas", required=True, type=Path)
    p.add_argument("--img", type=int, default=224)
    p.add_argument("--criticas", nargs="*", default=[],
                   help="clases que no pueden empeorar, ej. monilia fitoftora")
    a = p.parse_args(argv)

    clases = a.etiquetas.read_text(encoding="utf-8").split()
    ds = keras.utils.image_dataset_from_directory(
        a.datos, image_size=(a.img, a.img), batch_size=32,
        label_mode="int", shuffle=False, class_names=clases)

    xs, ys = [], []
    for x, y in ds.unbatch():
        xs.append(x.numpy())
        ys.append(int(y))
    xs, ys = np.stack(xs), np.array(ys)
    print(f"Conjunto de prueba: {len(ys)} imágenes, {len(clases)} clases")

    ex_nuevo, rec_nuevo = metricas(ys, predecir_tflite(a.nuevo, xs), clases)
    print(f"\nNUEVO   exactitud {ex_nuevo:.4f}")
    for c, r in rec_nuevo.items():
        print(f"        recall {c:<16} {r:.4f}")

    if not a.actual or not a.actual.exists():
        print("\nNo hay modelo anterior con el que comparar: este pasa a ser el de "
              "referencia. Guárdalo junto a su metadatos.json.")
        return 0

    ex_act, rec_act = metricas(ys, predecir_tflite(a.actual, xs), clases)
    print(f"\nACTUAL  exactitud {ex_act:.4f}")
    for c, r in rec_act.items():
        print(f"        recall {c:<16} {r:.4f}")

    delta = ex_nuevo - ex_act
    print(f"\nDiferencia de exactitud: {delta:+.4f}")

    empeoradas = [c for c in a.criticas
                  if c in rec_nuevo and rec_nuevo[c] < rec_act.get(c, 0.0) - 1e-9]
    if empeoradas:
        print("NO PUBLICAR: empeora en clases críticas: " + ", ".join(empeoradas))
        print("Un modelo que detecta peor la monilia es peor modelo aunque suba la "
              "exactitud general, porque el error caro es no ver la enfermedad.")
        veredicto = False
    elif delta >= MEJORA_MINIMA:
        print(f"PUBLICAR: mejora {delta*100:.1f} puntos (mínimo {MEJORA_MINIMA*100:.0f}).")
        veredicto = True
    else:
        print(f"NO PUBLICAR: la mejora ({delta*100:.1f} puntos) no llega al mínimo de "
              f"{MEJORA_MINIMA*100:.0f}. No vale la pena que el usuario descargue un "
              "archivo nuevo por eso.")
        veredicto = False

    Path("comparacion.json").write_text(json.dumps({
        "actual": {"archivo": str(a.actual), "exactitud": ex_act, "recall": rec_act},
        "nuevo": {"archivo": str(a.nuevo), "exactitud": ex_nuevo, "recall": rec_nuevo},
        "diferencia": delta, "publicar": veredicto,
        "clases_criticas_empeoradas": empeoradas,
    }, indent=2, ensure_ascii=False), encoding="utf-8")
    print("Detalle guardado en comparacion.json")
    return 0 if veredicto else 2


if __name__ == "__main__":
    raise SystemExit(main())
