package cl.wenasea.wenagastos.capture

import cl.wenasea.wenagastos.capture.fakes.FakeAccountDao
import cl.wenasea.wenagastos.capture.fakes.FakeCaptureLogDao
import cl.wenasea.wenagastos.capture.fakes.FakeCategoryDao
import cl.wenasea.wenagastos.capture.fakes.FakeTransactionDao
import cl.wenasea.wenagastos.data.Seeder
import cl.wenasea.wenagastos.data.local.entity.AccountEntity
import cl.wenasea.wenagastos.data.local.entity.AccountKind
import cl.wenasea.wenagastos.data.local.entity.CaptureLogEntity
import cl.wenasea.wenagastos.data.local.entity.CaptureOutcome
import cl.wenasea.wenagastos.data.local.entity.Direction
import cl.wenasea.wenagastos.data.local.entity.Institution
import cl.wenasea.wenagastos.data.local.entity.TransactionSource
import cl.wenasea.wenagastos.data.local.entity.TransactionStatus
import cl.wenasea.wenagastos.data.repo.AccountRepository
import cl.wenasea.wenagastos.data.repo.CaptureLogRepository
import cl.wenasea.wenagastos.data.repo.CategoryRepository
import cl.wenasea.wenagastos.data.repo.TransactionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas del motor completo: notificación → parser → cuenta → categoría → Room.
 *
 * Usa los repositorios reales sobre DAO en memoria, así cubre también la deduplicación
 * por hash, la ventana de ±5 minutos y el traspaso Google Wallet → banco, que no se
 * pueden provocar con `adb shell cmd notification post`.
 */
class CaptureEngineTest {

    private lateinit var accounts: FakeAccountDao
    private lateinit var categories: FakeCategoryDao
    private lateinit var txDao: FakeTransactionDao
    private lateinit var logDao: FakeCaptureLogDao

    private var ctaCteId = 0L
    private var debitoId = 0L
    private var supermercadoId = 0L
    private var restaurantesId = 0L
    private var otrosIngresosId = 0L

    private lateinit var engine: CaptureEngine

    private val baseTime = 1_700_000_000_000L

    private val compraDebito =
        "Compraste \$23.450 en JUMBO COSTA con tu tarjeta de débito terminada en 4321"

    @Before
    fun setUp() {
        accounts = FakeAccountDao()
        categories = FakeCategoryDao()
        txDao = FakeTransactionDao()
        logDao = FakeCaptureLogDao()

        val ids = accounts.seed(
            AccountEntity(
                name = "Santander Cta. Cte.",
                institution = Institution.SANTANDER,
                kind = AccountKind.CHECKING,
                colorHex = "#EC0000",
            ),
            AccountEntity(
                name = "Santander Débito",
                institution = Institution.SANTANDER,
                kind = AccountKind.DEBIT,
                last4 = "4321",
                colorHex = "#EC0000",
            ),
            AccountEntity(
                name = "Falabella CMR",
                institution = Institution.FALABELLA,
                kind = AccountKind.CREDIT,
                colorHex = "#7CB342",
            ),
        )
        ctaCteId = ids[0]
        debitoId = ids[1]

        categories.seedAll(Seeder.defaultCategories())
        supermercadoId = categories.stored.first { it.name == "Supermercado" }.id
        restaurantesId = categories.stored.first { it.name == "Restaurantes" }.id
        otrosIngresosId = categories.stored.first { it.name == "Otros ingresos" }.id

        engine = CaptureEngine(
            TransactionRepository(txDao),
            AccountRepository(accounts),
            CategoryRepository(categories),
            CaptureLogRepository(logDao),
        )
    }

    private fun notif(
        packageName: String = SANTANDER,
        title: String = "Santander",
        text: String,
        at: Long = baseTime,
    ) = RawNotification(
        packageName = packageName,
        title = title,
        text = text,
        postedAt = at,
        key = "$packageName|$at",
    )

    @Test
    fun `una compra con la tarjeta asociada queda confirmada y categorizada`() {
        runBlocking {
            assertEquals(CaptureOutcome.SAVED, engine.handle(notif(text = compraDebito)))

            val row = txDao.stored.single()
            assertEquals(TransactionStatus.CONFIRMED, row.status)
            assertEquals(TransactionSource.NOTIFICATION, row.source)
            assertEquals(23_450L, row.amount)
            assertEquals(Direction.DEBIT, row.direction)
            assertEquals(debitoId, row.accountId)
            assertEquals("JUMBO COSTA", row.merchantRaw)
            assertEquals(supermercadoId, row.categoryId)
            assertTrue(row.isExpense)
            assertNull(row.notes)
            assertNotNull(row.dedupeHash)
        }
    }

    @Test
    fun `si los ultimos 4 no estan asociados queda por revisar con la cuenta de la institucion`() {
        runBlocking {
            val outcome = engine.handle(
                notif(
                    text = "Compraste \$23.450 en JUMBO COSTA con tu tarjeta de débito terminada en 9999",
                ),
            )
            assertEquals(CaptureOutcome.PENDING, outcome)

            val row = txDao.stored.single()
            assertEquals(TransactionStatus.PENDING, row.status)
            assertEquals(ctaCteId, row.accountId)
            assertEquals("Asigna el número *9999 a una cuenta. Revisa y confirma.", row.notes)
        }
    }

    @Test
    fun `una cuenta con dos numeros reconoce la tarjeta y el numero de cuenta`() {
        runBlocking {
            accounts.update(accounts.getById(debitoId)!!.copy(last4 = "4321,5678"))

            // Compra con la tarjeta de débito: el primer número de la lista.
            assertEquals(CaptureOutcome.SAVED, engine.handle(notif(text = compraDebito)))

            // Transferencia desde la cuenta: el segundo número de la misma lista.
            assertEquals(
                CaptureOutcome.SAVED,
                engine.handle(
                    notif(
                        text = "Transferiste \$50.000 a MARIA SOTO desde tu cuenta terminada en 5678",
                        at = baseTime + 60_000,
                    ),
                ),
            )

            assertEquals(2, txDao.stored.size)
            assertTrue(txDao.stored.all { it.accountId == debitoId })
            assertTrue(txDao.stored.all { it.status == TransactionStatus.CONFIRMED })
            assertTrue(txDao.stored.all { it.notes == null })
        }
    }

    @Test
    fun `un numero parecido no completa el movimiento`() {
        runBlocking {
            accounts.update(accounts.getById(debitoId)!!.copy(last4 = "4321,5678"))

            val outcome = engine.handle(
                notif(
                    text = "Compraste \$3.000 en JUMBO COSTA con tu tarjeta de débito terminada en 8765",
                ),
            )

            assertEquals(CaptureOutcome.PENDING, outcome)
            val row = txDao.stored.single()
            assertEquals(ctaCteId, row.accountId)
            assertEquals("Asigna el número *8765 a una cuenta. Revisa y confirma.", row.notes)
        }
    }

    @Test
    fun `si la fuente no dice la cuenta cae en la primera con una nota`() {
        runBlocking {
            val outcome = engine.handle(
                notif(
                    packageName = "com.android.shell",
                    title = "Compra",
                    text = "Compraste \$8.990 en STARBUCKS COSTA",
                ),
            )
            assertEquals(CaptureOutcome.PENDING, outcome)

            val row = txDao.stored.single()
            assertEquals(ctaCteId, row.accountId)
            assertEquals("No supe con qué cuenta se pagó. Revisa y confirma.", row.notes)
            assertEquals(restaurantesId, row.categoryId)
        }
    }

    @Test
    fun `si no se puede leer el comercio queda por revisar`() {
        runBlocking {
            val outcome = engine.handle(
                notif(text = "Compraste \$5.000 con tu tarjeta de débito terminada en 4321"),
            )
            assertEquals(CaptureOutcome.PENDING, outcome)

            val row = txDao.stored.single()
            assertEquals(TransactionStatus.PENDING, row.status)
            assertEquals("Movimiento Santander", row.merchantRaw)
            assertEquals("No pude leer el comercio. Revisa y confirma.", row.notes)
        }
    }

    @Test
    fun `el mismo aviso dos veces no duplica el movimiento`() {
        runBlocking {
            assertEquals(CaptureOutcome.SAVED, engine.handle(notif(text = compraDebito)))
            assertEquals(CaptureOutcome.DUPLICATE, engine.handle(notif(text = compraDebito)))

            assertEquals(1, txDao.stored.size)
        }
    }

    @Test
    fun `otro banco con el mismo monto en la misma ventana se descarta`() {
        runBlocking {
            assertEquals(CaptureOutcome.SAVED, engine.handle(notif(text = compraDebito)))

            val outcome = engine.handle(
                notif(
                    packageName = FALABELLA,
                    title = "Falabella",
                    text = "Compraste \$23.450 en JUMBO COSTA",
                    at = baseTime + 60_000,
                ),
            )
            assertEquals(CaptureOutcome.DUPLICATE, outcome)
            assertEquals(1, txDao.stored.size)
            assertEquals("Mismo monto que un movimiento ya registrado", logDao.stored.last().detail)
        }
    }

    @Test
    fun `Google Wallet primero y el banco despues completa el mismo movimiento`() {
        runBlocking {
            val wallet = engine.handle(
                notif(packageName = WALLET, title = "Google Wallet", text = avisoWallet),
            )
            assertEquals(CaptureOutcome.PENDING, wallet)

            val pendiente = txDao.stored.single()
            assertEquals(TransactionSource.WALLET, pendiente.source)
            assertEquals(TransactionStatus.PENDING, pendiente.status)
            assertEquals(ctaCteId, pendiente.accountId)
            assertEquals(supermercadoId, pendiente.categoryId)
            assertEquals(baseTime, pendiente.occurredAt)

            val banco = engine.handle(notif(text = avisoBanco, at = baseTime + 60_000))
            assertEquals(CaptureOutcome.MERGED, banco)

            val merged = txDao.stored.single()
            assertEquals(debitoId, merged.accountId)
            assertEquals(TransactionStatus.CONFIRMED, merged.status)
            assertEquals(TransactionSource.NOTIFICATION, merged.source)
            assertEquals("LIDER EXPRESS", merged.merchantRaw)
            assertEquals(supermercadoId, merged.categoryId)
            assertEquals(baseTime, merged.occurredAt)
            assertEquals(
                "Completé el movimiento que había quedado de Google Wallet",
                logDao.stored.last().detail,
            )
        }
    }

    @Test
    fun `si el banco llega primero, Wallet despues no pisa el movimiento`() {
        runBlocking {
            assertEquals(CaptureOutcome.SAVED, engine.handle(notif(text = avisoBanco)))

            val wallet = engine.handle(
                notif(
                    packageName = WALLET,
                    title = "Google Wallet",
                    text = avisoWallet,
                    at = baseTime + 60_000,
                ),
            )
            assertEquals(CaptureOutcome.DUPLICATE, wallet)

            val row = txDao.stored.single()
            assertEquals(TransactionSource.NOTIFICATION, row.source)
            assertEquals(debitoId, row.accountId)
            assertEquals(TransactionStatus.CONFIRMED, row.status)
        }
    }

    @Test
    fun `un paquete que no leemos se ignora sin dejar registro`() {
        runBlocking {
            val outcome = engine.handle(
                notif(packageName = "com.whatsapp", title = "Mamá", text = "Compraste pan"),
            )
            assertEquals(CaptureOutcome.IGNORED, outcome)
            assertTrue(txDao.stored.isEmpty())
            assertTrue(logDao.stored.isEmpty())
        }
    }

    @Test
    fun `un resumen vacio no ensucia el registro de diagnostico`() {
        runBlocking {
            val outcome = engine.handle(notif(title = "", text = ""))
            assertEquals(CaptureOutcome.IGNORED, outcome)
            assertTrue(txDao.stored.isEmpty())
            assertTrue(logDao.stored.isEmpty())
        }
    }

    @Test
    fun `un aviso de saldo se rechaza con el motivo correcto`() {
        runBlocking {
            val outcome = engine.handle(notif(text = "Tu saldo disponible es \$1.250.000"))
            assertEquals(CaptureOutcome.REJECTED, outcome)
            assertTrue(txDao.stored.isEmpty())

            val log = logDao.stored.single()
            assertEquals(CaptureOutcome.REJECTED, log.outcome)
            assertEquals("Es un saldo, no un movimiento", log.detail)
        }
    }

    @Test
    fun `el registro de captura se recorta a las ultimas filas`() {
        runBlocking {
            val repo = CaptureLogRepository(logDao)
            repeat(250) { i ->
                repo.add(
                    CaptureLogEntity(
                        packageName = SANTANDER,
                        sourceLabel = "Santander",
                        title = "Santander",
                        text = "aviso $i",
                        postedAt = baseTime + i,
                        outcome = CaptureOutcome.SAVED,
                    ),
                )
            }

            assertEquals(CaptureLogEntity.MAX_ROWS, logDao.stored.size)
            assertEquals(51L, logDao.stored.minOf { it.id })
        }
    }

    @Test
    fun `una transferencia recibida entra como ingreso y no como gasto`() {
        runBlocking {
            val outcome = engine.handle(
                notif(text = "Recibiste una transferencia de JUAN PEREZ por \$25.000"),
            )
            assertEquals(CaptureOutcome.SAVED, outcome)

            val row = txDao.stored.single()
            assertEquals(Direction.CREDIT, row.direction)
            assertEquals(25_000L, row.amount)
            assertEquals("JUAN PEREZ", row.merchantRaw)
            assertEquals(otrosIngresosId, row.categoryId)
            assertEquals(false, row.isExpense)
            assertEquals(TransactionStatus.CONFIRMED, row.status)
        }
    }

    private companion object {
        const val SANTANDER = "cl.santander.smartphone"
        const val FALABELLA = "com.falabella.falabellaApp"
        const val WALLET = "com.google.android.apps.walletnfcrel"

        const val avisoWallet = "You paid CLP 12,500.00 at LIDER EXPRESS with Visa ••••8888"
        const val avisoBanco =
            "Compraste \$12.500 en LIDER EXPRESS con tu tarjeta de débito terminada en 4321"
    }
}
