package com.opentasker.core.contexts

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Current foreground-application observations, including transitions between packages.
 *
 * The initial snapshot is deliberately marked as "initial", not "opened": enabling the monitor
 * while an app is already visible should satisfy the foreground condition without pretending that
 * the app was just launched.
 */
object ApplicationContextEvents {
    private const val TYPE = "app"

    private val foregroundEvents = MutableSharedFlow<ContextEvent>(
        replay = 1,
        extraBufferCapacity = 16,
    )
    @Volatile private var latestForegroundPackage: String? = null
    @Volatile private var latestForegroundComponent: String? = null

    val events: Flow<ContextEvent> = flow {
        emit(
            ContextEvent(
                TYPE,
                matched = false,
                metadata = observationMetadata(
                    foreground = "",
                    component = "",
                    previousForeground = "",
                    transition = "unavailable",
                ),
            ),
        )
        emitAll(foregroundEvents.asSharedFlow())
    }

    fun publishForeground(
        packageName: String,
        className: String? = null,
        initial: Boolean = false,
    ): Boolean {
        val normalized = packageName.trim()
        if (normalized.isBlank()) return false
        val normalizedComponent = className?.trim().orEmpty()
        val previous = if (initial) "" else latestForegroundPackage.orEmpty()
        val transition = when {
            initial -> "initial"
            previous == normalized -> "activity"
            else -> "opened"
        }

        latestForegroundPackage = normalized
        latestForegroundComponent = normalizedComponent.ifBlank { null }
        return foregroundEvents.tryEmit(
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
    fun publishBackground(initial: Boolean = false): Boolean {
        val previous = if (initial) "" else latestForegroundPackage.orEmpty()
        if (!initial && previous.isBlank()) return false

        latestForegroundPackage = null
        latestForegroundComponent = null
        return foregroundEvents.tryEmit(
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
