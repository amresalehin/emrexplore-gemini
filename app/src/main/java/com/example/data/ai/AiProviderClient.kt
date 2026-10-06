            }
            val vector = embedText(listOf("embedding connection test"), config, model, "query").firstOrNull()
            if (vector == null || vector.isEmpty()) {
                ConnectionTestResult(false, "Embedding provider returned no vector.", System.currentTimeMillis() - started)
            } else {
                val multimodalModel = config.multimodalEmbeddingModel.trim()
                if (multimodalModel.isNotBlank()) {
                    val visualQuery = embedMultimodalQuery("visual embedding connection test", config)
                    if (visualQuery.isEmpty()) {
                        return@withContext ConnectionTestResult(false, "Text embedding connected, but the configured image embedding model returned no vector.", System.currentTimeMillis() - started)
                    }
                }
                ConnectionTestResult(
                    true,
                    if (multimodalModel.isBlank()) "Text embedding connected · " + provider.displayName else "Text + image embedding connected · " + provider.displayName,