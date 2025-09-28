package bor.tools.simplerag.entity.enums;

/**
 * Enum representing the type of embedding stored in the system. Maps to
 * PostgreSQL enum tipo_embedding ('documento', 'capitulo', 'trecho')
 */
public enum TipoEmbedding {
    /**
     * Document-level embedding (entire document)
     */
    DOCUMENTO("documento"),

    /**
     * Chapter-level embedding (~8k tokens)
     */
    CAPITULO("capitulo"),

    /**
     * Chunk-level embedding (~2k tokens)
     */
    TRECHO("trecho"),

    /**
     * Q&A embedding (for question-answer pairs)
     */
    PERGUNTAS_RESPOSTAS("perguntas_respostas"),

    /**
     * Summary embedding (for summarized content)
     */
    RESUMO("resumo"),
    
    /**
     * Metadata
     */
    METADATA("metadata"),
    /**
     * Other types of embeddings
     */
    OUTROS("outros");

    /**
     * Valor persistente
     */
    private final String dbValue;

    /**
     * 
     * @param dbValue
     */
   private TipoEmbedding(String dbValue) {
	this.dbValue = dbValue;
    }

    public String getDbValue() {
	return dbValue;
    }

    /**
     * Get enum from database value
     */
    public static TipoEmbedding fromDbValue(String dbValue) {
	for (TipoEmbedding tipo : values()) {
	    if (tipo.dbValue.equals(dbValue))
		return tipo;
	}
	throw new IllegalArgumentException("Unknown tipo_embedding value: " + dbValue);
    }
}