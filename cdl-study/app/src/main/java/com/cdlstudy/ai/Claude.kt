package com.cdlstudy.ai

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.cdlstudy.domain.AiGrading
import com.cdlstudy.domain.MiniJson
import com.cdlstudy.domain.Subsection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration

/**
 * The one part of the app that uses the network.
 *
 * It sends two things: the facts of the subsection you are on, which this app
 * wrote, and the words you typed from memory. It does not send the manual. The
 * app does not hold the manual, so it cannot.
 */
object Claude {

    /** A message to put on the screen. Never a stack trace. */
    class Failed(message: String) : Exception(message)

    suspend fun grade(
        settings: AiSettings,
        subsection: Subsection,
        blurt: String,
    ): AiGrading.Opinion = withContext(Dispatchers.IO) {
        if (!settings.ready) throw Failed("Put your API key in Settings first.")
        if (subsection.facts.isEmpty()) throw Failed("This part has no facts to grade.")

        val client = AnthropicOkHttpClient.builder()
            .apiKey(settings.key)
            .timeout(Duration.ofSeconds(180))
            .maxRetries(2)
            .build()

        val params = MessageCreateParams.builder()
            .model(settings.model)
            .maxTokens(4000L)
            .system(AiGrading.SYSTEM.trim())
            .addUserMessage(AiGrading.prompt(subsection, blurt))
            .outputConfig(
                OutputConfig.builder()
                    .format(
                        JsonOutputFormat.builder()
                            .schema(schemaOf(AiGrading.schema()))
                            .build()
                    )
                    .build()
            )
            .build()

        val message = try {
            client.messages().create(params)
        } catch (e: UnauthorizedException) {
            throw Failed("That API key was refused. Check it in Settings.")
        } catch (e: PermissionDeniedException) {
            throw Failed("That key is not allowed to use this model. Try another model.")
        } catch (e: RateLimitException) {
            throw Failed("Too many requests. Wait a minute and ask again.")
        } catch (e: AnthropicIoException) {
            throw Failed("No answer. Check your connection and ask again.")
        } catch (e: AnthropicServiceException) {
            throw Failed("The service returned an error. Wait a minute and ask again.")
        } catch (e: Exception) {
            throw Failed("The request did not go through. Ask again.")
        } finally {
            runCatching { client.close() }
        }

        val text = message.content()
            .mapNotNull { block -> block.text().orElse(null)?.text() }
            .joinToString("\n")
            .trim()

        if (text.isEmpty()) throw Failed("The answer came back empty. Ask again.")

        try {
            AiGrading.parse(text, subsection)
        } catch (e: AiGrading.BadReply) {
            throw Failed(e.message ?: "The answer could not be read.")
        }
    }

    /**
     * Turns the schema text into the shape the SDK wants: a map of JSON values.
     * The schema is written once, in [AiGrading.schema], where it is tested.
     */
    internal fun schemaOf(json: String): JsonOutputFormat.Schema {
        val map = MiniJson.parse(json) as? Map<*, *>
            ?: error("the schema in AiGrading is not a JSON object")
        val builder = JsonOutputFormat.Schema.builder()
        map.forEach { (name, value) ->
            builder.putAdditionalProperty(name as String, JsonValue.from(value))
        }
        return builder.build()
    }
}
