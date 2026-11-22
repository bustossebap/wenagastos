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

> La terracota pura se queda en 4,26:1 sobre la tarjeta más clara (`#2E3150`). Para texto de
> cargo en oscuro se usa `#E58A70` (4,91:1), que `Color.kt` guarda como `TerracotaNoche`.

**Los papeles de Material se conservan entre modos** para que nada cambie de significado al
cambiar de tema:

| Papel M3 | Claro | Oscuro | Para qué |
|---|---|---|---|
| `primary` | marino | trigo | marca, enlaces, "Gastos" en el gráfico |
| `secondary` | terracota entintada | terracota | cargo |
| `tertiary` | salvia entintada | salvia | abono, "Ingresos" en el gráfico |
| `error` | terracota entintada | terracota | error, presupuesto excedido |

> Pendiente para la etapa de componentes: el semáforo de presupuesto usa `primary/tertiary/error`,
> y con estos papeles "cerca del tope" queda verde en vez de ámbar. Necesita tokens propios
> (`Wena.paleta`), no roles M3.

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
- **VG5000** se reserva para el número grande del héroe (etapa 3). Todavía no se asigna a ningún
  rol porque las pantallas actuales fuerzan `FontWeight.Bold` y la familia tiene un solo peso: se
  engordaría sintéticamente y el trazo se mancha.
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

Los tres gestos que hacen reconocible la app sin el logo del manual:

1. **Teclas.** El módulo gráfico del manual son barras verticales y horizontales, "rectas pero con
   trazo orgánico". En la app: las barras de `Charts.kt` y de `ShareBar` se dibujan como teclas
   (alturas distintas, separación fina, borde levemente irregular) y el tab activo de la barra de
   navegación se marca con una tecla pisada, no con el pill de Material.
2. **Borde rasgado.** Una sola vez, en el borde superior del bloque del héroe de Inicio, como las
   bandas de la portada. `Path` con 5 o 6 curvas y amplitud baja.
3. **Bloques de color con el texto que manda el manual.** Un badge sobre marino lleva crema; sobre
   trigo, tinta. Nunca al revés.

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
| `ui/theme/Type.kt` | familias, escala tipográfica, `Vg5000` |
| `ui/theme/Shape.kt` | escala de radios |
| `res/values/colors.xml` | arranque en crema; icono sobre marino |
| `res/values-night/colors.xml` | arranque en noche |
| `res/font/` | Instrument Sans 400/600/700, VG5000 |
| `assets/licenses/` | OFL de las dos fuentes |

`Wena.paleta` es la puerta para lo que el `ColorScheme` de Material 3 no puede expresar: los seis
acentos del manual y los dos colores de dinero resueltos por modo (`abono`, `cargo`).

---

## 6. Etapas

| # | Alcance | Estado |
|---|---|---|
| 0 | Fichas, paleta, contrastes | ✅ este documento |
| 1 | Tokens: color, tipografía, formas, fuentes, arranque | ✅ |
| 2 | Chrome: barra de navegación con tecla activa, FAB, componentes comunes | pendiente |
| 3 | Inicio: héroe con borde rasgado y monto en VG5000 | pendiente |
| 4 | Reportes: barras-tecla y paleta de acento | pendiente |
| 5 | Resto: Movimientos, Balances, Ajustes, Editor, Bloqueo | pendiente |
| 6 | Auditoría de contraste en dispositivo + capturas antes/después | pendiente |

## 7. Pendientes conocidos

- **Avatares de categoría:** usan `color.copy(alpha = 0.18f)` sobre el color que elige el usuario.
  Sobre crema el tinte queda muy pálido y hay colores de categoría que no contrastan con el emoji
  encima. Etapa 2 o 5.
- **Logotipo de Wenagastos:** no existe. Idea anotada, no comprometida: un glifo de barras que se
  lea a la vez como teclas de piano y como barras de gráfico.
- **Icono de la app:** hoy son barras blancas sobre teal (`#0F766E`); el fondo ya pasó a marino,
  el dibujo de las barras se puede rehacer como teclas en la etapa 6.

