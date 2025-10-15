package bor.tools.simplerag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import bor.tools.simplellm.LLMConfig;
import bor.tools.simplellm.LLMService;
import bor.tools.simplellm.LLMServiceFactory;
import bor.tools.simplellm.LLMServiceFactory.SERVICE_PROVIDER;
import bor.tools.simplellm.Model;
import bor.tools.simplellm.ModelEmbedding;
import bor.tools.simplellm.Model_Type;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Configuration for LLM Service integration.
 *
 * This configuration reads properties from application.properties and creates
 * the LLMService bean for dependency injection.
 *
 * Configuration properties: - llmservice.provider.name: Provider class name
 * (e.g., LM_STUDIO, OPENAI, OLLAMA) - llmservice.provider.llm.models: Available
 * LLM models - llmservice.provider.embedding.model: Embedding model name -
 * llmservice.provider.embedding.dimension: Vector dimension for embeddings
 */
@Configuration
@Slf4j
public class LLMServiceConfig {

    @Value("${llmservice.provider.name:LM_STUDIO}")
    private String providerName;

    @Value("${llmservice.provider.llm.models:qwen/qwen3-1.7b}")
    private String llmModels;

    @Value("${llmservice.provider.embedding.model:text-embedding-nomic-embed-text-v1.5@q8_0}")
    private String embeddingModel;

    @Value("${llmservice.provider.embedding.dimension:768}")
    private Integer embeddingDimension;

    @Value("${llmservice.provider.embedding.embeddingContextLength:8192}")
    private Integer embeddingContextLength; // Default context length for embedding models

    @Value("${llmservice.provider.api.url:#{null}}")
    private String apiUrl;

    @Value("${llmservice.provider.api.key:#{null}}")
    private String apiKey;

    /**
     * Creates and configures the LLMService bean.
     *
     * @return Configured LLMService instance
     * @throws IllegalArgumentException if provider configuration is invalid
     */
    @Bean
    public LLMService llmService() {
	log.info("Initializing LLMService with provider: {}", providerName);
	log.info("LLM Models: {}", llmModels);
	log.info("Embedding Model: {}", embeddingModel);
	log.info("Embedding Dimension: {}", embeddingDimension);

	try {
	    // Convert provider name to SERVICE_PROVIDER
	    SERVICE_PROVIDER provider = parseProviderName(providerName);

	    LLMConfig config = null;
	    if (apiUrl != null || apiKey != null) {
		LLMConfig.LLMConfigBuilder builder = LLMConfig.builder();

		if (apiUrl != null) {
		    builder.baseUrl(apiUrl);
		}
		if (apiKey != null) {
		    builder.apiToken(apiKey);
		}
		config = builder.build();

		parseLlmModelsToArray(apiUrl, config);
		parseEmbeddingModelsToArray(embeddingModel, embeddingContextLength, config);
	    }

	    LLMService service = LLMServiceFactory.createLLMService(provider, config);
	    log.info("LLMService initialized successfully");
	    return service;

	} catch (Exception e) {
	    log.error("Failed to initialize LLMService: {}", e.getMessage(), e);
	    throw new IllegalStateException("Could not initialize LLMService", e);
	}
    }

    /**
     * Parses provider name from configuration to SERVICE_PROVIDER. Supports both
     * enum names and common aliases.
     *
     * @param name Provider name from configuration
     * @return Corresponding SERVICE_PROVIDER
     * @throws IllegalArgumentException if provider name is not recognized
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
	    // Try to parse as enum value directly
	    return SERVICE_PROVIDER.valueOf(normalized);
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
	     * @ Todo case "GEMINI": case "GOOGLE": return SERVICE_PROVIDER.GEMINI;
	     * 
	     * case "COHERE": return SERVICE_PROVIDER.COHERE;
	     * 
	     * case "HUGGINGFACE": case "HF": return SERVICE_PROVIDER.HUGGINGFACE;
	     */
	    default:
		throw new IllegalArgumentException("Unknown LLM provider: " + name + ". "
			+ "Supported providers: LM_STUDIO, OPENAI, OLLAMA, ANTHROPIC, GEMINI, COHERE, HUGGINGFACE");
	    }
	}
    }

    /**
     * Configuration properties bean for LLM Service. Useful for injecting
     * configuration values in other components.
     */
    @Bean
    public LLMServiceProperties llmServiceProperties() {
	LLMServiceProperties properties = new LLMServiceProperties();
	properties.setProviderName(providerName);
	properties.setLlmModels(llmModels);
	properties.setEmbeddingModel(embeddingModel);
	properties.setEmbeddingDimension(embeddingDimension);
	properties.setApiUrl(apiUrl);
	return properties;
    }

    /**
     * POJO for LLM Service configuration properties. Can be injected into other
     * components for configuration access.
     */
    @Data
    public static class LLMServiceProperties {
	private String providerName;
	private String llmModels;
	private String embeddingModel;
	private Integer embeddingDimension;
	private Integer embeddingContextLength;
	private String apiUrl;

	/**
	 * Returns array of LLM model names split by comma.
	 */
	public String[] getLlmModelsArray() {
	    if (llmModels == null || llmModels.isEmpty()) {
		return new String[0];
	    }
	    return llmModels.split(",\\s*");
	}
    }

    /**
     * Static utility method to parse llmModels string and return an array of Model
     * objects. Each model is created with default context length (8192) and
     * Model_Type.LANGUAGE. Also adds each model to the provided LLMCOnfig instance.
     *
     * @param llmModels Comma-separated model names
     * @param config    LLMCOnfig instance to add models to
     * @return Array of Model objects
     */
    protected static Model[] parseLlmModelsToArray(String llmModels, LLMConfig config) {
	if (llmModels == null || llmModels.isEmpty()) {
	    return new Model[0];
	}
	String[] modelsArray = llmModels.split(",\\s*");
	Model[] models = new Model[modelsArray.length];
	for (int i = 0; i < modelsArray.length; i++) {
	    Model modelo = new Model(modelsArray[i], 8192, Model_Type.LANGUAGE);
	    models[i] = modelo;
	    if (config != null) {
		config.addModels(modelo);
	    }
	}
	return models;
    }

    /**
     * Static utility method to parse embeddingModel string and return an array of
     * ModelEmbedding objects. Each embedding model is created with the provided
     * context length and Model_Type.EMBEDDING. Also adds each embedding model to
     * the provided LLMConfig instance.
     *
     * @param embeddingModel         Comma-separated embedding model names
     * @param embeddingContextLength Context length for embedding models
     * @param config                 LLMConfig instance to add embedding models to
     * @return Array of ModelEmbedding objects
     */
    protected static ModelEmbedding[] parseEmbeddingModelsToArray(String embeddingModel, int embeddingContextLength,
	    LLMConfig config) {
	if (embeddingModel == null || embeddingModel.isEmpty()) {
	    return new ModelEmbedding[0];
	}
	String[] embeddingModelNameArray = embeddingModel.split(",\\s*");
	ModelEmbedding[] embeddings = new ModelEmbedding[embeddingModelNameArray.length];
	for (int i = 0; i < embeddingModelNameArray.length; i++) {
	    ModelEmbedding embedding = new ModelEmbedding(embeddingModelNameArray[i], embeddingContextLength,
		    Model_Type.EMBEDDING);
	    embeddings[i] = embedding;
	    if (config != null) {
		config.addModels(embedding);
	    }
	}
	return embeddings;
    }
}