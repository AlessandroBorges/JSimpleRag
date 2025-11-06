package bor.tools.simplerag.entity;

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

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
    
    /**
     * All metadata tags used in MetaDoc.
     */
    private static final String[] ALL_TAGS = {URL_KEY,
	    NOME_DOCUMENTO_KEY,
	    CAPITULO_KEY,
	    DESCRICAO_KEY,
	    AREA_CONHECIMENTO_KEY,
	    PALAVRAS_CHAVE_KEY,
	    AUTOR_KEY,
	    DATA_PUBLICACAO_KEY,
	    CHECKSUM_KEY,
	    ISBN_KEY
    };
    
    private static Set<String> allTagsSet;
    
    static {
	allTagsSet = Set.of(ALL_TAGS);
    }
        
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
	return this.getString(URL_KEY);
	
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
	return getString(CAPITULO_KEY);	
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
	return this.getString(DESCRICAO_KEY);	
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
	return getString(AREA_CONHECIMENTO_KEY);	
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
	return this.getString(PALAVRAS_CHAVE_KEY);
	
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
    
   
    @Override
    public String toString() {
	StringBuilder builder = new StringBuilder();
	builder.append("Metadados [\n ");
	if (getNomeDocumento() != null) {
	    builder.append("nome documento : ");
	    builder.append(getNomeDocumento());
	    builder.append("\n ");
	}
	if (getUrl() != null) {
	    builder.append("url :");
	    builder.append(getUrl());
	    builder.append("\n ");
	}
	if (getCapitulo() != null) {
	    builder.append("capitulo : ");
	    builder.append(getCapitulo());
	    builder.append("\n ");
	}
	if (getDescricao() != null) {
	    builder.append("descricao : ");
	    builder.append(getDescricao());
	    builder.append("\n ");
	}
	if (getAreaConhecimento() != null) {
	    builder.append("area conhecimento : ");
	    builder.append(getAreaConhecimento());
	    builder.append("\n ");
	}
	if (getPalavrasChave() != null) {
	    builder.append("palavras chave : ");
	    builder.append(getPalavrasChave());
	    builder.append("\n ");
	}
	if (getAutor() != null) {
	    builder.append("autor : ");
	    builder.append(getAutor());
	    builder.append("\n ");
	}
	if (getDataPublicacao() != null) {
	    builder.append(" data publicacao : ");
	    builder.append(this.getDataPublicacao());
	    builder.append("\n ");
	}	
	
	for(var entry : this.entrySet()) {
	    String key = entry.getKey();
	    if (!allTagsSet.contains(key)) {		
		builder.append(key);
		builder.append(" : ");
		builder.append(entry.getValue());
		builder.append("\n ");
	    }
	}
	
	builder.append("\n]\n");
	return builder.toString();
    }
    
   

}