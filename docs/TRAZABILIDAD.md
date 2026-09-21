# Trazabilidad: requerimiento → código

Dónde está implementado cada requerimiento de la ERS. Sirve para revisar el
avance, para encontrar el código al que hay que tocar, y para responder "¿esto
está hecho?" sin leer todo el repositorio.

**Estado:** ✅ implementado · 🟡 parcial · ⛔ pendiente

Las rutas son relativas a `android/`. El paquete raíz es `ec.cacaotrace`; para
no repetirlo, `nucleo/...` significa
`nucleo/src/main/kotlin/ec/cacaotrace/nucleo/...` y `ui/...`, `datos/...`,
`ia/...`, `informes/...` y `trabajo/...` significan
`app/src/main/kotlin/ec/cacaotrace/...`.

> **Sobre el estado de verificación.** Todo lo marcado ✅ en `nucleo/` está
> **probado y ejecutándose** (142 pruebas en verde). Todo lo que vive en `app/`
> está **escrito y revisado**, pero no compilado: el entorno de esta entrega no
> tiene el SDK de Android. Ver la nota final de [`COMPILAR.md`](COMPILAR.md).

---

## 3.3 Sincronización

| Código | Estado | Dónde |
|---|---|---|
| RF-SYN-01 escribir primero en el teléfono | ✅ | `datos/repositorios/*` escriben en Room y encolan aparte |
| RF-SYN-02 UUID, fecha, dispositivo, estado | ✅ | `datos/bd/Comunes.kt` (`@Embedded Comunes`) |
| RF-SYN-03 subir con reintentos | ✅ | `datos/sync/ServicioSincronizacion.kt` (`ESPERAS_REINTENTO`) |
| RF-SYN-04 fotos solo con WiFi | ✅ | `ServicioSincronizacion.soloWifiParaFotos` + interruptor en `ui/pantallas/Ajustes.kt` |
| RF-SYN-05 conflictos por última modificación | 🟡 | columnas listas; la resolución vive en el sincronizador remoto |
| RF-SYN-06 indicador de estado | ✅ | `EstadoSincronizacion.etiqueta`, mostrado en `Inicio.kt` y `Ajustes.kt` |
| RF-SYN-07 respaldo semanal y restauración | 🟡 | `SincronizadorRemoto.respaldoCompleto()` definido; implementar con Drive |

## 4.1 Cuenta y configuración

| Código | Estado | Dónde |
|---|---|---|
| RF-AUT-01 iniciar sesión con Google | ⛔ | requiere Firebase; ver [`NUBE.md`](NUBE.md) |
| RF-AUT-02 modo solo teléfono | ✅ | es el modo por defecto de toda la app |
| RF-AUT-03 invitar ayudantes | ⛔ | requiere Firebase |
| RF-CFG-01 fincas | ✅ | `ui/pantallas/Ajustes.kt` → `DialogoFinca` |
| RF-CFG-02 equipos | ✅ | `ui/pantallas/Ajustes.kt` → `DialogoEquipo` |
| RF-CFG-03 editar umbrales y norma | ✅ | `DialogoUmbral`, `RepositorioConfiguracion.guardarUmbral` / `guardarNorma` |
| RF-CFG-04 unidades e idioma | 🟡 | todo en español; kg y °C en toda la interfaz |

## 4.2 Lotes y trazabilidad

| Código | Estado | Dónde |
|---|---|---|
| RF-LOT-01 código automático y QR | ✅ | `nucleo/modelo/Codigos.kt`, `ui/comun/CodigoQr.kt` |
| RF-LOT-02 estados del lote | ✅ | `nucleo/modelo/Etapas.kt`, `RepositorioLotes.cambiarEstado` |
| RF-LOT-03 no saltar etapas sin motivo | ✅ | `EtapaSaltadaSinMotivo` + campo `motivoSalto` |
| RF-LOT-04 escanear QR y abrir el lote | ✅ | `ui/pantallas/EscanerQr.kt` (ML Kit) + `Codigos.loteDeSaco` |
| RF-LOT-05 lote de producción | ✅ | `RepositorioProduccion.crear`, `ui/pantallas/Produccion.kt` |
| RF-LOT-06 línea de tiempo | ✅ | `RepositorioLotes.lineaDeTiempo`, `ui/pantallas/DetalleLote.kt` |
| RF-LOT-07 reporte PDF | ✅ | `informes/GeneradorPdf.kt`, `ui/pantallas/Reporte.kt` |
| RF-LOT-08 balance de masa | ✅ | `nucleo/calculo/BalanceMasa.kt` |

## 4.3–4.8 Etapas del grano

| Código | Estado | Dónde |
|---|---|---|
| RF-REC-01 registrar la recepción | ✅ | `ui/pantallas/Recepcion.kt` |
| RF-REC-02 clasificar mazorcas con M1 | ✅ | `ui/comun/CapturaIa.kt` + `ia/ServicioModelos.kt` |
| RF-REC-03 modo ráfaga con conteo | ✅ | `Recepcion.kt`, contador por clase |
| RF-REC-04 descartes con motivo | ✅ | `Recepcion.kt` |
| RF-REC-05 programar la apertura | ✅ | `guardarRecepcion` + `trabajo/PlanificadorAvisos.kt` |
| RF-REC-06 GPS de la foto | ✅ | `ubicacion/ServicioUbicacion.kt` (solo precisión gruesa, última posición conocida), permiso ofrecido una vez en `Recepcion.kt` |
| RF-APE-01/02/03 apertura | ✅ | `ui/pantallas/Apertura.kt`, `RepositorioLotes.cascaraRecomendada` |
| RF-FER-01 a 05, 07 fermentación | ✅ | `ui/pantallas/Fermentacion.kt` |
| RF-FER-06 prueba parcial del día 5 | ✅ | `PruebaCorteEntidad.esParcial` + interruptor en `PruebaCorte.kt` |
| RF-FER-08 importar del sensor | ⛔ | requiere el ESP32 |
| RF-SEC-01 a 05 secado | ✅ | `ui/pantallas/Secado.kt` (incluye la prueba del puñado) |
| RF-PRC-01 a 07, 09, 10 prueba de corte | ✅ | `ui/pantallas/PruebaCorte.kt`, `nucleo/norma/CalificadorCorte.kt` |
| RF-PRC-08 plantilla del tablero | ✅ | `GeneradorPdf.plantillaTablero()` (cuadrícula 10×10, marcas de esquina, tarjeta de color) |
| RF-PRC-08 segunda opinión con Gemini | ⛔ | opcional en la ERS; requiere internet y cuenta |
| RF-ALM-01/02/03 almacenamiento | ✅ | `ui/pantallas/Almacen.kt`, `RepositorioApoyo.crearSaco` / `inspeccionarAlmacen` |

## 4.9–4.13 Producción y venta

| Código | Estado | Dónde |
|---|---|---|
| RF-TOS-01 a 05 tostado | ✅ | `ui/pantallas/Produccion.kt` → `SeccionTostado` |
| RF-DES-01/02 descascarillado | ✅ | `SeccionDescascarillado` + RN-12 |
| RF-REF-01 a 04 refinado | ✅ | `nucleo/calculo/Receta.kt`, `SeccionRefinado` (calculadora + sensorial 1–5) |
| RF-ATE-01 a 05 atemperado | ✅ | `SeccionAtemperado` (tres temperaturas, prueba del papel, M4, inspecciones) |
| RF-EMP-01 empaque y vencimiento | ✅ | `RepositorioProduccion.guardarEmpaque`, `SeccionEmpaque` |
| RF-EMP-02 datos de etiqueta | ✅ | `nucleo/etiqueta/` calcula la tabla nutricional desde la receta y el semáforo del RTE INEN 022; se muestra en `SeccionEmpaque` y se guarda con la tanda |
| RF-EMP-03 QR público | ⛔ | marcado como futuro en la ERS |
| RF-INV-01/02 inventario | ✅ | `RepositorioApoyo.existencias`, `ui/pantallas/Inventario.kt` |
| RF-COS-01/02 costos | ✅ | `costosDeProduccion` con costo por barra y por kg de grano |
| RF-VEN-01/02 ventas y margen | ✅ | `registrarVenta`, `margenDeProduccion`, `SeccionVentaYCostos` |

## 4.14–4.18 BPM, alertas, panel e IA

| Código | Estado | Dónde |
|---|---|---|
| RF-BPM-01 checklists configurables | ✅ | sembrados en `datos/bd/BaseDatos.kt` (`SembradoInicial`), `ui/pantallas/Bpm.kt` |
| RF-BPM-02 registros que se cierran a las 24 h | ✅ | `cerrarRegistrosBpmVencidos`, `RegistroBpmCerrado`, anotaciones posteriores |
| RF-BPM-03 exportar BPM en PDF | ✅ | botón "Exportar los registros en PDF" en `Bpm.kt` |
| RF-LAB-01 resultados de laboratorio | ✅ | `ui/pantallas/Laboratorio.kt` |
| RF-LAB-02 bloqueo por cadmio | ✅ | `guardarLaboratorio` + `LoteEntidad.ventaBloqueada`; desbloquear exige motivo escrito |
| RF-ALE-01 motor de reglas | ✅ | `nucleo/reglas/MotorReglas.kt` (17 reglas) |
| RF-ALE-02 notificaciones locales | ✅ | `trabajo/ServicioNotificaciones.kt`, `trabajo/PlanificadorAvisos.kt` |
| RF-ALE-03 qué pasó / por qué / qué hacer | ✅ | `nucleo/reglas/Alerta.kt`, `ui/pantallas/Alertas.kt` |
| RF-COR-01 biblioteca de correcciones | ✅ | `nucleo/reglas/Correcciones.kt` (C-01 a C-09), `PantallaCorrecciones` |
| RF-COR-02 registrar la corrección aplicada | ✅ | `RepositorioAlertas.atender` |
| RF-COR-03 reporte de alertas del lote | ✅ | `correccionesDeLote` + línea de tiempo + sección del PDF |
| RF-TAB-01 "¿Qué hago hoy?" | ✅ | `RepositorioApoyo.tareasDeHoy`, `ui/pantallas/Inicio.kt` |
| RF-TAB-02 indicadores | ✅ | `RepositorioApoyo.indicadores`, `ui/pantallas/Panel.kt` |
| RF-TAB-03 comparar lotes | ✅ | `ui/pantallas/Comparar.kt` + `RepositorioApoyo.resumenParaComparar`; resalta las filas que más difieren |
| RF-REP-01 exportar CSV | ✅ | `GeneradorPdf.csv`, botón en `ui/pantallas/Reporte.kt` |
| RF-GUI-01 guías del proceso | ✅ | `ui/pantallas/Guias.kt` (9 guías, cada una con su error más común) |
| RF-GUI-02 glosario | ✅ | pestaña "Glosario" de `Guias.kt` (18 términos) |
| RF-SEN-01 a 03 sensor Bluetooth | ⛔ | requiere el ESP32 |
| RF-IA-01 modelos en el teléfono | ✅ | `ia/ServicioModelos.kt` (LiteRT, `MappedByteBuffer` desde assets) |
| RF-IA-02 confianza y "no estoy seguro" | ✅ | `nucleo/ia/ResultadoIa.kt` → `ResultadoClasificacion.estaSeguro` |
| RF-IA-03 el usuario siempre corrige | ✅ | `ui/comun/CapturaIa.kt`, corrección por grano en `PruebaCorte.kt` |
| RF-IA-04 guía de encuadre | ✅ | parámetro `consejo` de `BotonFotoIa` |
| RF-IA-05 versión del modelo en cada análisis | ✅ | columnas `modeloVersion` en tostado, atemperado y prueba de corte |
| RF-IA-06 descargar y volver atrás | 🟡 | `activarModelo` conserva el anterior; falta la descarga desde la app |
| RF-IA-07 la IA es de apoyo | ✅ | avisos en `Ajustes.kt`, `PruebaCorte.kt` y el propio PDF del reporte |

## 5. Reglas de negocio

Las 17 reglas están en `nucleo/reglas/MotorReglas.kt`, con pruebas en
`nucleo/src/test/kotlin/.../MotorReglasTest.kt`. **Estas sí están ejecutadas.**

| Regla | Método | Prueba |
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

Las 29 tablas del §6 están en `datos/bd/entidades/`, más seis que el diseño
necesitó: `configuracion`, `stocks_minimos`, `checklists_bpm`, `auditoria`,
`cola_sync` y `umbrales_guardados`. Total: 35 entidades en
`datos/bd/BaseDatos.kt`.

## 8. Modelos de IA

| Requisito | Estado | Dónde |
|---|---|---|
| Contrato del modelo (§8.2) | ✅ | `entrenar_clasificador.py` + `ia/ServicioModelos.kt` |
| Metas de liberación (§8.1) | ✅ | comprobadas por el script; bloquean la instalación |
| Ciclo de mejora continua (§8.3) | ✅ | `comparar_modelos.py`, `FotoEntidad.aptaDataset` |
| Protocolo de fotografía (§8.4) | ✅ | `entrenamiento/README.md` + `GeneradorPdf.plantillaTablero()` |

## 9. Requerimientos no funcionales

| Código | Estado | Nota |
|---|---|---|
| RNF-01 offline | ✅ | nada obligatorio depende de la red |
| RNF-02 rendimiento | 🟡 | falta medir en un teléfono real de gama media |
| RNF-03 tamaño ≤ 150 MB | 🟡 | depende del tamaño final de los modelos |
| RNF-04 batería | ✅ | WorkManager con restricciones; notificaciones inexactas |
| RNF-05 seguridad | 🟡 | reglas de Firestore documentadas; falta cifrar la base local |
| RNF-06 privacidad | ✅ | `ui/pantallas/Privacidad.kt`: qué se guarda, qué no, dónde vive y los derechos de la LOPDP, en lenguaje sencillo |
| RNF-07 confiabilidad | ✅ | escrituras compuestas en `withTransaction`; nada se guarda a medias |
| RNF-08 respaldo | 🟡 | pendiente de la nube |
| RNF-09 usabilidad | ✅ | volteo en un toque, áreas táctiles ≥ 48 dp |
| RNF-10 español de Ecuador | ✅ | toda la interfaz y los mensajes de error |
| RNF-11 auditoría | ✅ | tabla `auditoria` en BPM, prueba de corte y laboratorio |
| RNF-12 actualizable sin publicar | ✅ | umbrales y norma son datos, no código |
| RNF-13 Android 8+, claro y oscuro | ✅ | `minSdk = 26`, `ui/Tema.kt` con ambos esquemas |
| RNF-14 costos | ✅ | el diseño evita Storage y Cloud Functions |

## Lo que falta y por qué

| Pendiente | Motivo |
|---|---|
| Compilar `:app` | El entorno de esta entrega no tiene el SDK de Android y `dl.google.com` está bloqueado. Ver [`COMPILAR.md`](COMPILAR.md) |
| Firebase Auth, Firestore, Drive | Necesita credenciales del usuario. La interfaz ya está definida; ver [`NUBE.md`](NUBE.md) |
| Sensor ESP32 por Bluetooth | Necesita el dispositivo físico |
| Segunda opinión con Gemini | Opcional en la ERS; requiere internet y cuenta |
| Cifrado de la base local | Requiere SQLCipher; decisión de despliegue |
