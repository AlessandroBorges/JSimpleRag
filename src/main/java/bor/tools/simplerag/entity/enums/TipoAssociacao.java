package bor.tools.simplerag.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum representing the type of association between User and Library. Maps to
 * PostgreSQL enum tipo_associacao ('proprietario', 'colaborador', 'leitor')
 */
public enum TipoAssociacao {

    /**
     * Owner association (full access)
     */
    PROPRIETARIO("proprietario"),

    /**
     * Collaborator association (edit access)
     */
    COLABORADOR("colaborador"),

    /**
     * Reader association (read-only access)
     */
    LEITOR("leitor");

    /**
     * Persistent value
     */
    private final String dbValue;

    /**
     * 
     * @param dbValue
     */

    private TipoAssociacao(String dbValue) {
	this.dbValue = dbValue;
    }

    @JsonValue
    public String getDbValue() {
	return dbValue;
    }

    @JsonCreator
    public static TipoAssociacao fromString(String dbValue) {
	for (TipoAssociacao tipo : TipoAssociacao.values()) {
	    if (tipo.getDbValue().equalsIgnoreCase(dbValue)) {
		return tipo;
	    }
	}
	throw new IllegalArgumentException("Unknown value for enumeration TipoAssociacao: " + dbValue);
    }

}
