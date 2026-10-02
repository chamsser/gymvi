package io.github.chamsser.gymvi.ai

import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Drops expired conversations on a fixed schedule. Requests only check expiry when they arrive, so
 * without this the last conversations would stay in memory past their TTL whenever traffic stops.
 * Under normal execution, the next sweep drops a conversation after its TTL. The fixed delay is
 * not a hard wall-clock deadline during process pauses, a long sweep or a failed cleanup. The
 * application context closes the sweeper on shutdown, which ends its thread.
 */
internal class AiConversationSweeper(
    private val removeExpired: () -> Int,
    interval: Duration = DEFAULT_INTERVAL,
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "gymvi-ai-conversation-sweeper").apply { isDaemon = true }
    },
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(AiConversationSweeper::class.java)
    private val finishedSweeps = AtomicLong()

    init {
        require(!interval.isNegative && !interval.isZero)
        executor.scheduleWithFixedDelay(::sweep, interval.toNanos(), interval.toNanos(), TimeUnit.NANOSECONDS)
    }

    /** Sweeps finished so far, failed ones included, so tests can wait on sweeps rather than on time. */
    internal val sweeps: Long get() = finishedSweeps.get()

    internal val isTerminated: Boolean get() = executor.isTerminated

    private fun sweep() {
        try {
            removeExpired()
        } catch (error: RuntimeException) {
            // A scheduled task that throws never runs again, which would quietly end the TTL.
            log.warn("ai_conversation_sweep_failed type={}", error.javaClass.simpleName)
        } finally {
            finishedSweeps.incrementAndGet()
        }
    }

    override fun close() {
        executor.shutdownNow()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }

    companion object {
        val DEFAULT_INTERVAL: Duration = Duration.ofMinutes(1)
    }
}
