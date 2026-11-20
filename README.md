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
| 1 | Room + cuentas + categorías + carga manual | ⬜ |
| 2 | Captura por notificaciones + diagnóstico | ⬜ |
| 3 | Reglas y aprendizaje | ⬜ |
| 4 | Transferencias propias, reportes, presupuestos | ⬜ |
| 5 | Export, backup cifrado, biometría | ⬜ |

## Stack

- Kotlin 2.2.21 · Jetpack Compose (BOM 2025.06.01) · Material 3
- AGP 8.13.2 · Gradle 8.14.8 · `compileSdk 36` / `minSdk 26` / `targetSdk 36`
- JDK 21 (Homebrew `openjdk@21`), Android SDK en `~/Library/Android/sdk`
- Paquete: `cl.wenasea.wenagastos`

## Compilar e instalar

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21

./gradlew :app:assembleDebug          # genera app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug           # instala en el dispositivo conectado por adb
adb shell am start -n cl.wenasea.wenagastos/.MainActivity
```

Requisitos en el teléfono: Opciones de desarrollador + Depuración USB activadas.

## Privacidad

- Sin permiso `INTERNET`: la app no puede enviar datos a ningún lado.
- `allowBackup="false"`: sin respaldos automáticos a la nube.
- Los datos viven en el almacenamiento privado de la app.

## Convenciones

- Paquetes: `cl.wenasea.wenagastos.<feature>` (`ui`, `data`, `capture`, `domain`, `importexport`).
- Montos en `Long` (`amountMinor`); CLP se guarda sin decimales.
- Fechas con `kotlinx-datetime`, zona `America/Santiago`.
