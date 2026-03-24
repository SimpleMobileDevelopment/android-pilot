package co.pilot.sample

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import co.pilot.android.ai.AiRequest
import co.pilot.android.ai.AiResponse
import co.pilot.android.ai.InstructionType
import co.pilot.android.ai.MockBackend
import co.pilot.android.ai.UiAction
import co.pilot.android.runner.RouteTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class PilotDslRouteTest {

    private val composeTestRule = createAndroidComposeRule<SampleActivity>()

    private val mockBackend = MockBackend { request ->
        when (request.instructionType) {
            InstructionType.VERIFY -> AiResponse.VerifyResult(
                passed = true,
                reasoning = "Mock: verified",
            )
            InstructionType.REMEMBER -> AiResponse.RememberedValue(
                key = "mock",
                value = "mockValue",
                reasoning = "Mock: remembered",
            )
            InstructionType.ACTION -> decideAction(request)
        }
    }

    private val routeRule = RouteTestRule(composeTestRule, mockBackend)

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(composeTestRule).around(routeRule)

    @Test
    fun loginRoute() = runTest {
        routeRule.runRoute("Login via DSL") {
            step("Verify login screen") {
                verify("A login screen is visible with username and password fields")
            }
            step("Enter credentials") {
                action("Tap the username input field")
                action("Type 'testuser' into the username field")
                action("Tap the password input field")
                action("Type 'password123' into the password field")
                action("Tap the 'Log In' button")
            }
            step("Verify list screen") {
                verify("A list of items is visible on screen")
            }
        }
    }
}

private fun decideAction(request: AiRequest): AiResponse.PerformAction {
    val instruction = request.instruction.lowercase()
    val action = when {
        instruction.contains("tap") && instruction.contains("username") ->
            UiAction.Tap(testTag = "username_field")

        instruction.contains("type") && instruction.contains("username") ->
            UiAction.TypeText(testTag = "username_field", text = extractQuotedText(request.instruction))

        instruction.contains("tap") && instruction.contains("password") ->
            UiAction.Tap(testTag = "password_field")

        instruction.contains("type") && instruction.contains("password") ->
            UiAction.TypeText(testTag = "password_field", text = extractQuotedText(request.instruction))

        instruction.contains("tap") && instruction.contains("log in") ->
            UiAction.Tap(testTag = "login_button")

        instruction.contains("scroll") && instruction.contains("down") ->
            UiAction.Scroll(direction = co.pilot.android.ai.Direction.DOWN, testTag = "item_list")

        instruction.contains("tap") && instruction.contains("first") && instruction.contains("item") ->
            UiAction.Tap(testTag = "item_0")

        instruction.contains("tap") && instruction.contains("favorite") ->
            UiAction.Tap(testTag = "favorite_toggle")

        instruction.contains("tap") && instruction.contains("delete") ->
            UiAction.Tap(testTag = "delete_button")

        instruction.contains("tap") && instruction.contains("confirm") ->
            UiAction.Tap(testTag = "confirm_delete")

        instruction.contains("tap") && instruction.contains("cancel") ->
            UiAction.Tap(testTag = "cancel_delete")

        instruction.contains("tap") && instruction.contains("back") ->
            UiAction.Tap(testTag = "back_button")

        else -> UiAction.Tap(text = extractQuotedText(request.instruction))
    }
    return AiResponse.PerformAction(action = action, reasoning = "Mock: $instruction")
}

private fun extractQuotedText(instruction: String): String {
    val regex = "'([^']*)'".toRegex()
    return regex.find(instruction)?.groupValues?.get(1) ?: instruction
}
