package com.saiyanhabits.domain

import com.saiyanhabits.GameConfig
import com.saiyanhabits.domain.DayOutcome.BEAN_COVERED
import com.saiyanhabits.domain.DayOutcome.BROKEN
import com.saiyanhabits.domain.DayOutcome.MISSED
import com.saiyanhabits.domain.DayOutcome.PERFECT
import com.saiyanhabits.domain.DayOutcome.REST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

class ProgressRulesTest {

    // Monday 16 Nov 2026.
    private val monday = LocalDate.of(2026, 11, 16).toEpochDay()

    // ---- Weeks ----

    @Test
    fun `weeks start on Monday`() {
        for (offset in 0L..6L) assertEquals(monday, Weeks.startOf(monday + offset))
        assertEquals(monday - 7, Weeks.startOf(monday - 1))
        assertEquals(monday + 7, Weeks.startOf(monday + 7))
    }

    @Test
    fun `week starts agree with the calendar for many dates`() {
        for (day in -1000L..30_000L step 37) {
            val start = LocalDate.ofEpochDay(Weeks.startOf(day))
            assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
            assertTrue(day - start.toEpochDay() in 0..6)
        }
    }

    // ---- Story ----

    @Test
    fun `there are eight chapters and the first is open from the start`() {
        assertEquals(8, StoryRules.CHAPTER_COUNT)
        assertEquals(GameConfig.STARTING_POWER_LEVEL, GameConfig.STORY_CHAPTER_POWER_LEVELS.first())
        assertTrue(StoryRules.isUnlocked(1, GameConfig.STARTING_POWER_LEVEL.toLong(), highestForm = 0))
        assertFalse(StoryRules.isUnlocked(2, GameConfig.STARTING_POWER_LEVEL.toLong(), highestForm = 0))
    }

    @Test
    fun `chapters unlock at their power levels`() {
        GameConfig.STORY_CHAPTER_POWER_LEVELS.forEachIndexed { index, level ->
            val chapter = index + 1
            assertEquals(StoryRequirement.PowerLevel(level), StoryRules.requirement(chapter))
            assertFalse(chapter > 1 && StoryRules.isUnlocked(chapter, level - 1L, highestForm = 0))
            assertTrue(StoryRules.isUnlocked(chapter, level.toLong(), highestForm = 0))
        }
    }

    @Test
    fun `power thresholds rise chapter by chapter`() {
        assertTrue(GameConfig.STORY_CHAPTER_POWER_LEVELS.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun `the final chapter needs form 3, however high the power level`() {
        val last = StoryRules.CHAPTER_COUNT
        assertEquals(StoryRequirement.Form(3), StoryRules.requirement(last))
        assertFalse(StoryRules.isUnlocked(last, powerLevel = 9_999_999, highestForm = 2))
        assertTrue(StoryRules.isUnlocked(last, powerLevel = 5, highestForm = 3))
    }

    @Test
    fun `chapters outside the story are never unlocked`() {
        assertFalse(StoryRules.isUnlocked(0, 9_999_999, 3))
        assertFalse(StoryRules.isUnlocked(StoryRules.CHAPTER_COUNT + 1, 9_999_999, 3))
    }

    @Test
    fun `reading a chapter is remembered per chapter`() {
        var read = 0
        assertFalse(StoryRules.isRead(1, read))
        read = StoryRules.markRead(read, 1)
        read = StoryRules.markRead(read, 3)
        assertTrue(StoryRules.isRead(1, read))
        assertFalse(StoryRules.isRead(2, read))
        assertTrue(StoryRules.isRead(3, read))
        assertEquals(read, StoryRules.markRead(read, 3))
    }

    @Test
    fun `the first unread unlocked chapter is the one to show`() {
        assertEquals(1, StoryRules.firstUnread(powerLevel = 5, highestForm = 0, readMask = 0))
        val readOne = StoryRules.markRead(0, 1)
        assertNull(StoryRules.firstUnread(powerLevel = 50, highestForm = 0, readMask = readOne))
        assertEquals(2, StoryRules.firstUnread(powerLevel = 350, highestForm = 0, readMask = readOne))
        val readThree = StoryRules.markRead(StoryRules.markRead(readOne, 2), 3)
        assertEquals(8, StoryRules.firstUnread(powerLevel = 350, highestForm = 3, readMask = readThree))
    }

    // ---- Wish orbs ----

    @Test
    fun `a week with every day perfect earns an orb`() {
        assertTrue(WishRules.isPerfectWeek(List(7) { PERFECT }))
    }

    @Test
    fun `rest days do not spoil a perfect week`() {
        assertTrue(WishRules.isPerfectWeek(listOf(PERFECT, PERFECT, PERFECT, PERFECT, PERFECT, REST, REST)))
    }

    @Test
    fun `a week of nothing but rest earns nothing`() {
        assertFalse(WishRules.isPerfectWeek(List(7) { REST }))
    }

    @Test
    fun `one missed day spoils the week, even if a bean covered it`() {
        for (spoiler in listOf(MISSED, BROKEN, BEAN_COVERED)) {
            assertFalse(WishRules.isPerfectWeek(List(6) { PERFECT } + spoiler))
        }
    }

    @Test
    fun `a week without all seven days on record earns nothing`() {
        assertFalse(WishRules.isPerfectWeek(List(6) { PERFECT }))
        assertFalse(WishRules.isPerfectWeek(emptyList()))
    }

    @Test
    fun `a week is judged once it has finished`() {
        // First record on the Monday; nothing to judge until the following Monday.
        assertEquals(emptyList<Long>(), WishRules.weeksToJudge(null, monday, todayEpochDay = monday + 6))
        assertEquals(listOf(monday), WishRules.weeksToJudge(null, monday, todayEpochDay = monday + 7))
    }

    @Test
    fun `weeks are judged once each, oldest first`() {
        assertEquals(emptyList<Long>(), WishRules.weeksToJudge(monday, monday, todayEpochDay = monday + 10))
        assertEquals(
            listOf(monday + 7, monday + 14),
            WishRules.weeksToJudge(lastJudgedWeekStart = monday, firstRecordedDay = monday, todayEpochDay = monday + 23),
        )
    }

    @Test
    fun `judging starts with the week of the first record`() {
        // First record on a Wednesday: that part-week is judged (and fails for lack of days).
        assertEquals(listOf(monday), WishRules.weeksToJudge(null, monday + 2, todayEpochDay = monday + 8))
        assertEquals(emptyList<Long>(), WishRules.weeksToJudge(null, null, todayEpochDay = monday + 8))
    }

    @Test
    fun `at most seven orbs are held`() {
        assertEquals(7, GameConfig.WISH_ORBS_NEEDED)
        assertEquals(1, WishRules.orbsAfterPerfectWeek(0))
        assertEquals(7, WishRules.orbsAfterPerfectWeek(6))
        assertEquals(7, WishRules.orbsAfterPerfectWeek(7))
    }

    @Test
    fun `the dragon can be summoned only with all seven orbs`() {
        assertFalse(WishRules.canSummon(6))
        assertTrue(WishRules.canSummon(7))
    }

    // ---- Rival ----

    @Test
    fun `the rival is last week's total`() {
        val points = mapOf(monday - 7 to 100, monday - 5 to 50, monday - 1 to 30, monday to 40)
        val standing = RivalRules.standing(points, todayEpochDay = monday)
        assertEquals(180, standing.rivalTotal)
        assertEquals(40, standing.mine)
    }

    @Test
    fun `mid-week the comparison is with last week at the same weekday`() {
        // Last week: 100 on Monday, 50 on Wednesday, 30 on Sunday. This week so far: 120 by Wednesday.
        val points = mapOf(monday - 7 to 100, monday - 5 to 50, monday - 1 to 30, monday to 70, monday + 2 to 50)
        val tuesday = RivalRules.standing(points, todayEpochDay = monday + 1)
        assertEquals(100, tuesday.rivalByNow)
        assertEquals(70, tuesday.mine)
        assertEquals(-30, tuesday.lead)

        val wednesday = RivalRules.standing(points, todayEpochDay = monday + 2)
        assertEquals(150, wednesday.rivalByNow)
        assertEquals(120, wednesday.mine)
        assertEquals(-30, wednesday.lead)
        assertFalse(wednesday.beaten)
    }

    @Test
    fun `being ahead of the pace is not yet beating the rival`() {
        val points = mapOf(monday - 7 to 10, monday - 1 to 200, monday to 60)
        val standing = RivalRules.standing(points, todayEpochDay = monday)
        assertEquals(50, standing.lead)
        assertFalse(standing.beaten)
    }

    @Test
    fun `the rival is beaten once this week passes all of last week`() {
        val points = mapOf(monday - 7 to 100, monday to 60, monday + 1 to 60)
        assertFalse(RivalRules.standing(points, monday).beaten)
        assertTrue(RivalRules.standing(points, monday + 1).beaten)
    }

    @Test
    fun `by Sunday the pace and the total are the same thing`() {
        val points = mapOf(monday - 7 to 100, monday - 1 to 30)
        val sunday = RivalRules.standing(points, todayEpochDay = monday + 6)
        assertEquals(sunday.rivalTotal, sunday.rivalByNow)
    }

    @Test
    fun `there is no rival until a week has earned something`() {
        val standing = RivalRules.standing(mapOf(monday to 50), todayEpochDay = monday)
        assertFalse(standing.hasRival)
        assertFalse(standing.beaten)
        assertEquals(50, standing.mine)
    }

    @Test
    fun `points from other weeks are ignored`() {
        val points = mapOf(monday - 14 to 999, monday - 7 to 10, monday to 5, monday + 7 to 999)
        val standing = RivalRules.standing(points, todayEpochDay = monday + 3)
        assertEquals(10, standing.rivalTotal)
        assertEquals(5, standing.mine)
    }

    // ---- The story file that ships ----

    private val story: List<StoryChapter> by lazy {
        val file = File("src/main/assets/dialogue/story.json")
        assertTrue("Missing ${file.path}", file.isFile)
        StoryParser.parse(file.readText())
    }

    @Test
    fun `the story has one chapter for every unlock`() {
        assertEquals((1..StoryRules.CHAPTER_COUNT).toList(), story.map { it.number })
        assertTrue(story.all { it.title.isNotBlank() })
        assertEquals(story.size, story.map { it.title }.toSet().size)
    }

    @Test
    fun `every chapter reads in one to two minutes`() {
        // Around 200 words a minute: at least a short scene, at most two minutes.
        for (chapter in story) {
            assertTrue("Chapter ${chapter.number} has ${chapter.wordCount} words", chapter.wordCount in 120..400)
            assertTrue("Chapter ${chapter.number} has a blank line", chapter.lines.none { it.text.isBlank() })
        }
    }

    @Test
    fun `story speakers are mentors, the player or the narrator`() {
        val known = Mentor.entries.map { it.name }.toSet() + setOf("YOU", "NARRATOR")
        for (chapter in story) {
            val unknown = chapter.lines.map { it.speaker }.toSet() - known
            assertTrue("Chapter ${chapter.number} has speakers $unknown", unknown.isEmpty())
        }
    }

    @Test
    fun `the first chapter is Roshi's and the next four each bring in a new mentor`() {
        fun mentorsIn(chapter: Int) = story.first { it.number == chapter }.lines.mapNotNull { it.mentor }.toSet()
        assertEquals(setOf(Mentor.ROSHI), mentorsIn(1))
        val introduced = mutableSetOf(Mentor.ROSHI)
        for ((chapter, newcomer) in listOf(2 to Mentor.GOKU, 3 to Mentor.PICCOLO, 4 to Mentor.BULMA, 5 to Mentor.VEGETA)) {
            assertEquals("Chapter $chapter should introduce $newcomer", setOf(newcomer), mentorsIn(chapter) - introduced)
            introduced += newcomer
        }
        assertEquals(Mentor.entries.toSet(), mentorsIn(story.last().number).let { introduced })
    }
}
