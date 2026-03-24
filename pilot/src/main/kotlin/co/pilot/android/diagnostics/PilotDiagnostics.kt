package co.pilot.android.diagnostics

import androidx.test.platform.app.InstrumentationRegistry
import co.pilot.android.ai.AiBackend
import timber.log.Timber

object PilotDiagnostics {
    private const val TAG = "PilotDiagnostics"

    data class DiagnosticResult(
        val passed: Boolean,
        val message: String,
        val severity: Severity,
    )

    enum class Severity { INFO, WARNING, ERROR }

    fun check(
        backend: AiBackend,
        assetsPath: String = "routes",
        failOnError: Boolean = false,
    ): List<DiagnosticResult> {
        val results = mutableListOf<DiagnosticResult>()

        results.addAll(checkRoutesDirectory(assetsPath))
        results.addAll(checkBackend(backend))

        for (result in results) {
            when (result.severity) {
                Severity.INFO -> Timber.tag(TAG).i("[%s] %s", if (result.passed) "OK" else "FAIL", result.message)
                Severity.WARNING -> Timber.tag(TAG).w("[%s] %s", if (result.passed) "OK" else "FAIL", result.message)
                Severity.ERROR -> Timber.tag(TAG).e("[%s] %s", if (result.passed) "OK" else "FAIL", result.message)
            }
        }

        if (failOnError) {
            val errors = results.filter { !it.passed && it.severity == Severity.ERROR }
            if (errors.isNotEmpty()) {
                error(buildString {
                    appendLine("Pilot setup validation failed:")
                    errors.forEach { appendLine("  - ${it.message}") }
                })
            }
        }

        return results
    }

    private fun checkRoutesDirectory(assetsPath: String): List<DiagnosticResult> {
        val results = mutableListOf<DiagnosticResult>()
        try {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val files = context.assets.list(assetsPath)
            if (files == null || files.isEmpty()) {
                results.add(
                    DiagnosticResult(
                        passed = false,
                        message = "No files found in assets/$assetsPath. " +
                            "Create YAML route files in src/main/assets/$assetsPath/",
                        severity = Severity.WARNING,
                    ),
                )
            } else {
                val yamlFiles = files.filter { it.endsWith(".yaml") || it.endsWith(".yml") }
                if (yamlFiles.isEmpty()) {
                    results.add(
                        DiagnosticResult(
                            passed = false,
                            message = "Directory assets/$assetsPath exists but contains no .yaml/.yml files",
                            severity = Severity.WARNING,
                        ),
                    )
                } else {
                    results.add(
                        DiagnosticResult(
                            passed = true,
                            message = "Found ${yamlFiles.size} route file(s) in assets/$assetsPath: " +
                                yamlFiles.joinToString(),
                            severity = Severity.INFO,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            results.add(
                DiagnosticResult(
                    passed = false,
                    message = "Cannot access assets/$assetsPath: ${e.message}",
                    severity = Severity.WARNING,
                ),
            )
        }
        return results
    }

    private fun checkBackend(backend: AiBackend): List<DiagnosticResult> {
        val name = backend::class.simpleName ?: "Unknown"
        return listOf(
            DiagnosticResult(
                passed = true,
                message = "Using $name as AI backend",
                severity = Severity.INFO,
            ),
        )
    }
}
