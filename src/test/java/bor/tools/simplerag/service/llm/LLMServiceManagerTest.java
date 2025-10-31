package bor.tools.simplerag.service.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import bor.tools.simplellm.CompletionResponse;
import bor.tools.simplellm.Embeddings_Op;
import bor.tools.simplellm.LLMService;
import bor.tools.simplellm.exceptions.LLMException;
import bor.tools.simplerag.service.llm.LLMServiceManager.LLMServiceStats;

/**
 * Unit tests for LLMServiceManager.
 *
 * Tests failover, load balancing, and strategy behaviors.
 */
class LLMServiceManagerTest {

    private LLMService primaryService;
    private LLMService secondaryService;
    private LLMServiceManager manager;

    private static final float[] TEST_VECTOR = new float[] { 0.1f, 0.2f, 0.3f };
    private static final String TEST_COMPLETION = "Test response";

    @BeforeEach
    void setUp() throws LLMException {
	// Create mocks
	primaryService = mock(LLMService.class);
	secondaryService = mock(LLMService.class);

	// Mock Response objects for completion calls
	CompletionResponse primaryResponse = mock(CompletionResponse.class);
	CompletionResponse secondaryResponse = mock(CompletionResponse.class);

	when(primaryResponse.getText()).thenReturn(TEST_COMPLETION);
	when(secondaryResponse.getText()).thenReturn(TEST_COMPLETION);

	// Configure embeddings - accept any parameters
	when(primaryService.embeddings(any(Embeddings_Op.class), anyString(), any())).thenReturn(TEST_VECTOR);
	when(secondaryService.embeddings(any(Embeddings_Op.class), anyString(), any())).thenReturn(TEST_VECTOR);

	// Configure completion - accept any parameters
	when(primaryService.completion(anyString(), anyString(), any())).thenReturn(primaryResponse);
	when(secondaryService.completion(anyString(), anyString(), any())).thenReturn(secondaryResponse);

	// Configure isOnline for health checks
	when(primaryService.isOnline()).thenReturn(true);
	when(secondaryService.isOnline()).thenReturn(true);

	// Configure model names for MODEL_BASED strategy (will be overridden in
	// specific tests)
	when(primaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("default-model"));
	when(secondaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("default-model"));
    }

    // ============ PRIMARY_ONLY Strategy Tests ============

    @Test
    void testPrimaryOnlyStrategy_UsesOnlyPrimary() throws LLMException {
	// Given
	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.PRIMARY_ONLY, 3, 30);

	// When
	float[] result = manager.embeddings(Embeddings_Op.QUERY, "World Cup");

	// Then
	assertNotNull(result);
	verify(primaryService, times(1)).embeddings(any(Embeddings_Op.class), anyString(), any());
	verify(secondaryService, times(0)).embeddings(any(Embeddings_Op.class), anyString(), any());

	// Verify stats
	LLMServiceStats stats = manager.getStatistics();
	assertEquals(1, stats.getPrimaryRequests());
	assertEquals(0, stats.getSecondaryRequests());
    }

    // ============ FAILOVER Strategy Tests ============

    @Test
    void testFailoverStrategy_PrimarySuccess() throws LLMException {
	// Given
	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.FAILOVER, 3, 30);

	// When
	float[] result = manager.embeddings(Embeddings_Op.QUERY, "test", null);

	// Then
	assertNotNull(result);
	verify(primaryService, times(1)).embeddings(any(Embeddings_Op.class), anyString(), any());
	verify(secondaryService, times(0)).embeddings(any(Embeddings_Op.class), anyString(), any());

	LLMServiceStats stats = manager.getStatistics();
	assertEquals(0, stats.getFailoverEvents());
    }

    @Test
    void testFailoverStrategy_PrimaryFailsSecondarySucceeds() throws LLMException {
	// Given - Primary fails, but secondary succeeds
	when(primaryService.embeddings(any(Embeddings_Op.class), anyString(), any()))
		.thenThrow(new RuntimeException("Primary failed"));

	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.FAILOVER, 3, 30);

	// When
	float[] result = manager.embeddings(Embeddings_Op.QUERY, "test");

	// Then
	assertNotNull(result);
	verify(primaryService, times(3)).embeddings(any(Embeddings_Op.class), anyString(), any()); // Retries
	verify(secondaryService, times(1)).embeddings(any(Embeddings_Op.class), anyString(), any());

	LLMServiceStats stats = manager.getStatistics();
	assertEquals(1, stats.getFailoverEvents());
	assertEquals(1, stats.getSecondaryRequests());
    }

    @Test
    void testFailoverStrategy_BothFail() throws LLMException {
	// Given - Both fail
	when(primaryService.embeddings(any(Embeddings_Op.class), anyString(), any()))
		.thenThrow(new RuntimeException("Primary failed"));
	when(secondaryService.embeddings(any(Embeddings_Op.class), anyString(), any()))
		.thenThrow(new RuntimeException("Secondary failed"));

	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.FAILOVER, 2, 30);

	// When/Then
	assertThrows(LLMServiceException.class, () -> {
	    manager.embeddings(Embeddings_Op.QUERY, "test");
	});
    }

    // ============ ROUND_ROBIN Strategy Tests ============

    @Test
    void testRoundRobinStrategy_AlternatesProviders() throws LLMException {
	// Given
	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.ROUND_ROBIN, 3, 30);

	// When - Make 4 requests
	manager.embeddings(Embeddings_Op.QUERY, "request1");
	manager.embeddings(Embeddings_Op.QUERY, "request2");
	manager.embeddings(Embeddings_Op.QUERY, "request3");
	manager.embeddings(Embeddings_Op.QUERY, "request4");

	// Then - Should alternate: P, S, P, S
	verify(primaryService, times(2)).embeddings(any(Embeddings_Op.class), anyString(), any());
	verify(secondaryService, times(2)).embeddings(any(Embeddings_Op.class), anyString(), any());

	LLMServiceStats stats = manager.getStatistics();
	assertEquals(2, stats.getPrimaryRequests());
	assertEquals(2, stats.getSecondaryRequests());
	assertEquals(50.0, stats.getSecondaryUsagePercentage(), 0.1);
    }

    // ============ SPECIALIZED Strategy Tests ============

    @Test
    void testSpecializedStrategy_EmbeddingUsesPrimary() throws LLMException {
	// Given
	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.SPECIALIZED, 3, 30);

	// When
	manager.embeddings(Embeddings_Op.QUERY, "test");

	// Then - Embeddings use primary
	verify(primaryService, times(1)).embeddings(any(Embeddings_Op.class), anyString(), any());
	verify(secondaryService, times(0)).embeddings(any(Embeddings_Op.class), anyString(), any());
    }

    @Test
    void testSpecializedStrategy_CompletionUsesSecondary() throws LLMException {
	// Given
	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.SPECIALIZED, 3, 30);

	String system = "You are a helpfull assistant";
	String model = "qwen/qwen3-1.7b";

	// When
	String result = manager.generateCompletion(system, "prompt", model);

	// Then - Completions use secondary (more powerful)
	assertNotNull(result);
	verify(primaryService, times(0)).completion(anyString(), anyString(), any());
	verify(secondaryService, times(1)).completion(anyString(), anyString(), any());
    }

    // ============ DUAL_VERIFICATION Strategy Tests ============

    @Test
    void testDualVerificationStrategy_CallsBothProviders() throws LLMException {
	// Given
	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.DUAL_VERIFICATION, 3, 30);

	// When
	float[] result = manager.embeddings(Embeddings_Op.QUERY, "test");

	// Then - Both providers called
	assertNotNull(result);
	verify(primaryService, times(1)).embeddings(any(Embeddings_Op.class), anyString(), any());
	verify(secondaryService, times(1)).embeddings(any(Embeddings_Op.class), anyString(), any());

	LLMServiceStats stats = manager.getStatistics();
	assertEquals(1, stats.getPrimaryRequests());
	assertEquals(1, stats.getSecondaryRequests());
    }

    // ============ SMART_ROUTING Strategy Tests ============

    @Test
    void testSmartRoutingStrategy_SimpleQueryUsesPrimary() throws LLMException {
	// Given
	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.SMART_ROUTING, 3, 30);

	// When - Simple short query
	manager.embeddings(Embeddings_Op.QUERY, "test");

	// Then - Should use primary (fast)
	verify(primaryService, times(1)).embeddings(any(Embeddings_Op.class), anyString(), any());
	verify(secondaryService, times(0)).embeddings(any(Embeddings_Op.class), anyString(), any());
    }

    @Test
    void testSmartRoutingStrategy_ComplexQueryUsesSecondary() throws LLMException {
	// Given
	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.SMART_ROUTING, 3, 30);

	// When - Complex query with keyword
	String complexQuery = "Please explain in detail the architectural patterns of "
		+ "microservices and compare them with monolithic architectures";
	manager.embeddings(Embeddings_Op.QUERY, complexQuery);

	// Then - Should use secondary (powerful)
	verify(primaryService, times(0)).embeddings(any(Embeddings_Op.class), anyString(), any());
	verify(secondaryService, times(1)).embeddings(any(Embeddings_Op.class), anyString(), any());
    }

    @Test
    void testSmartRoutingStrategy_LongQueryUsesSecondary() throws LLMException {
	// Given
	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.SMART_ROUTING, 3, 30);

	// When - Very long query (> 1000 chars)
	StringBuilder longQuery = new StringBuilder();
	for (int i = 0; i < 200; i++) {
	    longQuery.append("word ");
	}
	manager.embeddings(Embeddings_Op.QUERY, longQuery.toString());

	// Then - Should use secondary (powerful)
	verify(secondaryService, times(1)).embeddings(any(Embeddings_Op.class), anyString(), any());
    }

    // ============ Statistics Tests ============

    @Test
    void testStatistics_Tracking() {
	// Given
	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.ROUND_ROBIN, 3, 30);

	// When
	manager.embeddings(Embeddings_Op.QUERY, "test1");
	manager.embeddings(Embeddings_Op.QUERY, "test2");
	manager.embeddings(Embeddings_Op.QUERY, "test3");

	// Then
	LLMServiceStats stats = manager.getStatistics();
	assertEquals(3, stats.getTotalRequests());
	assertEquals(2, stats.getProviderCount());
	assertTrue(stats.getSecondaryUsagePercentage() > 0);
    }

    @Test
    void testStatistics_Reset() {
	// Given
	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.PRIMARY_ONLY, 3, 30);

	manager.embeddings(Embeddings_Op.QUERY, "test");
	assertEquals(1, manager.getStatistics().getTotalRequests());

	// When
	manager.resetStatistics();

	// Then
	assertEquals(0, manager.getStatistics().getTotalRequests());
    }

    // ============ Configuration Tests ============

    @Test
    void testManagerConfiguration_SingleProvider() {
	// Given
	List<LLMService> services = Arrays.asList(primaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.FAILOVER, 3, 30);

	// Then
	assertEquals(1, manager.getProviderCount());
	assertEquals(LLMServiceStrategy.FAILOVER, manager.getStrategy());
    }

    @Test
    void testManagerConfiguration_InvalidServices() {
	// When/Then
	assertThrows(IllegalArgumentException.class, () -> {
	    new LLMServiceManager(null, LLMServiceStrategy.FAILOVER, 3, 30);
	});

	assertThrows(IllegalArgumentException.class, () -> {
	    new LLMServiceManager(Arrays.asList(), LLMServiceStrategy.FAILOVER, 3, 30);
	});
    }

    @Test
    void testManagerConfiguration_DefaultStrategy() {
	// Given - null strategy
	List<LLMService> services = Arrays.asList(primaryService);
	manager = new LLMServiceManager(services, null, 3, 30);

	// Then - Should default to FAILOVER
	assertEquals(LLMServiceStrategy.FAILOVER, manager.getStrategy());
    }

    // ============ Health Check Tests ============

    @Test
    void testHealthCheck_ProviderHealthy() {
	// Given
	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.FAILOVER, 3, 30);

	// When/Then
	assertTrue(manager.isProviderHealthy(0));
	assertTrue(manager.isProviderHealthy(1));
    }

    // ============ MODEL_BASED Strategy Tests ============

    @Test
    void testModelBasedStrategy_FindsModelInPrimary() throws LLMException {
	// Given
	when(primaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("llama2", "mistral", "qwen"));
	when(secondaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("gpt-3.5-turbo", "gpt-4"));

	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.MODEL_BASED, 3, 30);

	// When - Request with model from primary
	manager.embeddings(Embeddings_Op.QUERY, "test", "llama2");

	// Then - Should use primary
	verify(primaryService, times(1)).embeddings(any(Embeddings_Op.class), anyString(), any());
	verify(secondaryService, times(0)).embeddings(any(Embeddings_Op.class), anyString(), any());

	LLMServiceStats stats = manager.getStatistics();
	assertEquals(1, stats.getPrimaryRequests());
	assertEquals(0, stats.getSecondaryRequests());
    }

    @Test
    void testModelBasedStrategy_FindsModelInSecondary() throws LLMException {
	// Given
	when(primaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("llama2", "mistral"));
	when(secondaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("gpt-3.5-turbo", "gpt-4"));

	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.MODEL_BASED, 3, 30);

	// When - Request with model from secondary
	manager.embeddings(Embeddings_Op.QUERY, "test", "gpt-4");

	// Then - Should use secondary
	verify(primaryService, times(0)).embeddings(any(Embeddings_Op.class), anyString(), any());
	verify(secondaryService, times(1)).embeddings(any(Embeddings_Op.class), anyString(), any());

	LLMServiceStats stats = manager.getStatistics();
	assertEquals(0, stats.getPrimaryRequests());
	assertEquals(1, stats.getSecondaryRequests());
    }

    @Test
    void testModelBasedStrategy_ModelNotFound_FallbackToPrimary() throws LLMException {
	// Given
	when(primaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("llama2", "mistral"));
	when(secondaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("gpt-3.5-turbo"));

	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.MODEL_BASED, 3, 30);

	// When - Request with unknown model
	manager.embeddings(Embeddings_Op.QUERY, "test", "unknown-model");

	// Then - Should fallback to primary
	verify(primaryService, times(1)).embeddings(any(Embeddings_Op.class), anyString(), any());

	LLMServiceStats stats = manager.getStatistics();
	assertEquals(1, stats.getPrimaryRequests());
    }

    @Test
    void testModelBasedStrategy_PartialMatch() throws LLMException {
	// Given
	when(primaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("llama2-7b", "mistral-7b"));
	when(secondaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("gpt-4-turbo"));

	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.MODEL_BASED, 3, 30);

	// When - Request with partial model name
	manager.embeddings(Embeddings_Op.QUERY, "test", "llama2");

	// Then - Should find and use primary (partial match)
	verify(primaryService, times(1)).embeddings(any(Embeddings_Op.class), anyString(), any());

	LLMServiceStats stats = manager.getStatistics();
	assertEquals(1, stats.getPrimaryRequests());
    }

    @Test
    void testModelBasedStrategy_CaseInsensitive() throws LLMException {
	// Given
	when(primaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("Llama2", "Mistral"));
	when(secondaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("GPT-4"));

	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.MODEL_BASED, 3, 30);

	// When - Request with lowercase model name
	manager.embeddings(Embeddings_Op.QUERY, "test", "gpt-4");

	// Then - Should find and use secondary (case-insensitive)
	verify(secondaryService, times(1)).embeddings(any(Embeddings_Op.class), anyString(), any());

	LLMServiceStats stats = manager.getStatistics();
	assertEquals(1, stats.getSecondaryRequests());
    }

    @Test
    void testModelBasedStrategy_Completion() throws LLMException {
	// Given
	when(primaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("llama2", "mistral"));
	when(secondaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("gpt-3.5-turbo", "gpt-4"));

	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.MODEL_BASED, 3, 30);

	String system = "You are a helpful assistant";
	String model = "gpt-4";

	// When - Completion request with secondary model
	String result = manager.generateCompletion(system, "test prompt", model);

	// Then - Should use secondary
	assertNotNull(result);
	verify(secondaryService, times(1)).completion(anyString(), anyString(), any());
	verify(primaryService, times(0)).completion(anyString(), anyString(), any());
    }

    // ============ Model Discovery Tests ============

    @Test
    void testGetAllModels_ReturnsCombinedList() throws LLMException {
	// Given
	when(primaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("llama2", "mistral"));
	when(secondaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("gpt-3.5-turbo", "gpt-4"));

	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.MODEL_BASED, 3, 30);

	// When
	List<String> allModels = manager.getAllModels();

	// Then
	assertNotNull(allModels);
	assertEquals(4, allModels.size());
	assertTrue(allModels.contains("llama2"));
	assertTrue(allModels.contains("mistral"));
	assertTrue(allModels.contains("gpt-3.5-turbo"));
	assertTrue(allModels.contains("gpt-4"));
    }

    @Test
    void testGetAllAvailableModels_ReturnsModelsByProvider() throws LLMException {
	// Given
	when(primaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("llama2", "mistral"));
	when(secondaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("gpt-4"));

	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.MODEL_BASED, 3, 30);

	// When
	java.util.Map<Integer, List<String>> modelsByProvider = manager.getAllAvailableModels();

	// Then
	assertNotNull(modelsByProvider);
	assertEquals(2, modelsByProvider.size());
	assertEquals(2, modelsByProvider.get(0).size());
	assertEquals(1, modelsByProvider.get(1).size());
	assertTrue(modelsByProvider.get(0).contains("llama2"));
	assertTrue(modelsByProvider.get(1).contains("gpt-4"));
    }

    @Test
    void testFindProviderIndexByModel_ReturnsCorrectIndex() throws LLMException {
	// Given
	when(primaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("qwen3"));
	when(secondaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("PHI-3.5", "PHI-4"));

	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.MODEL_BASED, 3, 30);

	// When/Then
	assertEquals(0, manager.findProviderIndexByModel("qwen3"));
	assertEquals(1, manager.findProviderIndexByModel("PHI-4"));
	assertEquals(-1, manager.findProviderIndexByModel("unknown-model"));
    }

    @Test
    void testGetServiceByModel_ReturnsCorrectService() throws LLMException {
	// Given
	when(primaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("llama2"));
	when(secondaryService.getRegisteredModelNames()).thenReturn(Arrays.asList("gpt-4"));

	List<LLMService> services = Arrays.asList(primaryService, secondaryService);
	manager = new LLMServiceManager(services, LLMServiceStrategy.MODEL_BASED, 3, 30);

	// When/Then
	assertEquals(primaryService, manager.getServiceByModel("llama2"));
	assertEquals(secondaryService, manager.getServiceByModel("gpt-4"));
	assertEquals(null, manager.getServiceByModel("unknown-model"));
    }
}
