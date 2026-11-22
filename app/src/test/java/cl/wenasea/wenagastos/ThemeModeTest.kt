package cl.wenasea.wenagastos

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La preferencia de tema se guarda como texto: un valor viejo o escrito a mano tiene que caer en
 * automático en vez de dejar la app sin tema.
 */
class ThemeModeTest {

    @Test
    fun `sin elegir nada se sigue al telefono`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(null))
    }

    @Test
    fun `lo guardado se vuelve a leer igual`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromKey(mode.key))
        }
    }

    @Test
    fun `un valor desconocido cae en automatico`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("sepia"))
    }
}
