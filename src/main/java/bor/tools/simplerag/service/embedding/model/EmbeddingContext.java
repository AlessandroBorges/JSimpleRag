package bor.tools.simplerag.service.embedding.model;

import java.util.HashMap;
import java.util.Map;

import bor.tools.simplerag.dto.LibraryDTO;
import lombok.Builder;
import lombok.Data;

/**
 * Context object for embedding operations.
 *
 * Encapsulates library configuration, model selection, and metadata
 * for embedding generation operations. Supports hierarchical model
 * resolution: explicit override → library default → global default.
 *
 * @since 0.0.1
 * @see EmbeddingRequest
 */
@Data
@Builder
public class EmbeddingContext {

    /**
     * Library context for the embedding operation.
     * Provides access to library-specific configurations including
     * default models, search weights, and metadata.
     */
    private LibraryDTO library;

    /**
     * Override for embedding model name (optional).
     *
     * If provided, takes precedence over library and global defaults.
     * If null, uses library.defaultEmbeddingModel or global configuration.
     *
     * Example: "nomic-embed-text", "text-embedding-ada-002"
     */
    private String embeddingModelName;

    /**
     * Override for completion model name (optional).
     *
     * Used for Q&A generation and summarization operations.
     * If null, uses global default from LLMServiceManager.getDefaultCompletionModelName().
     *
     * Note: Library does not manage completion models, only embedding models.
     *
     * Example: "qwen3-1.7b", "gpt-4", "mistral"
     */
    private String completionQAModelName;
    
    /**
     * Maximum tokens for Embedding operations.
     * Can less, as 2048 or 512, depending on the model used.
     * 
     * Default is 8192 tokens.
     */	
    @Builder.Default
    private Integer maxTokens = 8192;
    
    /**
     * Dimension of the embedding vectors.
     *
     * Default is 768 dimensions.
     */	
    @Builder.Default
    private Integer embeddingDimension = 768; 

    
    /**
     * Additional metadata for the operation.
     *
     * Can store custom key-value pairs for strategy-specific needs.
     */
    @Builder.Default
    private Map<String, Object> additionalMetadata = new HashMap<>();

    /**
     * Enriches the context with library defaults if not already set.
     *
     * This method should be called after building the context to populate
     * fields from library metadata that weren't explicitly overridden.
     *
     * @return this context for method chaining
     */
    public EmbeddingContext enrichFromLibrary() {
        if (library != null) {
            // Only populate if not explicitly set (check if still equals default)
            if ( mustUpdate(embeddingModelName, library.getEmbeddingModel()) ) {
                embeddingModelName = library.getEmbeddingModel();
            }

            if (mustUpdate(embeddingDimension, library.getEmbeddingDimension())) {
                embeddingDimension = library.getEmbeddingDimension();
            }

            if (mustUpdate(maxTokens, library.getMaxTokens())) {	
                maxTokens = library.getMaxTokens();
            }

            if (mustUpdate(completionQAModelName, library.getCompletionQAModel())) {
                completionQAModelName = library.getCompletionQAModel();
            }
        }
        return this;
    }
    
    /**
     * Determines if an update is needed based on current and new values.
     * @param currentValue
     * @param newValue
     * @return
     */
    private boolean mustUpdate(Object currentValue, Object newValue) {
	// equals case - nothing to do
	if(currentValue == newValue)
	    return false;
	
	// newValue is not empty and currentValue is empty - need to update
	if(isEmpty(currentValue) && !isEmpty(newValue))
	    return true;
	
	// current value and newValues are different 
	if(!isEmpty(currentValue)  && !isEmpty(newValue) && !newValue.equals(currentValue))
	    return true;
	
	return false;
    }
    
    /**
     * checks if the object is null or an empty string
     * @param str
     * @return
     */
    private boolean isEmpty(Object obj) {
	if( obj == null )
	    return true;
	
	if(obj instanceof String s)
	    return s.trim().isEmpty();
	
	return false;
    }
    
    /**
     * Resolves which embedding model to use.
     *
     * Resolution priority:
     * 1. Explicit override (embeddingModelName)
     * 2. Library metadata (library.getEmbeddingModel())
     * 3. Global default (parameter)
     *
     * @param globalDefault Global default model name from configuration
     * @return Resolved embedding model name
     */
    public String resolveEmbeddingModel(String globalDefault) {
        if (embeddingModelName != null && !embeddingModelName.trim().isEmpty()) {
            return embeddingModelName;
        }
        if (library != null && library.getEmbeddingModel() != null) {
            return library.getEmbeddingModel();
        }
        return globalDefault;
    }

    /**
     * Resolves which completion model to use for Q&A generation.
     *
     * Resolution priority:
     * 1. Explicit override (completionQAModelName in context)
     * 2. Library suggestion (library.getCompletionQAModel())
     * 3. Global default (parameter from LLMServiceManager)
     *
     * Note: Unlike embedding models which are library-specific, completion models
     * are optionally suggested by library but ultimately managed by LLMServiceManager.
     * The library can suggest a preferred model for Q&A generation based on the
     * knowledge domain (e.g., legal terminology might prefer a specialized model).
     *
     * @param globalDefault Global default model name from LLMServiceManager
     * @return Resolved completion model name
     */
    public String resolveCompletionModel(String globalDefault) {
        // 1. Check explicit override
        if (completionQAModelName != null && !completionQAModelName.trim().isEmpty()) {
            return completionQAModelName;
        }

        // 2. Check library suggestion (populated via enrichFromLibrary())
        // This is already populated in completionQAModelName by enrichFromLibrary()

        // 3. Use global default
        return globalDefault;
    }

    /**
     * Adds a metadata entry to the context.
     *
     * @param key Metadata key
     * @param value Metadata value
     */
    public void addMetadata(String key, Object value) {
        if (this.additionalMetadata == null) {
            this.additionalMetadata = new HashMap<>();
        }
        this.additionalMetadata.put(key, value);
    }

    /**
     * Retrieves a metadata value by key.
     *
     * @param key Metadata key
     * @return Metadata value, or null if not found
     */
    public Object getMetadata(String key) {
        return this.additionalMetadata != null
            ? this.additionalMetadata.get(key)
            : null;
    }

    /**
     * Checks if the context has a specific metadata key.
     *
     * @param key Metadata key to check
     * @return true if key exists, false otherwise
     */
    public boolean hasMetadata(String key) {
        return this.additionalMetadata != null
            && this.additionalMetadata.containsKey(key);
    }

    /**
     * Creates a simple context with just a library.
     *
     * Automatically enriches the context with library metadata (models, dimensions, tokens).
     *
     * @param library Library context
     * @return EmbeddingContext with library defaults applied
     */
    public static EmbeddingContext fromLibrary(LibraryDTO library) {	
        var obj = EmbeddingContext.builder()
                .library(library)
                .build()
                .enrichFromLibrary();        
        obj.enrichFromLibrary();        
        return obj;
    }

    /**
     * Creates a context with library and explicit embedding model override.
     *
     * The explicit embedding model takes precedence over library defaults.
     *
     * @param library Library context
     * @param embeddingModel Embedding model name (overrides library default)
     * @return EmbeddingContext configured
     */
    public static EmbeddingContext withEmbeddingModel(LibraryDTO library, String embeddingModel) {
        return EmbeddingContext.builder()
                .library(library)
                .embeddingModelName(embeddingModel)
                .build()
                .enrichFromLibrary();
    }
}