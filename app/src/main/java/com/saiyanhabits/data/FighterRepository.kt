package com.saiyanhabits.data

import com.saiyanhabits.domain.FighterRules
import com.saiyanhabits.domain.FighterSheet
import com.saiyanhabits.domain.DayOutcome
import com.saiyanhabits.domain.ZeniRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Your fighter and your wallet, as your training has made them. */
data class FighterState(
    val sheet: FighterSheet,
    val zeni: Long,
    /** Highest form unlocked: the transformations you can use. */
    val highestForm: Int,
)

/** Puts the fighter together from power, bonds and days on record. Writes nothing except ledger entries. */
class FighterRepository(private val db: AppDatabase) {

    val state: Flow<FighterState> = combine(
        db.powerDao().observeTotalPoints(),
        db.bondDao().observeBondXp(),
        db.streakDao().observeAllRecords(),
        db.zeniDao().observeLedgerTotal(),
        db.streakDao().observePlayerState(),
    ) { points, bondXp, records, ledger, player ->
        val days = records.map { com.saiyanhabits.domain.PastDay(it.epochDay, it.scheduledCount, it.completedCount, it.outcome) }
        val earned = ZeniRules.earned(
            totalPoints = points,
            perfectDays = days.count { it.outcome == DayOutcome.PERFECT },
            perfectWeeks = ZeniRules.perfectWeeks(days),
        )
        FighterState(
            sheet = FighterRules.sheet(com.saiyanhabits.domain.PowerCalculator.powerLevel(points), bondXp.toLevels()),
            zeni = ZeniRules.balance(earned, ledger),
            highestForm = player?.highestForm ?: 0,
        )
    }
}
