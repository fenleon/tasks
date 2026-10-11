package com.lightphone.tasks.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/** A top-bar text action in the bottom-bar **Button** variant — the
 *  letter-spaced look Light's own apps use for text actions (measured on the
 *  LP3, feedback 2026-08-26). Upstream `LightTopBar` renders its text slots in
 *  Fine and its types are sealed, so this overlays the bar's right slot with
 *  the same geometry the SDK's own text-button rendering uses (3gu bar height,
 *  centered single line). Place it over a `LightTopBar(rightButton = null)`
 *  with `Modifier.align(Alignment.CenterEnd).padding(end = 1f.gridUnitsAsDp())`
 *  — the bar's own side padding.
 *
 *  ponytail: mirrors LightTopBar's private geometry (3gu height, 1gu padding).
 *  If upstream grows a `textVariant` param on LightTopBar, delete this and
 *  pass the variant instead. */
@Composable
fun TopBarTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(3f.gridUnitsAsDp())
            .widthIn(min = 3.5f.gridUnitsAsDp())
            .lightClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = text,
            variant = LightTextVariant.Button,
            maxLines = 1,
        )
    }
}
