package bor.tools.simplerag.service.embedding;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import bor.tools.simplellm.Embeddings_Op;
import bor.tools.simplellm.LLMService;
import bor.tools.simplellm.MapParam;
import bor.tools.simplellm.exceptions.LLMException;
import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocumentEmbeddingDTO;
import bor.tools.simplerag.service.embedding.model.EmbeddingContext;
import bor.tools.simplerag.service.embedding.model.EmbeddingRequest;
import bor.tools.simplerag.service.embedding.strategy.ChapterEmbeddingStrategy;
import bor.tools.simplerag.service.embedding.strategy.EmbeddingGenerationStrategy;
import bor.tools.simplerag.service.embedding.strategy.QAEmbeddingStrategy;
import bor.tools.simplerag.service.embedding.strategy.QueryEmbeddingStrategy;
import bor.tools.simplerag.service.embedding.strategy.SummaryEmbeddingStrategy;
import bor.tools.simplerag.service.llm.LLMServiceManager;
import lombok.RequiredArgsConstructor;

/**
 * Main implementation of the EmbeddingService interface.
 *
 * This service provides a clean, high-level API for embedding generation
 * across different contexts (documents, queries, Q&A, summaries).
 *
 * Architecture:
 * - Uses Strategy pattern to delegate to specialized strategies
 * - Integrates with LLMServiceManager for multi-provider support
 * - Supports hierarchical model resolution (request → library → global)
 * - Provides both convenience methods and flexible low-level access
 *
 * Thread-safety: This service is stateless and thread-safe.
 *
 * @since 0.0.1
 */
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingServiceImpl.class);

    private final LLMServiceManager llmServiceManager;
    private final List<EmbeddingGenerationStrategy> strategies;

    // Specific strategy references for direct access
    private final ChapterEmbeddingStrategy chapterStrategy;
    private final QueryEmbeddingStrategy queryStrategy;
    private final QAEmbeddingStrategy qaStrategy;
    private final SummaryEmbeddingStrategy summaryStrategy;

    @Value("${rag.embedding.default-model:snowflake}")
    private String defaultEmbeddingModel;

    // ========== Chapter Embeddings ==========

    @Override
    public List<DocumentEmbeddingDTO> generateChapterEmbeddings(ChapterDTO chapter, EmbeddingContext context) {
        return generateChapterEmbeddings(chapter, context, ChapterEmbeddingStrategy.FLAG_AUTO);
    }

    @Override
    public List<DocumentEmbeddingDTO> generateChapterEmbeddings(
            ChapterDTO chapter,
            EmbeddingContext context,
            int generationFlag) {

        log.debug("Generating chapter embeddings with flag: {}", generationFlag);

        EmbeddingRequest request = EmbeddingRequest.builder()
                .chapter(chapter)
                .context(context)
                .operation(Embeddings_Op.DOCUMENT)
                .generationFlag(generationFlag)
                .build();

        return chapterStrategy.generate(request);
    }

    // ========== Query Embeddings ==========

    @Override
    public float[] generateQueryEmbedding(String query, EmbeddingContext context) {
        return generateQueryEmbedding(query, context, null);
    }

    @Override
    public float[] generateQueryEmbedding(String query, EmbeddingContext context, String modelName) {
        log.debug("Generating query embedding for: {} chars", query != null ? query.length() : 0);

        // Resolve model name
        String resolvedModel = modelName != null
            ? modelName
            : context.resolveEmbeddingModel(defaultEmbeddingModel);

        // Get library context name
        String libraryContext = context.getLibrary() != null
            ? context.getLibrary().getNome()
            : null;

        // Use QueryEmbeddingStrategy's direct method
        return queryStrategy.generateQueryVector(query, resolvedModel, libraryContext);
    }

    // ========== Q&A Embeddings ==========

    @Override
    public List<DocumentEmbeddingDTO> generateQAEmbeddings(
            ChapterDTO chapter,
            EmbeddingContext context,
            Integer numberOfPairs) {

        log.debug("Generating Q&A embeddings for chapter: {}", chapter.getTitulo());

        EmbeddingRequest request = EmbeddingRequest.builder()
                .chapter(chapter)
                .context(context)
                .operation(Embeddings_Op.DOCUMENT)
                .numberOfQAPairs(numberOfPairs != null ? numberOfPairs : 3)
                .build();

        return qaStrategy.generate(request);
    }

    // ========== Summary Embeddings ==========

    @Override
    public List<DocumentEmbeddingDTO> generateSummaryEmbeddings(
            ChapterDTO chapter,
            EmbeddingContext context,
            Integer maxSummaryLength,
            String customInstructions) {

        log.debug("Generating summary embedding for chapter: {}", chapter.getTitulo());

        EmbeddingRequest request = EmbeddingRequest.builder()
                .chapter(chapter)
                .context(context)
                .operation(Embeddings_Op.DOCUMENT)
                .maxSummaryLength(maxSummaryLength != null ? maxSummaryLength : 500)
                .customSummaryInstructions(customInstructions)
                .build();

        return summaryStrategy.generate(request);
    }

    // ========== Low-level Methods ==========

    @Override
    public float[] generateEmbedding(Embeddings_Op operation, String text, EmbeddingContext context) {
        return generateEmbedding(operation, text, context, null);
    }

    @Override
    public float[] generateEmbedding(
            Embeddings_Op operation,
            String text,
            EmbeddingContext context,
            String modelName) {

        log.debug("Generating embedding with operation: {}", operation);

        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Text cannot be null or empty");
        }

        if (context == null) {
            throw new IllegalArgumentException("EmbeddingContext is required");
        }

        try {
            // Resolve embedding model to use
            String resolvedModel = modelName != null
                ? modelName
                : context.resolveEmbeddingModel(defaultEmbeddingModel);

            log.debug("Using embedding model: {}", resolvedModel);

            // Get appropriate LLMService from pool
            LLMService llmService = llmServiceManager.getLLMServiceByRegisteredModel(resolvedModel);

            if (llmService == null) {
                throw new IllegalStateException(
                    "No LLM service found for embedding model: " + resolvedModel +
                    ". Check if the model is registered in any provider."
                );
            }

            log.debug("Using LLMService from provider: {}", llmService.getServiceProvider());

            // Prepare parameters
            MapParam params = new MapParam();
            params.model(resolvedModel);

            // Add library context if available
            if (context.getLibrary() != null) {
                params.put("library_context", context.getLibrary().getNome());
            }

            // Generate embedding
            float[] embedding = llmService.embeddings(operation, text, params);

            log.debug("Successfully generated embedding with {} dimensions", embedding.length);
            return embedding;

        } catch (LLMException e) {
            log.error("Failed to generate embedding: {}", e.getMessage(), e);
            throw new RuntimeException("Embedding generation failed", e);
        }
    }

    @Override
    public List<DocumentEmbeddingDTO> generateEmbeddings(EmbeddingRequest request) {
        log.debug("Generating embeddings using custom request");

        if (request == null) {
            throw new IllegalArgumentException("EmbeddingRequest is required");
        }

        if (request.getContext() == null) {
            throw new IllegalArgumentException("EmbeddingContext is required");
        }

        // Find appropriate strategy
        EmbeddingGenerationStrategy strategy = findStrategy(request);

        if (strategy == null) {
            throw new IllegalStateException(
                "No strategy found for request. Operation: " + request.getOperation() +
                ", has chapter: " + (request.getChapter() != null) +
                ", has text: " + (request.getText() != null) +
                ", numberOfQAPairs: " + request.getNumberOfQAPairs() +
                ", maxSummaryLength: " + request.getMaxSummaryLength()
            );
        }

        log.debug("Using strategy: {}", strategy.getStrategyName());
        return strategy.generate(request);
    }

    // ========== Private Helper Methods ==========

    /**
     * Finds the appropriate strategy for a given request.
     *
     * Iterates through registered strategies and returns the first one
     * that supports the request.
     */
    private EmbeddingGenerationStrategy findStrategy(EmbeddingRequest request) {
        for (EmbeddingGenerationStrategy strategy : strategies) {
            if (strategy.supports(request)) {
                return strategy;
            }
        }
        return null;
    }

    /**
     * Gets information about available strategies.
     * Useful for debugging and monitoring.
     */
    public List<String> getAvailableStrategies() {
        return strategies.stream()
                .map(EmbeddingGenerationStrategy::getStrategyName)
                .toList();
    }

    /**
     * Gets service status information.
     * Useful for health checks and monitoring.
     */
    public java.util.Map<String, Object> getServiceStatus() {
        java.util.Map<String, Object> status = new java.util.HashMap<>();
        status.put("service_class", this.getClass().getSimpleName());
        status.put("default_embedding_model", defaultEmbeddingModel);
        status.put("available_strategies", getAvailableStrategies());
        status.put("llm_service_manager_available", llmServiceManager != null);

        if (llmServiceManager != null) {
            try {
        	llmServiceManager.refreshRegisteredModels();
        	var mapModelName2llmService = llmServiceManager.getRegisteredModelsMap();
                status.put("registered_models", mapModelName2llmService.keySet());
                status.put("map_registered_model_llmservice", mapModelName2llmService);

                // Get default completion model from LLMServiceManager
                String defaultCompletionModel = llmServiceManager.getDefaultCompletionModelName();
                status.put("default_completion_model", defaultCompletionModel);
            } catch (Exception e) {
                log.debug("Could not retrieve registered models: {}", e.getMessage());
            }
        }

        return status;
    }
}
