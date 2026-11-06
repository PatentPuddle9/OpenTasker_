package com.opentasker.core.engine

import android.content.ContextWrapper
import com.opentasker.core.actions.ConfirmDeviceCredentialAction
import com.opentasker.core.actions.FALLBACK_ACTION_BLOCK_ARGUMENT
import com.opentasker.core.model.ActionSpec
import com.opentasker.core.model.Task
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class TaskRunnerFailureRecoveryTest {

    private var calls = 0
    private var fallbackBlockCalls = 0
    private var afterFallbackCalls = 0

    @Before
    fun setUp() {
        calls = 0
        fallbackBlockCalls = 0
        afterFallbackCalls = 0
        ActionRegistry.register(object : Action {
            override val id = "test.flow.flaky"
            override val category = ActionCategory.FLOW
            override val retrySafety = ActionRetrySafety.IDEMPOTENT

            override suspend fun run(ctx: ActionContext, args: Map<String, String>): ActionResult {
                calls++
                return if (calls <= args["failures"].orEmpty().toInt()) {
                    ActionResult.Failure("transient failure")
                } else {
                    ActionResult.Success
                }
            }
        })
        ActionRegistry.register(object : Action {
            override val id = "test.flow.fail"
            override val category = ActionCategory.FLOW
            override val retrySafety = ActionRetrySafety.NEVER
            override suspend fun run(ctx: ActionContext, args: Map<String, String>): ActionResult =
                ActionResult.Failure("permanent failure")
        })
        ActionRegistry.register(object : Action {
            override val id = "test.signin.fallback"
            override val category = ActionCategory.NOTIFICATION
            override suspend fun run(ctx: ActionContext, args: Map<String, String>): ActionResult {
                fallbackBlockCalls++
                return ActionResult.Success
            }
        })
        ActionRegistry.register(object : Action {
            override val id = "test.signin.after"
            override val category = ActionCategory.FLOW
            override suspend fun run(ctx: ActionContext, args: Map<String, String>): ActionResult {
                afterFallbackCalls++
                return ActionResult.Success
            }
        })
    }

    @After
    fun restoreSignInAction() {
        ActionRegistry.register(ConfirmDeviceCredentialAction())
    }

    private fun run(vararg actions: ActionSpec): TaskRunReport = runBlocking {
        TaskRunner(ActionContext(ContextWrapper(null), VariableStore())).run(
            Task(name = "recovery", actions = actions.toList()),
        )
    }

    private fun marker(type: String, args: Map<String, String> = emptyMap()) =
        ActionSpec(type = type, args = args)

    @Test
    fun retriesIdempotentActionAndSkipsCatchAfterSuccess() {
        val report = run(
            marker(FlowControl.TRY, mapOf("max_attempts" to "3")),
            ActionSpec(type = "test.flow.flaky", args = mapOf("failures" to "2")),
            marker(FlowControl.CATCH),
            marker(FlowControl.ENDTRY),
        )

        assertTrue(report.success)
        assertEquals(3, calls)
        assertTrue(report.traces.any { it.message.contains("catch skipped") })
    }

    @Test
    fun catchesNonRetryableFailureAndExposesDetails() {
        val seen = mutableListOf<String>()
        ActionRegistry.register(object : Action {
            override val id = "test.flow.observe"
            override val category = ActionCategory.FLOW
            override val retrySafety = ActionRetrySafety.IDEMPOTENT
            override suspend fun run(ctx: ActionContext, args: Map<String, String>): ActionResult {
                seen += args["value"].orEmpty()
                return ActionResult.Success
            }
        })
        val report = run(
            marker(FlowControl.TRY, mapOf("max_attempts" to "3")),
            marker("test.flow.fail"),
            marker(FlowControl.CATCH),
            ActionSpec(type = "test.flow.observe", args = mapOf("value" to "%FLOW_ERROR_ACTION:%FLOW_ERROR_ATTEMPT")),
            marker(FlowControl.ENDTRY),
        )

        assertTrue(report.success)
        assertEquals(listOf("test.flow.fail:1"), seen)
        assertTrue(report.traces.any { it.message.contains("classified NEVER") })
    }

    @Test
    fun exhaustedRetryIsCaught() {
        val report = run(
            marker(FlowControl.TRY, mapOf("max_attempts" to "2")),
            ActionSpec(type = "test.flow.flaky", args = mapOf("failures" to "9")),
            marker(FlowControl.CATCH),
            marker(FlowControl.ENDTRY),
        )

        assertTrue(report.success)
        assertEquals(2, calls)
        assertEquals(2, report.traces.count { it.actionType == "test.flow.flaky" && it.status == ActionTraceStatus.FAILURE })
        assertTrue(report.traces.any { it.message.contains("exhausted the configured attempts") })
    }

    @Test
    fun uncaughtFailureStillFailsTask() {
        val variables = VariableStore()
        val report = runBlocking {
            TaskRunner(
                ActionContext(ContextWrapper(null), variables),
                originatingProfileId = 41L,
                originatingProfileName = "Work profile",
            ).run(
                Task(
                    id = 19L,
                    name = "Broken task",
                    actions = listOf(
                        ActionSpec(id = 77L, type = "test.flow.fail"),
                    ),
                ),
            )
        }

        assertFalse(report.success)
        assertNotNull(report.structuredError)
        val error = report.structuredError!!
        assertEquals(19L, error.taskId)
        assertEquals(77L, error.actionId)
        assertEquals(1, error.actionIndex)
        assertEquals("test.flow.fail", error.actionType)
        assertEquals("permanent failure", error.message)
        assertEquals(1, error.attemptCount)
        assertEquals(41L, error.originatingProfileId)
        assertEquals("Work profile", error.originatingProfileName)
        assertTrue(variables.globalSnapshot()[TaskFailureVariables.JSON].orEmpty().contains("test.flow.fail"))
    }

    @Test
    fun signInFailureRunsTheSelectedActionBlockAndStopsTheOriginalSequence() {
        ActionRegistry.register(object : Action {
            override val id = "auth.device_credential"
            override val category = ActionCategory.SYSTEM
            override suspend fun run(ctx: ActionContext, args: Map<String, String>): ActionResult =
                ActionResult.Failure(
                    "Sign-in was not completed",
                    fallbackActionSpecId = args["fallback_action_spec_id"]?.toLongOrNull(),
                )
        })
        val report = runBlocking {
            TaskRunner(ActionContext(ContextWrapper(null), VariableStore())).run(
                Task(
                    name = "Sign In fallback",
                    actions = listOf(
                        ActionSpec(
                            id = 11,
                            type = "auth.device_credential",
                            args = mapOf("fallback_action_spec_id" to "22"),
                        ),
                        ActionSpec(id = 22, type = "test.signin.fallback"),
                        ActionSpec(id = 33, type = "test.signin.after"),
                    ),
                ),
            )
        }

        assertTrue(report.success)
        assertEquals(1, fallbackBlockCalls)
        assertEquals(0, afterFallbackCalls)
        assertTrue(report.traces.single().message.contains("fallback action block"))
    }

    @Test
    fun anyActionCanRunItsConfiguredFallbackBlockAndStopTheOriginalSequence() {
        ActionRegistry.register(object : Action {
            override val id = "test.action.with.fallback"
            override val category = ActionCategory.FLOW
            override suspend fun run(ctx: ActionContext, args: Map<String, String>): ActionResult =
                ActionResult.Failure("The action failed")
        })

        val report = runBlocking {
            TaskRunner(ActionContext(ContextWrapper(null), VariableStore())).run(
                Task(
                    name = "Generic action fallback",
                    actions = listOf(
                        ActionSpec(
                            id = 11,
                            type = "test.action.with.fallback",
                            args = mapOf(FALLBACK_ACTION_BLOCK_ARGUMENT to "22"),
                            waitForCompletion = false,
                        ),
                        ActionSpec(id = 22, type = "test.signin.fallback"),
                        ActionSpec(id = 33, type = "test.signin.after"),
                    ),
                ),
            )
        }

        assertTrue(report.success)
        assertEquals(1, fallbackBlockCalls)
        assertEquals(0, afterFallbackCalls)
        assertTrue(report.traces.single().message.contains("fallback action block"))
    }

    @Test
    fun signInFallbackRejectsMissingOrNonActionBlockTargets() {
        ActionRegistry.register(object : Action {
            override val id = "auth.device_credential"
            override val category = ActionCategory.SYSTEM
            override suspend fun run(ctx: ActionContext, args: Map<String, String>): ActionResult =
                ActionResult.Failure("Sign-in was not completed", fallbackActionSpecId = 999L)
        })
        val report = runBlocking {
            TaskRunner(ActionContext(ContextWrapper(null), VariableStore())).run(
                Task(
                    name = "Missing fallback action",
                    actions = listOf(
                        ActionSpec(id = 11, type = "auth.device_credential"),
                        ActionSpec(id = 22, type = "test.signin.fallback"),
                    ),
                ),
            )
        }

        assertFalse(report.success)
        assertTrue(report.traces.single().message.contains("could not be used"))
        assertEquals(0, fallbackBlockCalls)
    }

    @Test
    fun signInFallbackCannotLaunchAnotherTopLevelSetupThroughTaskRun() {
        ActionRegistry.register(object : Action {
            override val id = "auth.device_credential"
            override val category = ActionCategory.SYSTEM
            override suspend fun run(ctx: ActionContext, args: Map<String, String>): ActionResult =
                ActionResult.Failure("Sign-in was not completed", fallbackActionSpecId = 22L)
        })

        val report = runBlocking {
            TaskRunner(ActionContext(ContextWrapper(null), VariableStore())).run(
                Task(
                    name = "Fallback cannot launch setup",
                    actions = listOf(
                        ActionSpec(id = 11, type = "auth.device_credential"),
                        ActionSpec(id = 22, type = SUB_TASK_ACTION_ID, args = mapOf("task" to "42")),
                    ),
                ),
            )
        }

        assertFalse(report.success)
        assertTrue(report.traces.single().message.contains("could not be used"))
        assertEquals(0, fallbackBlockCalls)
    }

    @Test
    fun retrySuccessClearsRetryingFlag() {
        val variables = VariableStore()
        val report = runBlocking {
            TaskRunner(ActionContext(ContextWrapper(null), variables)).run(
                Task(
                    name = "Eventually healthy",
                    actions = listOf(
                        marker(FlowControl.TRY, mapOf("max_attempts" to "2")),
                        ActionSpec(type = "test.flow.flaky", args = mapOf("failures" to "1")),
                        marker(FlowControl.CATCH),
                        marker(FlowControl.ENDTRY),
                    ),
                ),
            )
        }

        assertTrue(report.success)
        assertEquals("false", variables.globalSnapshot()[TaskFailureVariables.RETRYING])
        assertEquals("", variables.globalSnapshot()[TaskFailureVariables.RETRY_REASON])
    }
}

/**
 * A retry restarts the whole try body, so retry safety has to be judged across every action in it,
 * and entering a catch handler has to record that the failure was caught.
 */
class TaskRunnerRetryBodySafetyTest {

    private var sideEffects = 0
    private var flakyCalls = 0

    @Before
    fun setUp() {
        sideEffects = 0
        flakyCalls = 0
        ActionRegistry.register(object : Action {
            override val id = "test.body.sideeffect"
            override val category = ActionCategory.FLOW
            override val retrySafety = ActionRetrySafety.NEVER
            override suspend fun run(ctx: ActionContext, args: Map<String, String>): ActionResult {
                sideEffects++
                return ActionResult.Success
            }
        })
        ActionRegistry.register(object : Action {
            override val id = "test.body.flaky"
            override val category = ActionCategory.FLOW
            override val retrySafety = ActionRetrySafety.IDEMPOTENT
            override suspend fun run(ctx: ActionContext, args: Map<String, String>): ActionResult {
                flakyCalls++
                return ActionResult.Failure("transient failure")
            }
        })
    }

    private fun runWith(variables: VariableStore, vararg actions: ActionSpec): TaskRunReport = runBlocking {
        TaskRunner(ActionContext(ContextWrapper(null), variables)).run(
            Task(name = "retry-body", actions = actions.toList()),
        )
    }

    private fun marker(type: String, args: Map<String, String> = emptyMap()) =
        ActionSpec(type = type, args = args)

    @Test
    fun aNonRetrySafeActionInTheBodyPreventsReplayingTheWholeBody() {
        // Body is [NEVER-safe side effect, IDEMPOTENT failure]. Retrying restarts at the top, so
        // the side effect would run again for every attempt.
        runWith(
            VariableStore(),
            marker(FlowControl.TRY, mapOf("max_attempts" to "3")),
            ActionSpec(type = "test.body.sideeffect"),
            ActionSpec(type = "test.body.flaky"),
            marker(FlowControl.CATCH),
            marker(FlowControl.ENDTRY),
        )

        assertEquals("the non-retry-safe action must run exactly once", 1, sideEffects)
        assertEquals("the body must not be replayed", 1, flakyCalls)
    }

    @Test
    fun anAllIdempotentBodyStillRetries() {
        runWith(
            VariableStore(),
            marker(FlowControl.TRY, mapOf("max_attempts" to "3")),
            ActionSpec(type = "test.body.flaky"),
            marker(FlowControl.CATCH),
            marker(FlowControl.ENDTRY),
        )

        assertEquals(3, flakyCalls)
    }

    @Test
    fun flowErrorCaughtIsTrueInsideTheCatchHandler() {
        val variables = VariableStore()
        runWith(
            variables,
            marker(FlowControl.TRY),
            ActionSpec(type = "test.body.flaky"),
            marker(FlowControl.CATCH),
            ActionSpec(type = "test.body.sideeffect"),
            marker(FlowControl.ENDTRY),
        )

        assertEquals("the handler must run", 1, sideEffects)
        assertEquals("true", variables.get(TaskFailureVariables.CAUGHT))
    }
}
