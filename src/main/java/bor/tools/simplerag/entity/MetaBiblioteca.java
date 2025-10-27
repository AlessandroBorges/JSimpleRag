package bor.tools.simplerag.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.persistence.Transient;

/**
 * Metadata class for Library and Document entities. This class must include the
 * key 'language' for document processing. Optional keys include
 * 'embedding_model' and 'embedding_dimension'.
 * <p>
 * Example usage:
 * 
 * <pre>
 * {
 *   "language": "en",
 *   "embedding_model": "text-embedding-3-small",
 *   "embedding_dimension": 1536
 *   "completion_model_qa": "gpt-4o",
 * }
 * </pre>
 *
 * @author bor
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetaBiblioteca extends Metadata {
    
    private static final long serialVersionUID = -1309098035532919716L;
    
    protected static final String COMPLETION_MODEL_QA_KEY = "completion_model_qa";
    protected static final String MAX_TOKENS_KEY = "max_tokens";
    protected static final String DESCRICAO = "descricao";
    protected static final String LANG_KEY = "language";
    protected static final String MODEL_KEY = "embedding_model";
    protected static final String DIMENSION_KEY = "embedding_dimension";

    /**
     * Default constructor.
     */
    public MetaBiblioteca() {
	super();
    }

    /**
     * Constructor with language, embedding model, and embedding dimension.
     *
     * @param language           the language for document processing
     * @param embeddingModel     the embedding model name
     * @param embeddingDimension the dimension of the embedding
     */
    public MetaBiblioteca(String language, String embeddingModel, Integer embeddingDimension) {
	super();
	super.put(LANG_KEY, language);
	super.put(MODEL_KEY, embeddingModel);
	super.put(DIMENSION_KEY, embeddingDimension);
    }

    /**
     * Sets the language in the metadata.
     *
     * @param language the language to set
     */
    @Transient
    public void setLanguage(String language) {
	super.put(LANG_KEY, language);
    }

    /**
     * Gets the language from the metadata.
     *
     * @return the language string, or null if not set
     */
    @Transient
    public String getLanguage() {
	return super.getOrDefault(LANG_KEY, "pt_br").toString();
    }

    /**
     * Sets the embedding model in the metadata.
     *
     * @param embeddingModel the embedding model to set
     */
    @Transient
    public void setEmbeddingModel(String embeddingModel) {
	super.put(MODEL_KEY, embeddingModel);
    }

    /**
     * Gets the embedding model from the metadata.
     *
     * @return the embedding model string, or null if not set
     */
    @Transient
    public String getEmbeddingModel() {
	Object model = this.get(MODEL_KEY);
	return model != null ? model.toString() : null;
    }

    
    /**
     * Sets the embedding dimension in the metadata.
     *
     * @param embeddingDimension the embedding dimension to set
     */
    @Transient
    public void setEmbeddingDimension(Integer embeddingDimension) {
	super.put(DIMENSION_KEY, embeddingDimension);
    }    
    
    /**
     * Gets the embedding dimension from the metadata.
     *
     * @return the embedding dimension as Integer, or null if not set or invalid
     */
    @Transient
    public Integer getEmbeddingDimension() {
	Object dim = this.get(DIMENSION_KEY);
	if (dim instanceof Integer)
	    return (Integer) dim;
	else if (dim instanceof String) {
	    try {
		return Integer.parseInt((String) dim);
	    } catch (NumberFormatException e) {
		return null;
	    }
	}
	return null;
    }

    /**
     * Sets the description in the metadata.
     *
     * @param descricao the description to set
     */
    @Transient
    public void setDescricao(String descricao) {
	super.put(DESCRICAO, descricao);
    }

    /**
     * Gets the description from the metadata.
     *
     * @return the description string, or null if not set
     */
    @Transient
    public String getDescricao() {
	Object desc = this.get(DESCRICAO);
	return desc != null ? desc.toString() : null;
    }


   /**
    * Suggested Completion Model for QA creation 
    * @param model the completion model to set
    */
   @Transient 
   public void setCompletionQAModel(String model) {
       	super.put(COMPLETION_MODEL_QA_KEY, model);
   }
   
   
   /**
    * Gets the suggested Completion Model for QA from the metadata.
    *
    * @return the completion model string, or null if not set
    */
   @Transient
   public String getCompletionQAModel() {
       	return getString(COMPLETION_MODEL_QA_KEY);       	
   }
    /**
     * Gets the max tokens from the metadata.
     *
     * @return the max tokens as Integer, or null if not set or invalid
     */
    @Transient
    public Integer getMaxTokens() {
	return getInteger(MAX_TOKENS_KEY);
    }

    /**
     * Sets the max tokens in the metadata.
     *
     * @param maxTokens the max tokens to set
     */
    @Transient
    public void setMaxTokens(Integer maxTokens) {
	super.put(MAX_TOKENS_KEY, maxTokens);
    }

}