"""
generar_datos_sinteticos.py — CacaoTrace

Crea imágenes FALSAS con los colores y formas aproximados de cada clase, solo para
comprobar que todo el pipeline funciona de principio a fin (preparar → entrenar →
exportar a TFLite → verificar) sin necesidad de tener fotos reales todavía.

NO SIRVEN PARA ENTRENAR UN MODELO DE VERDAD. Un modelo entrenado con estas imágenes
reconoce manchas de color, no mazorcas. Úsalas solo para validar el pipeline, para
probar la app antes de tener fotos, y para que alguien nuevo en el proyecto pueda
correr todo en 5 minutos.

Uso:
    python herramientas/generar_datos_sinteticos.py --salida fuentes --tarea mazorca
    python herramientas/generar_datos_sinteticos.py --salida fuentes --tarea corte --n 30
"""
from __future__ import annotations

import argparse
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

# Color base aproximado de cada clase y cuánto varía, para que el problema sea
# aprendible pero no trivial.
PALETAS = {
    "mazorca": {
        "sana": ((196, 132, 42), 28),        # amarillo-naranja de CCN-51 maduro
        "monilia": ((138, 122, 108), 30),    # gris-café con polvo blanco encima
        "fitoftora": ((58, 44, 38), 24),     # mancha café muy oscura
        "otro": ((104, 138, 70), 30),        # verde: mazorca inmadura u otra cosa
    },
    "tostado": {
        "crudo": ((150, 110, 80), 18),
        "ligero": ((120, 82, 55), 16),
        "medio": ((92, 60, 40), 15),
        "oscuro": ((62, 40, 28), 14),
        "quemado": ((34, 24, 20), 12),
    },
    "chocolate": {
        "atemperado_ok": ((58, 38, 28), 10),
        "fat_bloom": ((110, 95, 88), 16),    # velo grisáceo
        "sugar_bloom": ((132, 122, 118), 16),
        "sin_brillo": ((72, 54, 44), 12),
    },
    # Clases de grano cortado del detector M2.
    "corte": {
        "bien_fermentado": ((122, 68, 38), 14),
        "ligeramente_fermentado": ((140, 88, 52), 14),
        "violeta": ((96, 70, 122), 16),
        "pizarroso": ((70, 74, 86), 14),
        "mohoso": ((178, 172, 150), 16),
        "dano_insectos": ((110, 84, 60), 14),
        "germinado": ((132, 96, 60), 14),
        "vano_plano": ((158, 140, 116), 14),
        "otro": ((100, 100, 100), 20),
    },
}


def _color(base, var, rnd):
    return tuple(max(0, min(255, c + rnd.randint(-var, var))) for c in base)


def imagen_clase(clase: str, paleta: dict, lado: int, rnd: random.Random) -> Image.Image:
    """Una forma ovalada del color de la clase sobre fondo blanco (como la caja de luz)."""
    base, var = paleta[clase]
    im = Image.new("RGB", (lado, lado), _color((242, 242, 240), 6, rnd))
    d = ImageDraw.Draw(im)
    m = lado // 8
    caja = [m + rnd.randint(-8, 8), m + rnd.randint(-8, 8),
            lado - m + rnd.randint(-8, 8), lado - m + rnd.randint(-8, 8)]
    d.ellipse(caja, fill=_color(base, var, rnd))
    # textura: manchitas del mismo tono, para que no sea un color plano
    for _ in range(rnd.randint(12, 30)):
        x = rnd.randint(caja[0], caja[2])
        y = rnd.randint(caja[1], caja[3])
        r = rnd.randint(2, max(3, lado // 22))
        d.ellipse([x - r, y - r, x + r, y + r], fill=_color(base, var + 18, rnd))
    return im.filter(ImageFilter.GaussianBlur(0.6))


def generar_clasificacion(salida: Path, tarea: str, n: int, lado: int, semilla: int):
    paleta = PALETAS[tarea]
    rnd = random.Random(semilla)
    carpeta = salida / f"sinteticas_{tarea}"
    for clase in paleta:
        destino = carpeta / clase
        destino.mkdir(parents=True, exist_ok=True)
        for i in range(n):
            imagen_clase(clase, paleta, lado, rnd).save(destino / f"{clase}_{i:04d}.jpg",
                                                        "JPEG", quality=90)
    mapeo = {f"sinteticas_{tarea}": {c: c for c in paleta}}
    ruta_mapeo = salida.parent / f"mapeo_{tarea}_sintetico.json"
    ruta_mapeo.write_text(json.dumps(mapeo, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"{len(paleta) * n} imágenes en {carpeta}")
    print(f"Mapeo listo en {ruta_mapeo}")


def generar_tableros(salida: Path, n: int, lado: int, semilla: int):
    """
    Tableros 10x10 de granos cortados en formato YOLO, para el detector M2.
    Genera datos/corte/{train,valid,test}/{images,labels} y su data.yaml.
    """
    paleta = PALETAS["corte"]
    clases = list(paleta)
    rnd = random.Random(semilla)
    raiz = salida
    reparto = {"train": int(n * 0.7) or 1, "valid": int(n * 0.15) or 1,
               "test": max(1, n - (int(n * 0.7) or 1) - (int(n * 0.15) or 1))}

    for particion, cuantos in reparto.items():
        (raiz / particion / "images").mkdir(parents=True, exist_ok=True)
        (raiz / particion / "labels").mkdir(parents=True, exist_ok=True)
        for k in range(cuantos):
            im = Image.new("RGB", (lado, lado), (246, 246, 244))
            d = ImageDraw.Draw(im)
            etiquetas = []
            celda = lado / 10
            for fila in range(10):
                for col in range(10):
                    # distribución parecida a un lote bien fermentado
                    idx = rnd.choices(range(len(clases)),
                                      weights=[45, 15, 15, 8, 4, 4, 3, 4, 2])[0]
                    base, var = paleta[clases[idx]]
                    cx, cy = (col + 0.5) * celda, (fila + 0.5) * celda
                    rx, ry = celda * 0.34, celda * 0.24
                    d.ellipse([cx - rx, cy - ry, cx + rx, cy + ry],
                              fill=_color(base, var, rnd))
                    etiquetas.append(f"{idx} {cx/lado:.6f} {cy/lado:.6f} "
                                     f"{2*rx/lado:.6f} {2*ry/lado:.6f}")
            nombre = f"tablero_{particion}_{k:03d}"
            im.filter(ImageFilter.GaussianBlur(0.4)).save(
                raiz / particion / "images" / f"{nombre}.jpg", "JPEG", quality=90)
            (raiz / particion / "labels" / f"{nombre}.txt").write_text(
                "\n".join(etiquetas), encoding="utf-8")

    data_yaml = raiz / "data.yaml"
    data_yaml.write_text(
        f"path: {raiz.resolve()}\n"
        "train: train/images\nval: valid/images\ntest: test/images\n"
        f"names: [{', '.join(clases)}]\n", encoding="utf-8")
    print(f"{sum(reparto.values())} tableros sintéticos en {raiz}")
    print(f"data.yaml listo en {data_yaml}")


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--salida", required=True, type=Path)
    p.add_argument("--tarea", default="mazorca",
                   choices=["mazorca", "tostado", "chocolate", "corte"])
    p.add_argument("--n", type=int, default=60,
                   help="imágenes por clase (o tableros, si la tarea es 'corte')")
    p.add_argument("--lado", type=int, default=320)
    p.add_argument("--semilla", type=int, default=7)
    a = p.parse_args(argv)

    print("AVISO: estas imágenes son sintéticas y solo sirven para probar el pipeline.")
    if a.tarea == "corte":
        generar_tableros(a.salida, a.n, max(a.lado, 640), a.semilla)
    else:
        generar_clasificacion(a.salida, a.tarea, a.n, a.lado, a.semilla)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
