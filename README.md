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
   Room (SQLite privado) → Dashboard · Reportes · Export · Respaldo
```

## Estado

| Fase | Alcance | Estado |
|---|---|---|
| 0 | Toolchain, esqueleto, APK debug instalable | ✅ |
| 1 | Room + cuentas + categorías + carga manual | ✅ |
| 2 | Captura por notificaciones + diagnóstico | ✅ |
| 3 | Reglas y aprendizaje | ✅ |
| 4 | Transferencias propias, reportes, presupuestos | ✅ |
| 5 | Export CSV/JSON, respaldo cifrado con contraseña, bloqueo al abrir | ✅ |
| 5.1 | Base cifrada en reposo con SQLCipher y llave en el Keystore | ✅ |

## Qué hace hoy (Fases 1 a 5.1)

- **Captura automática**: un `NotificationListenerService` local escucha solo los paquetes de Santander, Falabella (`falabella.com`, la app de la tienda), MercadoPago, Tenpo, Banco de Chile y Google Wallet; el resto se ignora sin guardar nada.
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
- **Reportes** (cuarta pestaña 📊): dona de gastos por categoría con leyenda de porcentajes y el detalle al tocar una categoría (sus movimientos del mes, con acceso al editor), más un gráfico de barras de gastos e ingresos de los últimos 6 meses que sirve para saltar de mes. Arriba, gastos / ingresos / balance / **promedio diario** (en el mes en curso divide por los días corridos). Todo se calcula en Kotlin desde los movimientos del mes, con la misma regla del inicio: no cuentan los movimientos *Por revisar* ni los traspasos, y lo que no tiene categoría aparece como **Sin categoría** para que las partes sumen 100 %.
- **Presupuestos**: en Reportes se le pone un tope mensual a cualquier categoría de gasto (Guardar / Quitar / Cancelar), y la tarjeta muestra *Presupuestado vs gastado* con el total y cada categoría ordenada por avance. El semáforo es **OK** bajo el 80 %, **ámbar** desde el 80 % y **rojo** al llegar al 100 % ("Te quedan $X" / "Te pasaste $X"). El presupuesto vive en `categories.monthlyBudget`, que existe desde la versión 1 de la base: **no hubo migración** (la base sigue en v4).
- **Aviso de presupuesto en Inicio**: cuando alguna categoría del mes alcanza el 80 % del tope aparece la tarjeta *Presupuestos* con la barra y lo que queda, y un atajo a Reportes. El aviso se calcula en vivo (no se guarda estado), así que editar o borrar un movimiento lo corrige al instante, y si no hay presupuestos alcanzados la tarjeta no ocupa espacio.
- **Nuevo/editar movimiento**: monto, comercio, cuenta, categoría (chips filtrados por gasto/ingreso) y fecha con calendario Material 3. El interruptor **Traspaso entre mis cuentas** marca el movimiento como traspaso y cambia los chips a las categorías de traspaso; en ese caso la cuenta se elige dos veces (**origen** y **destino**) y aparece la casilla de la otra pata. Se puede eliminar al editar.
- **Ajustes**: permiso de captura, cuentas (crear, editar, archivar y los números que aparecen en sus avisos), **Datos** (exportar, respaldar y restaurar), **bloqueo al abrir**, categorías y nota de privacidad.
- Datos iniciales: Santander Cta. Cte., Santander Débito, Santander MC Platinum Latam, Falabella Cta. Cte., Falabella CMR, Falabella Débito y MercadoPago + 18 categorías chilenas (Supermercado, Bencina, Delivery, Servicios básicos, Suscripciones, …).
- Cifrado en reposo: la base va cifrada con **SQLCipher** (AES-256) y la llave vive en el Keystore de Android. Ver *Fase 5.1* más abajo.

### Fase 5: los datos salen solo cuando el usuario quiere

- **Datos** (Ajustes → tarjeta *Datos*): cuatro botones y un aviso con el resultado. Todo pasa por el **selector de archivos de Android** (SAF): la app no pide permiso de almacenamiento ni escribe fuera de la carpeta que el usuario elige, y no adquiere ninguna vía para sacar datos sola.
- **Exportar CSV**: una fila por movimiento con fecha ISO (`2026-11-20`, ordena sola), hora, monto entero, `tipo` (Gasto / Ingreso / Traspaso), cuenta, cuenta de destino (en los traspasos), categoría, comercio, estado, origen, notas y texto del aviso. Va con **BOM UTF-8** para que Excel muestre `Súper` y no `SÃºper`, comillas dobles según RFC 4180 cuando el campo trae coma o salto de línea, y un apóstrofo delante de lo que empiece con `=`, `+`, `-` o `@` para que un nombre de comercio no se ejecute como fórmula.
- **Exportar JSON**: `{"app":"Wenagastos","documento":"movimientos","version":1,"generadoEn":…,"resumen":{…},"movimientos":[…]}` con los nombres ya resueltos y los campos vacíos omitidos.
- **Crear respaldo**: archivo `wenagastos-AAAA-MM-DD.wena` con **todo** (cuentas, categorías, reglas aprendidas y movimientos, con los ids originales), cifrado con **AES-256-GCM** y una llave derivada de la contraseña del usuario con **PBKDF2-HMAC-SHA256, 120.000 vueltas** y sal de 16 bytes al azar. Cabecera en claro de 40 bytes (`WENABK1` · versión · iteraciones · sal · IV) para poder derivar la llave; sin la contraseña el resto es ruido. La contraseña se pide dos veces al crear (una errata deja el archivo inservible para siempre), mínimo 8 caracteres, y no se guarda en ninguna parte: `SettingsViewModel` la borra de memoria apenas termina de cifrar.
- **Restaurar**: se elige el `.wena`, se escribe la contraseña y se confirma con un botón rojo, porque **reemplaza todo** lo que hay en el teléfono (la app no guarda copia previa). Antes de borrar nada se valida el archivo entero: si un movimiento apunta a una cuenta que no viene, si hay ids repetidos o si un monto es inválido, no se toca la base. La restauración corre en un solo `withTransaction` con las filas y sus ids originales, así un traspaso sigue apuntando a su otra pata y una regla a su categoría; si algo falla a mitad de camino, Room revierte y la base queda como estaba.
- **Bloqueo al abrir**: interruptor en Ajustes (solo si el teléfono tiene PIN o huella). Al abrir, una pantalla sin datos tapa la app entera; **Desbloquear** muestra el `BiometricPrompt` del sistema (huella, rostro o PIN en un solo diálogo, Android 11+) o la pantalla de bloqueo del sistema en Android 10 y anteriores. Si la app estuvo **más de un minuto** en segundo plano se vuelve a pedir (abrir el selector de archivos no cuenta como salir). Si el teléfono pierde su PIN, la app deja pasar y lo dice: el bloqueo no cifra la base, solo es una cortina para quien tome el teléfono desbloqueado.
- **Sin dependencias nuevas**: JSON, CSV, PBKDF2 y AES-GCM con la biblioteca estándar (`kotlin`, `javax.crypto`), y el diálogo biométrico del sistema en vez de `androidx.biometric`. El formato del respaldo se prueba en la JVM.

### Fase 5.1: la base cifrada

- **SQLCipher** (`net.zetetic:sqlcipher-android`) cifra el archivo de Room con AES-256. Room no cambia: sigue en **versión 4**, el esquema es el mismo y las migraciones son las de siempre; solo cambia el *open helper*, que pasa a ser `SupportOpenHelperFactory` con la llave.
- **Dónde está la llave**: 32 bytes al azar (`SecureRandom`) que **no** se guardan en claro: van envueltos con una clave AES-256-GCM del **Android Keystore** (alias `wenagastos_db_key`, sin pedir huella), y el sobre (IV + cifrado) queda en `SharedPreferences` (`wenagastos.secret`). Quien copie los archivos de la app se lleva el sobre, no la llave: esa no se puede exportar del Keystore. A SQLCipher se le pasa el secreto en hexadecimal (64 caracteres).
- **Migración automática al arrancar**: en `AppContainer`, antes de que Room abra nada, se mira la cabecera del archivo. Si dice `SQLite format 3` todavía es texto plano y se cifra; si no, se sigue de largo. La receta: `PRAGMA wal_checkpoint(FULL)` para consolidar el WAL, `ATTACH DATABASE … KEY …` + `sqlcipher_export('encrypted')` a un archivo temporal (que tiene que **existir** antes del `ATTACH`: SQLCipher abre el adjunto sin `CREATE`), `PRAGMA encrypted.user_version = 4` (el `user_version` no se copia solo y Room lo necesita) y **verificación antes de reemplazar**: mismas filas en las cinco tablas, misma versión y cabecera ya cifrada. Recién entonces se renombra el original a `wenagastos.db.old`, se pone el nuevo en su lugar y, si el nuevo no abre, se restaura el viejo. `sqlcipher_export` copia también `room_master_table`, sin eso Room rechazaría la base.
- **Si la base no se puede abrir** (Keystore perdido, archivo dañado) la app no se cae en un ciclo de errores: `MainActivity` muestra una pantalla que explica el problema y ofrece **Empezar de cero** (borra base y llave, reinicia) para después restaurar un `.wena`. La captura por notificaciones se detiene sola mientras la base no esté disponible.
- **Costo**: el APK crece ~10 MB (SQLCipher trae su SQLite compilado para `arm64-v8a`, `armeabi-v7a`, `x86` y `x86_64`) y la primera apertura paga la derivación de llave de SQLCipher (unos 200–400 ms, en el hilo de fondo).

## Stack

- Kotlin 2.2.21 · Jetpack Compose (BOM 2025.06.01) · Material 3
- Room 2.8.5 (KSP) · `net.zetetic:sqlcipher-android` 4.17.0 · Navigation Compose 2.9.8 · `java.time` (zona `America/Santiago`)
  - Las dos versiones van juntas a propósito: Room 2.8.5 arrastra `androidx.sqlite` 2.6.2, que es la que pide SQLCipher 4.17.0. Las 4.18+ exigen `androidx.sqlite` 2.7.0, que ningún Room estable usa todavía.
- AGP 8.13.2 · Gradle 8.14.8 · `compileSdk 36` / `minSdk 26` / `targetSdk 36`
- JDK 21 (Homebrew `openjdk@21`), Android SDK en `~/Library/Android/sdk`
- Paquete: `cl.wenasea.wenagastos`
- DI manual (`AppContainer` + `CompositionLocal`), sin Hilt

## Compilar e instalar

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21

./gradlew :app:assembleDebug          # genera app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug           # instala en el dispositivo conectado por adb
./gradlew :app:testDebugUnitTest      # 128 pruebas: parsers, montos, categorías, traspasos, reglas, motor, reportes, export/respaldo y cabecera/llave de la base
./gradlew :app:connectedDebugAndroidTest  # 3 pruebas instrumentadas: migración real a SQLCipher (necesita emulador o teléfono conectado)
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

### Probar export y respaldo

En el emulador (o en el teléfono) se hace a mano, en **Ajustes → Datos**:

1. **Exportar CSV / JSON** y guardar en *Descargas*; con `adb` se revisa el contenido:

   ```bash
   adb shell ls -l /sdcard/Download/
   adb shell cat /sdcard/Download/wenagastos-AAAA-MM-DD.csv | head
   ```

2. **Crear respaldo** con una contraseña de 8+ caracteres y guardarlo; el archivo debe medir más que el JSON (lleva cabecera y sello de GCM) y no debe contener texto legible:

   ```bash
   adb shell head -c 40 /sdcard/Download/wenagastos-AAAA-MM-DD.wena | xxd | head -2   # WENABK1 + versión + iteraciones
   adb shell grep -c Wenagastos /sdcard/Download/wenagastos-AAAA-MM-DD.wena            # 0
   ```

3. **Restaurar** primero con una contraseña equivocada (tiene que aparecer *La contraseña no es la de este respaldo* y la base quedar intacta) y después con la correcta. Con la base cifrada ya no se puede mirar por dentro con `sqlite3`, así que el conteo se compara en la app (Movimientos del mes y Ajustes → Cuentas) o con el JSON exportado antes del respaldo.

### Probar el cifrado

La primera vez que se abre la versión 5.1 con una base de texto plano, la migración corre sola. En el `logcat` queda una línea:

```bash
adb logcat -s WenagastosDb   # I WenagastosDb: Base cifrada: 29 filas, versión 4
```

Y el archivo deja de ser un SQLite normal:

```bash
# Cabecera: ya no dice "SQLite format 3", son 16 bytes de sal al azar.
adb exec-out run-as cl.wenasea.wenagastos cat databases/wenagastos.db | head -c 16 | xxd
# En la carpeta no quedan .new ni .old (la migración los borra al terminar).
adb shell run-as cl.wenasea.wenagastos ls databases/
# Y con sqlite3 de escritorio no abre:
adb exec-out run-as cl.wenasea.wenagastos cat databases/wenagastos.db > /tmp/wena.db
sqlite3 /tmp/wena.db 'select count(*) from transactions;'   # Error: file is not a database
```

Antes de migrar un teléfono con datos reales conviene guardar la base en claro y un `.wena`:

```bash
adb exec-out run-as cl.wenasea.wenagastos cat databases/wenagastos.db > /tmp/antes-de-cifrar.db
```

## Privacidad

- Sin permiso `INTERNET`: la app no puede enviar datos a ningún lado.
- `allowBackup="false"`: sin respaldos automáticos a la nube.
- Los datos viven en el almacenamiento privado de la app.
- La base va **cifrada con SQLCipher** y la llave (32 bytes al azar) está envuelta con una clave del **Android Keystore** que no se puede exportar: copiar `wenagastos.db` a otro lado no sirve de nada. La llave no se puede recuperar; si se pierde, solo queda restaurar un respaldo `.wena`.

## Convenciones

- Paquetes: `cl.wenasea.wenagastos.<feature>` (`ui`, `data`, `domain`, `capture`, `importexport`).
- Montos en `Long` en pesos chilenos, sin decimales (`TransactionEntity.amount`).
- Fechas: `epoch millis` en zona `America/Santiago`; se formatean con `java.time`.
- Esquemas Room exportados en `app/schemas/` (necesarios para las migraciones futuras). La versión 3 agrega `transactions.transferAccountId` + su índice con una `AutoMigration` 2 → 3; la versión 4 agrega `merchant_rules` (índice único `merchantKey + direction`, FK a `categories` con `SET_NULL`) con una `AutoMigration` 3 → 4. La **versión 4 sigue siendo la última**: los presupuestos usan `categories.monthlyBudget`, que existe desde la versión 1, y ni la Fase 5 (export, respaldo y bloqueo) ni la 5.1 (cifrado) tocan el esquema: SQLCipher cambia el archivo, no las tablas. El respaldo anota en `schema` la versión con la que se escribió.
- La normalización de texto (`TextNorm`) vive en `domain`, porque la usan tanto `capture` como los repositorios.

## Problemas conocidos

- **El APK pesa ~10 MB más**: SQLCipher trae su SQLite compilado para `arm64-v8a`, `armeabi-v7a`, `x86` y `x86_64`. Se puede recortar con ABI splits el día que importe; hoy es un solo teléfono.
- **Si se pierde la llave** (se borran los datos de la app, se cambia de teléfono sin respaldo, o el Keystore se resetea), la base cifrada no se puede abrir. La app lo detecta y ofrece *Empezar de cero* para después restaurar un `.wena`.
- **Avisos que nunca se registran**: la captura solo ve la notificación en el instante en que el sistema la publica. Si el teléfono estaba **apagado**, el permiso de acceso a notificaciones estaba revocado o la app estaba detenida, ese movimiento no existe para la app y **no hay forma de recuperarlo** (no hay `INTERNET`, no se leen SMS y no hay `getActiveNotifications`, `WorkManager` ni `BOOT_COMPLETED` que lo busquen después). Si el teléfono solo estaba sin señal, la push del banco llega al reconectar y sí se registra; un **SMS** del banco, en cambio, no se lee. Lo perdido se anota a mano con el botón **+**.
- **Un banco sin app instalada no tiene por dónde avisar**: la captura depende de que exista un canal, y el canal puede no existir. Pasó en el A34 con un retiro de la tarjeta Falabella: **no está instalada la app de Banco Falabella** (`com.falabella.falabellaApp` es `falabella.com`, la tienda: avisa compras con CMR, no avances en efectivo, y además estaba deshabilitada), **el banco no manda SMS de movimientos** (en 2.620 SMS de la bandeja solo había 7 del banco y todos eran códigos de seguridad) y Google Wallet no vio nada porque un retiro en cajero no es un pago NFC. Antes de culpar al teléfono apagado o al parser, revisar los canales: `adb shell pm list packages | grep -i <banco>` (¿está?), `adb shell pm list packages -d` (¿deshabilitada?), `adb shell appops get <paquete> POST_NOTIFICATION` (¿puede avisar?) y `adb shell content query --uri content://sms/inbox` (¿hay SMS del banco?).
- **Una app deshabilitada se ve igual que un banco sin avisos**: si la app está **deshabilitada** o **sin permiso de notificaciones** — lo típico en apps preinstaladas que nunca se abrieron — el sistema descarta el aviso *antes* de que el listener lo vea, y en **Diagnóstico** no queda ni rastro. Se comprueba con `adb shell pm list packages -d` y `adb shell appops get <paquete> POST_NOTIFICATION`; se arregla abriendo la app una vez y aceptando sus notificaciones (`pm install-existing --user 0 <paquete>` y `pm grant <paquete> android.permission.POST_NOTIFICATIONS` también sirven por adb; en el A34 Knox rechaza `pm enable`, pero `pm install-existing` sí pasó). La app tiene que estar **con sesión iniciada** para que el banco le mande la push.
- **Gradle no ve los cambios de archivos** (el daemon cachea el sistema de archivos y responde `UP-TO-DATE` aunque el código cambió). Solución:

  ```bash
  ./gradlew --stop && ./gradlew :app:assembleDebug --no-daemon
  ```

## Siguiente

- **Calibrar con avisos reales** (en curso): el motor ya está cerrado y probado con avisos sintéticos, pero falta ver los textos reales del A34. Para cada banco (Santander, Falabella, MercadoPago, Tenpo, Banco de Chile y Google Wallet) se compara el texto crudo con lo que se parseó en **Diagnóstico** y se ajustan `BankSource.packages` y los patrones de `NotifParser` / `AmountExtractor` / `MerchantExtractor` / `CardDetector`.
  Riesgo conocido: las compras en cuotas CMR pueden entregar el monto de la cuota en vez del total.
- **Fase 5.1 — SQLCipher**: ✅ la base va cifrada en reposo, la llave vive en el Keystore y la migración de un archivo en texto plano ocurre sola al arrancar (sin cambiar la versión de esquema). Lo pendiente es la verificación en el A34 con datos reales.
- **Fase 5.2 — captura por SMS** (idea para cuando aparezcan avisos perdidos): leer los SMS del banco con `READ_SMS`. Un SMS recibido con el teléfono apagado se entrega al encender, así que sería la única fuente capaz de recuperar un movimiento que ocurrió sin el teléfono prendido. Pide permiso nuevo y un parser por banco, porque el texto del SMS no se parece al de la notificación. Antes de construirla hay que confirmar que el banco **manda** SMS de movimientos: Banco Falabella no los manda (solo códigos de seguridad), así que ahí no habría nada que leer.
- **Estado de los bancos en Diagnóstico**: por cada paquete cubierto, mostrar si está habilitado y si puede publicar notificaciones (y, mejor, si está instalado), con un botón para abrir sus ajustes. Es exactamente la comprobación que faltó cuando el retiro con Falabella no apareció: habría mostrado de inmediato que **la app de Banco Falabella ni siquiera está instalada** —esa era la causa, no el teléfono apagado— y que la de la tienda estaba deshabilitada.
- **Recordatorios de respaldo**: un aviso (sin permisos nuevos) cuando pasan 30 días desde el último respaldo, usando la fecha que se guarde en `SharedPreferences`.
