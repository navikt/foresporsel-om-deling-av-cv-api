package sendforespørsel

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.provider.jdbc.JdbcLockProvider
import utils.log
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.sql.DataSource
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.timerTask

class UsendtScheduler(dataSource: DataSource, sendUsendteForespørsler: () -> Any) {

    private val lockProvider = JdbcLockProvider(dataSource)
    private val lockingExecutor = DefaultLockingTaskExecutor(lockProvider)

    private val runnableMedLås: TimerTask.() -> Unit = {
        lockingExecutor.executeWithLock(
            sendUsendteMedFeilhåndtering,
            LockConfiguration(Instant.now(),"retry-lock", Duration.ofMinutes(10), Duration.ofMillis(0L))
        )
    }

    private val sendUsendteMedFeilhåndtering = {
        try {
            sendUsendteForespørsler()
        } catch (error: Exception) {
            log.error("Det skjedde en feil i UsendtScheduler:", error)
        }
    }

    fun kjørEnGang() {
        timerTask(runnableMedLås).run()
    }

    fun kjørPeriodisk() {
        fixedRateTimer(
            name = "Send usendte forespørsler",
            period = Duration.ofSeconds(60).toMillis(),
            action = runnableMedLås,
            initialDelay = Duration.ofSeconds(60).toMillis()
        )
    }
}
