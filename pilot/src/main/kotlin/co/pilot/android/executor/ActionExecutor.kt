package co.pilot.android.executor

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import co.pilot.android.ai.Direction
import co.pilot.android.ai.UiAction

interface ActionExecutable {
    fun execute(action: UiAction)
}

class ComposeActionExecutor(private val composeTestRule: ComposeTestRule) : ActionExecutable {

    override fun execute(action: UiAction) {
        when (action) {
            is UiAction.Tap -> executeTap(action)
            is UiAction.TypeText -> executeTypeText(action)
            is UiAction.Scroll -> executeScroll(action)
            is UiAction.Swipe -> executeSwipe(action)
            is UiAction.WaitFor -> executeWaitFor(action)
        }
    }

    private fun executeTap(action: UiAction.Tap) {
        findNode(testTag = action.testTag, text = action.text, nodeId = action.nodeId)
            .performClick()
    }

    private fun executeTypeText(action: UiAction.TypeText) {
        findNode(testTag = action.testTag, text = null, nodeId = action.nodeId)
            .performClick()
            .performTextInput(action.text)
    }

    private fun executeScroll(action: UiAction.Scroll) {
        val node = if (action.testTag != null || action.nodeId != null) {
            findNode(testTag = action.testTag, text = null, nodeId = action.nodeId)
        } else {
            composeTestRule.onRoot()
        }

        node.performTouchInput {
            when (action.direction) {
                Direction.UP -> swipeDown()
                Direction.DOWN -> swipeUp()
                Direction.LEFT -> swipeRight()
                Direction.RIGHT -> swipeLeft()
            }
        }
    }

    private fun executeSwipe(action: UiAction.Swipe) {
        composeTestRule.onRoot().performTouchInput {
            when (action.direction) {
                Direction.UP -> swipeUp()
                Direction.DOWN -> swipeDown()
                Direction.LEFT -> swipeLeft()
                Direction.RIGHT -> swipeRight()
            }
        }
    }

    private fun executeWaitFor(action: UiAction.WaitFor) {
        val description = when {
            action.testTag != null -> "testTag='${action.testTag}'"
            action.text != null -> "text='${action.text}'"
            else -> throw IllegalArgumentException("WaitFor must specify testTag or text")
        }

        try {
            composeTestRule.waitUntil(timeoutMillis = action.timeoutMs) {
                when {
                    action.testTag != null ->
                        composeTestRule.onAllNodesWithTag(action.testTag).fetchSemanticsNodes().isNotEmpty()

                    action.text != null ->
                        composeTestRule.onAllNodesWithText(action.text).fetchSemanticsNodes().isNotEmpty()

                    else -> error("Unreachable: WaitFor validated above")
                }
            }
        } catch (@Suppress("SwallowedException") e: Throwable) {
            throw AssertionError(
                "Timed out waiting for node matching $description after ${action.timeoutMs}ms",
                e,
            )
        }
    }

    private fun findNode(
        testTag: String?,
        text: String?,
        nodeId: Int?,
    ): SemanticsNodeInteraction {
        composeTestRule.waitForIdle()

        return when {
            testTag != null -> composeTestRule.onNodeWithTag(testTag)
            text != null -> composeTestRule.onNodeWithText(text)
            nodeId != null -> composeTestRule.onNode(
                SemanticsMatcher("nodeId == $nodeId") { node ->
                    node.id == nodeId
                },
            )
            else -> throw IllegalArgumentException("Action must specify testTag, text, or nodeId")
        }
    }
}
