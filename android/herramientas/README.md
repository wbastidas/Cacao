# Verificadores estáticos

Dos guiones que comprueban a mano lo que el compilador comprobaría solo.

**No sustituyen a compilar.** Se escribieron porque el entorno donde se
desarrolló la app no tiene el SDK de Android —el proxy de salida bloquea
`dl.google.com`, del que depende `maven.google.com`—, así que el módulo `:app`
no podía pasar por el compilador de Kotlin ni por el procesador de Room. Si
puedes compilar, compila: esto es la red de seguridad, no el trapecio.

Siguen siendo útiles después: son rápidos, no necesitan SDK ni red, y sirven
para un repaso antes de abrir un PR o desde una máquina donde solo hay Python.

## Uso

```bash
python3 herramientas/verificar_referencias.py
python3 herramientas/verificar_room.py
```

Ambos devuelven 0 si no hay problemas y listan cada hallazgo con archivo y
método si los hay.

## Qué comprueba cada uno

### `verificar_referencias.py`

| | Comprobación |
|---|---|
| A | Todo `import ec.cacaotrace.*` apunta a una declaración que existe |
| B | Todo tipo del proyecto que se usa está declarado en el archivo, importado, o vive en el mismo paquete |
| C | Todo argumento con nombre corresponde a un parámetro real de esa función, incluidas las llamadas cualificadas (`repo.metodo(...)`) |

### `verificar_room.py`

| | Comprobación |
|---|---|
| 1 | Cada tabla de un `FROM`/`UPDATE`/`INTO`/`JOIN` existe como `@Entity` |
| 2 | Cada columna citada existe en alguna de las tablas de esa consulta |
| 3 | Cada parámetro `:x` del SQL corresponde a un argumento del método |
| 4 | Cada `@Entity` está declarada en `@Database`, y al revés |

## Una advertencia que vale la pena leer

Estos guiones trabajan con expresiones regulares sobre el texto fuente, no con
un árbol sintáctico. Eso tiene un riesgo concreto: **un fallo en el propio
verificador se ve exactamente igual que "todo está bien"**.

Durante su desarrollo pasó cinco veces. La expresión de los parámetros no
contemplaba `private val`, y daba por inexistentes tres parámetros que sí
estaban. La de tipos exigía una supertipo, y dejaba fuera del esquema la única
`@Entity` que a propósito no implementa `ConComunes`. Los literales de texto no
se descartaban, y `AVISO("Aviso")` contaba como uso de una clase de la interfaz.
Las llamadas con punto se filtraban, que son la mayoría. Y la comprobación de
columnas solo marcaba nombres que existieran en otra tabla, así que una columna
inventada del todo pasaba de largo.

Por eso, **si cambias un verificador, pruébalo rompiendo el código a
propósito**: mete un import falso, borra uno que se use, inventa un argumento,
escribe mal una tabla. Si el guion no se queja, el guion está roto, no el
código. Un verificador que siempre dice que todo va bien es peor que no tener
ninguno, porque da confianza sin fundamento.
