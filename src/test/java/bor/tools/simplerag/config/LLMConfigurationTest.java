package bor.tools.simplerag.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import bor.tools.simplellm.LLMService;
import bor.tools.simplellm.MapModels;
import bor.tools.simplellm.Model;
import bor.tools.simplellm.SERVICE_PROVIDER;
import bor.tools.simplellm.exceptions.LLMException;
import bor.tools.simplerag.service.llm.LLMServiceManager;

/**
 * Test suite for LLMConfiguration class.
 *
 * Tests cover:
 * - Bean creation for single provider scenario
 * - Bean creation for multi-provider scenario
 * - Configuration property parsing
 * - Provider map access
 * - Strategy configuration
 * - Error handling
 */
@SpringBootTest
@TestPropertySource(properties = {
    "llmservice.provider.name=LM_STUDIO",
    "llmservice.provider.llm.models=qwen/qwen3-1.7b",
    "llmservice.provider.embedding.model=text-embedding-nomic-embed-text-v1.5@q8_0",
    "llmservice.provider.embedding.dimension=768",
    "llmservice.provider.api.url=http://localhost:1234/v1",
    "llmservice.strategy=FAILOVER"
})
class LLMConfigurationTest {

    @Autowired(required = false)
    private LLMConfiguration llmConfiguration;

    @Autowired(required = false)
    private LLMService primaryLLMService;

    @Autowired(required = false)
    private LLMServiceManager llmServiceManager;

    // ========== Bean Creation Tests ==========

    @Test
    void testLLMConfigurationBeanCreated() {
        assertNotNull(llmConfiguration, "LLMConfiguration bean should be created");
    }

    @Test
    void testPrimaryLLMServiceBeanCreated() {
        assertNotNull(primaryLLMService, "Primary LLMService bean should be created");
    }

    @Test
    void testPrimaryLLMServiceProviderType() {
        assertEquals(SERVICE_PROVIDER.LM_STUDIO, primaryLLMService.getServiceProvider(),
                "Primary service should be LM_STUDIO");
    }

    @Test
    void testLLMServiceManagerBeanCreated() {
        assertNotNull(llmServiceManager, "LLMServiceManager bean should be created");
    }

    @Test
    void testLLMServiceManagerHasPrimaryProvider() {
        List<LLMService> services = llmConfiguration.getActiveProviderMap().values().stream().toList();
        assertNotNull(services, "Services list should not be null");
        assertFalse(services.isEmpty(), "Services list should not be empty");
        assertTrue(services.size() >= 1, "Should have at least primary service");
    }

    // ========== Configuration Properties Tests ==========

    @Test
    void testGetActiveProviderMap() {
        Map<String, LLMService> activeProviders = llmConfiguration.getActiveProviderMap();

        assertNotNull(activeProviders, "Active provider map should not be null");
        assertFalse(activeProviders.isEmpty(), "Active provider map should not be empty");
        assertTrue(activeProviders.size() >= 1, "Should have at least one active provider");
    }

    @Test
    void testActiveProviderMapContainsPrimaryService() {
        Map<String, LLMService> activeProviders = llmConfiguration.getActiveProviderMap();

        boolean hasLMStudio = activeProviders.keySet().stream()
                .anyMatch(key -> key.contains("LM_STUDIO"));

        assertTrue(hasLMStudio, "Active providers should contain LM_STUDIO service");
    }

    @Test
    void testActiveProviderMapIsUnmodifiable() {
        Map<String, LLMService> activeProviders = llmConfiguration.getActiveProviderMap();

        assertThrows(UnsupportedOperationException.class,
                () -> activeProviders.put("test", null),
                "Active provider map should be unmodifiable");
    }

    // ========== Service Configuration Tests ==========

    @Test
    void testPrimaryServiceHasModels() throws LLMException {
        MapModels models = primaryLLMService.getInstalledModels();
        assertNotNull(models, "Primary service should have models configured");

        List<Model> modelList = models.getModels();
        assertNotNull(modelList, "Model list should not be null");
        // May be empty if no models installed, but should not be null
    }

  

    @Test
    void testPrimaryServiceConfiguration() {
        assertNotNull(primaryLLMService.getLLMConfig(),
                "Primary service should have LLMConfig");
    }

    // ========== Provider Name Parsing Tests ==========

    @Test
    void testProviderNameNormalization() {
        // This test verifies that LLMConfiguration uses LLMProviderParser correctly
        // by checking that the service was created with the correct provider
        assertEquals(SERVICE_PROVIDER.LM_STUDIO, primaryLLMService.getServiceProvider(),
                "Provider name should be normalized to LM_STUDIO");
    }
}
