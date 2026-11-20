package com.cdlstudy.domain

/**
 * The manual sections Utah requires for a Class A CDL with air brakes and no
 * endorsements. The app shows these sections and no others.
 *
 * Three knowledge tests and three skills tests:
 *
 * - General Knowledge, which every applicant takes: sections 1, 2 and 3.
 * - Combination Vehicles, which only a Class A applicant takes: section 6.
 * - Air Brakes, which you need to drive a vehicle with air brakes: section 5.
 * - The skills test, in three parts: sections 11, 12 and 13.
 *
 * Sections 4, 7, 8, 9 and 10 are endorsement material. They are left out until
 * you want an endorsement.
 *
 * CHECK THIS AGAINST THE STATE. The source is the 2025 Utah Commercial Driver
 * License Handbook and the Utah Driver License Division testing page, read in
 * November 2026. Requirements change. The Division is the authority, not this app.
 */
object StudyPlan {

    data class RequiredSection(
        val number: Int,
        val title: String,
        val track: Track,
        val why: String,
    )

    data class ExcludedSection(
        val number: Int,
        val title: String,
        val why: String,
    )

    val required: List<RequiredSection> = listOf(
        RequiredSection(
            1, "Introduction", Track.GENERAL_KNOWLEDGE,
            "General Knowledge test. License classes, medical papers, and what " +
                "disqualifies a driver.",
        ),
        RequiredSection(
            2, "Driving Safely", Track.GENERAL_KNOWLEDGE,
            "General Knowledge test. The largest part of it, at 45 pages.",
        ),
        RequiredSection(
            3, "Transporting Cargo Safely", Track.GENERAL_KNOWLEDGE,
            "General Knowledge test. Weight, balance and securing a load.",
        ),
        RequiredSection(
            5, "Air Brakes", Track.AIR_BRAKES,
            "The Air Brakes knowledge test. Without it, your license carries a " +
                "restriction against air brakes.",
        ),
        RequiredSection(
            6, "Combination Vehicles", Track.COMBINATION,
            "The Combination Vehicles knowledge test. Only a Class A applicant takes it.",
        ),
        RequiredSection(
            11, "Vehicle Inspection", Track.INSPECTION,
            "Part one of the skills test. You say and show the inspection to the examiner.",
        ),
        RequiredSection(
            12, "Basic Vehicle Control Skills Test", Track.BASIC_CONTROL,
            "Part two of the skills test. The backing and maneuvering exercises.",
        ),
        RequiredSection(
            13, "On-Road Driving", Track.ON_ROAD,
            "Part three of the skills test. The drive with the examiner.",
        ),
    )

    val excluded: List<ExcludedSection> = listOf(
        ExcludedSection(4, "Transporting Passengers Safely", "Passenger (P) endorsement only."),
        ExcludedSection(7, "Doubles and Triples", "Doubles and triples (T) endorsement only."),
        ExcludedSection(8, "Tank Vehicles", "Tank vehicle (N) endorsement only."),
        ExcludedSection(9, "Hazardous Materials", "Hazardous materials (H) endorsement only."),
        ExcludedSection(10, "School Bus", "School bus (S) endorsement only."),
    )

    val requiredNumbers: Set<Int> = required.map { it.number }.toSet()

    fun isRequired(section: Int): Boolean = section in requiredNumbers

    fun requiredSection(number: Int): RequiredSection? = required.firstOrNull { it.number == number }

    /** The knowledge and skills tests, in the order the plan studies them. */
    fun tracksInOrder(): List<Track> = Track.entries.toList()
}
