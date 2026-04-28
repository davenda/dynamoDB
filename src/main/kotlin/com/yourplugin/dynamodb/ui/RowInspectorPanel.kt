package com.yourplugin.dynamodb.ui

import com.intellij.icons.AllIcons
import com.yourplugin.dynamodb.model.TableSchema
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.BasicStroke
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.MatteBorder

/**
 * 360 px slide-in row inspector.
 *
 * Shows the selected item's attributes, raw JSON, and (stub) history.
 * Call [showItem] to populate; [clear] to show the empty state.
 */
class RowInspectorPanel(
    private val onClose: () -> Unit,
    private val onEdit: (item: Map<String, AttributeValue>) -> Unit,
    private val getHistory: (Map<String, AttributeValue>) -> List<QueryRunnerPanel.ItemHistoryEntry> = { emptyList() },
) : JPanel(BorderLayout()) {

    companion object { const val WIDTH = 360 }

    private var currentItem: Map<String, AttributeValue>? = null
    private var currentSchema: TableSchema? = null
    private var activeTab = "attrs"

    // ── Header ────────────────────────────────────────────────────────────────
    private val idLabel   = JLabel("—").apply { foreground = DColors.accent; font = Font(Font.MONOSPACED, Font.BOLD, 12) }
    private val nameLabel = JLabel("").apply { foreground = DColors.fg1; font = font.deriveFont(12f) }

    private val editBtn = miniBtn(AllIcons.Actions.Edit, "Edit item") { currentItem?.let { onEdit(it) } }
    private val copyBtn = miniBtn(AllIcons.Actions.Copy, "Copy JSON") { copyAsJson() }

    private val header = JPanel().apply {
        layout        = BoxLayout(this, BoxLayout.LINE_AXIS)
        background    = DColors.bg1
        border = BorderFactory.createCompoundBorder(
            MatteBorder(0, 0, 1, 0, DColors.line),
            BorderFactory.createEmptyBorder(0, 12, 0, 8))
        preferredSize = Dimension(Int.MAX_VALUE, 44)
        maximumSize   = Dimension(Int.MAX_VALUE, 44)
        minimumSize   = Dimension(0, 44)

        idLabel.alignmentY = Component.CENTER_ALIGNMENT
        add(idLabel)
        add(Box.createHorizontalGlue())
        add(headerBtnGroup(editBtn, copyBtn))
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────
    private val tabIds = listOf("attrs" to "Attributes", "json" to "JSON", "history" to "History")
    private val tabBtns = tabIds.map { (id, lbl) -> id to makeTab(id, lbl) }.toMap()
    private val tabStrip = JPanel(GridLayout(1, tabIds.size)).apply {
        background = DColors.bg1
        border = MatteBorder(0, 0, 1, 0, DColors.line)
        preferredSize = Dimension(0, 32)
        maximumSize  = Dimension(Int.MAX_VALUE, 32)
        tabBtns.values.forEach { add(it) }
    }

    // ── Body panels ───────────────────────────────────────────────────────────
    private val attrsPanel   = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); background = DColors.bg1 }
    private val jsonArea = object : JTextPane() {
        // disable line wrapping so long values stay on one line
        override fun getScrollableTracksViewportWidth() = false
    }.apply {
        isEditable = false; background = DColors.bg1
        font  = Font(Font.MONOSPACED, Font.PLAIN, 12)
        border = BorderFactory.createEmptyBorder(10, 12, 10, 12)
    }
    private val historyPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); background = DColors.bg1 }

    private val bodyCards = CardLayout()
    private val bodyPanel = JPanel(bodyCards).apply {
        background = DColors.bg1
        add(JScrollPane(attrsPanel).apply { border = null; viewport.background = DColors.bg1 }, "attrs")
        add(JScrollPane(jsonArea).apply   { border = null; viewport.background = DColors.bg1 }, "json")
        add(JScrollPane(historyPanel).apply { border = null; viewport.background = DColors.bg1 }, "history")
    }

    // ── Empty state ───────────────────────────────────────────────────────────
    private val emptyLabel = JLabel("Select a row to inspect").apply {
        foreground = DColors.fg3; font = font.deriveFont(12f)
        horizontalAlignment = SwingConstants.CENTER
    }

    // topBar must be a field — showEmpty() calls removeAll() which would lose a local var
    private val topBar = JPanel(BorderLayout()).apply {
        background = DColors.bg1
        add(header,   BorderLayout.NORTH)
        add(tabStrip, BorderLayout.SOUTH)
    }

    init {
        background    = DColors.bg1
        preferredSize = Dimension(WIDTH, 0)
        minimumSize   = Dimension(160, 0)          // allow drag narrower
        maximumSize   = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)   // allow drag wider
        border = MatteBorder(0, 1, 0, 0, DColors.line)

        switchTab("attrs")
        showEmpty()
    }

    // ── Public API ────────────────────────────────────────────────────────────
    fun showItem(item: Map<String, AttributeValue>, schema: TableSchema?) {
        currentItem   = item
        currentSchema = schema

        val pk = schema?.partitionKey?.name
        val id = pk?.let { item[it]?.displayValue() } ?: item.keys.firstOrNull()?.let { item[it]?.displayValue() } ?: "—"
        idLabel.text   = id
        nameLabel.text = schema?.tableName ?: ""

        populateAttrs(item, schema)
        populateJson(item)
        populateHistory()

        // Re-add layout components (showEmpty removes them via removeAll)
        removeAll()
        add(topBar,    BorderLayout.NORTH)
        add(bodyPanel, BorderLayout.CENTER)
        bodyCards.show(bodyPanel, activeTab)

        revalidate(); repaint()
    }

    fun setSchema(schema: TableSchema?) { currentSchema = schema }

    fun clear() { showEmpty() }

    // ── Tab switching ─────────────────────────────────────────────────────────
    private fun makeTab(id: String, label: String) = object : JButton(label) {
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = DColors.bg1; g2.fillRect(0, 0, width, height)
            if (activeTab == id) { g2.color = DColors.accent; g2.fillRect(0, height - 2, width, 2) }
            super.paintComponent(g)
        }
    }.apply {
        isBorderPainted = false; isContentAreaFilled = false; isFocusPainted = false
        foreground = DColors.fg2; font = font.deriveFont(Font.PLAIN, 12f)
        addActionListener { switchTab(id) }
    }

    private fun switchTab(id: String) {
        activeTab = id
        bodyCards.show(bodyPanel, id)
        tabBtns.forEach { (tid, btn) ->
            btn.foreground = if (tid == activeTab) DColors.fg0 else DColors.fg2
            btn.font = btn.font.deriveFont(Font.PLAIN)
        }
        tabStrip.repaint()
    }

    // ── Attributes tab ────────────────────────────────────────────────────────
    private fun populateAttrs(item: Map<String, AttributeValue>, schema: TableSchema?) {
        attrsPanel.removeAll()
        val pkName = schema?.partitionKey?.name
        val skName = schema?.sortKey?.name
        val sortedKeys = item.keys.sortedWith(compareBy({ it != pkName }, { it != skName }, { it }))
        sortedKeys.forEach { key ->
            val av   = item[key] ?: return@forEach
            val isPk = key == pkName
            val isSk = key == skName
            val type = schema?.attributeDefinitions?.firstOrNull { it.name == key }?.type?.name ?: inferType(av)
            attrsPanel.add(attrRow(key, type, isPk, isSk, av))
        }
        attrsPanel.revalidate(); attrsPanel.repaint()
    }

    /**
     * Single-line attribute row: [name  [S] [PK?] [SK?]     value]
     */
    private fun attrRow(name: String, type: String, isPk: Boolean, isSk: Boolean, av: AttributeValue): JComponent {
        return JPanel(BorderLayout()).apply {
            background  = DColors.bg1
            border = BorderFactory.createCompoundBorder(
                MatteBorder(0, 0, 1, 0, DColors.line),
                BorderFactory.createEmptyBorder(0, 12, 0, 12))
            preferredSize = Dimension(0, 34)
            maximumSize   = Dimension(Int.MAX_VALUE, 34)
            minimumSize   = Dimension(0, 34)
            alignmentX    = LEFT_ALIGNMENT

            // Left: name + type badge + optional PK / SK badge — fixed 160px wide
            val left = JPanel().apply {
                layout        = BoxLayout(this, BoxLayout.LINE_AXIS)
                isOpaque      = false
                preferredSize = Dimension(160, 34)
                minimumSize   = Dimension(80,  0)
                maximumSize   = Dimension(200, Int.MAX_VALUE)

                add(JLabel(name).apply {
                    foreground = if (isPk || isSk) DColors.accent else DColors.fg2
                    font       = Font(Font.MONOSPACED, Font.PLAIN, 11)
                    alignmentY = Component.CENTER_ALIGNMENT
                })
                add(Box.createHorizontalStrut(5))
                add(typePill(type))
                if (isPk) { add(Box.createHorizontalStrut(3)); add(keyPill("PK", DColors.accent, DColors.bg1)) }
                if (isSk) { add(Box.createHorizontalStrut(3)); add(keyPill("SK", DColors.warn,   DColors.bg1)) }
            }
            add(left, BorderLayout.WEST)
            add(semanticLabel(av, isPk, isSk), BorderLayout.CENTER)
        }
    }

    private fun semanticLabel(av: AttributeValue, isPk: Boolean, isSk: Boolean): JComponent {
        val (color, font) = when {
            isPk -> DColors.accent to Font(Font.MONOSPACED, Font.PLAIN, 11)
            isSk -> DColors.warn   to Font(Font.MONOSPACED, Font.PLAIN, 11)
            else -> DColors.fg2    to Font(Font.SANS_SERIF,  Font.PLAIN, 11)
        }
        return JLabel(av.displayValue()).apply {
            foreground = color
            this.font  = font
            border     = BorderFactory.createEmptyBorder(0, 8, 0, 8)
        }
    }

    // ── JSON tab ──────────────────────────────────────────────────────────────
    private fun populateJson(item: Map<String, AttributeValue>) {
        val sb = StringBuilder("{\n")
        item.entries.sortedBy { it.key }.forEachIndexed { i, (k, v) ->
            sb.append("  \"$k\": ${v.toJsonValue()}")
            if (i < item.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("}")
        applyJsonHighlight(sb.toString())
    }

    private fun applyJsonHighlight(json: String) {
        val doc = jsonArea.styledDocument
        doc.remove(0, doc.length)

        fun attr(c: Color) = javax.swing.text.SimpleAttributeSet().also { a ->
            javax.swing.text.StyleConstants.setForeground(a, c)
            javax.swing.text.StyleConstants.setFontFamily(a, Font.MONOSPACED)
            javax.swing.text.StyleConstants.setFontSize(a, 12)
        }
        val keyAttr   = attr(DColors.fg0)    // key names — bright white
        val strAttr   = attr(DColors.synNum)  // string values — amber/yellow
        val numAttr   = attr(DColors.fg1)     // numbers — neutral
        val boolAttr  = attr(DColors.synKw)   // true / false / null — purple
        val punctAttr = attr(DColors.fg2)     // { } : , — dim
        val wsAttr    = attr(DColors.fg1)     // whitespace

        val tokenRe = Regex(""""(?:[^"\\]|\\.)*"|true|false|null|-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?|[{}\[\]:,]|\s+""")
        for (m in tokenRe.findAll(json)) {
            val tok = m.value
            val a = when {
                tok.isBlank() -> wsAttr
                tok in setOf("{", "}", "[", "]", ":", ",") -> punctAttr
                tok == "true" || tok == "false" || tok == "null" -> boolAttr
                tok.startsWith('"') -> {
                    // key if the next non-whitespace char after this token is ':'
                    val after = json.substring(m.range.last + 1).trimStart()
                    if (after.startsWith(':')) keyAttr else strAttr
                }
                else -> numAttr
            }
            doc.insertString(doc.length, tok, a)
        }
        jsonArea.caretPosition = 0
    }

    private fun AttributeValue.toJsonValue(): String = when {
        s()    != null -> "\"${s()!!.replace("\"", "\\\"")}\""
        n()    != null -> n()!!
        bool() != null -> bool().toString()
        nul() == true  -> "null"
        ss().isNotEmpty() -> "[${ss().joinToString { "\"$it\"" }}]"
        ns().isNotEmpty() -> "[${ns().joinToString()}]"
        l().isNotEmpty()  -> "[\n    ${l().joinToString(",\n    ") { it.toJsonValue() }}\n  ]"
        m().isNotEmpty()  -> "{\n    ${m().entries.joinToString(",\n    ") { (mk, mv) -> "\"$mk\": ${mv.toJsonValue()}" }}\n  }"
        b()   != null -> "<Binary>"
        else -> "null"
    }

    // ── History tab ───────────────────────────────────────────────────────────
    private fun populateHistory() {
        historyPanel.removeAll()
        val item    = currentItem
        val entries = if (item != null) getHistory(item) else emptyList()

        if (entries.isEmpty()) {
            historyPanel.add(JLabel("No history for this session").apply {
                foreground   = DColors.fg3
                font         = font.deriveFont(12f)
                border       = BorderFactory.createEmptyBorder(16, 12, 0, 12)
                alignmentX   = LEFT_ALIGNMENT
            })
        } else {
            entries.reversed().forEach { entry ->
                val actionColor = when (entry.action) {
                    "Created" -> DColors.good
                    "Deleted" -> DColors.bad
                    else      -> DColors.accent   // "Edited"
                }
                val timeStr = relativeTimestamp(entry.timestamp)

                // ── Entry card ─────────────────────────────────────────────
                val card = JPanel().apply {
                    layout     = BoxLayout(this, BoxLayout.Y_AXIS)
                    background = DColors.bg1
                    border = BorderFactory.createCompoundBorder(
                        MatteBorder(0, 0, 1, 0, DColors.line),
                        BorderFactory.createEmptyBorder(8, 12, 8, 12))
                    maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
                    alignmentX  = LEFT_ALIGNMENT
                }

                // Action row: colored action label + timestamp on the right
                val actionRow = JPanel(BorderLayout()).apply {
                    isOpaque = false
                    maximumSize = Dimension(Int.MAX_VALUE, 20)
                    alignmentX  = LEFT_ALIGNMENT
                }
                actionRow.add(JLabel(entry.action).apply {
                    foreground = actionColor
                    font       = font.deriveFont(Font.BOLD, 11f)
                }, BorderLayout.WEST)
                actionRow.add(JLabel(timeStr).apply {
                    foreground = DColors.fg3
                    font       = font.deriveFont(10f)
                }, BorderLayout.EAST)
                card.add(actionRow)

                // Change detail rows (for Edited entries)
                entry.changes.forEach { change ->
                    card.add(Box.createVerticalStrut(3))
                    card.add(JLabel(change).apply {
                        foreground  = DColors.fg2
                        font        = Font(Font.MONOSPACED, Font.PLAIN, 10)
                        alignmentX  = LEFT_ALIGNMENT
                        maximumSize = Dimension(Int.MAX_VALUE, 16)
                    })
                }

                historyPanel.add(card)
            }
        }

        historyPanel.add(Box.createVerticalGlue())
        historyPanel.revalidate()
        historyPanel.repaint()
    }

    /** Returns a short human-readable relative time string for a Unix ms timestamp. */
    private fun relativeTimestamp(ms: Long): String {
        val diff = System.currentTimeMillis() - ms
        return when {
            diff < 60_000L         -> "just now"
            diff < 3_600_000L      -> "${diff / 60_000}m ago"
            diff < 86_400_000L     -> "${diff / 3_600_000}h ago"
            diff < 7 * 86_400_000L -> "${diff / 86_400_000}d ago"
            else                   -> "${diff / (7 * 86_400_000)}w ago"
        }
    }

    // ── Empty state ───────────────────────────────────────────────────────────
    private fun showEmpty() {
        currentItem = null
        removeAll()
        add(emptyLabel, BorderLayout.CENTER)
        revalidate(); repaint()
    }

    // ── Copy as JSON ──────────────────────────────────────────────────────────
    private fun copyAsJson() {
        val text = jsonArea.document.getText(0, jsonArea.document.length).takeIf { it.isNotBlank() } ?: return
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun boolPill(v: Boolean): JComponent {
        val pillBg = if (v) DColors.goodBg else DColors.badBg
        val pillFg = if (v) DColors.good   else DColors.bad
        val dotClr = if (v) DColors.good   else DColors.bad
        val label  = if (v) "true" else "false"
        return object : JLabel() {
            private val pf = font.deriveFont(Font.PLAIN, 11f).deriveFont(
                mapOf(java.awt.font.TextAttribute.TRACKING to 0.08f))
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)
                val fm   = g2.getFontMetrics(pf)
                val dotD = 6; val padL = 7; val dotGap = 4; val padR = 8
                val pillH = 18
                val pillW = padL + dotD + dotGap + fm.stringWidth("false") + padR
                val pillX = 0; val pillY = (height - pillH) / 2
                g2.color = pillBg; g2.fillRoundRect(pillX, pillY, pillW, pillH, 16, 16)
                g2.color = dotClr; g2.fillOval(pillX + padL, pillY + (pillH - dotD) / 2, dotD, dotD)
                g2.font = pf; g2.color = pillFg
                g2.drawString(label, pillX + padL + dotD + dotGap,
                    pillY + (pillH + fm.ascent - fm.descent) / 2)
            }
            override fun getPreferredSize(): Dimension {
                val fm = getFontMetrics(pf)
                val dotD = 6; val padL = 7; val dotGap = 4; val padR = 8
                return Dimension(padL + dotD + dotGap + fm.stringWidth("false") + padR, 24)
            }
        }.apply { isOpaque = false; border = BorderFactory.createEmptyBorder() }
    }

    // Regular type badge (S, N, BOOL …) — darker bg3 background
    private fun typePill(text: String, fg: Color = DColors.fg3) = object : JLabel(text) {
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = DColors.bg3; g2.fillRoundRect(0, 0, width, height, 4, 4)
            g2.color = DColors.lineStrong; g2.drawRoundRect(0, 0, width - 1, height - 1, 4, 4)
            super.paintComponent(g)
        }
    }.apply {
        this.foreground = fg; font = font.deriveFont(9.5f)
        border = BorderFactory.createEmptyBorder(1, 4, 1, 4)
        isOpaque = false
    }

    // PK / SK badge — solid accent/warn background
    private fun keyPill(text: String, bg: Color, fg: Color) = object : JLabel(text) {
        override fun paintComponent(g: Graphics) {
            val g2 = g as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = bg; g2.fillRoundRect(0, 0, width, height, 4, 4)
            super.paintComponent(g)
        }
    }.apply {
        this.foreground = fg; font = font.deriveFont(Font.BOLD, 9f)
        border = BorderFactory.createEmptyBorder(1, 4, 1, 4)
        isOpaque = false
    }

    /** Button with a fully hand-drawn icon — consistent weight/color across all header buttons. */
    private fun drawnBtn(
        tip: String,
        itemAction: ((Map<String, AttributeValue>) -> Unit)?,
        paint: (Graphics2D, Int, Int) -> Unit,
    ) = object : JButton() {
        init {
            toolTipText = tip
            isBorderPainted = false; isContentAreaFilled = false; isFocusPainted = false
            preferredSize = Dimension(22, 22); minimumSize = Dimension(22, 22); maximumSize = Dimension(22, 22)
            setIcon(object : Icon {
                override fun getIconWidth()  = 16
                override fun getIconHeight() = 16
                override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
                    val g2 = g.create() as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color  = DColors.fg2
                    g2.stroke = BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    paint(g2, x, y)
                    g2.dispose()
                }
            })
            addActionListener { itemAction?.invoke(currentItem ?: return@addActionListener) }
        }
    }

    /** Same rounded-rect group container as the results toolbar btnGroup. */
    private fun headerBtnGroup(vararg btns: JButton): JComponent {
        return object : JPanel() {
            override fun paintComponent(g: Graphics) {
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = DColors.bg3
                g2.fillRoundRect(0, 0, width, height, 6, 6)
            }
        }.apply {
            layout   = BoxLayout(this, BoxLayout.LINE_AXIS)
            isOpaque = false
            border   = BorderFactory.createEmptyBorder(0, 2, 0, 2)
            alignmentY = Component.CENTER_ALIGNMENT
            btns.forEach { btn ->
                btn.isBorderPainted     = false
                btn.isContentAreaFilled = false
                btn.alignmentY          = Component.CENTER_ALIGNMENT
                add(btn)
            }
        }
    }

    private fun miniBtn(icon: Icon, tip: String, action: () -> Unit) = JButton(icon).apply {
        toolTipText         = tip
        isBorderPainted     = false
        isContentAreaFilled = false
        isFocusPainted      = false
        preferredSize       = Dimension(22, 22)
        minimumSize         = Dimension(22, 22)
        maximumSize         = Dimension(22, 22)
        addActionListener { action() }
    }

    private fun inferType(av: AttributeValue) = when {
        av.s() != null -> "S"
        av.n() != null -> "N"
        av.bool() != null -> "BOOL"
        av.l().isNotEmpty() -> "L"
        av.m().isNotEmpty() -> "M"
        av.ss().isNotEmpty() -> "SS"
        av.ns().isNotEmpty() -> "NS"
        av.b() != null -> "B"
        else -> "?"
    }

    private fun relativeTime(iso: String): String = runCatching {
        val t = java.time.Instant.parse(iso).toEpochMilli()
        val d = (System.currentTimeMillis() - t) / 86_400_000
        when {
            d < 1  -> "today"
            d < 7  -> "${d}d ago"
            d < 30 -> "${d/7}w ago"
            else   -> "${d/30}mo ago"
        }
    }.getOrElse { "" }

    private fun AttributeValue.displayValue(): String = when {
        s()    != null -> s()!!
        n()    != null -> n()!!
        bool() != null -> bool().toString()
        ss().isNotEmpty() -> ss().joinToString(", ", "[", "]")
        ns().isNotEmpty() -> ns().joinToString(", ", "[", "]")
        l().isNotEmpty()  -> "[List:${l().size}]"
        m().isNotEmpty()  -> "{Map:${m().size}}"
        nul() == true     -> "null"
        b()   != null     -> "<Binary>"
        else -> "?"
    }
}
