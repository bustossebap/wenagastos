package cl.wenasea.wenagastos.capture

import cl.wenasea.wenagastos.data.local.entity.Institution

/**
 * Apps cuyas notificaciones sabemos leer.
 *
 * [priority] define qué fuente gana cuando dos llegan por el mismo gasto:
 * el banco (3) manda sobre Google Wallet (1), porque el banco informa la tarjeta usada.
 */
enum class BankSource(
    val label: String,
    val packages: Set<String>,
    val institution: Institution?,
    val priority: Int,
) {
    SANTANDER(
        label = "Santander",
        packages = setOf("cl.santander.smartphone", "cl.santander.santanderpasschile"),
        institution = Institution.SANTANDER,
        priority = 3,
    ),
    FALABELLA(
        label = "Falabella",
        // Es `falabella.com`, la app de la tienda (avisa compras con CMR); el banco es otra
        // app, BANCO_FALABELLA. El label se deja igual porque el parser compara el título del
        // aviso con él para no tomarlo como comercio.
        packages = setOf("com.falabella.falabellaApp"),
        institution = Institution.FALABELLA,
        priority = 3,
    ),
    BANCO_FALABELLA(
        label = "Banco Falabella",
        // Su applicationId real es `cl.android` (framework Technisys): no dice "falabella",
        // así que sus avisos se descartaban como si vinieran de una app cualquiera.
        packages = setOf("cl.android"),
        institution = Institution.FALABELLA,
        priority = 3,
    ),
    MERCADOPAGO(
        label = "MercadoPago",
        packages = setOf("com.mercadopago.wallet"),
        institution = Institution.MERCADOPAGO,
        priority = 3,
    ),
    TENPO(
        label = "Tenpo",
        packages = setOf("com.krealo.tenpo"),
        institution = Institution.OTHER,
        priority = 3,
    ),
    BANCO_CHILE(
        label = "Banco de Chile",
        packages = setOf("cl.bancochile.mi_banco"),
        institution = Institution.OTHER,
        priority = 3,
    ),
    WALLET(
        label = "Google Wallet",
        packages = setOf("com.google.android.apps.walletnfcrel"),
        institution = null,
        priority = 1,
    ),

    /** Solo para pruebas: `adb shell cmd notification post` postea como `com.android.shell`. */
    SHELL(
        label = "Prueba (adb)",
        packages = setOf("com.android.shell"),
        institution = null,
        priority = 1,
    ),
    ;

    companion object {
        fun of(packageName: String): BankSource? =
            entries.firstOrNull { packageName in it.packages }
    }
}
