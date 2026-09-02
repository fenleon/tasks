package com.lightphone.tasks.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
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
import com.thelightphone.sdk.ui.scaledForScreenHeight
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class HomeViewModel : LightViewModel<Unit>() {

    /** How long a just-checked-off task stays visible, crossed out, before the
     *  view hides it (feedback 2026-09-02: "a few minutes"). */
    private val doneHideDelayMs = 3 * 60 * 1000L

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
     *  through) for [doneHideDelayMs] before the row disappears (feedback
     *  2026-08-26: a completed task remained until you left the panel —
     *  2026-09-02: now a few minutes, so the crossed-out state reads). Each
     *  id's hide timer is cancelled if the task is unchecked again; cleared by
     *  [select]. */
    val recentlyDone = MutableStateFlow<Set<String>>(emptySet())

    /** Per-task hide timers for [recentlyDone] rows. */
    private val doneHideTimers = mutableMapOf<String, Job>()

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
            selectedKey,
            recentlyDone,
        ) { _, cats, key, keep ->
            categories.value = cats
            context.value = contextFor(key, cats)
            tasks.value = TaskRepository.tasksForContext(key, keep)
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
        doneHideTimers.values.forEach { it.cancel() }
        doneHideTimers.clear()
        dueTodayOnly.value = false
    }

    fun setDone(task: Task, done: Boolean) {
        TaskRepository.setDone(task.id, done)
        if (done) {
            recentlyDone.value = recentlyDone.value + task.id
            doneHideTimers.remove(task.id)?.cancel()
            doneHideTimers[task.id] = viewModelScope.launch {
                delay(doneHideDelayMs)
                recentlyDone.value = recentlyDone.value - task.id
                doneHideTimers.remove(task.id)
            }
        } else {
            recentlyDone.value = recentlyDone.value - task.id
            doneHideTimers.remove(task.id)?.cancel()
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
                // is gone; delete moved to the lists panel's EDIT). No back
                // arrow (feedback 2026-09-02: the Lists panel is the only way
                // to switch views).
                LightTopBar(
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
                                context == TaskContext.PLANNED -> PlannedSections(
                                    tasks = shown,
                                    categories = categories,
                                    onEdit = { openTask(it) },
                                    onToggleDone = { task, done -> viewModel.setDone(task, done) },
                                )
                                else -> shown.forEach { task ->
                                    // All and Completed (feedback 2026-09-02)
                                    // are flat lists whose rows carry the
                                    // "{category}, {date}" line — the group
                                    // headers are gone with the completed rows.
                                    val meta = if (context == TaskContext.ALL || context == TaskContext.COMPLETED) {
                                        rowMeta(task, categories)
                                    } else {
                                        null
                                    }
                                    TaskRow(
                                        task = task,
                                        meta = meta,
                                        onToggle = { viewModel.setDone(task, !task.done) },
                                        onClick = { openTask(task) },
                                    )
                                }
                            }
                        }
                    }
                }
                // Per-view bottom bar (feedback 2026-08-26): Lists left, the
                // new-task note icon bottom right — except Completed, which is
                // read-only (no create, no middle button). Planned's middle
                // button toggles the DUE TODAY filter.
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = buildList {
                        add(
                            LightBarButton.LightIcon(
                                icon = LightIcons.LIST,
                                onClick = { openCategories() },
                                contentDescription = "Lists",
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

    /** A stored list's title opens the LP3 keyboard to rename it — the whole
     *  old category menu collapsed into a direct rename (feedback 2026-08-26). */
    private fun renameCategory(context: TaskContext) {
        navigateTo(screenFactory = {
            TitleEditorScreen(
                it,
                title = "Edit List",
                initial = TaskRepository.getCategory(context.key)?.name ?: context.name,
            )
        }) { value ->
            value.takeIf { it.isNotBlank() }?.let {
                TaskRepository.renameCategory(context.key, it)
            }
        }
    }
}

/** The row's "{category}, {date}" subtitle (feedback 2026-09-02) — shown on
 *  the All, Planned and Completed panels, where the rows carry their list:
 *  "Inbox", "Errands", or "Errands, Sep 3, 14:30" when the task has a due.
 *  (Inbox and single-list views skip it — the list is already the context.) */
private fun rowMeta(task: Task, categories: List<Category>): String {
    val name = task.categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name }
        ?: "Inbox"
    val due = task.dueAt?.let { TaskFormat.formatDue(it) }
    return if (due != null) "$name, $due" else name
}

/** The "Planned" view (feedback 2026-08-26): tasks with a due date divided
 *  under the topline headers Overdue / Due Today / Upcoming; each row's
 *  subtitle is "{category}, {date}" (feedback 2026-09-02). When the DUE TODAY
 *  filter is on, [tasks] already holds only today's tasks, so just the
 *  "Due Today" section renders. */
@Composable
private fun PlannedSections(
    tasks: List<Task>,
    categories: List<Category>,
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
                        meta = rowMeta(task, categories),
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
 *  Heading title, then a Superfine subtitle line — [meta] when the panel
 *  passes one ("{category}, {date}", feedback 2026-09-02), else the task's
 *  "[Date], [Time]" due (feedback 19). Tap the check toggles done; tap the
 *  body opens the editor. Done rows are struck through; all text is the full
 *  content color (feedback 9). */
@Composable
private fun TaskRow(
    task: Task,
    meta: String? = null,
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
            TaskTitle(task)
            val subtitle = meta ?: task.dueAt?.let { TaskFormat.formatDue(it) }
            subtitle?.let {
                LightText(
                    text = it,
                    variant = LightTextVariant.Superfine,
                )
            }
        }
    }
}

/** Titles wrap at most two lines, then ellipsize (feedback 2026-09-02: they
 *  used to clip mid-word on the first line — "Drop off library books" cut off
 *  at "library"). Done rows are struck through — one 2dp line per rendered
 *  text line, at the 16b position (62% down the line), each overshooting the
 *  last letter by about one letter width (feedback 12). The strike is drawn
 *  behind the text on the title's wrapper box, from the same layout the text
 *  renders with, so a wrapped title is crossed line by line. */
@Composable
private fun TaskTitle(task: Task) {
    val maxTitleLines = 2
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val textMeasurer = rememberTextMeasurer()
        val style = LightThemeTokens.typography.heading.scaledForScreenHeight()
        val maxWidthPx = constraints.maxWidth
        val strikeLayout = remember(task.title, style, maxWidthPx, task.done) {
            if (task.done) {
                textMeasurer.measure(
                    text = AnnotatedString(task.title),
                    style = style,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = maxTitleLines,
                    constraints = Constraints(maxWidth = maxWidthPx),
                )
            } else {
                null
            }
        }
        val content = LightThemeTokens.colors.content
        Box(
            modifier = Modifier.drawBehind {
                strikeLayout?.let { layout ->
                    val stroke = 2.dp.toPx()
                    // ~ one letter width past the last letter (feedback 12).
                    val overshoot = style.fontSize.toPx() * 0.5f
                    repeat(layout.lineCount) { line ->
                        val top = layout.getLineTop(line)
                        val bottom = layout.getLineBottom(line)
                        val y = top + (bottom - top) * 0.62f
                        drawLine(
                            color = content,
                            start = Offset(0f, y),
                            end = Offset(layout.getLineRight(line) + overshoot, y),
                            strokeWidth = stroke,
                        )
                    }
                }
            },
        ) {
            LightText(
                text = task.title,
                variant = LightTextVariant.Heading,
                maxLines = maxTitleLines,
                overflow = TextOverflow.Ellipsis,
            )
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