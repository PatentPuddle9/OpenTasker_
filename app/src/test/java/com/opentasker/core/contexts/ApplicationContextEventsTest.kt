package com.opentasker.core.contexts

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationContextEventsTest {
    @Test
    fun blankForegroundPackageIsIgnored() {
        assertFalse(ApplicationContextEvents.publishForeground(" "))
    }

    @Test
    fun initialForegroundSnapshotDoesNotPretendTheAppWasJustOpened() = runBlocking {
        // Seed the previous-package state; event pulses themselves are intentionally not replayed.
        ApplicationContextEvents.publishForeground("com.example.seed", initial = true)
        val nextEvent = async(start = CoroutineStart.UNDISPATCHED) {
            ApplicationContextEvents.events.drop(1).first()
        }

        assertTrue(ApplicationContextEvents.publishForeground("com.example.app", initial = true))

        val event = nextEvent.await()
        assertEquals("initial", event.metadata["transition"])
        assertEquals("", event.metadata["previous_foreground"])
        assertEquals("com.example.app", event.metadata["foreground"])
    }

    @Test
    fun appSwitchPublishesPreviousAndCurrentPackages() = runBlocking {
        ApplicationContextEvents.publishForeground("com.example.previous", initial = true)
        val nextEvent = async(start = CoroutineStart.UNDISPATCHED) {
            ApplicationContextEvents.events.drop(1).first()
        }

        assertTrue(ApplicationContextEvents.publishForeground("com.example.next"))

        val event = nextEvent.await()
        assertEquals("opened", event.metadata["transition"])
        assertEquals("com.example.next", event.metadata["foreground"])
        assertEquals("com.example.previous", event.metadata["previous_foreground"])
    }

    @Test
    fun appOpenPulseFromBeforeSubscriptionIsNotReplayedToANewMatcher() = runBlocking {
        ApplicationContextEvents.publishForeground("com.example.previous", initial = true)
        ApplicationContextEvents.publishForeground("com.example.already.open")

        val nextEvent = async(start = CoroutineStart.UNDISPATCHED) {
            ApplicationContextEvents.events.drop(1).first()
        }
        ApplicationContextEvents.publishForeground("com.example.just.opened")

        val event = nextEvent.await()
        assertEquals("opened", event.metadata["transition"])
        assertEquals("com.example.just.opened", event.metadata["foreground"])
        assertEquals("com.example.already.open", event.metadata["previous_foreground"])
    }

    @Test
    fun leavingAllApplicationsPublishesAClosedTransition() = runBlocking {
        ApplicationContextEvents.publishForeground("com.example.app", initial = true)
        val nextEvent = async(start = CoroutineStart.UNDISPATCHED) {
            ApplicationContextEvents.events.drop(1).first()
        }

        assertTrue(ApplicationContextEvents.publishBackground())

        val event = nextEvent.await()
        assertEquals("closed", event.metadata["transition"])
        assertEquals("", event.metadata["foreground"])
        assertEquals("com.example.app", event.metadata["previous_foreground"])
    }
}
