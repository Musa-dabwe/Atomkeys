package com.musa.atomkeys.design

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musa.atomkeys.engine.AtomDimens
import com.musa.atomkeys.engine.KeySize

/**
 * Compose-typed views of the sizing scale.
 *
 * The arithmetic itself lives in `:engine` (`KeyMetrics.kt`) so it can be unit-tested on the JVM;
 * this file only puts `Dp` and `TextUnit` on the results. Nothing here should compute — if a new
 * derived value is needed, it is derived in `:engine` and surfaced here.
 *
 * The design doc's CSS pixels map 1:1 to dp for dimensions and sp for text.
 */

val KeySize.height: Dp get() = heightDp.dp
val KeySize.gap: Dp get() = gapDp.dp
val KeySize.cornerRadius: Dp get() = cornerRadiusDp.dp
val KeySize.hintSize: TextUnit get() = hintSizeSp.sp
val KeySize.hintTop: Dp get() = hintTopDp.dp
val KeySize.hintRight: Dp get() = hintRightDp.dp
val KeySize.middleRuleInset: Dp get() = middleRuleInsetDp.dp
val KeySize.pressBubbleOffset: Dp get() = pressBubbleOffsetDp.dp
val KeySize.wrapPaddingTop: Dp get() = wrapPaddingTopDp.dp
val KeySize.wrapPaddingHorizontal: Dp get() = wrapPaddingHorizontalDp.dp
val KeySize.wrapPaddingBottom: Dp get() = wrapPaddingBottomDp.dp

/** A keycap label size at one of the [com.musa.atomkeys.engine.LabelScale] factors. */
fun KeySize.labelSize(scale: Double): TextUnit = labelSizeSp(scale).sp

/** The height-independent dimensions the design doc states outright. */
object AtomSizes {
    val stripHeight = AtomDimens.STRIP_HEIGHT_DP.dp
    val stripButtonSize = AtomDimens.STRIP_BUTTON_SIZE_DP.dp
    val stripButtonRadius = AtomDimens.STRIP_BUTTON_RADIUS_DP.dp
    val stripItemGap = AtomDimens.STRIP_ITEM_GAP_DP.dp
    val stripBottomMargin = AtomDimens.STRIP_BOTTOM_MARGIN_DP.dp

    val chipPaddingHorizontal = AtomDimens.CHIP_PADDING_HORIZONTAL_DP.dp
    val chipPaddingVertical = AtomDimens.CHIP_PADDING_VERTICAL_DP.dp
    val chipRadius = AtomDimens.CHIP_RADIUS_DP.dp
    val chipText = AtomDimens.CHIP_TEXT_SP.sp

    val subDotsText = AtomDimens.SUB_DOTS_TEXT_SP.sp
    val subDotsLetterSpacing = AtomDimens.SUB_DOTS_LETTER_SPACING_SP.sp
    val subDotsOffset = AtomDimens.SUB_DOTS_OFFSET_DP.dp

    val pressBubbleWidth = AtomDimens.PRESS_BUBBLE_WIDTH_DP.dp
    val pressBubbleHeight = AtomDimens.PRESS_BUBBLE_HEIGHT_DP.dp
    val pressBubbleRadius = AtomDimens.PRESS_BUBBLE_RADIUS_DP.dp
    val pressBubbleText = AtomDimens.PRESS_BUBBLE_TEXT_SP.sp

    /** Spike 0.2's reserved band above the grid, where floating elements render. */
    val overlayHeadroom = AtomDimens.OVERLAY_HEADROOM_DP.dp

    val middleRuleHeight = AtomDimens.MIDDLE_RULE_HEIGHT_DP.dp
    val middleRuleRadius = AtomDimens.MIDDLE_RULE_RADIUS_DP.dp
    val middleRuleVerticalMargin = AtomDimens.MIDDLE_RULE_VERTICAL_MARGIN_DP.dp

    val keyBorderWidth = AtomDimens.KEY_BORDER_WIDTH_DP.dp
    val keyShadowHeight = AtomDimens.KEY_SHADOW_HEIGHT_DP.dp
}
