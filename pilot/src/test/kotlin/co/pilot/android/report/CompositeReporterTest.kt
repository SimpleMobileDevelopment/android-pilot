package co.pilot.android.report

import co.pilot.android.ai.AiResponse
import co.pilot.android.ai.UiAction
import co.pilot.android.dsl.Instruction
import co.pilot.android.dsl.Route
import co.pilot.android.dsl.Step
import co.pilot.android.runner.FakeReporter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompositeReporterTest {

    private val testRoute = Route(
        name = "Test Route",
        steps = listOf(
            Step(
                name = "Test Step",
                instructions = listOf(Instruction.Action("tap button")),
            ),
        ),
    )

    private val testStep = testRoute.steps[0]
    private val testInstruction = testStep.instructions[0]

    private val actionResponse = AiResponse.PerformAction(
        action = UiAction.Tap(text = "button"),
        reasoning = "tapping the button",
    )

    private val verifyResponse = AiResponse.VerifyResult(
        passed = true,
        reasoning = "element is visible",
    )

    private val rememberResponse = AiResponse.RememberedValue(
        key = "value_key",
        value = "stored_value",
        reasoning = "found the value",
    )

    // ---------------------------------------------------------------------------
    // onRouteStart
    // ---------------------------------------------------------------------------

    @Test
    fun `onRouteStart delegates to all reporters`() {
        val reporter1 = FakeReporter()
        val reporter2 = FakeReporter()
        val composite = CompositeReporter(reporter1, reporter2)

        composite.onRouteStart(testRoute)

        assertTrue(reporter1.events.any { it.name == "routeStart:Test Route" })
        assertTrue(reporter2.events.any { it.name == "routeStart:Test Route" })
    }

    @Test
    fun `onRouteStart exception in first reporter does not prevent second reporter from being called`() {
        val throwingReporter = ThrowingReporter()
        val safeReporter = FakeReporter()
        val composite = CompositeReporter(throwingReporter, safeReporter)

        composite.onRouteStart(testRoute)

        assertTrue(safeReporter.events.any { it.name == "routeStart:Test Route" })
    }

    // ---------------------------------------------------------------------------
    // onRouteEnd
    // ---------------------------------------------------------------------------

    @Test
    fun `onRouteEnd delegates to all reporters`() {
        val reporter1 = FakeReporter()
        val reporter2 = FakeReporter()
        val composite = CompositeReporter(reporter1, reporter2)

        composite.onRouteEnd(testRoute, success = true)

        assertTrue(reporter1.events.any { it.name == "routeEnd:Test Route:true" })
        assertTrue(reporter2.events.any { it.name == "routeEnd:Test Route:true" })
    }

    @Test
    fun `onRouteEnd exception in first reporter does not prevent second reporter from being called`() {
        val throwingReporter = ThrowingReporter()
        val safeReporter = FakeReporter()
        val composite = CompositeReporter(throwingReporter, safeReporter)

        composite.onRouteEnd(testRoute, success = false)

        assertTrue(safeReporter.events.any { it.name == "routeEnd:Test Route:false" })
    }

    // ---------------------------------------------------------------------------
    // onStepStart
    // ---------------------------------------------------------------------------

    @Test
    fun `onStepStart delegates to all reporters`() {
        val reporter1 = FakeReporter()
        val reporter2 = FakeReporter()
        val composite = CompositeReporter(reporter1, reporter2)

        composite.onStepStart(testStep)

        assertTrue(reporter1.events.any { it.name == "stepStart:Test Step" })
        assertTrue(reporter2.events.any { it.name == "stepStart:Test Step" })
    }

    @Test
    fun `onStepStart exception in first reporter does not prevent second reporter from being called`() {
        val throwingReporter = ThrowingReporter()
        val safeReporter = FakeReporter()
        val composite = CompositeReporter(throwingReporter, safeReporter)

        composite.onStepStart(testStep)

        assertTrue(safeReporter.events.any { it.name == "stepStart:Test Step" })
    }

    // ---------------------------------------------------------------------------
    // onStepEnd
    // ---------------------------------------------------------------------------

    @Test
    fun `onStepEnd delegates to all reporters`() {
        val reporter1 = FakeReporter()
        val reporter2 = FakeReporter()
        val composite = CompositeReporter(reporter1, reporter2)

        composite.onStepEnd(testStep, screenshotBase64 = "abc123")

        assertTrue(reporter1.events.any { it.name == "stepEnd:Test Step" })
        assertTrue(reporter2.events.any { it.name == "stepEnd:Test Step" })
    }

    @Test
    fun `onStepEnd passes screenshot to all reporters`() {
        val reporter1 = FakeReporter()
        val reporter2 = FakeReporter()
        val composite = CompositeReporter(reporter1, reporter2)

        composite.onStepEnd(testStep, screenshotBase64 = "screenshot_data")

        val event1 = reporter1.events.first { it.name == "stepEnd:Test Step" }
        val event2 = reporter2.events.first { it.name == "stepEnd:Test Step" }
        assertEquals("screenshot_data", event1.screenshotBase64)
        assertEquals("screenshot_data", event2.screenshotBase64)
    }

    @Test
    fun `onStepEnd exception in first reporter does not prevent second reporter from being called`() {
        val throwingReporter = ThrowingReporter()
        val safeReporter = FakeReporter()
        val composite = CompositeReporter(throwingReporter, safeReporter)

        composite.onStepEnd(testStep, screenshotBase64 = null)

        assertTrue(safeReporter.events.any { it.name == "stepEnd:Test Step" })
    }

    // ---------------------------------------------------------------------------
    // onAction
    // ---------------------------------------------------------------------------

    @Test
    fun `onAction delegates to all reporters`() {
        val reporter1 = FakeReporter()
        val reporter2 = FakeReporter()
        val composite = CompositeReporter(reporter1, reporter2)

        composite.onAction(testInstruction, actionResponse, screenshotBase64 = null)

        assertTrue(reporter1.events.any { it.name == "action:tap button" })
        assertTrue(reporter2.events.any { it.name == "action:tap button" })
    }

    @Test
    fun `onAction exception in first reporter does not prevent second reporter from being called`() {
        val throwingReporter = ThrowingReporter()
        val safeReporter = FakeReporter()
        val composite = CompositeReporter(throwingReporter, safeReporter)

        composite.onAction(testInstruction, actionResponse, screenshotBase64 = null)

        assertTrue(safeReporter.events.any { it.name == "action:tap button" })
    }

    // ---------------------------------------------------------------------------
    // onVerify
    // ---------------------------------------------------------------------------

    @Test
    fun `onVerify delegates to all reporters`() {
        val reporter1 = FakeReporter()
        val reporter2 = FakeReporter()
        val composite = CompositeReporter(reporter1, reporter2)
        val verifyInstruction = Instruction.Verify("element is present")

        composite.onVerify(verifyInstruction, verifyResponse, screenshotBase64 = null)

        assertTrue(reporter1.events.any { it.name == "verify:element is present:true" })
        assertTrue(reporter2.events.any { it.name == "verify:element is present:true" })
    }

    @Test
    fun `onVerify exception in first reporter does not prevent second reporter from being called`() {
        val throwingReporter = ThrowingReporter()
        val safeReporter = FakeReporter()
        val composite = CompositeReporter(throwingReporter, safeReporter)
        val verifyInstruction = Instruction.Verify("element is present")

        composite.onVerify(verifyInstruction, verifyResponse, screenshotBase64 = null)

        assertTrue(safeReporter.events.any { it.name == "verify:element is present:true" })
    }

    // ---------------------------------------------------------------------------
    // onRemember
    // ---------------------------------------------------------------------------

    @Test
    fun `onRemember delegates to all reporters`() {
        val reporter1 = FakeReporter()
        val reporter2 = FakeReporter()
        val composite = CompositeReporter(reporter1, reporter2)
        val rememberInstruction = Instruction.Remember(key = "value_key", description = "the value")

        composite.onRemember(rememberInstruction, rememberResponse)

        assertTrue(reporter1.events.any { it.name == "remember:value_key=stored_value" })
        assertTrue(reporter2.events.any { it.name == "remember:value_key=stored_value" })
    }

    @Test
    fun `onRemember exception in first reporter does not prevent second reporter from being called`() {
        val throwingReporter = ThrowingReporter()
        val safeReporter = FakeReporter()
        val composite = CompositeReporter(throwingReporter, safeReporter)
        val rememberInstruction = Instruction.Remember(key = "value_key", description = "the value")

        composite.onRemember(rememberInstruction, rememberResponse)

        assertTrue(safeReporter.events.any { it.name == "remember:value_key=stored_value" })
    }

    // ---------------------------------------------------------------------------
    // onError
    // ---------------------------------------------------------------------------

    @Test
    fun `onError delegates to all reporters`() {
        val reporter1 = FakeReporter()
        val reporter2 = FakeReporter()
        val composite = CompositeReporter(reporter1, reporter2)
        val error = RuntimeException("something went wrong")

        composite.onError(testInstruction, error, screenshotBase64 = null)

        assertTrue(reporter1.events.any { it.name == "error:tap button" })
        assertTrue(reporter2.events.any { it.name == "error:tap button" })
    }

    @Test
    fun `onError exception in first reporter does not prevent second reporter from being called`() {
        val throwingReporter = ThrowingReporter()
        val safeReporter = FakeReporter()
        val composite = CompositeReporter(throwingReporter, safeReporter)
        val error = RuntimeException("something went wrong")

        composite.onError(testInstruction, error, screenshotBase64 = null)

        assertTrue(safeReporter.events.any { it.name == "error:tap button" })
    }

    // ---------------------------------------------------------------------------
    // generateReport
    // ---------------------------------------------------------------------------

    @Test
    fun `generateReport delegates to all reporters`() {
        val reporter1 = FakeReporter()
        val reporter2 = FakeReporter()
        val composite = CompositeReporter(reporter1, reporter2)

        composite.generateReport()

        assertTrue(reporter1.events.any { it.name == "generateReport" })
        assertTrue(reporter2.events.any { it.name == "generateReport" })
    }

    @Test
    fun `generateReport exception in first reporter does not prevent second reporter from being called`() {
        val throwingReporter = ThrowingReporter()
        val safeReporter = FakeReporter()
        val composite = CompositeReporter(throwingReporter, safeReporter)

        composite.generateReport()

        assertTrue(safeReporter.events.any { it.name == "generateReport" })
    }

    // ---------------------------------------------------------------------------
    // Edge cases
    // ---------------------------------------------------------------------------

    @Test
    fun `empty reporter list works without errors for all methods`() {
        val composite = CompositeReporter(emptyList())

        composite.onRouteStart(testRoute)
        composite.onRouteEnd(testRoute, success = true)
        composite.onStepStart(testStep)
        composite.onStepEnd(testStep, screenshotBase64 = null)
        composite.onAction(testInstruction, actionResponse, screenshotBase64 = null)
        composite.onVerify(
            Instruction.Verify("check"),
            verifyResponse,
            screenshotBase64 = null,
        )
        composite.onRemember(
            Instruction.Remember(key = "k", description = "desc"),
            rememberResponse,
        )
        composite.onError(testInstruction, RuntimeException("err"), screenshotBase64 = null)
        composite.generateReport()
        // No exception thrown — test passes
    }

    @Test
    fun `multiple exceptions are isolated and all reporters still receive the event`() {
        val throwingReporter1 = ThrowingReporter()
        val throwingReporter2 = ThrowingReporter()
        val safeReporter = FakeReporter()
        val composite = CompositeReporter(throwingReporter1, throwingReporter2, safeReporter)

        composite.onRouteStart(testRoute)

        assertTrue(safeReporter.events.any { it.name == "routeStart:Test Route" })
    }

    // ---------------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------------

    /**
     * A reporter that always throws on every method, used to verify exception isolation.
     */
    private class ThrowingReporter : RouteReporter {
        override fun onRouteStart(route: Route) = throw RuntimeException("onRouteStart failed")
        override fun onRouteEnd(route: Route, success: Boolean) = throw RuntimeException("onRouteEnd failed")
        override fun onStepStart(step: Step) = throw RuntimeException("onStepStart failed")
        override fun onStepEnd(step: Step, screenshotBase64: String?) = throw RuntimeException("onStepEnd failed")
        override fun onAction(
            instruction: Instruction,
            response: AiResponse.PerformAction,
            screenshotBase64: String?,
        ) = throw RuntimeException("onAction failed")
        override fun onVerify(
            instruction: Instruction,
            response: AiResponse.VerifyResult,
            screenshotBase64: String?,
        ) = throw RuntimeException("onVerify failed")
        override fun onRemember(instruction: Instruction, response: AiResponse.RememberedValue) =
            throw RuntimeException("onRemember failed")
        override fun onError(instruction: Instruction, error: Throwable, screenshotBase64: String?) =
            throw RuntimeException("onError failed")
        override fun generateReport() = throw RuntimeException("generateReport failed")
    }
}
