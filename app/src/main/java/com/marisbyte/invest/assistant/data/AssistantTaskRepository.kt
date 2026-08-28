package com.marisbyte.invest.assistant.data

import com.marisbyte.invest.assistant.model.AssistantTask
import com.marisbyte.invest.assistant.task.TaskMatcher
import com.marisbyte.invest.data.local.AssistantTaskDao
import com.marisbyte.invest.data.local.AssistantTaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Alfreds Aufgabenliste. Liegt in derselben Datenbank wie das Depot. */
class AssistantTaskRepository(private val dao: AssistantTaskDao) {

    fun observeTasks(): Flow<List<AssistantTask>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun openTasks(): List<AssistantTask> = dao.open().map { it.toModel() }

    /** Aufgaben mit Termin in der Zukunft - Grundlage fuers Neuplanen nach dem Neustart. */
    suspend fun upcomingTasks(): List<AssistantTask> =
        dao.upcoming(System.currentTimeMillis()).map { it.toModel() }

    suspend fun add(text: String, dueAt: Long?): AssistantTask {
        val entity = AssistantTaskEntity(
            text = text.trim(),
            dueAt = dueAt,
            done = false,
            createdAt = System.currentTimeMillis()
        )
        val id = dao.insert(entity)
        return entity.copy(id = id).toModel()
    }

    /**
     * Hakt die Aufgabe ab, die am besten zum Stichwort passt.
     * @return die abgehakte Aufgabe oder null, wenn nichts sicher genug passte.
     */
    suspend fun complete(query: String): AssistantTask? {
        val match = TaskMatcher.findBest(openTasks(), query) ?: return null
        dao.setDone(match.id, true)
        return match.copy(done = true)
    }

    suspend fun setDone(id: Long, done: Boolean) = dao.setDone(id, done)

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun deleteCompleted() = dao.deleteCompleted()

    suspend fun getById(id: Long): AssistantTask? = dao.getById(id)?.toModel()

    private fun AssistantTaskEntity.toModel() = AssistantTask(
        id = id,
        text = text,
        dueAt = dueAt,
        done = done,
        createdAt = createdAt
    )
}
