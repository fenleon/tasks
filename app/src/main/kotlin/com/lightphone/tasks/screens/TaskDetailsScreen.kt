package com.lightphone.tasks.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.lightphone.tasks.server.TaskFormat
import com.lightphone.tasks.server.TaskRepository
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.gridUnitsAsDp

/**
 * The read-only task details screen — tapping a task on Home opens this
 * (feedback 2026-08-26): a top bar of just back + EDIT (no title), then the
 * task name big, the date line under it ("Monday, August 24, 2026, 14:30"),
 * then the notes when there are any. EDIT opens the full edit screen; the
 * data is collected live, so a save in the editor is reflected here on
 * return. A task deleted via the editor's DELETE pops this screen back to
 * Home (the task no longer exists).
 */
class TaskDetailsScreen(
    sealedActivity: SealedLightActivity,
    private val taskId: String,
) : SimpleLightScreen<Unit>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val allTasks by TaskRepository.tasks.collectAsState()
        val task = allTasks.firstOrNull { it.id == taskId && it.deletedAt == null }

        // Deleted while editing: the details screen has nothing left to show —
        // pop back to the main panel.
        LaunchedEffect(task) {
            if (task == null) goBack()
        }

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
                    // No center title — the task name is the content heading
                    // (feedback 2026-08-26), so the bar is just back + EDIT.
                    center = null,
                    textVariant = LightTextVariant.Button,
                    rightButton = LightBarButton.Text(
                        text = "EDIT",
                        onClick = { openEdit() },
                    ),
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (task != null) {
                        LightScrollView {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 3f.gridUnitsAsDp()),
                            ) {
                                LightText(
                                    text = task.title,
                                    variant = LightTextVariant.Heading,
                                    modifier = Modifier.padding(vertical = 1.5f.gridUnitsAsDp()),
                                )
                                task.dueAt?.let {
                                    LightText(
                                        text = TaskFormat.formatDetails(it),
                                        variant = LightTextVariant.Copy,
                                    )
                                }
                                if (task.notes.isNotBlank()) {
                                    LightText(
                                        text = task.notes,
                                        variant = LightTextVariant.Paragraph,
                                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun openEdit() {
        navigateTo(screenFactory = { TaskEditScreen(it, taskId = taskId) })
    }
}
