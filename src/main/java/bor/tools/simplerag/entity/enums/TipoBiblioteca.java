package bor.tools.simplerag.entity.enums;

public enum TipoBiblioteca {
    COMPARTILHADA("compartilhada"),
    PESSOAL("pessoal"),
    CHATBOT("chat"),
    PROJETO("projeto");
    
    
    private final String dbValue;
    
    TipoBiblioteca(String string) {
	this.dbValue = string;
    }

    public String getDbValue() {
	return dbValue;
    }
    
    public static TipoBiblioteca fromString(String dbValue) {
	for (TipoBiblioteca tipo : TipoBiblioteca.values()) {
	    if (tipo.getDbValue().equalsIgnoreCase(dbValue)) {
		return tipo;
	    }
	}
	throw new IllegalArgumentException("Unknown dbValue: " + dbValue);
    }
    
}
