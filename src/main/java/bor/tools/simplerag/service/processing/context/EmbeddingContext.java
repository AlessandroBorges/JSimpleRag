package bor.tools.simplerag.service.processing.context;

import java.util.Collections;
import java.util.List;

import bor.tools.simplellm.LLMProvider;
import bor.tools.simplellm.MapParam;
import bor.tools.simplellm.ModelEmbedding;
import bor.tools.simplellm.Embeddings_Op;
import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.service.llm.LLMServiceManager;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Context for embedding generation operations.
 *
 * <p>Encapsulates a validated LLM service and embedding model for vector generation
 * operations. The context is created once per document and reused throughout the
 * processing pipeline to avoid redundant model lookups and validations.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Single validation point - created once, validated once</li>
 *   <li>Batch processing support - up to 10 texts per batch</li>
 *   <li>Dynamic context length - respects model capabilities</li>
 *   <li>Automatic model selection based on library configuration</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Create context once
 * EmbeddingContext context = EmbeddingContext.create(library, llmServiceManager);
 *
 * // Get dynamic context length
 * int contextLength = context.getContextLength(); // e.g., 8192
 *
 * // Generate embeddings in batches
 * String[] batch = {text1, text2, text3, ..., text10}; // up to 10
 * float[][] vectors = context.generateEmbeddingsBatch(
 *     batch,
 *     Embeddings_Op.DOCUMENT
 * );
 * }</pre>
 *
 * @since 0.0.3
 * @version 1.1
 */
@Data
@Builder
@Slf4j
public class EmbeddingContext {

    /**
     * The LLM service instance used for embeddings.
     */
    private LLMProvider llmService;

    /**
     * The embedding model metadata.
     * Must be ModelEmbedding, not the generic Model type.
     */
    private ModelEmbedding modelEmbedding;

    /**
     * The model name (alias or identifier).
     */
    private String modelName;

    /**
     * Default parameters for embedding requests.
     */
    private MapParam params;

    /**
     * Default embedding model if none specified in library.
     */
    private static final String DEFAULT_EMBEDDING_MODEL = "snowflake";

    /**
     * Default context length if model doesn't specify.
     */
    private static final int DEFAULT_CONTEXT_LENGTH = 512;

    /**
     * Creates a new embedding context for the given library.
     *
     * <p>Selects the embedding model specified in the library configuration,
     * or falls back to the default model. The model is validated and ready
     * for use.</p>
     *
     * @param library the library configuration
     * @param manager the LLM service manager
     * @return a new embedding context with validated service and model
     * @throws IllegalStateException if no LLM service supports the requested model
     */
    public static EmbeddingContext create(LibraryDTO library, LLMServiceManager manager) {
        log.debug("Creating embedding context for library: {}", library.getNome());

        // Get requested model from library or use default
        String requestedModel = library.getEmbeddingModel();
        if (requestedModel == null || requestedModel.trim().isEmpty()) {
            requestedModel = DEFAULT_EMBEDDING_MODEL;
            log.debug("No embedding model specified, using default: {}", requestedModel);
        }

        // Get service that supports this model
        LLMProvider service = manager.getLLMServiceByRegisteredModel(requestedModel);

        if (service == null) {
            throw new IllegalStateException(
                "No LLM service found with embedding model: " + requestedModel
            );
        }

        log.debug("Selected embedding model: {} from service: {}",
                 requestedModel,
                 service.getClass().getSimpleName());

        // Get ModelEmbedding metadata
        ModelEmbedding modelEmb = null;
        try {
            var models = service.getRegisteredModels();
            var model = models.get(requestedModel);
            if (model instanceof ModelEmbedding) {
                modelEmb = (ModelEmbedding) model;
                log.debug("Model context length: {}", modelEmb.getContextLength());
            } else {
                log.warn("Model {} is not a ModelEmbedding instance", requestedModel);
            }
        } catch (Exception e) {
            log.warn("Failed to get ModelEmbedding metadata: {}", e.getMessage());
        }

        // Build parameters
        MapParam params = new MapParam();
        params.model(requestedModel);

        return EmbeddingContext.builder()
                .llmService(service)
                .modelEmbedding(modelEmb)
                .modelName(requestedModel)
                .params(params)
                .build();
    }

    /**
     * Generates an embedding vector for a single text.
     *
     * <p>For batch operations, prefer {@link #generateEmbeddingsBatch(String[], Embeddings_Op)}
     * for better performance.</p>
     *
     * @param text the text to generate embedding for
     * @param operation the embedding operation type
     * @return the embedding vector
     * @throws Exception if embedding generation fails
     */
    public float[] generateEmbedding(String text, Embeddings_Op operation)
            throws Exception {
        log.debug("Generating single embedding with model: {}", modelName);

        // Use batch method with single text
        String[] texts = {text};
        List<float[]> vectors = llmService.embeddings(operation, texts, params);

        if (vectors == null || vectors.isEmpty()) {
            throw new IllegalStateException("No embedding generated for text");
        }

        float[] vector = vectors.get(0);
        log.debug("Generated embedding: {} dimensions", vector.length);
        return vector;
    }

    /**
     * Generates embedding vectors for multiple texts in a single batch.
     *
     * <p><b>Revision 1.1:</b> Supports up to 10 texts per batch (increased from 5).
     * Batch size should respect the model's context length obtained via
     * {@link #getContextLength()}.</p>
     *
     * <p>This is significantly more efficient than generating embeddings one by one,
     * as it makes a single API call for all texts.</p>
     *
     * @param texts the texts to generate embeddings for (up to 10)
     * @param operation the embedding operation type
     * @return array of embedding vectors, one per input text
     * @throws Exception if embedding generation fails
     */
    public List<float[]> generateEmbeddingsBatch(String[] texts, Embeddings_Op operation)
            throws Exception {
        if (texts == null || texts.length == 0) {
            return Collections.emptyList();
        }

        log.debug("Generating batch of {} embeddings with model: {}",
                 texts.length, modelName);

        List<float[]> vectors = llmService.embeddings(operation, texts, params);

        log.debug("Generated {} embeddings, each with {} dimensions",
                 vectors.size(),
                 vectors.size() > 0 ? vectors.get(0).length : 0);

        return vectors;
    }

    /**
     * Returns the maximum context length supported by this embedding model.
     *
     * <p><b>Revision 1.1:</b> This method enables dynamic batch size calculation
     * based on the actual model's capabilities. Different models have different
     * context windows (e.g., 2048, 8192, 32768 tokens).</p>
     *
     * <p>Use this value to ensure texts fit within the model's context window
     * before generating embeddings. If a text exceeds this length, it should
     * be either summarized or truncated.</p>
     *
     * @return the maximum number of tokens this model can process, or
     *         {@value DEFAULT_CONTEXT_LENGTH} if not available
     */
    public Integer getContextLength() {
        if (modelEmbedding != null && modelEmbedding.getContextLength() != null) {
            return modelEmbedding.getContextLength();
        }

        log.debug("Model context length not available, using default: {}",
                 DEFAULT_CONTEXT_LENGTH);
        return DEFAULT_CONTEXT_LENGTH;
    }

    /**
     * Returns the embedding dimension for this model.
     *
     * <p>This returns the default vector size generated by the model.
     * Note that the actual dimension used may differ if the library
     * configuration specifies a different value (via
     * {@link bor.tools.simplerag.dto.LibraryDTO#getEmbeddingDimension()}).</p>
     *
     * @return the number of dimensions in embedding vectors, or null if not available
     */
    public Integer getEmbeddingDimension() {
        if (modelEmbedding != null) {
            return modelEmbedding.getEmbeddingDimension();
        }
        return null;
    }

    /**
     * Validates if this context is ready for use.
     *
     * @return true if the context has a valid service and model name
     */
    public boolean isValid() {
        return llmService != null && modelName != null;
    }

    /**
     * Returns a string representation of this context.
     *
     * @return a string with model name, service type, and context length
     */
    @Override
    public String toString() {
        return String.format("EmbeddingContext[model=%s, service=%s, contextLength=%d, valid=%s]",
                           modelName,
                           llmService != null ? llmService.getClass().getSimpleName() : "null",
                           getContextLength(),
                           isValid());
    }
    
}// End of EmbeddingContext.java
