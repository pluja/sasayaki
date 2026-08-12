package com.sasayaki.domain.processing

import com.sasayaki.domain.model.AppContext
import com.sasayaki.domain.model.OutputStyle
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.model.RewriteMode
import com.sasayaki.domain.model.SummarizeMode

/**
 * Synthetic raw-ASR transcripts used by [PostProcessingBenchmark].
 *
 * They imitate what a Whisper-style backend actually returns: no punctuation, filler
 * words, repeated words, and mid-sentence self-corrections. Checks are deliberately
 * objective — "did the abandoned number survive", "is it still in Catalan" — rather than
 * a subjective quality score, so a model either passes or does not.
 */
data class Check(val name: String, val passes: (String) -> Boolean)

data class BenchmarkCase(
    val id: String,
    val language: String,
    val intent: String,
    val rawText: String,
    val profile: Profile,
    val appContext: AppContext? = null,
    val checks: List<Check>
)

private fun baseProfile(
    outputStyle: OutputStyle = OutputStyle.STANDARD,
    rewriteMode: RewriteMode = RewriteMode.FIX,
    summarizeMode: SummarizeMode = SummarizeMode.NONE,
    emojiAllowed: Boolean = false,
    language: String? = null
) = Profile(
    name = "Bench",
    llmEnabled = true,
    outputStyle = outputStyle,
    rewriteMode = rewriteMode,
    summarizeMode = summarizeMode,
    emojiAllowed = emojiAllowed,
    language = language
)

private fun word(text: String, term: String): Boolean =
    Regex("(?iu)(?<![\\p{L}\\p{N}])${Regex.escape(term)}(?![\\p{L}\\p{N}])").containsMatchIn(text)

fun mustContain(term: String) = Check("contains '$term'") { word(it, term) }
fun mustContainAny(vararg terms: String) =
    Check("contains any of ${terms.toList()}") { out -> terms.any { word(out, it) } }

fun mustNotContain(term: String) = Check("drops '$term'") { !word(it, term) }

val noEmoji = Check("no emoji added") { out ->
    out.none { ch ->
        val block = Character.UnicodeBlock.of(ch)
        block == Character.UnicodeBlock.EMOTICONS ||
            block == Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS ||
            block == Character.UnicodeBlock.TRANSPORT_AND_MAP_SYMBOLS ||
            block == Character.UnicodeBlock.SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS
    }
}

val allLowercase = Check("all lowercase") { out -> out.none(Char::isUpperCase) }

fun shorterThan(ratio: Double, sourceWords: Int) =
    Check("condensed below ${(ratio * 100).toInt()}% of $sourceWords words") { out ->
        out.trim().split(Regex("\\s+")).count { it.isNotBlank() } < sourceWords * ratio
    }

private const val RAMBLE_EN =
    "so um basically what i wanted to say is that the deployment went out yesterday evening " +
        "and uh there were a couple of issues with the database migration but we rolled it back " +
        "and then we tried again this morning and it worked fine so um yeah the the main thing " +
        "is that everything is stable now and i think we should probably schedule a retro " +
        "sometime next week to talk about what went wrong with the migration"

val BENCHMARK_CASES: List<BenchmarkCase> = listOf(
    BenchmarkCase(
        id = "en-self-correction",
        language = "English",
        intent = "Self-correction and filler removal",
        rawText = "so um we can meet at six wait no actually seven at the the restaurant " +
            "near the station uh the one we went to last time",
        profile = baseProfile(),
        appContext = AppContext(label = "Signal", packageName = "org.thoughtcrime.securesms"),
        checks = listOf(
            mustContainAny("seven", "7"),
            mustNotContain("six"),
            mustNotContain("um"),
            mustNotContain("uh"),
            Check("de-duplicates 'the the'") { !it.contains("the the", ignoreCase = true) }
        )
    ),
    BenchmarkCase(
        id = "en-role-adherence",
        language = "English",
        intent = "Must not answer a dictated question",
        rawText = "hey quick question what is the capital of france i always forget",
        profile = baseProfile(),
        checks = listOf(
            Check("does not answer with 'Paris'") { !word(it, "Paris") },
            mustContainAny("capital"),
            shorterThan(2.0, 13)
        )
    ),
    BenchmarkCase(
        id = "en-minimal-style",
        language = "English",
        intent = "MINIMAL style must force lowercase",
        rawText = "hey are you coming to the thing tonight i think it starts at eight",
        profile = baseProfile(outputStyle = OutputStyle.MINIMAL),
        checks = listOf(allLowercase, mustContainAny("eight", "8"))
    ),
    BenchmarkCase(
        id = "en-hard-summary-no-rewrite",
        language = "English",
        intent = "HARD condense with NONE rewrite (the resolved contradiction)",
        rawText = RAMBLE_EN,
        profile = baseProfile(rewriteMode = RewriteMode.NONE, summarizeMode = SummarizeMode.HARD),
        checks = listOf(
            shorterThan(0.5, RAMBLE_EN.split(Regex("\\s+")).size),
            mustContainAny("migration", "deployment", "database")
        )
    ),
    BenchmarkCase(
        id = "en-no-emoji",
        language = "English",
        intent = "Emoji disabled must stay disabled",
        rawText = "congrats on the new job that is amazing news so happy for you",
        profile = baseProfile(emojiAllowed = false),
        appContext = AppContext(label = "WhatsApp", packageName = "com.whatsapp"),
        checks = listOf(noEmoji, mustContainAny("congrats", "congratulations"))
    ),
    BenchmarkCase(
        id = "es-formal-email",
        language = "Spanish",
        intent = "Stays Spanish, drops fillers, keeps correction",
        rawText = "hola eh buenos días quería preguntarte si podemos mover la reunión del " +
            "martes al miércoles porque me ha surgido un imprevisto este gracias",
        profile = baseProfile(language = "Spanish"),
        appContext = AppContext(label = "Gmail", packageName = "com.google.android.gm"),
        checks = listOf(
            mustContain("miércoles"),
            mustContainAny("reunión", "reunion"),
            Check("still Spanish, not translated") { out ->
                !word(out, "Wednesday") && !word(out, "meeting")
            }
        )
    ),
    BenchmarkCase(
        id = "es-spoken-punctuation",
        language = "Spanish",
        intent = "Spoken punctuation commands become real punctuation",
        rawText = "estimado cliente coma le escribo para confirmar su pedido punto " +
            "muchas gracias por su confianza punto",
        profile = baseProfile(language = "Spanish"),
        checks = listOf(
            Check("emits a comma") { it.contains(",") },
            Check("emits a period") { it.contains(".") },
            mustNotContain("coma"),
            mustNotContain("punto")
        )
    ),
    BenchmarkCase(
        id = "fr-self-correction",
        language = "French",
        intent = "Stays French, applies the correction",
        rawText = "alors euh il faut que je rappelle le docteur demain matin avant onze " +
            "heures euh non plutôt avant dix heures",
        profile = baseProfile(language = "French"),
        checks = listOf(
            mustContainAny("dix", "10"),
            mustNotContain("onze"),
            mustNotContain("euh"),
            Check("still French, not translated") { !word(it, "doctor") && !word(it, "tomorrow") }
        )
    ),
    BenchmarkCase(
        id = "ca-self-correction",
        language = "Catalan",
        intent = "Stays Catalan rather than drifting to Spanish",
        rawText = "doncs mira eh he pensat que podríem quedar dissabte a la tarda no espera " +
            "diumenge al matí que dissabte tinc feina",
        profile = baseProfile(language = "Catalan"),
        checks = listOf(
            mustContain("diumenge"),
            Check("not translated to Spanish") { !word(it, "domingo") && !word(it, "sábado") },
            Check("not translated to English") { !word(it, "Sunday") && !word(it, "Saturday") }
        )
    ),
    BenchmarkCase(
        id = "it-numbers",
        language = "Italian",
        intent = "Stays Italian, applies numeric self-correction",
        rawText = "allora devo comprare tre chili di pomodori e due no scusa tre litri di " +
            "latte per domani mattina",
        profile = baseProfile(language = "Italian"),
        checks = listOf(
            mustContainAny("tre", "3"),
            mustNotContain("scusa"),
            Check("still Italian, not translated") { !word(it, "milk") && !word(it, "tomorrow") }
        )
    )
)
