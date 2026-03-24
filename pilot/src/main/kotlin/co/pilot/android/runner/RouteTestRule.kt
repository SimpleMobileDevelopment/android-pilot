package co.pilot.android.runner

import androidx.compose.ui.test.junit4.ComposeTestRule
import co.pilot.android.ai.AiBackend
import co.pilot.android.dsl.Route
import co.pilot.android.dsl.RouteBuilder
import co.pilot.android.dsl.route
import co.pilot.android.executor.ComposeActionExecutor
import co.pilot.android.report.CompositeReporter
import co.pilot.android.report.FileReporter
import co.pilot.android.report.LogcatReporter
import co.pilot.android.report.RouteReporter
import co.pilot.android.screen.ComposeScreenReader
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import co.pilot.android.diagnostics.PilotDiagnostics
import java.io.File

class RouteTestRule(
    private val composeTestRule: ComposeTestRule,
    private val aiBackend: AiBackend,
    private val outputDir: File? = null,
    private val enableDiagnostics: Boolean = true,
    private val assetsPath: String = "routes",
) : TestRule {

    private var runner: RouteRunner? = null

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                if (enableDiagnostics) {
                    PilotDiagnostics.check(
                        backend = aiBackend,
                        assetsPath = assetsPath,
                    )
                }
                val screenReader = ComposeScreenReader(composeTestRule)
                val executor = ComposeActionExecutor(composeTestRule)
                val reporter: RouteReporter = if (outputDir != null) {
                    CompositeReporter(LogcatReporter(), FileReporter(outputDir))
                } else {
                    LogcatReporter()
                }
                runner = RouteRunner(screenReader, aiBackend, executor, reporter)
                base.evaluate()
            }
        }
    }

    suspend fun runRoute(name: String, block: RouteBuilder.() -> Unit) {
        val r = runner
            ?: error("RouteTestRule has not been initialized. Ensure this rule is applied via @get:Rule before calling runRoute.")
        val j = route(name, block)
        r.run(j)
    }

    suspend fun runRoute(route: Route) {
        val r = runner
            ?: error("RouteTestRule has not been initialized. Ensure this rule is applied via @get:Rule before calling runRoute.")
        r.run(route)
    }
}
