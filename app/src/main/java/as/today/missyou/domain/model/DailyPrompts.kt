package `as`.today.missyou.domain.model

/**
 * The built-in writing prompts (§25).
 *
 * These ship with the app and are selected locally from a stable index derived from
 * the journal date, so a given day always shows the same prompt, the selection needs
 * no storage, and nothing is ever fetched.
 */
object DailyPrompts {

    val PROMPTS: List<String> = listOf(
        "What made today meaningful?",
        "What challenged you today?",
        "What are you grateful for?",
        "What did you learn today?",
        "Who mattered today, and why?",
        "What would you tell yourself this morning?",
        "What surprised you today?",
        "What did you get right today?",
        "What are you carrying into tomorrow?",
        "When did you last feel fully present?",
        "What small thing went better than expected?",
        "What did you choose today, and what did it cost?",
        "What would make tomorrow feel lighter?",
        "What do you want to remember about today?",
        "What is one thing you can let go of?",
        "What conversation is still with you?",
        "What did you notice that you usually miss?",
        "What are you building, slowly or quickly?",
        "What felt hardest, and how did you get through it?",
        "What would you repeat about today if you could?",
        "What did today teach you about yourself?",
        "What are you hoping for tomorrow?",
        "Where did you find calm today?",
        "What did someone do that you should acknowledge?",
        "What is a true thing about today that only you know?",
        "What made you laugh?",
        "What would you change about today, knowing what you know now?",
        "What are you proud of, however small?",
        "What did you need today that you did not ask for?",
        "If today had a headline, what would it be?",
    )

    /**
     * The prompt for [epochDay].
     *
     * `floorMod` keeps the selection stable and non-negative for dates before 1970.
     */
    fun forEpochDay(epochDay: Long): String = PROMPTS[floorMod(epochDay, PROMPTS.size.toLong()).toInt()]

    private fun floorMod(value: Long, modulus: Long): Long {
        val result = value % modulus
        return if (result < 0) result + modulus else result
    }
}
