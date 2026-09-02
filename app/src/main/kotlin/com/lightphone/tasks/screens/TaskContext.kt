package com.lightphone.tasks.screens

import com.lightphone.tasks.server.Category
import com.lightphone.tasks.server.TaskRepository

/**
 * A "view" of tasks: one of the four built-in views or a stored category
 * (feedback 2026-08-26). [key] is the repository's view key
 * (`TaskRepository.KEY_*` for the built-ins, a category id for the rest);
 * [name] is what the top bar and panels display.
 */
data class TaskContext(
    val key: String,
    val name: String,
) {
    companion object {
        val ALL = TaskContext(TaskRepository.KEY_ALL, "All")
        val INBOX = TaskContext(TaskRepository.KEY_INBOX, "Inbox")
        val PLANNED = TaskContext(TaskRepository.KEY_PLANNED, "Planned")
        val COMPLETED = TaskContext(TaskRepository.KEY_COMPLETED, "Completed")

        /** The categories-panel order (feedback 11): built-ins first, then
         *  stored categories alphabetically. */
        fun all(categories: List<Category>): List<TaskContext> =
            listOf(ALL, INBOX, PLANNED, COMPLETED) +
                categories.sortedBy { it.name.lowercase() }.map { of(it) }

        fun of(category: Category) = TaskContext(category.id, category.name)
    }

    /** The default category a new task goes to from this view: only a stored
     *  category places tasks; every built-in view falls back to the Inbox
     *  (feedback 24). */
    fun defaultCategoryId(): String? = if (isBuiltIn) null else key
}

/** Built-in views (All/Inbox/Planned/Completed) are fixed: no rename, no
 *  delete, no category options. */
val TaskContext.isBuiltIn: Boolean
    get() = key == TaskRepository.KEY_ALL ||
        key == TaskRepository.KEY_INBOX ||
        key == TaskRepository.KEY_PLANNED ||
        key == TaskRepository.KEY_COMPLETED