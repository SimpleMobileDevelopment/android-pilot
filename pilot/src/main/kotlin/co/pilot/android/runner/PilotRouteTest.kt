package co.pilot.android.runner

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import co.pilot.android.ai.AiBackend
import co.pilot.android.ai.AiResponse
import co.pilot.android.ai.InstructionType
import co.pilot.android.ai.MockBackend
import co.pilot.android.ai.UiAction
import co.pilot.android.dsl.RouteBuilder
import co.pilot.android.yaml.YamlRouteLoader
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.RuleChain
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Base class for Pilot route tests. Eliminates boilerplate by managing
 * ComposeTestRule, RouteTestRule, and RuleChain setup automatically.
 *
 * Usage:
 * ```kotlin
 * class MyRouteTest : PilotRouteTest<MainActivity>(MainActivity::class.java) {
 *     @Test fun loginFlow() = runYamlRoute("login-flow.yaml")
 * }
 * ```
 *
 * Override [createBackend] to provide a custom AI backend (e.g. MockBackend
 * with specific mock responses, or a real ClaudeBackend for live testing).
 */
abstract class PilotRouteTest<A : ComponentActivity>(
    activityClass: Class<A>,
    private val routeTimeout: Duration = 60.seconds,
    private val assetsPath: String = "routes",
) {
    protected val composeTestRule = createAndroidComposeRule(activityClass)

    /**
     * Override to provide a custom AI backend. The default is a MockBackend
     * that auto-passes all verifications (useful for validating that routes
     * parse and execute without needing an API key).
     */
    protected open fun createBackend(): AiBackend = MockBackend { request ->
        when (request.instructionType) {
            InstructionType.VERIFY -> AiResponse.VerifyResult(
                passed = true,
                reasoning = "Auto-pass (default MockBackend)",
            )
            InstructionType.REMEMBER -> AiResponse.RememberedValue(
                key = "mock",
                value = "mock",
                reasoning = "Auto-remember (default MockBackend)",
            )
            InstructionType.ACTION -> AiResponse.PerformAction(
                action = UiAction.Tap(text = ""),
                reasoning = "No-op (default MockBackend)",
            )
        }
    }

    protected open val outputDir: File? = null

    private val routeTestRule by lazy {
        RouteTestRule(composeTestRule, createBackend(), outputDir, assetsPath = assetsPath)
    }

    @get:Rule
    val ruleChain: RuleChain by lazy {
        RuleChain.outerRule(composeTestRule).around(routeTestRule)
    }

    /**
     * Load a YAML route file by filename from the assets directory and run it.
     * The filename is matched by converting route names to kebab-case.
     *
     * Example: `runYamlRoute("login-flow.yaml")`
     */
    protected fun runYamlRoute(filename: String) = runTest(timeout = routeTimeout) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val routes = YamlRouteLoader.loadFromAssets(context.assets, assetsPath)
        val route = routes.firstOrNull { filenameForRoute(it.name) == filename }
            ?: routes.firstOrNull {
                it.name.equals(
                    filename.removeSuffix(".yaml").removeSuffix(".yml"),
                    ignoreCase = true,
                )
            }
            ?: error(
                "No route matching filename '$filename' found in assets/$assetsPath. " +
                    "Available routes: ${routes.map { "'${it.name}' -> ${filenameForRoute(it.name)}" }}",
            )
        routeTestRule.runRoute(route)
    }

    /**
     * Run a route defined inline via the DSL builder.
     */
    protected fun runRoute(name: String, block: RouteBuilder.() -> Unit) =
        runTest(timeout = routeTimeout) {
            routeTestRule.runRoute(name, block)
        }

    companion object {
        fun filenameForRoute(routeName: String): String =
            routeName.trim().lowercase()
                .replace(Regex("[^a-z0-9\\s-]"), "")
                .replace(Regex("\\s+"), "-")
                .replace(Regex("-+"), "-")
                .trimStart('-').trimEnd('-')
                .plus(".yaml")
    }
}
