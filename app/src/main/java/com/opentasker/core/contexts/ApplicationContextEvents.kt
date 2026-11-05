package com.opentasker.core.contexts

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Current foreground-application observations, including transitions between packages.
 *
 * Every subscriber receives a fresh initial snapshot of the current package. Buffered live
 * transitions are versioned, so a subscriber never mistakes an older "opened" pulse for a new
 * launch, while events that arrive between taking the snapshot and subscribing are not lost.
 */
object ApplicationContextEvents {
    private const val TYPE = "app"

    private data class VersionedEvent(
        val revision: Long,
        val event: ContextEvent,
    )

    private data class Snapshot(
        val revision: Long,
        val event: ContextEvent,
    )

    private val stateLock = Any()
    private val foregroundEvents = MutableSharedFlow<VersionedEvent>(
        replay = 32,
        extraBufferCapacity = 32,
    )
    @Volatile private var latestForegroundPackage: String? = null
    @Volatile private var latestForegroundComponent: String? = null
    private var hasObservedForegroundState = false
    private var observationRevision = 0L

    val events: Flow<ContextEvent> = flow {
        val snapshot = synchronized(stateLock) {
            Snapshot(
                revision = observationRevision,
                event = initialSnapshotLocked(),
            )
        }
        emit(snapshot.event)
        emitAll(
            foregroundEvents.asSharedFlow()
                .filter { it.revision > snapshot.revision }
                .map { it.event },
        )
    }

    fun publishForeground(
        packageName: String,
        className: String? = null,
        initial: Boolean = false,
    ): Boolean = synchronized(stateLock) {
        val normalized = packageName.trim()
        if (normalized.isBlank()) return@synchronized false
        val normalizedComponent = className?.trim().orEmpty()
        val previous = if (initial) "" else latestForegroundPackage.orEmpty()
        val transition = when {
            initial -> "initial"
            previous == normalized -> "activity"
            else -> "opened"
        }

        latestForegroundPackage = normalized
        latestForegroundComponent = normalizedComponent.ifBlank { null }
        hasObservedForegroundState = true
        publishLocked(
            ContextEvent(
                type = TYPE,
                matched = true,
                metadata = observationMetadata(
                    foreground = normalized,
                    component = normalizedComponent,
                    previousForeground = previous,
                    transition = transition,
                ),
            ),
        )
    }

    /** Publish that no application Activity is currently resumed (for example, the lock screen). */
    fun publishBackground(initial: Boolean = false): Boolean = synchronized(stateLock) {
        val previous = if (initial) "" else latestForegroundPackage.orEmpty()
        if (!initial && previous.isBlank()) return@synchronized false

        latestForegroundPackage = null
        latestForegroundComponent = null
        hasObservedForegroundState = true
        publishLocked(
            ContextEvent(
                type = TYPE,
                matched = true,
                metadata = observationMetadata(
                    foreground = "",
                    component = "",
                    previousForeground = previous,
                    transition = if (initial) "initial" else "closed",
                ),
            ),
        )
    }

    /** Latest package shown by the Context Inspector, offered as an explicit editor shortcut. */
    fun latestObservedPackage(): String? = latestForegroundPackage

    /** Latest Activity class shown by the Context Inspector, when the OEM reported one. */
    fun latestObservedComponent(): String? = latestForegroundComponent

    private fun initialSnapshotLocked(): ContextEvent {
        if (!hasObservedForegroundState) {
            return ContextEvent(
                TYPE,
                matched = false,
                metadata = observationMetadata(
                    foreground = "",
                    component = "",
                    previousForeground = "",
                    transition = "unavailable",
                ),
            )
        }
        return ContextEvent(
            type = TYPE,
            matched = true,
            metadata = observationMetadata(
                foreground = latestForegroundPackage.orEmpty(),
                component = latestForegroundComponent.orEmpty(),
                previousForeground = "",
                transition = "initial",
            ),
        )
    }

    private fun publishLocked(event: ContextEvent): Boolean {
        observationRevision += 1L
        return foregroundEvents.tryEmit(VersionedEvent(observationRevision, event))
    }

    private fun observationMetadata(
        foreground: String,
        component: String,
        previousForeground: String,
        transition: String,
    ): Map<String, String> = mapOf(
        "foreground" to foreground,
        "component" to component,
        "component_status" to if (component.isBlank()) "unavailable" else "available",
        "previous_foreground" to previousForeground,
        "transition" to transition,
    )
}
