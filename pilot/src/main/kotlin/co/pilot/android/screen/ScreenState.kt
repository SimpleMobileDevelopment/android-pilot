package co.pilot.android.screen

import kotlinx.serialization.Serializable

@Serializable
data class ScreenState(
    val nodes: List<NodeInfo> = emptyList(),
    val screenshotBase64: String? = null,
)

@Serializable
data class NodeInfo(
    val id: Int,
    val testTag: String? = null,
    val contentDescription: String? = null,
    val text: String? = null,
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isToggleable: Boolean = false,
    val isSelected: Boolean = false,
    val isEnabled: Boolean = true,
    val boundsInRoot: BoundsInfo? = null,
    val children: List<Int> = emptyList(),
)

@Serializable
data class BoundsInfo(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

fun ScreenState.toPromptString(): String = buildString {
    val relevantNodes = nodes.filter { node ->
        node.text != null ||
            node.testTag != null ||
            node.contentDescription != null ||
            node.isClickable ||
            node.isEditable ||
            node.isScrollable
    }

    if (relevantNodes.isEmpty()) {
        append("(empty screen)")
        return@buildString
    }

    appendLine("Screen nodes:")
    for (node in relevantNodes) {
        append("  [${node.id}]")

        node.testTag?.let { append(" tag=\"$it\"") }
        node.text?.let { append(" text=\"$it\"") }
        node.contentDescription?.let { append(" desc=\"$it\"") }

        val capabilities = buildList {
            if (node.isClickable) add("clickable")
            if (node.isEditable) add("editable")
            if (node.isScrollable) add("scrollable")
            if (node.isToggleable) add("toggleable")
            if (node.isSelected) add("selected")
            if (!node.isEnabled) add("disabled")
        }
        if (capabilities.isNotEmpty()) {
            append(" [${capabilities.joinToString(", ")}]")
        }

        node.boundsInRoot?.let { b ->
            append(" @(${b.left.toInt()},${b.top.toInt()},${b.right.toInt()},${b.bottom.toInt()})")
        }

        appendLine()
    }
}
