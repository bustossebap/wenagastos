# Estado — Wenagastos

actualizado: 2026-11-24 · base **v9** (`app/schemas` tiene `1.json` … `9.json`) ·
`versionName 0.6.1` / `versionCode 7` en `app/build.gradle.kts`, por confirmar si
van retrasados respecto de las fases 9.x.

Instantánea, no historial: lo que se cierra se borra. Techo: ~120 líneas.

**Cuándo se actualiza:** solo si cambia la fase, el objetivo, la arquitectura, un
comportamiento no obvio del código, un bug, un resultado verificado o los
próximos pasos. Un arreglo menor no toca este archivo. La hoja de ruta acordada
vive en el README (`## Siguiente`); aquí solo lo que está en curso.

## Ahora
- Fase en curso: **9.5 (segunda parte)** — el desglose fijo / variable / ahorro
  del mes.
- **Alcance sin confirmar (decisión pendiente):** el README dice que va «en
  Reportes»; el trabajo de la sesión anterior lo puso en la tarjeta de resumen de
  Saldos/Inicio. Hay que confirmarlo antes de cerrar la fase. Mientras no se
  confirme, la fase 9.5 sigue **abierta** y su descripción no se cambia.

- **Sin commitear en el árbol (trabajo de una sesión anterior):** mecánica de
  traspasos con dos patas —`findUnlinkedLeg` en `TransactionDao`/`Repositories`,
  ventana de 3 días—, corrección al reimportar del traspaso que la cartola vieja
  anotó como gasto, el abono de la planilla de tarjeta como pago aunque no traiga
  resumen, el cupo y las fechas del resumen que pisan lo escrito a mano, y el
  desglose fijo / variable / ahorro por cuenta. Los comentarios lo llaman «Fase
  9.7», pero el README reserva 9.7 para reportes por cuenta: el número está por
  decidir.

## Último verificado
- Lectura de código (esta sesión): `BalancesViewModel.kt` ya calcula
  `monthFixed` / `monthVariable` / `monthSavings` y los expone en el estado de la
  pantalla, y `BalancesSummaryCard.kt` dibuja la fila correspondiente. No
  reimplementarlo.
- Suite completa (24-11-2026): **346 pruebas en la JVM, 0 fallas, 0 errores**
  (`:app:testDebugUnitTest` con JDK 21; Gradle respondió UP-TO-DATE y los XML de
  `app/build/test-results` dan 346/0/0). Incluye el trabajo de traspasos que aún
  no está commiteado. Sin `JAVA_HOME` apuntando al JDK 21 la corrida ni siquiera
  configura: el `java` del sistema es 8.

## Hechos del código que cuestan redescubrir
- El saldo no se guarda: se calcula con el último corte (`account_snapshots`) más
  los movimientos confirmados posteriores.
- Un traspaso mueve el saldo de las dos cuentas pero no el patrimonio, y no lleva
  categoría; la deuda de una tarjeta no resta del patrimonio.
- La captura por aviso y la cartola comparten la deduplicación; la cartola cruza
  por cuenta + monto + sentido en el día, no por `dedupeHash`.
- Las gráficas y las teclas son dibujo a mano: no hay librería de gráficos.

## Pendientes vigentes
- 9.5b: confirmar alcance (Reportes vs Inicio/Saldos) y cerrar el desglose.
- Fase 9.6, segunda mitad: que el pago de la tarjeta descuente de la cuenta
  corriente y que las dos patas se enlacen solas cuando lleguen las dos cartolas.
- Avisos reales de los seis bancos: la calibración sigue en curso según el README.
- Árbol de trabajo: 14 archivos `.kt` modificados (+816/−375) y dos sueltos:
  `BalancesViewModel.kt.bak` (la versión previa del archivo, sin el desglose) y
  `ScratchRealCartolaTest.kt` (su propio KDoc dice «temporal… se borra al
  terminar», pero corre en la suite: 1 prueba que lee cartolas de `/tmp`).
  Decidir si se commitea el trabajo de traspasos y se limpian los dos sueltos.
- `docs/fases.md` conserva la frase «hoy v6» tal como se escribió en su fase; la
  versión vigente es la 9.
- El README (`## Siguiente`) repite, condensada, la crónica de las fases 5.1,
  5.2, 7.1, 8.x, 9.1, 9.2, 9.3 y 9.6 que ahora vive en `docs/fases.md`. Se dejó
  intacta para no borrar historia: falta decidir si se recorta a la hoja de ruta.

## Próximos pasos
1. Confirmar el alcance de 9.5b y cerrar el desglose.
2. Correr `./gradlew :app:testDebugUnitTest` y anotar el conteo real aquí.
3. Después, en el orden acordado: 9.4 saldo corrido del mes, 9.7 reportes por
   cuenta y 9.8 gastos fijos recurrentes con proyección.

## Archivos calientes (solo si la tarea es de esta fase)
- `ui/balances/`: `BalancesViewModel.kt`, `BalancesSummaryCard.kt`,
  `BalancesScreen.kt`.
- `domain/Balances.kt` · `domain/Reports.kt`.
- Pruebas que fijan el contrato: `domain/BalancesTest.kt`,
  `domain/ReportsTest.kt`.
