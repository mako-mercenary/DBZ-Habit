package com.cdlstudy.domain

import com.cdlstudy.domain.Shape.Arrow
import com.cdlstudy.domain.Shape.Box
import com.cdlstudy.domain.Shape.Dot
import com.cdlstudy.domain.Shape.Label
import com.cdlstudy.domain.Shape.Label.Align
import com.cdlstudy.domain.Shape.Line

/** The diagrams the app ships. All drawn from scratch. */
object Diagrams {

    /** A labelled box in the air brake chain, with its pressure under it. */
    private fun stage(
        y: Float,
        name: String,
        detail: String,
        mark: Boolean = false,
    ): List<Shape> = listOf(
        Box(8f, y, 84f, 10f, if (mark) Ink.MARK else Ink.LINE, round = 2f),
        Label(12f, y + 4.3f, name, if (mark) Ink.MARK else Ink.LINE, 4.2f, bold = true),
        Label(12f, y + 8.3f, detail, Ink.FAINT, 3.4f),
    )

    private fun down(y: Float): Shape = Arrow(50f, y, 50f, y + 4.8f, Ink.FAINT)

    val airBrakeSystem = Diagram(
        id = "air-brake-system",
        title = "The air brake system, end to end",
        caption = "Air starts at the compressor and finishes pushing a shoe against a drum. " +
            "Every pressure you have to memorize belongs to one of these stages.",
        factIds = listOf(
            "5.1.2.1", "5.1.2.2", "5.1.6.1", "5.1.11.1", "5.1.14.3", "5.1.8.1",
        ),
        shapes = buildList {
            addAll(stage(1f, "Engine drives the compressor", "by gears or a v-belt"))
            add(down(11f))
            addAll(stage(15.8f, "Governor", "cut-out 125 psi · cut-in 100 psi", mark = true))
            add(down(25.8f))
            addAll(stage(30.6f, "Air tanks", "safety valve opens at 150 psi", mark = true))
            add(down(40.6f))
            addAll(stage(45.4f, "Brake pedal", "harder pedal, more air; fanning wastes it"))
            add(down(55.4f))
            addAll(stage(60.2f, "Brake chamber, slack adjuster", "over 1 inch of play needs adjusting", mark = true))
            add(down(70.2f))
            addAll(stage(75f, "S-cam turns, shoe meets drum", "friction, then heat, then fade"))

            // Falling pressure skips the chain: it goes straight to the
            // warning and then to the springs. Drawn as a line down the side.
            add(Line(8f, 35.6f, 3.5f, 35.6f, Ink.EMERGENCY))
            add(Line(3.5f, 35.6f, 3.5f, 50.4f, Ink.EMERGENCY))
            add(Arrow(3.5f, 50.4f, 8f, 50.4f, Ink.EMERGENCY))
            add(Label(50f, 91f, "pressure falling: warning before 55 psi,", Ink.EMERGENCY, 3.4f, Align.CENTER))
            add(Label(50f, 96f, "spring brakes between 20 and 45 psi", Ink.EMERGENCY, 3.4f, Align.CENTER))
        },
    )

    val fifthWheel = Diagram(
        id = "fifth-wheel",
        title = "The fifth wheel and the kingpin",
        caption = "A cut through the coupling, from the side. Two things fail you here: any " +
            "gap between the apron and the skid plate, and jaws closed around the head of " +
            "the kingpin instead of its shank. Check the release arm is seated and the " +
            "safety latch is over it.",
        factIds = listOf("6.4.1.11", "6.4.1.12", "11.6.1.4", "11.6.1.5", "11.6.1.6", "11.6.1.7"),
        shapes = buildList {
            // The trailer, and the apron it sits on.
            add(Box(18f, 4f, 64f, 14f, Ink.FAINT, round = 1f))
            add(Label(50f, 12.5f, "trailer", Ink.FAINT, 4.2f, Align.CENTER))
            add(Box(22f, 18f, 56f, 5f, Ink.LINE, fill = true))

            // The line where they meet. There must be nothing in it.
            add(Line(22f, 23f, 78f, 23f, Ink.MARK, weight = 2.4f))

            // The fifth wheel skid plate, with the jaws inside it.
            add(Box(22f, 23f, 56f, 13f, Ink.LINE))
            add(Box(31f, 23.6f, 16f, 5f, Ink.LINE, fill = true))
            add(Box(53f, 23.6f, 16f, 5f, Ink.LINE, fill = true))

            // The kingpin: a narrow shank with a wider head below it. The jaws
            // close on the shank, which is the whole point of the drawing.
            add(Box(47f, 18f, 6f, 11f, Ink.MARK, fill = true))
            add(Box(43f, 29f, 14f, 5f, Ink.MARK, fill = true))

            // Callouts, each on its own side and clear of the drawing.
            add(Label(2f, 44f, "NO GAP here", Ink.MARK, 4.2f, bold = true))
            add(Arrow(22f, 42.5f, 34f, 24.5f, Ink.MARK))

            add(Label(98f, 44f, "jaws on the SHANK", Ink.LINE, 4f, Align.RIGHT, bold = true))
            add(Arrow(78f, 42.5f, 62f, 29f, Ink.LINE))

            add(Label(98f, 54f, "not on the head", Ink.EMERGENCY, 4f, Align.RIGHT, bold = true))
            add(Arrow(74f, 52.5f, 56f, 34.5f, Ink.EMERGENCY))

            add(Label(2f, 54f, "apron, flat on the plate", Ink.FAINT, 3.4f))
            add(Arrow(20f, 52.5f, 24f, 22f, Ink.FAINT))

            // Below: the frame and the landing gear, and the clearance to check.
            add(Box(26f, 66f, 48f, 8f, Ink.FAINT, round = 1f))
            add(Label(50f, 71.5f, "tractor frame", Ink.FAINT, 4f, Align.CENTER))
            add(Box(80f, 66f, 6f, 16f, Ink.FAINT))
            add(Label(98f, 62f, "landing gear, fully up", Ink.FAINT, 3.4f, Align.RIGHT))
            add(Line(20f, 82f, 90f, 82f, Ink.FAINT))

            add(Label(50f, 91f, "the frame must clear the landing gear in a turn,", Ink.LINE, 3.6f, Align.CENTER))
            add(Label(50f, 96f, "and the tires must clear the trailer nose", Ink.LINE, 3.6f, Align.CENTER))
        },
    )

    val gladHands = Diagram(
        id = "glad-hands",
        title = "Glad hands, and what crossing them does",
        caption = "Blue is service, red is emergency. Press the seals together at 90 degrees " +
            "and turn to lock. Cross them and supply air goes to the service line, so the " +
            "trailer spring brakes never release.",
        factIds = listOf("6.2.5.1", "6.2.5.3", "6.2.5.4", "6.2.5.5", "6.2.4.6"),
        shapes = buildList {
            add(Label(50f, 6f, "RIGHT", Ink.MARK, 4.6f, Align.CENTER, bold = true))

            // Correct: blue to blue, red to red.
            add(Box(10f, 12f, 26f, 8f, Ink.SERVICE, round = 2f))
            add(Label(23f, 17.5f, "service", Ink.SERVICE, 4f, Align.CENTER))
            add(Line(36f, 16f, 64f, 16f, Ink.SERVICE, weight = 2f))
            add(Box(64f, 12f, 26f, 8f, Ink.SERVICE, round = 2f))
            add(Label(77f, 17.5f, "service", Ink.SERVICE, 4f, Align.CENTER))

            add(Box(10f, 24f, 26f, 8f, Ink.EMERGENCY, round = 2f))
            add(Label(23f, 29.5f, "emergency", Ink.EMERGENCY, 4f, Align.CENTER))
            add(Line(36f, 28f, 64f, 28f, Ink.EMERGENCY, weight = 2f))
            add(Box(64f, 24f, 26f, 8f, Ink.EMERGENCY, round = 2f))
            add(Label(77f, 29.5f, "emergency", Ink.EMERGENCY, 4f, Align.CENTER))

            add(Label(10f, 39f, "tractor", Ink.FAINT, 3.6f))
            add(Label(90f, 39f, "trailer", Ink.FAINT, 3.6f, Align.RIGHT))
            add(Label(50f, 39f, "air flows, brakes release", Ink.FAINT, 3.4f, Align.CENTER))

            // Crossed.
            add(Line(6f, 46f, 94f, 46f, Ink.FAINT, dashed = true))
            add(Label(50f, 54f, "CROSSED", Ink.EMERGENCY, 4.6f, Align.CENTER, bold = true))

            add(Box(10f, 60f, 26f, 8f, Ink.SERVICE, round = 2f))
            add(Label(23f, 65.5f, "service", Ink.SERVICE, 4f, Align.CENTER))
            add(Box(10f, 72f, 26f, 8f, Ink.EMERGENCY, round = 2f))
            add(Label(23f, 77.5f, "emergency", Ink.EMERGENCY, 4f, Align.CENTER))
            add(Box(64f, 60f, 26f, 8f, Ink.SERVICE, round = 2f))
            add(Label(77f, 65.5f, "service", Ink.SERVICE, 4f, Align.CENTER))
            add(Box(64f, 72f, 26f, 8f, Ink.EMERGENCY, round = 2f))
            add(Label(77f, 77.5f, "emergency", Ink.EMERGENCY, 4f, Align.CENTER))

            // The crossing itself.
            add(Line(36f, 64f, 64f, 76f, Ink.SERVICE, weight = 2f))
            add(Line(36f, 76f, 64f, 64f, Ink.EMERGENCY, weight = 2f))

            add(Label(50f, 88f, "supply air reaches the service line, not the tanks", Ink.EMERGENCY, 3.6f, Align.CENTER))
            add(Label(50f, 94f, "the trailer spring brakes stay on: check the connections", Ink.LINE, 3.6f, Align.CENTER))
        },
    )

    val walkAround = Diagram(
        id = "walk-around",
        title = "The walk-around, in order",
        caption = "Step 5 of the seven-step inspection follows one route, every time: left " +
            "front, front, right side, right rear, rear, left side. The vehicle is drawn " +
            "nose up, so its left is your left.",
        factIds = listOf("2.1.5.8"),
        shapes = buildList {
            // Nose up, so the vehicle's left side is on the left of the screen
            // and the labels need no translating.
            add(Box(35f, 8f, 30f, 20f, Ink.FAINT, round = 2f))
            add(Label(50f, 20f, "tractor", Ink.FAINT, 4f, Align.CENTER))
            add(Box(33f, 30f, 34f, 48f, Ink.FAINT, round = 2f))
            add(Label(50f, 56f, "trailer", Ink.FAINT, 4f, Align.CENTER))

            // Wheels, so it reads as a vehicle from above.
            listOf(11f, 22f).forEach { y ->
                add(Box(31f, y, 4f, 6f, Ink.FAINT, fill = true))
                add(Box(65f, y, 4f, 6f, Ink.FAINT, fill = true))
            }
            listOf(62f, 70f).forEach { y ->
                add(Box(29f, y, 4f, 6f, Ink.FAINT, fill = true))
                add(Box(67f, y, 4f, 6f, Ink.FAINT, fill = true))
            }

            // The route, running clockwise round the outside.
            add(Arrow(17f, 22f, 40f, 6f, Ink.MARK))
            add(Arrow(60f, 6f, 83f, 22f, Ink.MARK))
            add(Arrow(85f, 30f, 85f, 62f, Ink.MARK))
            add(Arrow(83f, 70f, 60f, 84f, Ink.MARK))
            add(Arrow(40f, 84f, 17f, 66f, Ink.MARK))
            add(Arrow(15f, 58f, 15f, 30f, Ink.MARK, weight = 0.6f))

            add(Label(2f, 20f, "1 left front", Ink.MARK, 3.8f, bold = true))
            add(Label(50f, 4f, "2 front", Ink.MARK, 3.8f, Align.CENTER, bold = true))
            add(Label(98f, 20f, "3 right side", Ink.MARK, 3.8f, Align.RIGHT, bold = true))
            add(Label(98f, 70f, "4 right rear", Ink.MARK, 3.8f, Align.RIGHT, bold = true))
            add(Label(50f, 92f, "5 rear", Ink.MARK, 3.8f, Align.CENTER, bold = true))
            add(Label(2f, 70f, "6 left side", Ink.MARK, 3.8f, bold = true))

            add(Label(50f, 98f, "same route, every time", Ink.FAINT, 3.4f, Align.CENTER))
        },
    )

    val all: List<Diagram> = listOf(airBrakeSystem, fifthWheel, gladHands, walkAround)

    fun of(id: String): Diagram? = all.firstOrNull { it.id == id }

    /** The diagrams that draw a fact from this subsection. */
    fun forSubsection(subsection: Subsection): List<Diagram> =
        all.filter { diagram ->
            diagram.factIds.any { factId -> subsection.facts.any { it.id == factId } }
        }
}
