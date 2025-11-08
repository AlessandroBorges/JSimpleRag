package bor.tools.simplerag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llmservice")
@lombok.Data
/**
 * Configuration properties for LLM service.
 * This class holds the configuration for the LLM service, including provider settings, strategy, and failover configuration.
 */
public class LlmServiceProperties {
    
    private ProviderConfig provider;
    private ProviderConfig provider2;
    private String strategy;
    private FailoverConfig failover;
    
    // Getters and setters
    /**
     * Configuration for a provider.
     * Contains settings for LLM, embedding, and API configurations.
     */
    @lombok.Data
    public static class ProviderConfig {
        private Boolean enabled;
        private String name;
        private Boolean use_defaults;
        private LlmConfig llm;
        private EmbeddingConfig embedding;
        private ApiConfig api;
        
        // Getters and setters
    }
    
    /**
     * API configuration.
     * Holds the URL and key for API access.
     */
    @lombok.Data
    public static class ApiConfig {
        private String url;
        private String key;
        
        // Getters and setters
    }
    
    /**
     * Embedding configuration.
     * Specifies the model, dimension, and context length for embeddings.
     */
    @lombok.Data	
    public static class EmbeddingConfig {
        private String model;
        private Integer dimension;
        private Integer embeddingContextLength;
        
        // Getters and setters
    }
    
    /**
     * LLM configuration.
     * Defines the models to use for language model operations.
     */
    @lombok.Data
    public static class LlmConfig {
        private String models;
        
        // Getters and setters
    }
    /**
     * Failover configuration.
     * Sets the maximum retries and timeout for failover scenarios.
     */
    @lombok.Data
    public static class FailoverConfig {
	
        private Integer maxRetries;
        private Integer timeoutSeconds;
        
        // Getters and setters
    }
}