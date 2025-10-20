package bor.tools.simplerag.entity;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A wrapper class for metadata information, extending LinkedHashMap to store key-value pairs.
 * This class provides convenient getter and setter methods for common metadata fields such as
 * URL, document name, chapter, description, knowledge area, keywords, author, and publication date.
 * It allows for fluent addition of metadata through the addMetadata methods.
 */
	
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Metadata extends LinkedHashMap<String, Object> {
    private static final long serialVersionUID = 1L;
    
    public static final String DATA_MAP_KEY = "dataMapKey";

    /**
     * Constructs an empty Metadata instance with default initial capacity.
     */
    public Metadata() {
	super();
    }

    /**
     * Constructs a Metadata instance containing the mappings from the specified map.
     * @param m the map whose mappings are to be placed in this map
     */
    public Metadata(java.util.Map<? extends String, ? extends Object> m) {
	super(m==null ? Map.of() : m);
    }
    
    /**
     * Adds all entries from the specified map to this metadata.
     * @param meta the map containing metadata entries to add
     * @return this Metadata instance for method chaining
     */
    public Metadata addMetadata(Map<String, Object> meta) {
	if (meta != null && !meta.isEmpty())	
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
     * Store a map of additional data in the metadata. <br>
     * Useful for storing arbitrary key-value pairs.
     * @param dataMap
     */
    public void setDataMap(Map<String, Object> dataMap) {
	this.put(DATA_MAP_KEY, dataMap);
    }
    
    /**
     * Retrieve the map of additional data from the metadata.
     * @return the data map, or null if not set
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDataMap() {
	Map<String, Object> dataMap = (Map<String, Object>) this.get(DATA_MAP_KEY);		
	return dataMap;	
    }
}