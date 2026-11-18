package com.pennywiseai.tracker.domain.usecase

import com.pennywiseai.tracker.data.model.ExtractedTransaction
import com.pennywiseai.tracker.data.preferences.CurrencyPreferences
import com.pennywiseai.tracker.data.repository.CategoryRepository
import com.pennywiseai.tracker.data.repository.LlmRepository
import com.pennywiseai.tracker.data.repository.ModelRepository
import com.pennywiseai.tracker.data.service.ModelExtraction
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/**
 * Reads one transaction out of free text — a voice transcript, a screenshot's
 * words — with the on-device model, in a one-shot turn that never touches chat
 * history. Null when the model isn't downloaded, the turn fails, or the text
 * holds no amount: the caller falls back (the image rules, or leaving the form
 * for the user to fill in).
 */
class ExtractTransactionUseCase @Inject constructor(
    private val modelRepository: ModelRepository,
    private val llmRepository: LlmRepository,
    private val currencyPreferences: CurrencyPreferences,
    private val categoryRepository: CategoryRepository,
) {
    suspend operator fun invoke(text: String): ExtractedTransaction? {
        if (text.isBlank() || !modelRepository.isModelDownloaded()) return null
        val reply = llmRepository.extractOneShot(
            systemPrompt = ModelExtraction.systemPrompt(
                baseCurrency = currencyPreferences.baseCurrency.first(),
                today = LocalDate.now(),
                expenseCategories = categoryRepository.getVisibleExpenseCategories().first().map { it.name },
                incomeCategories = categoryRepository.getVisibleIncomeCategories().first().map { it.name },
            ),
            userPrompt = ModelExtraction.prompt(text),
            jsonSchema = ModelExtraction.SCHEMA,
        ).getOrNull() ?: return null
        return ModelExtraction.parse(reply)
    }
}
