package bor.tools.simplerag.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import bor.tools.simplellm.LLMConfig;
import bor.tools.simplellm.*;
import bor.tools.simplellm.LLMServiceFactory;
import bor.tools.simplellm.LLMServiceFactory.SERVICE_PROVIDER;

import bor.tools.simplerag.service.llm.LLMServiceManager;
import bor.tools.simplerag.service.llm.LLMServiceStrategy;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration for Multiple LLM Service Providers.
 *
 * Supports primary and secondary LLM providers with fallback strategies.
 * The secondary provider can serve as:
 * - Backup/fallback when primary fails
 * - Complement for specific operations (e.g., different embedding dimensions)
 * - Load balancing target
 *
 * Configuration properties:
 * Primary provider:
 * - llmservice.provider.name
 * - llmservice.provider.llm.models
 * - llmservice.provider.embedding.model
 * - llmservice.provider.embedding.dimension
 * - llmservice.provider.api.url
 * - llmservice.provider.api.key
 *
 * Secondary provider (optional):
 * - llmservice.provider2.enabled
 * - llmservice.provider2.name
 * - llmservice.provider2.llm.models
 * - llmservice.provider2.embedding.model
 * - llmservice.provider2.embedding.dimension
 * - llmservice.provider2.api.url
 * - llmservice.provider2.api.key
 *
 * Strategy configuration:
 * - llmservice.strategy (FAILOVER, ROUND_ROBIN, PRIMARY_ONLY)
 */
@Configuration
@Slf4j
public class MultiLLMServiceConfig {

    private final LLMServiceConfig LLMServiceConfig;

    // ============ Primary Provider Configuration ============

    @Value("${llmservice.provider.name:LM_STUDIO}")
    private String primaryProviderName;

    @Value("${llmservice.provider.llm.models:qwen/qwen3-1.7b}")
    private String primaryLlmModels;

    @Value("${llmservice.provider.embedding.model:text-embedding-nomic-embed-text-v1.5@q8_0}")
    private String primaryEmbeddingModel;

    @Value("${llmservice.provider.embedding.dimension:768}")
    private Integer primaryEmbeddingDimension;

    @Value("${llmservice.provider.api.url:#{null}}")
    private String primaryApiUrl;

    @Value("${llmservice.provider.api.key:#{null}}")
    private String primaryApiKey;

    // ============ Secondary Provider Configuration ============

    @Value("${llmservice.provider2.enabled:false}")
    private Boolean secondaryEnabled;

    @Value("${llmservice.provider2.name:#{null}}")
    private String secondaryProviderName;

    @Value("${llmservice.provider2.llm.models:#{null}}")
    private String secondaryLlmModels;

    @Value("${llmservice.provider2.embedding.model:#{null}}")
    private String secondaryEmbeddingModel;

    @Value("${llmservice.provider2.embedding.dimension:#{null}}")
    private Integer secondaryEmbeddingDimension;

    @Value("${llmservice.provider2.api.url:#{null}}")
    private String secondaryApiUrl;

    @Value("${llmservice.provider2.api.key:#{null}}")
    private String secondaryApiKey;

    // ============ Strategy Configuration ============

    @Value("${llmservice.strategy:FAILOVER}")
    private String strategyName;

    @Value("${llmservice.failover.max-retries:3}")
    private Integer maxRetries;

    @Value("${llmservice.failover.timeout-seconds:30}")
    private Integer timeoutSeconds;

    MultiLLMServiceConfig(LLMServiceConfig LLMServiceConfig) {
        this.LLMServiceConfig = LLMServiceConfig;
    }

    /**
     * Creates the primary LLMService bean.
     * This is the main LLM provider.
     */
    @Bean(name = "primaryLLMService")
    @Primary
    public LLMService primaryLLMService() {
        log.info("Initializing Primary LLMService");
        log.info("  Provider: {}", primaryProviderName);
        log.info("  Models: {}", primaryLlmModels);
        log.info("  Embedding Model: {}", primaryEmbeddingModel);
        log.info("  Embedding Dimension: {}", primaryEmbeddingDimension);

        try {
            LLMService service = createLLMService(
                primaryProviderName,
                primaryEmbeddingModel,
                primaryApiUrl,
                primaryApiKey
            );

            log.info("Primary LLMService initialized successfully");
            return service;

        } catch (Exception e) {
            log.error("Failed to initialize Primary LLMService: {}", e.getMessage(), e);
            throw new IllegalStateException("Could not initialize Primary LLMService", e);
        }
    }

    /**
     * Creates the secondary LLMService bean if enabled.
     * This serves as backup/complement to the primary provider.
     */
    @Bean(name = "secondaryLLMService")
    @ConditionalOnProperty(name = "llmservice.provider2.enabled", havingValue = "true")
    public LLMService secondaryLLMService() {
        log.info("Initializing Secondary LLMService");
        log.info("  Provider: {}", secondaryProviderName);
        log.info("  Models: {}", secondaryLlmModels);
        log.info("  Embedding Model: {}", secondaryEmbeddingModel);
        log.info("  Embedding Dimension: {}", secondaryEmbeddingDimension);

        if (secondaryProviderName == null || secondaryProviderName.isEmpty()) {
            throw new IllegalStateException(
                "Secondary provider enabled but llmservice.provider2.name not configured"
            );
        }

        try {
            LLMService service = createLLMService(
                secondaryProviderName,
                secondaryEmbeddingModel,
                secondaryApiUrl,
                secondaryApiKey
            );

            log.info("Secondary LLMService initialized successfully");
            return service;

        } catch (Exception e) {
            log.error("Failed to initialize Secondary LLMService: {}", e.getMessage(), e);
            throw new IllegalStateException("Could not initialize Secondary LLMService", e);
        }
    }

    /**
     * Creates the LLMServiceManager bean that manages multiple providers.
     * This is the recommended bean to inject in services.
     */
    @Bean
    public LLMServiceManager llmServiceManager(
            LLMService primaryLLMService,
            List<LLMService> allLLMServices) {

        log.info("Initializing LLMServiceManager");
        log.info("  Strategy: {}", strategyName);
        log.info("  Max Retries: {}", maxRetries);
        log.info("  Timeout: {}s", timeoutSeconds);
        log.info("  Total Providers: {}", allLLMServices.size());

        LLMServiceStrategy strategy = parseStrategy(strategyName);

        // Filter out null services (secondary might not be configured)
        List<LLMService> validServices = new ArrayList<>();
        validServices.add(primaryLLMService);

        // Add secondary if it exists and is different from primary
        for (LLMService service : allLLMServices) {
            if (service != primaryLLMService && !validServices.contains(service)) {
                validServices.add(service);
            }
        }

        LLMServiceManager manager = new LLMServiceManager(
            validServices,
            strategy,
            maxRetries,
            timeoutSeconds
        );

        log.info("LLMServiceManager initialized with {} provider(s)", validServices.size());
        return manager;
    }

    /**
     * Configuration properties bean for primary provider.
     */
    @Bean(name = "primaryLLMProperties")
    public LLMProviderProperties primaryLLMProperties() {
        LLMProviderProperties props = new LLMProviderProperties();
        props.setProviderName(primaryProviderName);
        props.setLlmModels(primaryLlmModels);
        props.setEmbeddingModel(primaryEmbeddingModel);
        props.setEmbeddingDimension(primaryEmbeddingDimension);
        props.setApiUrl(primaryApiUrl);
        props.setPrimary(true);
        return props;
    }

    /**
     * Configuration properties bean for secondary provider.
     */
    @Bean(name = "secondaryLLMProperties")
    @ConditionalOnProperty(name = "llmservice.provider2.enabled", havingValue = "true")
    public LLMProviderProperties secondaryLLMProperties() {
        LLMProviderProperties props = new LLMProviderProperties();
        props.setProviderName(secondaryProviderName);
        props.setLlmModels(secondaryLlmModels);
        props.setEmbeddingModel(secondaryEmbeddingModel);
        props.setEmbeddingDimension(secondaryEmbeddingDimension);
        props.setApiUrl(secondaryApiUrl);
        props.setPrimary(false);
        return props;
    }

    // ============ Helper Methods ============

    /**
     * Creates an LLMService instance with given configuration.
     */
    private LLMService createLLMService(String providerName, 
	    				String embeddingModel,
	    				String apiUrl, 
	    				String apiKey) 
    {
        SERVICE_PROVIDER provider = parseProviderName(providerName);
        LLMConfig config = LLMConfig.builder()
        			.baseUrl(apiUrl)
        			.apiToken(apiKey).build();
        
        LLMServiceConfig.parseEmbeddingModelsToArray(embeddingModel, 0, config);
        
        LLMService service = LLMServiceFactory.createLLMService(provider, config);

        
        return service;
    }

    /**
     * Parses provider name from configuration to ProviderEnum.
     */
    private SERVICE_PROVIDER parseProviderName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Provider name cannot be null or empty");
        }

        String normalized = name.trim().toUpperCase()
        	.replace("-", "_")
        	.replace(" ", "_");
        normalized = normalized.replace("LLMSERVICE", "")
        	.replace(".java", "")
        	.replace("java", "");

        try {
            return SERVICE_PROVIDER.fromString	(normalized);
        } catch (IllegalArgumentException e) {
            // Handle common aliases
            switch (normalized) {
                case "LMSTUDIO":
                case "LM_STUDIO":                    
                    return SERVICE_PROVIDER.LM_STUDIO;
                case "OPENAI":
                case "GPT":
                    return SERVICE_PROVIDER.OPENAI;
                case "OLLAMA":
                    return SERVICE_PROVIDER.OLLAMA;
                case "ANTHROPIC":
                case "CLAUDE":
                    return SERVICE_PROVIDER.ANTHROPIC;
                    /*
                case "GEMINI":
                case "GOOGLE":
                    return SERVICE_PROVIDER.GEMINI;
                case "COHERE":
                    return SERVICE_PROVIDER.COHERE;
                case "HUGGINGFACE":
                case "HF":
                    return SERVICE_PROVIDER.HUGGINGFACE;
                    */
                default:
                    throw new IllegalArgumentException(
                        "Unknown LLM provider: " + name
                    );
            }
        }
    }

    /**
     * Parses strategy name from configuration.
     */
    private LLMServiceStrategy parseStrategy(String name) {
        if (name == null || name.isEmpty()) {
            return LLMServiceStrategy.FAILOVER;
        }

        String normalized = name.trim().toUpperCase().replace("-", "_");

        try {
            return LLMServiceStrategy.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown strategy '{}', using FAILOVER as default", name);
            return LLMServiceStrategy.FAILOVER;
        }
    }

    /**
     * POJO for LLM Provider configuration properties.
     */
    public static class LLMProviderProperties {
        private String providerName;
        private String llmModels;
        private String embeddingModel;
        private Integer embeddingDimension;
        private String apiUrl;
        private boolean primary;

        public String getProviderName() {
            return providerName;
        }

        public void setProviderName(String providerName) {
            this.providerName = providerName;
        }

        public String getLlmModels() {
            return llmModels;
        }

        public void setLlmModels(String llmModels) {
            this.llmModels = llmModels;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public void setEmbeddingModel(String embeddingModel) {
            this.embeddingModel = embeddingModel;
        }

        public Integer getEmbeddingDimension() {
            return embeddingDimension;
        }

        public void setEmbeddingDimension(Integer embeddingDimension) {
            this.embeddingDimension = embeddingDimension;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public boolean isPrimary() {
            return primary;
        }

        public void setPrimary(boolean primary) {
            this.primary = primary;
        }

        public String[] getLlmModelsArray() {
            if (llmModels == null || llmModels.isEmpty()) {
                return new String[0];
            }
            return llmModels.split(",\\s*");
        }
    }
}
