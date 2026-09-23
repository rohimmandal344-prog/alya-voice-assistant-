package com.example.accessibility

import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

enum class ActionNodeType {
    BUTTON,
    EDIT_TEXT,
    SWITCH_OR_CHECKBOX,
    IMAGE_BUTTON,
    TEXT_VIEW,
    SCROLL_CONTAINER,
    CONTAINER,
    UNKNOWN
}

enum class ActionType {
    CLICK,
    INPUT_TEXT,
    TOGGLE,
    LONG_CLICK,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    GLOBAL_ACTION
}

/**
 * ActionNode
 * 
 * High-level data model representing an interactive view component in the active application's
 * View hierarchy. Used by Alya Accessibility Automation to traverse UI elements, map voice
 * intent commands, trigger clicks, input text, and toggle state on behalf of the user.
 */
data class ActionNode(
    val id: String,
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val packageName: String?,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isCheckable: Boolean,
    val isChecked: Boolean,
    val isFocused: Boolean,
    val isScrollable: Boolean,
    val isVisibleToUser: Boolean,
    val nodeType: ActionNodeType,
    val supportedActions: List<ActionType>,
    @Transient val accessibilityNode: AccessibilityNodeInfo? = null,
    val children: MutableList<ActionNode> = mutableListOf()
) {

    companion object {
        private const val TAG = "ActionNode"

        /**
         * Recursively parses an AccessibilityNodeInfo and constructs an ActionNode view hierarchy tree.
         */
        fun fromAccessibilityNode(node: AccessibilityNodeInfo?, depth: Int = 0): ActionNode? {
            if (node == null) return null

            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            val text = node.text?.toString()?.trim()
            val contentDesc = node.contentDescription?.toString()?.trim()
            val className = node.className?.toString() ?: ""
            val packageName = node.packageName?.toString() ?: ""
            val idRes = node.viewIdResourceName ?: "node_${System.identityHashCode(node)}"

            val isClickable = node.isClickable
            val isEditable = node.isEditable || className.contains("EditText", ignoreCase = true)
            val isCheckable = node.isCheckable
            val isChecked = node.isChecked
            val isFocused = node.isFocused
            val isScrollable = node.isScrollable
            val isVisible = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                node.isVisibleToUser
            } else {
                true
            }

            val nodeType = when {
                isEditable -> ActionNodeType.EDIT_TEXT
                isCheckable || className.contains("Switch", ignoreCase = true) || className.contains("CheckBox", ignoreCase = true) -> ActionNodeType.SWITCH_OR_CHECKBOX
                className.contains("ImageButton", ignoreCase = true) -> ActionNodeType.IMAGE_BUTTON
                isClickable || className.contains("Button", ignoreCase = true) -> ActionNodeType.BUTTON
                isScrollable || className.contains("ScrollView", ignoreCase = true) || className.contains("RecyclerView", ignoreCase = true) -> ActionNodeType.SCROLL_CONTAINER
                className.contains("TextView", ignoreCase = true) -> ActionNodeType.TEXT_VIEW
                else -> ActionNodeType.CONTAINER
            }

            val actions = mutableListOf<ActionType>()
            if (isClickable || nodeType == ActionNodeType.BUTTON || nodeType == ActionNodeType.IMAGE_BUTTON) actions.add(ActionType.CLICK)
            if (isEditable) actions.add(ActionType.INPUT_TEXT)
            if (isCheckable) actions.add(ActionType.TOGGLE)
            if (node.isLongClickable) actions.add(ActionType.LONG_CLICK)
            if (isScrollable) {
                actions.add(ActionType.SCROLL_FORWARD)
                actions.add(ActionType.SCROLL_BACKWARD)
            }

            val actionNode = ActionNode(
                id = idRes,
                text = text,
                contentDescription = contentDesc,
                className = className,
                packageName = packageName,
                bounds = bounds,
                isClickable = isClickable,
                isEditable = isEditable,
                isCheckable = isCheckable,
                isChecked = isChecked,
                isFocused = isFocused,
                isScrollable = isScrollable,
                isVisibleToUser = isVisible,
                nodeType = nodeType,
                supportedActions = actions,
                accessibilityNode = node
            )

            // Recursively construct child ActionNodes
            if (depth < 15) { // Protect against infinite depth
                for (i in 0 until node.childCount) {
                    val childNodeInfo = node.getChild(i) ?: continue
                    val childActionNode = fromAccessibilityNode(childNodeInfo, depth + 1)
                    if (childActionNode != null) {
                        actionNode.children.add(childActionNode)
                    }
                }
            }

            return actionNode
        }
    }

    /**
     * Executes a click action on this ActionNode via AccessibilityNodeInfo or parent traversal.
     */
    fun performClick(): Boolean {
        val node = accessibilityNode
        if (node != null) {
            if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.i(TAG, "Successfully clicked ActionNode: ${getLabel()}")
                return true
            }
            // Try parent click traversal
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.i(TAG, "Successfully clicked parent of ActionNode: ${getLabel()}")
                    return true
                }
                parent = parent.parent
            }
        }
        Log.w(TAG, "Failed to click ActionNode: ${getLabel()}")
        return false
    }

    /**
     * Inputs text into an editable ActionNode.
     */
    fun performInputText(inputText: String): Boolean {
        val node = accessibilityNode ?: return false
        if (!isEditable && !node.isEditable && !className.orEmpty().contains("EditText", ignoreCase = true)) {
            Log.w(TAG, "ActionNode ${getLabel()} is not editable.")
            return false
        }

        // Focus field first
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, inputText)
        }
        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        if (success) {
            Log.i(TAG, "Successfully set text '$inputText' on ActionNode: ${getLabel()}")
        } else {
            Log.w(TAG, "Failed to set text on ActionNode: ${getLabel()}")
        }
        return success
    }

    /**
     * Toggles a checkable/switch ActionNode.
     */
    fun performToggle(): Boolean {
        val node = accessibilityNode ?: return false
        if (isCheckable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            Log.i(TAG, "Successfully toggled ActionNode: ${getLabel()}")
            return true
        }
        return performClick()
    }

    /**
     * Performs a long click on this ActionNode.
     */
    fun performLongClick(): Boolean {
        val node = accessibilityNode ?: return false
        if (node.isLongClickable && node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
            Log.i(TAG, "Successfully long-clicked ActionNode: ${getLabel()}")
            return true
        }
        return false
    }

    /**
     * Scrolls this ActionNode forward or backward.
     */
    fun performScroll(forward: Boolean): Boolean {
        val node = accessibilityNode ?: return false
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        val success = node.performAction(action)
        Log.i(TAG, "Perform scroll (forward=$forward) on ActionNode: ${getLabel()} -> success=$success")
        return success
    }

    /**
     * Returns best descriptive label for this ActionNode (text, content description, or ID).
     */
    fun getLabel(): String {
        return text?.takeIf { it.isNotBlank() }
            ?: contentDescription?.takeIf { it.isNotBlank() }
            ?: id.takeIf { it.isNotBlank() }
            ?: className
            ?: "UnlabeledNode"
    }

    /**
     * Flatten view tree into a list of all actionable descendant nodes.
     */
    fun flattenActionableNodes(): List<ActionNode> {
        val result = mutableListOf<ActionNode>()
        if (isClickable || isEditable || isCheckable || isScrollable) {
            result.add(this)
        }
        for (child in children) {
            result.addAll(child.flattenActionableNodes())
        }
        return result
    }

    /**
     * Traverses the hierarchy to find an ActionNode matching a voice intent query string.
     */
    fun findMatchingNode(query: String, requiredType: ActionNodeType? = null): ActionNode? {
        val cleanQuery = query.lowercase().trim()
        if (cleanQuery.isBlank()) return null

        val candidates = flattenActionableNodes()

        // 1. Exact match on text or content description
        candidates.firstOrNull { node ->
            (requiredType == null || node.nodeType == requiredType) &&
                    (node.text?.lowercase() == cleanQuery || node.contentDescription?.lowercase() == cleanQuery)
        }?.let { return it }

        // 2. Contains match on text or content description
        candidates.firstOrNull { node ->
            (requiredType == null || node.nodeType == requiredType) &&
                    ((node.text?.lowercase()?.contains(cleanQuery) == true) ||
                     (node.contentDescription?.lowercase()?.contains(cleanQuery) == true))
        }?.let { return it }

        // 3. ID match
        candidates.firstOrNull { node ->
            (requiredType == null || node.nodeType == requiredType) &&
                    node.id.lowercase().contains(cleanQuery)
        }?.let { return it }

        return null
    }

    /**
     * Returns all editable nodes in the view hierarchy (e.g. form fields).
     */
    fun findEditableNodes(): List<ActionNode> {
        return flattenActionableNodes().filter { it.isEditable }
    }
}
