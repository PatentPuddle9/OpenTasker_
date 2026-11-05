package com.opentasker.core.contexts

import android.content.Context
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Application context source backed by the system's foreground-app observations.
 *
 * Open/close application conditions are pulse contexts. This source exposes a subscription-ready
 * handshake so AutomationService does not start its fallback monitor before the matcher is
 * actually listening for those pulses.
 */
class ApplicationContextSourceImpl : SubscriptionReadyContextSource {
    override val type = "app"

    override fun events(app: Context, onSubscribed: () -> Unit): Flow<ContextEvent> = channelFlow {
        val collectors = listOf(
            ApplicationContextEvents.events,
            PackageContextEvents.events.map { event -> event.copy(type = type) },
        ).map { upstream ->
            launch(start = CoroutineStart.UNDISPATCHED) {
                upstream.collect { event -> send(event) }
            }
        }
        // Both undispatched collectors are subscribed before the producer is allowed to start.
        // Package monitoring is demand-gated by ContextMonitorLifecycle for install/update specs.
        onSubscribed()
        awaitClose { collectors.forEach { it.cancel() } }
    }
}
