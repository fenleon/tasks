package com.lightphone.tasks.server

import kotlinx.serialization.Serializable

/** A task category. The Inbox is implicit — a task with `categoryId == null`
 *  is in the Inbox — and is NOT stored here: it has no `Category` row, is
 *  always listed first, and cannot be renamed or deleted (SPEC §3). */
@Serializable
data class Category(
    val id: String, // stable UUID
    val name: String,
    val createdAt: Long,
)

/** A task. Optional fields carry defaults so old or partial files decode
 *  cleanly (the store writes with `explicitNulls = false`, so absent nulls
 *  are simply skipped; SPEC §3). */
@Serializable
data class Task(
    val id: String, // stable UUID
    val title: String,
    val categoryId: String? = null, // null = Inbox
    val dueAt: Long? = null, // epoch ms; optional; date-only or date+time
    val notes: String = "",
    val done: Boolean = false,
    val deletedAt: Long? = null, // soft delete — kept for future CalDAV sync; queries filter it out
    val createdAt: Long,
)

/** The on-disk shape: one `tasks.json` with the schema version at the top
 *  (SPEC §3). Internal — only the repository and its round-trip test read it;
 *  the server can decode the same JSON via the public `Category`/`Task`. */
@Serializable
internal data class TasksFile(
    val schemaVersion: Int = SCHEMA_VERSION,
    val categories: List<Category> = emptyList(),
    val tasks: List<Task> = emptyList(),
    // Per-view "show completed" flag (the category edit menu's SHOW COMPLETED),
    // keyed by view key: TaskRepository.KEY_INBOX / KEY_PLANNED or a category
    // id. Defaults false; "All" always shows everything and "Completed" is
    // completed-only, so neither consults the map.
    val showCompleted: Map<String, Boolean> = emptyMap(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}