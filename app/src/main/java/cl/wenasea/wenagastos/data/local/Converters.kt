package cl.wenasea.wenagastos.data.local

import androidx.room.TypeConverter
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.CaptureOutcome
import cl.wenasea.wenagastos.data.local.entity.CategoryKind
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.Institution
import cl.wenasea.wenagastos.data.local.entity.TransactionSource
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus

class Converters {

    @TypeConverter
    fun institutionToString(value: Institution): String = value.name

    @TypeConverter
    fun stringToInstitution(value: String): Institution = Institution.valueOf(value)

    @TypeConverter
    fun accountKindToString(value: AccountKind): String = value.name

    @TypeConverter
    fun stringToAccountKind(value: String): AccountKind = AccountKind.valueOf(value)

    @TypeConverter
    fun directionToString(value: Direction): String = value.name

    @TypeConverter
    fun stringToDirection(value: String): Direction = Direction.valueOf(value)

    @TypeConverter
    fun categoryKindToString(value: CategoryKind): String = value.name

    @TypeConverter
    fun stringToCategoryKind(value: String): CategoryKind = CategoryKind.valueOf(value)

    @TypeConverter
    fun sourceToString(value: TransactionSource): String = value.name

    @TypeConverter
    fun stringToSource(value: String): TransactionSource = TransactionSource.valueOf(value)

    @TypeConverter
    fun statusToString(value: TransactionStatus): String = value.name

    @TypeConverter
    fun stringToStatus(value: String): TransactionStatus = TransactionStatus.valueOf(value)

    @TypeConverter
    fun outcomeToString(value: CaptureOutcome): String = value.name

    @TypeConverter
    fun stringToOutcome(value: String): CaptureOutcome = CaptureOutcome.valueOf(value)
}
