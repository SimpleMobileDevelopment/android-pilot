package co.pilot.android.report

import co.pilot.android.ai.AiResponse
import co.pilot.android.ai.UiAction
import co.pilot.android.dsl.Instruction
import co.pilot.android.dsl.Route
import co.pilot.android.dsl.Step
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileReporterTest {

    private fun createTempDir(): File = createTempDirectory("file-reporter-test").toFile()

    private val testRoute = Route(
        name = "Login Flow",
        steps = listOf(
            Step(
                name = "Enter Credentials",
                instructions = listOf(Instruction.Action("tap email field")),
            ),
        ),
    )

    private val testStep = testRoute.steps[0]

    private val actionInstruction = Instruction.Action("tap email field")
    private val verifyInstruction = Instruction.Verify("email field is visible")
    private val rememberInstruction = Instruction.Remember(key = "email", description = "the email address")

    private val actionResponse = AiResponse.PerformAction(
        action = UiAction.Tap(text = "email"),
        reasoning = "tapping the email field",
    )

    private val verifyPassResponse = AiResponse.VerifyResult(
        passed = true,
        reasoning = "email field is present",
    )

    private val verifyFailResponse = AiResponse.VerifyResult(
        passed = false,
        reasoning = "email field not found",
    )

    private val rememberResponse = AiResponse.RememberedValue(
        key = "email",
        value = "user@example.com",
        reasoning = "extracted the email",
    )

    // ---------------------------------------------------------------------------
    // generateReport — output directory creation
    // ---------------------------------------------------------------------------

    @Test
    fun `generateReport creates output directory`() {
        val outputDir = File(createTempDir(), "nested/report/dir")
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onRouteEnd(testRoute, success = true)

        reporter.generateReport()

        assertTrue(outputDir.exists())
        assertTrue(outputDir.isDirectory)
    }

    @Test
    fun `generateReport writes XML file`() {
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onRouteEnd(testRoute, success = true)

        reporter.generateReport()

        val xmlFiles = outputDir.listFiles { f -> f.name.endsWith(".xml") }
        assertNotNull(xmlFiles)
        assertTrue(xmlFiles.isNotEmpty())
    }

    @Test
    fun `generateReport writes HTML file`() {
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onRouteEnd(testRoute, success = true)

        reporter.generateReport()

        val htmlFiles = outputDir.listFiles { f -> f.name.endsWith(".html") }
        assertNotNull(htmlFiles)
        assertTrue(htmlFiles.isNotEmpty())
    }

    // ---------------------------------------------------------------------------
    // Output filename contains sanitized route name
    // ---------------------------------------------------------------------------

    @Test
    fun `output filenames include sanitized route name`() {
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onRouteEnd(testRoute, success = true)

        reporter.generateReport()

        val files = outputDir.listFiles()!!.map { it.name }
        assertTrue(files.any { it.contains("Login_Flow") })
    }

    @Test
    fun `route name with special characters is sanitized in filenames`() {
        val route = Route(
            name = "Login & Sign-Up Flow!",
            steps = listOf(
                Step("step", listOf(Instruction.Action("do something"))),
            ),
        )
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(route)
        reporter.onRouteEnd(route, success = true)

        reporter.generateReport()

        val files = outputDir.listFiles()!!.map { it.name }
        // Sanitized name replaces non-alphanumeric with underscore
        assertTrue(files.any { it.contains("Login___Sign_Up_Flow_") })
    }

    // ---------------------------------------------------------------------------
    // Entry recording across lifecycle
    // ---------------------------------------------------------------------------

    @Test
    fun `action instruction is recorded as PASS entry`() {
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onStepStart(testStep)
        reporter.onAction(actionInstruction, actionResponse, screenshotBase64 = null)
        reporter.onStepEnd(testStep, screenshotBase64 = null)
        reporter.onRouteEnd(testRoute, success = true)

        reporter.generateReport()

        val xmlContent = readXmlFile(outputDir)
        assertTrue(xmlContent.contains("tap email field"))
        // PASS entries have no <failure> or <error> child elements
        assertFalse(xmlContent.contains("<failure"))
        assertFalse(xmlContent.contains("<error"))
    }

    @Test
    fun `failed verify instruction is recorded as FAIL entry in XML`() {
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onStepStart(testStep)
        reporter.onVerify(verifyInstruction, verifyFailResponse, screenshotBase64 = null)
        reporter.onStepEnd(testStep, screenshotBase64 = null)
        reporter.onRouteEnd(testRoute, success = false)

        reporter.generateReport()

        val xmlContent = readXmlFile(outputDir)
        assertTrue(xmlContent.contains("<failure"))
    }

    @Test
    fun `passed verify instruction is recorded as PASS entry in XML`() {
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onStepStart(testStep)
        reporter.onVerify(verifyInstruction, verifyPassResponse, screenshotBase64 = null)
        reporter.onStepEnd(testStep, screenshotBase64 = null)
        reporter.onRouteEnd(testRoute, success = true)

        reporter.generateReport()

        val xmlContent = readXmlFile(outputDir)
        assertFalse(xmlContent.contains("<failure"))
        assertFalse(xmlContent.contains("<error"))
    }

    @Test
    fun `error instruction is recorded as ERROR entry in XML`() {
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onStepStart(testStep)
        reporter.onError(actionInstruction, RuntimeException("action failed"), screenshotBase64 = null)
        reporter.onStepEnd(testStep, screenshotBase64 = null)
        reporter.onRouteEnd(testRoute, success = false)

        reporter.generateReport()

        val xmlContent = readXmlFile(outputDir)
        assertTrue(xmlContent.contains("<error"))
    }

    @Test
    fun `remember instruction is recorded as PASS entry`() {
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onStepStart(testStep)
        reporter.onRemember(rememberInstruction, rememberResponse)
        reporter.onStepEnd(testStep, screenshotBase64 = null)
        reporter.onRouteEnd(testRoute, success = true)

        reporter.generateReport()

        val xmlContent = readXmlFile(outputDir)
        assertFalse(xmlContent.contains("<failure"))
        assertFalse(xmlContent.contains("<error"))
    }

    // ---------------------------------------------------------------------------
    // XML counts
    // ---------------------------------------------------------------------------

    @Test
    fun `XML output contains correct test count`() {
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onStepStart(testStep)
        reporter.onAction(actionInstruction, actionResponse, screenshotBase64 = null)
        reporter.onVerify(verifyInstruction, verifyPassResponse, screenshotBase64 = null)
        reporter.onRemember(rememberInstruction, rememberResponse)
        reporter.onStepEnd(testStep, screenshotBase64 = null)
        reporter.onRouteEnd(testRoute, success = true)

        reporter.generateReport()

        val xmlContent = readXmlFile(outputDir)
        assertTrue(xmlContent.contains("tests=\"3\""))
    }

    @Test
    fun `XML output contains correct failure count`() {
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onStepStart(testStep)
        reporter.onVerify(verifyInstruction, verifyFailResponse, screenshotBase64 = null)
        reporter.onStepEnd(testStep, screenshotBase64 = null)
        reporter.onRouteEnd(testRoute, success = false)

        reporter.generateReport()

        val xmlContent = readXmlFile(outputDir)
        assertTrue(xmlContent.contains("failures=\"1\""))
        assertTrue(xmlContent.contains("errors=\"0\""))
    }

    @Test
    fun `XML output contains correct error count`() {
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onStepStart(testStep)
        reporter.onError(actionInstruction, RuntimeException("boom"), screenshotBase64 = null)
        reporter.onStepEnd(testStep, screenshotBase64 = null)
        reporter.onRouteEnd(testRoute, success = false)

        reporter.generateReport()

        val xmlContent = readXmlFile(outputDir)
        assertTrue(xmlContent.contains("errors=\"1\""))
        assertTrue(xmlContent.contains("failures=\"0\""))
    }

    @Test
    fun `XML output contains zero failures and zero errors for all passing entries`() {
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onStepStart(testStep)
        reporter.onAction(actionInstruction, actionResponse, screenshotBase64 = null)
        reporter.onVerify(verifyInstruction, verifyPassResponse, screenshotBase64 = null)
        reporter.onStepEnd(testStep, screenshotBase64 = null)
        reporter.onRouteEnd(testRoute, success = true)

        reporter.generateReport()

        val xmlContent = readXmlFile(outputDir)
        assertTrue(xmlContent.contains("failures=\"0\""))
        assertTrue(xmlContent.contains("errors=\"0\""))
    }

    // ---------------------------------------------------------------------------
    // HTML report
    // ---------------------------------------------------------------------------

    @Test
    fun `HTML report contains route name`() {
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onRouteEnd(testRoute, success = true)

        reporter.generateReport()

        val htmlContent = readHtmlFile(outputDir)
        assertTrue(htmlContent.contains("Login Flow"))
    }

    @Test
    fun `HTML report shows PASSED status for successful route`() {
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onRouteEnd(testRoute, success = true)

        reporter.generateReport()

        val htmlContent = readHtmlFile(outputDir)
        assertTrue(htmlContent.contains("PASSED"))
    }

    @Test
    fun `HTML report shows FAILED status for unsuccessful route`() {
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onStepStart(testStep)
        reporter.onVerify(verifyInstruction, verifyFailResponse, screenshotBase64 = null)
        reporter.onStepEnd(testStep, screenshotBase64 = null)
        reporter.onRouteEnd(testRoute, success = false)

        reporter.generateReport()

        val htmlContent = readHtmlFile(outputDir)
        assertTrue(htmlContent.contains("FAILED"))
    }

    @Test
    fun `HTML report contains instruction descriptions`() {
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onStepStart(testStep)
        reporter.onAction(actionInstruction, actionResponse, screenshotBase64 = null)
        reporter.onStepEnd(testStep, screenshotBase64 = null)
        reporter.onRouteEnd(testRoute, success = true)

        reporter.generateReport()

        val htmlContent = readHtmlFile(outputDir)
        assertTrue(htmlContent.contains("tap email field"))
    }

    @Test
    fun `HTML report contains step names`() {
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onStepStart(testStep)
        reporter.onAction(actionInstruction, actionResponse, screenshotBase64 = null)
        reporter.onStepEnd(testStep, screenshotBase64 = null)
        reporter.onRouteEnd(testRoute, success = true)

        reporter.generateReport()

        val htmlContent = readHtmlFile(outputDir)
        assertTrue(htmlContent.contains("Enter Credentials"))
    }

    @Test
    fun `HTML report uses relative screenshot paths not data URIs`() {
        // This test verifies the design contract: screenshots should be referenced
        // via relative file paths so the HTML report is self-contained when the
        // screenshots directory is co-located with the report.
        //
        // We verify the absence of data URIs and the structure of the img tag.
        // Screenshot writing requires android.util.Base64 which is unavailable in
        // JVM unit tests, so this test confirms the HTML template never falls back
        // to inline data URIs by inspecting entries without screenshots.
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onStepStart(testStep)
        reporter.onAction(actionInstruction, actionResponse, screenshotBase64 = null)
        reporter.onStepEnd(testStep, screenshotBase64 = null)
        reporter.onRouteEnd(testRoute, success = true)

        reporter.generateReport()

        val htmlContent = readHtmlFile(outputDir)
        // No data URIs should appear in the HTML output
        assertFalse(htmlContent.contains("data:image"))
        assertFalse(htmlContent.contains("base64,"))
    }

    // ---------------------------------------------------------------------------
    // Empty entries list produces valid output
    // ---------------------------------------------------------------------------

    @Test
    fun `empty entries list produces valid XML`() {
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onRouteEnd(testRoute, success = true)

        reporter.generateReport()

        val xmlContent = readXmlFile(outputDir)
        assertTrue(xmlContent.contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(xmlContent.contains("<testsuites>"))
        assertTrue(xmlContent.contains("</testsuites>"))
    }

    @Test
    fun `empty entries list produces valid HTML`() {
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(testRoute)
        reporter.onRouteEnd(testRoute, success = true)

        reporter.generateReport()

        val htmlContent = readHtmlFile(outputDir)
        assertTrue(htmlContent.contains("<!DOCTYPE html>"))
        assertTrue(htmlContent.contains("<html"))
        assertTrue(htmlContent.contains("</html>"))
    }

    // ---------------------------------------------------------------------------
    // sanitize() function — tested via filename behavior
    // ---------------------------------------------------------------------------

    @Test
    fun `sanitize replaces spaces with underscores in output filename`() {
        val route = Route(
            name = "My Route Name",
            steps = listOf(Step("s", listOf(Instruction.Action("a")))),
        )
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(route)
        reporter.onRouteEnd(route, success = true)

        reporter.generateReport()

        val files = outputDir.listFiles()!!.map { it.name }
        assertTrue(files.any { it.contains("My_Route_Name") })
    }

    @Test
    fun `sanitize replaces non-alphanumeric characters in output filename`() {
        val route = Route(
            name = "Route: Test #1",
            steps = listOf(Step("s", listOf(Instruction.Action("a")))),
        )
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)
        reporter.onRouteStart(route)
        reporter.onRouteEnd(route, success = true)

        reporter.generateReport()

        // The sanitized name should only contain alphanumeric chars and underscores
        val files = outputDir.listFiles()!!.map { it.name }
        val reportFiles = files.filter { it.startsWith("route-") || it.startsWith("route") }
        assertTrue(reportFiles.isNotEmpty())
        reportFiles.forEach { filename ->
            // Filename (excluding extension) should not contain special chars
            val nameWithoutExt = filename.substringBeforeLast(".")
            assertTrue(
                nameWithoutExt.all { c -> c.isLetterOrDigit() || c == '_' || c == '-' },
                "Filename '$nameWithoutExt' should only contain alphanumeric chars, underscores, and hyphens",
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Multiple steps and instructions across lifecycle
    // ---------------------------------------------------------------------------

    @Test
    fun `entries across multiple steps are all recorded in XML`() {
        val route = Route(
            name = "Multi Step",
            steps = listOf(
                Step("Step One", listOf(Instruction.Action("action A"))),
                Step("Step Two", listOf(Instruction.Verify("verify B"))),
            ),
        )
        val stepOne = route.steps[0]
        val stepTwo = route.steps[1]
        val outputDir = createTempDir()
        val reporter = FileReporter(outputDir)

        reporter.onRouteStart(route)
        reporter.onStepStart(stepOne)
        reporter.onAction(Instruction.Action("action A"), actionResponse, screenshotBase64 = null)
        reporter.onStepEnd(stepOne, screenshotBase64 = null)
        reporter.onStepStart(stepTwo)
        reporter.onVerify(Instruction.Verify("verify B"), verifyPassResponse, screenshotBase64 = null)
        reporter.onStepEnd(stepTwo, screenshotBase64 = null)
        reporter.onRouteEnd(route, success = true)

        reporter.generateReport()

        val xmlContent = readXmlFile(outputDir)
        assertTrue(xmlContent.contains("tests=\"2\""))
        assertTrue(xmlContent.contains("action A"))
        assertTrue(xmlContent.contains("verify B"))
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun readXmlFile(outputDir: File): String {
        val xmlFile = outputDir.listFiles { f -> f.name.endsWith(".xml") }?.firstOrNull()
        assertNotNull(xmlFile, "Expected an XML file in $outputDir")
        return xmlFile.readText()
    }

    private fun readHtmlFile(outputDir: File): String {
        val htmlFile = outputDir.listFiles { f -> f.name.endsWith(".html") }?.firstOrNull()
        assertNotNull(htmlFile, "Expected an HTML file in $outputDir")
        return htmlFile.readText()
    }
}

// TODO: Test that screenshot paths in HTML are relative to outputDir (not absolute) when
//       screenshotBase64 is provided. This requires either a JVM-compatible Base64 stub or
//       moving the Base64 decoding behind an abstraction so it can be replaced in tests.
// TODO: Test that screenshots directory is created under outputDir with correct structure
//       (screenshots/<sanitizedRouteName>/<sanitizedStepName>_<index>.png).
// TODO: Test thread safety of concurrent reporter calls (synchronized block behavior).
