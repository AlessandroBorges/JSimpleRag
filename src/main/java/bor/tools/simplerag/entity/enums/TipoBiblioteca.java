package bor.tools.simplerag.entity.enums;

import java.text.Normalizer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TipoBiblioteca {  
    PUBLICO("publico"),    
    PESSOAL("pessoal"),        
    CHATBOT("chat"),    
    PROJETO("projeto")    
    ;
    
    private final String dbValue;
    
    TipoBiblioteca(String string) {
	this.dbValue = string;
    }

    @JsonValue
    public String getDbValue() {
	return dbValue;
    }
    
    /**
     * Get enum from string, case insensitive, ignoring accents and leading/trailing spaces
     * 
     * @param tipo_ - string representation
     * @return TipoBiblioteca enum
     */
    @JsonCreator
    public static TipoBiblioteca fromString(String tipo_) {
	tipo_ = Normalizer.normalize(tipo_.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	for (TipoBiblioteca tipo : TipoBiblioteca.values()) {
	    if (tipo.getDbValue().equalsIgnoreCase(tipo_)) {
		return tipo;
	    }
	}
	throw new IllegalArgumentException("Unknown dbValue: " + tipo_);
    }
    
    /**
     * 
     * @param name
     * @return
     */
    public static TipoBiblioteca fromName(String name) {
	return TipoBiblioteca.fromString(name);
    }
    
    /**
     * 
     */
   public String toString() {
       return this.dbValue;
   }
    
}