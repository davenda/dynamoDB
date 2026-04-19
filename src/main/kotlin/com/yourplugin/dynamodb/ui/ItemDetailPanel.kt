package com.yourplugin.dynamodb.ui

import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.awt.BorderLayout
import java.awt.Font
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Displays a single DynamoDB item as an expandable tree.
 * Useful for nested maps and lists which don't render well in the table.
 */
class ItemDetailPanel : JPanel(BorderLayout()) {

    private val root = DefaultMutableTreeNode("Item")
    private val treeModel = DefaultTreeModel(root)
    private val tree = Tree(treeModel).apply {
        isRootVisible = true
        showsRootHandles = true
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }
    private val emptyLabel = JLabel("Select a row to see item detail", SwingConstants.CENTER).apply {
        font = font.deriveFont(Font.ITALIC)
        foreground = java.awt.Color(150, 150, 150)
    }

    init {
        add(emptyLabel, BorderLayout.CENTER)
    }

    fun showItem(item: Map<String, AttributeValue>) {
        remove(emptyLabel)
        root.removeAllChildren()
        item.entries.sortedBy { it.key }.forEach { (k, v) ->
            root.add(buildNode(k, v))
        }
        treeModel.reload()
        // Expand all nodes
        for (i in 0 until tree.rowCount) tree.expandRow(i)

        if (components.none { it is JScrollPane }) {
            add(JBScrollPane(tree), BorderLayout.CENTER)
        }
        revalidate()
        repaint()
    }

    fun clear() {
        root.removeAllChildren()
        treeModel.reload()
        removeAll()
        add(emptyLabel, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    private fun buildNode(key: String, value: AttributeValue): DefaultMutableTreeNode {
        return when {
            value.s()    != null -> DefaultMutableTreeNode("$key: \"${value.s()}\"  [S]")
            value.n()    != null -> DefaultMutableTreeNode("$key: ${value.n()}  [N]")
            value.bool() != null -> DefaultMutableTreeNode("$key: ${value.bool()}  [BOOL]")
            value.nul()  == true -> DefaultMutableTreeNode("$key: null  [NULL]")
            value.b()    != null -> DefaultMutableTreeNode("$key: <binary>  [B]")
            value.ss().isNotEmpty() -> DefaultMutableTreeNode("$key  [SS]").also { parent ->
                value.ss().forEach { parent.add(DefaultMutableTreeNode("\"$it\"")) }
            }
            value.ns().isNotEmpty() -> DefaultMutableTreeNode("$key  [NS]").also { parent ->
                value.ns().forEach { parent.add(DefaultMutableTreeNode(it)) }
            }
            value.l().isNotEmpty() -> DefaultMutableTreeNode("$key  [L: ${value.l().size}]").also { parent ->
                value.l().forEachIndexed { i, v -> parent.add(buildNode("[$i]", v)) }
            }
            value.m().isNotEmpty() -> DefaultMutableTreeNode("$key  [M: ${value.m().size}]").also { parent ->
                value.m().entries.sortedBy { it.key }.forEach { (k, v) -> parent.add(buildNode(k, v)) }
            }
            else -> DefaultMutableTreeNode("$key: ?")
        }
    }
}
