package bor.tools.simplerag.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import bor.tools.simplellm.LLMConfig;
import bor.tools.simplellm.LLMService;
import bor.tools.simplellm.SERVICE_PROVIDER;
import bor.tools.simplerag.config.LLMConfiguration;
import bor.tools.simplellm.Model;
import bor.tools.simplellm.Model_Type;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller for LLM Service information.
 *
 * Provides endpoints to query:
 * - Available LLM providers and their status
 * - Current configurations in use
 * - Installed models
 * - Registered models with online status
 */
@RestController
@RequestMapping("/api/v1/llm")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "LLM Info", description = "LLM Service information and status endpoints")
public class LLMInfoController {

    private final LLMService llmService;

    private final LLMConfiguration llmConfiguration;

    /**
     * Get available LLM providers and their online status.
     *
     * @return List of providers with online status
     */
    @GetMapping("/providers")
    @Operation(
        summary = "Get available LLM providers",
        description = "Returns a list of all LLM service providers and their online status",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved provider status",
                content = @Content(schema = @Schema(implementation = ProviderStatusResponse.class))
            )
        }
    )
    public ResponseEntity<List<ProviderStatus>> getProviders() {
        log.debug("Getting LLM providers status");

        List<ProviderStatus> providers = new ArrayList<>();

        // Check all known providers
        Map<String,LLMService> services = llmConfiguration.getActiveProviderMap();
        for ( LLMService llm : services.values()) {
            boolean isOnline = false;
            try {        	
                isOnline = llm.isOnline();
            } catch (Exception e) {
                log.debug("Failed to check status for provider {}: {}", llm.getServiceProvider(), e.getMessage());
            }

            providers.add(new ProviderStatus(llm.getServiceProvider().name(), isOnline));
        }

        log.info("Retrieved status for {} providers", providers.size());
        return ResponseEntity.ok(providers);
    }

    /**
     * Get current LLM configurations in use.
     *
     * @return Map of provider name to LLMConfig
     */
    @GetMapping("/configurations")
    @Operation(
        summary = "Get LLM configurations",
        description = "Returns the current configurations for all registered LLM service providers",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved configurations"
            )
        }
    )
    public ResponseEntity<Map<String, LLMConfig>> getConfigurations() {
	log.debug("Getting LLM configurations");

	Map<String, LLMConfig> configurations = new HashMap<>();

	try {
	    Map<String, LLMService> services = llmConfiguration.getActiveProviderMap();
	    
	    for (LLMService llmService : services.values()) {
		// Get current service provider
		SERVICE_PROVIDER currentProvider = llmService.getServiceProvider();

		// Get configuration for current provider
		LLMConfig config = llmService.getLLMConfig();

		if (config != null) {
		    configurations.put(currentProvider.name(), config);
		}
	    }
	    
	    log.info("Retrieved configurations for {} providers", configurations.size());
	    return ResponseEntity.ok(configurations);

	} catch (Exception e) {
	    log.error("Failed to retrieve configurations: {}", e.getMessage(), e);
	    return ResponseEntity.ok(configurations); // Return empty map on error
	}
    }

    /**
     * Get installed models available in the LLM service.
     *
     * @return List of installed models with provider and types
     */
    @GetMapping("/models/installed")
    @Operation(
        summary = "Get installed models",
        description = "Returns all models that are installed and available in the LLM services",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved installed models",
                content = @Content(schema = @Schema(implementation = InstalledModelResponse.class))
            )
        }
    )
    public ResponseEntity<List<ModelInfo>> getInstalledModels() {
	log.debug("Getting installed models");

	List<ModelInfo> modelInfoList = new ArrayList<>();

	try {
	    Map<String, LLMService> services = llmConfiguration.getActiveProviderMap();
	    for (LLMService llmService : services.values()) {
		SERVICE_PROVIDER currentProvider = llmService.getServiceProvider();
		List<Model> installedModels = llmService.getInstalledModels().getModels();

		if (installedModels != null) {
		    for (Model model : installedModels) {
			List<String> types = extractModelTypes(model);
			modelInfoList.add(new ModelInfo(currentProvider.name(), model, types, null // online status not
												   // applicable for
												   // installed models
			));
		    }
		}
	    }
	    log.info("Retrieved {} installed models", modelInfoList.size());
	    return ResponseEntity.ok(modelInfoList);

	} catch (Exception e) {
	    log.error("Failed to retrieve installed models: {}", e.getMessage(), e);
	    return ResponseEntity.ok(modelInfoList); // Return empty list on error
	}
    }

    /**
     * Get registered models with their online status.
     *
     * @return List of registered models with provider, types, and online status
     */
    @GetMapping("/models/registered")
    @Operation(
        summary = "Get registered models",
        description = "Returns all models that are registered for use, including their online status",
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved registered models",
                content = @Content(schema = @Schema(implementation = RegisteredModelResponse.class))
            )
        }
    )
    public ResponseEntity<List<ModelInfo>> getRegisteredModels() {
        log.debug("Getting registered models");

        List<ModelInfo> modelInfoList = new ArrayList<>();

        try {            
            Map<String,LLMService> services = llmConfiguration.getActiveProviderMap();
            
            for ( LLMService llmService : services.values()) {            
		SERVICE_PROVIDER currentProvider = llmService.getServiceProvider();
		List<Model> registeredModels = llmService.getRegisteredModels().getModels();

		if (registeredModels != null) {
		    for (Model model : registeredModels) {
			List<String> types = extractModelTypes(model);
			// Check if model is online
			boolean isOnline = false;
			try {
			    isOnline = llmService.isModelOnline(model);
			} catch (Exception e) {
			    log.debug("Failed to check online status for model {}: {}", model.getName(),
				    e.getMessage());
			}

			modelInfoList.add(new ModelInfo(currentProvider.name(), model, types, isOnline));
		    }
		}
	    }
            log.info("Retrieved {} registered models", modelInfoList.size());
            return ResponseEntity.ok(modelInfoList);

        } catch (Exception e) {
            log.error("Failed to retrieve registered models: {}", e.getMessage(), e);
            return ResponseEntity.ok(modelInfoList); // Return empty list on error
        }
    }

    /**
     * Extract model types from a Model object.
     * Returns the model types as a list of strings.
     */
    private List<String> extractModelTypes(Model model) {
        List<String> types = new ArrayList<>();

        if (model == null) {
            return types;
        }

        // Get model types from the Model object
        List<Model_Type> modelTypes = model.getTypes();

        if (modelTypes != null) {
            for (Model_Type type : modelTypes) {
                types.add(type.name());
            }
        }

        return types;
    }

    // ========== DTOs for API Responses ==========

    /**
     * DTO for provider status information.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "LLM service provider status")
    public static class ProviderStatus {
        @Schema(description = "Service provider name", example = "LM_STUDIO")
        private String serviceProvider;

        @Schema(description = "Whether the provider is online", example = "true")
        private boolean online;
    }

    /**
     * DTO for model information.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Model information with provider and status")
    public static class ModelInfo {
        @Schema(description = "Service provider name", example = "LM_STUDIO")
        private String serviceProvider;

        @Schema(description = "Model details")
        private Model model;

        @Schema(description = "Model types (LANGUAGE, EMBEDDING, etc.)",
                example = "[\"LANGUAGE\", \"EMBEDDING\"]")
        private List<String> types;

        @Schema(description = "Whether the model is online (null if not applicable)",
                example = "true")
        private Boolean online;
    }

    /**
     * Response wrapper for providers endpoint (for Swagger documentation).
     */
    @Schema(description = "Response containing list of provider statuses")
    public static class ProviderStatusResponse {
        @Schema(description = "List of provider statuses")
        private List<ProviderStatus> providers;
    }

    /**
     * Response wrapper for installed models endpoint (for Swagger documentation).
     */
    @Schema(description = "Response containing list of installed models")
    public static class InstalledModelResponse {
        @Schema(description = "List of installed models")
        private List<ModelInfo> models;
    }

    /**
     * Response wrapper for registered models endpoint (for Swagger documentation).
     */
    @Schema(description = "Response containing list of registered models")
    public static class RegisteredModelResponse {
        @Schema(description = "List of registered models")
        private List<ModelInfo> models;
    }
}
