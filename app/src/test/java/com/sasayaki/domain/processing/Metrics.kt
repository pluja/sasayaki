package com.sasayaki.domain.processing

/**
 * Continuous measures of model output.
 *
 * The differential suite compares these between settings rather than against fixed
 * thresholds, because "is HARD shorter than LIGHT" is a real question about the control
 * while "is HARD under 50%" only measures where somebody drew a line.
 */
object Metrics {

    fun wordCount(text: String): Double =
        text.trim().split(Regex("\\s+")).count { it.isNotBlank() }.toDouble()

    /** Punctuation marks per word, so the figure does not simply track length. */
    fun punctuationDensity(text: String): Double {
        val words = wordCount(text)
        if (words == 0.0) return 0.0
        val marks = text.count { it in ".,;:!?—–-()\"'…" }
        return marks / words
    }

    fun uppercaseRatio(text: String): Double {
        val letters = text.count(Char::isLetter)
        if (letters == 0) return 0.0
        return text.count(Char::isUpperCase).toDouble() / letters
    }

    /**
     * Counts emoji by code point, not by char.
     *
     * Most emoji live above the BMP, so in a UTF-16 String they are surrogate pairs and a
     * per-Char UnicodeBlock lookup only ever sees HIGH_SURROGATES. An earlier version did
     * exactly that and reported zero emoji for output that visibly contained them.
     */
    fun emojiCount(text: String): Double = text.codePoints().filter { codePoint ->
        when (Character.UnicodeBlock.of(codePoint)) {
            Character.UnicodeBlock.EMOTICONS,
            Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS,
            Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS,
            Character.UnicodeBlock.TRANSPORT_AND_MAP_SYMBOLS,
            Character.UnicodeBlock.SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS,
            Character.UnicodeBlock.DINGBATS -> true
            else -> false
        }
    }.count().toDouble()

    /**
     * How far the output moved from the raw transcript, normalised to 0..1. Used to tell
     * the rewrite tiers apart: a stronger tier should move the text further.
     */
    fun editDistanceFromRaw(raw: String, output: String): Double {
        val a = raw.lowercase().trim()
        val b = output.lowercase().trim()
        if (a.isEmpty() && b.isEmpty()) return 0.0
        return levenshtein(a, b).toDouble() / maxOf(a.length, b.length)
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
    }
}
