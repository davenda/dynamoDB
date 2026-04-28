package com.yourplugin.dynamodb.ui

import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Design tokens from the prototype. Dark values are used in the IDE's dark LaF,
 * light values in IntelliJ Light / macOS Light.
 */
object DColors {
    // ── Backgrounds ───────────────────────────────────────────────────────────
    val bg0  = JBColor(Color(0xFBFBFA), Color(0x1F2126))
    val bg1  = JBColor(Color(0xFFFFFF), Color(0x25272D))
    val bg2  = JBColor(Color(0xF5F5F3), Color(0x2B2D34))
    val bg3  = JBColor(Color(0xECECEA), Color(0x34363E))

    // ── Borders ───────────────────────────────────────────────────────────────
    val line      = JBColor(Color(0xE7E7E3), Color(0x34363E))
    val lineStrong= JBColor(Color(0xD6D6D1), Color(0x3E4049))

    // ── Text ──────────────────────────────────────────────────────────────────
    val fg0 = JBColor(Color(0x1D1F24), Color(0xE6E6EA))
    val fg1 = JBColor(Color(0x3A3D44), Color(0xC4C5CC))
    val fg2 = JBColor(Color(0x6F7280), Color(0x8E9099))
    val fg3 = JBColor(Color(0x9A9CA4), Color(0x62646C))

    // ── Semantic ──────────────────────────────────────────────────────────────
    val accent     = JBColor(Color(0x4F6DF0), Color(0x6E8EFF))
    val accentSoft = JBColor(Color(79, 109, 240, 28), Color(110, 142, 255, 40))
    val accentRow  = JBColor(Color(79, 109, 240, 65), Color(110, 142, 255, 85))
    val good       = JBColor(Color(77, 219, 87), Color(77, 219, 87))
    val goodBg     = JBColor(Color(77, 219, 87, 30), Color(77, 219, 87, 40))
    val warn       = JBColor(Color(0xA86B00), Color(0xD8A657))
    val bad        = JBColor(Color(0xB53A3A), Color(0xE26D6D))
    val badBg      = JBColor(Color(0xFCE8E8), Color(0x3D1F1F))
    val info       = JBColor(Color(0x266EA1), Color(0x6EA7D8))
    val infoSoft   = JBColor(Color(38, 110, 161, 25), Color(110, 167, 216, 35))

    // ── Syntax ────────────────────────────────────────────────────────────────
    val synKw  = JBColor(Color(0x7D2DB8), Color(0xCF8EFF))
    val synNum = JBColor(Color(0x8A5A16), Color(0xD6A772))
    val synStr = JBColor(Color(0x2C6E2C), Color(0xA3C780))
    val synDim = JBColor(Color(0x9A9CA4), Color(0x62646C))
}
