package co.pilot.android.runner

import co.pilot.android.ai.AiBackend
import co.pilot.android.ai.AiRequest
import co.pilot.android.ai.AiResponse
import co.pilot.android.ai.InstructionType
import co.pilot.android.dsl.Instruction
import co.pilot.android.dsl.Route
import co.pilot.android.dsl.Step
import co.pilot.android.executor.ActionExecutable
import co.pilot.android.report.RouteReporter
import co.pilot.android.screen.ScreenCapture
import co.pilot.android.screen.ScreenState
import kotlinx.coroutines.delay
import timber.log.Timber

class RouteRunner(
    private val screenReader: ScreenCapture,
    private val aiBackend: AiBackend,
    private val actionExecutor: ActionExecutable,
    private val reporter: RouteReporter,
) {
    private val rememberedValues = mutableMapOf<String, String>()

    suspend fun run(route: Route) {
        reporter.onRouteStart(route)
        var success = true
        try {
            for (step in route.steps) {
                executeStep(step, route.name)
            }
        } catch (e: Throwable) {
            success = false
            throw e
        } finally {
            reporter.onRouteEnd(route, success)
            reporter.generateReport()
        }
    }

    private suspend fun executeStep(step: Step, routeName: String) {
        reporter.onStepStart(step)
        for (instruction in step.instructions) {
            val screenState = screenReader.captureScreenState()
            try {
                executeInstruction(instruction, routeName, step, screenState)
            } catch (e: Throwable) {
                val errorScreenshot = captureErrorScreenshot(screenState)
                reporter.onError(instruction, e, errorScreenshot)
                throw e
            }
        }
        val stepScreenshot = screenReader.captureScreenState().screenshotBase64
        reporter.onStepEnd(step, stepScreenshot)
    }

    private fun captureErrorScreenshot(fallbackState: ScreenState): String? = try {
        screenReader.captureScreenState().screenshotBase64
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "Failed to capture error screenshot, using fallback")
        fallbackState.screenshotBase64
    }

    private suspend fun executeInstruction(
        instruction: Instruction,
        routeName: String,
        step: Step,
        screenState: ScreenState,
    ) {
        val request = AiRequest(
            instruction = resolveRememberedValues(instruction.description),
            instructionType = when (instruction) {
                is Instruction.Action -> InstructionType.ACTION
                is Instruction.Verify -> InstructionType.VERIFY
                is Instruction.Remember -> InstructionType.REMEMBER
            },
            screenState = screenState,
            rememberedValues = rememberedValues.toMap(),
            routeContext = "$routeName > ${step.name}",
        )

        val response = aiBackend.planAction(request)

        when (response) {
            is AiResponse.PerformAction -> {
                actionExecutor.execute(response.action)
                val afterState = screenReader.captureScreenState()
                reporter.onAction(instruction, response, afterState.screenshotBase64)
            }
            is AiResponse.VerifyResult -> {
                val finalResponse = if (!response.passed) {
                    retryVerify(instruction, request, response, screenState, step)
                } else {
                    response
                }
                reporter.onVerify(instruction, finalResponse, screenState.screenshotBase64)
                if (!finalResponse.passed) {
                    val timeoutUsed = step.timeoutSeconds ?: DEFAULT_VERIFY_TIMEOUT_SECONDS
                    throw AssertionError(
                        "Verification failed after ${timeoutUsed}s: ${instruction.description}\n" +
                            "Reasoning: ${finalResponse.reasoning}",
                    )
                }
            }
            is AiResponse.RememberedValue -> {
                rememberedValues[response.key] = response.value
                reporter.onRemember(instruction, response)
            }
        }
    }

    private suspend fun retryVerify(
        instruction: Instruction,
        originalRequest: AiRequest,
        initialResponse: AiResponse.VerifyResult,
        initialScreenState: ScreenState,
        step: Step,
    ): AiResponse.VerifyResult {
        val timeoutSeconds = step.timeoutSeconds ?: DEFAULT_VERIFY_TIMEOUT_SECONDS
        val deadlineMs = System.currentTimeMillis() + timeoutSeconds * 1000L
        var lastResponse = initialResponse
        var lastScreenNodes = initialScreenState.nodes

        Timber.tag(TAG).d(
            "Verify failed, retrying for up to ${timeoutSeconds}s: ${instruction.description}",
        )

        while (!lastResponse.passed && System.currentTimeMillis() < deadlineMs) {
            delay(RETRY_POLL_INTERVAL_MS)
            val newScreen = try {
                screenReader.captureScreenState()
            } catch (e: Exception) {
                Timber.tag(TAG).d("Screen capture failed during retry, waiting...")
                continue
            }

            if (newScreen.nodes != lastScreenNodes) {
                Timber.tag(TAG).d("Screen changed, re-checking verification")
                val retryRequest = originalRequest.copy(screenState = newScreen)
                val retryResponse = aiBackend.planAction(retryRequest)
                if (retryResponse is AiResponse.VerifyResult) {
                    lastResponse = retryResponse
                }
                lastScreenNodes = newScreen.nodes
            } else {
                Timber.tag(TAG).d("Screen unchanged, waiting...")
            }
        }

        return lastResponse
    }

    companion object {
        private const val TAG = "RouteRunner"
        private const val DEFAULT_VERIFY_TIMEOUT_SECONDS = 15
        private const val RETRY_POLL_INTERVAL_MS = 2000L
    }

    private fun resolveRememberedValues(text: String): String {
        var resolved = text
        for ((key, value) in rememberedValues) {
            resolved = resolved.replace("{$key}", value)
        }
        return resolved
    }
}
