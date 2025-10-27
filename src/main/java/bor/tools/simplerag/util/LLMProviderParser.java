package bor.tools.simplerag.util;

import bor.tools.simplellm.SERVICE_PROVIDER;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for parsing and normalizing LLM provider names.
 *
 * Consolidates provider name parsing logic that was previously duplicated
 * in LLMServiceConfig and MultiLLMServiceConfig.
 *
 * Features:
 * - Normalizes provider names (case, spaces, special characters)
 * - Handles common aliases (e.g., "LMSTUDIO" → LM_STUDIO)
 * - Validates provider names against SERVICE_PROVIDER enum
 * - Provides clear error messages for unknown providers
 *
 * @since 0.0.1
 */
@Slf4j
public class LLMProviderParser {

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private LLMProviderParser() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Parses provider name from configuration to SERVICE_PROVIDER enum.
     *
     * Supports both enum names and common aliases. Performs normalization:
     * - Trims whitespace
     * - Converts to uppercase
     * - Replaces hyphens and spaces with underscores
     * - Removes common suffixes (LLMSERVICE, .java, java)
     *
     * Supported aliases:
     * - LMSTUDIO, LM_STUDIO → LM_STUDIO
     * - OPENAI, GPT → OPENAI
     * - OLLAMA → OLLAMA
     * - ANTHROPIC, CLAUDE → ANTHROPIC
     *
     * @param name Provider name from configuration
     * @return Corresponding SERVICE_PROVIDER enum
     * @throws IllegalArgumentException if provider name is null, empty, or not recognized
     */
    public static SERVICE_PROVIDER parseProviderName(String name) {
        // Validate input
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Provider name cannot be null or empty");
        }

        // Normalize provider name
        String normalized = normalizeName(name);
        log.debug("Parsing provider name: '{}' → normalized: '{}'", name, normalized);

        // Try to parse as enum value directly
        try {
            SERVICE_PROVIDER provider = SERVICE_PROVIDER.valueOf(normalized);
            log.debug("Provider '{}' matched directly to enum: {}", name, provider);
            return provider;
        } catch (IllegalArgumentException e) {
            // Try aliases if direct match fails
            log.debug("No direct enum match for '{}', trying aliases", normalized);
            return parseProviderAlias(name, normalized);
        }
    }

    /**
     * Normalizes provider name by:
     * - Trimming whitespace
     * - Converting to uppercase
     * - Replacing hyphens and spaces with underscores
     * - Collapsing multiple underscores to single underscore
     * - Removing common suffixes (LLMSERVICE, .java, .JAVA, java, JAVA)
     *
     * @param name Raw provider name
     * @return Normalized provider name
     */
    private static String normalizeName(String name) {
        String normalized = name.trim()
                .toUpperCase()
                .replace("-", "_")
                .replace(" ", "_");

        // Collapse multiple underscores to single underscore
        while (normalized.contains("__")) {
            normalized = normalized.replace("__", "_");
        }

        // Remove common suffixes (case-insensitive already handled by toUpperCase)
        normalized = normalized.replace("LLMSERVICE", "");

        // Remove .java or .JAVA extensions
        if (normalized.endsWith(".JAVA")) {
            normalized = normalized.substring(0, normalized.length() - 5);
        }
        if (normalized.endsWith("JAVA")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }

        return normalized;
    }

    /**
     * Attempts to match normalized name against known aliases.
     *
     * @param originalName Original provider name (for error messages)
     * @param normalized Normalized provider name
     * @return Matched SERVICE_PROVIDER
     * @throws IllegalArgumentException if no alias matches
     */
    private static SERVICE_PROVIDER parseProviderAlias(String originalName, String normalized) {
        SERVICE_PROVIDER provider = switch (normalized) {
            case "LMSTUDIO", "LM_STUDIO" -> SERVICE_PROVIDER.LM_STUDIO;
            case "OPENAI", "OPEN_AI", "GPT" -> SERVICE_PROVIDER.OPENAI;
            case "OLLAMA" -> SERVICE_PROVIDER.OLLAMA;
            case "ANTHROPIC", "CLAUDE" -> SERVICE_PROVIDER.ANTHROPIC;
            /*
             * Commented out providers - uncomment when supported in SERVICE_PROVIDER:
             * case "GEMINI", "GOOGLE" -> SERVICE_PROVIDER.GEMINI;
             * case "COHERE" -> SERVICE_PROVIDER.COHERE;
             * case "HUGGINGFACE", "HF" -> SERVICE_PROVIDER.HUGGINGFACE;
             */
            default -> null;
        };

        if (provider != null) {
            log.debug("Provider '{}' matched via alias to: {}", originalName, provider);
            return provider;
        }

        // No match found - throw detailed error
        String supportedProviders = getSupportedProvidersString();
        String errorMessage = String.format(
            "Unknown LLM provider: '%s' (normalized: '%s'). Supported providers: %s",
            originalName, normalized, supportedProviders
        );
        log.error(errorMessage);
        throw new IllegalArgumentException(errorMessage);
    }

    /**
     * Returns formatted string of supported providers for error messages.
     *
     * @return Comma-separated list of supported provider names
     */
    private static String getSupportedProvidersString() {
        return "LM_STUDIO, OPENAI (GPT), OLLAMA, ANTHROPIC (CLAUDE)";
        // Add more when supported: ", GEMINI (GOOGLE), COHERE, HUGGINGFACE (HF)"
    }

    /**
     * Validates if a provider name is supported (can be parsed).
     *
     * @param name Provider name to validate
     * @return true if provider is supported, false otherwise
     */
    public static boolean isProviderSupported(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        try {
            parseProviderName(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Gets the normalized form of a provider name without parsing to enum.
     *
     * Useful for logging, comparison, or debugging purposes.
     *
     * @param name Provider name
     * @return Normalized provider name
     * @throws IllegalArgumentException if name is null or empty
     */
    public static String getNormalizedName(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Provider name cannot be null or empty");
        }
        return normalizeName(name);
    }
}
