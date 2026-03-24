package co.pilot.android.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class UiAction {

    @Serializable
    @SerialName("tap")
    data class Tap(
        val nodeId: Int? = null,
        val testTag: String? = null,
        val text: String? = null,
    ) : UiAction()

    @Serializable
    @SerialName("type_text")
    data class TypeText(
        val nodeId: Int? = null,
        val testTag: String? = null,
        val text: String,
    ) : UiAction()

    @Serializable
    @SerialName("scroll")
    data class Scroll(
        val direction: Direction,
        val nodeId: Int? = null,
        val testTag: String? = null,
    ) : UiAction()

    @Serializable
    @SerialName("swipe")
    data class Swipe(
        val direction: Direction,
    ) : UiAction()

    @Serializable
    @SerialName("wait_for")
    data class WaitFor(
        val testTag: String? = null,
        val text: String? = null,
        val timeoutMs: Long = 5000,
    ) : UiAction()
}

@Serializable
enum class Direction { UP, DOWN, LEFT, RIGHT }
