package co.pilot.android.report

import android.util.Base64
import co.pilot.android.ai.AiResponse
import co.pilot.android.ai.InstructionType
import co.pilot.android.dsl.Instruction
import co.pilot.android.dsl.Route
import co.pilot.android.dsl.Step
import java.io.File

class FileReporter(private val outputDir: File) : RouteReporter {

    private val lock = Any()

    private val entries = mutableListOf<ReportEntry>()

    private var currentRouteName: String = ""
    private var currentStepName: String = ""
    private var currentInstructionIndex: Int = 0
    private var routeSuccess: Boolean = true
    private var stepStartTime: Long = 0L
    private var instructionStartTime: Long = 0L

    override fun onRouteStart(route: Route) {
        synchronized(lock) {
            currentRouteName = route.name
            currentInstructionIndex = 0
        }
    }

    override fun onRouteEnd(route: Route, success: Boolean) {
        synchronized(lock) {
            routeSuccess = success
        }
    }

    override fun onStepStart(step: Step) {
        synchronized(lock) {
            currentStepName = step.name
            stepStartTime = System.currentTimeMillis()
            instructionStartTime = System.currentTimeMillis()
        }
    }

    override fun onStepEnd(step: Step, screenshotBase64: String?) {
        synchronized(lock) {
            screenshotBase64?.let { saveScreenshot(it, suffix = "step_end") }
        }
    }

    override fun onAction(
        instruction: Instruction,
        response: AiResponse.PerformAction,
        screenshotBase64: String?,
    ) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val screenshotPath = screenshotBase64?.let { saveScreenshot(it) }

            entries.add(
                ReportEntry(
                    timestamp = now,
                    routeName = currentRouteName,
                    stepName = currentStepName,
                    instructionDescription = instruction.description,
                    instructionType = InstructionType.ACTION,
                    status = EntryStatus.PASS,
                    reasoning = response.reasoning,
                    screenshotPath = screenshotPath,
                    durationMs = now - instructionStartTime,
                ),
            )
            currentInstructionIndex++
            instructionStartTime = System.currentTimeMillis()
        }
    }

    override fun onVerify(
        instruction: Instruction,
        response: AiResponse.VerifyResult,
        screenshotBase64: String?,
    ) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val screenshotPath = screenshotBase64?.let { saveScreenshot(it) }

            entries.add(
                ReportEntry(
                    timestamp = now,
                    routeName = currentRouteName,
                    stepName = currentStepName,
                    instructionDescription = instruction.description,
                    instructionType = InstructionType.VERIFY,
                    status = if (response.passed) EntryStatus.PASS else EntryStatus.FAIL,
                    reasoning = response.reasoning,
                    screenshotPath = screenshotPath,
                    durationMs = now - instructionStartTime,
                ),
            )
            currentInstructionIndex++
            instructionStartTime = System.currentTimeMillis()
        }
    }

    override fun onRemember(instruction: Instruction, response: AiResponse.RememberedValue) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            entries.add(
                ReportEntry(
                    timestamp = now,
                    routeName = currentRouteName,
                    stepName = currentStepName,
                    instructionDescription = instruction.description,
                    instructionType = InstructionType.REMEMBER,
                    status = EntryStatus.PASS,
                    reasoning = "${response.key}=${response.value}",
                    screenshotPath = null,
                    durationMs = now - instructionStartTime,
                ),
            )
            currentInstructionIndex++
            instructionStartTime = System.currentTimeMillis()
        }
    }

    override fun onError(instruction: Instruction, error: Throwable, screenshotBase64: String?) {
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val screenshotPath = screenshotBase64?.let { saveScreenshot(it) }

            entries.add(
                ReportEntry(
                    timestamp = now,
                    routeName = currentRouteName,
                    stepName = currentStepName,
                    instructionDescription = instruction.description,
                    instructionType = when (instruction) {
                        is Instruction.Action -> InstructionType.ACTION
                        is Instruction.Verify -> InstructionType.VERIFY
                        is Instruction.Remember -> InstructionType.REMEMBER
                    },
                    status = EntryStatus.ERROR,
                    reasoning = error.stackTraceToString(),
                    screenshotPath = screenshotPath,
                    durationMs = now - instructionStartTime,
                ),
            )
            currentInstructionIndex++
            instructionStartTime = System.currentTimeMillis()
        }
    }

    override fun generateReport() {
        synchronized(lock) {
            outputDir.mkdirs()
            generateJunitXml()
            generateHtmlReport()
        }
    }

    private fun saveScreenshot(base64Data: String, suffix: String? = null): String {
        val sanitizedRoute = sanitize(currentRouteName)
        val sanitizedStep = sanitize(currentStepName)
        val dir = File(outputDir, "screenshots/$sanitizedRoute")
        dir.mkdirs()
        val fileName = if (suffix != null) {
            "${sanitizedStep}_$suffix.png"
        } else {
            "${sanitizedStep}_$currentInstructionIndex.png"
        }
        val file = File(dir, fileName)
        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun sanitize(name: String): String = name.replace(Regex("[^a-zA-Z0-9]"), "_")

    private fun generateJunitXml() {
        val grouped = entries.groupBy { it.routeName }
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<testsuites>")

        for ((routeName, routeEntries) in grouped) {
            val tests = routeEntries.size
            val failures = routeEntries.count { it.status == EntryStatus.FAIL }
            val errors = routeEntries.count { it.status == EntryStatus.ERROR }
            val totalTime = routeEntries.sumOf { it.durationMs } / 1000.0

            sb.appendLine(
                "  <testsuite name=\"${escapeXml(routeName)}\" tests=\"$tests\" " +
                    "failures=\"$failures\" errors=\"$errors\" time=\"${"%.3f".format(totalTime)}\">",
            )

            for (entry in routeEntries) {
                val testTime = entry.durationMs / 1000.0
                val caseName = "${entry.stepName}: ${entry.instructionDescription}"
                sb.appendLine(
                    "    <testcase name=\"${escapeXml(caseName)}\" " +
                        "classname=\"${escapeXml(routeName)}\" time=\"${"%.3f".format(testTime)}\">",
                )

                when (entry.status) {
                    EntryStatus.FAIL -> {
                        sb.appendLine(
                            "      <failure message=\"${escapeXml(entry.instructionDescription)}\" " +
                                "type=\"AssertionError\">${escapeXml(entry.reasoning ?: "")}</failure>",
                        )
                    }
                    EntryStatus.ERROR -> {
                        sb.appendLine(
                            "      <error message=\"${escapeXml(entry.instructionDescription)}\" " +
                                "type=\"Error\">${escapeXml(entry.reasoning ?: "")}</error>",
                        )
                    }
                    EntryStatus.PASS -> { /* no child element */ }
                }

                if (entry.screenshotPath != null) {
                    sb.appendLine("      <system-out>Screenshot: ${escapeXml(entry.screenshotPath)}</system-out>")
                }

                sb.appendLine("    </testcase>")
            }

            sb.appendLine("  </testsuite>")
        }

        sb.appendLine("</testsuites>")

        val xmlFile = File(outputDir, "route-results-${sanitize(currentRouteName)}.xml")
        xmlFile.writeText(sb.toString())
    }

    @Suppress("LongMethod")
    private fun generateHtmlReport() {
        val statusLabel = if (routeSuccess) "PASSED" else "FAILED"
        val statusColor = if (routeSuccess) "#4caf50" else "#f44336"
        val grouped = entries.groupBy { it.stepName }

        val sb = StringBuilder()
        sb.appendLine("<!DOCTYPE html>")
        sb.appendLine("<html lang=\"en\">")
        sb.appendLine("<head>")
        sb.appendLine("<meta charset=\"UTF-8\">")
        sb.appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
        sb.appendLine("<title>$currentRouteName - $statusLabel</title>")
        sb.appendLine("<style>")
        sb.appendLine(
            """
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; color: #333; }
            h1 { background: #263238; color: #fff; padding: 20px; margin: -20px -20px 20px -20px; }
            .status-badge { display: inline-block; padding: 4px 12px; border-radius: 4px; color: #fff; font-weight: bold; font-size: 14px; }
            .badge-pass { background: #4caf50; }
            .badge-fail { background: #f44336; }
            .badge-error { background: #ff9800; }
            .step-section { background: #fff; border-radius: 8px; padding: 16px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(0,0,0,0.12); }
            .step-header { background: #37474f; color: #fff; padding: 10px 16px; margin: -16px -16px 16px -16px; border-radius: 8px 8px 0 0; }
            .instruction { border-left: 4px solid #ccc; padding: 8px 12px; margin-bottom: 12px; }
            .instruction.pass { border-left-color: #4caf50; }
            .instruction.fail { border-left-color: #f44336; }
            .instruction.error { border-left-color: #ff9800; }
            .type-label { font-size: 12px; text-transform: uppercase; font-weight: bold; color: #757575; }
            .reasoning { font-style: italic; color: #616161; margin-top: 4px; }
            .screenshot { max-width: 100%; margin-top: 8px; border: 1px solid #ddd; border-radius: 4px; }
            """.trimIndent(),
        )
        sb.appendLine("</style>")
        sb.appendLine("</head>")
        sb.appendLine("<body>")
        sb.appendLine(
            "<h1>${escapeHtml(currentRouteName)} " +
                "<span class=\"status-badge\" style=\"background:$statusColor\">$statusLabel</span></h1>",
        )

        for ((stepName, stepEntries) in grouped) {
            val stepPassed = stepEntries.all { it.status == EntryStatus.PASS }
            val stepBadge = if (stepPassed) "badge-pass" else "badge-fail"
            val stepLabel = if (stepPassed) "PASS" else "FAIL"

            sb.appendLine("<div class=\"step-section\">")
            sb.appendLine(
                "<div class=\"step-header\">${escapeHtml(stepName)} " +
                    "<span class=\"status-badge $stepBadge\">$stepLabel</span></div>",
            )

            for (entry in stepEntries) {
                val statusClass = when (entry.status) {
                    EntryStatus.PASS -> "pass"
                    EntryStatus.FAIL -> "fail"
                    EntryStatus.ERROR -> "error"
                }
                val badgeClass = when (entry.status) {
                    EntryStatus.PASS -> "badge-pass"
                    EntryStatus.FAIL -> "badge-fail"
                    EntryStatus.ERROR -> "badge-error"
                }
                val statusText = entry.status.name

                sb.appendLine("<div class=\"instruction $statusClass\">")
                sb.appendLine(
                    "<span class=\"type-label\">${escapeHtml(entry.instructionType.name.lowercase())}</span>",
                )
                sb.appendLine(" <span class=\"status-badge $badgeClass\">$statusText</span>")
                sb.appendLine("<p>${escapeHtml(entry.instructionDescription)}</p>")
                if (entry.reasoning != null) {
                    sb.appendLine("<p class=\"reasoning\">${escapeHtml(entry.reasoning)}</p>")
                }

                if (entry.screenshotPath != null) {
                    val relativePath = File(entry.screenshotPath).relativeTo(outputDir).path
                    sb.appendLine(
                        "<img class=\"screenshot\" src=\"$relativePath\" alt=\"Screenshot\">",
                    )
                }

                sb.appendLine("</div>")
            }

            sb.appendLine("</div>")
        }

        sb.appendLine("</body>")
        sb.appendLine("</html>")

        val htmlFile = File(outputDir, "route-report-${sanitize(currentRouteName)}.html")
        htmlFile.writeText(sb.toString())
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
