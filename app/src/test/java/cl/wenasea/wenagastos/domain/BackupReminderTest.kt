package cl.wenasea.wenagastos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El recordatorio del respaldo es la única defensa que queda si el teléfono se pierde: la base va
 * cifrada con la llave del Keystore y no se puede abrir en otro aparato. Por eso el plazo se cuenta
 * con cuidado, y sin respaldos se arranca desde la instalación para no molestar el primer día.
 */
class BackupReminderTest {

    private val day = 24L * 60L * 60L * 1000L
    private val installedAt = 1_700_000_000_000L

    @Test
    fun `los dias se cuentan completos`() {
        assertEquals(0L, BackupReminder.daysBetween(installedAt, installedAt + day - 1))
        assertEquals(1L, BackupReminder.daysBetween(installedAt, installedAt + day))
        assertEquals(45L, BackupReminder.daysBetween(installedAt, installedAt + 45 * day))
    }

    @Test
    fun `un reloj atrasado no adelanta el aviso`() {
        assertEquals(0L, BackupReminder.daysBetween(installedAt, installedAt - 10 * day))
        assertFalse(BackupReminder.isDue(lastBackupAt = 0L, firstInstallAt = installedAt, now = installedAt - day))
    }

    @Test
    fun `sin respaldos se cuenta desde la instalacion`() {
        val justBefore = installedAt + (BackupReminder.INTERVAL_DAYS - 1) * day
        val onTime = installedAt + BackupReminder.INTERVAL_DAYS * day
        assertFalse(BackupReminder.isDue(0L, installedAt, justBefore))
        assertTrue(BackupReminder.isDue(0L, installedAt, onTime))
    }

    @Test
    fun `con respaldo manda el respaldo, no la instalacion`() {
        val backupAt = installedAt + 200 * day
        assertFalse(
            BackupReminder.isDue(
                lastBackupAt = backupAt,
                firstInstallAt = installedAt,
                now = backupAt + 29 * day,
            ),
        )
        assertTrue(
            BackupReminder.isDue(
                lastBackupAt = backupAt,
                firstInstallAt = installedAt,
                now = backupAt + 30 * day,
            ),
        )
    }

    @Test
    fun `el texto dice cuando fue el ultimo respaldo`() {
        val backupAt = installedAt + 200 * day
        val label = BackupReminder.label(backupAt, installedAt, backupAt + 45 * day)
        assertEquals(
            "Hace 45 días del último respaldo. Si este teléfono se pierde, ese archivo es lo " +
                "único que devuelve el historial.",
            label,
        )
    }

    @Test
    fun `sin respaldos el texto lo dice sin vueltas`() {
        val label = BackupReminder.label(0L, installedAt, installedAt + 60 * day)
        assertTrue(label!!.startsWith("Nunca has creado un respaldo."))
    }

    @Test
    fun `mientras no toca, no hay nada que mostrar`() {
        assertNull(BackupReminder.label(0L, installedAt, installedAt + day))
        assertNull(
            BackupReminder.label(
                lastBackupAt = installedAt + 100 * day,
                firstInstallAt = installedAt,
                now = installedAt + 110 * day,
            ),
        )
    }
}
