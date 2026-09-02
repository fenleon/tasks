package com.lightphone.tasks.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.lightphone.tasks.server.TaskRepository
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/**
 * The category picker — the edit screen's Category row destination (feedback
 * 23): mirrors the categories panel minus the virtual views (ignoring All,
 * Planned, Completed) — Inbox (None) plus each stored category, all
 * left-aligned, the selected one underlined. Tap picks and returns; back
 * keeps the previous selection. Result: [CategorySelection] (`id == null` =
 * Inbox).
 */
class CategoryPickerScreen(
    sealedActivity: SealedLightActivity,
    private val initial: String?,
) : SimpleLightScreen<CategorySelection>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val categories by TaskRepository.categories.collectAsState()
        val sorted = categories.sortedBy { it.name.lowercase() }

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { goBack() },
                        contentDescription = "Back",
                    ),
                    center = LightTopBarCenter.Text(text = "Category"),
                )
                Box(modifier = Modifier.weight(1f)) {
                    LightScrollView {
                        PickerRow(
                            text = "Inbox",
                            selected = initial == null,
                            onClick = { goBack(CategorySelection(null)) },
                        )
                        sorted.forEach { category ->
                            PickerRow(
                                text = category.name,
                                selected = initial == category.id,
                                onClick = { goBack(CategorySelection(category.id)) },
                            )
                        }
                    }
                }
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        null,
                        LightBarButton.Text(
                            text = "CANCEL",
                            onClick = { goBack() },
                        ),
                        null,
                    ),
                )
            }
        }
    }
}

/** A choice row: Heading-size label, left-aligned (feedback 23), underlined
 *  when selected — the selection rule (underline, never color, DESIGN.md §7).
 *  The underline is the app's thin bar (feedback 2026-08-26: the SDK's
 *  selection underline reads too thick). */
@Composable
private fun PickerRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
    ) {
        LightText(
            text = text,
            variant = LightTextVariant.Heading,
            modifier = if (selected) Modifier.thinUnderline() else Modifier,
        )
    }
}