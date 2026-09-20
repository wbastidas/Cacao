#!/usr/bin/env python3
"""
Verificador estático de referencias cruzadas del proyecto Kotlin.

No sustituye al compilador: sustituye a leerse 40 archivos a mano buscando un
nombre mal escrito. Comprueba tres cosas que son las que de verdad rompen una
compilación cuando se escribe código sin poder compilarlo:

  A. Todo import de `ec.cacaotrace.*` apunta a una declaración que existe.
  B. Todo símbolo del proyecto que se usa en un archivo está declarado en ese
     archivo, importado, o vive en el mismo paquete.
  C. Todo argumento con nombre de una llamada a una función del proyecto
     corresponde a un parámetro real de esa función.
"""
from __future__ import annotations
import re, sys
from pathlib import Path
from collections import defaultdict

RAIZ = Path("/home/user/Cacao/android")
FUENTES = sorted(
    list((RAIZ / "nucleo/src/main/kotlin").rglob("*.kt"))
    + list((RAIZ / "app/src/main/kotlin").rglob("*.kt"))
)

# ---------------------------------------------------------------- recolección

# nombre declarado -> conjunto de "paquete.Nombre"
declaraciones: dict[str, set[str]] = defaultdict(set)
# "paquete.Nombre" -> paquete
paquete_de: dict[str, str] = {}
# nombre de función -> conjunto de nombres de parámetros válidos
parametros: dict[str, set[str]] = defaultdict(set)
# nombre de función -> archivo donde se declara (para el informe)
declarada_en: dict[str, str] = {}
# paquete -> nombres declarados en él
del_paquete: dict[str, set[str]] = defaultdict(set)

RE_PAQUETE = re.compile(r'^package\s+([\w.]+)', re.M)
RE_TIPO = re.compile(
    r'^(?:@\w+(?:\([^)]*\))?\s*)*'
    r'(?:public\s+|internal\s+|private\s+|abstract\s+|open\s+|sealed\s+|data\s+|value\s+|enum\s+|annotation\s+)*'
    r'(?:class|interface|object)\s+(\w+)', re.M)
RE_FUN = re.compile(
    r'^\s*(?:@\w+(?:\([^)]*\))?\s*)*'
    r'(?:public\s+|internal\s+|private\s+|override\s+|suspend\s+|inline\s+|operator\s+|abstract\s+|open\s+)*'
    r'fun\s+(?:<[^>]+>\s*)?(?:[\w.<>?]+\.)?(\w+)\s*\(', re.M)
RE_VAL_TOP = re.compile(r'^(?:internal\s+|private\s+|public\s+)?(?:val|var)\s+(\w+)', re.M)
RE_TYPEALIAS = re.compile(r'^(?:internal\s+|private\s+)?typealias\s+(\w+)', re.M)

def quitar_comentarios(texto: str) -> str:
    """Quita comentarios Y literales de texto.

    Los literales importan tanto como los comentarios: un enum que se llama
    AVISO("Aviso") no está usando la clase Aviso de la interfaz, y contarlo
    como uso genera un falso positivo que hace desconfiar de todo el informe.
    """
    texto = re.sub(r'/\*.*?\*/', '', texto, flags=re.S)
    texto = re.sub(r'(?<!:)//[^\n]*', '', texto)
    texto = re.sub(r'\"\"\".*?\"\"\"', '""', texto, flags=re.S)
    texto = re.sub(r'"(?:\\.|[^"\\\n])*"', '""', texto)
    return texto

def bloque_parametros(texto: str, inicio: int) -> str:
    """Devuelve el contenido entre paréntesis balanceados desde `inicio`."""
    nivel, i = 0, inicio
    while i < len(texto):
        if texto[i] == '(':
            nivel += 1
        elif texto[i] == ')':
            nivel -= 1
            if nivel == 0:
                return texto[inicio + 1:i]
        i += 1
    return ""

fuentes_limpias: dict[Path, tuple[str, str]] = {}

for ruta in FUENTES:
    crudo = ruta.read_text()
    limpio = quitar_comentarios(crudo)
    m = RE_PAQUETE.search(limpio)
    paquete = m.group(1) if m else ""
    fuentes_limpias[ruta] = (limpio, paquete)

    for nombre in RE_TYPEALIAS.findall(limpio):
        declaraciones[nombre].add(f"{paquete}.{nombre}")
        del_paquete[paquete].add(nombre)

    for m in RE_TIPO.finditer(limpio):
        nombre = m.group(1)
        declaraciones[nombre].add(f"{paquete}.{nombre}")
        del_paquete[paquete].add(nombre)
        declarada_en.setdefault(nombre, str(ruta))
        # Parámetros del constructor primario, si los hay.
        resto = limpio[m.end():]
        abre = resto.find('(')
        llave = resto.find('{')
        if abre != -1 and (llave == -1 or abre < llave) and abre < 200:
            cuerpo = bloque_parametros(resto, abre)
            for p in re.finditer(
                r'(?:^|,)\s*(?:@\w+(?:\([^)]*\))?\s+)*'
                r'(?:private\s+|internal\s+|public\s+|protected\s+)?'
                r'(?:override\s+)?(?:vararg\s+)?(?:val\s+|var\s+)?(\w+)\s*:', cuerpo
            ):
                parametros[nombre].add(p.group(1))

    for m in RE_FUN.finditer(limpio):
        nombre = m.group(1)
        declaraciones[nombre].add(f"{paquete}.{nombre}")
        del_paquete[paquete].add(nombre)
        declarada_en.setdefault(nombre, str(ruta))
        cuerpo = bloque_parametros(limpio, m.end() - 1)
        for p in re.finditer(
            r'(?:^|,)\s*(?:@\w+(?:\([^)]*\))?\s+)*'
            r'(?:vararg\s+|crossinline\s+|noinline\s+)?(\w+)\s*:', cuerpo
        ):
            parametros[nombre].add(p.group(1))

    for nombre in RE_VAL_TOP.findall(limpio):
        declaraciones[nombre].add(f"{paquete}.{nombre}")
        del_paquete[paquete].add(nombre)

# -------------------------------------------------------------- comprobación

problemas: list[str] = []

for ruta, (limpio, paquete) in fuentes_limpias.items():
    corto = str(ruta).replace(str(RAIZ) + "/", "")
    importados: set[str] = set()
    for m in re.finditer(r'^import\s+([\w.]+)(?:\s+as\s+(\w+))?', limpio, re.M):
        ruta_import, alias = m.group(1), m.group(2)
        nombre = alias or ruta_import.split('.')[-1]
        importados.add(nombre)

        # --- A: los imports del proyecto tienen que existir ---
        if ruta_import.startswith("ec.cacaotrace.") and ruta_import != "ec.cacaotrace.R":
            hoja = ruta_import.split('.')[-1]
            if ruta_import not in declaraciones.get(hoja, set()):
                # Puede ser un miembro de un tipo: ec...TipoX.miembro
                padre = '.'.join(ruta_import.split('.')[:-1])
                hoja_padre = padre.split('.')[-1]
                if padre not in declaraciones.get(hoja_padre, set()):
                    problemas.append(f"[A] {corto}: import inexistente -> {ruta_import}")

    # --- B: símbolos del proyecto usados sin importar ---
    locales = set(re.findall(r'\b(?:class|interface|object|fun|val|var|typealias)\s+(\w+)', limpio))
    for nombre, rutas_posibles in declaraciones.items():
        if len(nombre) < 4 or not nombre[0].isupper():
            continue  # solo tipos/objetos; las funciones sueltas dan mucho ruido
        if nombre in locales or nombre in importados:
            continue
        if nombre in del_paquete.get(paquete, set()):
            continue
        # ¿Se usa como identificador suelto?
        if re.search(r'(?<![\w.])' + re.escape(nombre) + r'(?![\w])', limpio):
            problemas.append(f"[B] {corto}: usa «{nombre}» sin importar (declarado en {sorted(rutas_posibles)[0]})")

    # --- C: argumentos con nombre contra la firma real ---
    #
    # Se aceptan también las llamadas cualificadas (`repo.metodo(...)`), que son
    # la mayoría: filtrarlas por el punto dejaba esta comprobación mirando casi
    # nada. Cuando dos clases declaran un método con el mismo nombre se unen sus
    # parámetros, que es conservador pero sigue cazando el nombre mal escrito.
    for m in re.finditer(r'(?<![\w])\.?([A-Z]\w+|[a-z]\w{3,})\s*\(', limpio):
        fn = m.group(1)
        if fn not in parametros or not parametros[fn]:
            continue
        cuerpo = bloque_parametros(limpio, m.end() - 1)
        if not cuerpo.strip():
            continue
        # Argumentos con nombre en el primer nivel de anidamiento.
        nivel = 0
        actual = []
        trozos = []
        for ch in cuerpo:
            if ch in '([{':
                nivel += 1
            elif ch in ')]}':
                nivel -= 1
            if ch == ',' and nivel == 0:
                trozos.append(''.join(actual)); actual = []
            else:
                actual.append(ch)
        trozos.append(''.join(actual))
        for trozo in trozos:
            mm = re.match(r'\s*(\w+)\s*=(?![=>])', trozo)
            if mm and mm.group(1) not in parametros[fn]:
                problemas.append(
                    f"[C] {corto}: {fn}(… {mm.group(1)} = …) no es un parámetro; "
                    f"los válidos son {sorted(parametros[fn])}"
                )

print(f"Archivos analizados: {len(FUENTES)}")
print(f"Declaraciones encontradas: {len(declaraciones)}")
print(f"Problemas: {len(problemas)}\n")
for p in sorted(set(problemas)):
    print(p)
sys.exit(1 if problemas else 0)
