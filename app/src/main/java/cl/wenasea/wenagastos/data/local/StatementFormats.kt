package cl.wenasea.wenagastos.data.local

import android.content.Context
import cl.wenasea.wenagastos.data.local.entity.Institution
import cl.wenasea.wenagastos.importexport.StatementTemplate

/**
 * La plantilla de cartola que se recuerda por banco: la primera importación la deduce del archivo y
 * las siguientes la reusan, para que la lectura del mes no dependa de que la heurística acierte
 * otra vez.
 */
class StatementFormats(private val context: Context) {

    fun templateFor(institution: Institution): StatementTemplate? =
        Prefs.statementTemplate(context, institution)?.let(StatementTemplate::fromJson)

    fun remember(institution: Institution, template: StatementTemplate) {
        Prefs.setStatementTemplate(context, institution, template.toJson())
    }
}
