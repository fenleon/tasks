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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
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
 * The lists panel: a selection screen (feedback 7) — tapping a row picks that
 * view as the main context and returns. Rows are Inbox, All, then stored
 * lists alphabetically; the built-in Planned and Completed views are the
 * bottom bar's icons either side of ADD NEW (feedback 2026-10-30: the alarm
 * clock opens Planned, the tick box opens Completed — no text rows, no back
 * arrow, no selection underline). Each row shows its **open** task count
 * (feedback 2026-08-26: only what's not done) near the scrollbar — zero
 * counts render as nothing (feedback 2026-09-02: a "0" reads as noise).
 * EDIT (top right, DONE to leave — same size as the bottom-bar buttons,
 * feedback 2026-08-26) switches to delete mode: only stored lists show, each
 * with an X at the far left (no counts), and the bottom bar disappears (no
 * ADD NEW, feedback 2026-08-26); tapping a row opens the rename editor
 * (feedback 2026-10-31). The X deletes the list directly when it has
 * no open tasks (feedback 2026-08-26); otherwise the no-top-bar confirmation
 * ("Delete list [name]?", CANCEL · DELETE) appears. ADD NEW opens the
 * new-list editor; blank is refused. Rename lives behind the main panel's
 * top-bar name.
 */
class CategoriesScreen(
    sealedActivity: SealedLightActivity,
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
                Box {
                    LightTopBar(
                        center = LightTopBarCenter.Text(text = "Lists"),
                    )
                    TopBarTextButton(
                        text = if (editMode) "DONE" else "EDIT",
                        onClick = { editMode = !editMode },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 1f.gridUnitsAsDp()),
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    LightScrollView {
                        if (editMode) {
                            // Delete mode: stored categories only, X far left,
                            // no counts (feedback 2026-08-26); tapping a row
                            // renames it (feedback 2026-10-31).
                            categories.sortedBy { it.name.lowercase() }.forEach { category ->
                                CategoryRow(
                                    context = TaskContext.of(category),
                                    count = 0,
                                    deleteMode = true,
                                    onDelete = { confirmDelete(category) },
                                    onClick = { renameCategory(category) },
                                )
                            }
                        } else {
                            TaskContext.all(categories).forEach { context ->
                                CategoryRow(
                                    context = context,
                                    count = openCountFor(context.key, tasks),
                                    deleteMode = false,
                                    onDelete = {},
                                    onClick = { goBack(context) },
                                )
                            }
                        }
                    }
                }
                // No ADD NEW while editing (feedback 2026-08-26). The built-in
                // Planned and Completed views live here as icons either side
                // of ADD NEW (feedback 2026-10-30): tick box left = Completed,
                // alarm clock right = Planned.
                if (!editMode) {
                    LightBottomBar(
                        modifier = Modifier.navigationBarsPadding(),
                        items = listOf(
                            LightBarButton.Icon(
                                painter = completedIconPainter(),
                                onClick = { goBack(TaskContext.COMPLETED) },
                                contentDescription = "Completed",
                            ),
                            LightBarButton.Text(
                                text = "ADD NEW",
                                onClick = { openAddCategory() },
                            ),
                            LightBarButton.LightIcon(
                                icon = LightIcons.ALARM,
                                onClick = { goBack(TaskContext.PLANNED) },
                                contentDescription = "Planned",
                            ),
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

    /** Tapping a list in EDIT mode opens the rename editor (feedback
     *  2026-10-31) — the same editor the main panel's top-bar name uses;
     *  blank keeps the old name. */
    private fun renameCategory(category: Category) {
        navigateTo(screenFactory = {
            TitleEditorScreen(it, title = "Edit List", initial = category.name)
        }) { name ->
            if (name.isNotBlank()) {
                TaskRepository.renameCategory(category.id, name)
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

    /** The count shown next to a view's row — what its default view shows:
     *  the open (not-done) tasks for every view, but Completed counts its done
     *  tasks (feedback 2026-09-02), since that view shows nothing else. */
    private fun openCountFor(key: String, tasks: List<Task>): Int =
        when (key) {
            TaskRepository.KEY_ALL -> tasks.count { !it.done }
            TaskRepository.KEY_INBOX -> tasks.count { it.categoryId == null && !it.done }
            TaskRepository.KEY_PLANNED -> tasks.count { it.dueAt != null && !it.done }
            TaskRepository.KEY_COMPLETED -> tasks.count { it.done }
            else -> tasks.count { it.categoryId == key && !it.done }
        }
}

/** One list row: the name left, the open task count near the scrollbar
 *  (feedback 2026-08-26), omitted entirely when it is zero (feedback
 *  2026-09-02); in delete mode a stored category shows an X at the
 *  far left instead of the count (feedback 2026-08-26). The row keeps its
 *  padding in both modes, so the spacing never changes (feedback
 *  2026-08-26). No selection underline (feedback 2026-10-30). All text full
 *  content color. */
@Composable
private fun CategoryRow(
    context: TaskContext,
    count: Int,
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
            LightText(
                text = context.name,
                variant = LightTextVariant.Heading,
                modifier = Modifier.weight(1f),
            )
            // No count when there is nothing to count (feedback 2026-09-02).
            if (count > 0) {
                LightText(
                    text = count.toString(),
                    variant = LightTextVariant.Copy,
                )
            }
        }
    }
}

/** The bottom bar's Completed icon — the drawn tick box (no SDK drawable
 *  matches; feedback 2026-10-30 asked for "the tick box"). Open square with a
 *  check inside, strokes slightly thicker than the task-row box so the 2-unit
 *  bar icon reads at a glance (feedback 2026-10-30). */
@Composable
private fun completedIconPainter(): Painter {
    val colors = LightThemeTokens.colors
    return remember(colors) {
        object : Painter() {
            override val intrinsicSize = Size.Unspecified
            override fun DrawScope.onDraw() {
                val stroke = 3.dp.toPx()
                drawRect(
                    color = colors.content,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke),
                )
                val cx = size.width
                val cy = size.height
                drawLine(
                    color = colors.content,
                    start = Offset(cx * 0.22f, cy * 0.52f),
                    end = Offset(cx * 0.44f, cy * 0.74f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = colors.content,
                    start = Offset(cx * 0.44f, cy * 0.74f),
                    end = Offset(cx * 0.80f, cy * 0.28f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
