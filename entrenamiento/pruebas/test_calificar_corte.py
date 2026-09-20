"""
Tests de la calificación según la norma INEN 176.

Los casos viven en `casos_norma.json` y los comparten esta implementación en Python y
la de la app en Dart (`app/test/nucleo/calificador_corte_test.dart`). Si una de las dos
se desvía de la otra, estos tests fallan.

    pytest entrenamiento/pruebas -v
"""
import json
import sys
from pathlib import Path

import pytest

RAIZ = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RAIZ))

from calificar_corte import ErrorNorma, calificar, cargar_norma, porcentajes  # noqa: E402

CASOS = json.loads((Path(__file__).with_name("casos_norma.json")).read_text(encoding="utf-8"))


def ids(casos):
    return [c["nombre"] for c in casos]


@pytest.mark.parametrize("caso", CASOS["casos"], ids=ids(CASOS["casos"]))
def test_resultado_por_perfil(caso):
    for perfil, esperado in caso["espera"].items():
        r = calificar(caso["conteo"], perfil)
        assert r["resultado"] == esperado["resultado"], (
            f"{caso['nombre']} / {perfil}: se esperaba '{esperado['resultado']}' "
            f"y salió '{r['resultado']}'")
        assert r["conforme"] == esperado["conforme"]


@pytest.mark.parametrize("caso", CASOS["casos"], ids=ids(CASOS["casos"]))
def test_porcentajes(caso):
    pct = porcentajes(caso["conteo"], cargar_norma())
    for indicador, valor in caso["porcentajes"].items():
        assert pct[indicador] == pytest.approx(valor, abs=0.05), (
            f"{caso['nombre']}: {indicador} debía ser {valor} y salió {pct[indicador]}")
    assert pct["_granos_evaluados"] == caso["granos_evaluados"]
    if "granos_ignorados" in caso:
        assert pct["_granos_ignorados"] == caso["granos_ignorados"]


@pytest.mark.parametrize("caso", CASOS["casos"], ids=ids(CASOS["casos"]))
def test_avisos(caso):
    if "avisos_esperados" not in caso:
        return
    perfil = next(iter(caso["espera"]))
    r = calificar(caso["conteo"], perfil)
    assert len(r["avisos"]) == caso["avisos_esperados"], r["avisos"]


@pytest.mark.parametrize("caso", CASOS["errores"], ids=ids(CASOS["errores"]))
def test_entradas_invalidas(caso):
    with pytest.raises(ErrorNorma):
        calificar(caso["conteo"])


def test_fermentado_total_es_la_suma():
    pct = porcentajes({"bien_fermentado": 50, "ligeramente_fermentado": 20,
                       "violeta": 30}, cargar_norma())
    assert pct["fermentado_total"] == pytest.approx(
        pct["fermentado_bueno"] + pct["fermentado_ligero"])


def test_perfil_inexistente():
    with pytest.raises(ErrorNorma, match="no existe"):
        calificar({"bien_fermentado": 100}, "perfil_que_no_existe")


def test_clase_desconocida_avisa_y_no_rompe():
    r = calificar({"bien_fermentado": 90, "clase_inventada": 10})
    assert r["porcentajes"]["_granos_evaluados"] == 90
    assert any("clase_inventada" in a for a in r["avisos"])


def test_todos_los_perfiles_de_la_norma_son_calificables():
    """Si alguien añade un perfil a norma_inen176.json, debe poder calcularse."""
    tabla = cargar_norma()
    conteo = {"bien_fermentado": 70, "ligeramente_fermentado": 8, "violeta": 12,
              "pizarroso": 8, "mohoso": 2}
    for perfil in tabla["perfiles"]:
        r = calificar(conteo, perfil)
        assert isinstance(r["resultado"], str) and r["resultado"]


def test_la_norma_no_tiene_indicadores_desconocidos():
    """Cada requisito debe referirse a un indicador que el cálculo produce."""
    tabla = cargar_norma()
    validos = {"fermentado_bueno", "fermentado_ligero", "fermentado_total",
               "violeta", "pizarroso", "mohoso", "defectuoso"}
    for nombre, perfil in tabla["perfiles"].items():
        grupos = perfil.get("grados", [perfil])
        for g in grupos:
            for r in g.get("requisitos", []):
                assert r["indicador"] in validos, (
                    f"El perfil '{nombre}' usa el indicador desconocido "
                    f"'{r['indicador']}'")
                assert r["tipo"] in ("min", "max")
