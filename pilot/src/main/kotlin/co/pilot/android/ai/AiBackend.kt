package co.pilot.android.ai

interface AiBackend {
    suspend fun planAction(request: AiRequest): AiResponse
}
