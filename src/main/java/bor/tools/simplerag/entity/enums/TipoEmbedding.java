package bor.tools.simplerag.entity.enums;

import java.text.Normalizer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

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
     * Metadata embedding (for metadata information)
     */
    METADADOS("metadados"),
    
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

   @JsonValue
    public String getDbValue() {
	return dbValue;
    }

    /**
     * Get enum from string value, ignoring accents and leading/trailing spaces
     * @param tipo_ - string representation
     */
    @JsonCreator
    public static TipoEmbedding fromString(String tipo_) {
	tipo_ = Normalizer.normalize(tipo_.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	for (TipoEmbedding tipo : values()) {
	    if (tipo.dbValue.equals(tipo_))
		return tipo;
	}
	throw new IllegalArgumentException("Unknown tipo_embedding value: " + tipo_);
    }
}