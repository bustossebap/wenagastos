package cl.wenasea.wenagastos.domain

/**
 * Cuándo volver a pedir un respaldo.
 *
 * El respaldo es el **único** archivo que se puede abrir en otro teléfono (la base va cifrada con
 * la llave del Keystore, que no sale de este aparato), así que pasar meses sin uno es pasar meses
 * a un robo de distancia de perder todo el historial.
 *
 * Se avisa cada [INTERVAL_DAYS] días contados desde el último respaldo y, si nunca hubo uno, desde
 * la instalación: así la tarjeta no aparece el mismo día en que se instala la app.
 */
object BackupReminder {

    const val INTERVAL_DAYS = 30L

    private const val DAY_MS = 24L * 60L * 60L * 1000L

    /** Días completos entre dos instantes; nunca negativo aunque el reloj vaya para atrás. */
    fun daysBetween(from: Long, to: Long): Long = ((to - from) / DAY_MS).coerceAtLeast(0)

    /** Desde cuándo se cuenta: el último respaldo; si nunca hubo, la instalación. */
    fun since(lastBackupAt: Long, firstInstallAt: Long): Long =
        if (lastBackupAt > 0L) lastBackupAt else firstInstallAt

    fun isDue(lastBackupAt: Long, firstInstallAt: Long, now: Long): Boolean =
        daysBetween(since(lastBackupAt, firstInstallAt), now) >= INTERVAL_DAYS

    /** Texto de la tarjeta; `null` mientras todavía no toca. */
    fun label(lastBackupAt: Long, firstInstallAt: Long, now: Long): String? {
        if (!isDue(lastBackupAt, firstInstallAt, now)) return null
        val days = daysBetween(since(lastBackupAt, firstInstallAt), now)
        return if (lastBackupAt > 0L) {
            "Hace $days días del último respaldo. Si este teléfono se pierde, ese archivo es lo " +
                "único que devuelve el historial."
        } else {
            "Nunca has creado un respaldo. La base vive solo en este teléfono: si se pierde o se " +
                "borran los datos, no hay de dónde recuperarla."
        }
    }
}
