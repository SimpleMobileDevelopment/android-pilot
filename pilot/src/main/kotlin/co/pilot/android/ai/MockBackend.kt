package co.pilot.android.ai

import java.util.Collections

class MockBackend(
    private val handler: (AiRequest) -> AiResponse,
) : AiBackend {
    val requestHistory: MutableList<AiRequest> = Collections.synchronizedList(mutableListOf())

    override suspend fun planAction(request: AiRequest): AiResponse {
        requestHistory.add(request)
        return handler(request)
    }
}
