package bor.tools.simplerag.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import bor.tools.simplellm.LLMService;
import bor.tools.simplerag.config.LLMServiceConfig.LLMServiceProperties;

/**
 * Test for LLMService Spring Configuration
 *
 * Verifies that LLMService bean is properly created and injected.
 *
 * This test uses minimal Spring context to avoid database dependencies.
 */
@SpringBootTest(
    classes = {
        LLMServiceConfig.class,
        MultiLLMServiceConfig.class
    }
)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    // Disable database autoconfiguration
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
        "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration"
})
class LLMServiceConfigTest {

    @Autowired(required = false)
    private LLMService llmService;

    @Autowired(required = false)
    private LLMServiceProperties llmServiceProperties;

    @Test
    void testLLMServiceBeanExists() {
        // Then - LLMService bean should be available
        assertNotNull(llmService, "LLMService bean should be created by Spring");
    }

    @Test
    void testLLMServicePropertiesBeanExists() {
        // Then - Properties bean should be available
        assertNotNull(llmServiceProperties, "LLMServiceProperties bean should be created");
    }

    @Test
    void testLLMServicePropertiesConfiguration() {
        // Given
        assertNotNull(llmServiceProperties);

        // Then - Properties should be loaded from application-test.properties
        assertNotNull(llmServiceProperties.getProviderName(), "Provider name should be configured");
        assertNotNull(llmServiceProperties.getEmbeddingModel(), "Embedding model should be configured");
        assertNotNull(llmServiceProperties.getEmbeddingDimension(), "Embedding dimension should be configured");

        assertTrue(llmServiceProperties.getEmbeddingDimension() > 0,
                "Embedding dimension should be positive");
    }

    @Test
    void testLLMServiceModelArrayParsing() {
        // Given
        assertNotNull(llmServiceProperties);

        // When
        String[] models = llmServiceProperties.getLlmModelsArray();

        // Then
        assertNotNull(models, "Models array should not be null");
        assertTrue(models.length > 0, "Should have at least one model configured");
    }

    @Test
    void testLLMServicePropertiesValues() {
        // Given
        assertNotNull(llmServiceProperties);

        // Then - Verify specific values from application-test.properties
        assertEquals("LM_STUDIO", llmServiceProperties.getProviderName());
        assertEquals(768, llmServiceProperties.getEmbeddingDimension());
        assertEquals("text-embedding-nomic-embed-text-v1.5@q8_0", llmServiceProperties.getEmbeddingModel());
        assertEquals("http://localhost:1234/v1", llmServiceProperties.getApiUrl());
    }
}
