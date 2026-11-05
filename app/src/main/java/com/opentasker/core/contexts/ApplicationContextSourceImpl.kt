package com.opentasker.core.contexts

import android.content.Context
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
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
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            ApplicationContextEvents.events.collect { event -> send(event) }
        }
        // The undispatched collector has subscribed to the shared event stream before the
        // producer is allowed to start.
        onSubscribed()
        awaitClose { collector.cancel() }
    }
}
