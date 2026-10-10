package com.lightphone.tasks.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lightphone.tasks.server.TaskFormat
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
import com.thelightphone.sdk.ui.verticalGridUnitsAsDp
import java.time.LocalDate
import java.time.LocalTime

/**
 * Pick a task's due date — a minimal month-grid calendar (feedback 22): the
 * top bar holds `<` previous / `>` next month around the month title, and
 * tapping a day selects it (the underline) — you have to press SAVE to commit
 * (feedback 2026-08-26). CLEAR removes the due entirely, X dismisses without
 * changing. Result: [DueValue] (`date == null` = cleared).
 */
class DueScreen(
    sealedActivity: SealedLightActivity,
    private val initialDate: LocalDate?,
    private val initialTime: LocalTime?,
) : SimpleLightScreen<DueValue>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val startDate = initialDate ?: LocalDate.now()
        var month by remember { mutableStateOf(startDate.withDayOfMonth(1)) }
        var selected by remember { mutableStateOf(initialDate) }

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                // Month navigation only — `<` previous, centered month title,
                // `>` next (feedback 22).
                LightTopBar(
                    leftButton = LightBarButton.LightIcon(
                        icon = LightIcons.BACK,
                        onClick = { month = month.minusMonths(1) },
                        contentDescription = "Previous month",
                    ),
                    center = LightTopBarCenter.Text(text = TaskFormat.formatMonthTitle(month)),
                    rightButton = LightBarButton.LightIcon(
                        icon = LightIcons.ARROW_RIGHT,
                        onClick = { month = month.plusMonths(1) },
                        contentDescription = "Next month",
                    ),
                )

                Box(modifier = Modifier.weight(1f)) {
                    LightScrollView {
                        MonthGrid(
                            month = month,
                            selected = selected,
                            onDaySelected = { day -> selected = day },
                        )
                    }
                }

                // CLEAR · X · SAVE — a picked day only applies on SAVE
                // (feedback 2026-08-26); an existing time carries over.
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        LightBarButton.Text(
                            text = "CLEAR",
                            onClick = { goBack(DueValue(null, null)) },
                        ),
                        LightBarButton.LightIcon(
                            icon = LightIcons.CLOSE,
                            onClick = { goBack() },
                            contentDescription = "Close without changing",
                        ),
                        LightBarButton.Text(
                            text = "SAVE",
                            onClick = { goBack(DueValue(selected, initialTime)) },
                        ),
                    ),
                )
            }
        }
    }
}

/** The month grid: weekday letters + a 7-day × 6-week layout, Sunday-first.
 *  Tapping a day selects it (thin underline); today carries a round dot drawn
 *  under the number without shifting it (feedback 2026-08-26: the dot used to
 *  push the number up), hidden while today is selected. */
@Composable
private fun MonthGrid(
    month: LocalDate,
    selected: LocalDate?,
    onDaySelected: (LocalDate) -> Unit,
) {
    val today = LocalDate.now()
    Column(modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp())) {
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_LETTERS.forEach { letter ->
                LightText(
                    text = letter.toString(),
                    variant = LightTextVariant.Fine,
                    align = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 0.5f.gridUnitsAsDp()),
                )
            }
        }
        val offset = month.dayOfWeek.value % 7
        val daysInMonth = month.lengthOfMonth()
        repeat(6) { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3f.verticalGridUnitsAsDp()),
            ) {
                for (col in 0 until 7) {
                    val dayNumber = week * 7 + col - offset + 1
                    val day = if (dayNumber in 1..daysInMonth) {
                        month.withDayOfMonth(dayNumber)
                    } else {
                        null
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .lightClickable(enabled = day != null) { day?.let(onDaySelected) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day != null) {
                            LightText(
                                text = dayNumber.toString(),
                                variant = LightTextVariant.Copy,
                                modifier = if (day == selected) {
                                    Modifier.thinUnderline()
                                } else {
                                    Modifier
                                },
                            )
                            // Today's dot sits at the cell's bottom edge as an
                            // overlay — the number stays centered above it.
                            if (day == today && day != selected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 0.35f.gridUnitsAsDp())
                                        .size(0.25f.gridUnitsAsDp())
                                        .background(
                                            LightThemeTokens.colors.content,
                                            CircleShape,
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val WEEKDAY_LETTERS = listOf('S', 'M', 'T', 'W', 'T', 'F', 'S')

/** The pickers' selection underline: a ~2dp bar at the text's bottom edge —
 *  the text-field underline thickness, thinner than LightText's 4dp selection
 *  bar (the passes picker idiom). Shared by the date, time and category
 *  pickers. */
@Composable
internal fun Modifier.thinUnderline(): Modifier {
    val density = LocalDensity.current
    val content = LightThemeTokens.colors.content
    val thicknessPx = with(density) { 2.dp.toPx() }
    return drawBehind {
        drawRect(
            color = content,
            topLeft = Offset(0f, size.height - thicknessPx),
            size = Size(size.width, thicknessPx),
        )
    }
}
