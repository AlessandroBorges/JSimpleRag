package bor.tools.simplerag.service.embedding.strategy;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import bor.tools.simplellm.Embeddings_Op;
import bor.tools.simplellm.LLMProvider;
import bor.tools.simplellm.MapParam;
import bor.tools.simplellm.exceptions.LLMException;
import bor.tools.simplerag.dto.DocumentEmbeddingDTO;
import bor.tools.simplerag.service.embedding.model.EmbeddingRequest;
import bor.tools.simplerag.service.llm.LLMServiceManager;
import lombok.RequiredArgsConstructor;

/**
 * Strategy for generating query-optimized embeddings.
 *
 * Used for search operations - generates embeddings optimized for
 * similarity matching against document embeddings.
 *
 * Uses Embeddings_Op.QUERY operation type for best search performance.
 * Integrates with LLMServiceManager pool for multi-provider support.
 *
 * @since 0.0.1
 */
@Component
@RequiredArgsConstructor
public class QueryEmbeddingStrategy implements EmbeddingGenerationStrategy {

    private static final Logger log = LoggerFactory.getLogger(QueryEmbeddingStrategy.class);

    private final LLMServiceManager llmServiceManager;

    @Value("${rag.embedding.default-model:nomic-embed-text}")
    private String defaultEmbeddingModel;

    @Override
    public List<DocumentEmbeddingDTO> generate(EmbeddingRequest request) {
        log.debug("Generating query embedding for text: {} chars",
                request.getText() != null ? request.getText().length() : 0);

        if (request.getText() == null || request.getText().trim().isEmpty()) {
            throw new IllegalArgumentException("Query text cannot be null or empty");
        }

        if (request.getContext() == null) {
            throw new IllegalArgumentException("EmbeddingContext is required");
        }

        try {
            // Resolve embedding model to use
            String modelName = request.getEmbeddingModelName(defaultEmbeddingModel);
            log.debug("Using embedding model: {}", modelName);

            // Get appropriate LLMProvider from pool
            LLMProvider llmService = llmServiceManager.getLLMServiceByRegisteredModel(modelName);

            if (llmService == null) {
                throw new IllegalStateException(
                    "No LLM service found for embedding model: " + modelName +
                    ". Check if the model is registered in any provider."
                );
            }

            log.debug("Using LLMProvider from provider: {}", llmService.getServiceProvider());

            // Prepare parameters
            MapParam params = new MapParam();
            params.model(modelName);

            // Add library context if available
            if (request.getContext().getLibrary() != null) {
                params.put("library_context", request.getContext().getLibrary().getNome());
            }

            // Generate query embedding
            float[] embedding = llmService.embeddings(
                    Embeddings_Op.QUERY,
                    request.getText(),
                    params);

            log.debug("Successfully generated query embedding with {} dimensions", embedding.length);

            // Query embeddings are typically used directly as float[]
            // Return empty list as this strategy is used via generateQueryVector()
            return new ArrayList<>();

        } catch (LLMException e) {
            log.error("Failed to generate query embedding: {}", e.getMessage(), e);
            throw new RuntimeException("Query embedding generation failed", e);
        }
    }

    @Override
    public boolean supports(EmbeddingRequest request) {
        return request.getOperation() == Embeddings_Op.QUERY
                && request.getText() != null
                && request.getChapter() == null;
    }

    @Override
    public String getStrategyName() {
        return "QueryEmbeddingStrategy";
    }

    /**
     * Generate query embedding directly as float array.
     * This is the preferred method for query embeddings.
     *
     * @param queryText Query text
     * @param modelName Embedding model name
     * @param libraryContext Library context name (optional)
     * @return Embedding vector
     */
    public float[] generateQueryVector(String queryText, String modelName, String libraryContext) {
        try {
            // Get appropriate LLMProvider from pool
            LLMProvider llmService = llmServiceManager.getLLMServiceByRegisteredModel(modelName);

            if (llmService == null) {
                throw new IllegalStateException(
                    "No LLM service found for embedding model: " + modelName +
                    ". Check if the model is registered in any provider."
                );
            }

            log.debug("Using LLMProvider from provider: {} for model: {}",
                     llmService.getServiceProvider(), modelName);

            // Prepare parameters
            MapParam params = new MapParam();
            params.model(modelName);

            if (libraryContext != null) {
                params.put("library_context", libraryContext);
            }

            // Generate embedding
            return llmService.embeddings(Embeddings_Op.QUERY, queryText, params);

        } catch (LLMException e) {
            log.error("Failed to generate query vector: {}", e.getMessage(), e);
            throw new RuntimeException("Query vector generation failed", e);
        }
    }

    /**
     * Generate query embedding with default model.
     *
     * @param queryText Query text
     * @return Embedding vector
     */
    public float[] generateQueryVector(String queryText) {
        return generateQueryVector(queryText, defaultEmbeddingModel, null);
    }
}
