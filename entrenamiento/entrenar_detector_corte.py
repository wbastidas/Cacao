"""
entrenar_detector_corte.py — CacaoTrace (modelo M2: prueba de corte)

Detecta CADA grano cortado en la foto del tablero de 100 granos y lo clasifica, para
que la app cuente por clase y calcule el grado automáticamente (RF-PRC-03, RF-PRC-06).

Metas de liberación (§8.1 de la ERS):
    mAP50 ≥ 0,80
    error de conteo ≤ 3 granos por tablero
    % de fermentados con error ≤ 5 puntos frente al conteo de un experto

Requiere un dataset en formato YOLO (lo exporta Roboflow o CVAT):
    datos/corte/
      data.yaml
      train/images, train/labels
      valid/images, valid/labels
      test/images,  test/labels

data.yaml de ejemplo — el orden de 'names' debe ser SIEMPRE el mismo, porque la app
guarda los índices, no los nombres:

    path: datos/corte
    train: train/images
    val: valid/images
    test: test/images
    names: [bien_fermentado, ligeramente_fermentado, violeta, pizarroso,
            mohoso, dano_insectos, germinado, vano_plano, otro]

Uso (Colab con GPU):
    python entrenar_detector_corte.py --datos datos/corte/data.yaml --epocas 100
    python entrenar_detector_corte.py --probar foto_tablero.jpg
    python entrenar_detector_corte.py --evaluar_conteo datos/corte/data.yaml

AVISO DE LICENCIA: Ultralytics (YOLO11) es AGPL-3.0. Para distribuir un modelo
entrenado con él en un producto comercial, revisa sus condiciones o adquiere su
licencia empresarial.
"""
from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path

from calificar_corte import calificar

# Metas de liberación del modelo M2 (§8.1 de la ERS).
META_MAP50 = 0.80
META_ERROR_CONTEO = 3
GRANOS_TABLERO = 100


def _nombres_clases(modelo) -> list[str]:
    return [modelo.names[i] for i in sorted(modelo.names)]


def entrenar(a) -> int:
    from ultralytics import YOLO

    modelo = YOLO(a.base)   # yolo11n.pt: el más liviano, pensado para teléfono
    modelo.train(
        data=a.datos,
        epochs=a.epocas,
        imgsz=a.img,
        batch=a.lote,
        patience=a.paciencia,
        project=str(a.salida.parent),
        name=a.salida.name,
        exist_ok=True,
        seed=a.semilla,
        # Aumentación pensada para fotos de tablero con luz variable.
        # El COLOR es la clase (violeta vs. café vs. pizarra): por eso el tono (hsv_h)
        # casi no se toca; saturación y brillo sí, porque cambian con la luz del día.
        hsv_h=0.01,
        hsv_s=0.3,
        hsv_v=0.3,
        degrees=10,
        flipud=0.5,
        fliplr=0.5,
        mosaic=1.0,
        close_mosaic=10,
    )

    split = "test" if a.usar_test else "val"
    metricas = modelo.val(data=a.datos, split=split)
    map50 = float(metricas.box.map50)
    map5095 = float(metricas.box.map)
    print(f"\nmAP50 = {map50:.3f} | mAP50-95 = {map5095:.3f}  (evaluado con '{split}')")

    mejor = a.salida / "weights" / "best.pt"
    final = YOLO(str(mejor))
    clases = _nombres_clases(final)

    # TFLite float16 (recomendado) y, si se pide, int8 (más rápido; revisar exactitud).
    exportados = {}
    ruta_fp16 = final.export(format="tflite", imgsz=a.img, half=True)
    exportados["fp16"] = str(ruta_fp16)
    print("Exportado:", ruta_fp16)
    if a.int8:
        try:
            ruta_int8 = final.export(format="tflite", imgsz=a.img, int8=True, data=a.datos)
            exportados["int8"] = str(ruta_int8)
            print("Exportado:", ruta_int8)
        except Exception as e:
            print(f"[AVISO] No se pudo exportar a int8 ({e}). Usa la versión fp16.")

    cumple = map50 >= META_MAP50
    meta = {
        "tarea": "prueba_corte",
        "modelo": "M2",
        "version": a.version,
        "clases": clases,
        "entrada": a.img,
        "umbral_confianza": a.conf,
        "umbral_iou": a.iou,
        "map50": round(map50, 4),
        "map50_95": round(map5095, 4),
        "evaluado_con": split,
        "cumple_metas_liberacion": cumple,
        "metas": {"map50_minimo": META_MAP50,
                  "error_conteo_maximo": META_ERROR_CONTEO},
        "archivos": exportados,
        "regla_app": (
            "Mostrar cada caja con su clase y color; el usuario puede tocar una caja "
            f"para corregirla. Si el total detectado se aleja de {GRANOS_TABLERO} en "
            f"más de {META_ERROR_CONTEO} granos, pedir revisión del conteo (RF-PRC-07)."),
    }
    (a.salida / "metadatos.json").write_text(
        json.dumps(meta, indent=2, ensure_ascii=False), encoding="utf-8")

    if cumple:
        print(f"METAS DE LIBERACIÓN: mAP50 {map50:.3f} ≥ {META_MAP50}. "
              "Falta validar el error de conteo con --evaluar_conteo.")
        return 0
    print(f"METAS DE LIBERACIÓN: NO CUMPLE (mAP50 {map50:.3f} < {META_MAP50}). "
          "Etiqueta más tableros y vuelve a entrenar. "
          "Mientras tanto la app usa el conteo manual (RF-PRC-05).")
    return 2


def probar(a) -> int:
    """Corre el modelo sobre una foto de tablero y califica el resultado."""
    from ultralytics import YOLO

    modelo = YOLO(a.pesos)
    r = modelo.predict(a.probar, imgsz=a.img, conf=a.conf, iou=a.iou, save=True,
                       project="salidas", name="prueba", exist_ok=True)[0]
    conteo = Counter(modelo.names[int(c)] for c in r.boxes.cls.tolist())
    total = sum(conteo.values())
    print(f"Granos detectados: {total}", dict(conteo))
    if abs(total - GRANOS_TABLERO) > META_ERROR_CONTEO:
        print(f"[AVISO] El total se aleja de {GRANOS_TABLERO} en más de "
              f"{META_ERROR_CONTEO} granos. Revisa la foto o el umbral de confianza.")

    for perfil in ("ccn51_referencia", "grados_1_2_3"):
        res = calificar(dict(conteo), perfil)
        estado = "CUMPLE" if res["conforme"] else "NO CUMPLE"
        print(f"[{perfil}] {estado}: {res['resultado']}")
        if res["fallas"]:
            print("   fallas:", json.dumps(res["fallas"], ensure_ascii=False))
        for aviso in res["avisos"]:
            print("   Aviso:", aviso)
    print("Imagen con las cajas dibujadas en salidas/prueba/")
    return 0


def evaluar_conteo(a) -> int:
    """
    Mide lo que de verdad le importa a la app: ¿cuántos granos se le escapan al modelo
    y cuánto se desvía el % de fermentados frente a las etiquetas del experto?

    El mAP50 puede verse bien y el conteo seguir mal (por ejemplo si el modelo parte
    un grano en dos cajas), por eso esta comprobación es aparte.
    """
    import yaml
    from ultralytics import YOLO

    cfg = yaml.safe_load(Path(a.evaluar_conteo).read_text(encoding="utf-8"))
    raiz = Path(cfg.get("path", Path(a.evaluar_conteo).parent))
    split = cfg.get("test") or cfg.get("val")
    carpeta_img = raiz / split
    carpeta_lbl = Path(str(carpeta_img).replace("images", "labels"))
    nombres = cfg["names"]
    if isinstance(nombres, dict):
        nombres = [nombres[i] for i in sorted(nombres)]

    modelo = YOLO(a.pesos)
    errores_conteo, errores_fermentado, filas = [], [], []

    for img in sorted(carpeta_img.glob("*")):
        if img.suffix.lower() not in {".jpg", ".jpeg", ".png"}:
            continue
        etiqueta = carpeta_lbl / f"{img.stem}.txt"
        if not etiqueta.exists():
            continue

        real = Counter()
        for linea in etiqueta.read_text(encoding="utf-8").splitlines():
            if linea.strip():
                real[nombres[int(linea.split()[0])]] += 1

        r = modelo.predict(img, imgsz=a.img, conf=a.conf, iou=a.iou, verbose=False)[0]
        pred = Counter(modelo.names[int(c)] for c in r.boxes.cls.tolist())

        err_conteo = abs(sum(pred.values()) - sum(real.values()))
        try:
            ferm_real = calificar(dict(real))["porcentajes"]["fermentado_total"]
            ferm_pred = calificar(dict(pred))["porcentajes"]["fermentado_total"]
            err_ferm = abs(ferm_pred - ferm_real)
        except Exception:
            err_ferm = float("nan")

        errores_conteo.append(err_conteo)
        errores_fermentado.append(err_ferm)
        filas.append({"tablero": img.name, "real": sum(real.values()),
                      "detectado": sum(pred.values()), "error_conteo": err_conteo,
                      "error_fermentado_pts": round(err_ferm, 2)})

    if not filas:
        print(f"[ERROR] No se encontraron tableros etiquetados en {carpeta_img}")
        return 1

    n = len(filas)
    media_conteo = sum(errores_conteo) / n
    peor_conteo = max(errores_conteo)
    validos = [e for e in errores_fermentado if e == e]   # descarta NaN
    media_ferm = sum(validos) / len(validos) if validos else float("nan")

    print(f"\n{'tablero':<28}{'real':>6}{'detec.':>8}{'err':>6}{'err %ferm':>11}")
    for f in filas:
        print(f"{f['tablero']:<28}{f['real']:>6}{f['detectado']:>8}"
              f"{f['error_conteo']:>6}{f['error_fermentado_pts']:>11}")

    print(f"\nTableros evaluados: {n}")
    print(f"Error de conteo    media {media_conteo:.2f} | peor {peor_conteo}  "
          f"(meta: ≤ {META_ERROR_CONTEO})")
    print(f"Error % fermentado media {media_ferm:.2f} puntos            (meta: ≤ 5)")

    cumple = media_conteo <= META_ERROR_CONTEO and media_ferm <= 5
    print("METAS DE CONTEO:", "CUMPLE" if cumple else "NO CUMPLE")
    return 0 if cumple else 2


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--datos", help="ruta a data.yaml, para entrenar")
    p.add_argument("--base", default="yolo11n.pt")
    p.add_argument("--epocas", type=int, default=100)
    p.add_argument("--img", type=int, default=640)
    p.add_argument("--lote", type=int, default=16)
    p.add_argument("--paciencia", type=int, default=20)
    p.add_argument("--conf", type=float, default=0.35)
    p.add_argument("--iou", type=float, default=0.5)
    p.add_argument("--int8", action="store_true")
    p.add_argument("--usar_test", action="store_true")
    p.add_argument("--semilla", type=int, default=42)
    p.add_argument("--version", default="1.0")
    p.add_argument("--salida", type=Path, default=Path("salidas/corte"))
    p.add_argument("--probar", help="foto de un tablero para probar el modelo")
    p.add_argument("--evaluar_conteo", help="data.yaml; mide el error de conteo real")
    p.add_argument("--pesos", default="salidas/corte/weights/best.pt")
    a = p.parse_args(argv)

    if a.probar:
        return probar(a)
    if a.evaluar_conteo:
        return evaluar_conteo(a)
    if a.datos:
        return entrenar(a)
    p.error("Usa --datos para entrenar, --probar para una foto "
            "o --evaluar_conteo para medir el error de conteo")


if __name__ == "__main__":
    raise SystemExit(main())
