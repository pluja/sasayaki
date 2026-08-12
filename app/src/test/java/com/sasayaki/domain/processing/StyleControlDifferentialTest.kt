package com.sasayaki.domain.processing

import com.sasayaki.domain.model.OutputStyle
import com.sasayaki.domain.model.Profile
import com.sasayaki.domain.model.RewriteMode
import com.sasayaki.domain.model.SummarizeMode
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Does each style control actually change the output?
 *
 * The behavioural suite checks correctness against fixed expectations. This one asks a
 * different question: if the user moves a control from LIGHT to HARD, does anything
 * measurably happen? A control could be a complete no-op and every correctness test would
 * still pass, so this compares settings against each other instead of against a threshold.
 *
 * A gap only counts when it exceeds the run-to-run spread within a single setting. Sampling
 * at temperature 0.3 wobbles, and without that guard the wobble reads as an effect.
 */
class StyleControlDifferentialTest {

    private val client = BenchmarkClient()

    private data class Variant(val label: String, val profile: Profile)

    /** [fromLeast] names variants in the order the metric is expected to increase. */
    private data class Ordering(val metric: String, val fromLeast: List<String>)

    private data class Probe(
        val control: String,
        val rawText: String,
        val variants: List<Variant>,
        val orderings: List<Ordering>
    )

    private val metrics: Map<String, (String, String) -> Double> = mapOf(
        "words" to { _, out -> Metrics.wordCount(out) },
        "punctuation/word" to { _, out -> Metrics.punctuationDensity(out) },
        "uppercase ratio" to { _, out -> Metrics.uppercaseRatio(out) },
        "emoji count" to { _, out -> Metrics.emojiCount(out) },
        "edit distance" to { raw, out -> Metrics.editDistanceFromRaw(raw, out) }
    )

    private fun profile(
        outputStyle: OutputStyle = OutputStyle.STANDARD,
        rewriteMode: RewriteMode = RewriteMode.FIX,
        summarizeMode: SummarizeMode = SummarizeMode.NONE,
        emojiAllowed: Boolean = false
    ) = Profile(
        name = "Probe",
        llmEnabled = true,
        outputStyle = outputStyle,
        rewriteMode = rewriteMode,
        summarizeMode = summarizeMode,
        emojiAllowed = emojiAllowed
    )

    private val rambleEn =
        "so um basically what i wanted to say is that the deployment went out yesterday " +
            "evening and uh there were a couple of issues with the database migration but we " +
            "rolled it back and then we tried again this morning and it worked fine so um yeah " +
            "the the main thing is that everything is stable now and i think we should probably " +
            "schedule a retro sometime next week to talk about what went wrong with the migration"

    private val multiSentenceEn =
        "i finished the report last night it still needs a review from you before we send it " +
            "can you take a look this afternoon otherwise it will slip to monday"

    private val informalEn =
        "yeah so me and him was gonna go over the numbers but it never happened cause the " +
            "client kept changing what they wanted and we run out of time basically"

    private val celebratoryEn =
        "congrats on the new job that is amazing news so happy for you we should celebrate soon"

    private val probes = listOf(
        Probe(
            control = "SummarizeMode (condense)",
            rawText = rambleEn,
            variants = listOf(
                Variant("HARD", profile(summarizeMode = SummarizeMode.HARD)),
                Variant("LIGHT", profile(summarizeMode = SummarizeMode.LIGHT)),
                Variant("NONE", profile(summarizeMode = SummarizeMode.NONE))
            ),
            orderings = listOf(Ordering("words", listOf("HARD", "LIGHT", "NONE")))
        ),
        Probe(
            control = "OutputStyle (casing and punctuation)",
            rawText = multiSentenceEn,
            variants = listOf(
                Variant("MINIMAL", profile(outputStyle = OutputStyle.MINIMAL)),
                Variant("RELAXED", profile(outputStyle = OutputStyle.RELAXED)),
                Variant("STANDARD", profile(outputStyle = OutputStyle.STANDARD))
            ),
            orderings = listOf(
                Ordering("punctuation/word", listOf("MINIMAL", "RELAXED", "STANDARD")),
                Ordering("uppercase ratio", listOf("MINIMAL", "STANDARD"))
            )
        ),
        Probe(
            control = "RewriteMode",
            rawText = informalEn,
            variants = listOf(
                Variant("NONE", profile(rewriteMode = RewriteMode.NONE)),
                Variant("FIX", profile(rewriteMode = RewriteMode.FIX)),
                Variant("POLISH", profile(rewriteMode = RewriteMode.POLISH))
            ),
            orderings = listOf(Ordering("edit distance", listOf("NONE", "FIX", "POLISH")))
        ),
        Probe(
            control = "emojiAllowed",
            rawText = celebratoryEn,
            variants = listOf(
                Variant("false", profile(emojiAllowed = false)),
                Variant("true", profile(emojiAllowed = true))
            ),
            orderings = listOf(Ordering("emoji count", listOf("false", "true")))
        )
    )

    private data class Sample(
        val label: String,
        val values: List<Double>,
        val outputs: List<String>
    ) {
        val median: Double get() = Metrics.median(values)
        val spread: Double get() = (values.maxOrNull() ?: 0.0) - (values.minOrNull() ?: 0.0)
    }

    private enum class Verdict { DIFFERENTIATED, FLAT, INVERTED }

    private fun judge(lower: Sample, higher: Sample): Verdict {
        val gap = higher.median - lower.median
        // The effect has to clear the wobble within either setting to count.
        val noise = maxOf(lower.spread, higher.spread, 1e-9)
        return when {
            gap > noise -> Verdict.DIFFERENTIATED
            -gap > noise -> Verdict.INVERTED
            else -> Verdict.FLAT
        }
    }

    @Test
    fun `style controls measurably change the output`() {
        assumeTrue(
            "Set OPENAI_ENDPOINT, OPENAI_API_KEY and a model list to run this",
            client.isConfigured
        )

        val sb = StringBuilder()
        fun line(text: String = "") {
            println(text)
            sb.appendLine(text)
        }

        line()
        line("# Style control differential eval")
        line()
        line("Each control is exercised on one fixed transcript, ${client.reps} repetitions per")
        line("setting. A gap counts only when it exceeds the spread within a setting.")
        line()

        var errorCount = 0
        var requestCount = 0
        val verdicts = mutableMapOf<Pair<String, String>, MutableList<String>>()

        client.models.forEach { model ->
            line("## $model")
            line()
            probes.forEach { probe ->
                val samplesByLabel = mutableMapOf<String, Sample>()
                probe.variants.forEach { variant ->
                    val prompt = SystemPromptBuilder.build(
                        profile = variant.profile,
                        appContext = null,
                        prompts = client.builtInPrompts
                    )
                    val completions = client.completeRepeatedly(model, prompt, probe.rawText)
                    requestCount += completions.size
                    errorCount += completions.count { it.error != null }
                    val outputs = completions.map(Completion::text)
                    samplesByLabel[variant.label] = Sample(variant.label, emptyList(), outputs)
                }

                line("### ${probe.control}")
                line()
                probe.orderings.forEach { ordering ->
                    val metric = metrics.getValue(ordering.metric)
                    val samples = ordering.fromLeast.map { label ->
                        val base = samplesByLabel.getValue(label)
                        base.copy(values = base.outputs.map { metric(probe.rawText, it) })
                    }
                    line("**${ordering.metric}** (expected to increase left to right)")
                    line()
                    line("| Setting | median | spread | samples |")
                    line("|---|---|---|---|")
                    samples.forEach {
                        line(
                            "| ${it.label} | ${"%.3f".format(it.median)} | " +
                                "${"%.3f".format(it.spread)} | " +
                                it.values.joinToString(", ") { v -> "%.2f".format(v) } + " |"
                        )
                    }
                    line()
                    samples.zipWithNext().forEach { (lower, higher) ->
                        val verdict = judge(lower, higher)
                        verdicts
                            .getOrPut(probe.control to "${lower.label} -> ${higher.label} (${ordering.metric})") { mutableListOf() }
                            .add("$model:$verdict")
                        line("- ${lower.label} -> ${higher.label}: **$verdict** " +
                            "(gap ${"%.3f".format(higher.median - lower.median)}, " +
                            "noise ${"%.3f".format(maxOf(lower.spread, higher.spread))})")
                    }
                    line()
                }
                line("<details><summary>outputs</summary>")
                line()
                probe.variants.forEach { v ->
                    samplesByLabel.getValue(v.label).outputs.forEachIndexed { i, out ->
                        line("- ${v.label} #${i + 1}: `$out`")
                    }
                }
                line()
                line("</details>")
                line()
            }
        }

        line("## Verdict summary")
        line()
        line("| Control | Transition | Per-model verdicts |")
        line("|---|---|---|")
        verdicts.forEach { (key, results) ->
            line("| ${key.first} | ${key.second} | ${results.joinToString(", ")} |")
        }

        val out = File("build/reports/benchmark/style-differential.md")
        out.parentFile?.mkdirs()
        out.writeText(sb.toString())
        println("\nReport written to ${out.absolutePath}")

        if (errorCount > 0) {
            throw AssertionError(
                "$errorCount of $requestCount requests failed; the differential run measured nothing."
            )
        }
    }
}
