package com.lightphone.tasks.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.thelightphone.lp3Keyboard.ui.KeyboardOptions
import com.thelightphone.lp3Keyboard.ui.viewmodel.defaultEmojis
import com.lightphone.tasks.server.TaskRepository
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.scaledForScreenHeight
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The quick-add screen (feedback 2/24): the LP3 keyboard is up immediately
 * and the first letter typed enters the task title (lowercase start — no
 * caps). SAVE creates the task (into the Inbox by default, or the current
 * stored list) and returns to the main panel. The second bottom-bar action,
 * MORE (feedback 2026-09-02), carries the typed title into the full edit
 * panel (list, due, start time, notes). Back discards.
 */
class QuickAddScreen(
    sealedActivity: SealedLightActivity,
    private val defaultCategoryId: String?,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val keyboardOptionsFlow = remember {
            MutableStateFlow(
                KeyboardOptions(
                    emojis = emptyList(),
                    displayReturn = false,
                    displayVoice = false,
                    enableKeyAnimation = true,
                    swipeEnabled = false,
                ),
            )
        }
        val inputStyle = LightThemeTokens.typography.heading
            .copy(color = LightThemeTokens.colors.content)
            .scaledForScreenHeight()
        val textState = rememberTextFieldState("")

        LightTheme(colors = themeColors) {
            LightTextInputEditor(
                title = "New Task",
                state = textState,
                keyboardOptionsFlow = keyboardOptionsFlow,
                onSubmit = { save(it.toString().trim()) },
                onBack = { goBack() },
                modifier = Modifier.background(LightThemeTokens.colors.background),
                submitLabel = "SAVE",
                bottomAligned = false,
                centered = true,
                singleLine = true,
                initialCaps = false,
                inputTextStyle = inputStyle,
                bottomBarLeadingButton = LightBarButton.Text(
                    text = "MORE",
                    onClick = { openDetails(textState.text.toString().trim()) },
                ),
            )
        }
    }

    private fun save(title: String) {
        if (title.isEmpty()) return
        TaskRepository.addTask(title, defaultCategoryId, dueAt = null)
        goBack()
    }

    /** MORE — hand the typed title into the full edit panel; the fresh task
     *  keeps its list, everything else comes from the edit screen.
     *  The quick-add pops itself first (feedback 2026-08-26: back/SAVE from
     *  the editor must land on the main panel, not on an emptied quick-add),
     *  so the stack becomes Home → Edit. */
    private fun openDetails(title: String) {
        goBack()
        navigateTo(screenFactory = {
            TaskEditScreen(
                it,
                taskId = null,
                initialTitle = title,
                initialCategoryId = defaultCategoryId,
            )
        })
    }
}