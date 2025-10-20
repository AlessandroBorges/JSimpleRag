package bor.tools.simplerag.entity.enums;

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
    
    @JsonCreator
    public static TipoBiblioteca fromString(String dbValue) {
	for (TipoBiblioteca tipo : TipoBiblioteca.values()) {
	    if (tipo.getDbValue().equalsIgnoreCase(dbValue)) {
		return tipo;
	    }
	}
	throw new IllegalArgumentException("Unknown dbValue: " + dbValue);
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