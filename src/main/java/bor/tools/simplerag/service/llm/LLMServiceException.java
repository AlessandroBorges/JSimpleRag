package bor.tools.simplerag.service.llm;

/**
 * Exception thrown when LLM service operations fail.
 *
 * This exception wraps underlying provider-specific exceptions
 * and provides a unified error handling mechanism.
 */
public class LLMServiceException extends RuntimeException {

    private final String providerName;
    private final ErrorType errorType;

    public LLMServiceException(String message) {
        super(message);
        this.providerName = null;
        this.errorType = ErrorType.UNKNOWN;
    }

    public LLMServiceException(String message, Throwable cause) {
        super(message, cause);
        this.providerName = null;
        this.errorType = ErrorType.UNKNOWN;
    }

    public LLMServiceException(String message, String providerName, ErrorType errorType) {
        super(message);
        this.providerName = providerName;
        this.errorType = errorType;
    }

    public LLMServiceException(String message, Throwable cause, String providerName, ErrorType errorType) {
        super(message, cause);
        this.providerName = providerName;
        this.errorType = errorType;
    }

    public String getProviderName() {
        return providerName;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    /**
     * Types of LLM service errors.
     */
    public enum ErrorType {
        /** Unknown or unclassified error */
        UNKNOWN,

        /** Network connectivity error */
        NETWORK_ERROR,

        /** Authentication/authorization error */
        AUTH_ERROR,

        /** Request timeout */
        TIMEOUT,

        /** Rate limit exceeded */
        RATE_LIMIT,

        /** Invalid request parameters */
        INVALID_REQUEST,

        /** Model not found or unavailable */
        MODEL_NOT_FOUND,

        /** Insufficient quota/credits */
        QUOTA_EXCEEDED,

        /** Provider service unavailable */
        SERVICE_UNAVAILABLE,

        /** Invalid response from provider */
        INVALID_RESPONSE,

        /** Configuration error */
        CONFIG_ERROR
    }

    /**
     * Returns a user-friendly error message.
     */
    public String getUserMessage() {
        StringBuilder sb = new StringBuilder();

        if (providerName != null) {
            sb.append("LLM Provider '").append(providerName).append("': ");
        }

        switch (errorType) {
            case NETWORK_ERROR:
                sb.append("Network connection failed. Please check your internet connection.");
                break;
            case AUTH_ERROR:
                sb.append("Authentication failed. Please verify your API key.");
                break;
            case TIMEOUT:
                sb.append("Request timed out. The service may be overloaded.");
                break;
            case RATE_LIMIT:
                sb.append("Rate limit exceeded. Please try again later.");
                break;
            case INVALID_REQUEST:
                sb.append("Invalid request. Please check your input parameters.");
                break;
            case MODEL_NOT_FOUND:
                sb.append("Model not found or unavailable.");
                break;
            case QUOTA_EXCEEDED:
                sb.append("Quota exceeded. Please check your account limits.");
                break;
            case SERVICE_UNAVAILABLE:
                sb.append("Service temporarily unavailable. Please try again later.");
                break;
            case INVALID_RESPONSE:
                sb.append("Received invalid response from service.");
                break;
            case CONFIG_ERROR:
                sb.append("Configuration error. Please check your settings.");
                break;
            default:
                sb.append(getMessage());
        }

        return sb.toString();
    }
}
