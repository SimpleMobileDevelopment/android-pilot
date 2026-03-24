package co.pilot.sample

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import co.pilot.android.ai.AiRequest
import co.pilot.android.ai.AiResponse
import co.pilot.android.ai.Direction
import co.pilot.android.ai.InstructionType
import co.pilot.android.ai.MockBackend
import co.pilot.android.ai.UiAction
import co.pilot.android.runner.RouteTestRule
import co.pilot.android.yaml.YamlRouteLoader
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class PilotYamlRouteTest {

    private val composeTestRule = createAndroidComposeRule<SampleActivity>()

    private val mockBackend = MockBackend { request ->
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

    private val routeRule = RouteTestRule(composeTestRule, mockBackend)

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(composeTestRule).around(routeRule)

    @Test
    fun runLoginFlowFromYaml() = runTest(timeout = 30.seconds) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val routes = YamlRouteLoader.loadFromAssets(context.assets, "routes")
        val loginRoute = routes.first { it.name == "Login flow" }
        routeRule.runRoute(loginRoute)
    }

    @Test
    fun runBrowseItemsFromYaml() = runTest(timeout = 30.seconds) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val routes = YamlRouteLoader.loadFromAssets(context.assets, "routes")
        val browseRoute = routes.first { it.name == "Browse items and view detail" }
        routeRule.runRoute(browseRoute)
    }

    private fun runYamlRoute(filename: String) = runTest(timeout = 60.seconds) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val routes = YamlRouteLoader.loadFromAssets(context.assets, "routes")
        val route = routes.first { route ->
            route.name.trim().lowercase()
                .replace(Regex("[^a-z0-9\\s-]"), "")
                .replace(Regex("\\s+"), "-")
                .replace(Regex("-+"), "-")
                .trimStart('-').trimEnd('-')
                .plus(".yaml") == filename
        }
        routeRule.runRoute(route)
    }
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

        else -> UiAction.Tap(text = "'([^']*)'".toRegex().find(request.instruction)?.groupValues?.get(1) ?: "")
    }
    return AiResponse.PerformAction(action = action, reasoning = "Mock: $instruction")
}
