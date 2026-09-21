"""
Pruebas de la división del dataset.

La que de verdad importa es la de la fuga de datos. Si dos fotos de la misma
toma caen en particiones distintas, el modelo se evalúa con granos que ya vio
entrenando: la validación da un número buenísimo, uno cree que el modelo está
listo, y en el teléfono falla. Es el fallo más caro del pipeline porque no se
manifiesta como un error, sino como una métrica demasiado buena.

    pytest entrenamiento/pruebas -v
"""
import csv
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

import pytest

RAIZ = Path(__file__).resolve().parents[1]
GUION = RAIZ / "preparar_dataset.py"


def _fotos(carpeta: Path, tableros: int = 20, por_tablero: int = 2) -> None:
    """Crea `tableros` tomas con `por_tablero` fotos cada una."""
    Image = pytest.importorskip("PIL.Image", reason="pillow no está instalado")
    destino = carpeta / "misfotos" / "sana"
    destino.mkdir(parents=True)
    for n in range(1, tableros + 1):
        for i in range(por_tablero):
            color = (n * 7 % 256, i * 40 % 256, 128)
            Image.new("RGB", (64, 64), color).save(
                destino / f"tablero_{n:03d}_{chr(ord('a') + i)}.jpg"
            )


def _preparar(tmp_path: Path, agrupar: str | None) -> Path:
    fuentes = tmp_path / "fuentes"
    _fotos(fuentes)
    mapeo = tmp_path / "mapeo.json"
    mapeo.write_text('{"misfotos": {"sana": "sana"}}', encoding="utf-8")
    salida = tmp_path / ("con" if agrupar else "sin")

    orden = [
        sys.executable, str(GUION),
        "--fuentes", str(fuentes),
        "--mapeo", str(mapeo),
        "--salida", str(salida),
    ]
    if agrupar:
        orden += ["--agrupar_por", agrupar]

    resultado = subprocess.run(orden, capture_output=True, text=True)
    assert resultado.returncode == 0, resultado.stdout + resultado.stderr
    return salida / "manifiesto.csv"


def _tomas_partidas(manifiesto: Path) -> list[str]:
    """Tomas cuyas fotos acabaron en más de una partición."""
    particiones = defaultdict(set)
    with manifiesto.open(encoding="utf-8") as f:
        for fila in csv.DictReader(f):
            toma = Path(fila["origen"]).name.rsplit("_", 1)[0]
            particiones[toma].add(fila["particion"])
    return sorted(t for t, p in particiones.items() if len(p) > 1)


def test_agrupar_por_evita_la_fuga_entre_particiones(tmp_path):
    """Con --agrupar_por, ninguna toma se parte entre particiones."""
    partidas = _tomas_partidas(_preparar(tmp_path, r"^(tablero_\d+)"))
    assert partidas == [], (
        f"Estas tomas quedaron repartidas entre particiones: {partidas}. "
        "Eso es fuga de datos: el modelo se evaluaría con fotos que ya vio."
    )


def test_sin_agrupar_si_se_parten(tmp_path):
    """
    El contraste que justifica la bandera.

    Sin agrupar, una buena parte de las tomas se reparte. Se comprueba a
    propósito: si un día dejara de ocurrir, la prueba de arriba estaría
    pasando por casualidad y no porque la agrupación funcione.
    """
    partidas = _tomas_partidas(_preparar(tmp_path, None))
    assert partidas, (
        "Sin --agrupar_por no se partió ninguna toma. La otra prueba ya no "
        "demuestra nada: revisa si la división cambió de comportamiento."
    )


def test_patron_sin_grupo_de_captura_falla_con_mensaje_claro(tmp_path):
    """
    Pasar 'tablero' en vez de '^(tablero_\\d+)' es un error fácil de cometer.

    Antes reventaba con un IndexError a mitad de la ejecución; ahora tiene que
    salir con código 1 y explicar qué se esperaba.
    """
    fuentes = tmp_path / "fuentes"
    _fotos(fuentes, tableros=2)
    mapeo = tmp_path / "mapeo.json"
    mapeo.write_text('{"misfotos": {"sana": "sana"}}', encoding="utf-8")

    resultado = subprocess.run(
        [
            sys.executable, str(GUION),
            "--fuentes", str(fuentes),
            "--mapeo", str(mapeo),
            "--salida", str(tmp_path / "salida"),
            "--agrupar_por", "tablero",
        ],
        capture_output=True, text=True,
    )
    assert resultado.returncode == 1
    assert "grupo de captura" in resultado.stdout
    assert "Traceback" not in resultado.stderr
