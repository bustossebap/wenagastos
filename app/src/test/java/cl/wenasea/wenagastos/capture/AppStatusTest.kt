package cl.wenasea.wenagastos.capture

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El estado de cada app de banco se decide con lo que responde el sistema. Estas pruebas fijan el
 * reparto, porque es lo que va a explicar un movimiento que no apareció: si esta tabla se equivoca,
 * Diagnóstico diría "Lista" justo cuando el banco no puede publicar.
 */
class AppStatusTest {

    private val instalada = AppFacts(installed = true, enabled = true, canNotify = true)

    @Test
    fun `sin instalar no se puede leer nada`() {
        assertEquals(SourceState.NOT_INSTALLED, SourceState.of(listOf(AppFacts())))
    }

    @Test
    fun `una app habilitada y con avisos esta lista`() {
        assertEquals(SourceState.READY, SourceState.of(listOf(instalada)))
    }

    @Test
    fun `deshabilitada gana sobre los avisos, porque no llega a publicar`() {
        val facts = AppFacts(installed = true, enabled = false, canNotify = true)
        assertEquals(SourceState.DISABLED, SourceState.of(listOf(facts)))
    }

    @Test
    fun `habilitada pero con los avisos apagados queda muda`() {
        val facts = AppFacts(installed = true, enabled = true, canNotify = false)
        assertEquals(SourceState.MUTED, SourceState.of(listOf(facts)))
    }

    /**
     * Santander publica desde dos apps. Si solo una sirve, la fuente sirve: el motor lee la que
     * avise y no hay nada que arreglar.
     */
    @Test
    fun `dos paquetes quedan en el mejor de los dos`() {
        val facts = listOf(
            AppFacts(installed = true, enabled = false),
            instalada,
        )
        assertEquals(SourceState.READY, SourceState.of(facts))
    }

    @Test
    fun `con un paquete sin instalar y otro apagado se informa el apagado`() {
        val facts = listOf(
            AppFacts(installed = false),
            AppFacts(installed = true, enabled = true, canNotify = false),
        )
        assertEquals(SourceState.MUTED, SourceState.of(facts))
    }

    @Test
    fun `una lista vacia no acusa a nadie de estar lista`() {
        assertEquals(SourceState.NOT_INSTALLED, SourceState.of(emptyList()))
    }
}
