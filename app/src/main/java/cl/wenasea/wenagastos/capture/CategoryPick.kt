package cl.wenasea.wenagastos.capture

import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.repo.CategoryRepository
import cl.wenasea.wenagastos.data.repo.MerchantRuleRepository
import cl.wenasea.wenagastos.domain.TextNorm

/**
 * La categoría que le toca a un comercio. Primero lo que el usuario enseñó (Fase 3); si no hay
 * regla, la semilla del catálogo de [MerchantCategories].
 *
 * Vive aparte porque la usan dos caminos que tienen que coincidir: la notificación que llega sola y
 * la cartola que se importa un mes después. Si cada uno eligiera por su cuenta, el mismo Lider
 * caería en "Súper" por aviso y en "Otros" por cartola.
 */
object CategoryPick {

    /**
     * @param merchantKnown `false` cuando el banco no dijo el comercio: ahí no hay regla que valga.
     * @param isTransfer un traspaso entre cuentas propias no es gasto ni ingreso, así que **no lleva
     *   categoría**: la plata no se gastó, solo cambió de cuenta, y decir "Sin categoría" sería
     *   tratarlo como un gasto al que le falta la etiqueta.
     */
    suspend fun resolve(
        rules: MerchantRuleRepository,
        categories: CategoryRepository,
        merchant: String,
        direction: Direction,
        rawText: String = "",
        merchantKnown: Boolean = true,
        isTransfer: Boolean = false,
    ): Long? {
        if (isTransfer) return null
        val active = categories.listActive()
        if (merchantKnown) rules.resolve(merchant, direction)?.let { return it }
        val name = MerchantCategories.categoryFor(merchant, direction, rawText) ?: return null
        val wanted = TextNorm.plain(name)
        return active.firstOrNull { TextNorm.plain(it.name) == wanted }?.id
    }
}
