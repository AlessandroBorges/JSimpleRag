package bor.tools.simplerag.service.processing;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 *  Generation flags for document processing.
 *  <p>Defines how text and metadata embeddings are generated
 *    during document processing. Options include generating 
 *    full text and metadata embeddings, only one of the two,
 *    splitting them, or auto-detecting based on content.
 *    </p>
 *    	
 *    
 */
public enum GenerationFlag {
         /**
          * Generate embeddings merging full text and metadata.
          */
        FULL_TEXT_METADATA(1),
        /**
         * Generate only metadata embeddings.
         */
        ONLY_METADATA(2),
        /**
	 * Default. Generate only full text embeddings.
	 */
        ONLY_TEXT(3),
        /**
	 * Generate embeddings by splitting text and metadata.
	 */
        SPLIT_TEXT_METADATA(4),
        /**
         * Auto-detect generation strategy based on content.<br>
         * This option allows the system to choose the best approach per document.
         * Some data sources may benefit from different strategies.<br>
         * For example:<br>
         * <li>A table of contents may need only metadata embeddings.
         * <li>A narrative chapter may need only text embeddings.
         * <li>A technical appendix may benefit from full text + metadata embeddings.
         * <li> An index may need split text and metadata embeddings.
         * <li> A table with plain data may not need any embeddings at all.
         */
        AUTO(5);

        private final int value;

        GenerationFlag(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
        
        /**
	 * Create GenerationFlag from various input types. <br>
	 * Handles String names, Integer values, and direct GenerationFlag inputs.<br>
	 * Null input defaults to ONLY_TEXT.	
	 * 
	 * @param value Input value (String, Integer, or GenerationFlag)
	 * @return Corresponding GenerationFlag
	 * @throws IllegalArgumentException if input is invalid
	 */
        @JsonCreator
        public static GenerationFlag fromValue(Object value) {
            if (value == null) {
        	 return ONLY_TEXT;
            }
            if (value instanceof GenerationFlag) {
		return (GenerationFlag) value;
	    }
            if (value instanceof String) {
        	return GenerationFlag.valueOf((String) value);
            }
            if (value instanceof Integer intValue) {            
        	for (GenerationFlag flag : GenerationFlag.values()) {
        	    if (flag.getValue() == intValue) {
        		return flag;
        	    }
        	}
	    }
	    throw new IllegalArgumentException("Invalid GenerationFlag value: " + value);
	}
    }