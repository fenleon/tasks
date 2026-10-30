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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
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
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
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
     *  due today show; the bottom-bar button reads VIEW ALL (VIEW TODAY when
     *  unset). [select] sets it for Planned, clears it elsewhere (feedback
     *  2026-10-30: Planned opens filtered). */
    val dueTodayOnly = MutableStateFlow(false)

    /** The Inbox / stored-list SHOW DONE toggle (feedback 2026-09-02): when
     *  set, the view mixes its done tasks back in (struck, below the open
     *  ones); the bottom-bar button reads HIDE DONE. Only these views get the
     *  button — All and Planned stay completed-free, Completed shows nothing
     *  else. Reset by [select], like [dueTodayOnly]. */
    val showDone = MutableStateFlow(false)

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
            showDone,
        ) { _, cats, key, keep, done ->
            categories.value = cats
            context.value = contextFor(key, cats)
            tasks.value = TaskRepository.tasksForContext(key, keep, done)
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
        // Planned opens on the DUE TODAY filter (feedback 2026-10-30).
        dueTodayOnly.value = key == TaskRepository.KEY_PLANNED
        showDone.value = false
    }

    fun toggleShowDone() {
        showDone.value = !showDone.value
    }

    fun setDone(task: Task, done: Boolean) {
        TaskRepository.setDone(task.id, done)
        if (done) {
            keepDoneVisible(task.id)
        } else {
            recentlyDone.value = recentlyDone.value - task.id
            doneHideTimers.remove(task.id)?.cancel()
        }
    }

    /** Keep a just-done task visible (struck) for [doneHideDelayMs] — fed by
     *  the row check on Home and by the details screen's MARK DONE (feedback
     *  2026-10-30, which returns here so the struck task shows on return). */
    fun keepDoneVisible(taskId: String) {
        recentlyDone.value = recentlyDone.value + taskId
        doneHideTimers.remove(taskId)?.cancel()
        doneHideTimers[taskId] = viewModelScope.launch {
            delay(doneHideDelayMs)
            recentlyDone.value = recentlyDone.value - taskId
            doneHideTimers.remove(taskId)
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
        val showDone by viewModel.showDone.collectAsState()
        val allTasks by TaskRepository.tasks.collectAsState()

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
                            // Planned reads "no tasks due" (its emptiness is
                            // about dues, not the list being empty — feedback
                            // 2026-10-31); other views keep "no tasks yet".
                            text = when {
                                dueTodayOnly -> "there are no tasks due today"
                                context == TaskContext.PLANNED -> "there are no tasks due"
                                else -> "no tasks yet"
                            },
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
                                    // "{category} · {date}" line — the group
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
                                    text = if (dueTodayOnly) "VIEW ALL" else "VIEW TODAY",
                                    onClick = { viewModel.toggleDueToday() },
                                ),
                            )
                        } else if (
                            context.key == TaskRepository.KEY_INBOX ||
                            !context.isBuiltIn
                        ) {
                            // Inbox and stored lists get the SHOW DONE toggle
                            // (feedback 2026-09-02) — mix the view's completed
                            // tasks back in / hide them again. No completed
                            // tasks in the view → no button (2026-10-31).
                            val hasDone = allTasks.any {
                                it.deletedAt == null && it.done && when (context.key) {
                                    TaskRepository.KEY_INBOX -> it.categoryId == null
                                    else -> it.categoryId == context.key
                                }
                            }
                            if (hasDone) {
                                add(
                                    LightBarButton.Text(
                                        text = if (showDone) "HIDE DONE" else "SHOW DONE",
                                        onClick = { viewModel.toggleShowDone() },
                                    ),
                                )
                            } else {
                                add(null)
                            }
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
        navigateTo(screenFactory = { TaskDetailsScreen(it, taskId = task.id) }) { markedDoneId ->
            // MARK DONE on the details screen: keep the task visible (struck)
            // on return, like a row check (feedback 2026-10-30).
            markedDoneId?.let { viewModel.keepDoneVisible(it) }
        }
    }

    private fun openCategories() {
        navigateTo(screenFactory = { CategoriesScreen(it) }) { selected ->
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

/** The row's "{category} · {date}" subtitle (feedback 2026-09-02: the dot,
 *  not a comma — the category and due read as separate items, like the
 *  bottom-bar separators) — shown on the All, Planned and Completed panels,
 *  where the rows carry their list: "Inbox", "Errands", or "Errands · Sep 3,
 *  14:30" when the task has a due. (Inbox and single-list views skip it — the
 *  list is already the context.) */
private fun rowMeta(task: Task, categories: List<Category>): String {
    val name = task.categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name }
        ?: "Inbox"
    val due = task.dueAt?.let { TaskFormat.formatDue(it) }
    return if (due != null) "$name · $due" else name
}

/** The "Planned" view (feedback 2026-08-26): tasks with a due date divided
 *  under the topline headers Overdue / Due Today / Upcoming; each row's
 *  subtitle is "{category} · {date}" (feedback 2026-09-02). When the DUE TODAY
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
 *  passes one ("{category} · {date}", feedback 2026-09-02), else the task's
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
    // Bottom-align the tick box with the first line's text baseline
    // (feedback 2026-10-31) — measured from a real layout of the rendered
    // style (same construction as TaskTitle's strike), so it tracks the
    // actual text instead of a line-height formula.
    val textMeasurer = rememberTextMeasurer()
    val heading = LightThemeTokens.typography.heading.scaledForScreenHeight()
    val titleStyle = heading.copy(lineHeight = heading.fontSize * 1.15f)
    val baseline = remember(task.title, titleStyle) {
        textMeasurer
            .measure(AnnotatedString(task.title), titleStyle, maxLines = 1)
            .getLineBaseline(0)
    }
    val checkTop = with(LocalDensity.current) {
        (baseline.toDp() - 1.25f.gridUnitsAsDp()).coerceAtLeast(0.dp)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
        verticalAlignment = Alignment.Top,
    ) {
        TaskCheckBox(
            done = task.done,
            onClick = onToggle,
            modifier = Modifier.padding(top = checkTop),
        )
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
 *  word ends by about a quarter letter width (feedback 12, 2026-10-31). The
 *  strike is drawn
 *  behind the text on the title's wrapper box, from the same layout the text
 *  renders with, so a wrapped title is crossed line by line. */
/** SDK's `scaledForScreenHeight` is internal upstream — same math via the
 *  public `designVerticalPxToSp` (screen-height-proportional sp, 600 px base).
 *  Unspecified units must pass through untouched: their `.value` is NaN, and a
 *  NaN letterSpacing poisons the measured layout (the drawn strike's
 *  lineRight goes NaN → the strike silently vanishes). */
@Composable
private fun TextStyle.scaledForScreenHeight(): TextStyle = copy(
    fontSize = if (fontSize.isUnspecified) fontSize else fontSize.value.designVerticalPxToSp(),
    lineHeight = if (lineHeight.isUnspecified) lineHeight else lineHeight.value.designVerticalPxToSp(),
    letterSpacing = if (letterSpacing.isUnspecified) letterSpacing else letterSpacing.value.designVerticalPxToSp(),
)

@Composable
private fun TaskTitle(task: Task) {
    val maxTitleLines = 2
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val textMeasurer = rememberTextMeasurer()
        // Tighter than the Heading default (1.35×): a wrapped two-line title
        // reads as one block (feedback 2026-10-30). The strike measurement
        // must use the same lineHeight or its per-line y positions drift.
        val style = LightThemeTokens.typography.heading.scaledForScreenHeight()
        val tightLineHeight = style.fontSize * 1.15f
        val strikeStyle = style.copy(lineHeight = tightLineHeight)
        val maxWidthPx = constraints.maxWidth
        val strikeLayout = remember(task.title, strikeStyle, maxWidthPx, task.done) {
            if (task.done) {
                textMeasurer.measure(
                    text = AnnotatedString(task.title),
                    style = strikeStyle,
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
                    // ~ a quarter letter width past the last letter and
                    // before the first (feedback 12, 2026-10-31).
                    val overshoot = style.fontSize.toPx() * 0.25f
                    repeat(layout.lineCount) { line ->
                        val top = layout.getLineTop(line)
                        val bottom = layout.getLineBottom(line)
                        val y = top + (bottom - top) * 0.62f
                        drawLine(
                            color = content,
                            start = Offset(-overshoot, y),
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
                lineHeight = tightLineHeight,
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
    modifier: Modifier = Modifier,
) {
    val content = LightThemeTokens.colors.content
    val background = LightThemeTokens.colors.background
    val density = LocalDensity.current
    val stroke = with(density) { 2.dp.toPx() }
    // The tick reads thicker than the box (3 dp vs 2 dp, feedback
    // 2026-10-31) — same as the Lists panel's bottom-bar tick box.
    val tickStroke = with(density) { 3.dp.toPx() }
    Box(
        modifier = modifier
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
                    strokeWidth = tickStroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = background,
                    start = Offset(cx * 0.44f, cy * 0.74f),
                    end = Offset(cx * 0.80f, cy * 0.28f),
                    strokeWidth = tickStroke,
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