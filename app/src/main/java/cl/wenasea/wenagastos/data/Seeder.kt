package cl.wenasea.wenagastos.data

import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.CategoryEntity
import cl.wenasea.wenagastos.data.local.entity.CategoryKind
import cl.wenasea.wenagastos.data.local.entity.Institution
import cl.wenasea.wenagastos.data.repo.AccountRepository
import cl.wenasea.wenagastos.data.repo.CategoryRepository

/**
 * Datos iniciales: las cuentas reales del usuario y un set de categorías chilenas.
 * Solo se insertan si la base está vacía.
 */
object Seeder {

    suspend fun seedIfEmpty(accounts: AccountRepository, categories: CategoryRepository) {
        if (accounts.count() == 0) accounts.addAll(defaultAccounts())
        if (categories.count() == 0) categories.addAll(defaultCategories())
    }

    /**
     * La tarjeta de débito no es una cuenta: es un número más de la cuenta corriente, porque la
     * plata es la misma. Una tarjeta de crédito sí es una cuenta propia: tiene su cupo y su deuda.
     */
    fun defaultAccounts(): List<AccountEntity> = listOf(
        AccountEntity(
            name = "Santander Cta. Cte.",
            institution = Institution.SANTANDER,
            kind = AccountKind.CHECKING,
            accountLast4 = "1483",
            cardLast4 = "4321",
            colorHex = "#EC0000",
        ),
        AccountEntity(
            name = "Santander MC Platinum Latam",
            institution = Institution.SANTANDER,
            kind = AccountKind.CREDIT,
            cardLast4 = "5566",
            colorHex = "#EC0000",
        ),
        AccountEntity(
            name = "Falabella Cta. Cte.",
            institution = Institution.FALABELLA,
            kind = AccountKind.CHECKING,
            accountLast4 = "8777",
            colorHex = "#7CB342",
        ),
        AccountEntity(
            name = "Falabella CMR",
            institution = Institution.FALABELLA,
            kind = AccountKind.CREDIT,
            cardLast4 = "7788",
            colorHex = "#7CB342",
        ),
        AccountEntity(
            name = "MercadoPago",
            institution = Institution.MERCADOPAGO,
            kind = AccountKind.SAVINGS,
            colorHex = "#00A1E0",
        ),
    )

    fun defaultCategories(): List<CategoryEntity> {
        fun expense(name: String, emoji: String, color: String, order: Int) = CategoryEntity(
            name = name,
            emoji = emoji,
            colorHex = color,
            kind = CategoryKind.EXPENSE,
            sortOrder = order,
        )

        fun income(name: String, emoji: String, color: String, order: Int) = CategoryEntity(
            name = name,
            emoji = emoji,
            colorHex = color,
            kind = CategoryKind.INCOME,
            sortOrder = order,
        )

        return listOf(
            expense("Supermercado", "🛒", "#2E7D32", 10),
            expense("Transporte", "🚌", "#1565C0", 20),
            expense("Bencina", "⛽", "#455A64", 30),
            expense("Delivery", "🛵", "#EF6C00", 40),
            expense("Restaurantes", "🍽️", "#C62828", 50),
            expense("Salud", "💊", "#00695C", 60),
            expense("Servicios básicos", "🏠", "#5E35B1", 70),
            expense("Suscripciones", "📺", "#AD1457", 80),
            expense("Mascotas", "🐶", "#8D6E63", 90),
            expense("Deporte", "⚽", "#00838F", 100),
            expense("Compras", "🛍️", "#6A1B9A", 110),
            expense("Gustitos", "🎉", "#F9A825", 120),
            expense("Educación", "📚", "#283593", 130),
            expense("Otros gastos", "❓", "#616161", 140),
            income("Sueldo", "💼", "#2E7D32", 200),
            income("Rendimientos", "📈", "#00897B", 210),
            income("Otros ingresos", "➕", "#43A047", 220),
        )
        // Un traspaso entre cuentas propias no lleva categoría: la plata no se gastó, cambió de
        // cuenta. Por eso el catálogo no tiene una categoría de traspaso (Fase 9.1).
    }
}
