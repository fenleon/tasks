package com.lightphone.tasks.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp

/**
 * The confirm-action screen (a dedicated pushed screen, never a modal — the
 * audiobooks RemoveBook-style house pattern). Only the **list delete** layout
 * remains (feedback 2026-09-02: task deletion no longer confirms): no top
 * bar; the main text "Delete list [name]?" with a smaller white detail line;
 * bottom bar CANCEL left · DELETE right.
 *
 * All text is the full content color (white). Result: `true` on confirm —
 * callers act on `confirmed == true` only; back / X cancels.
 */
class ConfirmDeleteScreen(
    sealedActivity: SealedLightActivity,
    private val mainText: String,
    private val detail: String? = null,
) : SimpleLightScreen<Boolean>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4f.gridUnitsAsDp(), start = 2f.gridUnitsAsDp(), end = 2f.gridUnitsAsDp()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        LightText(
                            text = mainText,
                            variant = LightTextVariant.Heading,
                            align = TextAlign.Center,
                        )
                        detail?.let {
                            LightText(
                                text = it,
                                variant = LightTextVariant.Copy,
                                modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
                            )
                        }
                    }
                }
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        LightBarButton.Text(text = "CANCEL", onClick = { goBack() }),
                        null,
                        LightBarButton.Text(text = "DELETE", onClick = { goBack(true) }),
                    ),
                )
            }
        }
    }
}
