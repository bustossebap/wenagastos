# Probar a mano — Wenagastos

Recetas por escenario: compilar, emulador, captura por aviso, exportar, importar la cartola y cifrado. Se lee solo al probar ese escenario.

La advertencia que vale para todas: las tareas instrumentadas desinstalan la app al terminar y se llevan la base y su llave, así que nunca se apuntan al teléfono.

## Compilar y correr las pruebas

```bash
# El `java` del sistema en el Mac es 8 y Gradle lo rechaza ("requires at least JVM runtime
# version 11"): hay que apuntar al JDK completo, con su archivo `release` adentro.
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

./gradlew :app:assembleDebug          # genera app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug           # instala en el dispositivo conectado por adb
./gradlew :app:testDebugUnitTest      # 339 pruebas: parsers, montos, categorías, traspasos, cuotas, reglas, motor, reportes, saldos, cupo, ciclo y deuda de la tarjeta, export/respaldo, cartolas en CSV, en PDF y en Excel, cabecera/llave de la base, estado de las apps de banco, recordatorio de respaldo, rutas de navegación, logos de banco, contraste de la paleta y modo de color
# Con el teléfono y el emulador conectados a la vez hay que elegir uno: `ANDROID_SERIAL=<serial>`
# (los seriales salen de `adb devices -l`). Sin él, Gradle instala el APK y corre las pruebas en
# los dos, y el `adb` de los otros comandos se niega a adivinar.
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest  # 6 pruebas instrumentadas: migraciones 6 -> 7, 7 -> 8 y 8 -> 9 sobre bases reales y la migración a SQLCipher (necesita API 31 o más). Nunca en el teléfono: la tarea desinstala la app al terminar y se lleva la base y su llave.
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

La cartola que el banco entrega en **PDF** se importa igual, sin convertirla: se copia al teléfono y se elige con el mismo botón.

```bash
adb push ~/Downloads/cartola-26.pdf /sdcard/Download/cartola-26.pdf
```

Si el banco entrega la planilla en el **`.xls` viejo** (el Excel 97-2003 de Falabella), la app lo rechaza a propósito —es un contenedor binario, no un ZIP— y el aviso pide abrirlo en Excel y guardarlo como `.xlsx` o CSV. Al pasar a CSV hay dos cosas que cuidar: dejarlo en orden **cronológico** (el banco lo lista al revés) y **comprobar el saldo corrido** fila a fila —anterior + abono − cargo = el saldo que trae esa fila—, porque una fila perdida en la conversión no se nota al importar y deja el corte mal. La cartola 8777 de noviembre se convirtió así: 20 movimientos y $910.109 de cierre.

Si el archivo no trae una tabla con fecha y monto —un escaneo, por ejemplo— el aviso dice que no pudo leer la tabla y no anota nada, que es preferible a anotar montos al azar.

Con el teléfono y el emulador conectados a la vez, todo lo de arriba lleva `-s <serial>` (`adb -s emulator-5554 push …`): sin `-s`, `adb` contesta *more than one device/emulator* y no hace nada.

En **Ajustes → Datos → Importar cartola** se elige la cuenta y el archivo, y el aviso de la tarjeta tiene que decir *Anoté 3 movimientos. El saldo que informa quedó como corte de la cuenta.* En **Saldos** esa cuenta muestra *saldo al 03-11-2026 (cartola), sin movimientos después* —si la cuenta ya tenía movimientos posteriores al corte, la leyenda dice cuántos en vez de *sin movimientos después*—. Volver a importar el mismo archivo dice *Nada nuevo: los movimientos ya estaban anotados* —el saldo se informa pero no se guarda, porque el corte que ya tiene es igual de nuevo— y la cuenta sigue con **un solo** corte. Con la base cifrada, el conteo se compara en la app y no con `sqlite3`.

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

