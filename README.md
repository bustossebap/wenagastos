# Wenagastos

App Android **privada** (uso personal) para llevar el control de gastos, inspirada en el flujo de Kuanto pero con una diferencia de fondo: **todo ocurre en el teléfono**. Sin backend, sin cuentas en la nube y sin permiso de `INTERNET` en el manifest.

## Cómo funciona

```
Notificación push del banco o correo en Gmail (Santander, Banco Falabella, MercadoPago, MACH)
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
| 2.1 | El diagnóstico dice si cada app de banco está instalada, habilitada y con avisos | ✅ |
| 3 | Reglas y aprendizaje | ✅ |
| 4 | Transferencias propias, reportes, presupuestos | ✅ |
| 5 | Export CSV/JSON, respaldo cifrado con contraseña, bloqueo al abrir | ✅ |
| 5.1 | Base cifrada en reposo con SQLCipher y llave en el Keystore | ✅ |
| 5.3 | Recordatorio de respaldo en Inicio cuando pasan 30 días | ✅ |
| 6 | Una cuenta por banco: la tarjeta de débito es un número de la cuenta (base v5) | ✅ |
| 6.2 | Transferencia recibida sin nombre: el correo de MACH completa el push del banco receptor | ✅ |
| 6.3 | La contraseña del respaldo deja de ser un PIN: mínimo 12 caracteres y no todos números | ✅ |
| 7 | Saldo por cuenta y por banco: último corte del banco + movimientos posteriores (base v6) | ✅ |
| 7.1 | Cupo y ciclo de la tarjeta de crédito: cupo, día de corte y de pago, disponible y compras del ciclo (base v7) | ✅ |
| 8 | Importar la cartola del banco en CSV: plantilla por institución, cruce con lo ya capturado y saldo del cierre | ✅ |

## Qué hace hoy (Fases 1 a 8)

- **Captura automática**: un `NotificationListenerService` local escucha solo los paquetes de Santander, Banco Falabella (`cl.android`), MercadoPago, Tenpo, Banco de Chile y Google Wallet; el resto se ignora sin guardar nada. La app de la tienda Falabella (`falabella.com`) queda fuera a propósito: avisa compras con CMR, no movimientos de cuenta. Al conectarse, el listener revisa además las notificaciones que siguen en la barra (de las últimas seis horas): así recupera lo que se publicó mientras el servicio estaba caído por un reinicio, una actualización o el sistema matando el proceso, que por `onNotificationPosted` no llega nunca.
- **Correos del banco cuando no hay push**: Banco Falabella no avisa una transferencia por push ni la deja en su buzón interno, así que Wenagastos lee también las notificaciones de **Gmail** (`com.google.android.gm`). El remitente va en el título: si no es `Banco Falabella` **no se guarda nada**, porque el descarte ocurre antes de tocar la base. El cuerpo se lee **por campo** (`Nombre destinatario`, `Monto transferencia`, `Cuenta de origen`, `Fecha`, `Hora`) y no con patrones sueltos: el RUT y el número de cuenta del destinatario son números largos que parecerían montos, y esa cuenta no es la del usuario (la propia es la de origen). Solo se aceptan correos que anuncien un movimiento y traigan monto en pesos; promociones, resúmenes y avisos de seguridad se descartan con el motivo anotado. La hora del movimiento sale del correo (`Fecha` + `Hora`), no de la notificación, que Gmail agrupa y republica tarde. También se lee el correo de **MACHBANK** (`machbank`, `machbank.cl`), que es el único lado que dice **quién** transfirió: MACH no pone el nombre en un campo sino en la frase (*"Recibiste una transferencia de Nicolás Bustos"*), y avisa el banco de destino (`Banco destino: Banco Falabella`) y el número de la cuenta que recibió (`Cuenta destino 19834048777` → `8777`), es decir la cuenta propia. Por eso ese correo se marca `isCounterpart`: se resuelve **solo por el número** —si esa cuenta no está configurada se rechaza con el motivo, nunca se elige "la primera"— y sirve para completar un movimiento, no para crear uno en el banco del remitente. Límites: Gmail solo notifica la **bandeja de entrada** (Spam, Promociones y Notificaciones no avisan) y la cuenta tiene que ser la sincronizada en el teléfono con notificaciones encendidas.
- **Un parser por banco**: obtiene monto en CLP, comercio o destinatario, últimos 4 dígitos, si la tarjeta es de crédito o débito, y si la plata entra o sale. Los avisos de saldo, claves, promociones y resúmenes vacíos se descartan con el motivo anotado.
- **Cuenta automática**: los 4 dígitos que trae el aviso (los de la tarjeta en una compra, los **últimos 4 del número de cuenta** en una transferencia: `cuenta 000095441483` → `1483`) se cruzan por número exacto con la cuenta configurada en Ajustes. La cuenta guarda **dos campos**: `accountLast4`, el número de la cuenta (uno solo), y `cardLast4`, los de sus tarjetas separados por coma. Una tarjeta de débito **no es otra cuenta**: la plata es la de la cuenta corriente, así que compras y transferencias caen en el mismo registro y suman una sola vez. Una tarjeta de crédito sí es una cuenta propia (tiene cupo y deuda), y ahí todo es número de tarjeta.
- **Confianza**: parseo completo + cuenta conocida → **Confirmado** (suma en los totales). Si falta el comercio, la tarjeta o la cuenta → **Por revisar**: no suma en los totales, se cuenta en la tarjeta *Por revisar* del inicio y el motivo queda escrito en las notas del movimiento (se ve al abrirlo y se borra al confirmarlo).
- **Traspasos entre cuentas propias**: "Transferencia hacia tu cuenta …" entra como ingreso y "… desde tu cuenta …" como gasto, aunque el aviso no traiga verbo. Se marcan con `isTransfer` y categoría **Entre cuentas**, así **no suman** en gastos, ingresos, balance ni en el gráfico por categoría: la plata no se gastó, solo cambió de cuenta. Solo cuentan como traspaso si el aviso nombra la cuenta propia y **no** trae destinatario; `Transferiste $50.000 a MARIA SOTO` o `Recibiste una transferencia de JUAN PEREZ` siguen siendo gasto e ingreso normales.
- **Origen y destino del traspaso**: al marcar *Traspaso entre mis cuentas* el editor pide **origen** y **destino** en dos filas de chips (y renombra los chips de sentido a *Sale de mi cuenta* / *Entra a mi cuenta*). La otra punta se guarda en `transferAccountId`, así la lista se lee **Entre cuentas · Santander Cta. Cte. → Falabella Cta. Cte.** desde cualquiera de las dos patas: la que sale y la que entra muestran el mismo par origen → destino. Si el banco avisó una sola pata, la casilla **Registrar la otra pata** (marcada por defecto cuando falta) anota el movimiento espejo en la cuenta de destino, con el sentido contrario, el mismo monto y la misma fecha, y las dos comparten `transferGroupId`. Si la otra pata ya existe (mismo monto, otra cuenta, ±6 h) se enlazan y no se duplica; un movimiento del mismo monto que no sea traspaso no se toca.
- **Reglas por comercio**: cuando la categoría sugerida no es la que quieres, el editor ofrece **Recordar para este comercio** (sin marcar por defecto). La regla se guarda en `merchant_rules` con la clave normalizada del comercio (`TextNorm.key`) **y el sentido**, así el mismo nombre puede ser gasto e ingreso con categorías distintas; volver a enseñarla actualiza la fila y suma un uso, nunca duplica. El motor resuelve primero el traspaso (siempre *Entre cuentas*), después la regla del usuario y al final la semilla de `MerchantCategories`; si la categoría de la regla se borró o archivó, la regla se ignora y vuelve la semilla. En **Ajustes → Reglas por comercio** se ven ordenadas por uso, se les cambia la categoría o se olvidan.
- **Cuentas**: en Ajustes se pueden **crear**, editar y **archivar**. El alta pide nombre, institución y tipo; el color sale de la institución. Los números de 4 dígitos no se pueden repetir entre cuentas activas (el cruce de los avisos es por número exacto). Una cuenta con movimientos no se borra: se **archiva** (deja de ofrecerse al anotar y sus avisos nuevos quedan en Por revisar), porque la FK `transactions.accountId` es `CASCADE` y borrarla se llevaría el historial; solo se borra de verdad cuando no tiene movimientos.
- **Duplicados**: hash SHA-256 (institución + monto + **sentido** + **número de cuenta** + comercio + ventana de 5 min) y búsqueda por monto y sentido en ±5 min. El sentido y la cuenta entran al hash porque un traspaso del mismo monto en el mismo minuto sale de una cuenta y entra a otra: son dos movimientos, no un duplicado. La fuente es la **institución** (`BankSource`), no el paquete: el mismo traspaso puede llegar por push (`cl.android`) y por el correo de Gmail, y no se duplica. La captura corre serializada (un `Mutex` en el motor), así dos avisos simultáneos no pasan los dos la revisión antes de guardarse. Si Google Wallet avisa primero, el aviso del banco **completa** ese movimiento (cuenta, comercio, estado, hash) en vez de duplicarlo; si el banco llega primero, Wallet se descarta y no lo pisa. Lo mismo vale entre el push del banco y el correo de la contraparte: si al movimiento que ya está le falta el nombre, el aviso que llega lo **completa** (nombre, categoría, estado, hash) aunque hayan pasado hasta 30 min —el correo puede demorar—, y si el que está tiene nombre, el nuevo se descarta. Esa ventana ancha solo se usa cuando a alguno de los dos le falta el nombre (lo dice `MerchantQuality`), así que dos compras iguales del mismo monto en la misma media hora siguen siendo dos movimientos.
- **Diagnóstico** (Ajustes → "Ver últimos avisos"): texto crudo, resultado (registrado / por revisar / duplicado / completado / otra app / no es un movimiento) y detalle. La bitácora se recorta sola a las últimas 200 filas. Arriba de la lista va **"Apps de los bancos"**: una fila por fuente con su paquete, si está instalada, si está habilitada y si puede publicar avisos (`AppOpsManager.POST_NOTIFICATION`; `MODE_DEFAULT` cuenta como permitido, que es lo que responde el teléfono cuando nadie lo tocó), más un botón que abre los ajustes de notificaciones de esa app. Se relee al volver a la pantalla, así que apagar y encender un permiso se ve sin reiniciar. Es la comprobación que faltó cuando el retiro de Falabella no apareció: la app del banco estaba instalada como `cl.android` y la de la tienda deshabilitada, y desde la app no se veía ninguna de las dos cosas. Verificado en el A34: Santander, Banco Falabella (`cl.android`), MercadoPago y Google Wallet salen **Lista**, y Tenpo y Banco de Chile **Deshabilitada** —lo mismo que responde `adb shell pm list packages -d`—, con el botón abriendo la pantalla de notificaciones de esa app.
- **Aviso en Inicio**: si el permiso de notificaciones está apagado aparece la tarjeta *La captura está apagada* con botón **Activar**; se va sola al volver de los Ajustes del sistema.
- **Inicio**: gasto total del mes, ingresos, balance, pendientes por revisar, top 5 categorías con barra de proporción y últimos movimientos. Navegación por mes (‹ ›).
- **Movimientos**: lista del mes agrupada por día (formato chileno `vie 20 nov`), con total gastado por día.
- **Reportes** (cuarta pestaña 📊): dona de gastos por categoría con leyenda de porcentajes y el detalle al tocar una categoría (sus movimientos del mes, con acceso al editor), más un gráfico de barras de gastos e ingresos de los últimos 6 meses que sirve para saltar de mes. Arriba, gastos / ingresos / balance / **promedio diario** (en el mes en curso divide por los días corridos). Todo se calcula en Kotlin desde los movimientos del mes, con la misma regla del inicio: no cuentan los movimientos *Por revisar* ni los traspasos, y lo que no tiene categoría aparece como **Sin categoría** para que las partes sumen 100 %.
- **Presupuestos**: en Reportes se le pone un tope mensual a cualquier categoría de gasto (Guardar / Quitar / Cancelar), y la tarjeta muestra *Presupuestado vs gastado* con el total y cada categoría ordenada por avance. El semáforo es **OK** bajo el 80 %, **ámbar** desde el 80 % y **rojo** al llegar al 100 % ("Te quedan $X" / "Te pasaste $X"). El presupuesto vive en `categories.monthlyBudget`, que existe desde la versión 1 de la base (hoy v6).
- **Aviso de presupuesto en Inicio**: cuando alguna categoría del mes alcanza el 80 % del tope aparece la tarjeta *Presupuestos* con la barra y lo que queda, y un atajo a Reportes. El aviso se calcula en vivo (no se guarda estado), así que editar o borrar un movimiento lo corrige al instante, y si no hay presupuestos alcanzados la tarjeta no ocupa espacio.
- **Nuevo/editar movimiento**: monto, comercio, cuenta, categoría (chips filtrados por gasto/ingreso) y fecha con calendario Material 3. El interruptor **Traspaso entre mis cuentas** marca el movimiento como traspaso y cambia los chips a las categorías de traspaso; en ese caso la cuenta se elige dos veces (**origen** y **destino**) y aparece la casilla de la otra pata. Se puede eliminar al editar.
- **Ajustes**: permiso de captura, cuentas (crear, editar, archivar, **unir** y los números que aparecen en sus avisos), **Datos** (exportar, respaldar, restaurar e **importar la cartola**), **bloqueo al abrir**, categorías y nota de privacidad.
- **Importar la cartola** (Ajustes → Datos): se elige la cuenta y un CSV del banco; lo que ya estaba se completa en vez de duplicarse y el saldo del cierre queda como corte de esa cuenta. Detalle en la Fase 8.
- Datos iniciales: Santander Cta. Cte. (número `1483` + débito `4321`), Santander MC Platinum Latam (`5566`), Falabella Cta. Cte. (`8777`), Falabella CMR (`7788`) y MercadoPago + 18 categorías chilenas (Supermercado, Bencina, Delivery, Servicios básicos, Suscripciones, …). No hay cuentas de débito sueltas: la tarjeta vive adentro de su cuenta.
- Cifrado en reposo: la base va cifrada con **SQLCipher** (AES-256) y la llave vive en el Keystore de Android. Ver *Fase 5.1* más abajo.

### Fase 5: los datos salen solo cuando el usuario quiere

- **Datos** (Ajustes → tarjeta *Datos*): cinco botones y un aviso con el resultado. Todo pasa por el **selector de archivos de Android** (SAF): la app no pide permiso de almacenamiento ni escribe fuera de la carpeta que el usuario elige, y no adquiere ninguna vía para sacar datos sola.
- **Exportar CSV**: una fila por movimiento con fecha ISO (`2026-11-20`, ordena sola), hora, monto entero, `tipo` (Gasto / Ingreso / Traspaso), cuenta, cuenta de destino (en los traspasos), categoría, comercio, estado, origen, notas y texto del aviso. Va con **BOM UTF-8** para que Excel muestre `Súper` y no `SÃºper`, comillas dobles según RFC 4180 cuando el campo trae coma o salto de línea, y un apóstrofo delante de lo que empiece con `=`, `+`, `-` o `@` para que un nombre de comercio no se ejecute como fórmula.
- **Exportar JSON**: `{"app":"Wenagastos","documento":"movimientos","version":1,"generadoEn":…,"resumen":{…},"movimientos":[…]}` con los nombres ya resueltos y los campos vacíos omitidos.
- **Crear respaldo**: archivo `wenagastos-AAAA-MM-DD.wena` con **todo** (cuentas, categorías, reglas aprendidas y movimientos, con los ids originales), cifrado con **AES-256-GCM** y una llave derivada de la contraseña del usuario con **PBKDF2-HMAC-SHA256, 120.000 vueltas** y sal de 16 bytes al azar. Cabecera en claro de 40 bytes (`WENABK1` · versión · iteraciones · sal · IV) para poder derivar la llave; sin la contraseña el resto es ruido. La contraseña se pide dos veces al crear (una errata deja el archivo inservible para siempre), **mínimo 12 caracteres y no de puros números**, y no se guarda en ninguna parte: `SettingsViewModel` la borra de memoria apenas termina de cifrar. La regla se exige **solo al crear**: al restaurar no se pide nada más que la contraseña, porque un respaldo viejo con una clave de 8 dígitos tiene que poder abrirse igual —`isWeakPassword` es una advertencia de creación, no un candado de lectura—.
- **Restaurar**: se elige el `.wena`, se escribe la contraseña y se confirma con un botón rojo, porque **reemplaza todo** lo que hay en el teléfono (la app no guarda copia previa). Antes de borrar nada se valida el archivo entero: si un movimiento apunta a una cuenta que no viene, si hay ids repetidos o si un monto es inválido, no se toca la base. La restauración corre en un solo `withTransaction` con las filas y sus ids originales, así un traspaso sigue apuntando a su otra pata y una regla a su categoría; si algo falla a mitad de camino, Room revierte y la base queda como estaba.
- **Bloqueo al abrir**: interruptor en Ajustes (solo si el teléfono tiene PIN o huella). Al abrir, una pantalla sin datos tapa la app entera; **Desbloquear** muestra el `BiometricPrompt` del sistema (huella, rostro o PIN en un solo diálogo, Android 11+) o la pantalla de bloqueo del sistema en Android 10 y anteriores. Si la app estuvo **más de un minuto** en segundo plano se vuelve a pedir (abrir el selector de archivos no cuenta como salir). Si el teléfono pierde su PIN, la app deja pasar y lo dice: el bloqueo no cifra la base, solo es una cortina para quien tome el teléfono desbloqueado.
- **Sin dependencias nuevas**: JSON, CSV, PBKDF2 y AES-GCM con la biblioteca estándar (`kotlin`, `javax.crypto`), y el diálogo biométrico del sistema en vez de `androidx.biometric`. El formato del respaldo se prueba en la JVM.
- **Recordatorio de respaldo**: en Inicio aparece una tarjeta *Respaldo pendiente* cuando pasan **30 días** desde el último `.wena`. Si nunca se creó uno se cuenta desde la instalación, para no molestar el primer día. La fecha se guarda en `SharedPreferences` **después** de escribir el archivo —si la escritura falla, no cuenta como respaldo— y la tarjeta se relee al volver al inicio, así que desaparece sin reiniciar la app. No pide permisos ni notificaciones: es un aviso que se ve al abrir la app, no una push. Probado en el A34 con la fecha movida a mano: a 45 días la tarjeta aparece con *Hace 45 días del último respaldo…* y **Ir a Ajustes** lleva a Ajustes; con la fecha del último `.wena` desaparece.

### Fase 5.1: la base cifrada

- **SQLCipher** (`net.zetetic:sqlcipher-android`) cifra el archivo de Room con AES-256. SQLCipher no cambia el esquema: solo cambia el *open helper*, que pasa a ser `SupportOpenHelperFactory` con la llave. La versión de la base la lleva cada fase (hoy v6, ver Fases 6 y 7).
- **Dónde está la llave**: 32 bytes al azar (`SecureRandom`) que **no** se guardan en claro: van envueltos con una clave AES-256-GCM del **Android Keystore** (alias `wenagastos_db_key`, sin pedir huella), y el sobre (IV + cifrado) queda en `SharedPreferences` (`wenagastos.secret`). Quien copie los archivos de la app se lleva el sobre, no la llave: esa no se puede exportar del Keystore. A SQLCipher se le pasa el secreto en hexadecimal (64 caracteres).
- **Migración automática al arrancar**: en `AppContainer`, antes de que Room abra nada, se mira la cabecera del archivo. Si dice `SQLite format 3` todavía es texto plano y se cifra; si no, se sigue de largo. La receta: `PRAGMA wal_checkpoint(FULL)` para consolidar el WAL, `ATTACH DATABASE … KEY …` + `sqlcipher_export('encrypted')` a un archivo temporal (que tiene que **existir** antes del `ATTACH`: SQLCipher abre el adjunto sin `CREATE`), `PRAGMA encrypted.user_version = 4` (el `user_version` no se copia solo y Room lo necesita) y **verificación antes de reemplazar**: mismas filas en las cinco tablas, misma versión y cabecera ya cifrada. Recién entonces se renombra el original a `wenagastos.db.old`, se pone el nuevo en su lugar y, si el nuevo no abre, se restaura el viejo. `sqlcipher_export` copia también `room_master_table`, sin eso Room rechazaría la base.
- **Si la base no se puede abrir** (Keystore perdido, archivo dañado) la app no se cae en un ciclo de errores: `MainActivity` muestra una pantalla que explica el problema y ofrece **Empezar de cero** (borra base y llave, reinicia) para después restaurar un `.wena`. La captura por notificaciones se detiene sola mientras la base no esté disponible.
- **Costo**: el APK crece ~10 MB (SQLCipher trae su SQLite compilado para `arm64-v8a`, `armeabi-v7a`, `x86` y `x86_64`) y la primera apertura paga la derivación de llave de SQLCipher (unos 200–400 ms, en el hilo de fondo).

### Fase 6: una cuenta por banco, con su tarjeta adentro

- **El modelo**: una cuenta corriente guarda un número de cuenta (`accountLast4`) y los de sus tarjetas de débito (`cardLast4`, separados por coma). La tarjeta **no** es un registro aparte porque la plata es la misma: si se anotaran por separado, el mismo saldo aparecería dos veces y un "traspaso" de la cuenta a su propia tarjeta sería falso. Una tarjeta de crédito sí sigue siendo una cuenta: tiene cupo, ciclo de facturación y deuda propia.
- **Migración 4 → 5** (`MIGRATION_4_5`, manual): el SQLite de `minSdk 26` no tiene `DROP COLUMN`, así que se recrea `accounts` y se copia con un `INSERT … SELECT`: en corriente/ahorro el **primer** número del viejo `last4` pasa a `accountLast4` y el resto a `cardLast4`; en débito/crédito todo queda como tarjeta. Room desactiva las claves foráneas durante la migración, así que `transactions.accountId` no se entera. El `CREATE TABLE` se copia del `createSql` de `5.json` (tabla e índice) para que la validación de esquema pase.
- **Unir dos cuentas** (Ajustes → cuenta → *Unir con …*): mismo banco y misma familia (corriente/ahorro/débito entre sí; crédito solo con crédito). `AccountMergeRepository.plan()` cuenta movimientos y traspasos internos y la pantalla lo pide confirmar **antes** de tocar nada. La fusión corre en un `withTransaction`: borra los traspasos entre las dos (dejan de tener sentido), mueve los movimientos, repunta las patas que apuntaban a la que desaparece, suma los números de tarjeta y borra la fila. El `dedupeHash` no incluye la cuenta, así que unir no reimporta avisos viejos.
- **Ajustes** muestra los números separados (`•••• 1483 + débito •••• 4321`) y **el respaldo sube a v2** con `accountLast4` + `cardLast4`; un `.wena` v1 se sigue leyendo y el reparto se hace al vuelo. Como la migración recrea la tabla, **conviene exportar un respaldo antes de instalar**.

### Fase 7: cuánto hay, por cuenta y por banco

- **Por qué un corte y no una suma**: sumar los movimientos no da el saldo. El historial empieza el día que se instaló la app y los avisos que no llegaron (teléfono apagado, permiso revocado, app del banco deshabilitada) no están. La pieza es `account_snapshots(id, accountId, asOf, balance, source, note)`: lo que el banco dijo **una vez**, a una fecha. El saldo actual es `último corte + movimientos confirmados posteriores`, y **no se guarda**: se calcula, así que una notificación que llega, un movimiento que se edita o un saldo que se escribe corrigen la cifra al instante.
- **El corte incluye su instante**: `asOf` es el momento en que se escribe el saldo y el cálculo suma solo `occurredAt > asOf` (estricto). Un movimiento *Por revisar* o *Ignorado* no mueve nada; los traspasos **sí** mueven el saldo de cada cuenta —la plata se fue de verdad— y se anulan solos en el patrimonio cuando las dos patas existen. Una cuenta sin corte dice *Sin saldo* y no suma: no es cero, es que no se sabe.
- **Dónde se ve**: en Inicio, una tarjeta *Saldos* con el patrimonio y una fila por banco (con cuántas cuentas quedan sin saldo); al tocarla, la pantalla *Saldos* con las cuentas de cada banco, su saldo, de dónde sale (*saldo al 21-11-2026 (a mano), 3 movimientos después*) y el gasto del mes. Cada cuenta tiene un botón **Saldo** que abre el diálogo para escribir lo que el banco muestra hoy; **Quitar** borra ese corte y vuelve a mandar el anterior. Un saldo negativo se acepta y se pinta en rojo: es una deuda (tarjeta usada o sobregiro), no un error de tipeo. En una tarjeta de crédito el diálogo pide la **deuda en positivo** y la app la guarda negativa (Fase 7.1). Las cuentas archivadas no suman.
- **Migración 5 → 6** (`AutoMigration`): solo agrega una tabla y su índice `accountId + asOf`, sin tocar lo que había. `accountId` es `CASCADE`, y al **unir dos cuentas** los cortes de la que desaparece se mueven a la que se queda (si no, la cascada los borraría y el saldo se perdería en silencio).
- **El respaldo sube a v3** con `saldos`; un `.wena` v2 se sigue leyendo (sin cortes: el saldo queda sin saberse hasta que se escriba uno). Restaurar borra los cortes antes que las cuentas e inserta después de ellas, y `decode` rechaza un corte cuya cuenta no venga en el archivo.
- **Límite conocido**: el editor fecha los movimientos nuevos a las **00:00 del día elegido**. Un movimiento escrito a mano con la fecha de hoy queda antes del corte de hoy, o sea cuenta como ya incluido en el saldo que escribiste —es lo correcto si el banco ya lo tenía—; lo que llega por notificación trae la hora real y mueve el saldo. Para un movimiento de hoy que el banco todavía no reflejaba, el saldo se corrige con el próximo corte. El cupo y el ciclo de una tarjeta de crédito llegan en la Fase 7.1.
- **Verificado en el A34** (21-11-2026): la base v5 migró sola a v6 al instalar y los datos quedaron intactos (6 cuentas, gastos del mes $10.314). Se escribió un saldo de $350.000 en la cuenta corriente: Inicio mostró *Patrimonio $350.000*, *1 de 6 cuentas con saldo* y *5 cuentas sin saldo no suman*. Con un movimiento posterior al corte el saldo bajó a **$345.000** con la leyenda *1 movimiento después*, y **Quitar** devolvió la pantalla a *Sin saldo* con el patrimonio en `—`. Los movimientos de prueba se borraron después.

### Fase 7.1: el cupo y el ciclo de la tarjeta

- **Tres datos, no una fórmula**: `accounts` suma `creditLimit`, `statementDay` y `paymentDay`, todos `null` por defecto y con sentido solo en una tarjeta de crédito: en otra cuenta el ViewModel los guarda en `null` aunque el diálogo no los muestre. El saldo de la tarjeta no cambia —sigue saliendo del corte de la Fase 7— y de ahí salen las dos cifras nuevas.
- **Disponible = cupo − deuda**: la deuda es el saldo negativo, y un saldo a favor deja el cupo completo (`debt = max(0, -balance)`). Sin saldo escrito o sin cupo configurado no hay disponible: la pantalla dice *falta el saldo para saber cuánto queda* en vez de inventar un cero, y si el disponible sale negativo se pinta en rojo.
- **Compras del ciclo**: el ciclo corre desde el último día de corte ya pasado hasta el siguiente (`Credit.cycle`), con el día recortado cuando el mes es más corto (corte el 31 en febrero es el 28). Cuentan los movimientos `CONFIRMED` + `DEBIT` de esa cuenta que no son traspaso y con `occurredAt >= inicio del ciclo`; un pago (`CREDIT`) o un traspaso no son compras. El vencimiento es el primer día de pago **después** del corte: corte el 25 y pago el 5 vence el 5 del mes siguiente; pago el 30, ese mismo mes.
- **Por qué dos líneas y no un "facturado"**: el disponible sale de lo que dijo el banco (un número confiable) y las compras del ciclo salen de los avisos (pueden faltar). Un *usado / cupo* mezclaría las dos confianzas, así que se muestran separadas: *Cupo $1.500.000 · disponible $1.300.000* y *Ciclo 25-10 → 25-11: $0 en compras*, más *El pago de esa factura vence el 05-12*.
- **Ventana de la consulta**: la pantalla pide los movimientos desde el día 1 del mes anterior —un ciclo nunca empieza antes— en vez de reconstruir la consulta cada vez que se edita el día de corte.
- **La deuda se escribe en positivo**: en una tarjeta el diálogo de saldo pide *Deuda de hoy*, no *Saldo*: `Credit.balanceFromDebt` la guarda como saldo negativo (deuda $200.000 → `-200000`) y así se resta del cupo. Antes pedía escribir `-200000` a mano en un campo que decía "deuda", y quien escribía el monto tal cual quedaba con el cupo completo: se arregló en la misma pasada. Un `-200.000` escrito por costumbre se acepta igual y no se resta dos veces; en una cuenta que no es tarjeta el campo sigue siendo el saldo del banco, con signo.
- **Migración 6 → 7** (`AutoMigration`) y **respaldo v4** con los tres campos; un `.wena` v3 se lee igual y la tarjeta queda sin configurar. Al **unir dos tarjetas**, el cupo y el ciclo pasan a la que se queda si esta no los tenía.
- **La migración se prueba, no se reza**: `Migration6To7Test` (instrumentada) crea la base con Room, le quita las tres columnas y la deja en `user_version = 6` con el hash de `6.json`; al reabrir, Room corre la `AutoMigration` de verdad y la prueba comprueba que la cuenta y su corte siguen ahí, que las tres columnas quedan en `NULL` y que se pueden escribir. No usa `room-testing` ni copia el DDL: `DROP COLUMN` (SQLite 3.35, API 31+) hace el trabajo.
- **Verificado en el A34** (21-11-2026): la base v6 migró sola a v7 al instalar y la app abrió con sus datos (6 cuentas, gastos del mes $10.314). Se creó una tarjeta de prueba (*Prueba cupo*, cupo $1.500.000, corte 25, pago 5): el detalle de saldos mostró *Cupo $1.500.000 · falta el saldo para saber cuánto queda* y el ciclo en curso con su vencimiento. Se le escribió una deuda de $200.000 en el diálogo y quedó **Cupo $1.500.000 · disponible $1.300.000** (antes del arreglo, el mismo $200.000 dejaba el cupo entero, que es el bug que se corrigió). Al pasarla a *Cuenta corriente* el cupo y el ciclo desaparecieron del detalle —los tres campos vuelven a `null`— y la tarjeta de prueba se borró después. **Sin verificar en el teléfono**: la restauración de un `.wena` v3 en la app (el parseo v3 sí está cubierto por los tests).

### Fase 8: la cartola del banco

- **Para qué**: los avisos cuentan solo lo que pasó con el teléfono prendido y el permiso dado. La cartola del mes cuenta **todo**. Importarla rellena los huecos sin duplicar lo que ya llegó por push.
- **Cómo se elige**: Ajustes → *Datos* → **Importar cartola** pide primero **la cuenta** (un archivo es de una sola) y después abre el selector de Android con `text/*`, `text/comma-separated-values` y `application/vnd.ms-excel`, porque los bancos no se ponen de acuerdo en el tipo que declaran.
- **La lectura no toca la base** (`StatementImport`): busca la cabecera en las primeras filas —fecha, comercio/descripción, cargo y abono (o un solo monto), saldo— y traduce cada renglón. Con una columna de monto **con signo**, `-` es gasto y `+` es ingreso; con una sola columna **sin signo** todo es gasto, que es como vienen las cartolas de tarjeta. Acepta `1.234.567`, `1234567`, `1.234,56`, `(12.345)` y `-12.345` como negativo, fechas `dd/mm/aaaa`, `dd-mm-aa`, `aaaa-mm-dd` y con hora (`dd/mm/aaaa hh:mm`), y descarta títulos, totales y líneas en blanco —esas se informan aparte como **filas salteadas**—.
- **Plantilla por institución**: lo deducido se guarda en `SharedPreferences` (`cartola_<institución>`, como JSON) y la próxima cartola del mismo banco se lee con esa plantilla aunque venga con las columnas en otro orden. La deducción es la red de seguridad, no la regla.
- **El cruce es por cuenta, monto y sentido en el día, no por el `dedupeHash`**: la cartola escribe `LIDER EXPRESS` donde el push decía `COMPRA` y trae el día sin la hora, así que el hash nunca calzaría. Cada fila busca sus parecidos (`findAllNearbyIn`: misma cuenta, mismo monto, mismo sentido, ±36 h del comienzo del día) y **cada parecido se reclama una sola vez**: dos cafés de $2.500 el mismo día son dos movimientos, no dos filas contra el mismo aviso.
- **Lo que completa**: si el movimiento ya existía pero con un comercio genérico (*Transferencia recibida*), la cartola le pone el nombre, lo deja **Confirmado** y borra la nota de *Por revisar*. Nunca pisa un comercio bueno ni toca un traspaso.
- **Lo que falta**: una fila sin comercio —o con uno genérico— entra como **Por revisar** con la nota *La cartola no traía el comercio*, en vez de inventar un nombre.
- **El saldo del cierre** se guarda como `account_snapshot` (Fase 7) con `source = "cartola"` a la **última hora del día más nuevo** del archivo —el corte es del cierre, no del comienzo— recortada a **este instante** si la cartola es del día en curso: el cierre de hoy todavía no llegó, y con una fecha en la noche lo que se comprara después quedaría escondido dentro del saldo. El nombre del archivo queda en la nota. Si la cuenta ya tenía un corte **igual o más nuevo** —o el mismo corte de cartola, con el mismo saldo y del mismo día, reimportado más tarde— el saldo se informa pero no se guarda: un corte viejo no puede pisar uno nuevo y reimportar el mes no deja dos cortes.
- **Todo en una transacción**: los movimientos, las confirmaciones y el corte entran en un solo `withTransaction`; si algo falla a mitad de camino, Room revierte y la cartola se puede volver a importar entera.
- **Probado en la JVM**: `StatementImportTest` cubre la lectura (cabeceras, signos, fechas, filas salteadas) y `StatementImportRepositoryTest` el cruce contra la base (importación limpia, reimportación idempotente, comercio que se completa, dos compras iguales el mismo día, saldo viejo rechazado, cartola de hoy que no deja el corte en la noche, reimportación del mismo día que no corre el corte y faltante de comercio que queda *Por revisar*).

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
# El `java` del sistema en el Mac es 8 y Gradle lo rechaza ("requires at least JVM runtime
# version 11"): hay que apuntar al JDK completo, con su archivo `release` adentro.
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

./gradlew :app:assembleDebug          # genera app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug           # instala en el dispositivo conectado por adb
./gradlew :app:testDebugUnitTest      # 253 pruebas: parsers, montos, categorías, traspasos, reglas, motor, reportes, saldos, cupo, ciclo y deuda de la tarjeta, export/respaldo, cartolas, cabecera/llave de la base, estado de las apps de banco, recordatorio de respaldo, contraste de la paleta y modo de color
./gradlew :app:connectedDebugAndroidTest  # 4 pruebas instrumentadas: migración real a SQLCipher y la 6 -> 7 sobre una base v6 (necesita emulador o teléfono con API 31 o más)
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

2. **Crear respaldo** con una contraseña de 12+ caracteres que no sea solo números y guardarlo; el archivo debe medir más que el JSON (lleva cabecera y sello de GCM) y no debe contener texto legible:

   ```bash
   adb shell head -c 40 /sdcard/Download/wenagastos-AAAA-MM-DD.wena | xxd | head -2   # WENABK1 + versión + iteraciones
   adb shell grep -c Wenagastos /sdcard/Download/wenagastos-AAAA-MM-DD.wena            # 0
   ```

3. **Restaurar** primero con una contraseña equivocada (tiene que aparecer *La contraseña no es la de este respaldo* y la base quedar intacta) y después con la correcta. Con la base cifrada ya no se puede mirar por dentro con `sqlite3`, así que el conteo se compara en la app (Movimientos del mes y Ajustes → Cuentas) o con el JSON exportado antes del respaldo.

### Probar la importación de una cartola

Sin una cartola real se arma una con la forma que tienen las chilenas —punto de miles, fecha `dd/mm/aaaa`, cargo y abono en columnas separadas, saldo en la última— y se deja en *Descargas* del teléfono:

```bash
cat > /tmp/cartola.csv <<'CSV'
Fecha;Descripción;Cargo;Abono;Saldo
01/11/2026;LIDER EXPRESS;12.990;;350.000
02/11/2026;SUELDO NOVIEMBRE;;1.200.000;1.550.000
03/11/2026;TRANSFERENCIA A JUAN PEREZ;50.000;;1.500.000
CSV
adb push /tmp/cartola.csv /sdcard/Download/cartola.csv
```

En **Ajustes → Datos → Importar cartola** se elige la cuenta y el archivo, y el aviso de la tarjeta tiene que decir *Anoté 3 movimientos. El saldo que informa quedó como corte de la cuenta.* En **Saldos** esa cuenta muestra *saldo al 03-11-2026 (cartola), sin movimientos después*. Volver a importar el mismo archivo dice *Nada nuevo: los movimientos ya estaban anotados* —el saldo se informa pero no se guarda, porque el corte que ya tiene es igual de nuevo— y la cuenta sigue con **un solo** corte. Con la base cifrada, el conteo se compara en la app y no con `sqlite3`.

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
- Esquemas Room exportados en `app/schemas/` (necesarios para las migraciones futuras). La versión 3 agrega `transactions.transferAccountId` + su índice con una `AutoMigration` 2 → 3; la versión 4 agrega `merchant_rules` (índice único `merchantKey + direction`, FK a `categories` con `SET_NULL`) con una `AutoMigration` 3 → 4. La **versión 5** (Fase 6) parte `accounts.last4` en `accountLast4` + `cardLast4` y cambia el índice a `institution + kind + accountLast4`: como `minSdk 26` no tiene `DROP COLUMN`, es una migración **manual** (`MIGRATION_4_5`) que recrea la tabla. La **versión 6** (Fase 7) agrega `account_snapshots` y su índice `accountId + asOf` con una `AutoMigration` 5 → 6. La **versión 7** (Fase 7.1) agrega `accounts.creditLimit`, `statementDay` y `paymentDay` (nullables) con una `AutoMigration` 6 → 7. El respaldo anota en `schema` la versión con la que se escribió y su propio `version` (hoy 4).
- La normalización de texto (`TextNorm`) vive en `domain`, porque la usan tanto `capture` como los repositorios.

## Problemas conocidos

- **Android 11 esconde los paquetes que la app no declara**: el diagnóstico nuevo mostró *No instalada* para los seis bancos del A34 aunque los seis estaban instalados, porque `getApplicationInfo` responde `NameNotFoundException` cuando el paquete no es visible. Se arregla declarándolos en `<queries>` del manifiesto —uno por uno, no `QUERY_ALL_PACKAGES`—, y sin esa lista cualquier pantalla que pregunte «¿está instalada?» miente. Es la versión en la app del mismo error que costó el retiro de Falabella, solo que esta vez lo dijo una pantalla en vez de un silencio.
- **El APK pesa ~10 MB más**: SQLCipher trae su SQLite compilado para `arm64-v8a`, `armeabi-v7a`, `x86` y `x86_64`. Se puede recortar con ABI splits el día que importe; hoy es un solo teléfono.
- **Si se pierde la llave** (se borran los datos de la app, se cambia de teléfono sin respaldo, o el Keystore se resetea), la base cifrada no se puede abrir. La app lo detecta y ofrece *Empezar de cero* para después restaurar un `.wena`.
- **Avisos que nunca se registran**: la captura solo ve la notificación en el instante en que el sistema la publica. Si el teléfono estaba **apagado**, el permiso de acceso a notificaciones estaba revocado o la app estaba detenida, ese movimiento no existe para la app y **no hay forma de recuperarlo** (no hay `INTERNET`, no se leen SMS y no hay `getActiveNotifications`, `WorkManager` ni `BOOT_COMPLETED` que lo busquen después). Si el teléfono solo estaba sin señal, la push del banco llega al reconectar y sí se registra; un **SMS** del banco, en cambio, no se lee. Lo perdido se anota a mano con el botón **+**.
- **La app del banco puede tener un paquete que no nombra al banco**: la captura depende de que exista un canal, y buscar el canal por nombre de paquete engaña. Pasó en el A34 con un retiro de la tarjeta Falabella: se concluyó que **no estaba instalada la app de Banco Falabella** porque ningún paquete se llamaba así, pero estaba instalada desde agosto como **`cl.android`** (el id genérico que deja el framework Technisys; label `Banco Falabella`, v18.46), habilitada y con permiso de notificaciones. El fallo era de Wenagastos: `cl.android` no estaba en `BankSource` y el listener descartaba el aviso en el primer `return`, sin dejar rastro en Diagnóstico. **Se identifica la app por su label, no por el id**: `adb shell pm path <paquete>` + `adb pull` + `aapt2 dump badging` (`application-label:`). Y para que la push llegue, la app tiene que estar **con sesión iniciada**. El resto de las comprobaciones sigue igual: `adb shell pm list packages -u` (¿está?), `pm list packages -d` (¿deshabilitada?), `appops get <paquete> POST_NOTIFICATION` (¿puede avisar?) y `content query --uri content://sms/inbox` (¿hay SMS del banco? Banco Falabella no manda SMS de movimientos: en 2.620 de la bandeja solo 7 eran del banco y todos códigos de seguridad). La app de la tienda (`com.falabella.falabellaApp`, label `Falabella`) quedó **fuera de la captura**: solo avisa compras con CMR, no movimientos de cuenta, y confundirla con el banco fue el origen de todo este lío.
- **Una app deshabilitada se ve igual que un banco sin avisos**: si la app está **deshabilitada** o **sin permiso de notificaciones** — lo típico en apps preinstaladas que nunca se abrieron — el sistema descarta el aviso *antes* de que el listener lo vea, y en **Diagnóstico** no queda ni rastro. Se comprueba con `adb shell pm list packages -d` y `adb shell appops get <paquete> POST_NOTIFICATION`; se arregla abriendo la app una vez y aceptando sus notificaciones (`pm install-existing --user 0 <paquete>` y `pm grant <paquete> android.permission.POST_NOTIFICATIONS` también sirven por adb; en el A34 Knox rechaza `pm enable`, pero `pm install-existing` sí pasó). La app tiene que estar **con sesión iniciada** para que el banco le mande la push.
- **Gradle no ve los cambios de archivos** (el daemon cachea el sistema de archivos y responde `UP-TO-DATE` aunque el código cambió). Solución:

  ```bash
  ./gradlew --stop && ./gradlew :app:assembleDebug --no-daemon
  ```

## Siguiente

- **Calibrar con avisos reales** (en curso): el motor ya está cerrado y probado con avisos sintéticos, pero falta ver los textos reales del A34. Para cada banco (Santander, Banco Falabella, MercadoPago, Tenpo, Banco de Chile y Google Wallet) se compara el texto crudo con lo que se parseó en **Diagnóstico** y se ajustan `BankSource.packages` y los patrones de `NotifParser` / `AmountExtractor` / `MerchantExtractor` / `CardDetector`.
  Riesgo conocido: las compras en cuotas CMR pueden entregar el monto de la cuota en vez del total.
- **Fase 5.1 — SQLCipher**: ✅ la base va cifrada en reposo, la llave vive en el Keystore y la migración de un archivo en texto plano ocurre sola al arrancar (sin cambiar la versión de esquema). **Verificado en el A34** (21-11-2026): `databases/wenagastos.db` y su `-wal` empiezan con bytes aleatorios (`ff4ff8b8…`, `377f0682…`) en vez de `SQLite format 3`, no queda ningún `.db` en claro ni `-journal` suelto en `databases/`, y la app abre los movimientos con normalidad —si la llave del Keystore no sirviera, la consulta fallaría en vez de mostrar 6 cuentas y 5 movimientos—. El respaldo `.wena` es otra cosa y también va cifrado, pero con la contraseña del usuario y no con la llave del teléfono: eso es justamente lo que lo hace restaurable en otro teléfono.
- **Fase 5.2 — captura por SMS** (idea para cuando aparezcan avisos perdidos): leer los SMS del banco con `READ_SMS`. Un SMS recibido con el teléfono apagado se entrega al encender, así que sería la única fuente capaz de recuperar un movimiento que ocurrió sin el teléfono prendido. Pide permiso nuevo y un parser por banco, porque el texto del SMS no se parece al de la notificación. Antes de construirla hay que confirmar que el banco **manda** SMS de movimientos: Banco Falabella no los manda (solo códigos de seguridad), así que ahí no habría nada que leer.
- **Estado de los bancos en Diagnóstico**: ✅ hecho (Fase 2.1). El aviso que faltaba cuando el retiro de Falabella no apareció ya se ve desde la app: **la app del banco estaba instalada como `cl.android`, fuera de la lista de `BankSource`** —esa era la causa, no el teléfono apagado— y la de la tienda estaba deshabilitada.
- **Recordatorios de respaldo**: ✅ hecho (Fase 5.3). 30 días sin respaldo, contados desde el último `.wena` o desde la instalación, con la fecha en `SharedPreferences`. Al crear el respaldo la tarjeta del inicio desaparece sola.
- **Fase 7.1 — cupo y facturado de las tarjetas**: ✅ hecha. `accounts` guarda cupo, día de corte y día de pago, y el detalle de saldos muestra el disponible y las compras del ciclo que corre. **Queda pendiente** el aviso previo al vencimiento (una notificación local unos días antes del `paymentDay`), que hoy no existe: la fecha se ve en pantalla pero nadie la recuerda.
- **Fase 8 — importar cartolas (CSV)**: ✅ hecha. Ajustes → Datos → *Importar cartola*: se elige la cuenta y el CSV, `StatementImport` deduce las columnas (fecha, comercio, cargo/abono o monto con signo, saldo) y guarda la plantilla por banco en `SharedPreferences` para la próxima. Cada fila calza con un movimiento ya capturado por **cuenta + monto + sentido en el día** (±36 h, cada parecido se reclama una vez), así llega lo que faltaba sin duplicar lo que llegó por push; la fila que encontraba un comercio genérico lo completa y la deja *Confirmado*, y la que no traía comercio entra *Por revisar*. El saldo del cierre queda como `account_snapshot` con `source = "cartola"` al final del día más nuevo del archivo (recortado a *ahora* si la cartola es de hoy), sin pisar un corte igual o más nuevo. **Queda pendiente** el Excel (`xlsx`): un `.xlsx` es un ZIP de XML, así que hay que descomprimirlo a mano —nada de `POI`— y hoy el selector solo abre texto.
