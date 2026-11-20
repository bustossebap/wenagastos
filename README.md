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
   Motor de reglas de comercio → regla aprendida del usuario, si no la semilla del catálogo
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
| 3 | Reglas y aprendizaje | ✅ |
| 4 | Transferencias propias, reportes, presupuestos | 🚧 traspasos con origen y destino, alta y archivo de cuentas |
| 5 | Export, backup cifrado (SQLCipher), biometría | ⬜ |

## Qué hace hoy (Fases 1, 2 y 3)

- **Captura automática**: un `NotificationListenerService` local escucha solo los paquetes de Santander, Falabella, MercadoPago, Tenpo, Banco de Chile y Google Wallet; el resto se ignora sin guardar nada.
- **Un parser por banco**: obtiene monto en CLP, comercio o destinatario, últimos 4 dígitos, si la tarjeta es de crédito o débito, y si la plata entra o sale. Los avisos de saldo, claves, promociones y resúmenes vacíos se descartan con el motivo anotado.
- **Cuenta automática**: los 4 dígitos que trae el aviso (los de la tarjeta en una compra, los **últimos 4 del número de cuenta** en una transferencia: `cuenta 000095441483` → `1483`) se cruzan por número exacto con `accounts.last4`, que se configura tocando la cuenta en Ajustes. Una cuenta puede tener **varios números** separados por coma (ej. `"5678,1234"`: su número de cuenta y el de su tarjeta de débito), así compras y transferencias caen en la misma cuenta.
- **Confianza**: parseo completo + cuenta conocida → **Confirmado** (suma en los totales). Si falta el comercio, la tarjeta o la cuenta → **Por revisar**: no suma en los totales, se cuenta en la tarjeta *Por revisar* del inicio y el motivo queda escrito en las notas del movimiento (se ve al abrirlo y se borra al confirmarlo).
- **Traspasos entre cuentas propias**: "Transferencia hacia tu cuenta …" entra como ingreso y "… desde tu cuenta …" como gasto, aunque el aviso no traiga verbo. Se marcan con `isTransfer` y categoría **Entre cuentas**, así **no suman** en gastos, ingresos, balance ni en el gráfico por categoría: la plata no se gastó, solo cambió de cuenta. Solo cuentan como traspaso si el aviso nombra la cuenta propia y **no** trae destinatario; `Transferiste $50.000 a MARIA SOTO` o `Recibiste una transferencia de JUAN PEREZ` siguen siendo gasto e ingreso normales.
- **Origen y destino del traspaso**: al marcar *Traspaso entre mis cuentas* el editor pide **origen** y **destino** en dos filas de chips (y renombra los chips de sentido a *Sale de mi cuenta* / *Entra a mi cuenta*). La otra punta se guarda en `transferAccountId`, así la lista se lee **Entre cuentas · Santander Cta. Cte. → Falabella Cta. Cte.** desde cualquiera de las dos patas: la que sale y la que entra muestran el mismo par origen → destino. Si el banco avisó una sola pata, la casilla **Registrar la otra pata** (marcada por defecto cuando falta) anota el movimiento espejo en la cuenta de destino, con el sentido contrario, el mismo monto y la misma fecha, y las dos comparten `transferGroupId`. Si la otra pata ya existe (mismo monto, otra cuenta, ±6 h) se enlazan y no se duplica; un movimiento del mismo monto que no sea traspaso no se toca.
- **Reglas por comercio**: cuando la categoría sugerida no es la que quieres, el editor ofrece **Recordar para este comercio** (sin marcar por defecto). La regla se guarda en `merchant_rules` con la clave normalizada del comercio (`TextNorm.key`) **y el sentido**, así el mismo nombre puede ser gasto e ingreso con categorías distintas; volver a enseñarla actualiza la fila y suma un uso, nunca duplica. El motor resuelve primero el traspaso (siempre *Entre cuentas*), después la regla del usuario y al final la semilla de `MerchantCategories`; si la categoría de la regla se borró o archivó, la regla se ignora y vuelve la semilla. En **Ajustes → Reglas por comercio** se ven ordenadas por uso, se les cambia la categoría o se olvidan.
- **Cuentas**: en Ajustes se pueden **crear**, editar y **archivar**. El alta pide nombre, institución y tipo; el color sale de la institución. Los números de 4 dígitos no se pueden repetir entre cuentas activas (el cruce de los avisos es por número exacto). Una cuenta con movimientos no se borra: se **archiva** (deja de ofrecerse al anotar y sus avisos nuevos quedan en Por revisar), porque la FK `transactions.accountId` es `CASCADE` y borrarla se llevaría el historial; solo se borra de verdad cuando no tiene movimientos.
- **Duplicados**: hash SHA-256 (paquete + monto + **sentido** + **número de cuenta** + comercio + ventana de 5 min) y búsqueda por monto y sentido en ±5 min. El sentido y la cuenta entran al hash porque un traspaso del mismo monto en el mismo minuto sale de una cuenta y entra a otra: son dos movimientos, no un duplicado. La captura corre serializada (un `Mutex` en el motor), así dos avisos simultáneos no pasan los dos la revisión antes de guardarse. Si Google Wallet avisa primero, el aviso del banco **completa** ese movimiento (cuenta, comercio, estado, hash) en vez de duplicarlo; si el banco llega primero, Wallet se descarta y no lo pisa.
- **Diagnóstico** (Ajustes → "Ver últimos avisos"): texto crudo, resultado (registrado / por revisar / duplicado / completado / otra app / no es un movimiento) y detalle. La bitácora se recorta sola a las últimas 200 filas.
- **Aviso en Inicio**: si el permiso de notificaciones está apagado aparece la tarjeta *La captura está apagada* con botón **Activar**; se va sola al volver de los Ajustes del sistema.
- **Inicio**: gasto total del mes, ingresos, balance, pendientes por revisar, top 5 categorías con barra de proporción y últimos movimientos. Navegación por mes (‹ ›).
- **Movimientos**: lista del mes agrupada por día (formato chileno `vie 20 nov`), con total gastado por día.
- **Nuevo/editar movimiento**: monto, comercio, cuenta, categoría (chips filtrados por gasto/ingreso) y fecha con calendario Material 3. El interruptor **Traspaso entre mis cuentas** marca el movimiento como traspaso y cambia los chips a las categorías de traspaso; en ese caso la cuenta se elige dos veces (**origen** y **destino**) y aparece la casilla de la otra pata. Se puede eliminar al editar.
- **Ajustes**: permiso de captura, cuentas (crear, editar, archivar y los números que aparecen en sus avisos), categorías y nota de privacidad.
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
./gradlew :app:testDebugUnitTest      # 74 pruebas: parsers, montos, categorías, traspasos, reglas y motor
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
- Esquemas Room exportados en `app/schemas/` (necesarios para las migraciones futuras). La versión 3 agrega `transactions.transferAccountId` + su índice con una `AutoMigration` 2 → 3; la versión 4 agrega `merchant_rules` (índice único `merchantKey + direction`, FK a `categories` con `SET_NULL`) con una `AutoMigration` 3 → 4.
- La normalización de texto (`TextNorm`) vive en `domain`, porque la usan tanto `capture` como los repositorios.

## Problemas conocidos

- **Gradle no ve los cambios de archivos** (el daemon cachea el sistema de archivos y responde `UP-TO-DATE` aunque el código cambió). Solución:

  ```bash
  ./gradlew --stop && ./gradlew :app:assembleDebug --no-daemon
  ```

## Siguiente

- **Calibrar con avisos reales** (en curso): el motor ya está cerrado y probado con avisos sintéticos, pero falta ver los textos reales del A34. Para cada banco (Santander, Falabella, MercadoPago, Tenpo, Banco de Chile y Google Wallet) se compara el texto crudo con lo que se parseó en **Diagnóstico** y se ajustan `BankSource.packages` y los patrones de `NotifParser` / `AmountExtractor` / `MerchantExtractor` / `CardDetector`.
  Riesgo conocido: las compras en cuotas CMR pueden entregar el monto de la cuota en vez del total.
- **Fase 4 — reportes y presupuestos**: gráficos por categoría y mes, presupuesto mensual por categoría con aviso al acercarse al límite. Los traspasos y las cuentas ya están cubiertos.
- **Fase 5 — export y respaldo**: exportar CSV/JSON a un archivo que el usuario elige, backup cifrado con SQLCipher y biometría para abrir la app.
