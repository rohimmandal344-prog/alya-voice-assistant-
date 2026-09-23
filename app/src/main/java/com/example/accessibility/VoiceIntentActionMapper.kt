package com.example.accessibility

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

data class VoiceIntentRequest(
    val intentName: String,
    val targetQuery: String? = null,
    val textToInput: String? = null,
    val parameters: Map<String, String> = emptyMap()
)

data class ActionNodeResult(
    val success: Boolean,
    val matchedNode: ActionNode? = null,
    val message: String,
    val actionExecuted: ActionType? = null
)

/**
 * VoiceIntentActionMapper
 * 
 * Centralized engine that maps spoken voice intent commands (e.g. "click send button",
 * "type my email in the email field", "toggle dark mode switch") to ActionNode objects,
 * traversing the active View hierarchy and executing clicks, input text, and gestures.
 */
class VoiceIntentActionMapper {

    companion object {
        private const val TAG = "VoiceIntentMapper"

        @Volatile
        private var instance: VoiceIntentActionMapper? = null

        fun getInstance(): VoiceIntentActionMapper {
            return instance ?: synchronized(this) {
                instance ?: VoiceIntentActionMapper().also { instance = it }
            }
        }
    }

    /**
     * Maps a voice intent request to an ActionNode from the root AccessibilityNodeInfo,
     * traverses the View hierarchy, and triggers the requested action.
     */
    fun mapAndExecuteIntent(
        rootAccessibilityNode: AccessibilityNodeInfo?,
        request: VoiceIntentRequest
    ): ActionNodeResult {
        if (rootAccessibilityNode == null) {
            return ActionNodeResult(
                success = false,
                message = "Active View hierarchy root is null. Accessibility Service may be inactive or restricted."
            )
        }

        // Build complete ActionNode hierarchy tree
        val rootActionNode = ActionNode.fromAccessibilityNode(rootAccessibilityNode)
            ?: return ActionNodeResult(
                success = false,
                message = "Failed to parse active View hierarchy into ActionNode tree."
            )

        val intent = request.intentName.lowercase().trim()
        val query = request.targetQuery ?: request.parameters["target"] ?: request.parameters["text"] ?: ""

        Log.i(TAG, "Mapping voice intent '$intent' with query '$query' across View hierarchy (${rootActionNode.children.size} root children)")

        return when {
            intent.contains("click") || intent.contains("tap") || intent.contains("press") || intent.contains("kholo") || intent.contains("open") -> {
                handleClickIntent(rootActionNode, query)
            }
            intent.contains("type") || intent.contains("input") || intent.contains("write") || intent.contains("enter_text") || intent.contains("fill") -> {
                val textToInput = request.textToInput ?: request.parameters["content"] ?: request.parameters["text_content"] ?: ""
                handleInputTextIntent(rootActionNode, query, textToInput)
            }
            intent.contains("toggle") || intent.contains("switch") -> {
                handleToggleIntent(rootActionNode, query)
            }
            intent.contains("long_click") || intent.contains("long_press") -> {
                handleLongClickIntent(rootActionNode, query)
            }
            intent.contains("scroll") -> {
                val isForward = !request.parameters["direction"].equals("up", ignoreCase = true)
                handleScrollIntent(rootActionNode, isForward)
            }
            intent.contains("traverse") || intent.contains("get_hierarchy") -> {
                ActionNodeResult(
                    success = true,
                    matchedNode = rootActionNode,
                    message = "Successfully traversed View hierarchy. Found ${rootActionNode.flattenActionableNodes().size} actionable ActionNodes.",
                    actionExecuted = ActionType.GLOBAL_ACTION
                )
            }
            else -> {
                // Fallback: search for any matching node with query
                handleGenericIntent(rootActionNode, query)
            }
        }
    }

    private fun handleClickIntent(root: ActionNode, query: String): ActionNodeResult {
        if (query.isBlank()) {
            // Click focused or main action node
            val candidate = root.flattenActionableNodes().firstOrNull { it.isFocused || it.nodeType == ActionNodeType.BUTTON }
            if (candidate != null && candidate.performClick()) {
                return ActionNodeResult(
                    success = true,
                    matchedNode = candidate,
                    message = "Clicked default actionable ActionNode: ${candidate.getLabel()}",
                    actionExecuted = ActionType.CLICK
                )
            }
            return ActionNodeResult(success = false, message = "No target specified for click intent.")
        }

        val matchedNode = root.findMatchingNode(query)
            ?: root.findMatchingNode(query, requiredType = ActionNodeType.BUTTON)
            ?: root.findMatchingNode(query, requiredType = ActionNodeType.IMAGE_BUTTON)

        if (matchedNode != null) {
            val success = matchedNode.performClick()
            return ActionNodeResult(
                success = success,
                matchedNode = matchedNode,
                message = if (success) "Successfully clicked ActionNode '${matchedNode.getLabel()}'" else "Matched ActionNode '${matchedNode.getLabel()}' but click failed.",
                actionExecuted = ActionType.CLICK
            )
        }

        return ActionNodeResult(
            success = false,
            message = "Could not find any clickable ActionNode matching query '$query' in View hierarchy."
        )
    }

    private fun handleInputTextIntent(root: ActionNode, fieldQuery: String, textToInput: String): ActionNodeResult {
        val editableNodes = root.findEditableNodes()

        if (editableNodes.isEmpty()) {
            return ActionNodeResult(
                success = false,
                message = "No editable input fields (EditText ActionNodes) found in active View hierarchy."
            )
        }

        // Find best field match, or fallback to currently focused / first editable node
        val targetField = if (fieldQuery.isNotBlank()) {
            root.findMatchingNode(fieldQuery, requiredType = ActionNodeType.EDIT_TEXT)
                ?: editableNodes.firstOrNull { it.getLabel().lowercase().contains(fieldQuery.lowercase()) }
                ?: editableNodes.firstOrNull { it.isFocused }
                ?: editableNodes.first()
        } else {
            editableNodes.firstOrNull { it.isFocused } ?: editableNodes.first()
        }

        val success = targetField.performInputText(textToInput)
        return ActionNodeResult(
            success = success,
            matchedNode = targetField,
            message = if (success) "Successfully input text into ActionNode field '${targetField.getLabel()}'" else "Failed to set text on ActionNode '${targetField.getLabel()}'",
            actionExecuted = ActionType.INPUT_TEXT
        )
    }

    private fun handleToggleIntent(root: ActionNode, query: String): ActionNodeResult {
        val switchNode = root.findMatchingNode(query, requiredType = ActionNodeType.SWITCH_OR_CHECKBOX)
            ?: root.findMatchingNode(query)

        if (switchNode != null) {
            val success = switchNode.performToggle()
            return ActionNodeResult(
                success = success,
                matchedNode = switchNode,
                message = if (success) "Successfully toggled ActionNode '${switchNode.getLabel()}'" else "Failed to toggle ActionNode '${switchNode.getLabel()}'",
                actionExecuted = ActionType.TOGGLE
            )
        }

        return ActionNodeResult(
            success = false,
            message = "No switch or checkable ActionNode matching '$query' found."
        )
    }

    private fun handleLongClickIntent(root: ActionNode, query: String): ActionNodeResult {
        val node = root.findMatchingNode(query)
        if (node != null) {
            val success = node.performLongClick()
            return ActionNodeResult(
                success = success,
                matchedNode = node,
                message = if (success) "Successfully long-clicked ActionNode '${node.getLabel()}'" else "Failed to long-click ActionNode '${node.getLabel()}'",
                actionExecuted = ActionType.LONG_CLICK
            )
        }
        return ActionNodeResult(success = false, message = "Target ActionNode for long-click '$query' not found.")
    }

    private fun handleScrollIntent(root: ActionNode, isForward: Boolean): ActionNodeResult {
        val scrollableNodes = root.flattenActionableNodes().filter { it.isScrollable }
        val targetScrollNode = scrollableNodes.firstOrNull()

        if (targetScrollNode != null) {
            val success = targetScrollNode.performScroll(isForward)
            return ActionNodeResult(
                success = success,
                matchedNode = targetScrollNode,
                message = "Scrolled ActionNode '${targetScrollNode.getLabel()}' (Forward=$isForward)",
                actionExecuted = if (isForward) ActionType.SCROLL_FORWARD else ActionType.SCROLL_BACKWARD
            )
        }

        return ActionNodeResult(
            success = false,
            message = "No scrollable container ActionNode found in View hierarchy."
        )
    }

    private fun handleGenericIntent(root: ActionNode, query: String): ActionNodeResult {
        val node = root.findMatchingNode(query)
        if (node != null) {
            val success = if (node.isEditable) {
                node.performInputText(query)
            } else {
                node.performClick()
            }
            return ActionNodeResult(
                success = success,
                matchedNode = node,
                message = "Executed generic action on matched ActionNode '${node.getLabel()}'",
                actionExecuted = if (node.isEditable) ActionType.INPUT_TEXT else ActionType.CLICK
            )
        }
        return ActionNodeResult(
            success = false,
            message = "No matching ActionNode found for query '$query'"
        )
    }
}
