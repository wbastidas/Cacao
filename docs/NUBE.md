# Activar la nube (Firebase + Google Drive)

La app **funciona completa sin esto**. Esta guía es para cuando quieras respaldo
automático y sincronización entre teléfonos (RF-SYN-03, RF-SYN-07).

## Qué está ya hecho en el código

| Pieza | Estado |
|---|---|
| UUID, `modificadoEn`, `estadoSync`, borrado lógico en todas las tablas | ✅ hecho |
| Cola de salida (`cola_sync`) con reintentos y retroceso exponencial | ✅ hecho |
| Opción "subir fotos solo con WiFi" | ✅ hecho |
| Indicador "Todo sincronizado / N pendientes / Sin conexión" | ✅ hecho |
| Interfaz `SincronizadorRemoto` | ✅ definida |
| Implementación Firestore + Drive | ⛔ pendiente (esta guía) |

Hoy se usa `SincronizadorLocalSimulado`, que vacía la cola marcando todo como
sincronizado. Sirve para desarrollo y para las pruebas automáticas.

## Pasos

### 1. Crear el proyecto Firebase

1. Entra a <https://console.firebase.google.com> y crea el proyecto `cacaotrace`.
2. Añade una app **Android** con el `applicationId` que usa el proyecto
   (`ec.cacaotrace.app`, definido en `app/android/app/build.gradle.kts`).
3. Descarga `google-services.json` y colócalo en `app/android/app/`.
   **Ese archivo está en `.gitignore` a propósito: no lo subas al repositorio.**
4. Activa **Authentication → Google** y **Cloud Firestore** (modo producción).

### 2. Reglas de Firestore (RNF-05)

Cada usuario solo puede ver sus datos y los lotes que compartió:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /usuarios/{uid}/{documento=**} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
    match /compartidos/{loteId} {
      allow read: if request.auth != null
                  && request.auth.token.email in resource.data.invitados;
      allow write: if request.auth != null
                   && request.auth.uid == resource.data.propietario;
    }
  }
}
```

### 3. Permisos de Google Drive

Usa el alcance **`https://www.googleapis.com/auth/drive.file`** y ningún otro: con él la
app solo ve los archivos que ella misma creó, nunca el resto del Drive del usuario
(RNF-05). La estructura de carpetas que la app espera está en el §3.2 del ERS.

### 4. Implementar el sincronizador

Crea `app/lib/datos/sync/sincronizador_firebase.dart`:

```dart
class SincronizadorFirebase implements SincronizadorRemoto {
  @override
  Future<ResultadoSync> enviarRegistro(OperacionSync op) async { ... }

  @override
  Future<ResultadoSync> subirFoto(Foto foto, {required bool soloWifi}) async { ... }

  @override
  Future<void> respaldoCompleto() async { ... }   // ZIP semanal a Drive/Respaldos/
}
```

y regístralo en `app/lib/datos/sync/proveedor_sincronizador.dart` en lugar de
`SincronizadorLocalSimulado`. **No hay que tocar la UI ni la base de datos.**

### 5. Remote Config (RNF-12)

Publica en Firebase Remote Config dos claves:

| Clave | Contenido |
|---|---|
| `norma_inen176` | el JSON de `entrenamiento/norma_inen176.json` |
| `umbrales` | JSON con los valores de la tabla 5.1 del ERS |

La app ya guarda una copia en caché y usa la última copia válida si no hay internet.

## Costos

Este diseño está pensado para operar dentro del **plan gratuito (Spark)** con 1–3
usuarios y ~20 lotes al año (RNF-14): las fotos van al Drive del usuario en lugar de
Firebase Storage, y las alertas corren en el teléfono en lugar de Cloud Functions —
ambos servicios exigen el plan de pago Blaze en proyectos nuevos. Verifica las
condiciones vigentes de Firebase antes de empezar, porque cambian.
