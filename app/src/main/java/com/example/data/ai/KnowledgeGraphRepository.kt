    val nodeCountFlow: Flow<Int> = kgDao.getNodeCountFlow()
    val edgeCountFlow: Flow<Int> = kgDao.getEdgeCountFlow()
    val chunkCountFlow: Flow<Int> = ragDao.getChunkCountFlow()
    val aiConfigFlow: Flow<AiProviderConfigEntity?> = aiConfigDao.getConfigFlow().map { it?.let(::decryptConfig) }
    val brainTopicsFlow: Flow<List<BrainTopicEntity>> = brainTopicDao.getAllFlow()

    private fun normalizeAiConfig(config: AiProviderConfigEntity): AiProviderConfigEntity {
        val provider = ProviderType.fromString(config.providerType)
        val legacy = config.embeddingModel.trim()
        val nvidiaEndpoint = config.baseUrl.contains("integrate.api.nvidia.com", ignoreCase = true)
        val text = config.textEmbeddingModel.trim().ifBlank {
            if (nvidiaEndpoint) provider.defaultTextEmbeddingModel else legacy
        }.ifBlank { provider.defaultTextEmbeddingModel }
        val multi = config.multimodalEmbeddingModel.trim().ifBlank {
            if (nvidiaEndpoint) provider.defaultMultimodalEmbeddingModel else ""
        }
        return config.copy(
            providerType = provider.name,
            textEmbeddingModel = text,
            multimodalEmbeddingModel = multi
        )
    }

    private fun decryptConfig(config: AiProviderConfigEntity): AiProviderConfigEntity =
        config.copy(apiKey = ApiKeyProtector.decrypt(config.apiKey))
