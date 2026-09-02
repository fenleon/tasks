package com.lightphone.tasks.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.lightphone.tasks.server.Category
import com.lightphone.tasks.server.Task
import com.lightphone.tasks.server.TaskFormat
import com.lightphone.tasks.server.TaskRepository
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
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
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class HomeViewModel : LightViewModel<Unit>() {

    /** The active view's key (feedback 1/7/12): the main panel shows exactly
     *  this view's tasks, its name in the top bar. */
    val selectedKey = MutableStateFlow(TaskRepository.KEY_INBOX)

    /** The live view (name always current — a renamed category shows its new
     *  name in the top bar). */
    val context = MutableStateFlow(TaskContext.INBOX)

    /** The view's tasks in repository display order (due asc, alpha, done last). */
    val tasks = MutableStateFlow<List<Task>>(emptyList())

    val categories = MutableStateFlow<List<Category>>(emptyList())

    /** Tasks marked done in the current view that stay visible (struck
     *  through) until the user leaves the panel — feedback 2026-08-26:
     *  "the completed task should remain and show marked off even if SHOW
     *  COMPLETED is turned off, until you go to another panel". Cleared by
     *  [select]. */
    val recentlyDone = MutableStateFlow<Set<String>>(emptySet())

    /** Planned's DUE TODAY filter (feedback 2026-08-26): when set, only tasks
     *  due today show; the bottom-bar button reads VIEW ALL. Reset by
     *  [select]. */
    val dueTodayOnly = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            combineState()
        }
    }

    private suspend fun combineState() {
        kotlinx.coroutines.flow.combine(
            TaskRepository.tasks,
            TaskRepository.categories,
            TaskRepository.showCompletedFlow,
            selectedKey,
            recentlyDone,
        ) { _, cats, showCompleted, key, keep ->
            categories.value = cats
            context.value = contextFor(key, cats)
            tasks.value = TaskRepository.tasksForContext(key, showCompleted[key] ?: false, keep)
        }.collect { }
    }

    private fun contextFor(key: String, categories: List<Category>): TaskContext =
        when (key) {
            TaskRepository.KEY_ALL -> TaskContext.ALL
            TaskRepository.KEY_PLANNED -> TaskContext.PLANNED
            TaskRepository.KEY_COMPLETED -> TaskContext.COMPLETED
            TaskRepository.KEY_INBOX -> TaskContext.INBOX
            else -> categories.firstOrNull { it.id == key }?.let(TaskContext::of)
                ?: TaskContext.INBOX // deleted category while selected
        }

    fun select(key: String) {
        selectedKey.value = key
        recentlyDone.value = emptySet()
        dueTodayOnly.value = false
    }

    fun setDone(task: Task, done: Boolean) {
        TaskRepository.setDone(task.id, done)
        recentlyDone.value = if (done) {
            recentlyDone.value + task.id
        } else {
            recentlyDone.value - task.id
        }
    }

    fun toggleDueToday() {
        dueTodayOnly.value = !dueTodayOnly.value
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, HomeViewModel>(sealedActivity) {

    init {
        TaskRepository.init(lightContext.filesDir)
    }

    override val viewModelClass: Class<HomeViewModel>
        get() = HomeViewModel::class.java

    override fun createViewModel(): HomeViewModel = HomeViewModel()

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val context by viewModel.context.collectAsState()
        val tasks by viewModel.tasks.collectAsState()
        val categories by viewModel.categories.collectAsState()
        val dueTodayOnly by viewModel.dueTodayOnly.collectAsState()

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                // The current view's name. Built-in views are fixed (feedback
                // 2026-08-26: no title select in All/Inbox/Planned/Completed);
                // a stored category's title opens the rename editor directly
                // (the old category menu — MARK ALL / SHOW COMPLETED / delete —
                // is gone; delete moved to the categories panel's EDIT).
                // Any view but Inbox shows a back arrow that returns to Inbox.
                LightTopBar(
                    leftButton = if (context.key == TaskRepository.KEY_INBOX) {
                        null
                    } else {
                        LightBarButton.LightIcon(
                            icon = LightIcons.BACK,
                            onClick = { viewModel.select(TaskRepository.KEY_INBOX) },
                            contentDescription = "Back to Inbox",
                        )
                    },
                    center = LightTopBarCenter.Text(
                        text = context.name,
                        onClick = if (context.isBuiltIn) null else ({ renameCategory(context) }),
                    ),
                )
                Box(modifier = Modifier.weight(1f)) {
                    // Planned's DUE TODAY filter (feedback 2026-08-26).
                    val shown = if (dueTodayOnly) {
                        tasks.filter { isDueToday(it.dueAt) }
                    } else {
                        tasks
                    }
                    if (shown.isEmpty()) {
                        EmptyState(
                            text = if (dueTodayOnly) "There are no tasks due today." else "No tasks yet",
                        )
                    } else {
                        LightScrollView {
                            when {
                                context == TaskContext.ALL || context == TaskContext.COMPLETED -> CategorySections(
                                    allTasks = shown,
                                    categories = categories,
                                    onEdit = { openTask(it) },
                                    onToggleDone = { task, done -> viewModel.setDone(task, done) },
                                )
                                context == TaskContext.PLANNED -> PlannedSections(
                                    tasks = shown,
                                    onEdit = { openTask(it) },
                                    onToggleDone = { task, done -> viewModel.setDone(task, done) },
                                )
                                else -> shown.forEach { task ->
                                    TaskRow(
                                        task = task,
                                        onToggle = { viewModel.setDone(task, !task.done) },
                                        onClick = { openTask(task) },
                                    )
                                }
                            }
                        }
                    }
                }
                // Per-view bottom bar (feedback 2026-08-26): Categories left,
                // the new-task note icon bottom right — except Completed, which
                // is read-only (no create, no middle button). Planned's middle
                // button toggles the DUE TODAY filter.
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = buildList {
                        add(
                            LightBarButton.LightIcon(
                                icon = LightIcons.LIST,
                                onClick = { openCategories() },
                                contentDescription = "Categories",
                            ),
                        )
                        if (context.key == TaskRepository.KEY_PLANNED) {
                            add(
                                LightBarButton.Text(
                                    text = if (dueTodayOnly) "VIEW ALL" else "DUE TODAY",
                                    onClick = { viewModel.toggleDueToday() },
                                ),
                            )
                        } else {
                            add(null)
                        }
                        if (context.key == TaskRepository.KEY_COMPLETED) {
                            add(null)
                        } else {
                            add(
                                LightBarButton.LightIcon(
                                    icon = LightIcons.COMPOSE_MESSAGE,
                                    onClick = { openAddTask() },
                                    contentDescription = "New task",
                                ),
                            )
                        }
                    },
                )
            }
        }
    }

    private fun openAddTask() {
        navigateTo(screenFactory = {
            QuickAddScreen(it, defaultCategoryId = viewModel.context.value.defaultCategoryId())
        })
    }

    private fun openTask(task: Task) {
        navigateTo(screenFactory = { TaskDetailsScreen(it, taskId = task.id) })
    }

    private fun openCategories() {
        navigateTo(screenFactory = {
            CategoriesScreen(it, selectedKey = viewModel.context.value.key)
        }) { selected ->
            viewModel.select(selected.key)
        }
    }

    /** A stored category's title opens the LP3 keyboard to rename it — the
     *  whole old category menu collapsed into a direct rename (feedback
     *  2026-08-26). */
    private fun renameCategory(context: TaskContext) {
        navigateTo(screenFactory = {
            TitleEditorScreen(
                it,
                title = "Edit Category",
                initial = TaskRepository.getCategory(context.key)?.name ?: context.name,
            )
        }) { value ->
            value.takeIf { it.isNotBlank() }?.let {
                TaskRepository.renameCategory(context.key, it)
            }
        }
    }
}

/** Category-grouped sections — the "All" view (every task) and the
 *  "Completed" view (done tasks only, feedback 2026-08-26: completed rows
 *  separated by category like in All): each category's tasks under a small
 *  top header, categories in panel order (inbox first, then stored categories
 *  alphabetically — feedback 20/11). Within a group the rows keep the global
 *  order (due asc, alpha, done last). */
@Composable
private fun CategorySections(
    allTasks: List<Task>,
    categories: List<Category>,
    onEdit: (Task) -> Unit,
    onToggleDone: (Task, Boolean) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp())) {
        val byCategory = allTasks.groupBy { it.categoryId }
        @Composable
        fun section(header: String, tasks: List<Task>) {
            if (tasks.isNotEmpty()) {
                LightText(
                    text = header,
                    variant = LightTextVariant.Superfine,
                    modifier = Modifier.padding(
                        horizontal = 2f.gridUnitsAsDp(),
                        vertical = 0.75f.gridUnitsAsDp(),
                    ),
                )
                tasks.forEach { task ->
                    TaskRow(
                        task = task,
                        onToggle = { onToggleDone(task, !task.done) },
                        onClick = { onEdit(task) },
                    )
                }
            }
        }
        section("Inbox", byCategory[null].orEmpty())
        categories.sortedBy { it.name.lowercase() }.forEach { category ->
            section(category.name, byCategory[category.id].orEmpty())
        }
    }
}

/** The "Planned" view (feedback 2026-08-26): tasks with a due date divided
 *  under the topline headers Overdue / Due Today / Upcoming, like the All
 *  view's category groups. When the DUE TODAY filter is on, [tasks] already
 *  holds only today's tasks, so just the "Due Today" section renders. */
@Composable
private fun PlannedSections(
    tasks: List<Task>,
    onEdit: (Task) -> Unit,
    onToggleDone: (Task, Boolean) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp())) {
        val startToday = TaskFormat.startOfDay(LocalDate.now())
        val startTomorrow = TaskFormat.startOfDay(LocalDate.now().plusDays(1))
        @Composable
        fun section(header: String, tasks: List<Task>) {
            if (tasks.isNotEmpty()) {
                LightText(
                    text = header,
                    variant = LightTextVariant.Superfine,
                    modifier = Modifier.padding(
                        horizontal = 2f.gridUnitsAsDp(),
                        vertical = 0.75f.gridUnitsAsDp(),
                    ),
                )
                tasks.forEach { task ->
                    TaskRow(
                        task = task,
                        onToggle = { onToggleDone(task, !task.done) },
                        onClick = { onEdit(task) },
                    )
                }
            }
        }
        section("Overdue", tasks.filter { it.dueAt!! < startToday })
        section("Due Today", tasks.filter { it.dueAt!! in startToday until startTomorrow })
        section("Upcoming", tasks.filter { it.dueAt!! >= startTomorrow })
    }
}

/** Whether a due epoch falls on today (Planned's DUE TODAY filter). */
private fun isDueToday(dueAt: Long?): Boolean =
    dueAt != null && TaskFormat.dateOf(dueAt) == LocalDate.now()

/** "No tasks" — one quiet line, full content color (feedback 9). */
@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 1f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = text,
            variant = LightTextVariant.Copy,
            align = TextAlign.Center,
        )
    }
}

/** One task row: leading check affordance (drawn outline square → filled ✓),
 *  Heading title, Superfine due line ("[Date], [Time]" — feedback 19). Tap the
 *  check toggles done; tap the body opens the editor. Done rows are struck
 *  through; all text is the full content color (feedback 9). */
@Composable
private fun TaskRow(
    task: Task,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskCheckBox(done = task.done, onClick = onToggle)
        Spacer(Modifier.width(0.75f.gridUnitsAsDp()))
        Column(
            modifier = Modifier
                .weight(1f)
                .lightClickable(onClick = onClick),
        ) {
            LightText(
                text = task.title,
                variant = LightTextVariant.Heading,
                maxLines = 1,
                modifier = if (task.done) Modifier.strikeThrough() else Modifier,
            )
            task.dueAt?.let {
                LightText(
                    text = TaskFormat.formatDue(it),
                    variant = LightTextVariant.Superfine,
                )
            }
        }
    }
}

/** The leading check affordance: an outline square, filled with a check when
 *  done — drawn monochrome (no SDK checkbox exists). The visual is ~1.25 gu;
 *  the touch target grows to the SDK's minimum 3.5 gu tap width. */
@Composable
private fun TaskCheckBox(
    done: Boolean,
    onClick: () -> Unit,
) {
    val content = LightThemeTokens.colors.content
    val background = LightThemeTokens.colors.background
    val density = LocalDensity.current
    val stroke = with(density) { 2.dp.toPx() }
    Box(
        modifier = Modifier
            .widthIn(min = 3.5f.gridUnitsAsDp())
            .lightClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(1.25f.gridUnitsAsDp())) {
            if (done) {
                drawRect(color = content)
                val cx = size.width
                val cy = size.height
                drawLine(
                    color = background,
                    start = Offset(cx * 0.22f, cy * 0.52f),
                    end = Offset(cx * 0.44f, cy * 0.74f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = background,
                    start = Offset(cx * 0.44f, cy * 0.74f),
                    end = Offset(cx * 0.80f, cy * 0.28f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            } else {
                drawRect(
                    color = content,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke),
                )
            }
        }
    }
}

/** A monochrome strikethrough for done rows — drawn just below the text's
 *  middle at the input-underline thickness (2dp — feedback 2026-08-26). */
@Composable
private fun Modifier.strikeThrough(): Modifier {
    val density = LocalDensity.current
    val stroke = with(density) { 2.dp.toPx() }
    val color = LightThemeTokens.colors.content
    return drawBehind {
        drawLine(
            color = color,
            start = Offset(0f, size.height * 0.62f),
            end = Offset(size.width, size.height * 0.62f),
            strokeWidth = stroke,
        )
    }
}