package bor.tools.simplerag.service.embedding.strategy;

import java.util.List;

import bor.tools.simplerag.dto.DocChunkDTO;
import bor.tools.simplerag.service.embedding.model.EmbeddingRequest;

/**
 * Strategy interface for embedding generation.
 *
 * Implements the Strategy pattern to allow different approaches
 * for generating embeddings based on context:
 * - Chapter embeddings (with chunking)
 * - Query embeddings (search-optimized)
 * - Q&A embeddings (synthetic pairs)
 * - Summary embeddings (condensed content)
 *
 * Each strategy encapsulates specific logic for its embedding type.
 *
 * @since 0.0.1
 */
public interface EmbeddingGenerationStrategy {

    /**
     * Generate embeddings based on the request.
     *
     * @param request Embedding request with all parameters
     * @return List of generated embeddings
     * @throws IllegalArgumentException if request is invalid for this strategy
     */
    List<DocChunkDTO> generate(EmbeddingRequest request);

    /**
     * Check if this strategy can handle the given request.
     *
     * @param request Embedding request
     * @return true if this strategy supports the request
     */
    boolean supports(EmbeddingRequest request);

    /**
     * Get the name of this strategy for logging/debugging.
     *
     * @return Strategy name
     */
    String getStrategyName();
}
