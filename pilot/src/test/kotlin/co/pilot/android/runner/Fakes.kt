package co.pilot.android.runner

import co.pilot.android.ai.AiResponse
import co.pilot.android.ai.UiAction
import co.pilot.android.dsl.Instruction
import co.pilot.android.dsl.Route
import co.pilot.android.dsl.Step
import co.pilot.android.executor.ActionExecutable
import co.pilot.android.report.RouteReporter
import co.pilot.android.screen.ScreenCapture
import co.pilot.android.screen.ScreenState

data class ReporterEvent(val name: String, val screenshotBase64: String? = null)

class FakeScreenCapture(
    private val states: List<ScreenState> = listOf(ScreenState(emptyList())),
) : ScreenCapture {
    var captureCount = 0

    constructor(screenshotBase64: String) : this(listOf(ScreenState(emptyList(), screenshotBase64)))

    override fun captureScreenState(includeScreenshot: Boolean): ScreenState {
        val state = states.getOrElse(captureCount) { states.last() }
        captureCount++
        return state
    }
}

class FakeActionExecutable : ActionExecutable {
    val executedActions = mutableListOf<UiAction>()
    override fun execute(action: UiAction) {
        executedActions.add(action)
    }
}

class FakeReporter : RouteReporter {
    val events = mutableListOf<ReporterEvent>()

    override fun onRouteStart(route: Route) { events.add(ReporterEvent("routeStart:${route.name}")) }
    override fun onRouteEnd(
        route: Route,
        success: Boolean
    ) { events.add(ReporterEvent("routeEnd:${route.name}:$success")) }
    override fun onStepStart(step: Step) { events.add(ReporterEvent("stepStart:${step.name}")) }
    override fun onStepEnd(step: Step, screenshotBase64: String?) {
        events.add(ReporterEvent("stepEnd:${step.name}", screenshotBase64))
    }
    override fun onAction(instruction: Instruction, response: AiResponse.PerformAction, screenshotBase64: String?) {
        events.add(ReporterEvent("action:${instruction.description}", screenshotBase64))
    }
    override fun onVerify(instruction: Instruction, response: AiResponse.VerifyResult, screenshotBase64: String?) {
        events.add(ReporterEvent("verify:${instruction.description}:${response.passed}", screenshotBase64))
    }
    override fun onRemember(instruction: Instruction, response: AiResponse.RememberedValue) {
        events.add(ReporterEvent("remember:${response.key}=${response.value}"))
    }
    override fun onError(instruction: Instruction, error: Throwable, screenshotBase64: String?) {
        events.add(ReporterEvent("error:${instruction.description}", screenshotBase64))
    }
    override fun generateReport() { events.add(ReporterEvent("generateReport")) }
}
