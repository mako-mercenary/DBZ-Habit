package com.cdlstudy.domain

/**
 * A diagram, as geometry rather than as a picture.
 *
 * The lessons are text, and some of this material does not go into sentences.
 * The air brake system is a chain, the fifth wheel is a cross-section, and the
 * walk-around is a route. A paragraph about a chain is a poor way to learn a
 * chain.
 *
 * These are drawn here as coordinates in a 100 by 100 box, for two reasons.
 * The app draws them on a canvas at any size, with the theme's own colors, and
 * a unit test renders the same coordinates to SVG so they can be looked at
 * before they ship. One source of geometry, so the preview cannot drift from
 * what the app draws.
 *
 * Every one is drawn from scratch. None of the manual's figures is traced or
 * copied: the manual is not in this app at all.
 */
data class Diagram(
    val id: String,
    val title: String,
    /** What the diagram is for, in one or two sentences. */
    val caption: String,
    /** The facts it draws, so you can go and read them. */
    val factIds: List<String> = emptyList(),
    val shapes: List<Shape> = emptyList(),
) {
    /** Height as a fraction of width, so a tall diagram is not squashed. */
    val aspect: Float get() = 1f
}

/** How a shape is colored. The theme decides the actual color. */
enum class Ink {
    /** The ordinary line and label color. */
    LINE,

    /** The thing to look at. Gold in the app. */
    MARK,

    /** Background detail, such as the outline of the vehicle. */
    FAINT,

    /** The service line. Blue, as the couplers are. */
    SERVICE,

    /** The emergency line, and a danger callout. Red, as the couplers are. */
    EMERGENCY,
}

sealed interface Shape {
    val ink: Ink

    data class Line(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        override val ink: Ink = Ink.LINE,
        val dashed: Boolean = false,
        val weight: Float = 1f,
    ) : Shape

    data class Arrow(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        override val ink: Ink = Ink.LINE,
        val weight: Float = 1f,
    ) : Shape

    data class Box(
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        override val ink: Ink = Ink.LINE,
        val fill: Boolean = false,
        val round: Float = 0f,
    ) : Shape

    data class Dot(
        val cx: Float,
        val cy: Float,
        val r: Float,
        override val ink: Ink = Ink.LINE,
        val fill: Boolean = false,
    ) : Shape

    data class Label(
        val x: Float,
        val y: Float,
        val text: String,
        override val ink: Ink = Ink.LINE,
        val size: Float = 4f,
        val align: Align = Align.LEFT,
        val bold: Boolean = false,
    ) : Shape {
        enum class Align { LEFT, CENTER, RIGHT }
    }
}
