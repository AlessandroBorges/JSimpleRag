package bor.tools.simplerag.service.embedding;

import java.util.List;

import bor.tools.simplellm.ModelEmbedding.Embeddings_Op;
import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocumentEmbeddingDTO;
import bor.tools.simplerag.service.embedding.model.EmbeddingContext;
import bor.tools.simplerag.service.embedding.model.EmbeddingRequest;

/**
 * Service interface for embedding generation operations.
 *
 * This service provides a clean, high-level API for generating embeddings
 * in various contexts (documents, queries, Q&A, summaries).
 *
 * Integrates with LLMServiceManager pool for multi-provider support
 * and hierarchical model resolution (request → library → global).
 *
 * Architecture:
 * - Uses Strategy pattern for different embedding types
 * - Integrates with LLMServiceManager for LLM access
 * - Supports async processing through EmbeddingOrchestrator
 *
 * @since 0.0.1
 * @author JSimpleRag Team
 */
public interface EmbeddingService {

    // ========== Chapter Embeddings ==========

    /**
     * Generate embeddings for a chapter using automatic strategy selection.
     *
     * Uses default embedding model from context or global configuration.
     * The service automatically chooses the best approach based on:
     * - Chapter size (tokens)
     * - Library configuration
     * - Content type
     *
     * @param chapter Chapter to process
     * @param context Context including library and model configuration
     * @return List of generated embeddings (may contain multiple chunks)
     */
    List<DocumentEmbeddingDTO> generateChapterEmbeddings(ChapterDTO chapter, EmbeddingContext context);

    /**
     * Generate embeddings for a chapter with specific generation flag.
     *
     * @param chapter Chapter to process
     * @param context Context including library and model configuration
     * @param generationFlag Strategy flag (FLAG_AUTO, FLAG_FULL_TEXT_METADATA, etc.)
     * @return List of generated embeddings
     */
    List<DocumentEmbeddingDTO> generateChapterEmbeddings(
            ChapterDTO chapter,
            EmbeddingContext context,
            int generationFlag);

    // ========== Query Embeddings ==========

    /**
     * Generate query-optimized embedding for search operations.
     *
     * Uses default embedding model from context or global configuration.
     * Optimized for similarity matching against document embeddings.
     * Uses Embeddings_Op.QUERY for best search performance.
     *
     * @param query Search query text
     * @param context Context including library and model configuration
     * @return Query embedding vector
     */
    float[] generateQueryEmbedding(String query, EmbeddingContext context);

    /**
     * Generate query embedding with explicit model override.
     *
     * Allows overriding the embedding model for specific queries.
     *
     * @param query Search query text
     * @param context Context including library configuration
     * @param modelName Embedding model name to use
     * @return Query embedding vector
     */
    float[] generateQueryEmbedding(String query, EmbeddingContext context, String modelName);

    // ========== Q&A Embeddings ==========

    /**
     * Generate Q&A embeddings from chapter content.
     *
     * Creates synthetic question-answer pairs using a completion model,
     * then generates embeddings for each pair. Improves retrieval for
     * conversational queries.
     *
     * Uses completion model for generation and embedding model for vectors.
     *
     * @param chapter Source chapter
     * @param context Context including library and model configuration
     * @param numberOfPairs Number of Q&A pairs to generate (null for default=3)
     * @return List of Q&A embeddings
     */
    List<DocumentEmbeddingDTO> generateQAEmbeddings(
            ChapterDTO chapter,
            EmbeddingContext context,
            Integer numberOfPairs);

    // ========== Summary Embeddings ==========

    /**
     * Generate summary-based embeddings from chapter content.
     *
     * Creates a condensed summary using a completion model, then generates
     * embeddings. Useful for improving retrieval of main concepts from
     * large chapters.
     *
     * Uses completion model for summarization and embedding model for vectors.
     *
     * @param chapter Source chapter
     * @param context Context including library and model configuration
     * @param maxSummaryLength Maximum summary length (null for default)
     * @param customInstructions Custom summarization instructions (null for default)
     * @return List of summary embeddings
     */
    List<DocumentEmbeddingDTO> generateSummaryEmbeddings(
            ChapterDTO chapter,
            EmbeddingContext context,
            Integer maxSummaryLength,
            String customInstructions);

    // ========== Low-level Methods ==========

    /**
     * Generate embedding using custom operation type and text.
     *
     * Uses default embedding model from context or global configuration.
     * Low-level method for advanced use cases.
     *
     * @param operation Embedding operation type (QUERY, DOCUMENT, CLUSTERING)
     * @param text Text to embed
     * @param context Context including library and model configuration
     * @return Embedding vector
     */
    float[] generateEmbedding(Embeddings_Op operation, String text, EmbeddingContext context);

    /**
     * Generate embedding with explicit model override.
     *
     * Allows full control over operation type and model selection.
     * Low-level method for advanced use cases.
     *
     * @param operation Embedding operation type (QUERY, DOCUMENT, CLUSTERING)
     * @param text Text to embed
     * @param context Context including library configuration
     * @param modelName Embedding model name to use
     * @return Embedding vector
     */
    float[] generateEmbedding(
            Embeddings_Op operation,
            String text,
            EmbeddingContext context,
            String modelName);

    /**
     * Generate embeddings using custom request configuration.
     *
     * Flexible method for complex embedding scenarios.
     * Delegates to appropriate strategy based on request configuration.
     *
     * @param request Embedding request with all parameters
     * @return List of generated embeddings
     */
    List<DocumentEmbeddingDTO> generateEmbeddings(EmbeddingRequest request);
}
