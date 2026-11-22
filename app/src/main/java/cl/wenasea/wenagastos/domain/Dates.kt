package cl.wenasea.wenagastos.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Fechas en hora de Chile central. */
object Dates {

    val zone: ZoneId = ZoneId.of("America/Santiago")
    private val locale = Locale.forLanguageTag("es-CL")
    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    private val dayFormatter = DateTimeFormatter.ofPattern("EEE d MMM", locale)

    fun currentMonth(): YearMonth = YearMonth.now(zone)

    fun monthStart(yearMonth: YearMonth): Long =
        yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun monthEndExclusive(yearMonth: YearMonth): Long =
        yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun monthLabel(yearMonth: YearMonth): String =
        monthFormatter.format(yearMonth).replaceFirstChar { it.uppercase(locale) }

    /** "nov" para las etiquetas de los gráficos. */
    fun shortMonthLabel(yearMonth: YearMonth): String =
        DateTimeFormatter.ofPattern("MMM", locale).format(yearMonth).trimEnd('.')

    fun toLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun today(): LocalDate = LocalDate.now(zone)

    fun todayMillis(): Long = startOfDay(today())

    fun dayLabel(epochMillis: Long): String = dayFormatter.format(toLocalDate(epochMillis))

    fun timeLabel(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone)
            .format(DateTimeFormatter.ofPattern("dd-MM HH:mm", locale))

    fun fullDateLabel(epochMillis: Long): String =
        toLocalDate(epochMillis).format(DateTimeFormatter.ofPattern("dd-MM-yyyy", locale))

    /** "25-11": día y mes de una fecha sin año, para los ciclos de facturación. */
    fun shortDateLabel(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("dd-MM", locale))

    /** "2026-11-20": ISO, para que la planilla ordene solo y no confunda día con mes. */
    fun isoDate(epochMillis: Long): String = toLocalDate(epochMillis).toString()

    /** "10:22": hora de Chile, sin segundos. */
    fun clockLabel(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
        .atZone(zone)
        .format(DateTimeFormatter.ofPattern("HH:mm", locale))

    /** "2026-11-20T10:22:33-03:00": marca de tiempo completa, para los archivos exportados. */
    fun isoDateTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis)
        .atZone(zone)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    /** El DatePicker de Material 3 trabaja con medianoche UTC: hay que traducir ambos sentidos. */
    fun toPickerMillis(epochMillis: Long): Long =
        toLocalDate(epochMillis).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerMillis(utcMillis: Long): Long =
        Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate().let(::startOfDay)
}
