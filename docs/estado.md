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
- Cerrada: **9.6, segunda mitad** (las dos patas del traspaso). En curso:
  **9.5b en *Reportes***, llevar allí el desglose fijo / variable / ahorro que ya
  está en la tarjeta de *Saldos*.
- Alcance de 9.5b **confirmado**: el primer corte va en *Saldos* (hecho) y la
  mitad que falta es *Reportes*, donde ya esperan `Reports.fixedExpenses` /
  `variableExpenses` / `savings`, escritas y **sin usar**.

## Último verificado
- Suite completa (24-11-2026): **346 pruebas en la JVM, 0 fallas, 0 errores**
  (`:app:testDebugUnitTest` con JDK 21; Gradle recompiló —`compileDebugKotlin`
  ejecutado— y los XML de `app/build/test-results` dan 346/0/0 en 34 clases).
  Sin `JAVA_HOME` apuntando al JDK 21 la corrida ni siquiera configura: el `java`
  del sistema es 8.
- **Nada de la segunda mitad del 9.6 está verificado en el A34**: falta reimportar
  la cartola de noviembre y ver los traspasos como traspasos y las patas
  enlazadas.

## Hechos del código que cuestan redescubrir
- El saldo no se guarda: se calcula con el último corte (`account_snapshots`) más
  los movimientos confirmados posteriores.
- Un traspaso mueve el saldo de las dos cuentas pero no el patrimonio, y no lleva
  categoría; la deuda de una tarjeta no resta del patrimonio.
- La captura por aviso y la cartola comparten la deduplicación; la cartola cruza
  por cuenta + monto + sentido en el día, no por `dedupeHash`.
- `Balances.monthSpendByAccount` **descarta los traspasos**: el desglose del mes no
  puede salir de filtrar su resultado, o los ahorros dan $0 (era el bug). De ahí
  `fixedExpensesByAccount` / `variableExpensesByAccount` / `savingsByAccount`, cada
  una con su criterio y su KDoc.
- Un traspaso importado se guarda **sin pareja** y se enlaza al final de la
  importación con `TransactionRepository.findUnlinkedLeg` (mismo monto, sentido
  contrario, otra cuenta, ±3 días; `PAIR_WINDOW`); una pata cuya pareja no está
  anotada nunca se enlaza.
- Las gráficas y las teclas son dibujo a mano: no hay librería de gráficos.

## Pendientes vigentes
- 9.5b en *Reportes*. Límites de hoy: «fijo» es la **cuota**, no la naturaleza
  FIJO de la categoría (las funciones por cuenta no reciben categorías), y el
  pago de la tarjeta cae en **Ahorros** porque también es un traspaso.
- Avisos reales de los seis bancos: la calibración sigue en curso según el README.
- El README (`## Siguiente`) repite, condensada, la crónica de las fases 5.1, 5.2,
  7.1, 8.x y 9.x que ahora vive en `docs/fases.md`. Falta decidir si se recorta a
  la hoja de ruta.

## Próximos pasos
1. 9.4 saldo corrido del mes (*corte vigente + movimientos*).
2. 9.5b en *Reportes*, con las tres funciones que ya existen.
3. Después: 9.7 reportes por cuenta y 9.8 gastos fijos recurrentes con
   proyección.

## Archivos calientes (solo si la tarea es de esta fase)
- `ui/balances/`: `BalancesViewModel.kt`, `BalancesSummaryCard.kt`,
  `BalancesScreen.kt` · `ui/reports/` para 9.5b.
- `domain/Balances.kt` · `domain/Reports.kt`.
- Pruebas que fijan el contrato: `domain/BalancesTest.kt`,
  `domain/ReportsTest.kt`.

## Archivos calientes (solo si la tarea es de esta fase)
- `ui/balances/`: `BalancesViewModel.kt`, `BalancesSummaryCard.kt`,
  `BalancesScreen.kt`.
- `domain/Balances.kt` · `domain/Reports.kt`.
- Pruebas que fijan el contrato: `domain/BalancesTest.kt`,
  `domain/ReportsTest.kt`.
