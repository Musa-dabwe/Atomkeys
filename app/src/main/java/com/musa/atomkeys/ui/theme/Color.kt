package com.musa.atomkeys.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Colours for the **settings app chrome only**.
 *
 * The keyboard itself uses none of these, nor Material3, nor dynamic colour — it drives its own
 * 14-token system from `:design` (`AtomTheme`). This file exists so the settings screens have
 * somewhere warm to sit next to the keyboard preview they host (Phase 5.2).
 *
 * The scaffold's template Purple/Pink values are gone deliberately: per the roadmap these files get
 * replaced, not extended. The values below come from the same warm-sand family as the design doc's
 * settings surface and its Claude Light token set.
 */

/** Warm sand — the settings surface the design doc renders its six sections on. */
val AtomSand = Color(0xFFF5F2EA)

/** The row/card fill that sits on [AtomSand]. */
val AtomPaper = Color(0xFFFAF9F5)

/** Deep ink for primary text. */
val AtomInk = Color(0xFF2A2723)

/** Muted ink for secondary text and captions — the doc's own caption colour. */
val AtomInkMuted = Color(0xFF6B6559)

/** Hairline dividers between the 64dp settings rows. */
val AtomHairline = Color(0xFFE0DBCD)

/** Accent for active toggles and segmented selections. Claude coral, per the doc. */
val AtomCoral = Color(0xFFD97757)

// --- Dark counterparts, from the Claude Dark token set ------------------------------------------

val AtomSandDark = Color(0xFF1F1E1D)
val AtomPaperDark = Color(0xFF30302E)
val AtomInkDark = Color(0xFFF5F4EF)
val AtomInkMutedDark = Color(0xFFC9C6BC)
val AtomHairlineDark = Color(0xFF3E3E3A)
