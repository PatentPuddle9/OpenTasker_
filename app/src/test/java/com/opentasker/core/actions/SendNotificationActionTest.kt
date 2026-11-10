package com.opentasker.core.actions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SendNotificationActionTest {
    private fun source(relativePath: String): String =
        listOf(File("src/main/java/$relativePath"), File("app/src/main/java/$relativePath"))
            .first { it.exists() }
            .readText()

    @Test
    fun standardNotificationDoesNotRequireOrSetProgress() {
        val builtIns = source("com/opentasker/core/actions/BuiltInActions.kt")
        val action = builtIns
            .substringAfter("class SendNotificationAction")
            .substringBefore("class ProgressNotificationAction")

        assertTrue(action.contains("NotificationCompat.Builder"))
        assertTrue(action.contains("NotificationManagerCompat.from(ctx.app).notify(tag, id, builder.build())"))
        assertFalse(action.contains(".setProgress("))
        assertFalse(action.contains("parseProgress("))
    }

    @Test
    fun standardNotificationChecksPermissionAndKeepsProgressActionSeparate() {
        val builtIns = source("com/opentasker/core/actions/BuiltInActions.kt")
        val action = builtIns
            .substringAfter("class SendNotificationAction")
            .substringBefore("class ProgressNotificationAction")

        assertTrue(action.contains("Manifest.permission.POST_NOTIFICATIONS"))
        assertTrue(action.contains("setOngoing(ongoing)"))
        assertTrue(builtIns.substringAfter("class ProgressNotificationAction").contains(".setProgress(100, progress, false)"))
    }
}
