package `as`.today.missyou.data.search

import `as`.today.missyou.core.text.TextMetrics
import `as`.today.missyou.crypto.Digests

/**
 * Compact inverted index over the journal.
 *
 * ## Why not just `LIKE '%word%'`?
 *
 * Journal bodies are sealed at rest, so SQL cannot search them. The alternative —
 * decrypting every document on every keystroke — would be unusable on a low-end
 * device. Instead each row carries a small, sealed list of SHA-256 *fingerprints*
 * of its tokens, and the app builds this index once, in memory, from those
 * fingerprints.
 *
 * ## Memory
 *
 * The index is two parallel [IntArray]s. A journal with ten years of daily writing
 * is roughly 3 650 rows; with ~100 tokens each that is ~365 000 pairs, or about
 * 2.9 MB, and no plaintext is retained. Entries are not decrypted until a query
 * actually matches them.
 *
 * ## Collisions
 *
 * A query fingerprint is truncated to 32 bits, so two different tokens can
 * occasionally collide and produce a false candidate. That is harmless: candidates
 * are always verified against the real text before being reported, which is why
 * truncation is safe here.
 */
class SearchIndex private constructor(
    private val keys: IntArray,
    private val values: IntArray,
    val entryIds: List<String>,
) {

    val size: Int get() = entryIds.size

    val isEmpty: Boolean get() = keys.isEmpty()

    /**
     * Entry ordinals that contain at least one of [queryTokens].
     *
     * Returned in ascending order with duplicates removed.
     */
    fun candidates(queryTokens: List<String>): List<Int> {
        if (keys.isEmpty() || queryTokens.isEmpty()) return emptyList()
        val matches = HashSet<Int>()
        for (token in queryTokens) {
            val fingerprint = tokenFingerprint(token)
            var low = lowerBound(fingerprint)
            while (low < keys.size && keys[low] == fingerprint) {
                matches.add(values[low])
                low++
            }
        }
        return matches.sorted()
    }

    fun entryIdAt(ordinal: Int): String? = entryIds.getOrNull(ordinal)

    private fun lowerBound(target: Int): Int {
        var low = 0
        var high = keys.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (keys[mid] < target) low = mid + 1 else high = mid
        }
        return low
    }

    companion object {
        val Empty = SearchIndex(IntArray(0), IntArray(0), emptyList())

        /**
         * Builds an index from per-entry token lists.
         *
         * @param entryTokens token lists aligned with [entryIds].
         */
        fun build(entryIds: List<String>, entryTokens: List<List<String>>): SearchIndex {
            if (entryIds.isEmpty()) return Empty
            val pairCount = entryTokens.sumOf { it.size }
            if (pairCount == 0) return SearchIndex(IntArray(0), IntArray(0), entryIds)

            // Pack (fingerprint, ordinal) into a single Long so the sort can use the
            // platform's primitive dual-pivot quicksort. Boxing ~365k pairs to use a
            // comparator would cost far more memory than the index itself.
            val packed = LongArray(pairCount)
            var cursor = 0
            for ((ordinal, tokens) in entryTokens.withIndex()) {
                for (token in tokens) {
                    val high = (tokenFingerprint(token).toLong() and 0xFFFFFFFFL) shl 32
                    packed[cursor] = high or (ordinal.toLong() and 0xFFFFFFFFL)
                    cursor++
                }
            }
            java.util.Arrays.sort(packed)

            val keys = IntArray(pairCount)
            val values = IntArray(pairCount)
            for (i in 0 until pairCount) {
                keys[i] = (packed[i] ushr 32).toInt()
                values[i] = (packed[i] and 0xFFFFFFFFL).toInt()
            }
            return SearchIndex(keys, values, entryIds)
        }

        /** 32-bit fingerprint of a search token. */
        fun tokenFingerprint(token: String): Int {
            val hex = Digests.sha256Hex(token)
            var value = 0L
            for (i in 0 until 8) {
                val digit = Character.digit(hex[i], 16)
                value = (value shl 4) or digit.toLong()
            }
            return value.toInt()
        }
    }
}

/** Token extraction shared by the indexer, the searcher and export. */
object Tokens {

    fun fromText(text: String): List<String> = TextMetrics.searchTokens(text)

    fun serialize(tokens: List<String>): String = tokens.joinToString(SEPARATOR)

    fun deserialize(blob: String): List<String> =
        if (blob.isEmpty()) emptyList() else blob.split(SEPARATOR).filter { it.isNotEmpty() }

    const val SEPARATOR = "\n"
}
