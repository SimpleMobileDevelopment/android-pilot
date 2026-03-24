package co.pilot.android.screen

import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import java.io.ByteArrayOutputStream

interface ScreenCapture {
    fun captureScreenState(includeScreenshot: Boolean = true): ScreenState
}

class ComposeScreenReader(private val composeTestRule: ComposeTestRule) : ScreenCapture {

    override fun captureScreenState(includeScreenshot: Boolean): ScreenState {
        val rootNodes = composeTestRule.onAllNodes(isRoot()).fetchSemanticsNodes()
        val nodes = mutableListOf<NodeInfo>()
        for (root in rootNodes) {
            collectNodes(root, nodes)
        }

        val screenshotBase64 = if (includeScreenshot) captureScreenshot() else null

        return ScreenState(
            nodes = nodes,
            screenshotBase64 = screenshotBase64,
        )
    }

    private fun collectNodes(node: SemanticsNode, result: MutableList<NodeInfo>) {
        result.add(node.toNodeInfo())
        node.children.forEach { child ->
            collectNodes(child, result)
        }
    }

    private fun SemanticsNode.toNodeInfo(): NodeInfo {
        val config = this.config
        return NodeInfo(
            id = this.id,
            testTag = config.getOrNull(SemanticsProperties.TestTag),
            contentDescription = config.getOrNull(SemanticsProperties.ContentDescription)
                ?.joinToString(", "),
            text = config.getOrNull(SemanticsProperties.Text)
                ?.joinToString(", ") { it.text },
            isClickable = config.contains(SemanticsActions.OnClick),
            isEditable = config.contains(SemanticsActions.SetText),
            isScrollable = config.contains(SemanticsActions.ScrollBy),
            isToggleable = config.contains(SemanticsProperties.ToggleableState),
            isSelected = config.getOrNull(SemanticsProperties.Selected) ?: false,
            isEnabled = !config.contains(SemanticsProperties.Disabled),
            boundsInRoot = this.boundsInRoot.let { rect ->
                BoundsInfo(
                    left = rect.left,
                    top = rect.top,
                    right = rect.right,
                    bottom = rect.bottom,
                )
            },
            children = this.children.map { it.id },
        )
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    private fun captureScreenshot(): String? = try {
        val rootNodes = composeTestRule.onAllNodes(isRoot())
        val imageBitmap = rootNodes[0].captureToImage()
        val bitmap = imageBitmap.asAndroidBitmap()
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 80, stream)
        Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    } catch (_: Exception) {
        null
    }
}
