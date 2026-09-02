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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lightphone.tasks.server.Category
import com.lightphone.tasks.server.Task
import com.lightphone.tasks.server.TaskRepository
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
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
 * The lists panel (feedback 2026-09-02: the categories panel was renamed):
 * a selection screen (feedback 7) — tapping a row picks that view as the main
 * context and returns. Rows are the four built-ins (All, Inbox, Planned,
 * Completed) then stored lists alphabetically (feedback 11); the currently
 * selected view is underlined (feedback 10), and each row shows its **open**
 * task count (feedback 2026-08-26: only what's not done) near the scrollbar.
 * EDIT (top right, DONE to leave — same size as the bottom-bar buttons,
 * feedback 2026-08-26) switches to delete mode: only stored lists show, each
 * with an X at the far left (no underline, no counts), and the bottom bar
 * disappears (no ADD NEW, feedback 2026-08-26). The X deletes the list
 * directly when it has no open tasks (feedback 2026-08-26); otherwise the
 * no-top-bar confirmation ("Delete list [name]?", CANCEL · DELETE) appears.
 * ADD NEW opens the new-list editor; blank is refused. Rename lives behind
 * the main panel's top-bar name.
 */
class CategoriesScreen(
    sealedActivity: SealedLightActivity,
    private val selectedKey: String,
) : SimpleLightScreen<TaskContext>(sealedActivity) {

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val allTasks by TaskRepository.tasks.collectAsState()
        val tasks = allTasks.filter { it.deletedAt == null }
        // collectAsState: recomposes on direct deletes (listCategories() is a
        // plain value read, not snapshot-tracked).
        val categories by TaskRepository.categories.collectAsState()
        var editMode by remember { mutableStateOf(false) }

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
                    center = LightTopBarCenter.Text(text = "Lists"),
                    // EDIT/DONE sized like the bottom-bar text buttons
                    // (feedback 2026-08-26).
                    textVariant = LightTextVariant.Button,
                    rightButton = LightBarButton.Text(
                        text = if (editMode) "DONE" else "EDIT",
                        onClick = { editMode = !editMode },
                    ),
                )
                Box(modifier = Modifier.weight(1f)) {
                    LightScrollView {
                        if (editMode) {
                            // Delete mode: stored categories only, X far left,
                            // no underline / counts (feedback 2026-08-26).
                            categories.sortedBy { it.name.lowercase() }.forEach { category ->
                                CategoryRow(
                                    context = TaskContext.of(category),
                                    count = 0,
                                    selected = false,
                                    deleteMode = true,
                                    onDelete = { confirmDelete(category) },
                                    onClick = {},
                                )
                            }
                        } else {
                            TaskContext.all(categories).forEach { context ->
                                CategoryRow(
                                    context = context,
                                    count = openCountFor(context.key, tasks),
                                    selected = context.key == selectedKey,
                                    deleteMode = false,
                                    onDelete = {},
                                    onClick = { goBack(context) },
                                )
                            }
                        }
                    }
                }
                // No ADD NEW while editing (feedback 2026-08-26).
                if (!editMode) {
                    LightBottomBar(
                        modifier = Modifier.navigationBarsPadding(),
                        items = listOf(
                            null,
                            LightBarButton.Text(
                                text = "ADD NEW",
                                onClick = { openAddCategory() },
                            ),
                            null,
                        ),
                    )
                }
            }
        }
    }

    private fun openAddCategory() {
        navigateTo(screenFactory = {
            TitleEditorScreen(it, title = "New List", initial = "")
        }) { name ->
            if (name.isNotBlank()) {
                TaskRepository.addCategory(name)
            }
        }
    }

    /** A list with no open tasks deletes immediately (feedback 2026-08-26);
     *  with open tasks the confirmation explains the move. */
    private fun confirmDelete(category: Category) {
        val openTasks = TaskRepository.tasks.value.count {
            it.deletedAt == null && it.categoryId == category.id && !it.done
        }
        if (openTasks == 0) {
            TaskRepository.deleteCategory(category.id)
            return
        }
        navigateTo(screenFactory = {
            ConfirmDeleteScreen(
                it,
                mainText = "Delete list ${category.name}?",
                detail = "Deleting moves open tasks to Inbox",
            )
        }) { deleted ->
            if (deleted == true) {
                TaskRepository.deleteCategory(category.id)
            }
        }
    }

    /** Open (not-done) task count for a view key — counts never include
     *  completed tasks (feedback 2026-08-26); the Completed view's is always
     *  zero. */
    private fun openCountFor(key: String, tasks: List<Task>): Int =
        when (key) {
            TaskRepository.KEY_ALL -> tasks.count { !it.done }
            TaskRepository.KEY_INBOX -> tasks.count { it.categoryId == null && !it.done }
            TaskRepository.KEY_PLANNED -> tasks.count { it.dueAt != null && !it.done }
            TaskRepository.KEY_COMPLETED -> 0
            else -> tasks.count { it.categoryId == key && !it.done }
        }
}

/** One context row: the name left, the open task count near the scrollbar
 *  (feedback 2026-08-26); in delete mode a stored category shows an X at the
 *  far left instead of the count (feedback 2026-08-26) and the selection
 *  underline is suppressed. The row keeps its padding in both modes, so the
 *  spacing never changes (feedback 2026-08-26). All text full content color. */
@Composable
private fun CategoryRow(
    context: TaskContext,
    count: Int,
    selected: Boolean,
    deleteMode: Boolean,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(start = 2f.gridUnitsAsDp(), end = 1f.gridUnitsAsDp(), top = 0.75f.gridUnitsAsDp(), bottom = 0.75f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (deleteMode) {
            Box(
                modifier = Modifier
                    .lightClickable(onClick = onDelete)
                    .padding(end = 1f.gridUnitsAsDp()),
                contentAlignment = Alignment.Center,
            ) {
                LightIcon(icon = LightIcons.CLOSE, size = 1.5f)
            }
            LightText(
                text = context.name,
                variant = LightTextVariant.Heading,
                modifier = Modifier.weight(1f),
            )
        } else {
            Box(modifier = Modifier.weight(1f)) {
                LightText(
                    text = context.name,
                    variant = LightTextVariant.Heading,
                    modifier = if (selected) Modifier.thinUnderline() else Modifier,
                )
            }
            LightText(
                text = count.toString(),
                variant = LightTextVariant.Copy,
            )
        }
    }
}
