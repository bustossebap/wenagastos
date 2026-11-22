package cl.wenasea.wenagastos.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Sistema visual heredado del manual de marca SolBemol v0.1 (páginas 13, 14, 19, 20 y 21).
 * Wenagastos no es un producto SolBemol: se hereda el sistema (paleta, reglas de contraste,
 * retícula, tipografía), no el imagotipo. Ver docs/diseno.md.
 *
 * Regla del manual, "interacción cromática": el color se usa como acento y no como atmósfera,
 * y el contraste tinta/crema lleva el protagonismo. Sobre marino, terracota y salvia el texto
 * va en crema; sobre trigo va en tinta.
 *
 * Aviso importante: los acentos del manual están pensados para bloques grandes, no para texto
 * chico. Medidos contra crema, terracota da 2.57:1, salvia 2.17:1 y trigo 1.34:1, todos por
 * debajo de WCAG AA (4.5:1). Por eso hay dos familias: el acento puro (solo relleno) y su
 * variante entintada (texto).
 */

// --- Paleta madre (manual, p. 13) ---
val Tinta = Color(0xFF333333)      // 11.02:1 sobre crema
val Crema = Color(0xFFF3F0DD)
val Marino = Color(0xFF383A5B)     //  9.52:1 sobre crema
val Terracota = Color(0xFFE07A5F)  // 2.57:1 sobre crema — solo relleno
val Trigo = Color(0xFFF2CB8D)      // 1.34:1 sobre crema — solo relleno
val Salvia = Color(0xFF73B193)     // 2.17:1 sobre crema — solo relleno

// --- Derivadas de papel, para separar planos sin sombras (no están en el manual) ---
val CremaAlta = Color(0xFFFCFAF0)
val CremaHonda = Color(0xFFE9E5CE)
val CremaBorde = Color(0xFFD5D0B8)

// --- Acentos entintados: la misma familia, oscurecida hasta pasar AA sobre crema y sobre las
// tarjetas de papel (CremaHonda #E9E5CE es el plano más oscuro donde va texto) ---
val TerracotaTinta = Color(0xFF9E4830) // 5.35:1 crema, 4.84:1 tarjeta, 5.02:1 sobre blush
val SalviaTinta = Color(0xFF436957)    // 5.39:1 crema, 4.88:1 tarjeta, 5.19:1 sobre verde pálido
val TrigoTinta = Color(0xFF7F5C1C)     // 5.31:1 crema, 4.80:1 tarjeta

// La terracota pura alcanza en oscuro sobre el fondo (5.57:1) pero se queda corta sobre la
// tarjeta más clara (#2E3150, 4.26:1). Esta variante, apenas más luminosa, pasa en las tres.
val TerracotaNoche = Color(0xFFE58A70) // 4.91:1 sobre #2E3150, 5.54:1 sobre #262943

// --- Noche: el modo oscuro que el manual no trae, deducido de la familia del marino ---
val NocheHueco = Color(0xFF14161F)
val NocheCapa1 = Color(0xFF191B29)
val Noche = Color(0xFF1C1E30)          // crema encima = 14.34:1
val NocheCapa2 = Color(0xFF262943)     // trigo encima = 9.25:1
val NocheCapa3 = Color(0xFF2E3150)
val NocheCapa4 = Color(0xFF363A5E)
