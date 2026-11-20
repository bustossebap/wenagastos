# Wenagastos

App Android **privada** (uso personal) para llevar el control de gastos, inspirada en el flujo de Kuanto pero con una diferencia de fondo: **todo ocurre en el teléfono**. Sin backend, sin cuentas en la nube y sin permiso de `INTERNET` en el manifest.

## Cómo funciona

```
Notificación push del banco (Santander, Falabella, MercadoPago)
        │
        ▼  NotificationListenerService (local, solo paquetes permitidos)
   Parser por institución → monto, comercio, tarjeta, tipo de movimiento
        │
        ▼  deduplicación (hash + ventana temporal)
   Motor de reglas + aprendizaje de comercios → categoría sugerida
        │
        ▼
   Room (SQLite cifrado) → Dashboard · Reportes · Export
```

## Estado

| Fase | Alcance | Estado |
|---|---|---|
| 0 | Toolchain, esqueleto, APK debug instalable | ✅ |
| 1 | Room + cuentas + categorías + carga manual | ✅ |
| 2 | Captura por notificaciones + diagnóstico | ⬜ |
| 3 | Reglas y aprendizaje | ⬜ |
| 4 | Transferencias propias, reportes, presupuestos | ⬜ |
| 5 | Export, backup cifrado (SQLCipher), biometría | ⬜ |

## Qué hace hoy (Fase 1)

- **Inicio**: gasto total del mes, ingresos, balance, pendientes por revisar, top 5 categorías con barra de proporción y últimos movimientos. Navegación por mes (‹ ›).
- **Movimientos**: lista del mes agrupada por día (formato chileno `vie 20 nov`), con total gastado por día.
- **Nuevo/editar movimiento**: monto, comercio, cuenta, categoría (chips filtrados por gasto/ingreso) y fecha con calendario Material 3. Se puede eliminar al editar.
- **Ajustes**: cuentas y categorías sembradas, nota de privacidad.
- Datos iniciales: Santander Cta. Cte., Santander Débito, Falabella CMR, Falabella Débito, MercadoPago Ahorro + 18 categorías chilenas (Supermercado, Bencina, Delivery, Servicios básicos, Suscripciones, …).
- Cifrado en reposo: por ahora lo entrega el File-Based Encryption de Android (almacenamiento privado de la app). SQLCipher se suma en la Fase 5, previo a los respaldos.

## Stack

- Kotlin 2.2.21 · Jetpack Compose (BOM 2025.06.01) · Material 3
- Room 2.7.2 (KSP) · Navigation Compose 2.9.8 · `java.time` (zona `America/Santiago`)
- AGP 8.13.2 · Gradle 8.14.8 · `compileSdk 36` / `minSdk 26` / `targetSdk 36`
- JDK 21 (Homebrew `openjdk@21`), Android SDK en `~/Library/Android/sdk`
- Paquete: `cl.wenasea.wenagastos`
- DI manual (`AppContainer` + `CompositionLocal`), sin Hilt

## Compilar e instalar

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21

./gradlew :app:assembleDebug          # genera app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug           # instala en el dispositivo conectado por adb
adb shell am start -n cl.wenasea.wenagastos/.MainActivity
```

Requisitos en el teléfono: Opciones de desarrollador + Depuración USB activadas.

### Emulador

```bash
export ANDROID_SDK_ROOT=$HOME/Library/Android/sdk
$ANDROID_SDK_ROOT/emulator/emulator -avd wena36 &
```

AVD `wena36`: Pixel 7, Android 16 (API 36), `google_apis`, arm64-v8a (sin Play Store).

## Privacidad

- Sin permiso `INTERNET`: la app no puede enviar datos a ningún lado.
- `allowBackup="false"`: sin respaldos automáticos a la nube.
- Los datos viven en el almacenamiento privado de la app.

## Convenciones

- Paquetes: `cl.wenasea.wenagastos.<feature>` (`ui`, `data`, `domain`, `capture`, `importexport`).
- Montos en `Long` en pesos chilenos, sin decimales (`TransactionEntity.amount`).
- Fechas: `epoch millis` en zona `America/Santiago`; se formatean con `java.time`.
- Esquemas Room exportados en `app/schemas/` (necesarios para las migraciones futuras).
