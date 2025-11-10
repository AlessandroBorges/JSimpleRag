package bor.tools.simplerag.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import bor.tools.simplellm.LLMConfig;
import bor.tools.simplellm.LLMProvider;
import bor.tools.simplellm.LLMServiceFactory;
import bor.tools.simplellm.SERVICE_PROVIDER;
import bor.tools.simplellm.Model;
import bor.tools.simplellm.ModelEmbedding;
import bor.tools.simplellm.Model_Type;
import bor.tools.simplerag.service.llm.LLMServiceManager;
import bor.tools.simplerag.service.llm.LLMServiceStrategy;
import bor.tools.simplerag.util.LLMProviderParser;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Unified Spring Configuration for LLM Service integration.
 *
 * This configuration consolidates the functionality of both LLMServiceConfig
 * and MultiLLMServiceConfig, providing a single entry point for all LLM
 * service configuration needs.
 *
 * Features:
 * - Single and multi-provider support (primary + optional secondary)
 * - Fallback strategies (FAILOVER, ROUND_ROBIN, PRIMARY_ONLY, MODEL_BASED)
 * - Granular configuration with property overrides
 * - Default configuration support via use_defaults flag
 * - Backward compatible with existing property names
 *
 * Configuration Properties:
 *
 * Primary Provider:
 * - llmservice.provider.name: Provider name (default: LM_STUDIO)
 * - llmservice.provider.use_defaults: Use default config (default: false)
 * - llmservice.provider.llm.models: LLM models (comma-separated)
 * - llmservice.provider.embedding.model: Embedding model name
 * - llmservice.provider.embedding.dimension: Vector dimension (default: 768)
 * - llmservice.provider.embedding.embeddingContextLength: Context length (default: 2048)
 * - llmservice.provider.api.url: API base URL (optional)
 * - llmservice.provider.api.key: API key (optional)
 *
 * Secondary Provider (optional):
 * - llmservice.provider2.enabled: Enable secondary provider (default: false)
 * - llmservice.provider2.name: Secondary provider name
 * - llmservice.provider2.llm.models: Secondary LLM models
 * - llmservice.provider2.embedding.model: Secondary embedding model
 * - llmservice.provider2.embedding.dimension: Secondary embedding dimension
 * - llmservice.provider2.api.url: Secondary API URL
 * - llmservice.provider2.api.key: Secondary API key
 *
 * Strategy Configuration:
 * - llmservice.strategy: Strategy (FAILOVER, ROUND_ROBIN, PRIMARY_ONLY, MODEL_BASED)
 * - llmservice.failover.max-retries: Max retry attempts (default: 3)
 * - llmservice.failover.timeout-seconds: Operation timeout (default: 30)
 *
 * @since 0.0.1
 */
@Configuration
@Slf4j
public class LLMConfiguration {

    // ============ Primary Provider Configuration ============

    @Value("${llmservice.provider.name:LM_STUDIO}")
    private String primaryProviderName;

    @Value("${llmservice.provider.use_defaults:false}")
    private boolean primaryUseDefaults;

    @Value("${llmservice.provider.llm.models:qwen/qwen3-1.7b}")
    private String primaryLlmModels;

    @Value("${llmservice.provider.embedding.model:snowflake}")
    private String primaryEmbeddingModel;

    @Value("${llmservice.provider.embedding.dimension:768}")
    private Integer primaryEmbeddingDimension;

    @Value("${llmservice.provider.embedding.embeddingContextLength:4096}")
    private Integer primaryEmbeddingContextLength;

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

    // ============ Internal State ============

    /**
     * Map of active LLM services by provider key.
     */
    private final Map<String, LLMProvider> activeLLMServices = new HashMap<>();

    // ============ Bean Creation Methods ============

    /**
     * Creates the primary LLMProvider bean.
     * This is the main LLM provider marked as @Primary for default injection.
     *
     * @return Configured primary LLMProvider instance
     * @throws IllegalStateException if provider configuration is invalid
     */
    @Bean(name = "primaryLLMService")
    @Primary
    LLMProvider primaryLLMService() {
        log.info("Initializing Primary LLMProvider");
        log.info("  Provider: {}", primaryProviderName);
        log.info("  Use Defaults: {}", primaryUseDefaults);
        log.info("  Models: {}", primaryLlmModels);
        log.info("  Embedding Model: {}", primaryEmbeddingModel);
        log.info("  Embedding Dimension: {}", primaryEmbeddingDimension);

        try {
            LLMProvider service = createLLMService(
                "primary",
                primaryProviderName,
                primaryUseDefaults,
                primaryLlmModels,
                primaryEmbeddingModel,
                primaryEmbeddingContextLength,
                primaryApiUrl,
                primaryApiKey
            );

            log.info("Primary LLMProvider initialized successfully");
            return service;

        } catch (Exception e) {
            log.error("Failed to initialize Primary LLMProvider: {}", e.getMessage(), e);
            throw new IllegalStateException("Could not initialize Primary LLMProvider", e);
        }
    }

    /**
     * Creates the secondary LLMProvider bean if enabled.
     * This serves as backup/complement to the primary provider.
     *
     * @return Configured secondary LLMProvider instance
     * @throws IllegalStateException if provider configuration is invalid
     */
    @Bean(name = "secondaryLLMService")
    @ConditionalOnProperty(name = "llmservice.provider2.enabled", havingValue = "true")
    public LLMProvider secondaryLLMService() {
        log.info("Initializing Secondary LLMProvider");
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
            LLMProvider service = createLLMService(
                "secondary",
                secondaryProviderName,
                false, // Secondary never uses defaults
                secondaryLlmModels,
                secondaryEmbeddingModel,
                2048, // Default context length for secondary
                secondaryApiUrl,
                secondaryApiKey
            );

            log.info("Secondary LLMProvider initialized successfully");
            return service;

        } catch (Exception e) {
            log.error("Failed to initialize Secondary LLMProvider: {}", e.getMessage(), e);
            throw new IllegalStateException("Could not initialize Secondary LLMProvider", e);
        }
    }

    /**
     * Creates the LLMServiceManager bean that manages multiple providers.
     * This is the RECOMMENDED bean to inject in services that need LLM functionality.
     *
     * @param primaryLLMService Primary LLM service (required)
     * @param allLLMServices All available LLM services (Spring injects all LLMProvider beans)
     * @return Configured LLMServiceManager instance
     */
    @Bean
    public LLMServiceManager llmServiceManager(
            LLMProvider primaryLLMService,
            List<LLMProvider> allLLMServices) {

        log.info("Initializing LLMServiceManager");
        log.info("  Strategy: {}", strategyName);
        log.info("  Max Retries: {}", maxRetries);
        log.info("  Timeout: {}s", timeoutSeconds);
        log.info("  Total Providers: {}", allLLMServices.size());

        LLMServiceStrategy strategy = parseStrategy(strategyName);

        // Build list of valid services (primary + any secondaries)
        List<LLMProvider> validServices = new ArrayList<>();
        validServices.add(primaryLLMService);

        // Add secondary if it exists and is different from primary
        for (LLMProvider service : allLLMServices) {
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
     * Useful for injecting configuration values in other components.
     *
     * @return LLMProviderProperties for primary provider
     */
    @Bean(name = "primaryLLMProperties")
    public LLMProviderProperties primaryLLMProperties() {
        LLMProviderProperties props = new LLMProviderProperties();
        props.setProviderName(primaryProviderName);
        props.setLlmModels(primaryLlmModels);
        props.setEmbeddingModel(primaryEmbeddingModel);
        props.setEmbeddingDimension(primaryEmbeddingDimension);
        props.setEmbeddingContextLength(primaryEmbeddingContextLength);
        props.setApiUrl(primaryApiUrl);
        props.setPrimary(true);
        return props;
    }

    /**
     * Configuration properties bean for secondary provider.
     * Only created if secondary provider is enabled.
     *
     * @return LLMProviderProperties for secondary provider
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
     * Creates an LLMProvider instance with given configuration.
     *
     * @param serviceKey Unique key for this service (e.g., "primary", "secondary")
     * @param providerName Provider name (e.g., "LM_STUDIO", "OPENAI")
     * @param useDefaults Whether to use default configuration from factory
     * @param llmModels Comma-separated list of LLM model names
     * @param embeddingModel Embedding model name
     * @param embeddingContextLength Context length for embeddings
     * @param apiUrl API base URL (optional)
     * @param apiKey API key (optional)
     * @return Configured LLMProvider instance
     */
    private LLMProvider createLLMService(String serviceKey, String providerName, boolean useDefaults, String llmModels,
	    String embeddingModel, Integer embeddingContextLength, String apiUrl, String apiKey) {

	// Parse provider name using utility class
	SERVICE_PROVIDER provider = LLMProviderParser.parseProviderName(providerName);

	// Build configuration
	LLMConfig config;
	if (useDefaults) {
	    // TODO: Aguardando implementação de LLMServiceFactory.getDefaultLLMConfig() no
	    // JSimpleLLM
	    config = LLMServiceFactory.getDefaultLLMConfig(provider);

	    // Override API URL and key if provided (redundant now, but kept for when TODO
	    // is resolved)
	    if (apiUrl != null) {
		config.setBaseUrl(apiUrl);
	    }
	    if (apiKey != null) {
		config.setApiToken(apiKey);
	    }
	} else {
	    config = buildConfigFromScratch(apiUrl, apiKey);
	}

	// Add models to configuration
	if (llmModels != null && !llmModels.isEmpty()) {
	    parseLLMModelsToArray(llmModels, config);
	}

	if (embeddingModel != null && !embeddingModel.isEmpty()) {
	    parseEmbeddingModelsToArray(embeddingModel, embeddingContextLength, config);
	}

	// Create service
	LLMProvider service = LLMServiceFactory.createLLMService(provider, config);

	// Store in active services map
	String mapKey = provider.name() + "-" + (apiUrl != null ? apiUrl : "default");
	activeLLMServices.put(mapKey, service);

	log.debug("Created LLMProvider: key={}, provider={}, baseUrl={}", 
	          serviceKey, provider, config.getBaseUrl());

	return service;
    }

    /**
     * Builds LLMConfig from scratch using builder pattern.
     *
     * @param apiUrl API base URL
     * @param apiKey API key
     * @return Built LLMConfig instance
     */
    private LLMConfig buildConfigFromScratch(String apiUrl, String apiKey) {
        LLMConfig.LLMConfigBuilder builder = LLMConfig.builder();

        if (apiUrl != null) {
            builder.baseUrl(apiUrl);
        }
        if (apiKey != null) {
            builder.apiToken(apiKey);
        }

        return builder.build();
    }

    /**
     * Parses strategy name from configuration to LLMServiceStrategy enum.
     *
     * @param name Strategy name (e.g., "FAILOVER", "ROUND_ROBIN")
     * @return Corresponding LLMServiceStrategy enum
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
     * Returns unmodifiable map of active LLM service providers.
     * Useful for controllers that need to list available providers.
     *
     * @return Map of provider key to LLMProvider
     */
    public Map<String, LLMProvider> getActiveProviderMap() {
        return Collections.unmodifiableMap(this.activeLLMServices);
    }

    // ============ Static Utility Methods ============

    /**
     * Static utility method to parse llmModels string and add Model objects to config.
     * Each model is created with default context length (8192) and Model_Type.LANGUAGE.
     *
     * @param llmModels Comma-separated model names
     * @param config LLMConfig instance to add models to
     * @return Array of Model objects
     */
    protected static Model[] parseLLMModelsToArray(String llmModels, LLMConfig config) {
        if (llmModels == null || llmModels.isEmpty()) {
            return new Model[0];
        }

        String[] modelsArray = llmModels.split(",\\s*");
        Model[] models = new Model[modelsArray.length];

        for (int i = 0; i < modelsArray.length; i++) {
            Model_Type[] guessedTypes = guessModelTypes(modelsArray[i]);
            
            Model modelo = new Model(modelsArray[i], 8192, guessedTypes);
            models[i] = modelo;
            if (config != null) {
                config.addModels(modelo);
            }
        }

        return models;
    }
    
    public static Model_Type[] guessModelTypes(String modelName) {
	List<Model_Type> types = new ArrayList<>();

	String nm = modelName.toLowerCase();

	if (nm.contains("embed") || nm.contains("embedding")) {
	    types.add(Model_Type.EMBEDDING);
	    return types.toArray(new Model_Type[1]);
	} else {
	    types.add(Model_Type.LANGUAGE);
	    types.add(Model_Type.TEXT);
	}
	
	if (nm.contains("code") || nm.contains("gpt")) 
	{
	    types.add(Model_Type.CODING);
	}
	
	if (nm.contains("reason") || nm.contains("logic") || nm.contains("think") ||
	    nm.contains("r1")     || nm.contains("o3")    || nm.contains("o4") ||
	    nm.contains("phi-4")  || nm.contains("phi-3.5") || nm.contains("qwen3") ||
	    nm.contains("gpt-oss") || nm.contains("gpt-4")
	    
	   ) 
	{
	    types.add(Model_Type.REASONING);
	}
	
	// reasoning prompt models
	if (nm.contains("phi-4") || nm.contains("phi-3.5") ||
	    nm.contains("qwen3") 
	    ) 
	{
	    types.add(Model_Type.REASONING_PROMPT);	  
	}
	
	
	if (nm.contains("fast") || nm.contains("quick") || nm.contains("4b") ||
            nm.contains("7b")   || nm.contains("8b")   ||  
	    nm.contains("3b")   || nm.contains("1.7b")  || nm.contains("1b") ||
	    nm.contains("2b")   || nm.contains("0.6b")  || 
	    nm.contains("mini")) {
	    types.add(Model_Type.FAST);
	}
	if (nm.contains("vision") || nm.contains("image") || nm.contains("img") || 
	    nm.contains("dall")   || nm.contains("llava") || nm.contains("diffusion") ||
	    nm.contains("-vl")) 
	{
	    types.add(Model_Type.VISION);	  
	}
	if(nm.contains("audio") || nm.contains("speech") || nm.contains("wav") || 
	   nm.contains("tts")   || nm.contains("whisper")) 
	{
	    types.add(Model_Type.AUDIO);	  
	}
	if(nm.contains("chat") || nm.contains("gpt") || nm.contains("dialog") || 
	   nm.contains("conversation") || nm.contains("responses")) 
	{
	    types.add(Model_Type.RESPONSES_API);	  
	}
	
	// Image generation
	if(nm.contains("dall-e") || nm.contains("stable") || nm.contains("image-edit") || 
	   nm.contains("image-gen") || nm.contains("imagegen") || nm.contains("midjourney")
	   ) 
	{
	    types.add(Model_Type.IMAGE);	  
	}
	
	return types.toArray(new Model_Type[0]);
    }

    /**
     * Static utility method to parse embeddingModel string and add ModelEmbedding objects to config.
     * Each embedding model is created with the provided context length and Model_Type.EMBEDDING.
     * 
     * It uses a default embedding dimension of 768, which is the default size for BERT models.	
     *
     * @param embeddingModel Comma-separated embedding model names
     * @param embeddingContextLength Context length for embedding models
     * @param config LLMConfig instance to add embedding models to
     * @return Array of ModelEmbedding objects
     */
    protected static ModelEmbedding[] parseEmbeddingModelsToArray(
            String embeddingModel,
            int embeddingContextLength,
            LLMConfig config) {

        if (embeddingModel == null || embeddingModel.isEmpty()) {
            return new ModelEmbedding[0];
        }

        String[] embeddingModelNameArray = embeddingModel.split(",\\s*");
        ModelEmbedding[] embeddings = new ModelEmbedding[embeddingModelNameArray.length];

        for (int i = 0; i < embeddingModelNameArray.length; i++) {
            ModelEmbedding embedding = new ModelEmbedding(
                embeddingModelNameArray[i],
                embeddingContextLength,
                768,
                Model_Type.EMBEDDING
            );
            embeddings[i] = embedding;
            if (config != null) {
                config.addModels(embedding);
            }
        }

        return embeddings;
    }

    // ============ Inner Classes ============

    /**
     * POJO for LLM Provider configuration properties.
     * Can be injected into other components for configuration access.
     */
    @Data
    public static class LLMProviderProperties {
        private String providerName;
        private String llmModels;
        private String embeddingModel;
        private Integer embeddingDimension;
        private Integer embeddingContextLength;
        private String apiUrl;
        private boolean primary;

        /**
         * Returns array of LLM model names split by comma.
         *
         * @return Array of model names
         */
        public String[] getLlmModelsArray() {
            if (llmModels == null || llmModels.isEmpty()) {
                return new String[0];
            }
            return llmModels.split(",\\s*");
        }
    }
}
