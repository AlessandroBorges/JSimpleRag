/**
 * 
 */
package bor.tools.simplerag.entity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A Wrapper for metadata information.
 */
public class Metadata extends LinkedHashMap<String, Object> {
    
    
    private static final String URL2 = "url";
    private static final String NOME_DOCUMENTO = "nome_documento";
    private static final long serialVersionUID = 1L;

    public Metadata() {
	super();
    }

    public Metadata(int initialCapacity) {
	super(initialCapacity);
    }

    public Metadata(int initialCapacity, float loadFactor) {
	super(initialCapacity, loadFactor);
    }

    public Metadata(java.util.Map<? extends String, ? extends Object> m) {
	super(m);
    }
    
    public Metadata addMetadata(Map<String, Object> meta) {
	this.putAll(meta);
	return this;
    }
    
    public Metadata addMetadata(String key, Object value) {
	this.put(key, value);
	return this;
    }

    public void setNomeDocumento(String string) {
	this.put(NOME_DOCUMENTO, string);	
    }
    
    public String getNomeDocumento() {
	Object obj = this.get(NOME_DOCUMENTO);
	return obj != null ? obj.toString() : null;
    }
    
    public void setUrl(String url) {
	this.put(URL2, url);
    }
    
    public String getUrl() {
	Object obj = this.get(URL2);
	return obj != null ? obj.toString() : null;
    }

}
