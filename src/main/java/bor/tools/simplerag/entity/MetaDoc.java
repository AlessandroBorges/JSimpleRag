package bor.tools.simplerag.entity;

import java.util.Date;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import bor.tools.utils.RAGUtil;
import jakarta.persistence.Transient;

/**
 * A wrapper class for document metadata information, extending Metadata to store key-value pairs.
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetaDoc extends Metadata {
    private static final long serialVersionUID = 1L;

    private static final String URL_KEY = "url";
    private static final String NOME_DOCUMENTO_KEY = "nome_documento";
    private static final String CAPITULO_KEY = "capitulo";
    private static final String DESCRICAO_KEY = "descricao";
    private static final String AREA_CONHECIMENTO_KEY = "area_conhecimento";
    private static final String PALAVRAS_CHAVE_KEY = "keywords";
    private static final String AUTOR_KEY = "autor";
    private static final String DATA_PUBLICACAO_KEY = "data_publicacao";
    private static final String CHECKSUM_KEY = "checksum";
    private static final String ISBN_KEY = "isbn";
    private static final String DATA_PUBLICAO_KEY = "data_publicacao";

        
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
    @JsonIgnore
    @Transient
    public void setNomeDocumento(String nomeDocumento) {
	this.put(NOME_DOCUMENTO_KEY, nomeDocumento);	
    }
    
    /**
     * Gets the document name.
     * @return the document name, or null if not set
     */
    @JsonIgnore
    @Transient
    public String getNomeDocumento() {
	Object obj = this.get(NOME_DOCUMENTO_KEY);
	return obj != null ? obj.toString() : null;
    }
    
    /**
     * Sets the URL.
     * @param url the URL to set
     */
    @JsonIgnore
    @Transient
    public void setUrl(String url) {
	this.put(URL_KEY, url);
    }
    
    /**
     * Gets the URL.
     * @return the URL, or null if not set
     */
    @JsonIgnore
    @Transient
    public String getUrl() {
	Object obj = this.get(URL_KEY);
	return obj != null ? obj.toString() : null;
    }

    /**
     * Sets the chapter or subtitle.
     * @param capitulo the chapter or subtitle to set
     */
    @JsonIgnore
    @Transient
    public void setCapitulo(String capitulo) {
	this.put(CAPITULO_KEY, capitulo);
    }
    
    /**
     * Gets the chapter or subtitle.
     * @return the chapter or subtitle, or null if not set
     */
    @JsonIgnore
    @Transient
    public String getCapitulo() {
	Object obj = this.get(CAPITULO_KEY);
	return obj != null ? obj.toString() : null;
    }

    /**
     * Sets the description of the document.
     * @param descricao the description to set
     */
    @JsonIgnore
    @Transient
    public void setDescricao(String descricao) {
	this.put(DESCRICAO_KEY, descricao);
    }
    
    /**
     * Gets the description of the document.
     * @return the description, or null if not set
     */
    @JsonIgnore
    @Transient
    public String getDescricao() {
	Object obj = this.get(DESCRICAO_KEY);
	return obj != null ? obj.toString() : null;
    }

    /**
     * Sets the knowledge area.
     * @param areaConhecimento the knowledge area to set
     */
    public void setAreaConhecimento(String areaConhecimento) {
	this.put(AREA_CONHECIMENTO_KEY, areaConhecimento);
    }
    
    /**
     * Gets the knowledge area.
     * @return the knowledge area, or null if not set
     */
    public String getAreaConhecimento() {
	Object obj = this.get(AREA_CONHECIMENTO_KEY);
	return obj != null ? obj.toString() : null;
    }

    /**
     * Sets the keywords for accessing this document.
     * 
     * @param palavrasChave the keywords to set
     */
    @JsonIgnore
    @Transient
    public void setPalavrasChave(String palavrasChave) {
	this.put(PALAVRAS_CHAVE_KEY, palavrasChave);
    }
    
    /**
     * Gets the keywords for accessing this document.
     * @return the keywords, or null if not set
     */
    @JsonIgnore
    @Transient
    public String getPalavrasChave() {
	Object obj = this.get(PALAVRAS_CHAVE_KEY);
	return obj != null ? obj.toString() : null;
    }

    /**
     * Sets the author.
     * @param autor the author to set
     */
    @JsonIgnore
    @Transient
    public void setAutor(String autor) {
	this.put(AUTOR_KEY, autor);
    }
    
    /**
     * Gets the author.
     * @return the author, or null if not set
     */
    @JsonIgnore
    @Transient
    public String getAutor() {
	Object obj = this.get(AUTOR_KEY);
	return obj != null ? obj.toString() : null;
    }

    /**
     * Sets the publication date.
     * @param dataPublicacao the publication date to set
     */
    @JsonIgnore
    @Transient
    public void setDataPublicacao(String dataPublicacao) {
	this.put(DATA_PUBLICACAO_KEY, dataPublicacao);
    }
    
    /**
     * Gets the publication date.
     * @return the publication date, or null if not set
     */
    @JsonIgnore
    @Transient
    public String getDataPublicacao() {
	Object obj = this.get(DATA_PUBLICACAO_KEY);
	return obj != null ? obj.toString() : null;
    }
    
    @JsonIgnore
    @Transient
    public void setChecksum(String checksum) {
	this.put(CHECKSUM_KEY, checksum);
    }
    
    @JsonIgnore
    @Transient
    public String getChecksum() {
	Object obj = this.get(CHECKSUM_KEY);
	return obj != null ? obj.toString() : null;
    }
    
    @JsonIgnore
    @Transient
    public void setIsbn(String isbn) {
	this.put(ISBN_KEY, isbn);
    }
    
    @JsonIgnore
    @Transient
    public String getIsbn() {
	Object obj = this.get(ISBN_KEY);
	return obj != null ? obj.toString() : null;
    }
    
    
    @JsonIgnore
    @Transient
    public void setDataPublicao(Date dataPublicao) {
	String dataStr = RAGUtil.format(dataPublicao);
	this.put(DATA_PUBLICAO_KEY, dataStr);	
    }
    
    @JsonIgnore
    @Transient
    public void setDataPublicao(String dataPublicao) {
	this.put(DATA_PUBLICAO_KEY, dataPublicao);	
    }
    
    @JsonIgnore
    @Transient
    public String getDataPublicao() {
	Object obj = this.get(DATA_PUBLICAO_KEY);
	return obj != null ? obj.toString() : null;	
    }

}