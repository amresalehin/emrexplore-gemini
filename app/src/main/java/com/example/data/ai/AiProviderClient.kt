                return@withContext ConnectionTestResult(false, "Choose a chat model first. Tap Fetch models.", System.currentTimeMillis() - startTime)
            }

            val listedModels = runCatching { listModels(config) }.getOrNull()
            if (!listedModels.isNullOrEmpty()) {
                val textEmbedding = config.textEmbeddingModel.ifBlank { config.embeddingModel }
                val multimodalEmbedding = config.multimodalEmbeddingModel.ifBlank { textEmbedding }
                val requirements: List<Triple<String, String, (AvailableAiModel) -> Boolean>> = listOf(
                    Triple("chat", config.chatModel, { model: AvailableAiModel -> model.supportsChat }),
                    Triple("vision", config.visionModel, { model: AvailableAiModel -> model.supportsVision }),
                    Triple("text embedding", textEmbedding, { model: AvailableAiModel -> model.supportsEmbedding }),
                    Triple("multimodal embedding", multimodalEmbedding, { model: AvailableAiModel ->
                        model.supportsMultimodalEmbedding ||
                            (multimodalEmbedding == textEmbedding && model.supportsEmbedding)
                    })
                )
                for ((role, modelIdRaw, capability) in requirements) {
                    val modelId = modelIdRaw.trim()
                    if (modelId.isNotBlank() && listedModels.none { it.id == modelId && capability(it) }) {
                        return@withContext ConnectionTestResult(
                            false,
                            "Selected $role model is not available: $modelId. Tap Fetch and choose a compatible model.",
                            System.currentTimeMillis() - startTime
                        )
                    }