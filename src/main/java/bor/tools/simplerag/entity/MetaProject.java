/**
 * 
 */
package bor.tools.simplerag.entity;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Metadados específicos para "Projeto".
 */
	
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetaProject extends MetaBiblioteca {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String CHAT_KEY = "chats";

    /**
     * 
     */
    public MetaProject() {
    }

    /**
     * @param language
     * @param embeddingModel
     * @param embeddingDimension
     */
    public MetaProject(String language, String embeddingModel, Integer embeddingDimension) {
	super(language, embeddingModel, embeddingDimension);
    }

    /**
     * Adiciona um chat aos metadados do projeto.
     * 
     * @param chat - chat a ser adicionado
     * @return true se o chat foi adicionado, false caso contrário
     */
    @SuppressWarnings("unchecked")
    public boolean addChat(Chat chat) {
	if (chat == null || chat.getId() == null)
	    return false;
	    
	 if( this.get(CHAT_KEY) == null) {
	     this.put(CHAT_KEY, new java.util.ArrayList<UUID>());
	 }
	 return ((java.util.ArrayList<UUID>) this.get(CHAT_KEY)).add(chat.getId());	
    }

    /**
     * Remove um chat dos metadados do projeto.
     * 
     * @param chat - chat a ser removido
     * @return true se o chat foi removido, false caso contrário
     */  
    public boolean removeChat(Chat chat) {
	if (chat == null || chat.getId() == null || this.get(CHAT_KEY) == null)
	    return false;
	var list = this.get(CHAT_KEY);
	if (list instanceof java.util.List) {
	    return ((java.util.ArrayList<?>) list).remove(chat.getId());
	}
	return false;
    }

    /**
     * Retorna o número de chats associados ao projeto.
     * 
     * @return número de chats ou null se não definido
     */
    public int getNumberOfChats() {
	var list = this.get(CHAT_KEY);
	if (list instanceof java.util.List) {
	    return ((java.util.ArrayList<?>) list).size();
	}
	return 0;
    }
}
