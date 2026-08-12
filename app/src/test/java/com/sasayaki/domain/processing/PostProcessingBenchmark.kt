package com.sasayaki.domain.processing

import com.google.gson.Gson
import com.sasayaki.data.api.model.ChatCompletionRequest
import com.sasayaki.data.api.model.ChatCompletionResponse
import com.sasayaki.data.api.model.ChatMessage
import com.sasayaki.domain.model.PostProcessingPrompt
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

/**
 * LLM-in-the-loop benchmark for the post-processing pipeline.
 *
 * Skipped unless OPENAI_ENDPOINT and OPENAI_API_KEY are present in the environment, so
 * normal builds and CI never make a network call or need credentials. Run it with:
 *
 *   docker run --rm --network host --env-file .env ... ./gradlew testDebugUnitTest \
 *       --tests '*PostProcessingBenchmark*' -PbenchModels=model-a,model-b
 *
 * Prompts come from the production [SystemPromptBuilder] and [BuiltInPrompts], so what is
 * measured is what the app actually sends.
 */
class PostProcessingBenchmark {

    private val endpoint: String? = System.getenv("OPENAI_ENDPOINT")?.trimEnd('/')
    private val apiKey: String? = System.getenv("OPENAI_API_KEY")

    private val models: List<String> = (
        System.getenv("BENCH_MODELS")
            ?: System.getProperty("benchModels")
            ?: System.getenv("OPENAI_MODEL")
            ?: ""
        ).split(",").map(String::trim).filter(String::isNotEmpty)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private val builtInPrompts: List<PostProcessingPrompt> =
        BuiltInPrompts.ALL.mapIndexed { index, seed ->
            PostProcessingPrompt(
                id = index + 1L,
                title = seed.title,
                prompt = seed.prompt,
                builtIn = true
            )
        }

    private data class Outcome(
        val case: BenchmarkCase,
        val model: String,
        val output: String,
        val latencyMs: Long,
        val failedChecks: List<String>,
        val totalChecks: Int,
        val error: String? = null
    ) {
        val passed: Int get() = totalChecks - failedChecks.size
    }

    @Test
    fun `benchmark models across languages`() {
        assumeTrue(
            "Set OPENAI_ENDPOINT and OPENAI_API_KEY to run the benchmark",
            !endpoint.isNullOrBlank() && !apiKey.isNullOrBlank()
        )
        assumeTrue("No models configured (BENCH_MODELS or OPENAI_MODEL)", models.isNotEmpty())

        val outcomes = mutableListOf<Outcome>()
        models.forEach { model ->
            BENCHMARK_CASES.forEach { case ->
                outcomes += runCase(model, case)
            }
        }
        report(outcomes)
    }

    private fun runCase(model: String, case: BenchmarkCase): Outcome {
        val systemPrompt = SystemPromptBuilder.build(
            profile = case.profile,
            appContext = case.appContext,
            prompts = builtInPrompts
        )
        val request = ChatCompletionRequest(
            model = model,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = case.rawText)
            )
        )

        var body = ""
        var failure: String? = null
        val latency = measureTimeMillis {
            try {
                val http = Request.Builder()
                    .url("$endpoint/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(gson.toJson(request).toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(http).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        failure = "HTTP ${response.code}"
                    } else {
                        body = gson.fromJson(raw, ChatCompletionResponse::class.java).text.trim()
                    }
                }
            } catch (e: Exception) {
                failure = e::class.simpleName + ": " + (e.message ?: "")
            }
        }

        val failed = if (failure != null) {
            case.checks.map(Check::name)
        } else {
            case.checks.filterNot { it.passes(body) }.map(Check::name)
        }
        return Outcome(case, model, body, latency, failed, case.checks.size, failure)
    }

    private fun report(outcomes: List<Outcome>) {
        val sb = StringBuilder()
        fun line(text: String = "") {
            println(text)
            sb.appendLine(text)
        }

        line()
        line("# Post-processing benchmark")
        line()
        line("| Model | Checks passed | Cases fully passed | p50 latency | mean | max | Errors |")
        line("|---|---|---|---|---|---|---|")
        outcomes.groupBy(Outcome::model).forEach { (model, rows) ->
            val latencies = rows.map(Outcome::latencyMs).sorted()
            val p50 = latencies[latencies.size / 2]
            val mean = latencies.average().toLong()
            val checksPassed = rows.sumOf(Outcome::passed)
            val checksTotal = rows.sumOf(Outcome::totalChecks)
            val clean = rows.count { it.failedChecks.isEmpty() }
            val errors = rows.count { it.error != null }
            line(
                "| $model | $checksPassed/$checksTotal | $clean/${rows.size} | ${p50}ms | " +
                    "${mean}ms | ${latencies.last()}ms | $errors |"
            )
        }

        line()
        line("## Per-language checks passed")
        line()
        val languages = BENCHMARK_CASES.map(BenchmarkCase::language).distinct()
        line("| Model | ${languages.joinToString(" | ")} |")
        line("|---|${languages.joinToString("") { "---|" }}")
        outcomes.groupBy(Outcome::model).forEach { (model, rows) ->
            val cells = languages.map { lang ->
                val forLang = rows.filter { it.case.language == lang }
                "${forLang.sumOf(Outcome::passed)}/${forLang.sumOf(Outcome::totalChecks)}"
            }
            line("| $model | ${cells.joinToString(" | ")} |")
        }

        line()
        line("## Failures")
        line()
        val failures = outcomes.filter { it.failedChecks.isNotEmpty() }
        if (failures.isEmpty()) {
            line("None.")
        } else {
            failures.forEach { o ->
                line("### ${o.model} — ${o.case.id} (${o.case.language})")
                line("- intent: ${o.case.intent}")
                o.error?.let { line("- request error: $it") }
                line("- failed: ${o.failedChecks.joinToString("; ")}")
                line("- raw: `${o.case.rawText.take(160)}`")
                line("- out: `${o.output.take(300)}`")
                line()
            }
        }

        line()
        line("## All outputs")
        line()
        outcomes.groupBy { it.case.id }.forEach { (id, rows) ->
            line("### $id (${rows.first().case.language}) — ${rows.first().case.intent}")
            line("- raw: `${rows.first().case.rawText}`")
            rows.forEach { line("- **${it.model}** (${it.latencyMs}ms): `${it.output}`") }
            line()
        }

        val out = File("build/reports/benchmark/post-processing.md")
        out.parentFile?.mkdirs()
        out.writeText(sb.toString())
        println("\nReport written to ${out.absolutePath}")
    }
}
