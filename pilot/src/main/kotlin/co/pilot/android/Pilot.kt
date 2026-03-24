package co.pilot.android

import co.pilot.android.ai.AiBackend
import co.pilot.android.ai.ClaudeBackend
import java.io.File

/**
 * Pilot: AI-powered natural language integration testing for Android.
 *
 * Usage:
 * ```
 * val config = Pilot.configure {
 *     apiKey = "sk-ant-..."
 * }
 *
 * @get:Rule
 * val routeRule = RouteTestRule(
 *     composeTestRule = composeTestRule,
 *     aiBackend = config.buildBackend(),
 *     outputDir = config.outputDir,
 * )
 * ```
 */
object Pilot {
    const val VERSION = "0.1.0"

    fun configure(block: PilotConfig.() -> Unit): PilotConfig =
        PilotConfig().apply(block)
}

class PilotConfig {
    var apiKey: String = ""
    var model: String = ClaudeBackend.DEFAULT_MODEL
    var maxTokens: Int = 1024
    var outputDir: File? = null

    fun buildBackend(): AiBackend = ClaudeBackend(
        apiKey = apiKey.also {
            require(it.isNotEmpty()) {
                "Pilot API key is required. Set it via Pilot.configure { apiKey = \"...\" }"
            }
        },
        model = model,
        maxTokens = maxTokens,
    )
}
