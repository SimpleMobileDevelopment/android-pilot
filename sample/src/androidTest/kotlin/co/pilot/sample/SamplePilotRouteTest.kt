package co.pilot.sample

import co.pilot.android.ai.AiBackend
import co.pilot.android.ai.AiRequest
import co.pilot.android.ai.AiResponse
import co.pilot.android.ai.Direction
import co.pilot.android.ai.InstructionType
import co.pilot.android.ai.MockBackend
import co.pilot.android.ai.UiAction
import co.pilot.android.runner.PilotRouteTest
import org.junit.Test

/**
 * Demonstrates the simplified PilotRouteTest base class.
 * Compare with PilotYamlRouteTest to see how much boilerplate is eliminated.
 */
class SamplePilotRouteTest : PilotRouteTest<SampleActivity>(SampleActivity::class.java) {

    override fun createBackend(): AiBackend = MockBackend { request ->
        when (request.instructionType) {
            InstructionType.VERIFY -> AiResponse.VerifyResult(
                passed = true,
                reasoning = "Mock: verified",
            )
            InstructionType.REMEMBER -> AiResponse.RememberedValue(
                key = "selectedItem",
                value = "Item 1",
                reasoning = "Mock: remembered",
            )
            InstructionType.ACTION -> mapAction(request)
        }
    }

    @Test
    fun loginFlow() = runYamlRoute("login-flow.yaml")

    @Test
    fun browseItems() = runYamlRoute("browse-items-and-view-detail.yaml")
}

private fun mapAction(request: AiRequest): AiResponse.PerformAction {
    val instruction = request.instruction.lowercase()
    val action = when {
        instruction.contains("tap") && instruction.contains("username") ->
            UiAction.Tap(testTag = "username_field")

        instruction.contains("type") && instruction.contains("username") -> {
            val text = "'([^']*)'".toRegex().find(request.instruction)?.groupValues?.get(1) ?: ""
            UiAction.TypeText(testTag = "username_field", text = text)
        }

        instruction.contains("tap") && instruction.contains("password") ->
            UiAction.Tap(testTag = "password_field")

        instruction.contains("type") && instruction.contains("password") -> {
            val text = "'([^']*)'".toRegex().find(request.instruction)?.groupValues?.get(1) ?: ""
            UiAction.TypeText(testTag = "password_field", text = text)
        }

        instruction.contains("tap") && (instruction.contains("log in") || instruction.contains("continue")) ->
            UiAction.Tap(testTag = "login_button")

        instruction.contains("scroll") && instruction.contains("down") ->
            UiAction.Scroll(direction = Direction.DOWN, testTag = "item_list")

        instruction.contains("tap") && instruction.contains("first") && instruction.contains("item") ->
            UiAction.Tap(testTag = "item_0")

        instruction.contains("tap") && instruction.contains("back") ->
            UiAction.Tap(testTag = "back_button")

        instruction.contains("tap") && instruction.contains("favorite") ->
            UiAction.Tap(testTag = "favorite_toggle")

        else -> UiAction.Tap(
            text = "'([^']*)'".toRegex().find(request.instruction)?.groupValues?.get(1) ?: "",
        )
    }
    return AiResponse.PerformAction(action = action, reasoning = "Mock: $instruction")
}
