package co.pilot.android.report

import co.pilot.android.ai.AiResponse
import co.pilot.android.dsl.Instruction
import co.pilot.android.dsl.Route
import co.pilot.android.dsl.Step
import timber.log.Timber

class CompositeReporter(private val reporters: List<RouteReporter>) : RouteReporter {

    constructor(vararg reporters: RouteReporter) : this(reporters.toList())

    private inline fun forEachSafe(methodName: String, action: (RouteReporter) -> Unit) {
        reporters.forEach { reporter ->
            try {
                action(reporter)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Reporter ${reporter::class.simpleName} failed in $methodName")
            }
        }
    }

    override fun onRouteStart(route: Route) {
        forEachSafe("onRouteStart") { it.onRouteStart(route) }
    }

    override fun onRouteEnd(route: Route, success: Boolean) {
        forEachSafe("onRouteEnd") { it.onRouteEnd(route, success) }
    }

    override fun onStepStart(step: Step) {
        forEachSafe("onStepStart") { it.onStepStart(step) }
    }

    override fun onStepEnd(step: Step, screenshotBase64: String?) {
        forEachSafe("onStepEnd") { it.onStepEnd(step, screenshotBase64) }
    }

    override fun onAction(
        instruction: Instruction,
        response: AiResponse.PerformAction,
        screenshotBase64: String?,
    ) {
        forEachSafe("onAction") { it.onAction(instruction, response, screenshotBase64) }
    }

    override fun onVerify(
        instruction: Instruction,
        response: AiResponse.VerifyResult,
        screenshotBase64: String?,
    ) {
        forEachSafe("onVerify") { it.onVerify(instruction, response, screenshotBase64) }
    }

    override fun onRemember(instruction: Instruction, response: AiResponse.RememberedValue) {
        forEachSafe("onRemember") { it.onRemember(instruction, response) }
    }

    override fun onError(instruction: Instruction, error: Throwable, screenshotBase64: String?) {
        forEachSafe("onError") { it.onError(instruction, error, screenshotBase64) }
    }

    override fun generateReport() {
        forEachSafe("generateReport") { it.generateReport() }
    }

    companion object {
        private const val TAG = "CompositeReporter"
    }
}
