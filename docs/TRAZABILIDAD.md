# Trazabilidad: requerimiento → código

Dónde está implementado cada requerimiento de la ERS. Sirve para revisar el
avance, para encontrar el código al que hay que tocar, y para responder "¿esto
está hecho?" sin leer todo el repositorio.

**Estado:** ✅ implementado · 🟡 parcial · ⛔ pendiente

---

## 3.3 Sincronización

| Código | Estado | Dónde |
|---|---|---|
| RF-SYN-01 escribir primero en el teléfono | ✅ | `datos/repositorios/*` escriben en Drift y encolan aparte |
| RF-SYN-02 UUID, fecha, dispositivo, estado | ✅ | `datos/bd/tablas/comunes.dart` (mixin `ColumnasComunes`) |
| RF-SYN-03 subir con reintentos | ✅ | `datos/sync/servicio_sincronizacion.dart` |
| RF-SYN-04 fotos solo con WiFi | ✅ | `servicio_sincronizacion.dart` + `PantallaSincronizacion` |
| RF-SYN-05 conflictos por última modificación | 🟡 | columnas listas; la resolución vive en el sincronizador remoto |
| RF-SYN-06 indicador de estado | ✅ | `EstadoSincronizacion.etiqueta` + `ui/pantallas/inicio.dart` |
| RF-SYN-07 respaldo semanal y restauración | 🟡 | `SincronizadorRemoto.respaldoCompleto()` definido; implementar con Drive |

## 4.1 Cuenta y configuración

| Código | Estado | Dónde |
|---|---|---|
| RF-AUT-01 iniciar sesión con Google | ⛔ | requiere Firebase; ver `docs/NUBE.md` |
| RF-AUT-02 modo solo teléfono | ✅ | es el modo por defecto de toda la app |
| RF-AUT-03 invitar ayudantes | ⛔ | requiere Firebase |
| RF-CFG-01 fincas | ✅ | `ui/pantallas/ajustes.dart` → `PantallaFincas` |
| RF-CFG-02 equipos | ✅ | `ui/pantallas/ajustes.dart` → `PantallaEquipos` |
| RF-CFG-03 editar umbrales y norma | ✅ | `PantallaUmbrales`, `RepositorioConfiguracion.guardarNorma` |
| RF-CFG-04 unidades e idioma | 🟡 | todo en español; la app usa kg y °C en toda la interfaz |

## 4.2 Lotes y trazabilidad

| Código | Estado | Dónde |
|---|---|---|
| RF-LOT-01 código automático y QR | ✅ | `siguienteCodigo()`, `PantallaDetalleLote._mostrarQr` |
| RF-LOT-02 estados del lote | ✅ | `nucleo/modelo/etapas.dart`, `RepositorioLotes.cambiarEstado` |
| RF-LOT-03 no saltar etapas sin motivo | ✅ | `EtapaSaltadaSinMotivo` + campo `motivoSalto` |
| RF-LOT-04 escanear QR y abrir el lote | ✅ | `ui/pantallas/lotes.dart` → `PantallaEscaner` |
| RF-LOT-05 lote de producción | ✅ | `RepositorioProduccion.crear` |
| RF-LOT-06 línea de tiempo | ✅ | `RepositorioLotes.lineaDeTiempo` |
| RF-LOT-07 reporte PDF | ✅ | `ui/pantallas/reporte.dart` |
| RF-LOT-08 balance de masa | ✅ | `nucleo/calculo/balance_masa.dart` |

## 4.3–4.8 Etapas del grano

| Código | Estado | Dónde |
|---|---|---|
| RF-REC-01 registrar la recepción | ✅ | `ui/pantallas/recepcion.dart` |
| RF-REC-02 clasificar mazorcas con M1 | ✅ | `ui/comun/camara_ia.dart` + `ia/servicio_modelos.dart` |
| RF-REC-03 modo ráfaga con conteo | ✅ | `recepcion.dart`, contador por clase |
| RF-REC-04 descartes con motivo | ✅ | `recepcion.dart` |
| RF-REC-05 programar la apertura | ✅ | `guardarRecepcion` + `PlanificadorAvisos` |
| RF-REC-06 GPS de la foto | 🟡 | columnas `latitud`/`longitud` en `fotos`; falta el permiso en uso |
| RF-APE-01/02/03 apertura | ✅ | `ui/pantallas/apertura.dart`, `cascaraRecomendada` |
| RF-FER-01 a 05, 07 fermentación | ✅ | `ui/pantallas/fermentacion.dart` |
| RF-FER-06 prueba parcial del día 5 | ✅ | `PruebasCorte.esParcial` + interruptor en la pantalla |
| RF-FER-08 importar del sensor | ⛔ | requiere el ESP32 |
| RF-SEC-01 a 05 secado | ✅ | `ui/pantallas/secado.dart` (incluye prueba del puñado) |
| RF-PRC-01 a 10 prueba de corte | ✅ | `ui/pantallas/prueba_corte.dart`, `tablero_pdf.dart` |
| RF-PRC-08 segunda opinión con Gemini | ⛔ | opcional, requiere internet y cuenta |
| RF-ALM-01/02/03 almacenamiento | ✅ | `ui/pantallas/almacen.dart`, `RepositorioApoyo` |

## 4.9–4.13 Producción y venta

| Código | Estado | Dónde |
|---|---|---|
| RF-TOS-01 a 05 tostado | ✅ | `produccion.dart` → `_SeccionTostado` |
| RF-DES-01/02 descascarillado | ✅ | `_SeccionDescascarillado` + RN-12 |
| RF-REF-01 a 04 refinado | ✅ | `nucleo/calculo/receta.dart`, `_SeccionRefinado` |
| RF-ATE-01 a 05 atemperado | ✅ | `_SeccionAtemperado` (asistente con las tres temperaturas) |
| RF-EMP-01 empaque y vencimiento | ✅ | `RepositorioProduccion.guardarEmpaque` |
| RF-EMP-02 datos de etiqueta | 🟡 | campo `etiquetaJson` listo; falta el generador de semáforo |
| RF-EMP-03 QR público | ⛔ | marcado como futuro en la ERS |
| RF-INV-01/02 inventario | ✅ | `RepositorioApoyo.existencias`, `ui/pantallas/inventario.dart` |
| RF-COS-01/02 costos | ✅ | `costosDeProduccion` con costo por barra y por kg |
| RF-VEN-01/02 ventas y margen | ✅ | `registrarVenta`, `margenDeProduccion` |

## 4.14–4.18 BPM, alertas, panel e IA

| Código | Estado | Dónde |
|---|---|---|
| RF-BPM-01 checklists configurables | ✅ | sembrados en `base_datos.dart`, `ui/pantallas/bpm.dart` |
| RF-BPM-02 registros que se cierran a las 24 h | ✅ | `cerrarRegistrosBpmVencidos`, `RegistroBpmCerrado` |
| RF-BPM-03 exportar BPM en PDF | ✅ | `PantallaBpmPdf` |
| RF-LAB-01 resultados de laboratorio | ✅ | `ui/pantallas/laboratorio.dart` |
| RF-LAB-02 bloqueo por cadmio | ✅ | `guardarLaboratorio` + `Lotes.ventaBloqueada` |
| RF-ALE-01 motor de reglas | ✅ | `nucleo/reglas/motor_reglas.dart` (17 reglas) |
| RF-ALE-02 notificaciones locales | ✅ | `datos/servicio_notificaciones.dart`, `planificador_avisos.dart` |
| RF-ALE-03 qué pasó / por qué / qué hacer | ✅ | clase `Alerta` y `ui/pantallas/alertas.dart` |
| RF-COR-01 biblioteca de correcciones | ✅ | `nucleo/reglas/correcciones.dart` (C-01 a C-09) |
| RF-COR-02 registrar la corrección aplicada | ✅ | `RepositorioAlertas.atender` |
| RF-COR-03 reporte de alertas del lote | ✅ | `correccionesDeLote` + línea de tiempo |
| RF-TAB-01 "¿Qué hago hoy?" | ✅ | `RepositorioApoyo.tareasDeHoy`, `ui/pantallas/inicio.dart` |
| RF-TAB-02 indicadores | ✅ | `RepositorioApoyo.indicadores` |
| RF-TAB-03 comparar lotes | ⛔ | deseable, no implementado |
| RF-REP-01 exportar CSV | ✅ | `ui/pantallas/inventario.dart` |
| RF-GUI-01/02 guías y glosario | ✅ | `ui/pantallas/guias.dart` (8 guías, 16 términos) |
| RF-SEN-01 a 03 sensor Bluetooth | ⛔ | requiere el ESP32 |
| RF-IA-01 modelos en el teléfono | ✅ | `ia/servicio_modelos.dart` |
| RF-IA-02 confianza y "no estoy seguro" | ✅ | `ResultadoClasificacion.estaSeguro` |
| RF-IA-03 el usuario siempre corrige | ✅ | `camara_ia.dart`, `conGranoCorregido` |
| RF-IA-04 guía de encuadre | ✅ | parámetro `consejo` de `BotonFotoIa` |
| RF-IA-05 versión del modelo en cada análisis | ✅ | columnas `modeloVersion` |
| RF-IA-06 descargar y volver atrás | 🟡 | `activarModelo` conserva el anterior; falta la descarga |
| RF-IA-07 la IA es de apoyo | ✅ | avisos en `PantallaModelos`, `prueba_corte.dart` y el PDF |

## 5. Reglas de negocio

Las 17 reglas están en `nucleo/reglas/motor_reglas.dart`, con un test cada una
en `test/nucleo/motor_reglas_test.dart`.

| Regla | Método | Test |
|---|---|---|
| RN-01 reposo | `evaluarReposo` | ✅ |
| RN-02 masa mínima | `evaluarApertura` | ✅ |
| RN-03 volteo | `evaluarFermentacion` | ✅ |
| RN-04 fermentación fría | `evaluarFermentacion` | ✅ (criterio §10) |
| RN-05 muy caliente | `evaluarFermentacion` | ✅ |
| RN-06 olor | `evaluarFermentacion` | ✅ |
| RN-07 duración | `evaluarFermentacion` | ✅ |
| RN-08 humedad al cerrar | `evaluarSecado` | ✅ |
| RN-09 moho | `evaluarSecado` | ✅ |
| RN-10 prueba de corte | `evaluarPruebaCorte` | ✅ |
| RN-11 almacén | `evaluarAlmacen` | ✅ |
| RN-12 merma y cascarilla | `evaluarTostado` | ✅ |
| RN-13 cuarto de atemperado | `evaluarAtemperado` | ✅ |
| RN-14 temperatura de trabajo | `evaluarAtemperado` | ✅ |
| RN-15 cadmio | `evaluarLaboratorio` | ✅ |
| RN-16 confianza de la IA | `ResultadoClasificacion` | ✅ |
| RN-17 rendimiento | `evaluarRendimiento` | ✅ |

## 6. Modelo de datos

Las 29 tablas del §6 están en `datos/bd/tablas/`, más seis que el diseño
necesitó: `Configuracion`, `StocksMinimos`, `ChecklistsBpm`, `Auditoria`,
`ColaSync` y `UmbralesGuardados`.

## 8. Modelos de IA

| Requisito | Estado | Dónde |
|---|---|---|
| Contrato del modelo (§8.2) | ✅ | `entrenar_clasificador.py` + `ia/servicio_modelos.dart` |
| Metas de liberación (§8.1) | ✅ | comprobadas por el script, bloquean la instalación |
| Ciclo de mejora continua (§8.3) | ✅ | `comparar_modelos.py`, `Fotos.aptaDataset` |
| Protocolo de fotografía (§8.4) | ✅ | `entrenamiento/README.md`, plantilla con tarjeta de color |

## 9. Requerimientos no funcionales

| Código | Estado | Nota |
|---|---|---|
| RNF-01 offline | ✅ | nada obligatorio depende de la red |
| RNF-02 rendimiento | 🟡 | medir en un teléfono real de gama media |
| RNF-03 tamaño ≤ 150 MB | 🟡 | depende del tamaño final de los modelos |
| RNF-04 batería | ✅ | sin procesos en segundo plano; notificaciones inexactas |
| RNF-05 seguridad | 🟡 | reglas de Firestore documentadas; falta cifrar la base local |
| RNF-06 privacidad | 🟡 | avisos en la app; falta el texto legal completo |
| RNF-07 confiabilidad | ✅ | cada registro es una transacción; nada se pierde a medias |
| RNF-08 respaldo | 🟡 | pendiente de la nube |
| RNF-09 usabilidad | ✅ | volteo en un toque, botones ≥ 48 dp |
| RNF-10 español de Ecuador | ✅ | toda la interfaz y los mensajes de error |
| RNF-11 auditoría | ✅ | tabla `Auditoria` en BPM, prueba de corte y laboratorio |
| RNF-12 actualizable sin publicar | ✅ | umbrales y norma son datos, no código |
| RNF-13 Android 8+, claro y oscuro | ✅ | `minSdk = 26`, `temaClaro()` y `temaOscuro()` |
| RNF-14 costos | ✅ | el diseño evita Storage y Cloud Functions |

## Lo que falta y por qué

| Pendiente | Motivo |
|---|---|
| Firebase Auth, Firestore, Drive | Necesita credenciales del usuario. La interfaz ya está definida; ver `docs/NUBE.md` |
| Sensor ESP32 por Bluetooth | Necesita el dispositivo físico |
| Segunda opinión con Gemini | Opcional en la ERS; requiere internet y cuenta |
| Comparar lotes lado a lado | Marcado como deseable |
| Cifrado de la base local | Requiere `sqlcipher`; decisión de despliegue |
