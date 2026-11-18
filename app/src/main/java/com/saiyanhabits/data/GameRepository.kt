package com.saiyanhabits.data

import androidx.room.withTransaction
import com.saiyanhabits.domain.Mentor
import com.saiyanhabits.domain.MiniGame
import com.saiyanhabits.domain.MiniGameRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** What a finished play earned. */
data class PlayResult(val bondXp: Int, val bondLevelUp: Int?)

/** Records mini game plays and reads back records. */
class GameRepository(
    private val db: AppDatabase,
    private val today: () -> LocalDate = LocalDate::now,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.gameDao()

    private val _levelUps = MutableSharedFlow<GameEvent.BondLevelUp>(extraBufferCapacity = 4)

    /** Bond levels gained from mini games, as they happen. */
    val levelUps: SharedFlow<GameEvent.BondLevelUp> = _levelUps

    fun plays(game: MiniGame): Flow<List<MinigamePlayEntity>> = dao.observePlays(game)

    /** Each game's best score so far, for the games list. Games never played are missing. */
    val bests: Flow<Map<MiniGame, Long>> = dao.observeAll().map { plays ->
        plays.groupBy { it.game }.mapValues { (game, list) ->
            when (game) {
                // Chess counts wins rather than a best.
                MiniGame.CHESS -> list.count { it.score > 0 }.toLong()
                else -> if (game.higherIsBetter) list.maxOf { it.score } else list.minOf { it.score }
            }
        }
    }

    /** Records a finished play and gives its mentor bond XP, up to the daily cap. */
    suspend fun record(game: MiniGame, score: Long, detail: String = ""): PlayResult {
        val result = db.withTransaction {
            val day = today().toEpochDay()
            val mentor: Mentor = game.mentor
            val xp = MiniGameRules.bondXp(dao.bondXpOn(mentor, day))
            val before = db.bondDao().getBondXp().toLevels().getValue(mentor)
            dao.insert(MinigamePlayEntity(0, game, mentor, day, score, detail, xp, nowMillis()))
            val after = db.bondDao().getBondXp().toLevels().getValue(mentor)
            PlayResult(xp, after.takeIf { it > before })
        }
        result.bondLevelUp?.let { _levelUps.emit(GameEvent.BondLevelUp(game.mentor, it)) }
        return result
    }
}
