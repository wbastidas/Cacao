#!/usr/bin/env python3
"""
Verifica las consultas de Room contra el esquema real.

Room valida el SQL en tiempo de compilación con KSP: una tabla mal escrita o
una columna que no existe no dan un error en ejecución, revientan el build. Sin
poder compilar aquí, esto es lo que más se le parece.

Comprueba:
  1. Cada tabla nombrada en un FROM/UPDATE/INTO existe como @Entity.
  2. Cada columna citada existe en alguna entidad usada por esa consulta.
  3. Cada parámetro :x de la consulta corresponde a un argumento del método.
  4. Cada entidad del proyecto está declarada en @Database.
"""
import re, glob
from pathlib import Path

RAIZ = Path("/home/user/Cacao/android/app/src/main/kotlin/ec/cacaotrace")

def sin_comentarios(t):
    t = re.sub(r'/\*.*?\*/', '', t, flags=re.S)
    return re.sub(r'(?<!:)//[^\n]*', '', t)

# ------------------------------------------------ entidades y sus columnas
tabla_de_clase, columnas_de_tabla = {}, {}
COMUNES = {"id", "creadoEn", "modificadoEn", "usuarioId", "dispositivoId",
           "estadoSync", "eliminado"}

for f in glob.glob(str(RAIZ / "datos/bd/entidades/*.kt")):
    texto = sin_comentarios(Path(f).read_text())
    for m in re.finditer(
        # La supertipo es opcional: la cola de sincronización es una @Entity
        # que a propósito NO implementa ConComunes, y exigir el «:» la dejaba
        # fuera del esquema, haciendo saltar sus cuatro consultas como falsas.
        r'@Entity\s*\((.*?)\)\s*(?:@\w+\s*)*data\s+class\s+(\w+)\s*\((.*?)\n\)\s*(?::|$)',
        texto, re.S,
    ):
        cabecera, clase, cuerpo = m.group(1), m.group(2), m.group(3)
        mt = re.search(r'tableName\s*=\s*"(\w+)"', cabecera)
        tabla = mt.group(1) if mt else clase
        tabla_de_clase[clase] = tabla
        # El «override» va entre la anotación y el val en las entidades que
        # implementan ConComunes; sin contemplarlo, «comunes» no se detectaba
        # y ninguna entidad heredaba las siete columnas del §6 de la ERS.
        cols = set(re.findall(
            r'(?:^|,)\s*(?:@\w+(?:\([^)]*\))?\s*)*'
            r'(?:override\s+|private\s+|internal\s+)*val\s+(\w+)\s*:', cuerpo))
        if "@Embedded" in cuerpo and "comunes" in cols:
            cols = (cols - {"comunes"}) | COMUNES
        columnas_de_tabla[tabla] = cols

todas_las_columnas = set().union(*columnas_de_tabla.values()) if columnas_de_tabla else set()
PALABRAS_SQL = {
    "select","from","where","and","or","order","by","asc","desc","limit","group",
    "having","insert","into","values","update","set","delete","join","left","inner",
    "on","as","not","null","is","in","like","count","sum","avg","min","max","distinct",
    "case","when","then","else","end","union","all","offset","exists","between","coalesce",
}

problemas = []

# ------------------------------------------------------------- consultas
for f in glob.glob(str(RAIZ / "datos/bd/daos/*.kt")):
    texto = sin_comentarios(Path(f).read_text())
    nombre = Path(f).name
    for m in re.finditer(
        r'@Query\(\s*(?:"""(?P<triple>.*?)"""|"(?P<simple>(?:[^"\\]|\\.)*)")\s*\)\s*'
        r'(?:suspend\s+)?fun\s+(?P<metodo>\w+)\s*\((?P<firma>.*?)\)',
        texto, re.S,
    ):
        sql = m.group("triple") or m.group("simple") or ""
        metodo, firma = m.group("metodo"), m.group("firma")
        args = set(re.findall(r'(\w+)\s*:', firma))

        for tabla in re.findall(r'\b(?:FROM|INTO|UPDATE|JOIN)\s+`?(\w+)`?', sql, re.I):
            if tabla not in columnas_de_tabla:
                problemas.append(f"{nombre}::{metodo}: tabla «{tabla}» no es ninguna @Entity")

        for param in re.findall(r':(\w+)', sql):
            if param not in args:
                problemas.append(
                    f"{nombre}::{metodo}: :{param} no es argumento del método "
                    f"(tiene {sorted(args)})")

        tablas = [t for t in re.findall(r'\b(?:FROM|INTO|UPDATE|JOIN)\s+`?(\w+)`?', sql, re.I)
                  if t in columnas_de_tabla]
        validas = set().union(*[columnas_de_tabla[t] for t in tablas]) if tablas else set()
        cuerpo_sql = re.sub(r':\w+', '', sql)
        cuerpo_sql = re.sub(r"'[^']*'", '', cuerpo_sql)
        for ident in re.findall(r'(?<![:.\w])([a-zA-Z_]\w*)', cuerpo_sql):
            if ident.lower() in PALABRAS_SQL or ident in tablas:
                continue
            if ident in validas:
                continue
            # Se marca cualquier identificador que no sea columna válida, no
            # solo los que existen en otra tabla: si no, una columna inventada
            # del todo («borrado» por «eliminado») pasaba sin más.
            problemas.append(
                f"{nombre}::{metodo}: «{ident}» no es columna de {tablas}")

# --------------------------------------------- entidades en @Database
bd = sin_comentarios((RAIZ / "datos/bd/BaseDatos.kt").read_text())
declaradas = set(re.findall(r'(\w+Entidad)::class', bd))
for clase in tabla_de_clase:
    if clase not in declaradas:
        problemas.append(f"BaseDatos.kt: la entidad {clase} no está en @Database")
for clase in declaradas:
    if clase not in tabla_de_clase:
        problemas.append(f"BaseDatos.kt: @Database nombra {clase}, que no se encontró")

print(f"Entidades: {len(tabla_de_clase)} · columnas totales: {len(todas_las_columnas)}")
print(f"Problemas: {len(problemas)}")
for p in sorted(set(problemas)):
    print("  ", p)
