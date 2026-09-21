#!/usr/bin/env bash
#
# smoke_test.sh — CacaoTrace
#
# Comprueba que TODO el pipeline de entrenamiento funciona de principio a fin, usando
# imágenes sintéticas. No entrena un modelo útil: comprueba que las piezas encajan.
#
# Úsalo cuando:
#   · acabas de clonar el repositorio y quieres saber si el entorno está bien;
#   · cambiaste algo en los scripts y quieres saber si rompiste algo;
#   · vas a enseñarle el proyecto a alguien y necesitas que corra en 5 minutos.
#
#   bash herramientas/smoke_test.sh            # solo clasificador (rápido)
#   bash herramientas/smoke_test.sh --con-yolo # incluye el detector M2 (lento)
#
set -euo pipefail

cd "$(dirname "$0")/.."
PY="${PY:-python3}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

CON_YOLO=0
[[ "${1:-}" == "--con-yolo" ]] && CON_YOLO=1

paso() { printf '\n\033[1m==> %s\033[0m\n' "$1"; }

paso "1/5  Tests de la norma INEN 176"
$PY -m pytest pruebas -q

paso "2/5  Generando imágenes sintéticas"
$PY herramientas/generar_datos_sinteticos.py --salida "$TMP/fuentes" \
    --tarea mazorca --n 24 --lado 128

paso "3/5  Preparando el dataset (unir, deduplicar, dividir)"
$PY preparar_dataset.py --fuentes "$TMP/fuentes" \
    --mapeo "$TMP/mapeo_mazorca_sintetico.json" \
    --salida "$TMP/datos/mazorca" --lado_max 128

paso "4/5  Entrenando y exportando a TFLite"
# Pocas épocas a propósito: aquí importa que el pipeline corra, no la exactitud.
# Por eso se ignora el código 2 ("no cumple las metas"), que es el esperado.
set +e
$PY entrenar_clasificador.py --datos "$TMP/datos/mazorca" --tarea mazorca \
    --arquitectura efficientnetv2b0 --img 96 --lote 8 \
    --epocas 2 --epocas_ajuste 1 --salida "$TMP/salidas/mazorca" 2>&1 \
    | grep -E "Clases:|Fase|TFLite|Verificación|recomendado|METAS|AVISO|ERROR" || true
CODIGO=${PIPESTATUS[0]}
set -e
if [[ $CODIGO -ne 0 && $CODIGO -ne 2 ]]; then
  echo "FALLO: el entrenamiento terminó con código $CODIGO"
  exit 1
fi

paso "5/5  Comprobando los archivos que necesita la app"
for f in modelo.keras etiquetas.txt metadatos.json reporte.txt \
         matriz_confusion.png curvas.png mazorca_fp16.tflite; do
  if [[ ! -s "$TMP/salidas/mazorca/$f" ]]; then
    echo "FALLO: falta o está vacío $f"
    exit 1
  fi
  printf '  ok  %s\n' "$f"
done
$PY - "$TMP/salidas/mazorca/metadatos.json" <<'PYEOF'
import json, sys
m = json.load(open(sys.argv[1], encoding="utf-8"))
for clave in ("tarea", "clases", "entrada", "umbral_confianza", "exactitud_test",
              "recall_por_clase", "verificacion_tflite", "cumple_metas_liberacion"):
    assert clave in m, f"metadatos.json no trae '{clave}'"
fp16 = m["verificacion_tflite"]["fp16"]
assert fp16["concordancia_keras"] >= 0.98, (
    f"El .tflite fp16 no coincide con el modelo Keras "
    f"(concordancia {fp16['concordancia_keras']}). La conversión está rota.")
print(f"  ok  metadatos.json completo; concordancia fp16 = {fp16['concordancia_keras']}")
PYEOF

if [[ $CON_YOLO -eq 1 ]]; then
  paso "extra  Detector M2 (YOLO) sobre tableros sintéticos"
  $PY herramientas/generar_datos_sinteticos.py --salida "$TMP/corte" --tarea corte --n 8
  set +e
  $PY entrenar_detector_corte.py --datos "$TMP/corte/data.yaml" --epocas 2 \
      --img 320 --lote 2 --salida "$TMP/salidas/corte" 2>&1 | tail -20
  CODIGO=${PIPESTATUS[0]}
  set -e
  if [[ $CODIGO -ne 0 && $CODIGO -ne 2 ]]; then
    echo "FALLO: el detector terminó con código $CODIGO"
    exit 1
  fi
  echo "  ok  el detector entrenó y exportó"
fi

printf '\n\033[1;32mTODO EN ORDEN.\033[0m El pipeline funciona de principio a fin.\n'
echo "Recuerda: las imágenes eran sintéticas. Para un modelo de verdad necesitas"
echo "fotos reales tomadas con el protocolo de la sección 8.4 de la ERS."
