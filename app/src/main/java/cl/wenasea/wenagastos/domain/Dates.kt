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

    fun toLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun today(): LocalDate = LocalDate.now(zone)

    fun todayMillis(): Long = startOfDay(today())

    fun dayLabel(epochMillis: Long): String = dayFormatter.format(toLocalDate(epochMillis))

    fun fullDateLabel(epochMillis: Long): String =
        toLocalDate(epochMillis).format(DateTimeFormatter.ofPattern("dd-MM-yyyy", locale))

    /** El DatePicker de Material 3 trabaja con medianoche UTC: hay que traducir ambos sentidos. */
    fun toPickerMillis(epochMillis: Long): Long =
        toLocalDate(epochMillis).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun fromPickerMillis(utcMillis: Long): Long =
        Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate().let(::startOfDay)
}
