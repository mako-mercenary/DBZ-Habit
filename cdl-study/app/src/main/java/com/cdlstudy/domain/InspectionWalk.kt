package com.cdlstudy.domain

/**
 * The pre-trip inspection, area by area, in the order you walk it.
 *
 * The test is not a quiz. The examiner walks you round the vehicle and at each
 * area you name the item, point to it or touch it, and say what you are
 * inspecting it for. Leave out any of the three and the item scores nothing.
 *
 * A flat pool of 81 facts cannot train that, because the real thing is a route.
 * This turns Section 11 into the route: the areas in order, what waits in each,
 * and how far round you have got.
 */
object InspectionWalk {

    /** Section 11. The whole inspection test lives in one section. */
    const val SECTION = 11

    data class Area(
        val subsectionId: String,
        val title: String,
        val items: Int,
        val mustMemorize: Int,
        /** True for an area only a combination or a trailer has. */
        val combinationOnly: Boolean,
    )

    data class Progress(
        val areas: List<Area>,
        /** Area ids with a recorded recall. */
        val done: Set<String>,
    ) {
        val total: Int get() = areas.size
        val cleared: Int get() = areas.count { it.subsectionId in done }
        val percent: Int get() = if (total == 0) 0 else 100 * cleared / total
        val complete: Boolean get() = total > 0 && cleared == total

        /** The next area to walk: the first one not cleared. */
        val next: Area? get() = areas.firstOrNull { it.subsectionId !in done }
    }

    /**
     * The areas, in the order the section lists them, which is the order you
     * walk the vehicle. A heading that only groups other areas is left out,
     * and so is an area with no items to name.
     */
    fun areas(content: Content): List<Area> {
        val section = content.section(SECTION) ?: return emptyList()
        return section.parts
            .filter { it.facts.isNotEmpty() }
            .map { sub ->
                Area(
                    subsectionId = sub.id,
                    title = sub.title,
                    items = sub.facts.size,
                    mustMemorize = sub.criticalFacts.size,
                    // 11.6 is the combination and trailer part of the section.
                    combinationOnly = sub.id.startsWith("11.6"),
                )
            }
    }

    fun progress(content: Content, recalled: Set<String>): Progress {
        val areas = areas(content)
        return Progress(areas, recalled.filter { id -> areas.any { it.subsectionId == id } }.toSet())
    }

    /** What you must do at every item, which is the thing people lose points on. */
    const val THE_THREE_THINGS =
        "Name it. Point to it or touch it. Say what you are inspecting it for. " +
            "Leave out any one of the three and the item scores nothing."
}
