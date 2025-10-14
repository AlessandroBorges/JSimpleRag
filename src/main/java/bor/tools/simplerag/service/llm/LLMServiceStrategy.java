package bor.tools.simplerag.service.llm;

/**
 * Strategy for managing multiple LLM service providers.
 *
 * Defines how requests are distributed across primary and secondary providers.
 */
public enum LLMServiceStrategy {

    /**
     * Use only the primary provider.
     * Secondary provider is ignored.
     *
     * Use case: Single provider deployment, secondary for admin/testing only
     */
    PRIMARY_ONLY,

    /**
     * Use primary provider, fallback to secondary on failure.
     * This is the default and recommended strategy.
     *
     * Flow:
     * 1. Try primary provider
     * 2. If fails (timeout, error), try secondary
     * 3. If secondary also fails, throw exception
     *
     * Use case: High availability with backup provider
     */
    FAILOVER,

    /**
     * Distribute requests evenly between providers (round-robin).
     *
     * Flow:
     * 1. Request 1 → Primary
     * 2. Request 2 → Secondary
     * 3. Request 3 → Primary
     * 4. And so on...
     *
     * Use case: Load balancing, cost distribution
     */
    ROUND_ROBIN,

    /**
     * Use primary for embeddings, secondary for completions (or vice versa).
     * Allows specialization of providers by operation type.
     *
     * Configuration determines which provider handles which operation.
     *
     * Use case: Different models for different tasks
     * Example: Fast local embedding + GPT-4 for complex reasoning
     */
    SPECIALIZED,

    /**
     * Always try both providers and compare results.
     * Returns the "best" result based on defined criteria.
     *
     * Use case: Quality assurance, A/B testing
     * Note: Doubles the cost and latency
     */
    DUAL_VERIFICATION,

    /**
     * Intelligent routing based on request characteristics.
     * - Simple queries → Primary (fast, local)
     * - Complex queries → Secondary (powerful, cloud)
     *
     * Use case: Cost optimization with hybrid deployment
     */
    SMART_ROUTING,

    /**
     * Route requests based on the model name specified.
     * Automatically selects the provider that supports the requested model.
     *
     * Flow:
     * 1. Check if primary provider supports the model
     * 2. If not, check secondary provider
     * 3. If model not found, fallback to primary
     *
     * Use case: Multiple providers with different models
     * Example: Local models (llama, mistral) + Cloud models (gpt-4, claude)
     */
    MODEL_BASED
}
