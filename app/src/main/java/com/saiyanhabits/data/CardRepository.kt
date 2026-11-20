package com.saiyanhabits.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.saiyanhabits.domain.CardBook
import com.saiyanhabits.domain.CardCollecting
import com.saiyanhabits.domain.CardDef
import com.saiyanhabits.domain.CardParser
import com.saiyanhabits.domain.Challenger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

/** Today's challenger, and what's at stake against them. */
data class ChallengerToday(
    val challenger: Challenger,
    /** True if a card can change hands in the next match: false once one has today, either way. */
    val stakes: Boolean,
    /** True if you already won a card from them today. */
    val wonToday: Boolean,
    /** True if they already took (or are holding on to) a card from you today. */
    val lostToday: Boolean,
    /** Your cards they hold. */
    val holding: List<CardDef>,
)

/** A match ready to play: the challenger's deck (to pick from if you win) and the five they'll play. */
data class CardMatchSetup(val challenger: Challenger, val deck: List<CardDef>, val hand: List<CardDef>, val stakes: Boolean)

/** A habit you could redo to keep a card. */
data class RedoOption(val habitId: Long, val name: String)

/** What happens to the card you lost. */
sealed interface LossOutcome {
    /** Nothing changes hands: no stakes today, or too few cards to give one up. */
    data object Nothing : LossOutcome

    /** The challenger took [card]. */
    data class Taken(val card: CardDef) : LossOutcome

    /** [card] is held until [habitName] is checked off again today. */
    data class Pending(val card: CardDef, val habitName: String) : LossOutcome
}

/**
 * Card battles: your collection, the daily pack, today's challenger, and what
 * changes hands after a match. A challenger who takes a card keeps it, and
 * always plays it against you, until you win it back.
 */
class CardRepository(
    context: Context,
    private val db: AppDatabase,
    private val habits: HabitRepository,
    private val today: () -> LocalDate = LocalDate::now,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {
    private val appContext = context.applicationContext
    private val dao = db.cardDao()
    private val lock = Mutex()
    private var cached: CardBook? = null

    suspend fun book(): CardBook = lock.withLock {
        cached ?: withContext(Dispatchers.IO) {
            try {
                CardParser.parse(appContext.assets.open("cards/cards.json").bufferedReader().use { it.readText() })
            } catch (e: Exception) {
                Log.e("Cards", "Could not read cards.json: ${e.message}")
                CardBook(emptyList(), emptyList())
            }
        }.also { cached = it }
    }

    /** Card id to how many you own. */
    val collection: Flow<Map<String, Int>> = dao.observeCollection().map { rows -> rows.associate { it.cardId to it.count } }

    /** Challenger id to the cards of yours they hold. */
    val held: Flow<Map<String, List<String>>> = dao.observeHeld().map { rows ->
        rows.groupBy { it.challengerId }.mapValues { (_, list) -> list.flatMap { r -> List(r.count) { r.cardId } } }
    }

    val matches: Flow<List<CardMatchEntity>> = dao.observeMatches()

    val pending: Flow<List<CardPendingEntity>> = dao.observeOpenPending()

    private suspend fun add(cardId: String, n: Int = 1) {
        val owned = dao.owned(cardId)
        dao.saveOwned(CardOwnedEntity(cardId, (owned?.count ?: 0) + n, owned?.firstAtMillis ?: nowMillis()))
    }

    private suspend fun remove(cardId: String) {
        val owned = dao.owned(cardId) ?: return
        dao.saveOwned(owned.copy(count = (owned.count - 1).coerceAtLeast(0)))
    }

    private suspend fun collectionSize(): Int = dao.collection().sumOf { it.count }

    /**
     * Gives the starter deck the first time, any bond cards your bonds have
     * unlocked, and settles cards saved by redoing a habit. Returns the cards
     * newly given, for a celebration.
     */
    suspend fun sync(): List<CardDef> {
        val book = book()
        if (book.cards.isEmpty()) return emptyList()
        val levels = db.bondDao().getBondXp().toLevels()
        val given = ArrayList<CardDef>()
        db.withTransaction {
            if (dao.granted(STARTER) == 0) {
                CardCollecting.starterDeck(book, random).forEach { add(it.id) }
                dao.grant(CardGrantEntity(STARTER, nowMillis()))
            }
            for (card in CardCollecting.bondCardsUnlocked(book, levels)) {
                val grant = "bond:${card.id}"
                if (dao.granted(grant) == 0) {
                    add(card.id)
                    dao.grant(CardGrantEntity(grant, nowMillis()))
                    given += card
                }
            }
        }
        settlePending()
        return given
    }

    /** Cards saved by a redo: kept once the habit is checked off again; lost if its day ended without it. */
    private suspend fun settlePending() {
        val todayDay = today().toEpochDay()
        for (p in dao.openPending()) {
            val redone = habits.isCompletedOn(p.habitId, LocalDate.ofEpochDay(p.epochDay))
            when {
                redone -> dao.updatePending(p.copy(outcome = KEPT))
                p.epochDay < todayDay -> db.withTransaction {
                    dao.updatePending(p.copy(outcome = LOST))
                    remove(p.cardId)
                    val h = dao.held(p.challengerId, p.cardId)
                    dao.saveHeld(CardHeldEntity(p.challengerId, p.cardId, (h?.count ?: 0) + 1))
                }
            }
        }
    }

    /** True when every habit that counts today is done, and at least one did. */
    suspend fun todayComplete(): Boolean {
        val snapshot = habits.todaySnapshot()
        return snapshot.any { it.countsToday } && habits.openToday().isEmpty()
    }

    /** True if today's pack is waiting to be opened. */
    suspend fun packWaiting(): Boolean = dao.pack(today().toEpochDay()) == null && todayComplete()

    /** Opens today's pack, if it's earned and not yet opened. */
    suspend fun openPack(): List<CardDef>? {
        val book = book()
        if (!packWaiting()) return null
        val cards = CardCollecting.openPack(book, random)
        db.withTransaction {
            dao.insertPack(CardPackEntity(today().toEpochDay(), cards.joinToString(",") { it.id }, nowMillis()))
            cards.forEach { add(it.id) }
        }
        return cards
    }

    /** Today's challenger: the same one all day. */
    suspend fun challengerToday(): ChallengerToday? {
        val book = book()
        val day = today().toEpochDay()
        val matches = dao.matchesOn(day)
        val pool = CardCollecting.pool(book, db.bondDao().getBondXp().toLevels())
        val challenger = CardCollecting.challengerOfTheDay(pool, day) ?: return null
        val won = matches.any { it.challengerId == challenger.id && it.result == WIN && it.cardWon != null }
        val lost = matches.any { it.challengerId == challenger.id && it.result == LOSE && it.stakes }
        return ChallengerToday(challenger, CardCollecting.stakesToday(lost, won), won, lost, heldCards(challenger.id))
    }

    private fun stakes(challengerId: String, matches: List<CardMatchEntity>): Boolean {
        val won = matches.any { it.challengerId == challengerId && it.result == WIN && it.cardWon != null }
        val lost = matches.any { it.challengerId == challengerId && it.result == LOSE && it.stakes }
        return CardCollecting.stakesToday(lost, won)
    }

    private suspend fun heldCards(challengerId: String): List<CardDef> {
        val book = book()
        return dao.heldBy(challengerId).flatMap { r -> List(r.count) { book.card(r.cardId) } }.filterNotNull()
    }

    /** Sets up a match against [challenger]: a fresh random deck, with any of your cards they hold in their hand. */
    suspend fun setUp(challenger: Challenger): CardMatchSetup {
        val book = book()
        val held = heldCards(challenger.id)
        val deck = CardCollecting.challengerDeck(book, challenger, random)
        val hand = CardCollecting.challengerHand(deck, held, challenger.signature)
        return CardMatchSetup(challenger, deck + held, hand, stakes(challenger.id, dao.matchesOn(today().toEpochDay())))
    }

    /**
     * Records a win. With stakes you get [chosen] from their deck (one of
     * yours they held comes back); a friendly rematch win records nothing won.
     */
    suspend fun recordWin(setup: CardMatchSetup, chosen: CardDef?) = db.withTransaction {
        if (setup.stakes && chosen != null) {
            add(chosen.id)
            val h = dao.held(setup.challenger.id, chosen.id)
            if (h != null && h.count > 0) dao.saveHeld(h.copy(count = h.count - 1))
            dao.insertMatch(CardMatchEntity(0, today().toEpochDay(), setup.challenger.id, WIN, true, chosen.id, null, nowMillis()))
        } else {
            dao.insertMatch(CardMatchEntity(0, today().toEpochDay(), setup.challenger.id, WIN, false, null, null, nowMillis()))
        }
    }

    suspend fun recordDraw(setup: CardMatchSetup) {
        dao.insertMatch(CardMatchEntity(0, today().toEpochDay(), setup.challenger.id, DRAW, false, null, null, nowMillis()))
    }

    /** The card a loss would cost, or null if none would change hands. */
    suspend fun cardAtStake(setup: CardMatchSetup, playedHand: List<CardDef>): CardDef? =
        if (CardCollecting.loses(collectionSize(), setup.stakes)) CardCollecting.cardTaken(playedHand) else null

    /** Habits checked off today that could be redone to keep a card. */
    suspend fun redoOptions(): List<RedoOption> {
        val day = today().toEpochDay()
        val done = db.completionDao().getBetween(day, day).map { it.habitId }.toSet()
        return habits.getActiveHabits().filter { it.id in done }.map { RedoOption(it.id, it.name) }
    }

    /**
     * Records a loss. With [redo], that habit is unchecked and the card is
     * only lost if the habit isn't checked off again today; otherwise the
     * challenger takes it now.
     */
    suspend fun recordLoss(setup: CardMatchSetup, playedHand: List<CardDef>, redo: RedoOption?): LossOutcome {
        val card = cardAtStake(setup, playedHand)
        val day = today().toEpochDay()
        if (card == null) {
            dao.insertMatch(CardMatchEntity(0, day, setup.challenger.id, LOSE, false, null, null, nowMillis()))
            return LossOutcome.Nothing
        }
        if (redo != null) {
            db.withTransaction {
                dao.insertMatch(CardMatchEntity(0, day, setup.challenger.id, LOSE, true, null, card.id, nowMillis()))
                dao.insertPending(CardPendingEntity(0, day, setup.challenger.id, card.id, redo.habitId, ""))
            }
            habits.undoCompletion(redo.habitId, today())
            return LossOutcome.Pending(card, redo.name)
        }
        db.withTransaction {
            dao.insertMatch(CardMatchEntity(0, day, setup.challenger.id, LOSE, true, null, card.id, nowMillis()))
            remove(card.id)
            val h = dao.held(setup.challenger.id, card.id)
            dao.saveHeld(CardHeldEntity(setup.challenger.id, card.id, (h?.count ?: 0) + 1))
        }
        return LossOutcome.Taken(card)
    }

    private companion object {
        const val STARTER = "starter"
        const val WIN = "WIN"
        const val LOSE = "LOSE"
        const val DRAW = "DRAW"
        const val KEPT = "KEPT"
        const val LOST = "LOST"
    }
}
