package bor.tools.simplerag.service.llm;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import bor.tools.simplellm.*;
import bor.tools.simplellm.ModelEmbedding.Embeddings_Op;
import bor.tools.simplellm.exceptions.LLMException;
import lombok.extern.slf4j.Slf4j;

/**
 * Manager for multiple LLM service providers.
 *
 * Handles failover, load balancing, and routing strategies across
 * primary and secondary LLM providers.
 *
 * Thread-safe implementation supporting concurrent requests.
 */
@Slf4j
public class LLMServiceManager {

    private final List<LLMService> services;
    private final LLMServiceStrategy strategy;
    private final int maxRetries;
    private final int timeoutSeconds;

    
    // Round-robin counter
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    // Statistics
    private final AtomicInteger primaryRequests = new AtomicInteger(0);
    private final AtomicInteger secondaryRequests = new AtomicInteger(0);
    private final AtomicInteger failoverEvents = new AtomicInteger(0);
    protected java.util.Map<String, LLMService> modelsToService;

    /**
     * Creates a new LLMServiceManager.
     *
     * @param services List of LLM services (primary first)
     * @param strategy Strategy for provider selection
     * @param maxRetries Maximum retry attempts on failure
     * @param timeoutSeconds Timeout for each request
     */
    public LLMServiceManager(List<LLMService> services,
                            LLMServiceStrategy strategy,
                            int maxRetries,
                            int timeoutSeconds) {
        if (services == null || services.isEmpty()) {
            throw new IllegalArgumentException("At least one LLM service required");
        }

        this.services = services;
        this.strategy = strategy != null ? strategy : LLMServiceStrategy.FAILOVER;
        this.maxRetries = maxRetries > 0 ? maxRetries : 3;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 30;

        log.info("LLMServiceManager created with {} provider(s), strategy: {}",
                services.size(), this.strategy);
    }

    /**
     * Generates embedding using configured strategy.
     *
     * @param text Text to embed
     * @return Embedding vector
     * @throws LLMServiceException if all providers fail
     */
    public float[] embeddings(Embeddings_Op op, String text) throws LLMServiceException {
        return embeddings(op, text, "nomic");
    }
    
    /**
     * Retrieves the default completion model name from the primary service.
     *
     * @return Default completion model name, or null if unavailable
     */
    public String getDefaultCompletionModelName() {
	LLMService service = getPrimaryService();
	try {

	    int i = 1;
	    while (service == null && i < services.size()) {
		if (i == 1)
		    log.warn("No primary LLM service available to get default model");
		i++;
		service = services.get(i);
		if (service != null)
		    log.info("Trying secondary LLM service at index {} as primary, as LLMService", i,
			    service.getServiceProvider());
	    }
	    if (service == null) {
		log.error("No LLM service available to get default model");
		return null;
	    }

	    MapModels models = service.getInstalledModels();
	    if (models != null && !models.isEmpty()) {
		// Return the first model as default
		return models.keySet().iterator().next();
	    }
	} catch (LLMServiceException e) {
	    log.warn("Failed to get installed models from primary service: {}", e.getMessage());
	    e.printStackTrace();
	} catch (LLMException e) {
	    log.warn("LLM exception while getting installed models: {}", e.getMessage());
	    e.printStackTrace();
	}
	return null;
    }

    /**
     * Generates embedding with specific operation type.
     *
     * @param operation Operation type (QUERY, DOCUMENT, etc.)
     * @param text Text to embed     
     * @return Embedding vector
     * @throws LLMServiceException if all providers fail
     */
    public float[] embeddings(Embeddings_Op op, String text, String modelName) throws LLMServiceException {
	MapParam param = new MapParam();
	param.model(modelName);

        switch (strategy) {
            case PRIMARY_ONLY:
                return executeOnPrimaryOnly(() -> getPrimaryService().embeddings(op, text, param));

            case FAILOVER:
                return executeWithFailover(() -> generateEmbeddingInternal(op, text, modelName));

            case ROUND_ROBIN:
                return executeRoundRobin(() -> generateEmbeddingInternal(op, text, modelName));

            case SPECIALIZED:
                // For embeddings, always use primary (typically optimized for this)
                return executeOnPrimaryOnly(() -> getPrimaryService().embeddings(op, text, param));

            case DUAL_VERIFICATION:
                return executeDualVerification(() -> generateEmbeddingInternal(op, text, modelName));

            case SMART_ROUTING:
                return executeSmartRouting(text, () -> generateEmbeddingInternal(op, text, modelName));

            case MODEL_BASED:
                return executeModelBased(modelName, () -> generateEmbeddingInternal(op, text, modelName));

            default:
                return executeWithFailover(() -> generateEmbeddingInternal(op, text, modelName));
        }
    }

    /**
     * Generates text completion using configured strategy.
     *
     * @param prompt Prompt text
     * @param model Model to use
     * @return Generated text
     * @throws LLMServiceException if all providers fail
     */
    public String generateCompletion(String system, String prompt, String model) throws LLMServiceException {
	MapParam params = new MapParam();

	params.model(model);
        switch (strategy) {
            case PRIMARY_ONLY:
                return executeOnPrimaryOnly(() -> getPrimaryService().completion(system, prompt, params).getText());

            case FAILOVER:
                return executeWithFailover(() -> generateCompletionInternal(system, prompt, model));

            case ROUND_ROBIN:
                return executeRoundRobin(() -> generateCompletionInternal(system, prompt, model));

            case SPECIALIZED:
                // For completions, prefer secondary if available (might be more powerful)
                return services.size() > 1
                    ? executeOnService(services.get(1), () -> services.get(1).completion(system, prompt, params).getText())
                    : executeOnPrimaryOnly(() -> getPrimaryService().completion(system,prompt, params).getText());

            case DUAL_VERIFICATION:
                return executeDualVerification(() -> generateCompletionInternal(system, prompt, model));

            case SMART_ROUTING:
                return executeSmartRouting(prompt, () -> generateCompletionInternal(system, prompt, model));

            case MODEL_BASED:
                return executeModelBased(model, () -> generateCompletionInternal(system, prompt, model));

            default:
                return executeWithFailover(() -> generateCompletionInternal(system, prompt, model));
        }
    }

    // ============ Strategy Implementations ============

    /**
     * Execute only on primary provider.
     */
    private <T> T executeOnPrimaryOnly(ServiceCallable<T> callable) throws LLMServiceException {
        primaryRequests.incrementAndGet();
        return executeOnService(getPrimaryService(), callable);
    }

    /**
     * Execute with failover to secondary on failure.
     */
    private <T> T executeWithFailover(ServiceCallable<T> callable) throws LLMServiceException {
        LLMService primary = getPrimaryService();
        primaryRequests.incrementAndGet();

        try {
            return executeOnService(primary, callable);
        } catch (LLMServiceException e) {
            log.warn("Primary LLM service failed: {}. Trying secondary...", e.getMessage());

            if (services.size() > 1) {
                failoverEvents.incrementAndGet();
                secondaryRequests.incrementAndGet();
                LLMService secondary = services.get(1);

                try {
                    T result = executeOnService(secondary, callable);
                    log.info("Successfully failed over to secondary provider");
                    return result;
                } catch (LLMServiceException e2) {
                    log.error("Secondary LLM service also failed: {}", e2.getMessage());
                    throw new LLMServiceException("All LLM providers failed", e2);
                }
            }

            throw e;
        }
    }

    /**
     * Execute in round-robin fashion across all providers.
     */
    private <T> T executeRoundRobin(ServiceCallable<T> callable) throws LLMServiceException {
        int index = roundRobinCounter.getAndIncrement() % services.size();
        LLMService service = services.get(index);

        if (index == 0) {
            primaryRequests.incrementAndGet();
        } else {
            secondaryRequests.incrementAndGet();
        }

        return executeOnService(service, callable);
    }

    /**
     * Execute on both providers and verify results match.
     */
    private <T> T executeDualVerification(ServiceCallable<T> callable) throws LLMServiceException {
        if (services.size() < 2) {
            return executeOnPrimaryOnly(callable);
        }

        primaryRequests.incrementAndGet();
        secondaryRequests.incrementAndGet();

        T result1 = executeOnService(services.get(0), callable);
        T result2 = executeOnService(services.get(1), callable);

        // For embeddings, compare vector similarity
        if (result1 instanceof float[] && result2 instanceof float[]) {
            float[] vec1 = (float[]) result1;
            float[] vec2 = (float[]) result2;

            if (vec1.length != vec2.length) {
                log.warn("Dual verification: Vector dimensions differ ({} vs {})",
                        vec1.length, vec2.length);
            }

            float similarity = cosineSimilarity(vec1, vec2);
            log.info("Dual verification: Vector similarity = {}", similarity);

            if (similarity < 0.8f) {
                log.warn("Dual verification: Low similarity between providers ({})", similarity);
            }
        }

        // Return primary result
        return result1;
    }

    /**
     * Smart routing based on request characteristics.
     */
    private <T> T executeSmartRouting(String input, ServiceCallable<T> callable)
            throws LLMServiceException {
        // Simple heuristic: route based on input length
        boolean isComplex = input.length() > 1000 || input.contains("explain") ||
                           input.contains("analyze") || input.contains("compare");

        if (isComplex && services.size() > 1) {
            log.debug("Smart routing: Complex query → Secondary provider");
            secondaryRequests.incrementAndGet();
            return executeOnService(services.get(1), callable);
        } else {
            log.debug("Smart routing: Simple query → Primary provider");
            primaryRequests.incrementAndGet();
            return executeOnService(services.get(0), callable);
        }
    }

    /**
     * Route based on model availability in providers.
     * Searches for the provider that supports the requested model.
     */
    private <T> T executeModelBased(String modelName, ServiceCallable<T> callable)
            throws LLMServiceException {
        if (modelName == null || modelName.trim().isEmpty()) {
            log.debug("Model-based routing: No model specified, using primary");
            primaryRequests.incrementAndGet();
            return executeOnService(getPrimaryService(), callable);
        }

        // Try to find provider that supports this model
        LLMService targetService = findServiceByModel(modelName);

        if (targetService == getPrimaryService()) {
            log.debug("Model-based routing: Model '{}' found in primary provider", modelName);
            primaryRequests.incrementAndGet();
        } else {
            log.debug("Model-based routing: Model '{}' found in secondary provider", modelName);
            secondaryRequests.incrementAndGet();
        }

        return executeOnService(targetService, callable);
    }

    /**
     * Execute operation on specific service with retry logic.
     */
    private <T> T executeOnService(LLMService service, ServiceCallable<T> callable)
            throws LLMServiceException {
        int attempts = 0;
        Exception lastException = null;

        while (attempts < maxRetries) {
            try {
                return callable.call();
            } catch (Exception e) {
                lastException = e;
                attempts++;

                if (attempts < maxRetries) {
                    log.warn("LLM service call failed (attempt {}/{}): {}",
                            attempts, maxRetries, e.getMessage());
                    try {
                        Thread.sleep(1000 * attempts); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new LLMServiceException("Interrupted during retry", ie);
                    }
                }
            }
        }

        throw new LLMServiceException(
            "Failed after " + maxRetries + " attempts",
            lastException
        );
    }

    // ============ Internal Methods ============

    private float[] generateEmbeddingInternal(Embeddings_Op op, String text, String model) throws LLMException {
        LLMService service = getPrimaryService();
        // Assuming LLMService has a method that accepts operation type
        // If not available, just use basic generateEmbedding    
        MapParam param = new MapParam();
        param.model(model);
        return service.embeddings(op, text, null);
    }

    private String generateCompletionInternal(String system,String prompt, String model) throws LLMException {
        LLMService service = getPrimaryService();
        MapParam params = new MapParam();
        params.model(model);
        var response = service.completion(system, prompt, params);
        return response.getText();
    }

    private LLMService getPrimaryService() {
        return services.get(0);
    }

    /**
     * Finds the LLM service that supports the given model name.
     * Performs case-insensitive partial matching.
     *
     * @param modelName Model name to search for
     * @return LLMService that supports the model, or primary service if not found
     */
    private LLMService findServiceByModel(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            return getPrimaryService();
        }

        String normalizedModelName = modelName.toLowerCase().trim();

        // Search through all services
        for (LLMService service : services) {
            if (serviceSupportsModel(service, normalizedModelName)) {
                return service;
            }
        }

        // If model not found in any service, use primary as fallback
        log.warn("Model '{}' not found in any provider. Falling back to primary.", modelName);
        return getPrimaryService();
    }

    /**
     * Checks if a service supports a given model.
     * Uses partial matching to handle model name variations.
     *
     * @param service LLMService to check
     * @param normalizedModelName Normalized model name (lowercase, trimmed)
     * @return true if service supports the model
     */
    private boolean serviceSupportsModel(LLMService service, String normalizedModelName) {
        try {
            // Get available models from service
            MapModels models = service.getInstalledModels();

            if (models == null || models.isEmpty()) {
                return false;
            }

            // Check for exact match
            for (String availableModel : models.keySet()) {
                String normalizedAvailable = availableModel.toLowerCase().trim();

                // Exact match
                if (normalizedAvailable.equals(normalizedModelName)) {
                    return true;
                }               
            }
         // Check for partial match
            for (String availableModel : models.keySet()) {
                String normalizedAvailable = availableModel.toLowerCase().trim();
                
                // Partial match (model name contains or is contained in available model)
                if (normalizedAvailable.contains(normalizedModelName) ||
                    normalizedModelName.contains(normalizedAvailable)) {
                    return true;
                }
            }
            
            
            
        } catch (Exception e) {
            log.debug("Error checking models for service: {}", e.getMessage());
        }

        return false;
    }

    /**
     * Calculates cosine similarity between two vectors.
     */
    private float cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1.length != vec2.length) {
            return 0f;
        }

        float dotProduct = 0f;
        float norm1 = 0f;
        float norm2 = 0f;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        if (norm1 == 0f || norm2 == 0f) {
            return 0f;
        }

        return dotProduct / (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    // ============ Statistics and Monitoring ============

    /**
     * Returns provider selection statistics.
     */
    public LLMServiceStats getStatistics() {
        return new LLMServiceStats(
            primaryRequests.get(),
            secondaryRequests.get(),
            failoverEvents.get(),
            services.size()
        );
    }

    /**
     * Resets statistics counters.
     */
    public void resetStatistics() {
        primaryRequests.set(0);
        secondaryRequests.set(0);
        failoverEvents.set(0);
        roundRobinCounter.set(0);
    }

    /**
     * Returns current strategy.
     */
    public LLMServiceStrategy getStrategy() {
        return strategy;
    }

    /**
     * Returns number of available providers.
     */
    public int getProviderCount() {
        return services.size();
    }

    /**
     * Checks if provider at index is healthy.
     */
    public boolean isProviderHealthy(int index) {
        if (index < 0 || index >= services.size()) {
            return false;
        }

        // Simple health check - try to get embedding of a test string
        try {
            LLMService service = services.get(index);
            return service.isOnline();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns a map of all registered models to their corresponding LLMService.
     * This is useful to see which service provides which models.
     * 
     * @return Map where keys are model names and values are the LLMService instances that provide them
     */
    public java.util.Map<String, LLMService> getRegisteredModelsMap() {
	
	if (modelsToService == null) {
	    modelsToService = new java.util.HashMap<>();
	}
	
	if(!modelsToService.isEmpty()) {
	    return modelsToService;
	}
        

        for (LLMService service : services) {
            try {
                MapModels registeredModels = service.getRegisteredModels();
                if (registeredModels != null && !registeredModels.isEmpty()) {
                    for (String modelName : registeredModels.keySet()) {
                        // Only add if not already present (first service wins)
                        modelsToService.putIfAbsent(modelName, service);
                    }
                    
                    // Also add aliases
                    for (java.util.Map.Entry<String, Model> entry : registeredModels.entrySet()) {
                        Model model = entry.getValue();
                        if (model != null && model.getAlias() != null) {
                            modelsToService.putIfAbsent(model.getAlias(), service);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Error getting registered models from service: {}", e.getMessage());
            }
        }

        return modelsToService;
    }

    /**
     * Returns all available models from all providers.
     *
     * @return Map of provider index to list of model names
     */
    public java.util.Map<Integer, java.util.List<String>> getAllAvailableModels() {
        java.util.Map<Integer, java.util.List<String>> modelsByProvider = new java.util.HashMap<>();

        for (int i = 0; i < services.size(); i++) {
            try {
                LLMService service = services.get(i);
                java.util.List<String> models = service.getRegisteredModelNames();
                if (models != null && !models.isEmpty()) {
                    modelsByProvider.put(i, new java.util.ArrayList<>(models));
                }
            } catch (Exception e) {
                log.warn("Failed to get models from provider {}: {}", i, e.getMessage());
                modelsByProvider.put(i, java.util.Collections.emptyList());
            }
        }

        return modelsByProvider;
    }

    /**
     * Returns all models from all providers as a flat list.
     *
     * @return List of all available model names across all providers
     */
    public java.util.List<String> getAllModels() {
        java.util.Set<String> allModels = new java.util.HashSet<>();

        for (LLMService service : services) {
            try {
                java.util.List<String> models = service.getRegisteredModelNames();
                if (models != null) {
                    allModels.addAll(models);
                }
            } catch (Exception e) {
                log.debug("Error getting models from service: {}", e.getMessage());
            }
        }

        return new java.util.ArrayList<>(allModels);
    }

    /**
     * Finds which provider supports a given model.
     *
     * @param modelName Model name to search for
     * @return Provider index (0 = primary, 1 = secondary, etc.) or -1 if not found
     */
    public int findProviderIndexByModel(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            return 0; // Return primary by default
        }

        String normalizedModelName = modelName.toLowerCase().trim();

        for (int i = 0; i < services.size(); i++) {
            if (serviceSupportsModel(services.get(i), normalizedModelName)) {
                return i;
            }
        }

        return -1; // Not found
    }

    /**
     * Gets the LLM service that supports the given model.
     * This is useful for direct access when using MODEL_BASED strategy.
     *
     * @param modelName Model name to search for
     * @return LLMService that supports the model, or null if not found
     */
    public LLMService getServiceByModel(String modelName) {
        int index = findProviderIndexByModel(modelName);
        return index >= 0 ? services.get(index) : null;
    }

    /**
     * Retrieves an LLMService from the pool that has a registered model with the given name.
     * 
     * This method searches through all available LLMService instances in the pool
     * and returns the first service that has the specified model registered.
     * The search uses the getRegisteredModels() method to check for model availability.
     * 
     * The matching is case-insensitive and supports partial matching, meaning:
     * - Exact matches are prioritized
     * - Model names containing the search term are also matched
     * - Model aliases are also searched
     * 
     * @param modelName The name of the registered model to search for
     * @return LLMService instance that has the model registered, or null if not found
     * @throws IllegalArgumentException if modelName is null or empty
     * 
     * @see LLMService#getRegisteredModels()
     */
    public LLMService getLLMServiceByRegisteredModel(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            throw new IllegalArgumentException("Model name cannot be null or empty");
        }

        String normalizedModelName = modelName.toLowerCase().trim();

        // Search through all services in the pool
        for (LLMService service : services) {
            if (serviceHasRegisteredModel(service, normalizedModelName)) {
                log.debug("Found LLMService for registered model '{}' in provider: {}", 
                         modelName, service.getServiceProvider());
                return service;
            }
        }

        log.warn("No LLMService found with registered model: {}", modelName);
        return null;
    }

    /**
     * Checks if a service has a specific model registered.
     * Uses getRegisteredModels() to check for model availability.
     *
     * @param service LLMService to check
     * @param normalizedModelName Normalized model name (lowercase, trimmed)
     * @return true if service has the model registered
     */
    private boolean serviceHasRegisteredModel(LLMService service, String normalizedModelName) {
        try {
            // Get registered models from service
            MapModels registeredModels = service.getRegisteredModels();

            if (registeredModels == null || registeredModels.isEmpty()) {
                return false;
            }

            // Check for exact match first
            for (String registeredModelName : registeredModels.keySet()) {
                String normalizedRegistered = registeredModelName.toLowerCase().trim();

                if (normalizedRegistered.equals(normalizedModelName)) {
                    return true;
                }
            }

            // Check for partial match
            for (String registeredModelName : registeredModels.keySet()) {
                String normalizedRegistered = registeredModelName.toLowerCase().trim();

                if (normalizedRegistered.contains(normalizedModelName) ||
                    normalizedModelName.contains(normalizedRegistered)) {
                    return true;
                }
            }

            // Check model aliases
            for (java.util.Map.Entry<String, Model> entry : registeredModels.entrySet()) {
                Model model = entry.getValue();
                if (model != null && model.getAlias() != null) {
                    String normalizedAlias = model.getAlias().toLowerCase().trim();
                    if (normalizedAlias.equals(normalizedModelName) ||
                        normalizedAlias.contains(normalizedModelName) ||
                        normalizedModelName.contains(normalizedAlias)) {
                        return true;
                    }
                }
            }

        } catch (Exception e) {
            log.debug("Error checking registered models for service: {}", e.getMessage());
        }

        return false;
    }

    // ============ Inner Classes ============

    /**
     * Functional interface for service calls.
     */
    @FunctionalInterface
    private interface ServiceCallable<T> {
        T call() throws Exception;
    }

    /**
     * Statistics holder.
     */
    public static class LLMServiceStats {
        private final int primaryRequests;
        private final int secondaryRequests;
        private final int failoverEvents;
        private final int providerCount;

        public LLMServiceStats(int primaryRequests, int secondaryRequests,
                              int failoverEvents, int providerCount) {
            this.primaryRequests = primaryRequests;
            this.secondaryRequests = secondaryRequests;
            this.failoverEvents = failoverEvents;
            this.providerCount = providerCount;
        }

        public int getPrimaryRequests() {
            return primaryRequests;
        }

        public int getSecondaryRequests() {
            return secondaryRequests;
        }

        public int getFailoverEvents() {
            return failoverEvents;
        }

        public int getTotalRequests() {
            return primaryRequests + secondaryRequests;
        }

        public int getProviderCount() {
            return providerCount;
        }

        public double getSecondaryUsagePercentage() {
            int total = getTotalRequests();
            return total > 0 ? (secondaryRequests * 100.0 / total) : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                "LLMServiceStats[providers=%d, primary=%d, secondary=%d, failovers=%d, secondaryUsage=%.1f%%]",
                providerCount, primaryRequests, secondaryRequests, failoverEvents,
                getSecondaryUsagePercentage()
            );
        }
    }

    /**
     * Refreshes the cached map of registered models to services.
     * This forces a re-query of all services to update the mapping.
     */
    public void refreshRegisteredModels() {
	if(this.modelsToService != null) {
	    this.modelsToService.clear();
	    this.getRegisteredModelsMap();
	}
	
    }
}
