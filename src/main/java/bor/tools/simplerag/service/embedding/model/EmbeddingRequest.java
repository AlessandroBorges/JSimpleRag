package bor.tools.simplerag.service.embedding.model;

import bor.tools.simplellm.Embeddings_Op;
import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.entity.enums.TipoEmbedding;
import lombok.Builder;
import lombok.Data;

/**
 * Request object for embedding generation.
 *
 * Encapsulates all parameters needed for flexible embedding generation.
 * Uses EmbeddingContext for library configuration and model resolution.
 *
 * @since 0.0.1
 */
@Data
@Builder
public class EmbeddingRequest {

    /**
     * Chapter to process (for document-based embeddings)
     */
    private ChapterDTO chapter;

    /**
     * Raw text to embed (for text-based embeddings)
     */
    private String text;

    /**
     * Context including library configuration, model selection, and metadata
     */
    private EmbeddingContext context;

    /**
     * Embedding operation type
     */
    private Embeddings_Op operation;

    /**
     * Type of embedding to generate
     */
    private TipoEmbedding tipoEmbedding;

    /**
     * Generation strategy flag
     * (FLAG_AUTO, FLAG_FULL_TEXT_METADATA, etc.)
     */
    @Builder.Default
    private Integer generationFlag = 5; // FLAG_AUTO

    /**
     * Number of Q&A pairs to generate (for Q&A embeddings)
     */
    private Integer numberOfQAPairs;

    /**
     * Maximum summary length (for summary embeddings)
     */
    private Integer maxSummaryLength;

    /**
     * Custom summarization instructions (for summary embeddings)
     */
    private String customSummaryInstructions;

    /**
     * Whether to include metadata in embedding
     */
    @Builder.Default
    private boolean includeMetadata = true;

    /**
     * Document ID (optional, for context)
     */
    private Integer documentId;

    /**
     * Chapter ID (optional, for context)
     */
    private Integer chapterId;

    /**
     * Convenience method to get embedding model name from context.
     * Resolves using context's hierarchy: explicit → library → global.
     *
     * @param globalDefault Global default model name
     * @return Resolved embedding model name
     */
    public String getEmbeddingModelName(String globalDefault) {
        return context != null
            ? context.resolveEmbeddingModel(globalDefault)
            : globalDefault;
    }

    /**
     * Convenience method to get completion model name from context.
     * Resolves using context's hierarchy: explicit → library → global.
     *
     * @param globalDefault Global default model name
     * @return Resolved completion model name
     */
    public String getCompletionModelName(String globalDefault) {
        return context != null
            ? context.resolveCompletionModel(globalDefault)
            : globalDefault;
    }
}