package bor.tools.simplerag.entity;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A wrapper class for document metadata information, extending Metadata to store key-value pairs.
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetaDoc extends Metadata {


    private static final String URL = "url";
    private static final String NOME_DOCUMENTO = "nome_documento";
    private static final String CAPITULO = "capitulo";
    private static final String DESCRICAO = "descricao";
    private static final String AREA_CONHECIMENTO = "area_conhecimento";
    private static final String PALAVRAS_CHAVE = "palavras_chave";
    private static final String AUTOR = "autor";
    private static final String DATA_PUBLICACAO = "data_publicacao";
    private static final long serialVersionUID = 1L;

    
    public MetaDoc() {
	super();
    }
    
    public MetaDoc(Map<? extends String, ? extends Object> m) {
	super(m==null ? Map.of() : m);	
    }
    
    /**
     * Sets the document name.
     * @param nomeDocumento the document name to set
     */
    public void setNomeDocumento(String nomeDocumento) {
	this.put(NOME_DOCUMENTO, nomeDocumento);	
    }
    
    /**
     * Gets the document name.
     * @return the document name, or null if not set
     */
    public String getNomeDocumento() {
	Object obj = this.get(NOME_DOCUMENTO);
	return obj != null ? obj.toString() : null;
    }
    
    /**
     * Sets the URL.
     * @param url the URL to set
     */
    public void setUrl(String url) {
	this.put(URL, url);
    }
    
    /**
     * Gets the URL.
     * @return the URL, or null if not set
     */
    public String getUrl() {
	Object obj = this.get(URL);
	return obj != null ? obj.toString() : null;
    }

    /**
     * Sets the chapter or subtitle.
     * @param capitulo the chapter or subtitle to set
     */
    public void setCapitulo(String capitulo) {
	this.put(CAPITULO, capitulo);
    }
    
    /**
     * Gets the chapter or subtitle.
     * @return the chapter or subtitle, or null if not set
     */
    public String getCapitulo() {
	Object obj = this.get(CAPITULO);
	return obj != null ? obj.toString() : null;
    }

    /**
     * Sets the description of the document.
     * @param descricao the description to set
     */
    public void setDescricao(String descricao) {
	this.put(DESCRICAO, descricao);
    }
    
    /**
     * Gets the description of the document.
     * @return the description, or null if not set
     */
    public String getDescricao() {
	Object obj = this.get(DESCRICAO);
	return obj != null ? obj.toString() : null;
    }

    /**
     * Sets the knowledge area.
     * @param areaConhecimento the knowledge area to set
     */
    public void setAreaConhecimento(String areaConhecimento) {
	this.put(AREA_CONHECIMENTO, areaConhecimento);
    }
    
    /**
     * Gets the knowledge area.
     * @return the knowledge area, or null if not set
     */
    public String getAreaConhecimento() {
	Object obj = this.get(AREA_CONHECIMENTO);
	return obj != null ? obj.toString() : null;
    }

    /**
     * Sets the keywords for accessing this document.
     * @param palavrasChave the keywords to set
     */
    public void setPalavrasChave(String palavrasChave) {
	this.put(PALAVRAS_CHAVE, palavrasChave);
    }
    
    /**
     * Gets the keywords for accessing this document.
     * @return the keywords, or null if not set
     */
    public String getPalavrasChave() {
	Object obj = this.get(PALAVRAS_CHAVE);
	return obj != null ? obj.toString() : null;
    }

    /**
     * Sets the author.
     * @param autor the author to set
     */
    public void setAutor(String autor) {
	this.put(AUTOR, autor);
    }
    
    /**
     * Gets the author.
     * @return the author, or null if not set
     */
    public String getAutor() {
	Object obj = this.get(AUTOR);
	return obj != null ? obj.toString() : null;
    }

    /**
     * Sets the publication date.
     * @param dataPublicacao the publication date to set
     */
    public void setDataPublicacao(String dataPublicacao) {
	this.put(DATA_PUBLICACAO, dataPublicacao);
    }
    
    /**
     * Gets the publication date.
     * @return the publication date, or null if not set
     */
    public String getDataPublicacao() {
	Object obj = this.get(DATA_PUBLICACAO);
	return obj != null ? obj.toString() : null;
    }

}
