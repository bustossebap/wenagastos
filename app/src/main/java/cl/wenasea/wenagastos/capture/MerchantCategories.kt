package cl.wenasea.wenagastos.capture

import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.domain.TextNorm

/**
 * Semilla de categorización por comercio, para que los movimientos capturados
 * no queden todos en "Sin categoría". La Fase 3 reemplaza esto por reglas propias
 * y aprendizaje a partir de lo que el usuario vaya corrigiendo.
 *
 * El orden importa: gana la primera regla que calce ("uber eats" antes que "uber").
 */
object MerchantCategories {

    private data class Rule(val keywords: List<String>, val category: String)

    private val expenseRules = listOf(
        Rule(
            listOf(
                "lider", "jumbo", "santa isabel", "tottus", "unimarc", "acuenta",
                "mayorista", "vega central", "supermercado", "superbodega", "cugat",
                "ok market", "alvi", "central mayorista",
            ),
            "Supermercado",
        ),
        Rule(
            listOf(
                "uber eats", "ubereats", "pedidosya", "rappi", "didi food",
                "justo", "cornershop",
            ),
            "Delivery",
        ),
        Rule(
            listOf(
                "copec", "shell", "petrobras", "enex", "gasolinera", "combustible",
                "pronto copec", "upita",
            ),
            "Bencina",
        ),
        Rule(
            listOf(
                "uber", "cabify", "didi", "metro", "bip", "red movilidad", "turbus",
                "pullman", "latam", "jetsmart", "sky airline", "taxi", "efevoo",
                "estacion central",
            ),
            "Transporte",
        ),
        Rule(
            listOf(
                "mcdonald", "burger", "kfc", "subway", "domino", "papa john",
                "starbucks", "juan maestro", "dunkin", "sushi", "restaurante",
                "restoran", "cafeteria", "cafe", "pizzeria", "pizza", "taco bell",
                "chilis", "applebees", "mostaza", "doggi", "fuente", "chorrillana",
            ),
            "Restaurantes",
        ),
        Rule(
            listOf(
                "enel", "chilectra", "frontel", "aguas andinas", "esval", "essbio",
                "smapa", "gasco", "abastible", "lipigas", "metrogas", "vtr",
                "movistar", "entel", "claro", "wom", "gtd", "mundo pacifico",
            ),
            "Servicios básicos",
        ),
        Rule(
            listOf(
                "netflix", "spotify", "disney", "hbo max", "hbo", "crunchyroll",
                "youtube", "apple.com/bill", "itunes", "icloud", "google storage",
                "google one", "chatgpt", "openai", "anthropic", "claude",
                "prime video", "amazon prime", "paramount", "star plus", "starplus",
                "deezer", "audible", "canva", "adobe",
            ),
            "Suscripciones",
        ),
        Rule(
            listOf(
                "cruz verde", "salcobrand", "ahumada", "farmacia", "isapre",
                "consalud", "colmena", "banmedica", "clinica", "dental",
                "optica", "laboratorio",
            ),
            "Salud",
        ),
        Rule(
            listOf(
                "falabella", "paris", "ripley", "la polar", "hites", "temu", "shein",
                "aliexpress", "mercado libre", "mercadolibre", "sodimac", "easy",
                "ikea", "zara", "h&m", "decathlon", "pcfactory", "preunic",
            ),
            "Compras",
        ),
        Rule(
            listOf("petco", "petsy", "mascota", "veterinaria", "zoologico"),
            "Mascotas",
        ),
        Rule(
            listOf(
                "smart fit", "smartfit", "pacific fitness", "sportlife", "energy fitness",
                "nike", "adidas", "topper", "under armour",
            ),
            "Deporte",
        ),
        Rule(
            listOf(
                "udemy", "coursera", "platzi", "universidad", "instituto", "colegio",
                "libreria", "escuela",
            ),
            "Educación",
        ),
        Rule(
            listOf(
                "cinemark", "cineplanet", "cine", "steam", "playstation", "xbox",
                "nintendo", "betano", "apuesta", "coolbet",
            ),
            "Gustitos",
        ),
    )

    private val incomeRules = listOf(
        Rule(listOf("sueldo", "remuneracion", "haberes", "liquidacion"), "Sueldo"),
        Rule(listOf("rendimiento", "interes", "dividendo"), "Rendimientos"),
        Rule(listOf("devolucion", "reembolso"), "Otros ingresos"),
    )

    /** Palabras que delatan una transferencia: el comercio es una persona, no una marca. */
    private val transferCues = listOf("transferencia", "transferiste", "transfirio", "transferi")

    /** Nombre de la categoría sugerida, o `null` si no hay regla. */
    fun categoryFor(merchant: String, direction: Direction, text: String = ""): String? {
        val key = TextNorm.key(merchant)
        val rules = if (direction == Direction.CREDIT) incomeRules else expenseRules
        rules.firstOrNull { rule -> rule.keywords.any { key.contains(it) } }
            ?.let { return it.category }

        // Una transferencia recibida de una persona no calza con ninguna regla de comercio.
        if (direction == Direction.CREDIT) {
            val plain = TextNorm.plain(text)
            if (transferCues.any { plain.contains(it) }) return "Otros ingresos"
        }
        return null
    }
}
