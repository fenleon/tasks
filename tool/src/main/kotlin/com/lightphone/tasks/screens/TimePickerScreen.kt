package com.lightphone.tasks.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollBarPosition
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
import com.thelightphone.sdk.ui.scrollBarGutterUnits
import com.thelightphone.sdk.ui.verticalGridUnitsAsDp
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * Pick a task's start time — the edit screen's "Start time" row destination
 * (feedback 15): scrollable HOUR / MIN columns (24-hour, tap to select, the
 * selected value underlined — the passes recipe). No top-bar navigation — just
 * the centered title — and the bottom bar is CLEAR · X · SAVE (feedback
 * 2026-08-26): CLEAR removes the time, X dismisses without changing, SAVE
 * commits the selection. Result: [TimePick] (`time == null` = removed — the
 * non-null wrapper distinguishes it from a dismiss, since the SDK only
 * delivers non-null results).
 */
class TimePickerScreen(
    sealedActivity: SealedLightActivity,
    private val initial: LocalTime?,
) : SimpleLightScreen<TimePick>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        var time by remember { mutableStateOf(initial ?: LocalTime.NOON) }

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(
                    center = LightTopBarCenter.Text(text = "Start time"),
                )
                // HOUR / MIN each fill half the width and the full height
                // between the bars, so the scrollable values run down to the
                // bottom bar (feedback 2026-09-02: the old fixed 4-row window
                // cut the list off after ~3 values and left the screen below
                // empty).
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 2f.gridUnitsAsDp()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimeColumn(
                        label = "HOUR",
                        values = (0..23).toList(),
                        selected = time.hour,
                        onSelect = { hour -> time = time.withHour(hour) },
                        modifier = Modifier.weight(1f),
                    )
                    TimeColumn(
                        label = "MIN",
                        values = (0..59).toList(),
                        selected = time.minute,
                        onSelect = { minute -> time = time.withMinute(minute) },
                        modifier = Modifier.weight(1f),
                    )
                }
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        LightBarButton.Text(
                            text = "CLEAR",
                            onClick = { goBack(TimePick(null)) },
                        ),
                        LightBarButton.LightIcon(
                            icon = LightIcons.CLOSE,
                            onClick = { goBack() },
                            contentDescription = "Close without changing",
                        ),
                        LightBarButton.Text(
                            text = "SAVE",
                            onClick = { goBack(TimePick(time)) },
                        ),
                    ),
                )
            }
        }
    }
}

/** One scrollable value column (HOUR / MIN) with a selection underline — the
 *  passes time-picker idiom. The column fills the height between the bars;
 *  opening scrolls the selected value a couple of rows below the label. */
@Composable
private fun TimeColumn(
    label: String,
    values: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LightText(
            text = label,
            variant = LightTextVariant.Fine,
            align = TextAlign.Center,
            // The scroll viewport below reserves its scrollbar gutter at the
            // right edge, so the values center one grid unit left of the
            // column's center; the label takes the same gutter padding to sit
            // centered over them (feedback 2026-09-02: HOUR/MIN read shifted
            // right of their columns).
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    end = scrollBarGutterUnits(LightScrollBarPosition.Outside).gridUnitsAsDp(),
                )
                .padding(vertical = 0.5f.gridUnitsAsDp()),
        )
        val density = LocalDensity.current
        val rowHeight = TIME_ROW_HEIGHT_UNITS.verticalGridUnitsAsDp()
        val rowHeightPx = with(density) { rowHeight.toPx() }
        val initialIndex = values.indexOf(selected).coerceAtLeast(0)
        val initialScroll = ((initialIndex - 2).coerceAtLeast(0) * rowHeightPx).roundToInt()
        val scrollState = rememberScrollState(initial = initialScroll)
        LightScrollView(
            scrollState = scrollState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            values.forEach { value ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .lightClickable(onClick = { onSelect(value) }),
                    contentAlignment = Alignment.Center,
                ) {
                    LightText(
                        text = value.toString().padStart(2, '0'),
                        variant = LightTextVariant.Copy,
                        modifier = if (value == selected) {
                            Modifier.thinUnderline()
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

// Time-picker geometry (passes recipe): 2.5 vertical-grid-unit rows. The
// columns fill the whole height between the bars; the initial scroll puts the
// selected value a couple of rows below the top (feedback 2026-08-24).
private const val TIME_ROW_HEIGHT_UNITS = 2.5f