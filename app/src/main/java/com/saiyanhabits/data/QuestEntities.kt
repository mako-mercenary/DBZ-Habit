package com.saiyanhabits.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.saiyanhabits.domain.Boss
import com.saiyanhabits.domain.ChamberTargetType
import com.saiyanhabits.domain.MilestoneState
import com.saiyanhabits.domain.PowerSource
import com.saiyanhabits.domain.QuestStatus

/** A long goal fought as a boss. Its HP is the total weight of its milestones. */
@Entity(tableName = "sagas")
data class SagaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val boss: Boss,
    val status: QuestStatus = QuestStatus.ACTIVE,
    val createdAtEpochDay: Long,
    val finishedAtEpochDay: Long? = null,
    /** Power bonus paid when the boss was defeated; 0 until then. */
    val victoryBonus: Int = 0,
)

@Entity(
    tableName = "saga_milestones",
    foreignKeys = [
        ForeignKey(
            entity = SagaEntity::class,
            parentColumns = ["id"],
            childColumns = ["sagaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sagaId"])],
)
data class MilestoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sagaId: Long,
    val name: String,
    /** 1 to GameConfig.MAX_MILESTONE_WEIGHT. */
    val weight: Int,
    /** Order within the saga. */
    val position: Int,
    /** Time chamber sessions spent on this milestone. Any at all earns the focus multiplier. */
    val focusSessions: Int = 0,
    /** Null until the milestone is finished. */
    val completedAtEpochDay: Long? = null,
    /** Damage dealt when it was finished; 0 until then. */
    val damageDealt: Int = 0,
    /** Points earned when it was finished; 0 until then. */
    val points: Int = 0,
) {
    val isDone: Boolean get() = completedAtEpochDay != null
    fun toState() = MilestoneState(weight, damageDealt, isDone)
}

data class SagaWithMilestones(
    @Embedded val saga: SagaEntity,
    @Relation(parentColumn = "id", entityColumn = "sagaId")
    val unorderedMilestones: List<MilestoneEntity>,
) {
    val milestones: List<MilestoneEntity> get() = unorderedMilestones.sortedBy { it.position }
}

/** A long task with no milestones: a target amount that fills up as progress is logged. */
@Entity(tableName = "spirit_bombs")
data class SpiritBombEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** What is being counted, e.g. "minutes", "pages", "sessions". */
    val unit: String,
    val target: Int,
    val progress: Int = 0,
    /** 1 to 3: small, medium, large. Sets the points, see GameConfig. */
    val size: Int,
    val status: QuestStatus = QuestStatus.ACTIVE,
    val createdAtEpochDay: Long,
    val finishedAtEpochDay: Long? = null,
)

@Entity(
    tableName = "bomb_logs",
    foreignKeys = [
        ForeignKey(
            entity = SpiritBombEntity::class,
            parentColumns = ["id"],
            childColumns = ["bombId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["bombId"])],
)
data class BombLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bombId: Long,
    val amount: Int,
    val epochDay: Long,
    val createdAtMillis: Long,
    val viaTimeChamber: Boolean = false,
)

/**
 * The power ledger: every gain that is not a plain habit check-off. The power
 * level is the starting power, plus all check-off points, plus all of these.
 */
@Entity(
    tableName = "power_events",
    indices = [Index(value = ["source", "sourceId"]), Index(value = ["epochDay"])],
)
data class PowerEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val points: Int,
    val source: PowerSource,
    /** Id of the habit, milestone, saga, bomb log or bomb this came from, depending on [source]. */
    val sourceId: Long,
    val createdAtMillis: Long,
)

/** A finished time chamber session. */
@Entity(tableName = "focus_sessions", indices = [Index(value = ["epochDay"])])
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val minutes: Int,
    val targetType: ChamberTargetType,
    val targetId: Long,
    val finishedAtMillis: Long,
)
