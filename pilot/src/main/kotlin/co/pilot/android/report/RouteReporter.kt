package co.pilot.android.report

import co.pilot.android.ai.AiResponse
import co.pilot.android.dsl.Instruction
import co.pilot.android.dsl.Route
import co.pilot.android.dsl.Step

interface RouteReporter {
    fun onRouteStart(route: Route) {}
    fun onRouteEnd(route: Route, success: Boolean) {}
    fun onStepStart(step: Step) {}
    fun onStepEnd(step: Step, screenshotBase64: String?) {}
    fun onAction(instruction: Instruction, response: AiResponse.PerformAction, screenshotBase64: String?) {}
    fun onVerify(instruction: Instruction, response: AiResponse.VerifyResult, screenshotBase64: String?) {}
    fun onRemember(instruction: Instruction, response: AiResponse.RememberedValue) {}
    fun onError(instruction: Instruction, error: Throwable, screenshotBase64: String?) {}
    fun generateReport() {}
}
