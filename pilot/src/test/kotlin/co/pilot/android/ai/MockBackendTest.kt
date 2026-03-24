package co.pilot.android.ai

import co.pilot.android.screen.BoundsInfo
import co.pilot.android.screen.NodeInfo
import co.pilot.android.screen.ScreenState
import co.pilot.android.screen.toPromptString
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MockBackendTest {

    private val emptyScreenState = ScreenState()

    private fun buildRequest(
        instruction: String = "tap login",
        type: InstructionType = InstructionType.ACTION,
        screenState: ScreenState = emptyScreenState,
    ) = AiRequest(
        instruction = instruction,
        instructionType = type,
        screenState = screenState,
        rememberedValues = emptyMap(),
        routeContext = "test route",
    )

    @Test
    fun `planAction returns expected PerformAction response`() = runTest {
        val expectedAction = UiAction.Tap(text = "Login")
        val backend = MockBackend { AiResponse.PerformAction(expectedAction, "tapping login") }

        val response = backend.planAction(buildRequest())

        assertTrue(response is AiResponse.PerformAction)
        assertEquals(expectedAction, response.action)
        assertEquals("tapping login", response.reasoning)
    }

    @Test
    fun `planAction returns expected VerifyResult response`() = runTest {
        val backend = MockBackend { AiResponse.VerifyResult(passed = true, reasoning = "text visible") }

        val response = backend.planAction(buildRequest(type = InstructionType.VERIFY))

        assertTrue(response is AiResponse.VerifyResult)
        assertTrue(response.passed)
    }

    @Test
    fun `planAction returns expected RememberedValue response`() = runTest {
        val backend = MockBackend {
            AiResponse.RememberedValue(key = "username", value = "john", reasoning = "found username")
        }

        val response = backend.planAction(buildRequest(type = InstructionType.REMEMBER))

        assertTrue(response is AiResponse.RememberedValue)
        assertEquals("username", response.key)
        assertEquals("john", response.value)
    }

    @Test
    fun `request history is captured`() = runTest {
        val backend = MockBackend { AiResponse.VerifyResult(passed = true, reasoning = "ok") }

        val request1 = buildRequest(instruction = "first")
        val request2 = buildRequest(instruction = "second")
        backend.planAction(request1)
        backend.planAction(request2)

        assertEquals(2, backend.requestHistory.size)
        assertEquals("first", backend.requestHistory[0].instruction)
        assertEquals("second", backend.requestHistory[1].instruction)
    }

    @Test
    fun `different instruction types produce correct requests`() = runTest {
        val backend = MockBackend { AiResponse.VerifyResult(passed = true, reasoning = "ok") }

        backend.planAction(buildRequest(type = InstructionType.ACTION))
        backend.planAction(buildRequest(type = InstructionType.VERIFY))
        backend.planAction(buildRequest(type = InstructionType.REMEMBER))

        assertEquals(InstructionType.ACTION, backend.requestHistory[0].instructionType)
        assertEquals(InstructionType.VERIFY, backend.requestHistory[1].instructionType)
        assertEquals(InstructionType.REMEMBER, backend.requestHistory[2].instructionType)
    }

    @Test
    fun `AiRequest serialization round-trip`() {
        val json = Json { ignoreUnknownKeys = true }
        val request = buildRequest(
            screenState = ScreenState(
                nodes = listOf(
                    NodeInfo(id = 1, text = "Hello", isClickable = true),
                ),
            ),
        )

        val encoded = json.encodeToString(AiRequest.serializer(), request)
        val decoded = json.decodeFromString(AiRequest.serializer(), encoded)

        assertEquals(request, decoded)
    }

    @Test
    fun `AiResponse serialization round-trip`() {
        val json = Json { ignoreUnknownKeys = true }
        val response: AiResponse = AiResponse.PerformAction(
            action = UiAction.Tap(nodeId = 5, text = "Submit"),
            reasoning = "submit button found",
        )

        val encoded = json.encodeToString(AiResponse.serializer(), response)
        val decoded = json.decodeFromString(AiResponse.serializer(), encoded)

        assertEquals(response, decoded)
    }

    @Test
    fun `ScreenState serialization round-trip`() {
        val json = Json { ignoreUnknownKeys = true }
        val screenState = ScreenState(
            nodes = listOf(
                NodeInfo(
                    id = 0,
                    testTag = "btn_login",
                    text = "Login",
                    isClickable = true,
                    boundsInRoot = BoundsInfo(0f, 100f, 200f, 150f),
                    children = listOf(1, 2),
                ),
                NodeInfo(id = 1, text = "Icon"),
                NodeInfo(id = 2, text = "Label"),
            ),
        )

        val encoded = json.encodeToString(ScreenState.serializer(), screenState)
        val decoded = json.decodeFromString(ScreenState.serializer(), encoded)

        assertEquals(screenState, decoded)
    }

    @Test
    fun `toPromptString filters non-interactive nodes without text`() {
        val screenState = ScreenState(
            nodes = listOf(
                NodeInfo(id = 0, text = "Login", isClickable = true),
                NodeInfo(id = 1), // no text, no tag, not interactive — should be filtered
                NodeInfo(id = 2, testTag = "header"),
            ),
        )

        val prompt = screenState.toPromptString()

        assertTrue(prompt.contains("[0]"))
        assertFalse(prompt.contains("[1]"))
        assertTrue(prompt.contains("[2]"))
    }

    @Test
    fun `toPromptString includes capabilities and bounds`() {
        val screenState = ScreenState(
            nodes = listOf(
                NodeInfo(
                    id = 3,
                    text = "Email",
                    isEditable = true,
                    boundsInRoot = BoundsInfo(10f, 20f, 300f, 60f),
                ),
            ),
        )

        val prompt = screenState.toPromptString()

        assertTrue(prompt.contains("editable"))
        assertTrue(prompt.contains("@(10,20,300,60)"))
    }

    @Test
    fun `toPromptString handles empty screen`() {
        val prompt = ScreenState().toPromptString()
        assertEquals("(empty screen)", prompt)
    }
}
