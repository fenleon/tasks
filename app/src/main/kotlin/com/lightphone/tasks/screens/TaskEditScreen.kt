package com.lightphone.tasks.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lightphone.tasks.server.TaskFormat
import com.lightphone.tasks.server.TaskRepository
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
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
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow

/** A date + optional time as chosen on the due screen. `date == null` means the
 *  due was cleared. Sent as a non-null result so the edit screen can tell
 *  "cleared" apart from "cancelled" (the SDK only delivers non-null results). */
data class DueValue(val date: LocalDate?, val time: LocalTime?)

/** The picked category — `id == null` means Inbox (also a non-null wrapper;
 *  a plain null result would read as "cancelled"). */
data class CategorySelection(val id: String?)

/** The picked start time — `time == null` means removed (a non-null wrapper;
 *  a plain null result would read as "cancelled"). */
data class TimePick(val time: LocalTime?)

/**
 * Add / Edit task — one screen, two modes (taskId null = add, optionally
 * prefilled with a title + category from the quick-add's DETAILS, feedback
 * 24). Top bar: just the centered "Edit Task" / "New Task" title — no back
 * navigation (the bottom-bar X dismisses). Rows (feedback 2026-08-26): the
 * name with no label, just the value + input underline; Category; Due Date
 * and Start Time on one line (always both); Notes reading "Add Notes" /
 * "Edit Notes". Bottom bar: Delete (edit only) · X · SAVE.
 */
class TaskEditViewModel(
    private val taskId: String?,
    private val initialTitle: String?,
    private val initialCategoryId: String?,
) : LightViewModel<Unit>() {

    val title = MutableStateFlow(initialTitle ?: "")
    val categoryId = MutableStateFlow(initialCategoryId)

    /** The picked date, or null when no due is set. */
    val dueDate = MutableStateFlow<LocalDate?>(null)

    /** The picked time-of-day, or null for a date-only due. */
    val dueTime = MutableStateFlow<LocalTime?>(null)

    val notes = MutableStateFlow("")

    val isEdit: Boolean get() = taskId != null

    init {
        taskId?.let { id ->
            TaskRepository.getTask(id)?.let { task ->
                title.value = task.title
                categoryId.value = task.categoryId
                dueDate.value = task.dueAt?.let { TaskFormat.dateOf(it) }
                dueTime.value = task.dueAt?.let { TaskFormat.timeOfDay(it) }
                notes.value = task.notes
            }
        }
    }

    fun categoryName(): String? = categoryId.value?.let { TaskRepository.getCategory(it)?.name }

    fun save(screen: SimpleLightScreen<Unit>) {
        val trimmed = title.value.trim()
        if (trimmed.isEmpty()) return // blank tasks are refused
        val dueAt = dueDate.value?.let { TaskFormat.combine(it, dueTime.value) }
        if (taskId == null) {
            TaskRepository.addTask(trimmed, categoryId.value, dueAt, notes.value)
        } else {
            TaskRepository.updateTask(taskId, trimmed, categoryId.value, dueAt, notes.value)
        }
        screen.goBack()
    }

    /** Deletes via the shared ConfirmDelete screen; on confirm the task is
     *  removed and the edit screen pops back to the details screen, which
     *  sees the task is gone and pops on to Home. */
    fun delete(screen: SimpleLightScreen<Unit>) {
        screen.navigateTo(screenFactory = {
            ConfirmDeleteScreen(
                it,
                topBarTitle = title.value,
                mainText = "Are you sure you'd like to delete this task?",
                cancelText = null,
                confirmText = "CONFIRM",
                confirmCentered = true,
            )
        }) { deleted ->
            if (deleted == true) {
                taskId?.let { id -> TaskRepository.deleteTask(id) }
                screen.goBack()
            }
        }
    }
}

class TaskEditScreen(
    sealedActivity: SealedLightActivity,
    private val taskId: String?,
    private val initialTitle: String? = null,
    private val initialCategoryId: String? = null,
) : LightScreen<Unit, TaskEditViewModel>(sealedActivity) {

    override val viewModelClass: Class<TaskEditViewModel>
        get() = TaskEditViewModel::class.java

    override fun createViewModel(): TaskEditViewModel =
        TaskEditViewModel(taskId, initialTitle, initialCategoryId)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val title by viewModel.title.collectAsState()
        val categoryId by viewModel.categoryId.collectAsState()
        val dueDate by viewModel.dueDate.collectAsState()
        val dueTime by viewModel.dueTime.collectAsState()
        val notes by viewModel.notes.collectAsState()
        val selectedDate = dueDate
        val selectedTime = dueTime

        val dueDisplay = if (selectedDate == null) "None" else {
            TaskFormat.formatDue(TaskFormat.combine(selectedDate, selectedTime))
        }
        val timeDisplay = selectedTime?.let { TaskFormat.formatTime(it) }.orEmpty()
        val categoryName = categoryId?.let { TaskRepository.getCategory(it)?.name } ?: "None"

        LightTheme(colors = themeColors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                // No top-bar navigation (feedback 2026-08-26): just the
                // centered title — the bottom-bar X dismisses.
                LightTopBar(
                    center = LightTopBarCenter.Text(
                        text = if (viewModel.isEdit) "Edit Task" else "New Task",
                    ),
                )
                Box(modifier = Modifier.weight(1f)) {
                    LightScrollView {
                        // Name — no label, just the name with the input
                        // underline (feedback 2026-08-26).
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .lightClickable(onClick = { editTitle() })
                                .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
                        ) {
                            LightText(
                                text = title.ifEmpty { "Add Title" },
                                variant = LightTextVariant.Heading,
                            )
                            Spacer(Modifier.height(0.25f.gridUnitsAsDp()))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(LightThemeTokens.colors.content),
                            )
                        }
                        EditFieldRow(
                            label = "Category",
                            value = categoryName,
                            placeholder = "None",
                            onClick = { pickCategory() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
                        )
                        // Due Date + Start Time on one line, always both
                        // (feedback 2026-08-26).
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
                        ) {
                            EditFieldRow(
                                label = "Due Date",
                                value = dueDisplay,
                                placeholder = "None",
                                onClick = { pickDue() },
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(1f.gridUnitsAsDp()))
                            EditFieldRow(
                                label = "Start Time",
                                value = timeDisplay,
                                placeholder = "None",
                                onClick = { pickTime() },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Notes — the row reads "Add Notes" / "Edit Notes",
                        // opening the composer (feedback 2026-08-26).
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .lightClickable(onClick = { editNotes() })
                                .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
                        ) {
                            LightText(
                                text = if (notes.isBlank()) "Add Notes" else "Edit Notes",
                                variant = LightTextVariant.Copy,
                            )
                            Spacer(Modifier.height(0.25f.gridUnitsAsDp()))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(LightThemeTokens.colors.content),
                            )
                        }
                    }
                }
                LightBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    items = listOf(
                        if (viewModel.isEdit) {
                            LightBarButton.LightIcon(
                                icon = LightIcons.TRASH,
                                onClick = { viewModel.delete(this@TaskEditScreen) },
                                contentDescription = "Delete task",
                            )
                        } else {
                            null
                        },
                        LightBarButton.LightIcon(
                            icon = LightIcons.CLOSE,
                            onClick = { goBack() },
                            contentDescription = "Discard changes",
                        ),
                        LightBarButton.Text(
                            text = "SAVE",
                            onClick = { viewModel.save(this@TaskEditScreen) },
                        ),
                    ),
                )
            }
        }
    }

    private fun editTitle() {
        navigateTo(screenFactory = {
            TitleEditorScreen(
                it,
                title = if (viewModel.isEdit) "Task" else "New Task",
                initial = viewModel.title.value,
                singleLine = true,
            )
        }) { value ->
            value.takeIf { it.isNotBlank() }?.let {
                viewModel.title.value = it
            }
        }
    }

    private fun pickCategory() {
        navigateTo(screenFactory = {
            CategoryPickerScreen(it, initial = viewModel.categoryId.value)
        }) { selection ->
            viewModel.categoryId.value = selection.id
        }
    }

    private fun pickDue() {
        navigateTo(screenFactory = {
            DueScreen(
                it,
                initialDate = viewModel.dueDate.value,
                initialTime = viewModel.dueTime.value,
            )
        }) { value ->
            viewModel.dueDate.value = value.date
            viewModel.dueTime.value = value.time
        }
    }

    private fun pickTime() {
        navigateTo(screenFactory = {
            TimePickerScreen(it, initial = viewModel.dueTime.value)
        }) { pick ->
            viewModel.dueTime.value = pick.time
        }
    }

    private fun editNotes() {
        navigateTo(screenFactory = {
            TitleEditorScreen(
                it,
                title = "Notes",
                initial = viewModel.notes.value,
                singleLine = false,
                displayReturn = true,
                clearBar = true,
            )
        }) { value ->
            viewModel.notes.value = value
        }
    }
}

/** A label + current value (or placeholder) row with the input underline — the
 *  edit-form idiom (passes EditFieldRow). Tap opens the editor/picker. */
@Composable
internal fun EditFieldRow(
    label: String,
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.lightClickable(onClick = onClick),
    ) {
        LightText(label, variant = LightTextVariant.Superfine)
        LightText(
            text = value.ifEmpty { placeholder },
            variant = LightTextVariant.Copy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.5f.gridUnitsAsDp()),
        )
        Spacer(Modifier.height(0.25f.gridUnitsAsDp()))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(LightThemeTokens.colors.content),
        )
    }
}
