# Especificación de Requerimientos de Software (ERS)

## CacaoTrace — App móvil para producción bean-to-bar de chocolate 90% con cacao CCN-51

| Campo | Valor |
| --- | --- |
| Versión | 1.0 |
| Fecha | 19 de septiembre de 2026 |
| Estándar de referencia | ISO/IEC/IEEE 29148 (estructura adaptada) |
| Producto | Aplicación móvil Android (iOS opcional) con almacenamiento local y en la nube |
| Usuario principal | Productor artesanal en Guayaquil, cacao CCN-51 de Catarama (Los Ríos) |
| Estado | Borrador para validar con el desarrollador |

**Cómo leer este documento.** Cada requerimiento tiene un código (RF = funcional, RNF = no funcional, RN = regla de negocio) y una prioridad: **M** = obligatorio para la primera versión (MVP), **D** = deseable, **F** = futuro. Entrégaselo completo a quien vaya a programar la app; con esto puede cotizar y construir.

---

## 1. Introducción

### 1.1 Propósito

Definir qué debe hacer la app CacaoTrace para registrar, controlar, analizar y corregir todo el proceso de elaboración de chocolate, desde la recepción de mazorcas hasta la venta, incluyendo análisis de imágenes con inteligencia artificial y trazabilidad para los trámites de ARCSA.

### 1.2 Alcance

La app cubre 12 etapas: recepción de mazorcas, reposo, apertura, fermentación, secado, prueba de corte, almacenamiento, tostado, descascarillado, refinado/conchado, atemperado/moldeado y empaque/venta. Además incluye inventario, costos, BPM, resultados de laboratorio (cadmio), alertas, biblioteca de correcciones, tableros y guías para principiantes.

**Fuera de alcance (v1):** control automático de máquinas, facturación electrónica del SRI, tienda en línea, multiempresa.

### 1.3 Definiciones

| Término | Significado |
| --- | --- |
| Lote | Conjunto de cacao que se procesa junto desde la recepción; recibe un código único y QR |
| Lote de producción | Tanda de chocolate hecha con uno o más lotes de grano |
| Baba | Grano fresco con pulpa, recién sacado de la mazorca |
| Prueba de corte | Cortar 100 granos secos a lo largo y clasificarlos por color y defecto |
| TFLite / LiteRT | Formato de modelo de IA que corre dentro del teléfono sin internet |
| Offline-first | La app funciona completa sin internet y sincroniza cuando hay señal |
| Sincronización | Copia de datos del teléfono a la nube y viceversa |

### 1.4 Referencias

NTE INEN 176 (cacao en grano), NTE INEN-ISO 1114 (prueba de corte), NTE INEN-ISO 2291 (humedad), NTE INEN 621 (chocolates), RTE INEN 022 (etiquetado), Resolución ARCSA-DE-067-2015-GGG y normativa ARCSA vigente, Reglamento (UE) 2023/915 (cadmio), y los dos informes de investigación previos de este proyecto.

---

## 2. Descripción general

### 2.1 Perspectiva del producto

App móvil independiente, **offline-first**, con base de datos local en el teléfono que se sincroniza con la nube (Firebase + Google Drive del usuario). Puede recibir datos de un sensor de temperatura ESP32 por Bluetooth.

```
flowchart LR
  subgraph Telefono["Teléfono (funciona sin internet)"]
    UI[Pantallas] --> BD[(SQLite local)]
    UI --> CAM[Cámara]
    CAM --> IA[Modelos TFLite]
    IA --> BD
    BD --> COLA[Cola de sincronización]
    ALR[Motor de alertas local] --> NOT[Notificaciones]
    BD --> ALR
  end
  SEN[Sensor ESP32 + DS18B20] -- Bluetooth --> UI
  COLA -- con internet --> FS[(Firestore: registros)]
  COLA -- con internet --> GD[(Google Drive: fotos, respaldos, reportes)]
  UI -. opcional .-> GEM[Gemini: segunda opinión]
  RC[Remote Config: umbrales y versión de modelos] --> UI
  GD --> LS[Looker Studio / Sheets]
```

### 2.2 Usuarios

| Rol | Descripción | Permisos |
| --- | --- | --- |
| Productor (dueño) | Tú. Administra todo | Todo |
| Ayudante | Registra volteos, fotos y lecturas | Registrar; no borrar ni ver costos |
| Consulta | Catador, cliente mayorista o inspector | Solo lectura de lotes compartidos |

### 2.3 Entorno de operación

- Android 8.0 (API 26) o superior, 3 GB de RAM, cámara de 12 MP. iOS 15+ como opción futura (misma base de código).
- Uso en Catarama sin señal (recepción) y en Guayaquil con WiFi (resto del proceso).
- Ambiente caluroso y húmedo: el teléfono se usa con manos sucias o húmedas.

### 2.4 Restricciones

- Presupuesto bajo: usar servicios con nivel gratuito y evitar servidores propios.
- Usuario sin experiencia técnica: todo en español simple, con guías.
- Las fotos se guardan en el **Google Drive del propio usuario** (15 GB gratis), no en servidores del desarrollador.

### 2.5 Supuestos

- Un lote típico: ~100 mazorcas → 16–18 kg de baba → ~6 kg de grano seco → ~5 kg de chocolate.
- El usuario tiene cuenta de Google.
- Los umbrales técnicos (temperaturas, humedad, norma) pueden cambiar y deben ser editables.

---

## 3. Arquitectura y tecnología recomendada

| Capa | Tecnología sugerida | Motivo |
| --- | --- | --- |
| App | **Flutter** (Dart) | Una sola base de código para Android e iOS; buen soporte de cámara, Bluetooth y TFLite |
| Base de datos local | SQLite con **Drift** | Relacional, rápida, funciona sin internet |
| Archivos locales | Carpeta privada de la app | Fotos y modelos |
| IA en el teléfono | **LiteRT/TFLite** (`tflite_flutter`) + YOLO exportado a TFLite | Funciona offline |
| Autenticación | Firebase Authentication con Google | Un solo inicio de sesión para Firebase y Drive |
| Registros en la nube | **Cloud Firestore** con persistencia offline | Sincronización entre teléfonos; nivel gratuito suficiente |
| Fotos, respaldos y reportes | **Google Drive API** (permiso `drive.file`) | Usa el Drive del usuario; la app solo ve lo que ella crea |
| Parámetros y versión de modelos | Firebase Remote Config | Cambiar umbrales sin publicar una nueva versión |
| IA en la nube (opcional) | Firebase AI Logic con Gemini | Segunda opinión con explicación en texto |
| Notificaciones | Notificaciones locales programadas | Funcionan sin internet |
| Sensor | Bluetooth LE (`flutter_blue_plus`) | El ESP32 envía la temperatura directo al teléfono |
| Reportes | Generación de PDF en el teléfono | Trazabilidad para ARCSA y clientes |

**Nota de costos:** Firebase Storage y Cloud Functions requieren el plan de pago por uso (Blaze) en proyectos nuevos; por eso este diseño guarda las fotos en Google Drive y ejecuta alertas en el teléfono, para poder operar con el plan gratuito (Spark). Verificar las condiciones vigentes de Firebase al iniciar el desarrollo.

### 3.1 Qué se guarda dónde

| Dato | Teléfono | Nube | Detalle |
| --- | --- | --- | --- |
| Registros de todas las etapas | ✅ Fuente principal | ✅ Firestore | Se escribe primero en el teléfono; se sube al haber internet |
| Fotos originales | ✅ Comprimidas (máx. 1600 px, JPG 85%) | ✅ Google Drive | Borrado local opcional tras subir (configurable) |
| Miniaturas | ✅ | ❌ | Para listas rápidas |
| Modelos de IA (.tflite) | ✅ | ✅ Drive/Remote Config (versiones nuevas) | La app descarga modelos nuevos solo con WiFi |
| Umbrales y tabla de norma | ✅ Copia en caché | ✅ Remote Config | Si no hay internet usa la última copia |
| Guías y correcciones | ✅ Incluidas en la app | ✅ Actualizables | Disponibles sin internet |
| Respaldo completo | ❌ | ✅ Drive (ZIP semanal) | Restaurable en otro teléfono |
| Reportes PDF y CSV | ✅ Temporal | ✅ Drive | Carpeta por lote |
| Fotos corregidas para reentrenar | ✅ | ✅ Drive `/dataset/` | Alimentan el entrenamiento de nuevos modelos |
| Credenciales | ✅ Almacén seguro cifrado | ❌ | Nunca en texto plano |

### 3.2 Estructura de carpetas en Google Drive

```
CacaoTrace/
  Lotes/
    L-2026-001/
      01_recepcion/  02_apertura/  03_fermentacion/  04_secado/
      05_prueba_corte/  06_tostado/  07_refinado/  08_atemperado/  09_empaque/
      reporte_trazabilidad_L-2026-001.pdf
  Produccion/  P-2026-001/ ...
  Laboratorio/   (resultados de cadmio y microbiología)
  BPM/           (checklists exportados)
  Respaldos/     (ZIP semanales)
  Exportes/      (CSV / Google Sheets para Looker Studio)
  Dataset/
    mazorca/<clase>/   corte/<imagen + etiquetas>/   tostado/<clase>/   chocolate/<clase>/
```

### 3.3 Sincronización

| Código | Requerimiento | Prioridad |
| --- | --- | --- |
| RF-SYN-01 | Todo registro se guarda primero en el teléfono y queda marcado como "pendiente de sincronizar" | M |
| RF-SYN-02 | Cada registro tiene identificador único (UUID), fecha de modificación, dispositivo y estado de sincronización | M |
| RF-SYN-03 | Al detectar internet, la app sube registros pendientes y luego las fotos, con reintentos automáticos | M |
| RF-SYN-04 | Opción "subir fotos solo con WiFi" activada por defecto | M |
| RF-SYN-05 | Conflictos: gana la última modificación por campo y se guarda un historial del valor anterior | D |
| RF-SYN-06 | Indicador visible: "Todo sincronizado" / "N pendientes" / "Sin conexión" | M |
| RF-SYN-07 | Respaldo automático semanal a Drive y restauración completa en un teléfono nuevo | M |

---

## 4. Requerimientos funcionales por módulo

### 4.1 Cuenta y configuración (AUT / CFG)

| Código | Requerimiento | Prioridad |
| --- | --- | --- |
| RF-AUT-01 | Iniciar sesión con Google (una sola vez; luego funciona sin internet) | M |
| RF-AUT-02 | Modo "solo teléfono" sin cuenta, con aviso de que no hay respaldo | D |
| RF-AUT-03 | Invitar ayudantes por correo y asignar rol | D |
| RF-CFG-01 | Registrar fincas de origen (nombre, ubicación GPS, contacto, variedad) | M |
| RF-CFG-02 | Registrar equipos: fermentador (medidas, material, capacidad), marquesina, horno, melanger, moldes | M |
| RF-CFG-03 | Editar todos los umbrales de la tabla 5.1 y la tabla de norma de prueba de corte | M |
| RF-CFG-04 | Unidades (kg/lb, °C) e idioma español por defecto | M |

### 4.2 Lotes y trazabilidad (LOT)

| Código | Requerimiento | Prioridad |
| --- | --- | --- |
| RF-LOT-01 | Crear lote con código automático `L-AAAA-NNN` y generar QR imprimible | M |
| RF-LOT-02 | El lote avanza por estados: Recepción → Reposo → Fermentación → Secado → Almacenado → Tostado → Descascarillado → Refinado → Atemperado → Empacado → Vendido/Consumido. También Descartado | M |
| RF-LOT-03 | No permitir saltar etapas obligatorias sin confirmar el motivo (queda registrado) | M |
| RF-LOT-04 | Escanear el QR de un saco o barra y abrir su historia completa | M |
| RF-LOT-05 | Crear lote de producción `P-AAAA-NNN` a partir de uno o varios lotes de grano, con los kg usados de cada uno | M |
| RF-LOT-06 | Línea de tiempo del lote con todas las fotos, lecturas, alertas y correcciones | M |
| RF-LOT-07 | Reporte PDF de trazabilidad por lote (origen, fechas, parámetros, prueba de corte, laboratorio) | M |
| RF-LOT-08 | Balance de masa automático: rendimiento en cada etapa comparado con el esperado | M |

### 4.3 Recepción y reposo de mazorcas (REC)

| Código | Requerimiento | Prioridad |
| --- | --- | --- |
| RF-REC-01 | Registrar finca, fecha de cosecha, fecha de llegada, número de sacos, número de mazorcas y peso total | M |
| RF-REC-02 | Tomar fotos de mazorcas; el modelo M1 clasifica cada foto en sana / monilia / fitóftora / otro, **sin internet** | M |
| RF-REC-03 | Modo ráfaga: fotografiar mazorca por mazorca y llevar el conteo automático por clase | D |
| RF-REC-04 | Registrar mazorcas descartadas con motivo | M |
| RF-REC-05 | Programar la fecha de apertura según los días de reposo elegidos (3–6) y avisar el día indicado | M |
| RF-REC-06 | Guardar la ubicación GPS de la foto (opcional, para trazabilidad de origen) | D |

### 4.4 Apertura (APE)

| Código | Requerimiento | Prioridad |
| --- | --- | --- |
| RF-APE-01 | Registrar mazorcas abiertas, kg de baba y kg de cáscara añadida al fermentador | M |
| RF-APE-02 | Calcular rendimiento baba/mazorca y compararlo con el esperado (~0,17 kg) | M |
| RF-APE-03 | Recomendar la cantidad de cáscara a añadir si la baba es menor a la masa mínima configurada | M |

### 4.5 Fermentación (FER)

| Código | Requerimiento | Prioridad |
| --- | --- | --- |
| RF-FER-01 | Iniciar fermentación indicando fermentador, hora de inicio, masa y aislamiento usado | M |
| RF-FER-02 | Registrar temperatura manual (con foto opcional del termómetro) o automática por sensor Bluetooth | M |
| RF-FER-03 | Registrar pH, olor (lista: alcohólico, avinagrado, pútrido, amoniaco, moho) y aspecto con foto | M |
| RF-FER-04 | Recordatorio de volteo cada 24 h; el volteo se confirma con un toque y foto opcional | M |
| RF-FER-05 | Gráfica de la curva de temperatura frente a la curva objetivo | M |
| RF-FER-06 | Prueba de corte parcial en el día 5 (usa el módulo PRC con 20–50 granos) | D |
| RF-FER-07 | Cerrar la fermentación con duración total y pasar el lote a Secado | M |
| RF-FER-08 | Importar lecturas del sensor guardadas en su memoria cuando el teléfono no estuvo cerca | D |

### 4.6 Secado (SEC)

| Código | Requerimiento | Prioridad |
| --- | --- | --- |
| RF-SEC-01 | Registrar método (marquesina, sol, secador), espesor de capa y remociones del día | M |
| RF-SEC-02 | Registrar humedad del grano (medidor) y temperatura/humedad ambiente | M |
| RF-SEC-03 | Guía de la "prueba del puñado" cuando no hay medidor, registrada como resultado cualitativo | M |
| RF-SEC-04 | Foto diaria del grano; alerta si el usuario marca moho visible | M |
| RF-SEC-05 | Cerrar el secado con peso seco y calcular rendimiento baba→seco | M |

### 4.7 Prueba de corte (PRC)

| Código | Requerimiento | Prioridad |
| --- | --- | --- |
| RF-PRC-01 | Guía paso a paso: tomar 100 granos al azar, cortar a lo largo, colocar en tablero 10 × 10 | M |
| RF-PRC-02 | Plantilla de tablero imprimible (PDF) con tarjeta de color de referencia | M |
| RF-PRC-03 | Foto del tablero; el modelo M2 detecta cada grano y lo clasifica **sin internet** | M |
| RF-PRC-04 | Mostrar cada grano con un recuadro de color; el usuario toca un grano para corregir su clase | M |
| RF-PRC-05 | Conteo manual alternativo con botones grandes por clase (+/−) | M |
| RF-PRC-06 | Calcular porcentajes y resultado según la tabla de norma configurable, mostrando qué requisito falla | M |
| RF-PRC-07 | Aviso si se detectan menos de 97 o más de 103 granos | M |
| RF-PRC-08 | Segunda opinión opcional con Gemini (requiere internet), que explica en texto lo que ve | D |
| RF-PRC-09 | Guardar la foto y las correcciones del usuario en `Dataset/corte/` para reentrenar | M |
| RF-PRC-10 | Registrar peso de 100 granos | M |

### 4.8 Almacenamiento (ALM)

| Código | Requerimiento | Prioridad |
| --- | --- | --- |
| RF-ALM-01 | Registrar sacos (número, peso, ubicación) con QR por saco | M |
| RF-ALM-02 | Inspección periódica (cada 7 días configurable): humedad ambiente, olor, plagas, moho, foto | M |
| RF-ALM-03 | Salidas de grano hacia producción con descuento automático de inventario | M |

### 4.9 Tostado (TOS)

| Código | Requerimiento | Prioridad |
| --- | --- | --- |
| RF-TOS-01 | Registrar equipo, kg de entrada, temperatura y tiempo (perfil por tramos opcional) | M |
| RF-TOS-02 | Temporizador integrado con avisos de remover | M |
| RF-TOS-03 | Foto de nibs/granos; el modelo M3 estima grado de tostado (crudo, ligero, medio, oscuro, quemado) | D |
| RF-TOS-04 | Registrar kg de salida y calcular merma | M |
| RF-TOS-05 | Guardar "recetas de tostado" y comparar resultados entre lotes | D |

### 4.10 Descascarillado (DES)

| Código | Requerimiento | Prioridad |
| --- | --- | --- |
| RF-DES-01 | Registrar kg de nibs y kg de cascarilla; calcular porcentaje de cascarilla | M |
| RF-DES-02 | Alerta si la cascarilla es muy distinta del valor esperado (posible pérdida de nibs o nibs sucios) | D |

### 4.11 Refinado y conchado (REF)

| Código | Requerimiento | Prioridad |
| --- | --- | --- |
| RF-REF-01 | Calculadora de receta: ingresar kg de nibs y % objetivo (90%) → kg de azúcar, manteca y lecitina | M |
| RF-REF-02 | Registrar hora de inicio, momento de añadir azúcar, temperatura y horas totales | M |
| RF-REF-03 | Recordatorios programados (añadir azúcar, revisar, probar textura) | M |
| RF-REF-04 | Registro sensorial simple: acidez, amargor, astringencia, textura (escala 1–5) | M |

### 4.12 Atemperado y moldeado (ATE)

| Código | Requerimiento | Prioridad |
| --- | --- | --- |
| RF-ATE-01 | Asistente de atemperado con temporizador y las tres temperaturas objetivo, con método (siembra o mármol) | M |
| RF-ATE-02 | Registrar temperatura y humedad del cuarto; bloquear con advertencia si superan los umbrales | M |
| RF-ATE-03 | Registrar prueba del papel (endurece en ~3 min con brillo: sí/no) con foto | M |
| RF-ATE-04 | Foto de barras terminadas; el modelo M4 detecta fat bloom, sugar bloom o falta de brillo | D |
| RF-ATE-05 | Inspección de barras almacenadas a los 7, 30 y 90 días con foto y M4 | D |

### 4.13 Empaque, etiquetado, inventario y ventas (EMP / INV / VEN)

| Código | Requerimiento | Prioridad |
| --- | --- | --- |
| RF-EMP-01 | Registrar número de barras, peso unitario, fecha de elaboración y fecha de vencimiento (calculada según vida útil configurada) | M |
| RF-EMP-02 | Generar datos de etiqueta: ingredientes en orden, lote, fechas, contenido neto, número de notificación sanitaria y semáforo nutricional a partir de la tabla nutricional ingresada | D |
| RF-EMP-03 | QR en la etiqueta que abre una página pública simple con el origen del lote (opcional) | F |
| RF-INV-01 | Inventario de mazorcas, grano seco, nibs, chocolate, azúcar, manteca, empaques | M |
| RF-INV-02 | Aviso de stock mínimo de insumos | D |
| RF-COS-01 | Registrar costos (mazorcas, transporte, insumos, energía, empaques, trámites) por lote | M |
| RF-COS-02 | Calcular costo por kg de grano y costo por barra | M |
| RF-VEN-01 | Registrar ventas y consumo propio por lote de producción, con cliente y precio | D |
| RF-VEN-02 | Reporte de margen por lote | D |

### 4.14 BPM y laboratorio (BPM / LAB)

| Código | Requerimiento | Prioridad |
| --- | --- | --- |
| RF-BPM-01 | Checklists diarios configurables: limpieza de equipos, higiene personal, control de plagas, agua | M |
| RF-BPM-02 | Registros firmados (nombre + fecha + hora) no editables después de 24 h, solo anotables | D |
| RF-BPM-03 | Exportar registros BPM en PDF por rango de fechas | M |
| RF-LAB-01 | Registrar resultados de laboratorio (cadmio, humedad, microbiología) con PDF adjunto y lote asociado | M |
| RF-LAB-02 | Alerta si cadmio supera el límite configurado (0,80 mg/kg por defecto) y bloqueo de "venta" del lote hasta confirmar | M |

### 4.15 Alertas y correcciones (ALE / COR)

| Código | Requerimiento | Prioridad |
| --- | --- | --- |
| RF-ALE-01 | Motor de reglas que corre en el teléfono y evalúa cada nueva lectura contra los umbrales de la tabla 5.1 | M |
| RF-ALE-02 | Notificaciones locales programadas (volteos, apertura de mazorcas, refinado, inspecciones), también sin internet | M |
| RF-ALE-03 | Cada alerta muestra: qué pasó, por qué importa y qué hacer (enlace a la corrección) | M |
| RF-COR-01 | Biblioteca de correcciones con pasos sencillos y fotos (ver tabla 5.2) | M |
| RF-COR-02 | Registrar qué corrección se aplicó, cuándo y el resultado posterior | M |
| RF-COR-03 | Reporte de alertas y correcciones por lote para aprender de errores | D |

### 4.16 Panel, reportes y ayuda (TAB / REP / GUI)

| Código | Requerimiento | Prioridad |
| --- | --- | --- |
| RF-TAB-01 | Pantalla de inicio "¿Qué hago hoy?" con tareas del día de todos los lotes | M |
| RF-TAB-02 | Indicadores: rendimientos por etapa, % fermentación, temperatura máxima de fermentación, días de secado, costo por barra, alertas abiertas | M |
| RF-TAB-03 | Comparar dos o más lotes lado a lado | D |
| RF-REP-01 | Exportar a CSV y a Google Sheets para Looker Studio | M |
| RF-GUI-01 | Guía ilustrada por etapa, disponible sin internet | M |
| RF-GUI-02 | Glosario de términos con ejemplos | M |

### 4.17 Sensor (SEN)

| Código | Requerimiento | Prioridad |
| --- | --- | --- |
| RF-SEN-01 | Vincular un sensor ESP32 por Bluetooth y asignarlo a un fermentador | D |
| RF-SEN-02 | Recibir temperatura cada 15 min (configurable) y guardarla como lectura del lote activo | D |
| RF-SEN-03 | Mostrar batería y última lectura; alerta si no llegan datos en 2 h | D |

### 4.18 Inteligencia artificial (IA) — requisitos comunes

| Código | Requerimiento | Prioridad |
| --- | --- | --- |
| RF-IA-01 | Todos los modelos principales (M1–M4) corren dentro del teléfono sin internet | M |
| RF-IA-02 | Mostrar clase y porcentaje de confianza; si la confianza es menor al umbral (0,60 por defecto) mostrar "No estoy seguro" y pedir decisión del usuario | M |
| RF-IA-03 | El usuario siempre puede corregir el resultado; la corrección prevalece y se guarda | M |
| RF-IA-04 | Guía de encuadre en la cámara (marco, distancia, aviso de poca luz o foto borrosa) | M |
| RF-IA-05 | Guardar versión del modelo usada en cada análisis | M |
| RF-IA-06 | Descargar modelos nuevos con WiFi y permitir volver a la versión anterior | D |
| RF-IA-07 | La IA es de apoyo: los resultados legales (grado, cadmio) dependen de la norma y del laboratorio, y la app lo indica | M |

---

## 5. Reglas de negocio

### 5.1 Umbrales por defecto (todos editables)

| Código | Etapa | Regla | Acción |
| --- | --- | --- | --- |
| RN-01 | Reposo | Días de reposo entre 3 y 6 | Aviso el día de apertura; alerta si pasan de 7 días |
| RN-02 | Apertura | Baba < 20 kg | Recomendar añadir cáscara de mazorca y aislamiento extra |
| RN-03 | Fermentación | Sin volteo registrado en 26 h | Alerta "Voltear ahora" |
| RN-04 | Fermentación | Temperatura < 40 °C a partir de las 60 h | Alerta "Fermentación fría" → corrección C-01 |
| RN-05 | Fermentación | Temperatura > 52 °C | Alerta "Muy caliente" → voltear y destapar |
| RN-06 | Fermentación | Olor pútrido o amoniaco | Alerta "Sobrefermentación" → pasar a secado |
| RN-07 | Fermentación | Duración > 7 días | Alerta de sobrefermentación |
| RN-08 | Secado | Humedad del grano > 7% al cerrar | No permitir pasar a almacenamiento sin confirmación |
| RN-09 | Secado | Moho visible | Alerta → corrección C-03 |
| RN-10 | Prueba de corte | Resultado no conforme según tabla | Mostrar requisitos que fallan y correcciones |
| RN-11 | Almacenamiento | Humedad relativa > 70% en inspección | Alerta de riesgo de moho |
| RN-12 | Tostado | Merma fuera de 3–12% | Revisar balanza o tostado excesivo |
| RN-13 | Atemperado | Cuarto > 22 °C o HR > 60% | Advertencia de fat/sugar bloom |
| RN-14 | Atemperado | Temperatura de trabajo fuera de 31–32 °C | Aviso en el asistente |
| RN-15 | Laboratorio | Cadmio > 0,80 mg/kg | Bloquear venta del lote hasta confirmar |
| RN-16 | IA | Confianza < 0,60 | Pedir confirmación manual |
| RN-17 | Rendimiento | Rendimiento de una etapa se aleja más de 20% del esperado | Aviso para revisar datos |

### 5.2 Biblioteca inicial de correcciones

| Código | Problema | Qué hacer (resumen) |
| --- | --- | --- |
| C-01 | Fermentación fría | Añadir cáscara de mazorca troceada, cubrir con hojas y sacos de yute, cerrar tapa, reforzar aislamiento exterior, juntar más baba en el próximo lote |
| C-02 | Muchos granos violetas | Alargar la fermentación en el próximo lote y revisar volteos |
| C-03 | Moho en secado | Separar granos afectados, capa más delgada, más remociones, ventilar la marquesina |
| C-04 | Muchos pizarrosos | La fermentación no arrancó: revisar masa mínima y aislamiento |
| C-05 | Olor pútrido | Pasar a secado de inmediato; acortar la fermentación en el próximo lote |
| C-06 | Chocolate arenoso | Más horas de refinado |
| C-07 | Chocolate muy ácido | Conchado más largo con tapa abierta; revisar tostado |
| C-08 | Fat bloom | Repetir atemperado; bajar temperatura del cuarto |
| C-09 | Sugar bloom | Evitar refrigerador sin empaque; controlar humedad del cuarto |

---

## 6. Modelo de datos (resumen)

Todas las tablas incluyen: `id (UUID)`, `creado_en`, `modificado_en`, `usuario_id`, `dispositivo_id`, `estado_sync`, `eliminado (lógico)`.

| Tabla | Campos principales |
| --- | --- |
| `finca` | nombre, provincia, cantón, lat, lon, contacto, variedad |
| `equipo` | tipo, nombre, largo\_cm, ancho\_cm, alto\_cm, material, capacidad\_kg, notas |
| `lote` | codigo, finca\_id, variedad, fecha\_cosecha, fecha\_llegada, estado, qr |
| `recepcion` | lote\_id, sacos, mazorcas\_total, peso\_kg, mazorcas\_sanas, monilia, fitoftora, otro, descartadas, dias\_reposo, fecha\_apertura\_plan |
| `apertura` | lote\_id, fecha, mazorcas\_abiertas, kg\_baba, kg\_cascara\_añadida |
| `fermentacion` | lote\_id, equipo\_id, inicio, fin, masa\_kg, aislamiento, estado |
| `lectura_fermentacion` | fermentacion\_id, fecha\_hora, temp\_c, ph, olor, fuente (manual/sensor), foto\_id |
| `volteo` | fermentacion\_id, fecha\_hora, foto\_id |
| `secado` | lote\_id, metodo, inicio, fin, kg\_seco |
| `lectura_secado` | secado\_id, fecha, humedad\_grano, temp\_amb, hr\_amb, espesor\_cm, remociones, moho, foto\_id |
| `prueba_corte` | lote\_id, fecha, perfil\_norma, granos, conteos (JSON por clase), porcentajes (JSON), resultado, peso\_100\_g, modelo\_version, foto\_id |
| `saco` | lote\_id, codigo\_qr, peso\_kg, ubicacion, estado |
| `inspeccion_almacen` | fecha, hr, temp, plagas, moho, olor, foto\_id |
| `lote_produccion` | codigo, fecha, receta\_id, kg\_chocolate, estado |
| `lote_produccion_origen` | lote\_produccion\_id, lote\_id, kg\_usados |
| `tostado` | lote\_produccion\_id, equipo\_id, kg\_entrada, perfil (JSON), kg\_salida, grado\_ia, foto\_id |
| `descascarillado` | lote\_produccion\_id, kg\_nibs, kg\_cascarilla |
| `refinado` | lote\_produccion\_id, inicio, horas, azucar\_kg, manteca\_kg, lecitina\_kg, evaluacion\_sensorial (JSON) |
| `atemperado` | lote\_produccion\_id, metodo, temps (JSON), temp\_cuarto, hr\_cuarto, prueba\_papel, resultado\_ia, foto\_id |
| `empaque` | lote\_produccion\_id, barras, peso\_g, fecha\_elab, fecha\_venc |
| `inventario_mov` | item, tipo (entrada/salida), cantidad, unidad, referencia |
| `costo` | lote\_id o lote\_produccion\_id, concepto, monto\_usd, fecha |
| `venta` | lote\_produccion\_id, cliente, barras, precio\_usd, fecha |
| `bpm_registro` | checklist\_id, fecha, items (JSON), responsable |
| `laboratorio` | lote\_id, analisis, valor, unidad, limite, laboratorio, archivo\_id |
| `alerta` | lote\_id, regla, mensaje, fecha, estado (abierta/atendida) |
| `correccion_aplicada` | alerta\_id, correccion\_codigo, fecha, resultado |
| `foto` | ruta\_local, drive\_file\_id, miniatura, etapa, lote\_id, analisis\_ia (JSON), etiqueta\_usuario, apta\_dataset |
| `modelo_ia` | nombre, version, clases, umbral, ruta, activo |

---

## 7. Pantallas principales

1. **Inicio "¿Qué hago hoy?"** — tareas del día, alertas abiertas, estado de sincronización.
2. **Lotes** — lista con estado y semáforo de calidad; botón "Nuevo lote" y "Escanear QR".
3. **Detalle de lote** — línea de tiempo, balance de masa, botones de la etapa actual.
4. **Cámara con IA** — marco guía, aviso de luz, resultado con confianza y botón "Corregir".
5. **Fermentación** — gráfica de temperatura, botón grande "Registré volteo", última lectura del sensor.
6. **Prueba de corte** — foto del tablero con recuadros por grano, conteos, resultado y requisitos que fallan.
7. **Asistente de atemperado** — temperatura objetivo grande, temporizador y pasos.
8. **Inventario, costos y ventas.**
9. **BPM y laboratorio.**
10. **Panel de indicadores.**
11. **Guías y correcciones.**
12. **Ajustes** — fincas, equipos, umbrales, norma, cuenta, respaldo, modelos.

**Diseño visual:** botones de al menos 48 × 48 dp, texto mínimo 16 sp, alto contraste para uso al sol, máximo 3 toques para registrar una lectura, iconos con texto.

---

## 8. Especificación de los modelos de IA

### 8.1 Resumen

| Modelo | Tarea | Tipo | Clases | Datos iniciales | Meta mínima para liberar |
| --- | --- | --- | --- | --- | --- |
| M1 Mazorca | Estado sanitario de la mazorca | Clasificación, EfficientNetV2-B0 o MobileNetV3, 224 px | sana, monilia, fitoftora, otro | CocoaMoniliaDataSet, Roboflow (CC BY 4.0) + fotos propias | Exactitud ≥ 90%; recall de monilia y fitóftora ≥ 90% |
| M2 Prueba de corte | Detectar y clasificar cada grano cortado | Detección YOLO11n, 640 px | bien\_fermentado, ligeramente\_fermentado, violeta, pizarroso, mohoso, dano\_insectos, germinado, vano\_plano, otro | Fotos propias etiquetadas (datasets abiertos solo para aprendizaje según licencia) | mAP50 ≥ 0,80; error de conteo ≤ 3 granos por tablero; porcentaje de fermentados con error ≤ 5 puntos frente a conteo experto |
| M3 Tostado | Grado de tostado | Clasificación, 224 px | crudo, ligero, medio, oscuro, quemado | Solo fotos propias | Exactitud ≥ 85% |
| M4 Chocolate | Calidad superficial de la barra | Clasificación, 224 px | atemperado\_ok, fat\_bloom, sugar\_bloom, sin\_brillo | Solo fotos propias | Exactitud ≥ 85% |

La humedad del grano y el cadmio **no** se estiman con fotos: se miden con medidor y laboratorio.

### 8.2 Contrato del modelo con la app

- Entrada: imagen RGB redimensionada al tamaño indicado, píxeles 0–255 (el modelo normaliza internamente).
- Salida: probabilidades por clase (clasificadores) o cajas + clase + confianza (detector).
- Cada modelo se entrega con `etiquetas.txt` y `metadatos.json` (versión, clases, umbral, exactitud).
- Tamaño objetivo: ≤ 10 MB por modelo; tiempo de respuesta ≤ 2 s en un teléfono de gama media.

### 8.3 Ciclo de mejora continua

1. El usuario toma fotos y corrige resultados en la app.
2. Las fotos corregidas se guardan en `Drive/CacaoTrace/Dataset/`.
3. Cada cierto número de fotos nuevas (por ejemplo 200) se reentrena en Google Colab con los scripts entregados.
4. Se compara el modelo nuevo con el anterior sobre el mismo conjunto de prueba; solo se publica si mejora.
5. La app descarga la nueva versión con WiFi y conserva la anterior para volver atrás.

### 8.4 Protocolo de fotografía

Fondo blanco mate, luz LED 5000–6500 K (caja de luz), teléfono a 30 cm y perpendicular, sin flash, tarjeta de color en la foto; para la prueba de corte, tablero de 10 × 10 con los granos mostrando la cara cortada.

### 8.5 Licencias de datos

Usar para el producto solo datasets que permitan uso comercial (por ejemplo CC BY 4.0) y fotos propias. El dataset de prueba de corte de Santos et al. (2019) tiene licencia CC BY-NC-ND 4.0 y no debe usarse para un modelo comercial sin permiso de los autores.

---

## 9. Requerimientos no funcionales

| Código | Categoría | Requerimiento |
| --- | --- | --- |
| RNF-01 | Offline | El 100% de las funciones marcadas M funcionan sin internet, excepto inicio de sesión inicial, sincronización y segunda opinión con Gemini |
| RNF-02 | Rendimiento | Abrir la app en ≤ 3 s; guardar un registro en ≤ 0,5 s; análisis de imagen en ≤ 2 s (M1, M3, M4) y ≤ 4 s (M2) en teléfono de gama media |
| RNF-03 | Almacenamiento | La app instalada ocupa ≤ 150 MB con modelos; aviso si quedan menos de 500 MB libres |
| RNF-04 | Batería | Sin procesos en segundo plano salvo sincronización y Bluetooth del sensor, que puede desactivarse |
| RNF-05 | Seguridad | Base local cifrada; comunicación con HTTPS; permisos de Drive limitados a archivos creados por la app; reglas de Firestore que solo permiten a cada usuario ver sus datos y los compartidos |
| RNF-06 | Privacidad | Cumplir la Ley Orgánica de Protección de Datos Personales del Ecuador: aviso de privacidad, consentimiento para ubicación y opción de borrar la cuenta y sus datos |
| RNF-07 | Confiabilidad | Ningún dato se pierde si la app se cierra o el teléfono se apaga durante el registro |
| RNF-08 | Respaldo | Copia completa semanal en Drive; restauración probada en otro teléfono |
| RNF-09 | Usabilidad | Un principiante registra una lectura de fermentación en ≤ 3 toques y ≤ 20 s |
| RNF-10 | Idioma | Español de Ecuador, sin tecnicismos sin explicar |
| RNF-11 | Auditoría | Cambios en registros de BPM, prueba de corte y laboratorio quedan en historial (quién, cuándo, valor anterior) |
| RNF-12 | Mantenibilidad | Umbrales, norma y modelos actualizables sin publicar una nueva versión de la app |
| RNF-13 | Compatibilidad | Android 8.0+; pantallas de 5 a 7 pulgadas; modo claro y oscuro |
| RNF-14 | Costos de operación | Operar con planes gratuitos para 1–3 usuarios y hasta ~20 lotes al año |

---

## 10. Criterios de aceptación (pruebas clave)

| Prueba | Criterio para aprobar |
| --- | --- |
| Modo avión | Crear lote, registrar recepción con 10 fotos analizadas por M1, fermentación con 6 lecturas y volteos; al volver la conexión todo se sincroniza sin duplicados |
| Alertas | Simular temperatura de 38 °C a las 72 h: aparece la alerta RN-04 con la corrección C-01 |
| Prueba de corte | Con 10 tableros contados por un experto, la app queda dentro de ±5 puntos en % de fermentados y ±3 granos en el total |
| Trazabilidad | Escanear el QR de una barra muestra finca, fechas, prueba de corte y resultado de cadmio del lote |
| Respaldo | Instalar en otro teléfono, iniciar sesión y restaurar todos los lotes y fotos |
| Usabilidad | Una persona sin experiencia registra un volteo con foto en menos de 20 s sin ayuda |

---

## 11. Plan de desarrollo por fases

| Fase | Contenido | Duración estimada\* |
| --- | --- | --- |
| 0. Datos y modelos | Tomar fotos con el protocolo, entrenar M1 con datasets abiertos, etiquetar primeros tableros de corte | 3–6 semanas (en paralelo) |
| 1. MVP | Lotes y QR, recepción con M1, apertura, fermentación, secado, prueba de corte manual + cálculo de norma, alertas locales, guías, sincronización y respaldo | 8–12 semanas |
| 2. Calidad | M2 en la prueba de corte, almacenamiento, tostado, refinado, atemperado, reportes PDF, panel | 6–8 semanas |
| 3. Venta | Inventario, costos, ventas, BPM, laboratorio, etiquetas, sensor Bluetooth, M3 y M4 | 6–8 semanas |

\*Estimación para un desarrollador Flutter con experiencia; confirmar con cotizaciones reales.

---

## 12. Riesgos

| Riesgo | Impacto | Mitigación |
| --- | --- | --- |
| Pocas fotos propias para entrenar | Modelos poco precisos | Empezar con conteo manual; la IA se activa cuando cumpla las metas de la sección 8.1 |
| Diferencias de luz y cámara | Errores de clasificación | Caja de luz, tarjeta de color y guía de encuadre |
| Cambios en planes gratuitos de Firebase o Gemini | Costos inesperados | Fotos en Drive, alertas locales, IA en el teléfono; Gemini es opcional |
| Interpretación de la norma INEN 176 | Resultado de grado equivocado | Tabla editable; validar con el texto oficial y un catador certificado |
| Pérdida del teléfono | Pérdida de datos | Sincronización y respaldo semanal |
| Licencias de datasets | Problemas legales al vender | Usar solo datos con licencia comercial y fotos propias |

---

## Anexo A. Archivos del modelo de entrenamiento entregados

`preparar_dataset.py`, `entrenar_clasificador.py`, `entrenar_detector_corte.py`, `calificar_corte.py`, `norma_inen176.json`, `mapeo_mazorca_ejemplo.json`, `requirements.txt` y `README.md` con la guía para Google Colab.
