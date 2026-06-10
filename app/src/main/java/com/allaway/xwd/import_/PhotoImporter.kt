package com.allaway.xwd.import_

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.Model
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive
import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * Sends a photo/screenshot of a crossword to Claude, which reads the grid
 * and clues and solves the puzzle, returning structured JSON.
 */
class PhotoImporter(private val apiKey: String) {

    fun import(jpegBytes: ByteArray): ImportedPuzzle {
        val client = AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .build()
        try {
            val params = MessageCreateParams.builder()
                .model(Model.CLAUDE_OPUS_4_8)
                .maxTokens(MAX_TOKENS)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .outputConfig(
                    OutputConfig.builder()
                        .effort(OutputConfig.Effort.HIGH)
                        .format(JsonOutputFormat.builder().schema(responseSchema()).build())
                        .build(),
                )
                .addUserMessageOfBlockParams(
                    listOf(
                        ContentBlockParam.ofImage(
                            ImageBlockParam.builder()
                                .source(
                                    Base64ImageSource.builder()
                                        .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                                        .data(Base64.getEncoder().encodeToString(jpegBytes))
                                        .build(),
                                )
                                .build(),
                        ),
                        ContentBlockParam.ofText(TextBlockParam.builder().text(PROMPT).build()),
                    ),
                )
                .build()

            // Stream so long solves don't hit request timeouts; accumulate the
            // full message rather than handling individual events.
            val accumulator = MessageAccumulator.create()
            client.messages().createStreaming(params).use { stream ->
                stream.stream().forEach(accumulator::accumulate)
            }
            val message = accumulator.message()
            val text = message.content()
                .mapNotNull { block -> block.text().map { it.text() }.orElse(null) }
                .joinToString("")
            if (text.isBlank()) {
                throw ImportException("The model returned no answer (stop reason: ${message.stopReason()}).")
            }
            return parseResponse(text)
        } finally {
            client.close()
        }
    }

    companion object {
        private const val MAX_TOKENS = 64_000L

        private val json = Json { ignoreUnknownKeys = true }

        fun parseResponse(text: String): ImportedPuzzle = try {
            json.decodeFromString(ImportedPuzzle.serializer(), text.trim())
        } catch (e: Exception) {
            throw ImportException("Could not parse the model's response: ${e.message}")
        }

        private val PROMPT = """
            This image is a photo or screenshot of a crossword puzzle. Your job:

            1. Read the grid exactly: its dimensions and the position of every black square.
               Ignore any handwritten or typed letters already filled in — you are
               reconstructing the blank puzzle.
            2. Read every Across and Down clue with its number, exactly as printed.
            3. Solve the crossword completely. Every answer must genuinely answer its clue,
               and every crossing letter must agree. Use standard American-style numbering.
            4. Report the result as JSON matching the response schema:
               - ok: true when you read and solved the puzzle; false otherwise, with a short
                 human-readable explanation in "error" (e.g. the image is not a crossword,
                 or the clues are illegible).
               - grid: exactly `height` strings of exactly `width` characters — the SOLVED
                 grid, uppercase A-Z for letters and "." for black squares.
               - across/down: every clue with its number, the printed clue text, and your answer.
               - title/author: as printed, or "" if absent.

            The grid strings are the source of truth: make sure they agree with every answer
            you list. Double-check the black-square pattern and the clue numbering implied by
            it before answering.
        """.trimIndent()

        fun responseSchema(): JsonOutputFormat.Schema {
            val clueItem = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "number" to mapOf("type" to "integer"),
                    "clue" to mapOf("type" to "string"),
                    "answer" to mapOf("type" to "string"),
                ),
                "required" to listOf("number", "clue", "answer"),
                "additionalProperties" to false,
            )
            val clueArray = mapOf("type" to "array", "items" to clueItem)
            val schema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "ok" to mapOf("type" to "boolean"),
                    "error" to mapOf("type" to "string"),
                    "title" to mapOf("type" to "string"),
                    "author" to mapOf("type" to "string"),
                    "width" to mapOf("type" to "integer"),
                    "height" to mapOf("type" to "integer"),
                    "grid" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                    "across" to clueArray,
                    "down" to clueArray,
                ),
                "required" to listOf(
                    "ok", "error", "title", "author", "width", "height", "grid", "across", "down",
                ),
                "additionalProperties" to false,
            )
            val builder = JsonOutputFormat.Schema.builder()
            schema.forEach { (key, value) -> builder.putAdditionalProperty(key, JsonValue.from(value)) }
            return builder.build()
        }
    }
}
