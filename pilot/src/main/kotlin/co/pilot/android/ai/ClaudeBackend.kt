package co.pilot.android.ai

import co.pilot.android.screen.toPromptString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ClaudeBackend(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
    private val maxTokens: Int = 1024,
    private val client: OkHttpClient = defaultClient(),
    private val apiUrl: String = DEFAULT_API_URL,
    private val apiVersion: String = DEFAULT_API_VERSION,
) : AiBackend {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun planAction(request: AiRequest): AiResponse {
        require(apiKey.isNotEmpty()) {
            "PILOT_AI_API_KEY not configured. Set it in local.properties or as an environment variable."
        }

        val requestBody = buildRequestBody(request)
        val httpRequest = Request.Builder()
            .url(apiUrl)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", apiVersion)
            .addHeader("content-type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val responseText = withContext(Dispatchers.IO) {
            client.newCall(httpRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    error("Claude API error ${response.code}: $body")
                }
                body
            }
        }

        return parseResponse(responseText)
    }

    private fun buildRequestBody(request: AiRequest): JsonObject {
        val userContent = buildList {
            add(JsonObject(mapOf("type" to JsonPrimitive("text"), "text" to JsonPrimitive(buildUserMessage(request)))))

            request.screenState.screenshotBase64?.let { base64 ->
                add(
                    JsonObject(
                        mapOf(
                            "type" to JsonPrimitive("image"),
                            "source" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("base64"),
                                    "media_type" to JsonPrimitive("image/png"),
                                    "data" to JsonPrimitive(base64),
                                ),
                            ),
                        ),
                    ),
                )
            }
        }

        return JsonObject(
            mapOf(
                "model" to JsonPrimitive(model),
                "max_tokens" to JsonPrimitive(maxTokens),
                "system" to JsonPrimitive(SYSTEM_PROMPT),
                "tool_choice" to JsonObject(mapOf("type" to JsonPrimitive("any"))),
                "tools" to buildToolDefinitions(),
                "messages" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "role" to JsonPrimitive("user"),
                                "content" to JsonArray(userContent),
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun buildUserMessage(request: AiRequest): String = buildString {
        appendLine("Instruction type: ${request.instructionType.name}")
        appendLine("Instruction: ${request.instruction}")
        appendLine()
        if (request.routeContext.isNotEmpty()) {
            appendLine("Route context: ${request.routeContext}")
            appendLine()
        }
        if (request.rememberedValues.isNotEmpty()) {
            appendLine("Remembered values:")
            request.rememberedValues.forEach { (key, value) ->
                appendLine("  $key = $value")
            }
            appendLine()
        }
        appendLine("Current screen state:")
        append(request.screenState.toPromptString())
    }

    @Suppress("LongMethod")
    private fun buildToolDefinitions(): JsonArray {
        val tapProperties = JsonObject(
            mapOf(
                "nodeId" to jsonSchemaInt("ID of the node to tap"),
                "testTag" to jsonSchemaString("Test tag of the element to tap"),
                "text" to jsonSchemaString("Visible text of the element to tap"),
            ),
        )
        val typeTextProperties = JsonObject(
            mapOf(
                "nodeId" to jsonSchemaInt("ID of the node to type into"),
                "testTag" to jsonSchemaString("Test tag of the element to type into"),
                "text" to jsonSchemaString("Text to type"),
            ),
        )
        val directionEnum = JsonArray(listOf("UP", "DOWN", "LEFT", "RIGHT").map { JsonPrimitive(it) })
        val scrollProperties = JsonObject(
            mapOf(
                "direction" to JsonObject(
                    mapOf("type" to JsonPrimitive("string"), "enum" to directionEnum, "description" to JsonPrimitive("Scroll direction")),
                ),
                "nodeId" to jsonSchemaInt("ID of the scrollable node"),
                "testTag" to jsonSchemaString("Test tag of the scrollable element"),
            ),
        )
        val swipeProperties = JsonObject(
            mapOf(
                "direction" to JsonObject(
                    mapOf("type" to JsonPrimitive("string"), "enum" to directionEnum, "description" to JsonPrimitive("Swipe direction")),
                ),
            ),
        )
        val waitForProperties = JsonObject(
            mapOf(
                "testTag" to jsonSchemaString("Test tag to wait for"),
                "text" to jsonSchemaString("Text to wait for"),
                "timeoutMs" to JsonObject(
                    mapOf("type" to JsonPrimitive("integer"), "description" to JsonPrimitive("Timeout in milliseconds")),
                ),
            ),
        )
        val verifyProperties = JsonObject(
            mapOf(
                "passed" to JsonObject(
                    mapOf("type" to JsonPrimitive("boolean"), "description" to JsonPrimitive("Whether the assertion passed")),
                ),
                "reasoning" to jsonSchemaString("Explanation of the verification result"),
            ),
        )
        val rememberProperties = JsonObject(
            mapOf(
                "key" to jsonSchemaString("Key name to store the value under"),
                "value" to jsonSchemaString("Value extracted from the screen"),
                "reasoning" to jsonSchemaString("Explanation of what was extracted"),
            ),
        )

        return JsonArray(
            listOf(
                tool("tap", "Tap on a UI element", tapProperties),
                tool("type_text", "Type text into a field", typeTextProperties, listOf("text")),
                tool("scroll", "Scroll in a direction", scrollProperties, listOf("direction")),
                tool("swipe", "Swipe the screen", swipeProperties, listOf("direction")),
                tool("wait_for", "Wait for an element to appear", waitForProperties),
                tool(
                    "verify",
                    "Evaluate an assertion about the screen",
                    verifyProperties,
                    listOf("passed", "reasoning")
                ),
                tool(
                    "remember",
                    "Extract and remember a value from the screen",
                    rememberProperties,
                    listOf("key", "value", "reasoning")
                ),
            ),
        )
    }

    private fun tool(
        name: String,
        description: String,
        properties: JsonObject,
        required: List<String> = emptyList(),
    ): JsonObject = JsonObject(
        mapOf(
            "name" to JsonPrimitive(name),
            "description" to JsonPrimitive(description),
            "input_schema" to JsonObject(
                buildMap {
                    put("type", JsonPrimitive("object"))
                    put("properties", properties)
                    if (required.isNotEmpty()) {
                        put("required", JsonArray(required.map { JsonPrimitive(it) }))
                    }
                },
            ),
        ),
    )

    private fun jsonSchemaString(description: String): JsonObject = JsonObject(
        mapOf("type" to JsonPrimitive("string"), "description" to JsonPrimitive(description)),
    )

    private fun jsonSchemaInt(description: String): JsonObject = JsonObject(
        mapOf("type" to JsonPrimitive("integer"), "description" to JsonPrimitive(description)),
    )

    private fun parseResponse(responseText: String): AiResponse {
        val responseObj = json.parseToJsonElement(responseText).jsonObject
        val content = responseObj["content"]?.jsonArray
            ?: error("No content in Claude response: $responseText")

        var reasoning = ""
        var toolName: String? = null
        var toolInput: JsonObject? = null

        for (block in content) {
            val blockObj = block.jsonObject
            when (blockObj["type"]?.jsonPrimitive?.content) {
                "text" -> reasoning = blockObj["text"]?.jsonPrimitive?.content ?: ""
                "tool_use" -> {
                    toolName = blockObj["name"]?.jsonPrimitive?.content
                    toolInput = blockObj["input"]?.jsonObject
                }
            }
        }

        if (toolName == null || toolInput == null) {
            error("No tool_use block found in Claude response: $responseText")
        }

        return mapToolToResponse(toolName, toolInput, reasoning)
    }

    @Suppress("CyclomaticComplexity", "LongMethod")
    private fun mapToolToResponse(
        toolName: String,
        input: JsonObject,
        reasoning: String,
    ): AiResponse {
        return when (toolName) {
            "tap" -> AiResponse.PerformAction(
                action = UiAction.Tap(
                    nodeId = input["nodeId"]?.jsonPrimitive?.intOrNull,
                    testTag = input["testTag"]?.jsonPrimitive?.content,
                    text = input["text"]?.jsonPrimitive?.content,
                ),
                reasoning = reasoning,
            )
            "type_text" -> AiResponse.PerformAction(
                action = UiAction.TypeText(
                    nodeId = input["nodeId"]?.jsonPrimitive?.intOrNull,
                    testTag = input["testTag"]?.jsonPrimitive?.content,
                    text = input["text"]?.jsonPrimitive?.content
                        ?: error("type_text tool response missing required field 'text'"),
                ),
                reasoning = reasoning,
            )
            "scroll" -> AiResponse.PerformAction(
                action = UiAction.Scroll(
                    direction = Direction.valueOf(
                        input["direction"]?.jsonPrimitive?.content
                            ?: error("scroll tool response missing required field 'direction'"),
                    ),
                    nodeId = input["nodeId"]?.jsonPrimitive?.intOrNull,
                    testTag = input["testTag"]?.jsonPrimitive?.content,
                ),
                reasoning = reasoning,
            )
            "swipe" -> AiResponse.PerformAction(
                action = UiAction.Swipe(
                    direction = Direction.valueOf(
                        input["direction"]?.jsonPrimitive?.content
                            ?: error("swipe tool response missing required field 'direction'"),
                    ),
                ),
                reasoning = reasoning,
            )
            "wait_for" -> AiResponse.PerformAction(
                action = UiAction.WaitFor(
                    testTag = input["testTag"]?.jsonPrimitive?.content,
                    text = input["text"]?.jsonPrimitive?.content,
                    timeoutMs = input["timeoutMs"]?.jsonPrimitive?.longOrNull ?: 5000L,
                ),
                reasoning = reasoning,
            )
            "verify" -> AiResponse.VerifyResult(
                passed = input["passed"]?.jsonPrimitive?.booleanOrNull
                    ?: error("verify tool response missing required field 'passed'"),
                reasoning = input["reasoning"]?.jsonPrimitive?.content ?: reasoning,
            )
            "remember" -> AiResponse.RememberedValue(
                key = input["key"]?.jsonPrimitive?.content
                    ?: error("remember tool response missing required field 'key'"),
                value = input["value"]?.jsonPrimitive?.content
                    ?: error("remember tool response missing required field 'value'"),
                reasoning = input["reasoning"]?.jsonPrimitive?.content ?: reasoning,
            )
            else -> error("Unknown tool name from Claude: $toolName")
        }
    }

    companion object {
        private const val DEFAULT_API_URL = "https://api.anthropic.com/v1/messages"
        private const val DEFAULT_API_VERSION = "2023-06-01"
        const val DEFAULT_MODEL = "claude-sonnet-4-20250514"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        private const val SYSTEM_PROMPT =
            "You are a UI test automation agent. You are given the current screen state of an Android app " +
                "and a natural language instruction. Your job is to determine the correct action to perform.\n\n" +
                "The screen state shows UI nodes with their IDs, test tags, text content, capabilities, and positions.\n\n" +
                "When choosing a target node, prefer using testTag if available (most stable), then text, " +
                "then nodeId as a last resort.\n\n" +
                "Always respond by calling exactly one tool."
    }
}
