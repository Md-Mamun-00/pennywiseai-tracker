package com.pennywiseai.tracker.domain.service

import kotlinx.coroutines.flow.Flow

/** One piece of a streamed model reply: text, or a tool the model wants called. */
sealed class LlmEvent {
    data class Text(val delta: String) : LlmEvent()
    data class ToolCall(val name: String, val arguments: Map<String, Any?>) : LlmEvent()
}

/**
 * The model's 4096-token window can't take the input: too long a history when a conversation is
 * created, or a prefill or reply that ran out of room. LiteRT-LM 0.19.0 has no error type for it;
 * both of its messages ("Not enough space left in the context window…", "Max number of tokens
 * reached, the context window is full") say this.
 */
fun Throwable.isContextFull(): Boolean = message?.contains("context window", ignoreCase = true) == true

/** The backend ran fine and the model's output was the problem: a full window, or a tool call that doesn't parse. */
fun Throwable.isModelOutputError(): Boolean =
    isContextFull() || message?.contains(TOOL_CALL_PARSE_FAILURE, ignoreCase = true) == true

/** LiteRT-LM has no error type for it, only this message (0.19.0). */
const val TOOL_CALL_PARSE_FAILURE = "Failed to parse tool calls"

interface LlmService {
    suspend fun initialize(modelPath: String): Result<Unit>

    /**
     * @param withTools register the app's tool set (#170). Tool calls are never
     *  executed by the runtime — they surface as [LlmEvent.ToolCall] and the app
     *  decides (mutations always go through user confirmation).
     */
    suspend fun createConversation(
        systemPrompt: String,
        history: List<Pair<String, Boolean>>, // message to isUser
        withTools: Boolean = false
    ): Result<Unit>

    fun sendMessage(message: String): Flow<String>
    fun sendMessageEvents(message: String): Flow<LlmEvent>

    /**
     * One ad-hoc turn in its own conversation: [systemPrompt], no chat history and
     * no tools, its reply returned whole instead of streamed. The live conversation
     * is closed first — the chat rebuilds its own from Room on the next send — and
     * this one is closed before returning. [jsonSchema], when set, constrains the
     * reply to that schema (constrained decoding). The engine must be initialized.
     */
    suspend fun askOneShot(systemPrompt: String, userPrompt: String, jsonSchema: String? = null): Result<String>
    fun hasActiveConversation(): Boolean
    suspend fun closeConversation()
    suspend fun reset()
    fun isInitialized(): Boolean

    /** Tokens the live conversation holds (preface, history and replies so far), or null without one. */
    suspend fun tokenCount(): Int?
}
