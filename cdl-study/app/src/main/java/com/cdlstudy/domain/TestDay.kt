package com.cdlstudy.domain

/**
 * What Utah asks of you, away from the manual.
 *
 * Every item here was read off the Utah Driver License Division's own pages on
 * the date in [CHECKED_ON], and each one carries the page it came from. None of
 * it is from memory.
 *
 * These things change, and a number in an app is not a number at a counter. So
 * every screen that shows this says to check the page before the trip, and the
 * date is shown so you can see how old it is.
 */
object TestDay {

    /** The date these items were read off dld.utah.gov. */
    const val CHECKED_ON = "24 November 2026"

    /** Where to check them again. */
    const val SITE = "dld.utah.gov"

    /**
     * A notice that was on the CDL pages when they were read. It is not a study
     * item, but it would be wrong to leave it out.
     */
    const val NOTICE =
        "The CDL pages carried this on ${CHECKED_ON}: as of 20 January 2026, and in " +
            "conjunction with FMCSA's annual review of Utah's CDL Program, Utah is pausing " +
            "the issuance of non-domiciled CDLs until further notice."

    data class Item(
        val id: String,
        val text: String,
        /** The page this came from, as a path on dld.utah.gov. */
        val source: String,
        /** True for an item you tick off, false for one that is just a fact. */
        val tickable: Boolean = true,
    )

    data class Group(val title: String, val note: String, val items: List<Item>)

    val groups: List<Group> = listOf(
        Group(
            title = "Documents to take",
            note = "Four documents from three categories. Originals or certified copies with a " +
                "raised seal, stamp or signature, and unexpired. No photocopies, no faxes, no " +
                "laminated birth certificate, no hospital certificate. Approval can take more " +
                "than one day and need a second visit.",
            items = listOf(
                Item(
                    "doc-identity",
                    "One for identity and date of birth: a valid unexpired US passport or " +
                        "passport card, a certified copy of your birth certificate, a Consular " +
                        "Report of Birth Abroad (FS-240, DS-1350 or FS-545), a permanent " +
                        "resident card (I-551), a certificate of naturalization (N-550 or " +
                        "N-570), or a certificate of citizenship (N-560 or N-561).",
                    "/required-documents-for-us-citizen/",
                ),
                Item(
                    "doc-ssn",
                    "One for your Social Security number: the signed card itself, or a W-2, an " +
                        "SSA-1099, a non-SSA-1099 or a pay stub showing your name and full " +
                        "number. A card stub is not accepted.",
                    "/required-documents-for-us-citizen/",
                ),
                Item(
                    "doc-address",
                    "Two for your Utah address, each showing your name and address and dated " +
                        "within 90 days: a bank statement, court documents, a current mortgage " +
                        "or rental contract, a major credit card bill, a property tax notice, a " +
                        "school transcript, a utility bill or a vehicle title.",
                    "/required-documents-for-us-citizen/",
                ),
            ),
        ),
        Group(
            title = "Before the knowledge tests",
            note = "Closed book, multiple choice, 80% to pass. English only: translation " +
                "material is strictly prohibited.",
            items = listOf(
                Item(
                    "know-general",
                    "General knowledge, 50 questions. Every Class A, B and C applicant takes it. " +
                        "Pass it and you are issued a commercial learner permit for 180 days.",
                    "/cdl-written-test/",
                ),
                Item(
                    "know-combination",
                    "Combination, 20 questions. Class A only.",
                    "/cdl-written-test/",
                ),
                Item(
                    "know-airbrakes",
                    "Air brakes, 25 questions. Optional, but without it, and without doing the " +
                        "skills test in an air-brake vehicle, an L restriction goes on your " +
                        "license and you may not drive a CMV with air brakes.",
                    "/cdl-written-test/",
                ),
                Item(
                    "know-fee",
                    "The $52.00 license application fee covers the three core tests. Each test " +
                        "may be taken twice before a $26.00 second test fee. Endorsement tests " +
                        "are $9.00 for two attempts.",
                    "/cdl-written-test/",
                    tickable = false,
                ),
            ),
        ),
        Group(
            title = "Before the skills test",
            note = "Three parts: the pre-trip vehicle inspection, basic control skills, and the " +
                "driving skills test on the road.",
            items = listOf(
                Item(
                    "skills-eldt",
                    "Complete Entry-Level Driver Training before you can schedule the skills " +
                        "test. The DLD links the federal Training Provider Registry at " +
                        "tpr.fmcsa.dot.gov, so your provider has to be on it.",
                    "/cdl-driving-test/",
                ),
                Item(
                    "skills-14-days",
                    "Hold the commercial learner permit for 14 days before you are eligible.",
                    "/cdl-driving-test/",
                ),
                Item(
                    "skills-vehicle",
                    "Bring your own registered and road-ready vehicle. The DLD does not provide " +
                        "one.",
                    "/cdl-driving-test/",
                ),
                Item(
                    "skills-office",
                    "Check the office takes CDLs. On the date above, Farmington, Logan and " +
                        "Tooele did not process them.",
                    "/cdl-driving-test/",
                ),
                Item(
                    "skills-appointment",
                    "Book the appointment. The DLD schedules CDL skills tests and CDL license " +
                        "appointments separately.",
                    "/cdl-driving-test/",
                ),
            ),
        ),
        Group(
            title = "How the skills test is scored",
            note = "Not a percentage. The skills test counts error points, which is why the " +
                "three skills exams in this app are a test of your knowledge of the rules and " +
                "not a model of the real scoring.",
            items = listOf(
                Item(
                    "score-basic",
                    "Basic control skills: you pass with 8 or fewer error points.",
                    "/cdl-driving-test/",
                    tickable = false,
                ),
                Item(
                    "score-road",
                    "Driving skills on the road: you pass with 30 or fewer error points.",
                    "/cdl-driving-test/",
                    tickable = false,
                ),
                Item(
                    "score-pretrip",
                    "The pre-trip is graded on the license class, whether the vehicle has air " +
                        "brakes, and which part of the vehicle you are asked to inspect.",
                    "/cdl-driving-test/",
                    tickable = false,
                ),
            ),
        ),
        Group(
            title = "The fees",
            note = "Fees are subject to change and are non-refundable. The DLD takes cash, " +
                "checks, money orders and credit cards.",
            items = listOf(
                Item("fee-written", "CDL with written knowledge test: $52.00", "/fees/", false),
                Item("fee-skills", "CDL with driving skills test: $78.00", "/fees/", false),
                Item("fee-endorsement", "CDL endorsement: $9.00", "/fees/", false),
                Item("fee-retake-written", "Retake a written knowledge test: $26.00", "/fees/", false),
                Item("fee-retake-skills", "Retake a driving skills test: $52.00", "/fees/", false),
                Item("fee-reschedule", "Rescheduling fee: $25.00", "/fees/", false),
                Item("fee-intrastate", "Intrastate medical: $25.00", "/fees/", false),
                Item(
                    "fee-third-party",
                    "A third-party tester sets its own price for the skills test, and the DLD " +
                        "does not publish it.",
                    "/cdl-driving-test/",
                    false,
                ),
            ),
        ),
        Group(
            title = "Not on the DLD pages read",
            note = "These are in the handbook but were not on the pages above, so do not take " +
                "the detail from this app. Ask the DLD.",
            items = listOf(
                Item(
                    "gap-medical",
                    "The Medical Examiner's Certificate and self-certification. The handbook " +
                        "names form MCSA-5876 and the four self-certification categories. The " +
                        "CDL pages read here did not cover it.",
                    "",
                    false,
                ),
                Item(
                    "gap-experience",
                    "Utah's one-year licensed-driver requirement before you may apply. It is in " +
                        "the handbook, not on the pages read here.",
                    "",
                    false,
                ),
                Item(
                    "gap-time-limit",
                    "Any time limit on a knowledge test. The pages read here gave none, so this " +
                        "app sets none.",
                    "",
                    false,
                ),
            ),
        ),
    )

    val allItems: List<Item> get() = groups.flatMap { it.items }

    val tickable: List<Item> get() = allItems.filter { it.tickable }

    fun done(ticked: Set<String>): Int = tickable.count { it.id in ticked }

    fun percent(ticked: Set<String>): Int {
        val total = tickable.size
        return if (total == 0) 0 else 100 * done(ticked) / total
    }
}
