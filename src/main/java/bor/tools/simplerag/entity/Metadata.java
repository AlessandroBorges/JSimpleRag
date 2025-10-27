package bor.tools.simplerag.entity;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.persistence.Transient;

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
    
    protected static final String DATA_MAP_KEY = "dataMapKey";

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
    @Transient
    @JsonIgnore
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
    @Transient
    @JsonIgnore
    public Metadata addMetadata(String key, Object value) {
	this.put(key, value);
	return this;
    }
    
    /**
     * Store a map of additional data in the metadata. <br>
     * Useful for storing arbitrary key-value pairs.
     * @param dataMap
     */
    /*
     * @Transient
     * 
     * @JsonIgnore public void addDataMap(Map<String, Object> dataMap) {
     * this.put(DATA_MAP_KEY, dataMap); }
     */
    
    /**
     * Retrieve the map of additional data from the metadata.
     * @return the data map, or null if not set
     */
    /*
     * @SuppressWarnings("unchecked")
     * 
     * @Transient
     * 
     * @JsonIgnore public Map<String, Object> getDataMap() { Map<String, Object>
     * dataMap = (Map<String, Object>) this.get(DATA_MAP_KEY); return dataMap; }
     */
    
    /**
     * Retrieves a string value from the metadata by key.
     * @param key the key for the metadata entry
     * @return the string value, or null if not found
     */
    @Transient
    @JsonIgnore
    public String getString(String key) {
	Object value = this.get(key);
	return value != null ? value.toString() : null;
    }
    
    /**
     * Retrieves a numeric value from the metadata by key.
     * If the value is not a Number, attempts to parse it as a Number.
     * @param key the key for the metadata entry
     * @return the Number value, or null if not found or not parsable
     */
    @Transient
    @JsonIgnore
    public Number getNumber(String key) {
	Object value = this.get(key);
	if (value instanceof Number)
	    return (Number) value;
	return parseNumber(value);
    }
    
    /**
     * Retrieves an Integer value from the metadata by key.
     * @param key the key for the metadata entry
     * @return the Integer value, or null if not found or not parsable
     */
    @Transient
    @JsonIgnore
    public Integer getInteger(String key) {
	Number num = getNumber(key);
	return num != null ? num.intValue() : null;
    }
    
    /**
     * Retrieves a Long value from the metadata by key.
     * @param key the key for the metadata entry
     * @return the Long value, or null if not found or not parsable
     */	
    @Transient
    @JsonIgnore
    public Long getLong(String key) {
	Number num = getNumber(key);
	return num != null ? num.longValue() : null;
    }
    
    /**
     * Retrieves a Double value from the metadata by key.
     * @param key the key for the metadata entry
     * @return the Double value, or null if not found or not parsable
     */
    @Transient
    @JsonIgnore
    public Double getDouble(String key) {
	Number num = getNumber(key);
	return num != null ? num.doubleValue() : null;	
    }
    
    /**
     * Retrieves a Float value from the metadata by key.
     * @param key the key for the metadata entry
     * @return the Float value, or null if not found or not parsable
     */
    @Transient
    @JsonIgnore
    public Float getFloat(String key) {
	Number num = getNumber(key);
	return num != null ? num.floatValue() : null;
    }
    
    /**
     * Parses an object into a Number if possible.
     * @param value the object to parse
     * @return the parsed Number, or null if parsing fails
     */
    @Transient
    @JsonIgnore
    protected Number parseNumber(Object value) {
	if (value == null)
	    return null;

	if (value instanceof Number)
	    return (Number) value;
	try {
	    if (value.toString().contains(".")) {
		return Double.parseDouble(value.toString());
	    } else {
		return Long.parseLong(value.toString());
	    }
	} catch (Exception e) {
	    return null;
	}
    }
    
    /**
     * Checks if the metadata is empty.
     * @return true if the metadata has no entries, false otherwise
     */
    @Transient
    @JsonIgnore
    public boolean isEmpty() {	
	return super.isEmpty();	
    }
    

}