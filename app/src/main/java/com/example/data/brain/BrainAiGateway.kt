package com.example.data.brain

import com.example.data.ai.AiProviderClient
import com.example.data.ai.AnalysisResult
import com.example.data.ai.ExtractedRelation
import com.example.data.local.AiProviderConfigEntity

interface BrainAiGateway {
    suspend fun analyzeDocument(
        text: String,
        fileName: String,
        config: AiProviderConfigEntity
    ): AnalysisResult

    suspend fun analyzeImage(
        base64Jpeg: String?,
        metadataSummary: String,
        fileName: String,
        config: AiProviderConfigEntity
    ): AnalysisResult

    suspend fun embedTextPassages(
        texts: List<String>,
        config: AiProviderConfigEntity
    ): List<FloatArray>

    suspend fun embedTextQuery(
        text: String,
        config: AiProviderConfigEntity
    ): FloatArray?

    suspend fun generateRagAnswer(
        question: String,
        contextText: String,
        graphContext: String,
        chatHistory: List<Pair<String, String>>,
        config: AiProviderConfigEntity
    ): String

    suspend fun chatAboutFile(
        question: String,
        fileName: String,
        fileContent: String?,
        base64Jpeg: String?,
        metadataSummary: String?,
        chatHistory: List<Pair<String, String>>,
        config: AiProviderConfigEntity
    ): String

    suspend fun chatGeneral(
        question: String,
        chatHistory: List<Pair<String, String>>,
        config: AiProviderConfigEntity
    ): String
}

class DefaultBrainAiGateway(
    private val client: AiProviderClient
) : BrainAiGateway {
    override suspend fun analyzeDocument(
        text: String,
        fileName: String,
        config: AiProviderConfigEntity
    ): AnalysisResult = client.analyzeDocument(text, fileName, config)

    override suspend fun analyzeImage(
        base64Jpeg: String?,
        metadataSummary: String,
        fileName: String,
        config: AiProviderConfigEntity
    ): AnalysisResult = client.analyzeImage(base64Jpeg, metadataSummary, fileName, config)

    override suspend fun embedTextPassages(
        texts: List<String>,
        config: AiProviderConfigEntity
    ): List<FloatArray> = client.embedTextPassages(texts, config)

    override suspend fun embedTextQuery(
        text: String,
        config: AiProviderConfigEntity
    ): FloatArray? = client.embedTextQuery(text, config)

    override suspend fun generateRagAnswer(
        question: String,
        contextText: String,
        graphContext: String,
        chatHistory: List<Pair<String, String>>,
        config: AiProviderConfigEntity
    ): String = client.generateRagAnswer(
        question = question,
        contextText = contextText,
        graphContext = graphContext,
        chatHistory = chatHistory,
        config = config
    )

    override suspend fun chatAboutFile(
        question: String,
        fileName: String,
        fileContent: String?,
        base64Jpeg: String?,
        metadataSummary: String?,
        chatHistory: List<Pair<String, String>>,
        config: AiProviderConfigEntity
    ): String = client.chatAboutFile(
        question,
        fileName,
        fileContent,
        base64Jpeg,
        metadataSummary,
        chatHistory,
        config
    )

    override suspend fun chatGeneral(
        question: String,
        chatHistory: List<Pair<String, String>>,
        config: AiProviderConfigEntity
    ): String = client.chatGeneral(question, null, chatHistory, config)
}
