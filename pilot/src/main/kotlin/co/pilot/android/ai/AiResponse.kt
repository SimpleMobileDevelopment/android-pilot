package co.pilot.android.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class AiResponse {
    abstract val reasoning: String

    @Serializable
    @SerialName("perform_action")
    data class PerformAction(
        val action: UiAction,
        override val reasoning: String,
    ) : AiResponse()

    @Serializable
    @SerialName("verify_result")
    data class VerifyResult(
        val passed: Boolean,
        override val reasoning: String,
    ) : AiResponse()

    @Serializable
    @SerialName("remembered_value")
    data class RememberedValue(
        val key: String,
        val value: String,
        override val reasoning: String,
    ) : AiResponse()
}
