package com.saiyanhabits.data

import androidx.room.withTransaction
import com.saiyanhabits.domain.PowerSource
import com.saiyanhabits.domain.RoutineRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/** A template with its steps. */
data class RoutineTemplate(val template: RoutineTemplateEntity, val items: List<RoutineTemplateItemEntity>)

/** A day's routine with its items. */
data class DayRoutine(val routine: DayRoutineEntity, val items: List<DayRoutineItemEntity>) {
    val done: Int get() = items.count { it.doneAtMillis != null }
    val finished: Boolean get() = items.isNotEmpty() && done == items.size
}

/**
 * Routines: checklists for a day, planned ahead from saved templates or from
 * scratch. Each item checked off on its day gives power; unchecking takes it
 * back. Templates and day routines are separate copies: editing one never
 * changes the other.
 */
class RoutineRepository(
    private val db: AppDatabase,
    private val today: () -> LocalDate = LocalDate::now,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.routineDao()

    val templates: Flow<List<RoutineTemplate>> = combine(dao.observeTemplates(), dao.observeTemplateItems()) { templates, items ->
        val byTemplate = items.groupBy { it.templateId }
        templates.map { RoutineTemplate(it, byTemplate[it.id].orEmpty()) }
    }

    /** Routines from [from] on, by day. */
    fun routinesFrom(from: LocalDate): Flow<List<DayRoutine>> =
        combine(dao.observeRoutinesFrom(from.toEpochDay()), dao.observeItemsFrom(from.toEpochDay())) { routines, items ->
            val byRoutine = items.groupBy { it.routineId }
            routines.map { DayRoutine(it, byRoutine[it.id].orEmpty()) }
        }

    suspend fun template(id: Long): RoutineTemplate? =
        dao.template(id)?.let { RoutineTemplate(it, dao.templateItems(id)) }

    suspend fun dayRoutine(id: Long): DayRoutine? =
        dao.routine(id)?.let { DayRoutine(it, dao.routineItems(id)) }

    /** Saves a template; with [id], replaces that one's name and steps. Returns its id. */
    suspend fun saveTemplate(id: Long?, name: String, steps: List<String>): Long = db.withTransaction {
        val clean = RoutineRules.cleanItems(steps)
        val templateId = if (id == null) {
            dao.insertTemplate(RoutineTemplateEntity(name = RoutineRules.cleanName(name), createdAtMillis = nowMillis()))
        } else {
            val existing = dao.template(id) ?: return@withTransaction id
            dao.updateTemplate(existing.copy(name = RoutineRules.cleanName(name)))
            dao.clearTemplateItems(id)
            id
        }
        dao.insertTemplateItems(clean.mapIndexed { i, text -> RoutineTemplateItemEntity(templateId = templateId, position = i, text = text) })
        templateId
    }

    suspend fun deleteTemplate(id: Long) = dao.deleteTemplate(id)

    /** Plans a routine on [date] with [steps]. Returns its id, or null if the date can't be planned. */
    suspend fun plan(date: LocalDate, name: String, steps: List<String>, templateId: Long? = null): Long? = db.withTransaction {
        val day = date.toEpochDay()
        if (!RoutineRules.canPlan(day, today().toEpochDay())) return@withTransaction null
        val id = dao.insertRoutine(
            DayRoutineEntity(
                epochDay = day,
                name = RoutineRules.cleanName(name),
                templateId = templateId,
                position = dao.lastPositionOn(day) + 1,
                createdAtMillis = nowMillis(),
            ),
        )
        dao.insertItems(RoutineRules.cleanItems(steps).mapIndexed { i, text -> DayRoutineItemEntity(routineId = id, position = i, text = text, doneAtMillis = null) })
        id
    }

    /** Plans a copy of a template on each of [dates]. Returns how many were planned. */
    suspend fun planFromTemplate(templateId: Long, dates: List<LocalDate>): Int {
        val t = template(templateId) ?: return 0
        return dates.count { plan(it, t.template.name, t.items.map { item -> item.text }, templateId) != null }
    }

    /** Saves a day routine's steps as a new template. Returns its id. */
    suspend fun saveAsTemplate(routineId: Long, name: String? = null): Long? {
        val r = dayRoutine(routineId) ?: return null
        return saveTemplate(null, name ?: r.routine.name, r.items.map { it.text })
    }

    /**
     * Renames a day routine and sets its steps: kept items keep their ticks
     * (matched by id), new texts are added, missing ones removed along with
     * any power they gave.
     */
    suspend fun editRoutine(routineId: Long, name: String, steps: List<Pair<Long?, String>>) = db.withTransaction {
        val r = dao.routine(routineId) ?: return@withTransaction
        dao.updateRoutine(r.copy(name = RoutineRules.cleanName(name)))
        val existing = dao.routineItems(routineId).associateBy { it.id }
        val kept = steps.filter { it.second.isNotBlank() }
        val keptIds = kept.mapNotNull { it.first }.toSet()
        for (item in existing.values) if (item.id !in keptIds) {
            dao.deleteItem(item.id)
            db.powerDao().delete(PowerSource.ROUTINE_ITEM, item.id)
        }
        kept.forEachIndexed { i, (id, text) ->
            val old = id?.let(existing::get)
            if (old != null) dao.updateItem(old.copy(position = i, text = text.trim()))
            else dao.insertItems(listOf(DayRoutineItemEntity(routineId = routineId, position = i, text = text.trim(), doneAtMillis = null)))
        }
    }

    /** Deletes a day routine, and the power its items gave. */
    suspend fun deleteRoutine(routineId: Long) = db.withTransaction {
        dao.routineItems(routineId).forEach { db.powerDao().delete(PowerSource.ROUTINE_ITEM, it.id) }
        dao.deleteRoutine(routineId)
    }

    /**
     * Checks an item off, or unchecks it. Checking off today's items gives
     * power; unchecking takes it back. Returns the power gained (negative when
     * taken back).
     */
    suspend fun toggle(itemId: Long): Int = db.withTransaction {
        val item = dao.item(itemId) ?: return@withTransaction 0
        val routine = dao.routine(item.routineId) ?: return@withTransaction 0
        if (item.doneAtMillis == null) {
            dao.updateItem(item.copy(doneAtMillis = nowMillis()))
            val power = RoutineRules.powerFor(routine.epochDay, today().toEpochDay())
            if (power > 0) db.powerDao().insert(PowerEventEntity(0, routine.epochDay, power, PowerSource.ROUTINE_ITEM, itemId, nowMillis()))
            power
        } else {
            dao.updateItem(item.copy(doneAtMillis = null))
            val taken = db.powerDao().pointsFor(PowerSource.ROUTINE_ITEM, itemId)
            db.powerDao().delete(PowerSource.ROUTINE_ITEM, itemId)
            -taken
        }
    }
}
