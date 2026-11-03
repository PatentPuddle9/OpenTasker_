package com.opentasker.core.engine

import android.content.ContextWrapper
import com.opentasker.core.model.ActionSpec
import com.opentasker.core.model.Task
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskRunnerWaitForCompletionTest {

    private val slowActionFinished = AtomicBoolean(false)
    private val nextActionObservedFinish = AtomicBoolean(true)

    @Before
    fun setUp() {
        slowActionFinished.set(false)
        nextActionObservedFinish.set(true)

        ActionRegistry.register(object : Action {
            override val id = "test.wait.slow"
            override val category = ActionCategory.SYSTEM

            override suspend fun run(ctx: ActionContext, args: Map<String, String>): ActionResult {
                delay(150)
                slowActionFinished.set(true)
                return ActionResult.Success
            }
        })
        ActionRegistry.register(object : Action {
            override val id = "test.wait.observe"
            override val category = ActionCategory.SYSTEM

            override suspend fun run(ctx: ActionContext, args: Map<String, String>): ActionResult {
                nextActionObservedFinish.set(slowActionFinished.get())
                return ActionResult.Success
            }
        })
    }

    private fun runTask(vararg actions: ActionSpec): TaskRunReport = runBlocking {
        TaskRunner(ActionContext(ContextWrapper(null), VariableStore())).run(
            Task(name = "Wait for action test", actions = actions.toList()),
        )
    }

    @Test
    fun actionsWaitByDefault() {
        val report = runTask(
            ActionSpec(type = "test.wait.slow"),
            ActionSpec(type = "test.wait.observe"),
        )

        assertTrue(report.success)
        assertTrue(slowActionFinished.get())
        assertTrue("the next action should start after the previous action finishes", nextActionObservedFinish.get())
        assertEquals(2, report.results.size)
        assertEquals(2, report.traces.size)
    }

    @Test
    fun disablingWaitAllowsNextActionToStartBeforeCurrentActionFinishes() {
        val report = runTask(
            ActionSpec(type = "test.wait.slow", waitForCompletion = false),
            ActionSpec(type = "test.wait.observe"),
        )

        assertTrue(report.success)
        assertFalse("the next action should overlap the first action", nextActionObservedFinish.get())
        assertTrue("the task must still join the first action before completing", slowActionFinished.get())
        assertEquals(2, report.results.size)
        assertEquals(2, report.traces.size)
        assertEquals(
            listOf("test.wait.slow", "test.wait.observe"),
            report.traces.map { it.actionType },
        )
    }

    @Test
    fun waitForCompletionDefaultsToTrueForExistingActionSpecs() {
        assertTrue(ActionSpec(type = "test.wait.slow").waitForCompletion)
    }
}
