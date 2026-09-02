package com.lightphone.tasks.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.thelightphone.lp3Keyboard.ui.KeyboardOptions
import com.thelightphone.lp3Keyboard.ui.viewmodel.defaultEmojis
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.scaledForScreenHeight
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The LP3 keyboard editor for a text value — the task title (add and edit),
 * the category name (new and rename) and task notes (feedback 25). The top
 * bar title is Title Case (feedback 4/15), the keyboard starts on lowercase
 * letters (feedback 3), and [singleLine] switches between a single-line
 * editor (titles) and a multiline one (notes, with its return key). Result:
 * the trimmed text ("" clears); back keeps the old value.
 *
 * [clearBar] (notes, feedback 2026-08-26) swaps the editor's single SAVE for
 * a CLEAR · X · SAVE bottom bar — no top-bar back, CLEAR wipes the draft,
 * X dismisses without saving.
 */
class TitleEditorScreen(
    sealedActivity: SealedLightActivity,
    private val title: String,
    private val initial: String,
    private val singleLine: Boolean = true,
    private val displayReturn: Boolean = false,
    private val clearBar: Boolean = false,
) : SimpleLightScreen<String>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val keyboardOptionsFlow = remember {
            MutableStateFlow(
                KeyboardOptions(
                    emojis = emptyList(),
                    displayReturn = displayReturn,
                    displayVoice = false,
                    enableKeyAnimation = true,
                    swipeEnabled = false,
                ),
            )
        }
        val inputStyle = LightThemeTokens.typography.heading
            .copy(color = LightThemeTokens.colors.content)
            .scaledForScreenHeight()
        val textState = rememberTextFieldState(initial)

        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = title,
                state = textState,
                keyboardOptionsFlow = keyboardOptionsFlow,
                onSubmit = { result -> goBack(result.toString().trim()) },
                onBack = { goBack() },
                modifier = Modifier.background(LightThemeTokens.colors.background),
                submitLabel = "SAVE",
                bottomAligned = false,
                centered = true,
                singleLine = singleLine,
                initialCaps = false,
                inputTextStyle = inputStyle,
                showBackButton = !clearBar,
                submitBottomRight = clearBar,
                bottomBarLeadingButton = if (clearBar) {
                    LightBarButton.Text(
                        text = "CLEAR",
                        onClick = { goBack("") },
                    )
                } else {
                    null
                },
                bottomBarCenterButton = if (clearBar) {
                    LightBarButton.LightIcon(
                        icon = LightIcons.CLOSE,
                        onClick = { goBack() },
                        contentDescription = "Close without saving",
                    )
                } else {
                    null
                },
            )
        }
    }
}