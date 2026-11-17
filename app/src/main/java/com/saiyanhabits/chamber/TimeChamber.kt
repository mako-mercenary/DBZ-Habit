package com.saiyanhabits.chamber

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.saiyanhabits.data.HabitRepository
import com.saiyanhabits.data.QuestRepository
import com.saiyanhabits.domain.ChamberTargetType
import com.saiyanhabits.domain.SpiritBombRules
import com.saiyanhabits.reminders.Notifications
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/** The habit, saga milestone or spirit bomb a session is spent on. */
data class ChamberTarget(val type: ChamberTargetType, val id: Long, val label: String)

data class ChamberSession(
    val minutes: Int,
    val startedAtMillis: Long,
    val endsAtMillis: Long,
    val target: ChamberTarget,
)

/** A question the app still needs answered after a session, to finish logging it. */
sealed interface ChamberFollowUp {
    /** The bomb counts something other than minutes or sessions: how much got done? */
    data class BombAmount(val bombId: Long, val unit: String) : ChamberFollowUp

    /** The milestone is charged: is it also finished? */
    data class MilestoneFinished(val milestoneId: Long, val name: String) : ChamberFollowUp
}

/** What a finished session logged, as lines to show, plus anything still to ask. */
data class ChamberOutcome(val lines: List<String>, val followUp: ChamberFollowUp? = null)

sealed interface ChamberState {
    data object Idle : ChamberState
    data class Running(val session: ChamberSession) : ChamberState
    data class Finished(val session: ChamberSession, val outcome: ChamberOutcome) : ChamberState
}

/**
 * The focus timer. Holds the one session that can be running, saves it so it
 * survives the app being killed, and logs its rewards when time is up.
 * [TimeChamberService] keeps the process alive and counts the time down.
 */
class TimeChamber(
    private val context: Context,
    private val habits: HabitRepository,
    private val quests: QuestRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val prefs = context.getSharedPreferences("time_chamber", Context.MODE_PRIVATE)
    private val finishing = Mutex()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<ChamberState> = _state

    fun start(minutes: Int, target: ChamberTarget) {
        if (_state.value !is ChamberState.Idle) return
        val now = nowMillis()
        setState(ChamberState.Running(ChamberSession(minutes, now, now + minutes * 60_000L, target)))
        startService()
    }

    /** Gives up on the running session. Nothing is logged. */
    fun cancel() {
        if (_state.value is ChamberState.Running) setState(ChamberState.Idle)
        context.stopService(Intent(context, TimeChamberService::class.java))
    }

    /** Clears a finished session once its result has been read. */
    fun dismissResult() {
        if (_state.value is ChamberState.Finished) setState(ChamberState.Idle)
        Notifications.cancelChamberFinished(context)
    }

    /**
     * Call when the app starts. Picks a saved session back up: finishes it if
     * its time ran out while the app was dead, otherwise restarts the countdown.
     */
    suspend fun restore() {
        val running = _state.value as? ChamberState.Running ?: return
        if (nowMillis() >= running.session.endsAtMillis) finish() else startService()
    }

    /** Time is up: logs the session to whatever it was linked to. Safe to call twice. */
    suspend fun finish(): ChamberOutcome? = finishing.withLock {
        val running = _state.value as? ChamberState.Running ?: return null
        val session = running.session
        val outcome = logRewards(session)
        quests.recordFocusSession(session.minutes, session.target.type, session.target.id)
        setState(ChamberState.Finished(session, outcome))
        outcome
    }

    /** Answers [ChamberFollowUp.BombAmount]. */
    suspend fun logBombAmount(amount: Int) {
        val finished = _state.value as? ChamberState.Finished ?: return
        val followUp = finished.outcome.followUp as? ChamberFollowUp.BombAmount ?: return
        val result = quests.logBombProgress(followUp.bombId, amount, viaTimeChamber = true)
        val line = if (result == null) {
            "Nothing was logged."
        } else {
            "Logged ${result.amount} ${followUp.unit}: +${result.points} and +${result.focusBonus} time chamber bonus."
        }
        setState(finished.copy(outcome = ChamberOutcome(finished.outcome.lines + line)))
    }

    /** Answers [ChamberFollowUp.MilestoneFinished]. */
    suspend fun answerMilestoneFinished(isFinished: Boolean) {
        val finished = _state.value as? ChamberState.Finished ?: return
        val followUp = finished.outcome.followUp as? ChamberFollowUp.MilestoneFinished ?: return
        var lines = finished.outcome.lines
        if (isFinished) {
            val strike = quests.strike(followUp.milestoneId)
            if (strike != null) {
                lines = lines + "Hit for ${strike.damage} damage: +${strike.points}."
                if (strike.victoryBonus != null) lines = lines + "Boss defeated: +${strike.victoryBonus}."
            }
        }
        setState(finished.copy(outcome = ChamberOutcome(lines)))
    }

    private suspend fun logRewards(session: ChamberSession): ChamberOutcome {
        val target = session.target
        return when (target.type) {
            ChamberTargetType.HABIT -> {
                val result = habits.completeViaTimeChamber(target.id)
                    ?: return ChamberOutcome(listOf("\"${target.label}\" no longer exists, so nothing was logged."))
                ChamberOutcome(
                    listOfNotNull(
                        if (result.checkOffPoints > 0) "Checked off \"${result.habitName}\": +${result.checkOffPoints}." else null,
                        if (result.focusBonus > 0) {
                            "Time chamber bonus: +${result.focusBonus}."
                        } else {
                            "Today's time chamber bonus for \"${result.habitName}\" was already earned."
                        },
                    ),
                )
            }

            ChamberTargetType.MILESTONE -> {
                if (quests.chargeMilestone(target.id)) {
                    ChamberOutcome(
                        lines = listOf("\"${target.label}\" is charged: it will pay extra points when it lands."),
                        followUp = ChamberFollowUp.MilestoneFinished(target.id, target.label),
                    )
                } else {
                    ChamberOutcome(listOf("\"${target.label}\" was already finished, so nothing was logged."))
                }
            }

            ChamberTargetType.BOMB -> {
                val bomb = quests.getBomb(target.id)
                    ?: return ChamberOutcome(listOf("\"${target.label}\" no longer exists, so nothing was logged."))
                val amount = when {
                    SpiritBombRules.isMinutesUnit(bomb.unit) -> session.minutes
                    SpiritBombRules.isSessionsUnit(bomb.unit) -> 1
                    else -> return ChamberOutcome(
                        lines = listOf("Session spent on \"${bomb.name}\"."),
                        followUp = ChamberFollowUp.BombAmount(bomb.id, bomb.unit),
                    )
                }
                val result = quests.logBombProgress(bomb.id, amount, viaTimeChamber = true)
                ChamberOutcome(
                    listOf(
                        if (result == null) {
                            "\"${bomb.name}\" is already full, so nothing was logged."
                        } else {
                            "Logged ${result.amount} ${bomb.unit} to \"${bomb.name}\": " +
                                "+${result.points} and +${result.focusBonus} time chamber bonus."
                        },
                    ),
                )
            }
        }
    }

    private fun startService() {
        try {
            ContextCompat.startForegroundService(context, Intent(context, TimeChamberService::class.java))
        } catch (_: RuntimeException) {
            // Android refuses to start it while the app is in the background. The
            // session is saved, so it is picked up the next time the app opens.
        }
    }

    private fun setState(state: ChamberState) {
        _state.value = state
        prefs.edit().putString(KEY_STATE, encode(state)).apply()
    }

    private fun load(): ChamberState =
        try {
            decode(prefs.getString(KEY_STATE, null))
        } catch (_: Exception) {
            ChamberState.Idle
        }

    private companion object {
        const val KEY_STATE = "state"

        fun encode(state: ChamberState): String? = when (state) {
            ChamberState.Idle -> null
            is ChamberState.Running -> JSONObject().put("session", encode(state.session)).toString()
            is ChamberState.Finished -> JSONObject()
                .put("session", encode(state.session))
                .put("lines", JSONArray(state.outcome.lines))
                .apply {
                    when (val followUp = state.outcome.followUp) {
                        is ChamberFollowUp.BombAmount ->
                            put("ask", "bomb").put("askId", followUp.bombId).put("askText", followUp.unit)
                        is ChamberFollowUp.MilestoneFinished ->
                            put("ask", "milestone").put("askId", followUp.milestoneId).put("askText", followUp.name)
                        null -> Unit
                    }
                }
                .toString()
        }

        fun encode(session: ChamberSession): JSONObject = JSONObject()
            .put("minutes", session.minutes)
            .put("startedAt", session.startedAtMillis)
            .put("endsAt", session.endsAtMillis)
            .put("targetType", session.target.type.name)
            .put("targetId", session.target.id)
            .put("targetLabel", session.target.label)

        fun decode(text: String?): ChamberState {
            if (text == null) return ChamberState.Idle
            val json = JSONObject(text)
            val s = json.getJSONObject("session")
            val session = ChamberSession(
                minutes = s.getInt("minutes"),
                startedAtMillis = s.getLong("startedAt"),
                endsAtMillis = s.getLong("endsAt"),
                target = ChamberTarget(
                    ChamberTargetType.valueOf(s.getString("targetType")),
                    s.getLong("targetId"),
                    s.getString("targetLabel"),
                ),
            )
            if (!json.has("lines")) return ChamberState.Running(session)
            val linesJson = json.getJSONArray("lines")
            val lines = List(linesJson.length()) { linesJson.getString(it) }
            val followUp = when (json.optString("ask")) {
                "bomb" -> ChamberFollowUp.BombAmount(json.getLong("askId"), json.getString("askText"))
                "milestone" -> ChamberFollowUp.MilestoneFinished(json.getLong("askId"), json.getString("askText"))
                else -> null
            }
            return ChamberState.Finished(session, ChamberOutcome(lines, followUp))
        }
    }
}
