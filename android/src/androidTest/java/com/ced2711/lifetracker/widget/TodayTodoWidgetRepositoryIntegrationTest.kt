package com.ced2711.lifetracker.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ced2711.lifetracker.data.local.SubtaskEntity
import com.ced2711.lifetracker.data.local.TaskLedgerDatabase
import com.ced2711.lifetracker.data.local.TodoEntity
import com.ced2711.lifetracker.data.repository.TaskLedgerRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TodayTodoWidgetRepositoryIntegrationTest {
    private lateinit var database: TaskLedgerDatabase
    private lateinit var repository: TaskLedgerRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TaskLedgerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TaskLedgerRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun queryShowsOnlyTodayActiveDeadlinesAndParentCompletionKeepsSubtasks() = runBlocking {
        val dao = database.dao()
        val today = LocalDate.now().toEpochDay()
        val allDayId = dao.insertTodo(
            TodoEntity(
                title = "All-day today",
                description = "All-day today",
                deadlineEpochDay = today,
            ),
        )
        val timedId = dao.insertTodo(
            TodoEntity(
                title = "Timed today",
                description = "Timed today",
                deadlineEpochDay = today,
                deadlineMinute = 600,
            ),
        )
        dao.insertTodo(TodoEntity(title = "No deadline", description = "No deadline"))
        dao.insertTodo(
            TodoEntity(
                title = "Tomorrow",
                description = "Tomorrow",
                deadlineEpochDay = today + 1,
            ),
        )
        dao.insertTodo(
            TodoEntity(
                title = "Completed today",
                description = "Completed today",
                deadlineEpochDay = today,
                completedAt = 1,
            ),
        )
        dao.insertTodo(
            TodoEntity(
                title = "Deleted today",
                description = "Deleted today",
                deadlineEpochDay = today,
                deletedAt = 1,
            ),
        )
        dao.insertSubtasks(
            listOf(SubtaskEntity(todoId = allDayId, description = "Keep this subtask")),
        )

        assertEquals(
            listOf(allDayId, timedId),
            repository.activeTodosForDeadlineDay(today).first().map(TodoEntity::id),
        )

        completeTodoParentOnly(repository, allDayId)

        assertNotNull(repository.getTodo(allDayId)?.completedAt)
        assertFalse(repository.getSubtasks(allDayId).single().isCompleted)
        assertEquals(
            listOf(timedId),
            repository.activeTodosForDeadlineDay(today).first().map(TodoEntity::id),
        )
    }
}
