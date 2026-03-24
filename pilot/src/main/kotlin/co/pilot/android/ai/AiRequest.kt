package co.pilot.android.ai

import co.pilot.android.screen.ScreenState
import kotlinx.serialization.Serializable

@Serializable
data class AiRequest(
    val instruction: String,
    val instructionType: InstructionType,
    val screenState: ScreenState,
    val rememberedValues: Map<String, String>,
    val routeContext: String,
)

@Serializable
enum class InstructionType { ACTION, VERIFY, REMEMBER }
