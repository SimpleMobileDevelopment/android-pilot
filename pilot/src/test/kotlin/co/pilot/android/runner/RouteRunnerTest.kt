package co.pilot.android.runner

import co.pilot.android.ai.AiResponse
import co.pilot.android.ai.InstructionType
import co.pilot.android.ai.MockBackend
import co.pilot.android.ai.UiAction
import co.pilot.android.dsl.route
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RouteRunnerTest {

    @Test
    fun `happy path with all instruction types`() = runTest {
        val screenCapture = FakeScreenCapture()
        val actionExecutable = FakeActionExecutable()
        val reporter = FakeReporter()
        val backend = MockBackend { request ->
            when (request.instructionType) {
                InstructionType.ACTION -> AiResponse.PerformAction(
                    action = UiAction.Tap(text = "Login"),
                    reasoning = "tapping login",
                )
                InstructionType.VERIFY -> AiResponse.VerifyResult(
                    passed = true,
                    reasoning = "home screen visible",
                )
                InstructionType.REMEMBER -> AiResponse.RememberedValue(
                    key = "user",
                    value = "john",
                    reasoning = "found username",
                )
            }
        }

        val testRoute = route("login flow") {
            step("login") {
                action("tap login")
                verify("screen shows home")
                remember("user", "the username")
            }
        }

        val runner = RouteRunner(screenCapture, backend, actionExecutable, reporter)
        runner.run(testRoute)

        assertEquals(1, actionExecutable.executedActions.size)
        assertEquals(UiAction.Tap(text = "Login"), actionExecutable.executedActions[0])
        assertTrue(reporter.events.any { it.name == "action:tap login" })
        assertTrue(reporter.events.any { it.name == "verify:screen shows home:true" })
        assertTrue(reporter.events.any { it.name == "remember:user=john" })
        assertEquals("generateReport", reporter.events.last().name)
    }

    @Test
    fun `verification failure throws AssertionError`() = runTest {
        val reporter = FakeReporter()
        val backend = MockBackend {
            AiResponse.VerifyResult(passed = false, reasoning = "not found")
        }

        val testRoute = route("verify flow") {
            step("check") {
                verify("element is visible")
            }
        }

        val runner = RouteRunner(FakeScreenCapture(), backend, FakeActionExecutable(), reporter)
        assertFailsWith<AssertionError> {
            runner.run(testRoute)
        }

        assertTrue(reporter.events.any { it.name == "verify:element is visible:false" })
        assertTrue(reporter.events.any { it.name == "routeEnd:verify flow:false" })
        assertTrue(reporter.events.any { it.name == "generateReport" })
    }

    @Test
    fun `multiple steps execute in order`() = runTest {
        val reporter = FakeReporter()
        val backend = MockBackend {
            AiResponse.PerformAction(action = UiAction.Tap(text = "btn"), reasoning = "tap")
        }

        val testRoute = route("multi step") {
            step("step1") { action("action1") }
            step("step2") { action("action2") }
            step("step3") { action("action3") }
        }

        val runner = RouteRunner(FakeScreenCapture(), backend, FakeActionExecutable(), reporter)
        runner.run(testRoute)

        val stepEvents = reporter.events
            .map { it.name }
            .filter { it.startsWith("stepStart:") || it.startsWith("stepEnd:") }
        assertEquals(
            listOf(
                "stepStart:step1",
                "stepEnd:step1",
                "stepStart:step2",
                "stepEnd:step2",
                "stepStart:step3",
                "stepEnd:step3"
            ),
            stepEvents,
        )
    }

    @Test
    fun `remembered values resolved in subsequent instructions`() = runTest {
        val reporter = FakeReporter()
        var callCount = 0
        val backend = MockBackend { request ->
            callCount++
            when {
                request.instructionType == InstructionType.REMEMBER -> AiResponse.RememberedValue(
                    key = "goal",
                    value = "strength",
                    reasoning = "extracted goal",
                )
                else -> AiResponse.PerformAction(
                    action = UiAction.Tap(text = "ok"),
                    reasoning = "tapping",
                )
            }
        }

        val testRoute = route("remember flow") {
            step("extract") {
                remember("goal", "extract goal")
            }
            step("use") {
                action("verify {goal} is shown")
            }
        }

        val runner = RouteRunner(FakeScreenCapture(), backend, FakeActionExecutable(), reporter)
        runner.run(testRoute)

        val lastRequest = backend.requestHistory.last()
        assertEquals("verify strength is shown", lastRequest.instruction)
    }

    @Test
    fun `error in middle step stops execution`() = runTest {
        val reporter = FakeReporter()
        var callCount = 0
        val backend = MockBackend {
            callCount++
            if (callCount == 2) {
                AiResponse.VerifyResult(passed = false, reasoning = "failed")
            } else {
                AiResponse.PerformAction(action = UiAction.Tap(text = "btn"), reasoning = "tap")
            }
        }

        val testRoute = route("fail flow") {
            step("step1") { action("action1") }
            step("step2") { verify("check something") }
            step("step3") { action("action3") }
        }

        val runner = RouteRunner(FakeScreenCapture(), backend, FakeActionExecutable(), reporter)
        assertFailsWith<AssertionError> {
            runner.run(testRoute)
        }

        assertEquals(2, backend.requestHistory.size)
        assertTrue(reporter.events.any { it.name == "routeEnd:fail flow:false" })
    }

    @Test
    fun `empty route throws IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            route("empty") {}
        }
    }

    @Test
    fun `screen capture called for every instruction`() = runTest {
        val screenCapture = FakeScreenCapture()
        val backend = MockBackend {
            AiResponse.PerformAction(action = UiAction.Tap(text = "btn"), reasoning = "tap")
        }

        val testRoute = route("capture flow") {
            step("step1") {
                action("action1")
                action("action2")
            }
            step("step2") {
                action("action3")
                action("action4")
            }
        }

        val runner = RouteRunner(screenCapture, backend, FakeActionExecutable(), FakeReporter())
        runner.run(testRoute)

        assertTrue(screenCapture.captureCount >= 4)
    }

    @Test
    fun `generateReport called even on failure`() = runTest {
        val reporter = FakeReporter()
        val backend = MockBackend {
            throw RuntimeException("backend error")
        }

        val testRoute = route("error flow") {
            step("step1") { action("do something") }
        }

        val runner = RouteRunner(FakeScreenCapture(), backend, FakeActionExecutable(), reporter)
        assertFailsWith<RuntimeException> {
            runner.run(testRoute)
        }

        assertTrue(reporter.events.any { it.name == "generateReport" })
    }
}
