# Referencia

Código que **no se mantiene**, conservado porque las decisiones de diseño que
contiene siguen siendo válidas y sirven para comparar.

## `flutter-app/`

Primera implementación de CacaoTrace en Flutter (Dart), construida siguiendo la
recomendación del §3 de la ERS. Funcionaba: 118 tests en verde y `flutter
analyze` sin observaciones.

Se reemplazó por la implementación **nativa de Android en Kotlin** que vive en
`../android/`, por decisión del proyecto: la app es solo para Android, y nativo
da mejor integración con la cámara, las tareas en segundo plano y LiteRT, además
de un APK más liviano.

**No la uses como base de trabajo.** Está aquí para consultar:

| Qué mirar | Dónde |
|---|---|
| Reglas de negocio RN-01…RN-17 con su redacción en español | `lib/nucleo/reglas/motor_reglas.dart` |
| Biblioteca de correcciones C-01…C-09 | `lib/nucleo/reglas/correcciones.dart` |
| Los 24 umbrales con su explicación y rango | `lib/nucleo/reglas/umbrales.dart` |
| Textos de las guías por etapa y el glosario | `lib/ui/pantallas/guias.dart` |
| Esquema de datos del §6 de la ERS | `lib/datos/bd/tablas/` |

La lógica de la norma INEN 176 y sus casos de prueba **no** se quedaron aquí:
viven en `entrenamiento/norma_inen176.json` y
`entrenamiento/pruebas/casos_norma.json`, y los usan tanto el pipeline de
Python como la app de Kotlin.
