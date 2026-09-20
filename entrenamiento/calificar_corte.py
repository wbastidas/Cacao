"""
calificar_corte.py — CacaoTrace

Convierte el conteo de granos por clase (venga del detector M2 o del conteo manual)
en porcentajes y en el resultado de calidad según la tabla de `norma_inen176.json`.

Esta misma lógica está replicada en la app, en Dart
(`app/lib/nucleo/norma/calificador_corte.dart`), para que funcione sin internet.
Las dos implementaciones leen EL MISMO archivo JSON y tienen los mismos tests:
si una se desvía de la otra, los tests fallan.

Requerimientos que cubre: RF-PRC-06 (porcentajes y requisito que falla),
RF-PRC-07 (aviso si el total de granos se sale de 97–103), RN-10.

Uso:
    python calificar_corte.py '{"bien_fermentado":70,"violeta":12,"pizarroso":6}'
    python calificar_corte.py --archivo conteo.json --perfil grados_1_2_3
    python calificar_corte.py --listar-perfiles
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

NORMA = Path(__file__).with_name("norma_inen176.json")

# Total de granos que manda la norma para una prueba de corte completa.
GRANOS_NORMA = 100
# Margen aceptado antes de avisar que el conteo no cuadra (RF-PRC-07).
GRANOS_MIN_AVISO = 97
GRANOS_MAX_AVISO = 103

# Indicadores que siempre deben existir en el resultado, aunque valgan 0 %.
INDICADORES = (
    "fermentado_bueno",
    "fermentado_ligero",
    "violeta",
    "pizarroso",
    "mohoso",
    "defectuoso",
)


class ErrorNorma(ValueError):
    """Problema con los datos de entrada o con la tabla de la norma."""


def cargar_norma(ruta_norma: Path = NORMA) -> dict:
    """Lee y valida mínimamente la tabla de la norma."""
    ruta = Path(ruta_norma)
    if not ruta.exists():
        raise ErrorNorma(f"No se encuentra la tabla de la norma en {ruta}")
    tabla = json.loads(ruta.read_text(encoding="utf-8"))
    for clave in ("clases_detector", "perfiles"):
        if clave not in tabla:
            raise ErrorNorma(f"La tabla de la norma no tiene la sección '{clave}'")
    return tabla


def porcentajes(conteo: dict, tabla: dict) -> dict:
    """
    Agrupa el conteo por clase del detector en los indicadores de la norma y
    los convierte a porcentaje sobre el total de granos VÁLIDOS.

    Las clases mapeadas a "ignorar" (por ejemplo 'otro') no entran en el total,
    para que un grano que el modelo no supo clasificar no diluya los porcentajes.
    """
    if not isinstance(conteo, dict) or not conteo:
        raise ErrorNorma("El conteo debe ser un diccionario {clase: cantidad} no vacío")

    agrupado: dict[str, int] = {}
    desconocidas: list[str] = []
    ignorados = 0

    for clase, n in conteo.items():
        if not isinstance(n, (int, float)) or n < 0:
            raise ErrorNorma(f"La cantidad de '{clase}' debe ser un número ≥ 0 (llegó {n!r})")
        if clase not in tabla["clases_detector"]:
            desconocidas.append(clase)
            continue
        grupo = tabla["clases_detector"][clase]
        if grupo == "ignorar":
            ignorados += int(n)
            continue
        agrupado[grupo] = agrupado.get(grupo, 0) + int(n)

    total = sum(agrupado.values())
    if total == 0:
        raise ErrorNorma(
            "No hay granos válidos para calificar. Revisa que las clases del conteo "
            f"existan en 'clases_detector'. Clases no reconocidas: {desconocidas or 'ninguna'}"
        )

    pct = {k: 100.0 * v / total for k, v in agrupado.items()}
    for k in INDICADORES:
        pct.setdefault(k, 0.0)
    pct["fermentado_total"] = pct["fermentado_bueno"] + pct["fermentado_ligero"]

    pct["_granos_evaluados"] = total
    pct["_granos_ignorados"] = ignorados
    pct["_granos_contados"] = total + ignorados
    pct["_clases_desconocidas"] = desconocidas
    return pct


def cumple(pct: dict, requisitos: list) -> tuple[bool, list[str]]:
    """Devuelve (cumple_todo, lista de requisitos que fallan en español)."""
    fallas = []
    for r in requisitos:
        v = pct.get(r["indicador"], 0.0)
        ok = v >= r["valor"] if r["tipo"] == "min" else v <= r["valor"]
        if not ok:
            signo = "≥" if r["tipo"] == "min" else "≤"
            fallas.append(
                f"{r['indicador'].replace('_', ' ')} = {v:.1f}% "
                f"(requiere {signo} {r['valor']}%)"
            )
    return len(fallas) == 0, fallas


def _avisos(pct: dict) -> list[str]:
    """Advertencias que no invalidan el cálculo pero el usuario debe ver."""
    avisos = []
    contados = pct["_granos_contados"]
    if contados < GRANOS_MIN_AVISO or contados > GRANOS_MAX_AVISO:
        avisos.append(
            f"Se contaron {contados} granos; la norma usa {GRANOS_NORMA}. "
            "Revisa el conteo antes de dar el resultado por bueno."
        )
    if pct["_granos_ignorados"]:
        avisos.append(
            f"{pct['_granos_ignorados']} grano(s) quedaron sin clasificar y no entraron "
            "en los porcentajes."
        )
    if pct["_clases_desconocidas"]:
        avisos.append(
            "Clases que no están en la tabla de la norma y se descartaron: "
            + ", ".join(sorted(set(pct["_clases_desconocidas"])))
        )
    return avisos


def calificar(conteo: dict, perfil: str = "ccn51_referencia",
              ruta_norma: Path = NORMA) -> dict:
    """
    Califica un conteo de granos.

    Devuelve:
        resultado    texto del grado o conformidad
        conforme     True si pasó
        porcentajes  todos los indicadores en %
        fallas       lista (perfil simple) o dict por grado (perfil por grados)
        avisos       advertencias para el usuario
    """
    tabla = cargar_norma(ruta_norma)
    if perfil not in tabla["perfiles"]:
        disponibles = ", ".join(tabla["perfiles"])
        raise ErrorNorma(f"El perfil '{perfil}' no existe. Disponibles: {disponibles}")

    pct = porcentajes(conteo, tabla)
    p = tabla["perfiles"][perfil]
    avisos = _avisos(pct)

    # Perfil por grados: se prueba Grado 1, luego 2, luego 3; gana el primero que cumpla.
    if "grados" in p:
        fallas_por_grado = {}
        for g in p["grados"]:
            ok, fallas = cumple(pct, g["requisitos"])
            if ok:
                return {"resultado": g["grado"], "conforme": True, "perfil": perfil,
                        "porcentajes": pct, "fallas": {}, "avisos": avisos}
            fallas_por_grado[g["grado"]] = fallas
        return {"resultado": p["resultado_si_no_cumple"], "conforme": False, "perfil": perfil,
                "porcentajes": pct, "fallas": fallas_por_grado, "avisos": avisos}

    # Perfil simple: cumple o no cumple.
    ok, fallas = cumple(pct, p["requisitos"])
    return {
        "resultado": p["resultado_si_cumple"] if ok else p["resultado_si_no_cumple"],
        "conforme": ok,
        "perfil": perfil,
        "porcentajes": pct,
        "fallas": fallas,
        "avisos": avisos,
    }


def imprimir(r: dict) -> None:
    marca = "OK " if r["conforme"] else "NO "
    print(f"\n[{r['perfil']}] {marca} {r['resultado']}")
    for k, v in r["porcentajes"].items():
        if not k.startswith("_"):
            print(f"   {k:<18} {v:5.1f}%")
    print(f"   {'granos evaluados':<18} {r['porcentajes']['_granos_evaluados']:5d}")
    if r["fallas"]:
        print("   Motivos:", json.dumps(r["fallas"], ensure_ascii=False, indent=2))
    for a in r["avisos"]:
        print("   Aviso:", a)


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("conteo", nargs="?", help='JSON en línea, ej. \'{"violeta":12}\'')
    p.add_argument("--archivo", type=Path, help="archivo JSON con el conteo")
    p.add_argument("--perfil", help="perfil de la norma; si se omite se calculan todos")
    p.add_argument("--norma", type=Path, default=NORMA)
    p.add_argument("--listar-perfiles", action="store_true")
    p.add_argument("--json", action="store_true", help="salida en JSON en vez de texto")
    a = p.parse_args(argv)

    if a.listar_perfiles:
        tabla = cargar_norma(a.norma)
        for nombre, perfil in tabla["perfiles"].items():
            print(f"{nombre}: {perfil.get('descripcion', '')}")
        return 0

    if a.archivo:
        conteo = json.loads(a.archivo.read_text(encoding="utf-8"))
    elif a.conteo:
        conteo = json.loads(a.conteo)
    else:
        # Ejemplo de demostración con un lote realista de CCN-51.
        conteo = {"bien_fermentado": 70, "ligeramente_fermentado": 9, "violeta": 12,
                  "pizarroso": 6, "mohoso": 1, "vano_plano": 2}
        print("Sin conteo indicado; se usa un ejemplo de demostración.")

    perfiles = [a.perfil] if a.perfil else list(cargar_norma(a.norma)["perfiles"])
    try:
        resultados = [calificar(conteo, perfil, a.norma) for perfil in perfiles]
    except ErrorNorma as e:
        print(f"Error: {e}", file=sys.stderr)
        return 1

    if a.json:
        print(json.dumps(resultados, ensure_ascii=False, indent=2))
    else:
        for r in resultados:
            imprimir(r)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
