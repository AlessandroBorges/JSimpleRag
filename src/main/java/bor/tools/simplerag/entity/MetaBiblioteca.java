package bor.tools.simplerag.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadata class for Library and Document entities.
 * This class must include the key 'language' for document processing.
 * Optional keys include 'embedding_model' and 'embedding_dimension'.
 * <p>
 * Example usage:
 * <pre>
 * {
 *   "language": "en",
 *   "embedding_model": "text-embedding-3-small",
 *   "embedding_dimension": 1536
 * }
 * </pre>
 *
 * @author bor
 */
	
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetaBiblioteca extends Metadata{
	private static final String DESCRICAO = "descricao";

	/**
	 *
	 */
	private static final long serialVersionUID = -1309098035532919716L;
	
	public static final String lANG_KEY = "language";
	public static final String MODEL_KEY = "embedding_model";
	public static final String DIMENSION_KEY = "embedding_dimension";


    /**
     * Default constructor.
     */
    public MetaBiblioteca() {
		super();
	}

	/**
	 * Constructor with language, embedding model, and embedding dimension.
	 *
	 * @param language the language for document processing
	 * @param embeddingModel the embedding model name
	 * @param embeddingDimension the dimension of the embedding
	 */
	public MetaBiblioteca(String language, String embeddingModel, Integer embeddingDimension) {
		super();
		this.put(lANG_KEY, language);
		this.put(MODEL_KEY, embeddingModel);
		this.put(DIMENSION_KEY, embeddingDimension);
	}

   /**
    * Gets the language from the metadata.
    *
    * @return the language string, or null if not set
    */
   public String getLanguage() {
	   Object lang = this.get(lANG_KEY);
	   return lang != null ? lang.toString() : null;
   }

   /**
    * Gets the embedding model from the metadata.
    *
    * @return the embedding model string, or null if not set
    */
   public String getEmbeddingModel() {
	   Object model = this.get(MODEL_KEY);
	   return model != null ? model.toString() : null;
   }

   /**
    * Gets the embedding dimension from the metadata.
    *
    * @return the embedding dimension as Integer, or null if not set or invalid
    */
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
   public void setDescricao(String descricao) {
	   this.put(DESCRICAO, descricao);
   }
   
   /**
    * Gets the description from the metadata.
    *
    * @return the description string, or null if not set
    */
   public String getDescricao() {
	   Object desc = this.get(DESCRICAO);
	   return desc != null ? desc.toString() : null;
   }
   
   /**
    * Sets the language in the metadata.
    *
    * @param language the language to set
    */
   public void setLanguage(String language) {
	   this.put(lANG_KEY, language);
   }

   /**
    * Sets the embedding model in the metadata.
    *
    * @param embeddingModel the embedding model to set
    */
   public void setEmbeddingModel(String embeddingModel) {
	   this.put(MODEL_KEY, embeddingModel);
   }

   /**
    * Sets the embedding dimension in the metadata.
    *
    * @param embeddingDimension the embedding dimension to set
    */
   public void setEmbeddingDimension(Integer embeddingDimension) {
	   this.put(DIMENSION_KEY, embeddingDimension);
   }
}