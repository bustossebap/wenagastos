# Diseño — sistema visual de Wenagastos

Base: manual de marca **SolBemol v0.1** (Gastón Meneses Espejo, 27 páginas; se leyeron en detalle
las páginas 13, 14, 19, 20 y 21). Wenagastos **no** es un producto SolBemol: se hereda el
*sistema* —paleta, reglas de contraste, retícula, tipografía—, no el imagotipo ni los artefactos
de la marca musical. El logotipo de Wenagastos es un problema aparte y todavía no existe.

---

## 1. Paleta

Seis colores del manual, sin inventar tonos nuevos. Los HEX están verificados contra la página 13
y los ratios de contraste se calcularon con la fórmula WCAG 2.1 (sRGB, luminancia relativa).

| Nombre | HEX | Rol en el manual | Contra tinta/crema |
|---|---|---|---|
| Tinta | `#333333` | texto | **11,02:1** ✅ |
| Crema | `#F3F0DD` | papel / fondo | — |
| Marino | `#383A5B` | la marca | **9,52:1** ✅ |
| Terracota | `#E07A5F` | acento | 2,57:1 ❌ |
| Trigo | `#F2CB8D` | acento | 1,34:1 ❌ |
| Salvia | `#73B193` | acento | 2,17:1 ❌ |

### El problema que hubo que resolver

La paleta del manual está pensada para **bloques grandes** (tapas, afiches, tarjetas), no para
texto chico. Medido contra crema, el propio manual pone crema sobre terracota a **2,57:1**, que no
alcanza ni el 3:1 de texto grande. En una app eso no se sostiene: la mayoría del texto son
etiquetas de 11–12 sp.

Regla de la casa, entonces:

> **Terracota, trigo y salvia son color de relleno, nunca de texto sobre papel.**
> Cuando un acento tiene que ser texto, se usa su variante entintada.

| Acento | Relleno | Variante para texto | Sobre crema | Sobre tarjeta |
|---|---|---|---|---|
| Terracota | `#E07A5F` | **`#9E4830`** | 5,35:1 ✅ | 4,84:1 ✅ |
| Salvia | `#73B193` | **`#436957`** | 5,39:1 ✅ | 4,88:1 ✅ |
| Trigo | `#F2CB8D` | **`#7F5C1C`** | 5,31:1 ✅ | 4,80:1 ✅ |

La columna "tarjeta" es la que manda: el texto de acento también aparece sobre `surfaceContainerHigh`
(`#E9E5CE`), el plano más oscuro donde se lee, y ahí las variantes tienen que seguir pasando AA.

### Modo oscuro

El manual no trae modo oscuro. Se deduce de la familia del marino: de día el marino es tinta
sobre papel, de noche el papel es marino. Y **la regla se invierte** — sobre `#1C1E30` los
acentos puros sí pasan AA:

| Color | sobre `#1C1E30` (fondo) | sobre `#262943` (tarjeta) |
|---|---|---|
| Crema `#F3F0DD` | 14,34:1 ✅ | 12,37:1 ✅ |
| Trigo `#F2CB8D` | 10,72:1 ✅ | 9,25:1 ✅ |
| Salvia `#73B193` | 6,60:1 ✅ | 5,70:1 ✅ |
| Terracota `#E07A5F` | 5,57:1 ✅ | 4,81:1 ✅ |
| Marino `#383A5B` | 1,51:1 ❌ | 1,30:1 ❌ |

Por eso en oscuro el acento de marca (`primary`) es **trigo sobre tinta** — par que el propio
manual sanciona en la página de interacción cromática — y el marino deja de ser acento para
quedar sólo como fondo.

> En oscuro los acentos puros solo alcanzan AA sobre el fondo: la terracota baja de 5,57:1 sobre la
> noche a 4,26:1 sobre la capa 3 y 3,70:1 sobre la tarjeta más clara (`#363A5E`), y la salvia de
> 6,60:1 a 4,39:1 sobre esa misma tarjeta. Como los montos de abono se leen dentro de tarjetas
> —el detalle por categoría de Reportes— y el semáforo de presupuesto vive en una tarjeta de Inicio
> y otra de Reportes, la auditoría de la etapa 6 cambió los dos papeles: el cargo usa
> `TerracotaNoche #EE9A82` (4,98:1 sobre la capa 4) y el abono y el semáforo `SalviaNoche #8FC9AE`
> (5,79:1), dos variantes que pasan en las cuatro capas de la noche.

> El héroe de Inicio necesita un marino que se despegue de las tarjetas, y `#383A5B` tiene la
> misma luminancia que `#363A5E`. En oscuro el bloque usa **`MarinoAlto #4A4E78`** (crema encima
> 6,93:1, trigo 5,16:1); en claro usa el Marino del manual sin cambios (crema 9,52:1).

### Quién elige el modo

Automático es el de fábrica: la app sigue al teléfono. Abajo de todo en Ajustes hay tres teclas
—**Automático / Claro / Oscuro**— para forzarlo, y la elección se guarda en `SharedPreferences`
(`Prefs.themeKey`), no en el respaldo: es de la instalación, como el bloqueo.

La preferencia no se pasa a mano a Compose. Se aplica en `attachBaseContext` (`AppAppearance.kt`),
que sobreescribe el bit de noche del contexto base igual que `AppLocale` sobreescribe el idioma.
Así una sola decisión alcanza a todo lo que mira la configuración —`isSystemInDarkTheme()`, los
recursos `values-night` que pintan la ventana en el arranque en frío y el color de los íconos de la
barra de estado que instala `enableEdgeToEdge()`— y no hay forma de que el tema de Compose y el
del sistema queden en desacuerdo. El costo es honesto: al cambiar de modo la actividad se vuelve a
crear una vez, como cuando Android cambia el tema del sistema.

**Los papeles de Material se conservan entre modos** para que nada cambie de significado al
cambiar de tema:

| Papel M3 | Claro | Oscuro | Para qué |
|---|---|---|---|
| `primary` | marino | trigo | marca, enlaces, "Gastos" en el gráfico |
| `secondary` | terracota entintada | terracota | cargo |
| `tertiary` | salvia entintada | salvia | abono, "Ingresos" en el gráfico |
| `error` | terracota entintada | terracota | error, presupuesto excedido |

> El semáforo de presupuesto **no** usa `primary/tertiary/error`: con esos papeles "cerca del tope"
> quedaba verde y el aviso no avisaba. Tiene tres tokens propios en `Wena.paleta` —
> `presupuestoOk` (salvia), `presupuestoCerca` (trigo) y `presupuestoExcedido` (terracota) — que de
> día son las variantes entintadas y de noche los acentos puros. `budgetColor()` los lee de ahí.

### Reglas de uso

1. **El color es acento, no atmósfera.** No se tiñe la pantalla ni las filas de una lista. El
   fondo es papel; el color aparece en chips, barras, badges y bloques contados.
2. **El contraste tinta/crema lleva el protagonismo.** Los montos y las etiquetas se leen.
3. **Sobre relleno de color, el texto sigue la regla del manual:** crema sobre marino, terracota y
   salvia; tinta sobre trigo.
4. **Dinero:** abono = salvia, cargo = terracota. Nunca verde/rojo puros: se pierden sobre crema y
   rompen la paleta.

---

## 2. Tipografía

| Fuente | Uso | Licencia | ¿Va en el APK? |
|---|---|---|---|
| **Instrument Sans** 400/600/700 | todo el texto | SIL OFL 1.1 | ✅ `res/font/instrument_sans_*.ttf` |
| **VG5000** | cifra protagonista, logotipo | SIL OFL 1.1 (Velvetyne / Justin Bihan) | ✅ `res/font/vg5000_regular.otf` |
| Ouma Latin VF | display del manual | Adobe Fonts (uso) | ❌ no redistribuible |
| Myriad Variable Concept | — | Adobe | ❌ |
| Komet, Indivisible, Alegreya Sans | — | Adobe / Google | ❌ (no se usan) |

- La escala display se resolvió con Instrument Sans Bold y tracking cerrado (−1 a −0,5 sp): Ouma
  no se puede empaquetar.
- **Cifras tabulares (`tnum`) en toda la escala.** Instrument Sans trae la característica; así los
  montos quedan alineados en columna sin trucos de ancho fijo.
- **VG5000** se usa sólo en la cifra del héroe de Inicio (`HeroAmount`, 40/44 sp), sin tocar
  `fontWeight`: la familia tiene un solo peso y engordarla sintéticamente mancha el trazo. El
  énfasis lo hacen el tamaño y el trigo sobre marino. Nunca va en texto corrido.
- Licencias completas en `app/src/main/assets/licenses/` (viajan dentro del APK, como pide la OFL).

### Escala (sp)

| Rol | Tamaño / línea | Peso |
|---|---|---|
| displayLarge | 48 / 52 | Bold |
| displayMedium | 40 / 44 | Bold |
| displaySmall | 33 / 38 | Bold |
| headlineLarge | 30 / 36 | SemiBold |
| headlineMedium | 26 / 32 | SemiBold |
| headlineSmall | 22 / 28 | SemiBold |
| titleLarge | 20 / 26 | SemiBold |
| titleMedium | 16 / 22 | SemiBold |
| titleSmall | 14 / 20 | SemiBold |
| bodyLarge | 16 / 24 | Regular |
| bodyMedium | 14 / 20 | Regular |
| bodySmall | 12 / 17 | Regular |
| labelLarge | 14 / 20 | SemiBold |
| labelMedium | 12 / 16 | SemiBold |
| labelSmall | 11 / 15 | SemiBold |

---

## 3. Retícula, radios y trazos

- **Radios cortos y contenidos:** 2, 3, 5, 8 y 12 dp (`Shape.kt`). Nada de píldoras ni de 28 dp:
  el manual construye con bloques de papel casi rectos.
- **Separar planos con líneas, no con sombras.** El manual no tiene elevación; la separación es de
  color y de borde. Se prefiere `outlineVariant` de 1 dp antes que `CardDefaults.cardElevation`.
- **Espaciado en múltiplos de 4**, con 8 y 16 como valores dominantes; 24 para separar secciones.
- **Margen lateral de 16 dp**, 20 dp en pantallas de lectura.

---

## 4. Componentes firma

Los cuatro gestos que hacen reconocible la app sin el logo del manual:

1. **Teclas.** El módulo gráfico del manual son barras verticales y horizontales, "rectas pero con
   trazo orgánico". En la app: las barras de `Charts.kt` (la serie mensual y la barra apilada de
   categorías) y `ShareBar` se dibujan como teclas (alturas distintas, separación fina, borde
   levemente irregular) y el tab activo de la barra de navegación se marca con una tecla pisada, no
   con el pill de Material. Desde la etapa 5 lo que se toca también es tecla: `WenaButton` y
   `WenaChip` (`ui/common/Keys.kt`) reemplazan la píldora de `Button` y el chip rosado de Material
   por un bloque de 5 dp de radio —apagado con borde, pisado con `primary`—, y el FAB bajó de los
   16 dp que trae Material 3 a los 12 dp de la escala. La etapa 6 sumó `WenaSwitch`: la píldora de
   32 dp del interruptor es ahora una pista de 44 × 26 dp con radio de 3 dp y un pulgar cuadrado,
   dibujada a mano pero con `Modifier.toggleable` y `Role.Switch` debajo para que el lector de
   pantalla siga anunciando «activado/desactivado». Los `Checkbox` no se tocaron: ya son cuadrados
   de 2 dp de radio encendidos en `primary`, o sea que estaban dentro de la regla.
2. **Borde rasgado.** Una sola vez, en el borde superior del bloque del héroe de Inicio, como las
   bandas de la portada. `Path` con una decena de curvas cortas, 7 dp de amplitud y un perfil fijo
   —no aleatorio— para que no cambie de forma entre recomposiciones (`HeroBlock.kt`).
3. **Renglones rayados.** Las listas largas —los movimientos del mes, las cuentas de cada banco, las
   categorías de Ajustes— se separan con una regla de 1 dp en `outlineVariant` (`LedgerRule`), no
   con una tarjeta por fila ni con sombras. La hoja se lee como el libro de cuentas que la app es.
4. **Bloques de color con el texto que manda el manual.** Un badge sobre marino lleva crema; sobre
   trigo, tinta. Nunca al revés. Los avisos del inicio (`WenaNotice`) llevan el acento como una
   tecla de 4 dp al canto sobre papel, no como fondo teñido: teñir la tarjeta entera es la
   "atmósfera" que la regla de uso prohíbe.

Lo que **no** se trae: la mascota (es música), la textura de papel (ensucia la lectura de cifras y
envejece mal en pantalla), el imagotipo `solbemol` (marca distinta) y todo lo de imprenta (CMYK,
papeles, área de resguardo).

---

## 5. Dónde vive cada cosa

| Archivo | Qué contiene |
|---|---|
| `ui/theme/Color.kt` | paleta madre, derivadas de papel, acentos entintados, capas de noche |
| `ui/theme/Theme.kt` | `ColorScheme` claro y oscuro + instalación de la paleta de marca |
| `ui/theme/WenaPalette.kt` | `WenaPalette` y `Wena.paleta`: acentos y colores de dinero por modo |
| `ui/theme/Type.kt` | familias, escala tipográfica, `Vg5000` y `HeroAmount` |
| `ui/theme/Shape.kt` | escala de radios |
| `AppAppearance.kt` | `ThemeMode` y el `uiMode` forzado en el contexto base (tema claro/oscuro) |
| `ui/nav/WenagastosNavHost.kt` | barra con tecla pisada (`WenaNavBar`, `WenaTab`) y FAB de marca a 12 dp |
| `ui/common/HeroBlock.kt` | bloque del héroe y `TornTopEdge` (borde rasgado) |
| `ui/common/Keys.kt` | `WenaButton`, `WenaChip` y `WenaSwitch`: lo que se toca, dibujado como tecla |
| `ui/common/Components.kt` | piezas compartidas: `MonthSwitcher` con teclas, `SectionHeader`, `TransactionRow`, `CategoryAvatar` y `ShareBar` como teclas, `LedgerRule`, `WenaNotice`, `EmptyState` |
| `ui/common/BudgetComponents.kt` | semáforo de presupuesto: `budgetColor()` desde `Wena.paleta` y `BudgetProgress` |
| `ui/reports/Charts.kt` | `MonthlyBars` (serie de seis meses) y `CategoryStack` (barra-tecla apilada de partes), dibujados a mano |
| `ui/settings/ThemeCard.kt` | las tres teclas de tema al final de Ajustes |
| `res/drawable/ic_launcher_foreground.xml` | el icono: tres teclas sobre el marino de la marca |
| `res/values/colors.xml` | arranque en crema; icono sobre marino |
| `res/values-night/colors.xml` | arranque en noche |
| `res/font/` | Instrument Sans 400/600/700, VG5000 |
| `assets/licenses/` | OFL de las dos fuentes |

`Wena.paleta` es la puerta para lo que el `ColorScheme` de Material 3 no puede expresar: los seis
acentos del manual, los dos colores de dinero resueltos por modo (`abono`, `cargo`), los tres del
héroe (`heroFondo`, `heroTexto`, `heroCifra`) y el semáforo de presupuesto
(`presupuestoOk`, `presupuestoCerca`, `presupuestoExcedido`). El trío del héroe no es sólo del
héroe: el patrimonio de Saldos y la portada del bloqueo lo reusan para no inventar un segundo
marino ni un segundo trigo.

---

## 6. Etapas

| # | Alcance | Estado |
|---|---|---|
| 0 | Fichas, paleta, contrastes | ✅ este documento |
| 1 | Tokens: color, tipografía, formas, fuentes, arranque | ✅ |
| 2 | Chrome: barra de navegación con tecla activa, FAB, componentes comunes | ✅ |
| 3 | Inicio: héroe con borde rasgado y monto en VG5000 | ✅ |
| 4 | Reportes: barras-tecla y paleta de acento | ✅ héroe, barras-tecla, leyenda, semáforo y categorías |
| 5 | Resto: Movimientos, Balances, Ajustes, Editor, Bloqueo | ✅ teclas para tocar, renglones rayados, patrimonio en bloque de marca y portada de bloqueo |
| 6 | Auditoría de contraste en dispositivo + capturas antes/después | ✅ dos defectos corregidos en oscuro, `WenaSwitch`, notas de Inicio en papel, icono de teclas y tema claro/oscuro en Ajustes |

## 7. Pendientes conocidos

- **Auditoría de contraste:** cerrada en la etapa 6. `ContrastTest` mide con la fórmula de WCAG
  2.1 los pares que la app pinta de verdad, en claro y en oscuro, y falla si un token se sale de
  AA. La revisión en el emulador (1080 × 2400, tema claro y oscuro) encontró dos defectos que el
  test ahora cuida: el abono y el semáforo de presupuesto se leían dentro de tarjetas con 4,39:1 y
  3,70:1. `SalviaNoche` y el nuevo `TerracotaNoche` los dejan en 5,79:1 y 4,98:1.
- **Avatares de categoría:** resuelto en la etapa 2 — tecla cuadrada con tinte al 22 % y borde al
  55 % del color elegido, que sobre crema sí define la forma. La auditoría de la etapa 6 revisó el
  emoji encima con los colores más claros de la paleta: el emoji no es texto (trae sus propios
  colores) y el borde mantiene la forma, así que no hizo falta cambiarlo.
- **Gráfico de partes de Reportes:** resuelto en la etapa 4 — la dona salió y entró una barra-tecla
  apilada de 20 dp con 2 dp de papel entre tramos. Una dona solo se lee con pocas rebanadas y el
  manual no tiene curvas; con quince categorías se volvía confeti. La barra muestra las cinco que
  más gastan y suma el resto en un tramo neutro (`outline`), que en la leyenda no lleva tecla: la
  tecla es de una categoría y el agregado no tiene color propio. El detalle completo sigue abajo,
  categoría por categoría, con sus montos y sus movimientos.
- **Logotipo de Wenagastos:** no existe. Idea anotada, no comprometida: un glifo de barras que se
  lea a la vez como teclas de piano y como barras de gráfico. El icono de la app ya va en esa
  dirección (tres teclas sobre marino), pero un icono no es un logotipo.
- **Arranque en frío:** la apertura de SQLCipher tiene los ojos en blanco entre 1,3 y 1,9 s en el
  emulador. Candidato para una etapa propia: una portada de papel con las teclas del icono mientras
  la base abre, en vez de la ventana vacía.
- **Otras etapas:** ninguna abierta. Lo que sigue es producto, no sistema visual.

