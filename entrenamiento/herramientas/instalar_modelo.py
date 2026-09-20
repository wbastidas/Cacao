"""
instalar_modelo.py — CacaoTrace

Copia el modelo entrenado, sus etiquetas y sus metadatos a la carpeta de recursos de
la app Android, con el nombre que la app espera.

La app busca, para cada tarea:
    android/app/src/main/assets/modelos/<tarea>/modelo.tflite
    android/app/src/main/assets/modelos/<tarea>/etiquetas.txt
    android/app/src/main/assets/modelos/<tarea>/metadatos.json

Si la carpeta no existe o está vacía, la app arranca igual y muestra "modelo no
instalado"; el registro y el conteo manual siguen funcionando (§12 de la ERS).

Uso:
    python herramientas/instalar_modelo.py --tarea mazorca
    python herramientas/instalar_modelo.py --tarea mazorca --variante fp16 --forzar
"""
from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
TAMANO_MAXIMO_MB = 10     # §8.2 de la ERS: ≤ 10 MB por modelo


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--tarea", required=True,
                   help="mazorca | corte | tostado | chocolate")
    p.add_argument("--salidas", type=Path, default=None,
                   help="carpeta con los resultados del entrenamiento")
    p.add_argument("--destino", type=Path, default=None)
    p.add_argument("--variante", choices=["auto", "int8", "fp16"], default="auto",
                   help="'auto' usa el archivo_recomendado de metadatos.json")
    p.add_argument("--forzar", action="store_true",
                   help="instala aunque el modelo no cumpla las metas de liberación")
    a = p.parse_args(argv)

    origen = a.salidas or (RAIZ / "entrenamiento" / "salidas" / a.tarea)
    destino = a.destino or (
        RAIZ / "android" / "app" / "src" / "main" / "assets" / "modelos" / a.tarea
    )

    meta_ruta = origen / "metadatos.json"
    if not meta_ruta.exists():
        print(f"[ERROR] No existe {meta_ruta}. ¿Ya entrenaste el modelo '{a.tarea}'?")
        return 1
    meta = json.loads(meta_ruta.read_text(encoding="utf-8"))

    if not meta.get("cumple_metas_liberacion", False) and not a.forzar:
        print("[ERROR] Este modelo todavía no cumple las metas de liberación:")
        for x in meta.get("problemas", ["(sin detalle)"]):
            print("  -", x)
        print("\nLa app funciona sin él (conteo y registro manual). Si aun así quieres "
              "instalarlo para probar, repite con --forzar.")
        return 2

    if a.variante == "auto":
        nombre = meta.get("archivo_recomendado")
        if not nombre:
            print("[ERROR] metadatos.json no indica un archivo recomendado: ninguna "
                  "variante TFLite quedó usable. Vuelve a entrenar.")
            return 1
    else:
        nombre = f"{a.tarea}_{a.variante}.tflite"

    modelo = origen / nombre
    etiquetas = origen / "etiquetas.txt"
    for f in (modelo, etiquetas):
        if not f.exists():
            print(f"[ERROR] Falta {f}")
            return 1

    mb = modelo.stat().st_size / 1e6
    if mb > TAMANO_MAXIMO_MB:
        print(f"[AVISO] El modelo pesa {mb:.1f} MB y la ERS pide ≤ {TAMANO_MAXIMO_MB} MB "
              "(§8.2). La app instalada quedará más grande de lo previsto; considera "
              "la variante int8 o una arquitectura más liviana.")

    destino.mkdir(parents=True, exist_ok=True)
    shutil.copy2(modelo, destino / "modelo.tflite")
    shutil.copy2(etiquetas, destino / "etiquetas.txt")
    shutil.copy2(meta_ruta, destino / "metadatos.json")

    print(f"Instalado en {destino}")
    print(f"  modelo.tflite    {mb:.1f} MB  (desde {nombre})")
    print(f"  clases           {', '.join(meta.get('clases', []))}")
    print(f"  versión          {meta.get('version')}")
    print("\nNo hace falta ningún paso más: Gradle empaqueta todo lo que haya bajo "
          "src/main/assets/. Vuelve a instalar la app para que el modelo llegue al "
          "teléfono.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
