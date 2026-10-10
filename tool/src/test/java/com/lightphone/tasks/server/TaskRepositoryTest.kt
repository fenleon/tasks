package com.lightphone.tasks.server

import com.thelightphone.sdk.shared.lightJson
import java.io.File
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Round-trip test for the tasks.json store: categories + tasks (incl. notes),
 *  done flag, category delete → Inbox move for open tasks / soft delete for
 *  done ones, soft delete, the view queries
 *  (All/Inbox/Planned/Completed/custom), display order + the kept-visible
 *  just-completed rows (only the Completed view lists done tasks otherwise,
 *  or the SHOW DONE includeDone flag, feedback 2026-09-02), and a second
 *  load from the same dir seeing everything the first instance wrote. */
class TaskRepositoryTest {

    private val dir = File(System.getProperty("java.io.tmpdir"), "tasks-test-${UUID.randomUUID()}")

    @Test
    fun `round trip categories tasks ordering soft delete completed views`() {
        val now = System.currentTimeMillis()

        // Fresh dir starts empty; Inbox is implicit.
        TaskRepository.init(dir)
        assertTrue(TaskRepository.listCategories().isEmpty())
        assertEquals(emptyList(), TaskRepository.tasksForContext(TaskRepository.KEY_INBOX))

        // Categories: creation order, duplicates allowed, blank refused.
        val work = assertNotNull(TaskRepository.addCategory("Work"))
        val errands = assertNotNull(TaskRepository.addCategory("Errands"))
        assertNull(TaskRepository.addCategory("   "))
        assertNotNull(TaskRepository.addCategory("Work")) // duplicates allowed
        assertEquals(listOf("Work", "Errands", "Work"), TaskRepository.listCategories().map { it.name })

        // Tasks: one in each category + one in the Inbox (categoryId = null).
        val inboxEarly = TaskRepository.addTask("inbox early", null, dueAt = now - 1000)
        val workLate = TaskRepository.addTask("work late", work.id, dueAt = now + 5000)
        val workNoDue = TaskRepository.addTask("work no due", work.id, dueAt = null)
        val errandOne = TaskRepository.addTask("errand one", errands.id, dueAt = null, notes = "note")

        // View queries.
        assertEquals(4, TaskRepository.tasksForContext(TaskRepository.KEY_ALL).size)
        assertEquals(listOf("inbox early"), TaskRepository.tasksForContext(TaskRepository.KEY_INBOX).map { it.title })
        assertEquals(listOf("inbox early", "work late"), TaskRepository.tasksForContext(TaskRepository.KEY_PLANNED).map { it.title })
        assertTrue(TaskRepository.tasksForContext(TaskRepository.KEY_COMPLETED).isEmpty())
        assertEquals(
            listOf("work late", "work no due"),
            TaskRepository.tasksForContext(work.id).map { it.title },
        )
        assertEquals("note", TaskRepository.getTask(errandOne.id)?.notes)

        // Display order within a view: due asc (nulls last), then title alpha.
        assertEquals(
            listOf("inbox early", "work late", "errand one", "work no due"),
            TaskRepository.tasksForContext(TaskRepository.KEY_ALL).map { it.title },
        )

        // Check off an active task -> it vanishes from All and every list
        // (feedback 2026-09-02: no list but Completed shows done tasks) and
        // shows up in the Completed view.
        TaskRepository.setDone(workLate.id, true)
        assertEquals(
            listOf("inbox early", "errand one", "work no due"),
            TaskRepository.tasksForContext(TaskRepository.KEY_ALL).map { it.title },
        )
        assertEquals(listOf("work no due"), TaskRepository.tasksForContext(work.id).map { it.title })
        assertEquals(listOf("work late"), TaskRepository.tasksForContext(TaskRepository.KEY_COMPLETED).map { it.title })

        // A just-completed task stays visible, marked off, while it is kept
        // (feedback 2026-08-26 + 2026-09-02: a crossed-out row lingers a few
        // minutes before the view hides it) — and keeps its open-task sort
        // position (due before no-due), not sinking to the end.
        assertEquals(
            listOf("inbox early", "work late", "errand one", "work no due"),
            TaskRepository.tasksForContext(TaskRepository.KEY_ALL, setOf(workLate.id)).map { it.title },
        )
        assertEquals(
            listOf("work late", "work no due"),
            TaskRepository.tasksForContext(work.id, setOf(workLate.id)).map { it.title },
        )

        // The Inbox / stored-list SHOW DONE toggle (feedback 2026-09-02):
        // done rows mix back into a list only when includeDone is set, sorted
        // below the open ones.
        val inboxDone = TaskRepository.addTask("inbox done", null, dueAt = null)
        TaskRepository.setDone(inboxDone.id, true)
        assertEquals(listOf("work no due"), TaskRepository.tasksForContext(work.id).map { it.title })
        assertEquals(
            listOf("work no due", "work late"),
            TaskRepository.tasksForContext(work.id, includeDone = true).map { it.title },
        )
        assertEquals(
            listOf("inbox early"),
            TaskRepository.tasksForContext(TaskRepository.KEY_INBOX).map { it.title },
        )
        assertEquals(
            listOf("inbox early", "inbox done"),
            TaskRepository.tasksForContext(TaskRepository.KEY_INBOX, includeDone = true).map { it.title },
        )

        // Deleting a category moves its open tasks to the Inbox and
        // soft-deletes its completed ones (feedback 2026-08-26).
        val errandDone = TaskRepository.addTask("errand done", errands.id, dueAt = null)
        TaskRepository.setDone(errandDone.id, true)
        TaskRepository.deleteCategory(errands.id)
        assertNull(TaskRepository.getCategory(errands.id))
        assertNull(TaskRepository.getTask(errandOne.id)?.categoryId) // open task survives in Inbox
        assertNull(TaskRepository.getTask(errandDone.id)) // done task is gone from queries

        // Soft delete: gone from queries, still on disk with deletedAt set.
        TaskRepository.deleteTask(inboxEarly.id)
        assertNull(TaskRepository.getTask(inboxEarly.id))
        assertTrue(TaskRepository.tasksForContext(TaskRepository.KEY_ALL).none { it.id == inboxEarly.id })

        // A second load from the same dir sees exactly what the first wrote.
        TaskRepository.reload()
        assertEquals(listOf("Work", "Work"), TaskRepository.listCategories().map { it.name })
        assertEquals(true, TaskRepository.getTask(workLate.id)?.done) // done survives
        assertEquals(false, TaskRepository.getTask(errandOne.id)?.done) // never completed
        assertNull(TaskRepository.getTask(errandOne.id)?.categoryId) // Inbox move survives
        assertNull(TaskRepository.getTask(errandDone.id)) // done-task soft delete survives
        assertNull(TaskRepository.getTask(inboxEarly.id)) // soft delete survives

        // And the file itself has the expected shape.
        val file = File(dir, "tasks.json")
        assertTrue(file.isFile)
        val onDisk = lightJson.decodeFromString(TasksFile.serializer(), file.readText())
        assertEquals(1, onDisk.schemaVersion)
        assertEquals(2, onDisk.categories.size)
        assertEquals(6, onDisk.tasks.size) // all six still stored (soft delete keeps the row)
        assertNotNull(onDisk.tasks.first { it.id == inboxEarly.id }.deletedAt)
        assertNotNull(onDisk.tasks.first { it.id == errandDone.id }.deletedAt)
    }

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }
}
