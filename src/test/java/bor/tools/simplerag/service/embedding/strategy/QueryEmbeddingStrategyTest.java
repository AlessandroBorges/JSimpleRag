package bor.tools.simplerag.service.embedding.strategy;

import bor.tools.simplellm.Embeddings_Op;
import bor.tools.simplellm.LLMProvider;
import bor.tools.simplellm.MapParam;
import bor.tools.simplellm.SERVICE_PROVIDER;
import bor.tools.simplellm.exceptions.LLMException;
import bor.tools.simplerag.dto.DocumentEmbeddingDTO;
import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.service.embedding.model.EmbeddingContext;
import bor.tools.simplerag.service.embedding.model.EmbeddingRequest;
import bor.tools.simplerag.service.llm.LLMServiceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for QueryEmbeddingStrategy.
 *
 * Tests query embedding generation with LLMServiceManager integration.
 */
@ExtendWith(MockitoExtension.class)
class QueryEmbeddingStrategyTest {

    @Mock
    private LLMServiceManager llmServiceManager;

    @Mock
    private LLMProvider mockLLMService;

    @InjectMocks
    private QueryEmbeddingStrategy strategy;

    private LibraryDTO testLibrary;
    private EmbeddingContext testContext;
    private float[] mockEmbedding;

    @BeforeEach
    void setUp() {
        // Set default embedding model via reflection
        ReflectionTestUtils.setField(strategy, "defaultEmbeddingModel", "nomic-embed-text");

        // Create test library
        testLibrary = new LibraryDTO();
        testLibrary.setId(1);
        testLibrary.setNome("Test Library");

        // Create test context
        testContext = EmbeddingContext.builder()
                .library(testLibrary)
                .build();

        // Create mock embedding
        mockEmbedding = new float[]{0.1f, 0.2f, 0.3f, 0.4f, 0.5f};
    }

    @Test
    void testGenerateQueryVector_Success() throws LLMException {
        // Arrange
        String queryText = "What is RAG?";
        String modelName = "nomic-embed-text";

        when(llmServiceManager.getLLMServiceByRegisteredModel(modelName))
                .thenReturn(mockLLMService);
        when(mockLLMService.getServiceProvider()).thenReturn(SERVICE_PROVIDER.OPENAI);
        when(mockLLMService.embeddings(eq(Embeddings_Op.QUERY), eq(queryText), any(MapParam.class)))
                .thenReturn(mockEmbedding);

        // Act
        float[] result = strategy.generateQueryVector(queryText, modelName, testLibrary.getNome());

        // Assert
        assertNotNull(result, "Result should not be null");
        assertArrayEquals(mockEmbedding, result, "Should return correct embedding");

        // Verify interactions
        verify(llmServiceManager).getLLMServiceByRegisteredModel(modelName);
        verify(mockLLMService).embeddings(eq(Embeddings_Op.QUERY), eq(queryText), any(MapParam.class));

        // Verify parameters
        ArgumentCaptor<MapParam> paramsCaptor = ArgumentCaptor.forClass(MapParam.class);
        verify(mockLLMService).embeddings(any(), (String)any(), paramsCaptor.capture());
        MapParam capturedParams = paramsCaptor.getValue();
        assertNotNull(capturedParams, "Params should not be null");
    }

    @Test
    void testGenerateQueryVector_WithDefaultModel() throws LLMException {
        // Arrange
        String queryText = "What is semantic search?";

        when(llmServiceManager.getLLMServiceByRegisteredModel("nomic-embed-text"))
                .thenReturn(mockLLMService);
        when(mockLLMService.getServiceProvider()).thenReturn(SERVICE_PROVIDER.OPENAI);
        when(mockLLMService.embeddings(any(), (String)any(), any()))
                .thenReturn(mockEmbedding);

        // Act
        float[] result = strategy.generateQueryVector(queryText);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertArrayEquals(mockEmbedding, result, "Should return correct embedding");

        // Verify default model was used
        verify(llmServiceManager).getLLMServiceByRegisteredModel("nomic-embed-text");
    }

    @Test
    void testGenerateQueryVector_ModelNotFound() {
        // Arrange
        String queryText = "Test query";
        String modelName = "non-existent-model";

        when(llmServiceManager.getLLMServiceByRegisteredModel(modelName))
                .thenReturn(null);

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            strategy.generateQueryVector(queryText, modelName, null);
        });

        assertTrue(exception.getMessage().contains("No LLM service found"),
                "Should indicate model not found");
        assertTrue(exception.getMessage().contains(modelName),
                "Should mention the model name");
    }

    @Test
    void testGenerateQueryVector_LLMException() throws LLMException {
        // Arrange
        String queryText = "Test query";
        String modelName = "nomic-embed-text";

        when(llmServiceManager.getLLMServiceByRegisteredModel(modelName))
                .thenReturn(mockLLMService);
        when(mockLLMService.getServiceProvider()).thenReturn(SERVICE_PROVIDER.OPENAI);
        when(mockLLMService.embeddings(any(), (String)any(), any()))
                .thenThrow(new LLMException("LLM service error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            strategy.generateQueryVector(queryText, modelName, null);
        });

        assertTrue(exception.getMessage().contains("Query vector generation failed"),
                "Should indicate query generation failure");
    }

    @Test
    void testGenerate_WithRequest() throws LLMException {
        // Arrange
        EmbeddingRequest request = EmbeddingRequest.builder()
                .text("What is embeddings?")
                .context(testContext)
                .operation(Embeddings_Op.QUERY)
                .build();

        when(llmServiceManager.getLLMServiceByRegisteredModel(anyString()))
                .thenReturn(mockLLMService);
        when(mockLLMService.getServiceProvider()).thenReturn(SERVICE_PROVIDER.OPENAI);
        when(mockLLMService.embeddings(any(), (String)any(), any()))
                .thenReturn(mockEmbedding);

        // Act
        List<DocumentEmbeddingDTO> result = strategy.generate(request);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isEmpty(), "Query strategy returns empty list (embeddings used directly)");

        // Verify LLM was called
        verify(mockLLMService).embeddings(eq(Embeddings_Op.QUERY), anyString(), any(MapParam.class));
    }

    @Test
    void testSupports_QueryRequest() {
        // Arrange
        EmbeddingRequest queryRequest = EmbeddingRequest.builder()
                .text("Test query")
                .operation(Embeddings_Op.QUERY)
                .build();

        // Act & Assert
        assertTrue(strategy.supports(queryRequest), "Should support query requests");
    }

    @Test
    void testSupports_DocumentRequest() {
        // Arrange
        EmbeddingRequest docRequest = EmbeddingRequest.builder()
                .text("Test document")
                .operation(Embeddings_Op.DOCUMENT)
                .build();

        // Act & Assert
        assertFalse(strategy.supports(docRequest), "Should not support document requests");
    }

    @Test
    void testSupports_RequestWithChapter() {
        // Arrange
        EmbeddingRequest requestWithChapter = EmbeddingRequest.builder()
                .text("Test query")
                .chapter(new bor.tools.simplerag.dto.ChapterDTO())
                .operation(Embeddings_Op.QUERY)
                .build();

        // Act & Assert
        assertFalse(strategy.supports(requestWithChapter),
                "Should not support requests with chapter");
    }

    @Test
    void testGetStrategyName() {
        // Act
        String name = strategy.getStrategyName();

        // Assert
        assertEquals("QueryEmbeddingStrategy", name, "Should return correct strategy name");
    }

    @Test
    void testGenerate_NullText() {
        // Arrange
        EmbeddingRequest request = EmbeddingRequest.builder()
                .text(null)
                .context(testContext)
                .operation(Embeddings_Op.QUERY)
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            strategy.generate(request);
        });

        assertTrue(exception.getMessage().contains("cannot be null or empty"),
                "Should indicate null text");
    }

    @Test
    void testGenerate_EmptyText() {
        // Arrange
        EmbeddingRequest request = EmbeddingRequest.builder()
                .text("   ")
                .context(testContext)
                .operation(Embeddings_Op.QUERY)
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            strategy.generate(request);
        });

        assertTrue(exception.getMessage().contains("cannot be null or empty"),
                "Should indicate empty text");
    }

    @Test
    void testGenerate_NullContext() {
        // Arrange
        EmbeddingRequest request = EmbeddingRequest.builder()
                .text("Test query")
                .context(null)
                .operation(Embeddings_Op.QUERY)
                .build();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            strategy.generate(request);
        });

        assertTrue(exception.getMessage().contains("EmbeddingContext is required"),
                "Should indicate context required");
    }

    @Test
    void testGenerate_WithLibraryContext() throws LLMException {
        // Arrange
        EmbeddingRequest request = EmbeddingRequest.builder()
                .text("Test query")
                .context(testContext)
                .operation(Embeddings_Op.QUERY)
                .build();

        when(llmServiceManager.getLLMServiceByRegisteredModel(anyString()))
                .thenReturn(mockLLMService);
        when(mockLLMService.getServiceProvider()).thenReturn(SERVICE_PROVIDER.OPENAI);
        when(mockLLMService.embeddings(any(), (String)any(), any()))
                .thenReturn(mockEmbedding);

        // Act
        strategy.generate(request);

        // Assert - Verify library context was passed
        ArgumentCaptor<MapParam> paramsCaptor = ArgumentCaptor.forClass(MapParam.class);
        verify(mockLLMService).embeddings(any(), (String)any(), paramsCaptor.capture());

        MapParam capturedParams = paramsCaptor.getValue();
        assertNotNull(capturedParams, "Params should not be null");
        // Note: We can't easily verify the library_context was set in MapParam
        // without accessing its internals, but we verified it's being created
    }
}
