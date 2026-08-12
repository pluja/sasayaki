package com.sasayaki.domain.processing

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * LLM-in-the-loop benchmark for the post-processing pipeline.
 *
 * Skipped unless OPENAI_ENDPOINT and OPENAI_API_KEY are present, so normal builds and CI
 * never make a network call or need credentials. Run it with:
 *
 *   docker run --rm --network host --env-file .env \
 *     -e BENCH_MODELS="model-a,model-b" ... ./gradlew testDebugUnitTest \
 *     --tests '*PostProcessingBenchmark*'
 *
 * Prompts come from the production [SystemPromptBuilder] and [BuiltInPrompts], so what is
 * measured is what the app actually sends. Every case runs several times: sampling wobbles
 * at temperature 0.3, and a single sample once made a model look perfect on one run and
 * flawed on the next.
 */
class PostProcessingBenchmark {

    private val client = BenchmarkClient()

    private data class CheckResult(
        val model: String,
        val case: BenchmarkCase,
        val checkName: String,
        val passes: Int,
        val reps: Int
    ) {
        val rate: Double get() = passes.toDouble() / reps
        val flaky: Boolean get() = passes in 1 until reps
    }

    @Test
    fun `benchmark models across languages`() {
        assumeTrue(
            "Set OPENAI_ENDPOINT, OPENAI_API_KEY and a model list to run the benchmark",
            client.isConfigured
        )

        val results = mutableListOf<CheckResult>()
        val latencies = mutableMapOf<String, MutableList<Long>>()
        val outputs = mutableMapOf<Pair<String, String>, List<String>>()
        var errors = 0
        var requests = 0

        client.models.forEach { model ->
            BENCHMARK_CASES.forEach { case ->
                val prompt = SystemPromptBuilder.build(
                    profile = case.profile,
                    appContext = case.appContext,
                    prompts = client.builtInPrompts
                )
                val completions = client.completeRepeatedly(model, prompt, case.rawText)
                requests += completions.size
                errors += completions.count { it.error != null }
                latencies.getOrPut(model) { mutableListOf() } += completions.map(Completion::latencyMs)
                outputs[model to case.id] = completions.map(Completion::text)

                case.checks.forEach { check ->
                    val passes = completions.count { it.error == null && check.passes(it.text) }
                    results += CheckResult(model, case, check.name, passes, completions.size)
                }
            }
        }

        report(results, latencies, outputs, errors, requests)

        // Failing checks are a model-quality signal and only get reported. A request that
        // never completed means the run measured nothing, which must not exit green.
        if (errors > 0) {
            throw AssertionError("$errors of $requests requests failed; the run measured nothing.")
        }
    }

    private fun report(
        results: List<CheckResult>,
        latencies: Map<String, List<Long>>,
        outputs: Map<Pair<String, String>, List<String>>,
        errors: Int,
        requests: Int
    ) {
        val sb = StringBuilder()
        fun line(text: String = "") {
            println(text)
            sb.appendLine(text)
        }

        line()
        line("# Post-processing benchmark")
        line()
        line("${client.reps} repetitions per case. \"Checks\" counts each check once per")
        line("repetition, so a check that passes 2 of 3 times scores 2/3 rather than pass or fail.")
        line()
        line("| Model | Checks | Always-pass cases | Flaky checks | p50 | mean | max |")
        line("|---|---|---|---|---|---|---|")
        results.groupBy(CheckResult::model).forEach { (model, rows) ->
            val passed = rows.sumOf(CheckResult::passes)
            val total = rows.sumOf(CheckResult::reps)
            val perCase = rows.groupBy { it.case.id }
            val clean = perCase.count { (_, checks) -> checks.all { it.passes == it.reps } }
            val flaky = rows.count(CheckResult::flaky)
            val ms = latencies.getValue(model).sorted()
            line(
                "| $model | $passed/$total (${(100.0 * passed / total).toInt()}%) | " +
                    "$clean/${perCase.size} | $flaky | ${ms[ms.size / 2]}ms | " +
                    "${ms.average().toLong()}ms | ${ms.last()}ms |"
            )
        }

        line()
        line("## Per-language pass rate")
        line()
        val languages = BENCHMARK_CASES.map(BenchmarkCase::language).distinct()
        line("| Model | ${languages.joinToString(" | ")} |")
        line("|---|${languages.joinToString("") { "---|" }}")
        results.groupBy(CheckResult::model).forEach { (model, rows) ->
            val cells = languages.map { lang ->
                val forLang = rows.filter { it.case.language == lang }
                "${forLang.sumOf(CheckResult::passes)}/${forLang.sumOf(CheckResult::reps)}"
            }
            line("| $model | ${cells.joinToString(" | ")} |")
        }

        line()
        line("## Checks that did not always pass")
        line()
        val imperfect = results.filter { it.passes < it.reps }.sortedBy { it.rate }
        if (imperfect.isEmpty()) {
            line("None.")
        } else {
            line("| Model | Case | Language | Check | Passed |")
            line("|---|---|---|---|---|")
            imperfect.forEach {
                val tag = if (it.flaky) " (flaky)" else ""
                line(
                    "| ${it.model} | ${it.case.id} | ${it.case.language} | ${it.checkName} | " +
                        "${it.passes}/${it.reps}$tag |"
                )
            }
        }

        line()
        line("## Outputs")
        line()
        BENCHMARK_CASES.forEach { case ->
            line("### ${case.id} (${case.language}) — ${case.intent}")
            line("- raw: `${case.rawText}`")
            client.models.forEach { model ->
                outputs[model to case.id]?.forEachIndexed { i, text ->
                    line("- **$model** #${i + 1}: `$text`")
                }
            }
            line()
        }

        if (errors > 0) line("**$errors of $requests requests failed.**")

        val out = File("build/reports/benchmark/post-processing.md")
        out.parentFile?.mkdirs()
        out.writeText(sb.toString())
        println("\nReport written to ${out.absolutePath}")
    }
}
