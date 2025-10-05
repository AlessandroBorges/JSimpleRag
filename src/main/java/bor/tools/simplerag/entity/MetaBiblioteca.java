package bor.tools.simplerag.entity;

import java.util.LinkedHashMap;

/**
 * Metadata for Library and Documento entities.
 * Must include the key 'language' for document processing.
 * Optional keys: 'embedding_model' and 'embedding_dimension'.
 *
 * Example:
 * {
 *   "language": "en",
 *   "embedding_model": "text-embedding-3-small",
 *   "embedding_dimension": 1536
 * }
 *
 * @author bor
 *
 */
public class MetaBiblioteca extends LinkedHashMap<String, Object> {
	/**
	 *
	 */
	private static final long serialVersionUID = -1309098035532919716L;
	public static final String lANG_KEY = "language";
	public static final String MODEL_KEY = "embedding_model";
	public static final String DIMENSION_KEY = "embedding_dimension";


    public MetaBiblioteca() {
		super();
	}

	public MetaBiblioteca(String language, String embeddingModel, Integer embeddingDimension) {
		super();
		this.put(lANG_KEY, language);
		this.put(MODEL_KEY, embeddingModel);
		this.put(DIMENSION_KEY, embeddingDimension);
	}

   public String getLanguage() {
	   Object lang = this.get(lANG_KEY);
	   return lang != null ? lang.toString() : null;
   }

   public String getEmbeddingModel() {
	   Object model = this.get(MODEL_KEY);
	   return model != null ? model.toString() : null;
   }

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

   public void setLanguage(String language) {
	   this.put(lANG_KEY, language);
   }

   public void setEmbeddingModel(String embeddingModel) {
	   this.put(MODEL_KEY, embeddingModel);
   }

   public void setEmbeddingDimension(Integer embeddingDimension) {
	   this.put(DIMENSION_KEY, embeddingDimension);
   }
}
