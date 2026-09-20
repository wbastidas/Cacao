"""
preparar_dataset.py — CacaoTrace

Une varias fuentes de imágenes (datasets abiertos + tus fotos exportadas desde la app)
en una sola carpeta con nombres de clase unificados y la divide en train / val / test
de forma estratificada (misma proporción de cada clase en cada partición).

Estructura de entrada esperada — una carpeta por fuente, una subcarpeta por clase:

    fuentes/
      CocoaMoniliaDataSet/   h0/  m1/  m2/  m3/
      Cacao-Diseases-Project/  sana/  monilia/  fitoftora/
      fotos_app/             sana/  monilia/  fitoftora/  otro/

Archivo de mapeo JSON (clase de la fuente -> clase de la app, o null para descartar):

    {"CocoaMoniliaDataSet": {"h0": "sana", "m1": "monilia", "m2": "monilia", "m3": "monilia"},
     "fotos_app": {"sana": "sana", "monilia": "monilia", "fitoftora": "fitoftora"}}

Uso:
    python preparar_dataset.py --fuentes fuentes --mapeo mapeo_mazorca.json \
        --salida datos/mazorca --lado_max 1024

Qué hace además de copiar:
  · corrige la rotación EXIF de las fotos del teléfono (si no, salen acostadas);
  · elimina duplicados exactos por MD5 (frecuente al mezclar datasets abiertos);
  · deja las fotos de una misma "toma" en la MISMA partición cuando se usa
    --agrupar_por, para que el modelo no se evalúe con una foto casi idéntica a
    otra que ya vio entrenando (fuga de datos);
  · escribe `manifiesto.csv` e `informe.json` para poder reproducir la división.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import random
import re
import shutil
from collections import Counter, defaultdict
from pathlib import Path

from PIL import Image, ImageOps

EXTENSIONES = {".jpg", ".jpeg", ".png", ".bmp", ".webp"}
# Mínimo de imágenes de entrenamiento por clase antes de confiar en el modelo (§8.1 ERS).
MINIMO_RECOMENDADO = 50


def md5(ruta: Path) -> str:
    h = hashlib.md5()
    with open(ruta, "rb") as f:
        for bloque in iter(lambda: f.read(1 << 20), b""):
            h.update(bloque)
    return h.hexdigest()


def clave_grupo(ruta: Path, patron: str | None) -> str:
    """
    Identifica la 'toma' a la que pertenece una foto para no separarla entre
    particiones. Por defecto el grupo es la propia foto (sin agrupar).

    Con --agrupar_por 'carpeta' se agrupa por la carpeta que la contiene.
    Con una expresión regular se usa el primer grupo capturado del nombre:
    por ejemplo '^(mazorca_\\d+)' agrupa mazorca_007_a.jpg y mazorca_007_b.jpg.
    """
    if not patron:
        return str(ruta)
    if patron == "carpeta":
        return str(ruta.parent)
    m = re.search(patron, ruta.name)
    if m is None:
        return str(ruta)
    if m.re.groups == 0:
        # Sin grupo de captura no hay nada que agrupar. Es un error fácil de
        # cometer (pasar 'tablero' en vez de '^(tablero_\d+)') y antes se
        # manifestaba como un IndexError ilegible a mitad de la ejecución.
        raise ValueError(
            f"El patrón de --agrupar_por debe llevar un grupo de captura entre "
            f"paréntesis; recibí {patron!r}. Por ejemplo: '^(tablero_\\d+)'. "
            f"También se acepta la palabra 'carpeta'."
        )
    return m.group(1)


def recolectar(fuentes: Path, mapeo: dict, patron_grupo: str | None):
    """Devuelve {clase_unificada: {grupo: [(fuente, ruta), ...]}} sin duplicados exactos."""
    por_clase: dict[str, dict[str, list]] = defaultdict(lambda: defaultdict(list))
    vistos: dict[str, Path] = {}
    duplicados = descartados = ilegibles = 0

    for fuente, clases in mapeo.items():
        carpeta = fuentes / fuente
        if not carpeta.exists():
            print(f"[AVISO] No existe la fuente {carpeta}, se omite.")
            continue
        for clase_origen, clase_destino in clases.items():
            sub = carpeta / clase_origen
            if not sub.exists():
                print(f"[AVISO] No existe {sub}, se omite.")
                continue
            for img in sorted(sub.rglob("*")):
                if not img.is_file() or img.suffix.lower() not in EXTENSIONES:
                    continue
                if clase_destino is None:
                    descartados += 1
                    continue
                try:
                    firma = md5(img)
                except OSError as e:
                    print(f"[ERROR] No se pudo leer {img}: {e}")
                    ilegibles += 1
                    continue
                if firma in vistos:
                    duplicados += 1
                    continue
                vistos[firma] = img
                por_clase[clase_destino][clave_grupo(img, patron_grupo)].append((fuente, img))

    print(f"Duplicados exactos eliminados: {duplicados} | "
          f"descartados por el mapeo: {descartados} | ilegibles: {ilegibles}")
    return por_clase


def guardar(origen: Path, destino: Path, lado_max: int) -> bool:
    """Copia la imagen corrigiendo rotación y tamaño. Devuelve False si estaba corrupta."""
    destino.parent.mkdir(parents=True, exist_ok=True)
    try:
        with Image.open(origen) as im:
            im = ImageOps.exif_transpose(im)
            im = im.convert("RGB")
            if lado_max and max(im.size) > lado_max:
                im.thumbnail((lado_max, lado_max), Image.LANCZOS)
            im.save(destino.with_suffix(".jpg"), "JPEG", quality=92)
        return True
    except Exception as e:
        print(f"[ERROR] Imagen descartada {origen}: {e}")
        return False


def repartir(grupos: list, n_val: float, n_test: float, aleatorio: random.Random):
    """
    Reparte GRUPOS (no imágenes sueltas) en test / val / train respetando las
    proporciones pedidas lo más cerca posible.
    """
    grupos = list(grupos)
    aleatorio.shuffle(grupos)
    total_img = sum(len(v) for _, v in grupos)
    objetivo_test = total_img * n_test
    objetivo_val = total_img * n_val

    particiones = {"test": [], "val": [], "train": []}
    acum_test = acum_val = 0
    for clave, items in grupos:
        if acum_test < objetivo_test:
            particiones["test"].append((clave, items))
            acum_test += len(items)
        elif acum_val < objetivo_val:
            particiones["val"].append((clave, items))
            acum_val += len(items)
        else:
            particiones["train"].append((clave, items))

    # Con muy pocos datos, test y val pueden quedar vacíos: es preferible avisar
    # a dejar el entrenamiento sin conjunto de validación.
    if not particiones["val"] and particiones["train"]:
        particiones["val"].append(particiones["train"].pop())
    return particiones


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--fuentes", required=True, type=Path)
    p.add_argument("--mapeo", required=True, type=Path)
    p.add_argument("--salida", required=True, type=Path)
    p.add_argument("--val", type=float, default=0.15)
    p.add_argument("--test", type=float, default=0.15)
    p.add_argument("--lado_max", type=int, default=1024,
                   help="lado mayor en píxeles; 0 para no redimensionar")
    p.add_argument("--agrupar_por", default=None,
                   help="'carpeta' o una expresión regular con un grupo de captura; "
                        "evita que fotos de la misma toma caigan en particiones distintas")
    p.add_argument("--semilla", type=int, default=42)
    a = p.parse_args(argv)

    if not a.mapeo.exists():
        print(f"[ERROR] No existe el archivo de mapeo {a.mapeo}")
        return 1
    if a.val + a.test >= 1.0:
        print("[ERROR] --val + --test debe ser menor que 1.0")
        return 1

    aleatorio = random.Random(a.semilla)
    mapeo = json.loads(a.mapeo.read_text(encoding="utf-8"))
    try:
        por_clase = recolectar(a.fuentes, mapeo, a.agrupar_por)
    except ValueError as fallo:
        # Un patrón de agrupación mal escrito es error del usuario, no un fallo
        # del programa: se dice en una línea, sin traza.
        print(f"[ERROR] {fallo}")
        return 1

    if not por_clase:
        print("[ERROR] No se encontró ninguna imagen. Revisa --fuentes y el mapeo.")
        return 1

    if a.salida.exists():
        shutil.rmtree(a.salida)
    a.salida.mkdir(parents=True)

    resumen = defaultdict(Counter)
    filas = []
    for clase, grupos in sorted(por_clase.items()):
        particiones = repartir(list(grupos.items()), a.val, a.test, aleatorio)
        for particion, lista in particiones.items():
            i = 0
            for _, items in lista:
                for fuente, ruta in items:
                    destino = a.salida / particion / clase / f"{fuente}_{i:05d}"
                    if guardar(ruta, destino, a.lado_max):
                        resumen[particion][clase] += 1
                        filas.append({"particion": particion, "clase": clase,
                                      "fuente": fuente, "origen": str(ruta),
                                      "destino": str(destino.with_suffix(".jpg"))})
                        i += 1

    clases = sorted(por_clase)
    print("\nResumen (imágenes por clase):")
    print(f"{'clase':<22}{'train':>7}{'val':>7}{'test':>7}{'total':>8}")
    for c in clases:
        t = sum(resumen[p][c] for p in ("train", "val", "test"))
        print(f"{c:<22}{resumen['train'][c]:>7}{resumen['val'][c]:>7}"
              f"{resumen['test'][c]:>7}{t:>8}")

    with open(a.salida / "manifiesto.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["particion", "clase", "fuente", "origen", "destino"])
        w.writeheader()
        w.writerows(filas)

    informe = {
        "clases": clases,
        "semilla": a.semilla,
        "agrupar_por": a.agrupar_por,
        "lado_max": a.lado_max,
        "conteos": {p: dict(resumen[p]) for p in ("train", "val", "test")},
        "total": len(filas),
    }
    (a.salida / "informe.json").write_text(
        json.dumps(informe, indent=2, ensure_ascii=False), encoding="utf-8")

    escasas = [c for c in clases if resumen["train"][c] < MINIMO_RECOMENDADO]
    if escasas:
        print(f"\n[AVISO] Estas clases tienen menos de {MINIMO_RECOMENDADO} imágenes de "
              f"entrenamiento: {', '.join(escasas)}.")
        print("        Toma más fotos de esas clases antes de confiar en el modelo.")
    faltan_val = [c for c in clases if resumen["val"][c] == 0]
    if faltan_val:
        print(f"[AVISO] Sin imágenes de validación para: {', '.join(faltan_val)}. "
              "Las métricas de esas clases no serán fiables.")

    print(f"\nListo. Dataset en {a.salida.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
