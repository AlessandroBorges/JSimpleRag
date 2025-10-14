package bor.tools.simplerag.entity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A wrapper class for metadata information, extending LinkedHashMap to store key-value pairs.
 * This class provides convenient getter and setter methods for common metadata fields such as
 * URL, document name, chapter, description, knowledge area, keywords, author, and publication date.
 * It allows for fluent addition of metadata through the addMetadata methods.
 */
public class Metadata extends LinkedHashMap<String, Object> {
    
    
    private static final String URL = "url";
    private static final String NOME_DOCUMENTO = "nome_documento";
    private static final String CAPITULO = "capitulo";
    private static final String DESCRICAO = "descricao";
    private static final String AREA_CONHECIMENTO = "area_conhecimento";
    private static final String PALAVRAS_CHAVE = "palavras_chave";
    private static final String AUTOR = "autor";
    private static final String DATA_PUBLICACAO = "data_publicacao";
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an empty Metadata instance with default initial capacity.
     */
    public Metadata() {
	super();
    }

    /**
     * Constructs an empty Metadata instance with the specified initial capacity.
     * @param initialCapacity the initial capacity of the map
     */
    public Metadata(int initialCapacity) {
	super(initialCapacity);
    }

    /**
     * Constructs an empty Metadata instance with the specified initial capacity and load factor.
     * @param initialCapacity the initial capacity of the map
     * @param loadFactor the load factor of the map
     */
    public Metadata(int initialCapacity, float loadFactor) {
	super(initialCapacity, loadFactor);
    }

    /**
     * Constructs a Metadata instance containing the mappings from the specified map.
     * @param m the map whose mappings are to be placed in this map
     */
    public Metadata(java.util.Map<? extends String, ? extends Object> m) {
	super(m);
    }
    
    /**
     * Adds all entries from the specified map to this metadata.
     * @param meta the map containing metadata entries to add
     * @return this Metadata instance for method chaining
     */
    public Metadata addMetadata(Map<String, Object> meta) {
	this.putAll(meta);
	return this;
    }
    
    /**
     * Adds a single metadata entry with the specified key and value.
     * @param key the key for the metadata entry
     * @param value the value for the metadata entry
     * @return this Metadata instance for method chaining
     */
    public Metadata addMetadata(String key, Object value) {
	this.put(key, value);
	return this;
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