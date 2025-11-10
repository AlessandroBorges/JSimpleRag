package bor.tools.simplerag.service.processing.context;

import bor.tools.simplellm.LLMProvider;
import bor.tools.simplellm.MapParam;
import bor.tools.simplellm.Model;
import bor.tools.simplellm.Model_Type;
import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.service.llm.LLMServiceManager;
import bor.tools.simplerag.service.llm.LLMServiceManager.Model_Provider;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * Context for LLM completion operations.
 *
 * <p>Encapsulates a validated LLM service and model for text completion operations
 * such as generating summaries and answering questions. The context is created once
 * per document and reused throughout the processing pipeline to avoid redundant
 * model lookups and validations.</p>
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Single validation point - created once, validated once</li>
 *   <li>Reusable across multiple completions</li>
 *   <li>Automatic best model selection from available providers</li>
 *   <li>Token counting support for precise text measurement</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Create context once
 * LLMContext context = LLMContext.create(library, llmServiceManager);
 *
 * // Reuse for multiple operations
 * String summary = context.generateCompletion(
 *     "Summarize the following text:",
 *     chapterText
 * );
 *
 * int tokens = context.tokenCount(text, "fast");
 * }</pre>
 *
 * @since 0.0.3
 * @version 1.1
 */
@Data
@Builder
@Slf4j
public class LLMContext {

    /**
     * The LLM service instance used for completions.
     */
    private LLMProvider llmService;

    /**
     * The model metadata from the service.
     */
    private Model model;

    /**
     * The model name (alias or identifier).
     */
    private String modelName;

    /**
     * Default parameters for completion requests.
     */
    private MapParam params;

    /**
     * Creates a new LLM context for the given library.
     *
     * <p>Selects the best available completion model from the LLM service manager's
     * registered providers. The model is validated and ready for use.</p>
     *
     * @param library the library configuration
     * @param manager the LLM service manager
     * @return a new LLM context with validated service and model
     * @throws IllegalStateException if no completion model is available
     */
    public static LLMContext create(LibraryDTO library, LLMServiceManager manager) {
	
        log.debug("Creating LLM context for library: {}", library.getNome());
        
        LLMServiceManager.Model_Provider modelProvider = null;
        
        String libModelName = library.getCompletionQAModel();
        if (libModelName != null && !libModelName.isEmpty()) {
	    log.debug("Library specifies completion model: {}", libModelName);
	    // Check if specified model is available
	    var provider = manager.getLLMServiceByRegisteredModel(libModelName);
	    modelProvider = new Model_Provider(libModelName, provider);		

	    if (modelProvider != null) {
		log.debug("Using library-specified completion model: {} from service: {}",
			 modelProvider.modelName(),
			 modelProvider.service().getClass().getSimpleName());
	    }
        }
        
        // fallback to best available model
        if(modelProvider == null) {
            // Get best completion model from cache
            Model_Type[] model_IN = {Model_Type.LANGUAGE, Model_Type.FAST};
            Model_Type[] model_OUT = {Model_Type.VISION, Model_Type.IMAGE};
            modelProvider = manager.getBestCompletionModelName(model_IN, model_OUT);
        }

        if (modelProvider == null) {
            throw new IllegalStateException("No completion model available");
        }

        log.debug("Selected completion model: {} from service: {}",
                 modelProvider.modelName(),
                 modelProvider.service().getClass().getSimpleName());

        // Get Model metadata
        Model model = null;
        try {
            var models = modelProvider.service().getRegisteredModels();
            model = models.get(modelProvider.modelName());
        } catch (Exception e) {
            log.warn("Failed to get Model metadata: {}", e.getMessage());
        }

        // Build parameters
        MapParam params = new MapParam();
        params.model(modelProvider.modelName());

        return LLMContext.builder()
                .llmService(modelProvider.service())
                .model(model)
                .modelName(modelProvider.modelName())
                .params(params)
                .build();
    }

    /**
     * Generates a text completion using this context.
     *
     * <p>Sends a completion request to the LLM service with the configured
     * model and parameters.</p>
     *
     * @param systemPrompt the system prompt (instructions)
     * @param userPrompt the user prompt (content to process)
     * @return the generated completion text
     * @throws Exception if completion generation fails
     */
    public String generateCompletion(String systemPrompt, String userPrompt, MapParam extraParams)
            throws Exception {
        log.debug("Generating completion with model: {}", modelName);
        
        // add missing params from context
        for(var entry : this.params.entrySet()) {
	    extraParams.putIfAbsent(entry.getKey(), entry.getValue());
	}
        
        var response = llmService.completion(systemPrompt, userPrompt, extraParams);

        String text = response.getText();
        log.debug("Generated completion: {} characters", text.length());

        return text;
    }

    /**
     * Counts the number of tokens in the given text.
     *
     * <p>Uses the LLM service's tokenization to get an accurate count.
     * This is essential for determining if text fits within model context
     * windows and for batch size calculations.</p>
     *
     * <p><b>Revision 1.1:</b> Uses "fast" model parameter for efficient
     * token counting as specified in the approved architecture.</p>
     *
     * @param text the text to count tokens for
     * @param model the model name to use for counting (typically "fast")
     * @return the number of tokens in the text
     * @throws Exception if token counting fails
     */
    public int tokenCount(String text, String model) throws Exception {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int count = llmService.tokenCount(text, model);
        log.trace("Token count for {} chars: {} tokens (model: {})",
                 text.length(), count, model);

        return count;
    }

    /**
     * Validates if this context is ready for use.
     *
     * @return true if the context has a valid service and model name
     */
    public boolean isValid() {
        return llmService != null && modelName != null;
    }

    /**
     * Returns a string representation of this context.
     *
     * @return a string with model name and service type
     */
    @Override
    public String toString() {
        return String.format("LLMContext[model=%s, service=%s, valid=%s]",
                           modelName,
                           llmService != null ? llmService.getClass().getSimpleName() : "null",
                           isValid());
    }
}
