package co.pilot.android.report

import co.pilot.android.ai.AiResponse
import co.pilot.android.dsl.Instruction
import co.pilot.android.dsl.Route
import co.pilot.android.dsl.Step
import timber.log.Timber

class LogcatReporter : RouteReporter {

    companion object {
        private const val TAG = "Routes"
    }

    override fun onRouteStart(route: Route) {
        Timber.tag(TAG).i("=== Route: ${route.name} ===")
    }

    override fun onRouteEnd(route: Route, success: Boolean) {
        val status = if (success) "PASSED" else "FAILED"
        Timber.tag(TAG).i("=== Route: ${route.name} $status ===")
    }

    override fun onStepStart(step: Step) {
        Timber.tag(TAG).i("--- Step: ${step.name} ---")
    }

    override fun onStepEnd(step: Step, screenshotBase64: String?) {
        Timber.tag(TAG).i("--- Step complete: ${step.name} ---")
    }

    override fun onAction(instruction: Instruction, response: AiResponse.PerformAction, screenshotBase64: String?) {
        Timber.tag(TAG).d("Action: ${instruction.description}")
        Timber.tag(TAG).d("  -> ${response.action}")
        Timber.tag(TAG).d("  Reasoning: ${response.reasoning}")
    }

    override fun onVerify(instruction: Instruction, response: AiResponse.VerifyResult, screenshotBase64: String?) {
        val status = if (response.passed) "PASS" else "FAIL"
        Timber.tag(TAG).d("Verify [$status]: ${instruction.description}")
        Timber.tag(TAG).d("  Reasoning: ${response.reasoning}")
    }

    override fun onRemember(instruction: Instruction, response: AiResponse.RememberedValue) {
        Timber.tag(TAG).d("Remember: ${response.key}=${response.value}")
        Timber.tag(TAG).d("  Reasoning: ${response.reasoning}")
    }

    override fun onError(instruction: Instruction, error: Throwable, screenshotBase64: String?) {
        Timber.tag(TAG).e(error, "Error during: ${instruction.description}")
    }

    override fun generateReport() {
        // No-op for logcat reporter
    }
}
