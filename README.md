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
   Motor de reglas de comercio → categoría sugerida (Fase 3: reglas propias)
        │
        ▼
   Room (SQLite cifrado) → Dashboard · Reportes · Export
```

## Estado

| Fase | Alcance | Estado |
|---|---|---|
| 0 | Toolchain, esqueleto, APK debug instalable | ✅ |
| 1 | Room + cuentas + categorías + carga manual | ✅ |
| 2 | Captura por notificaciones + diagnóstico | ✅ |
| 3 | Reglas y aprendizaje | ⬜ |
| 4 | Transferencias propias, reportes, presupuestos | ⬜ |
| 5 | Export, backup cifrado (SQLCipher), biometría | ⬜ |

## Qué hace hoy (Fases 1 y 2)

- **Captura automática**: un `NotificationListenerService` local escucha solo los paquetes de Santander, Falabella, MercadoPago, Tenpo, Banco de Chile y Google Wallet; el resto se ignora sin guardar nada.
- **Un parser por banco**: obtiene monto en CLP, comercio o destinatario, últimos 4 dígitos, si la tarjeta es de crédito o débito, y si la plata entra o sale. Los avisos de saldo, claves, promociones y resúmenes vacíos se descartan con el motivo anotado.
- **Cuenta automática**: los 4 dígitos que trae el aviso (los de la tarjeta en una compra, los de la cuenta en una transferencia) se cruzan por número exacto con `accounts.last4`, que se configura tocando la cuenta en Ajustes. Una cuenta puede tener **varios números** separados por coma (ej. `"5678,1234"`: su número de cuenta y el de su tarjeta de débito), así compras y transferencias caen en la misma cuenta.
- **Confianza**: parseo completo + cuenta conocida → **Confirmado** (suma en los totales). Si falta el comercio, la tarjeta o la cuenta → **Por revisar**: no suma en los totales, se cuenta en la tarjeta *Por revisar* del inicio y el motivo queda escrito en las notas del movimiento (se ve al abrirlo y se borra al confirmarlo).
- **Duplicados**: hash SHA-256 (paquete + monto + comercio + ventana de 5 min) y búsqueda por monto y sentido en ±5 min. Si Google Wallet avisa primero, el aviso del banco **completa** ese movimiento (cuenta, comercio, estado, hash) en vez de duplicarlo; si el banco llega primero, Wallet se descarta y no lo pisa.
- **Diagnóstico** (Ajustes → "Ver últimos avisos"): texto crudo, resultado (registrado / por revisar / duplicado / completado / otra app / no es un movimiento) y detalle. La bitácora se recorta sola a las últimas 200 filas.
- **Aviso en Inicio**: si el permiso de notificaciones está apagado aparece la tarjeta *La captura está apagada* con botón **Activar**; se va sola al volver de los Ajustes del sistema.
- **Inicio**: gasto total del mes, ingresos, balance, pendientes por revisar, top 5 categorías con barra de proporción y últimos movimientos. Navegación por mes (‹ ›).
- **Movimientos**: lista del mes agrupada por día (formato chileno `vie 20 nov`), con total gastado por día.
- **Nuevo/editar movimiento**: monto, comercio, cuenta, categoría (chips filtrados por gasto/ingreso) y fecha con calendario Material 3. Se puede eliminar al editar.
- **Ajustes**: permiso de captura, cuentas (con los números que aparecen en sus avisos), categorías y nota de privacidad.
- Datos iniciales: Santander Cta. Cte., Santander Débito, Santander MC Platinum Latam, Falabella Cta. Cte., Falabella CMR, Falabella Débito y MercadoPago + 18 categorías chilenas (Supermercado, Bencina, Delivery, Servicios básicos, Suscripciones, …).
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
./gradlew :app:testDebugUnitTest      # 42 pruebas: parsers, montos, categorías y motor de captura
adb shell am start -n cl.wenasea.wenagastos/.MainActivity
```

Requisitos en el teléfono: Opciones de desarrollador + Depuración USB activadas.

### Emulador

```bash
export ANDROID_SDK_ROOT=$HOME/Library/Android/sdk
$ANDROID_SDK_ROOT/emulator/emulator -avd wena36 &
```

AVD `wena36`: Pixel 7, Android 16 (API 36), `google_apis`, arm64-v8a (sin Play Store).

### Probar la captura sin gastar plata

```bash
adb shell 'cmd notification post -S bigtext -t Santander tag_compra "Compraste \$7.890 en FARMACIA AHUMADA con tu tarjeta de débito terminada en 4321"'
```

El aviso lo publica `com.android.shell`, así que el resultado depende del texto:

- sin los 4 dígitos en el aviso (o con un número que no está asociado a ninguna cuenta) el movimiento queda **Por revisar**, con la nota del número que hay que asignar (no suma en los totales y el motivo se ve al abrir el movimiento);
- con `terminada en 4321` y ese número asociado a una cuenta entra como **Confirmado** en ella y mueve los totales.
- si la cuenta tiene varios números (`"1483,4321"`: el de la cuenta y el de su tarjeta de débito) un aviso de compra con `4321` y una transferencia con `1483` caen en la misma cuenta.

Ojo con el `$`: hay que escaparlo dentro de la comilla que recibe el teléfono, si no el shell local lo
come y el aviso llega cortado (`text=Compraste`), lo que termina como *no es un movimiento* en el
diagnóstico. Los caminos difíciles (duplicados, traspaso Wallet → banco) están cubiertos por los tests.

## Privacidad

- Sin permiso `INTERNET`: la app no puede enviar datos a ningún lado.
- `allowBackup="false"`: sin respaldos automáticos a la nube.
- Los datos viven en el almacenamiento privado de la app.

## Convenciones

- Paquetes: `cl.wenasea.wenagastos.<feature>` (`ui`, `data`, `domain`, `capture`, `importexport`).
- Montos en `Long` en pesos chilenos, sin decimales (`TransactionEntity.amount`).
- Fechas: `epoch millis` en zona `America/Santiago`; se formatean con `java.time`.
- Esquemas Room exportados en `app/schemas/` (necesarios para las migraciones futuras).

## Problemas conocidos

- **Gradle no ve los cambios de archivos** (el daemon cachea el sistema de archivos y responde `UP-TO-DATE` aunque el código cambió). Solución:

  ```bash
  ./gradlew --stop && ./gradlew :app:assembleDebug --no-daemon
  ```

## Siguiente

- **Calibrar con avisos reales** (en curso): el motor ya está cerrado y probado con avisos sintéticos, pero falta ver los textos reales del A34. Para cada banco (Santander, Falabella, MercadoPago, Tenpo, Banco de Chile y Google Wallet) se compara el texto crudo con lo que se parseó en **Diagnóstico** y se ajustan `BankSource.packages` y los patrones de `NotifParser` / `AmountExtractor` / `MerchantExtractor` / `CardDetector`.
  Riesgo conocido: las compras en cuotas CMR pueden entregar el monto de la cuota en vez del total.
- **Fase 3 — reglas y aprendizaje**: tabla `merchant_rules` (clave del comercio única, categoría, usos, fecha), resolución regla del usuario → `MerchantCategories`, botón "recordar para este comercio" en el editor y pantalla "Reglas por comercio" en Ajustes.
