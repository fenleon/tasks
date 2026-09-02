package com.lightphone.tasks.server

import com.thelightphone.sdk.shared.lightJson
import java.io.File
import java.util.UUID
import kotlin.comparisons.nullsLast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The task store: in-process copies with write-through JSON in a file under
 * the tool's sandboxed filesDir (the passes idiom — `PassRepository.kt:53-60`:
 * `@Serializable` models, `lightJson`, atomic tmp+rename persist). The Inbox
 * is implicit (`categoryId == null`), never stored. All mutators are
 * synchronized — the UI may call from any thread — and every mutation
 * persists and updates the state flows, so Compose can collect them.
 */
object TaskRepository {

    private const val STORAGE_FILE = "tasks.json"

    private var storageFile: File? = null

    /** Raw in-memory snapshots (insertion order). Queries apply the sort. */
    private val mutableTasks = MutableStateFlow<List<Task>>(emptyList())
    private val mutableCategories = MutableStateFlow<List<Category>>(emptyList())

    /** Legacy per-view SHOW COMPLETED map (the category edit menu, removed
     *  2026-08-26). No UI sets it any more and queries ignore it (feedback
     *  2026-09-02: only the Completed view shows done tasks) — kept purely so
     *  old files round-trip unchanged. */
    private val mutableShowCompleted = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    val tasks: StateFlow<List<Task>> = mutableTasks.asStateFlow()
    val categories: StateFlow<List<Category>> = mutableCategories.asStateFlow()

    /** Idempotent — call once from the entry screen before first use. A
     *  missing file starts empty; a corrupt one is renamed aside to
     *  `tasks.json.corrupt-<ts>` (preserved for debugging) and we start
     *  empty rather than clobbering it. */
    fun init(filesDir: File) {
        if (storageFile != null) return
        filesDir.mkdirs()
        storageFile = File(filesDir, STORAGE_FILE)
        loadFromDisk()
    }

    /** Re-reads from disk (what a fresh repository instance would see). Used
     *  by the round-trip test to simulate re-init against the same dir. */
    internal fun reload() {
        storageFile?.let { loadFromDisk() }
    }

    // --- Categories -------------------------------------------------------

    /** Stored categories in creation order. Inbox is implicit and rendered
     *  first by the UI — it has no Category row and is not stored. */
    fun listCategories(): List<Category> = mutableCategories.value

    /** Creates a category; the name is trimmed, blank refused (returns null).
     *  Duplicate names are allowed (kept simple — SPEC doesn't forbid them). */
    fun addCategory(name: String): Category? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val category = Category(UUID.randomUUID().toString(), trimmed, System.currentTimeMillis())
        synchronized(this) {
            mutableCategories.value = mutableCategories.value + category
            persist()
        }
        return category
    }

    /** Renames a category in place; a blank name is ignored. */
    fun renameCategory(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        synchronized(this) {
            mutableCategories.value = mutableCategories.value.map {
                if (it.id == id) it.copy(name = trimmed) else it
            }
            persist()
        }
    }

    /** Deletes a category; its open tasks move to the Inbox (`categoryId ->
     *  null`), its done tasks are soft-deleted, and its SHOW COMPLETED flag is
     *  dropped (feedback 2026-08-26: only open tasks survive a category
     *  delete). */
    fun deleteCategory(id: String) {
        synchronized(this) {
            mutableCategories.value = mutableCategories.value.filterNot { it.id == id }
            mutableTasks.value = mutableTasks.value.map {
                if (it.categoryId == id) {
                    if (it.done) {
                        it.copy(categoryId = null, deletedAt = System.currentTimeMillis())
                    } else {
                        it.copy(categoryId = null)
                    }
                } else {
                    it
                }
            }
            persist()
        }
    }

    // --- Tasks ------------------------------------------------------------

    /** Adds a task (title trimmed). Returns the stored task. */
    fun addTask(title: String, categoryId: String?, dueAt: Long?, notes: String = ""): Task {
        val task = Task(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            categoryId = categoryId,
            dueAt = dueAt,
            notes = notes,
            createdAt = System.currentTimeMillis(),
        )
        synchronized(this) {
            mutableTasks.value = mutableTasks.value + task
            persist()
        }
        return task
    }

    /** Full replace of the editable fields (title, category, due, notes);
     *  id/createdAt/done/deletedAt persist as-is. */
    fun updateTask(id: String, title: String, categoryId: String?, dueAt: Long?, notes: String) {
        synchronized(this) {
            mutableTasks.value = mutableTasks.value.map {
                if (it.id == id) it.copy(
                    title = title.trim(),
                    categoryId = categoryId,
                    dueAt = dueAt,
                    notes = notes,
                ) else it
            }
            persist()
        }
    }

    /** Toggles completion without touching anything else. */
    fun setDone(id: String, done: Boolean) {
        synchronized(this) {
            mutableTasks.value = mutableTasks.value.map {
                if (it.id == id) it.copy(done = done) else it
            }
            persist()
        }
    }

    /** Soft delete — the task stays in the file with `deletedAt` set (future
     *  CalDAV sync); queries filter it out. */
    fun deleteTask(id: String) {
        synchronized(this) {
            mutableTasks.value = mutableTasks.value.map {
                if (it.id == id) it.copy(deletedAt = System.currentTimeMillis()) else it
            }
            persist()
        }
    }

    // --- Queries (all filter `deletedAt == null`) --------------------------

    /** View keys for the built-in contexts — "All" (everything), the implicit
     *  Inbox (`categoryId == null`), "Planned" (has a due), "Completed". Stored
     *  categories use their own id as the key. */
    const val KEY_ALL = "all"
    const val KEY_INBOX = "inbox"
    const val KEY_PLANNED = "planned"
    const val KEY_COMPLETED = "completed"

    /** Non-deleted tasks in the given view (SPEC feedback 2026-08-26 +
     *  2026-09-02): key [KEY_ALL] = everything, [KEY_INBOX] the implicit
     *  inbox, [KEY_PLANNED] tasks with a due, [KEY_COMPLETED] done tasks,
     *  anything else = that category's tasks. Completed rows show **only** in
     *  the Completed view (feedback 2026-09-02: All and every list hide done
     *  tasks — the old per-view SHOW COMPLETED flag no longer gates
     *  anything); [keepVisible] carries the task ids just marked done in the
     *  current view, which stay visible, marked off, for a few minutes until a
     *  hide timer clears them (feedback 2026-08-26 + 2026-09-02) — kept rows
     *  also keep their open-task sort position. Display order (feedback): done
     *  last, then due asc (nulls last), then title alphabetical, then
     *  createdAt. */
    fun tasksForContext(key: String, keepVisible: Set<String> = emptySet()): List<Task> {
        val all = mutableTasks.value.filter { it.deletedAt == null }
        val filtered = when (key) {
            KEY_ALL -> all
            KEY_INBOX -> all.filter { it.categoryId == null }
            KEY_PLANNED -> all.filter { it.dueAt != null }
            KEY_COMPLETED -> all.filter { it.done }
            else -> all.filter { it.categoryId == key }
        }
        val visible = if (key == KEY_COMPLETED) {
            filtered
        } else {
            filtered.filter { !it.done || it.id in keepVisible }
        }
        return visible.sortedWith(displayOrder(keepVisible))
    }

    /** Lookups also hide soft-deleted tasks. */
    fun getTask(id: String): Task? =
        mutableTasks.value.firstOrNull { it.id == id && it.deletedAt == null }

    fun getCategory(id: String): Category? =
        mutableCategories.value.firstOrNull { it.id == id }

    // --- Persistence ------------------------------------------------------

    private fun loadFromDisk() {
        val file = storageFile ?: return
        val data = if (file.isFile) {
            val parsed = runCatching {
                lightJson.decodeFromString(TasksFile.serializer(), file.readText())
            }.getOrNull()
            if (parsed == null) {
                // Preserve the corrupt payload before it is ever overwritten.
                file.renameTo(File(file.parentFile, "$STORAGE_FILE.corrupt-${System.currentTimeMillis()}"))
                TasksFile()
            } else {
                parsed
            }
        } else {
            TasksFile()
        }
        mutableCategories.value = data.categories
        mutableTasks.value = data.tasks
        mutableShowCompleted.value = data.showCompleted
    }

    private fun persist() {
        val file = storageFile ?: return
        val json = lightJson.encodeToString(
            TasksFile.serializer(),
            TasksFile(
                categories = mutableCategories.value,
                tasks = mutableTasks.value,
                showCompleted = mutableShowCompleted.value,
            ),
        )
        // Write-then-rename so a crash mid-write can't corrupt the store.
        val tmp = File(file.parentFile, "$STORAGE_FILE.tmp")
        tmp.writeText(json)
        tmp.renameTo(file)
    }

    /** Display order (feedback 2026-08-26): completed tasks last; within a
     *  group, due asc with no-due last, then title alphabetical, then
     *  createdAt as the tiebreak. Just-completed tasks kept visible by
     *  [keepVisible] sort with the open tasks (they stay in place until the
     *  view changes). */
    private fun displayOrder(keepVisible: Set<String>): Comparator<Task> =
        compareBy<Task> { it.done && it.id !in keepVisible }
            .thenBy(nullsLast()) { it.dueAt }
            .thenBy { it.title.lowercase() }
            .thenBy { it.createdAt }
}