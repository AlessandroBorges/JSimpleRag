package bor.tools.simplerag.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import bor.tools.simplerag.dto.DocumentoWithAssociationDTO;
import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.entity.MetaBiblioteca;
import bor.tools.simplerag.entity.MetaDoc;
import bor.tools.simplerag.entity.enums.TipoBiblioteca;
import bor.tools.simplerag.service.embedding.EmbeddingOrchestrator;
import bor.tools.simplerag.service.embedding.model.EmbeddingContext;
import bor.tools.simplerag.service.embedding.model.ProcessingOptions;

/**
 * Integration tests for EmbeddingOrchestrator.
 *
 * Tests the complete document processing pipeline including:
 * - Content type detection
 * - Document splitting
 * - Embedding generation (basic, Q&A, summaries)
 * - Retry logic
 * - Async processing
 *
 * NOTE: These tests require:
 * - PostgreSQL database with PGVector
 * - LLM service configured (LMStudio, Ollama, or OpenAI)
 * - Sufficient test timeout for LLM operations
 *
 * Configure test environment in application-test.properties
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmbeddingOrchestratorIntegrationTest {

    @Autowired
    private EmbeddingOrchestrator embeddingOrchestrator;

    private LibraryDTO testLibrary;
    private DocumentoWithAssociationDTO testDocument;

    @BeforeEach
    void setUp() {
        // Create test library
        testLibrary = new LibraryDTO();
        testLibrary.setId(1);
        testLibrary.setNome("Test Library - Orchestrator");
        testLibrary.setDescription("Library for orchestrator testing");
        testLibrary.setTipo(TipoBiblioteca.PUBLICO);
        testLibrary.setPesoSemantico(0.7f);
        testLibrary.setPesoTextual(0.3f);

        MetaBiblioteca meta = new MetaBiblioteca();
        meta.setEmbeddingModel("snowflake");
        // Note: Completion model comes from LLMServiceManager, not Library
        testLibrary.setMetadados(meta);

        // Create test document
        testDocument = new DocumentoWithAssociationDTO();
        testDocument.setId(1);
        testDocument.setTitulo("Test Document - Integration");
        testDocument.setBibliotecaId(testLibrary.getId());

        // Create realistic test content
        testDocument.setConteudoMarkdown(createTestMarkdownContent());

        MetaDoc docMeta = new MetaDoc();
        docMeta.setAutor("Test Author");
        docMeta.setDataPublicacao("2025-01-15");
        testDocument.setMetadados(docMeta);
    }

    /**
     * Test basic orchestrator processing without Q&A or summaries.
     */
    @Test
    void testBasicProcessing() {
        // Arrange
        EmbeddingContext context = EmbeddingContext.builder()
                .library(testLibrary)
                .build();

        ProcessingOptions options = ProcessingOptions.basicOnly();

        // Act
        EmbeddingOrchestrator.ProcessingResult result =
            embeddingOrchestrator.processDocumentWithoutRetry(testDocument, context, options);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isSuccessful(), "Processing should be successful");
        assertFalse(result.getCapitulos().isEmpty(), "Should generate chapters");
        assertFalse(result.getAllEmbeddings().isEmpty(), "Should generate embeddings");

        // Verify statistics
        EmbeddingOrchestrator.ProcessingStats stats = result.getStats();
        assertTrue(stats.getChaptersCount() > 0, "Should have chapters");
        assertTrue(stats.getEmbeddingsCount() > 0, "Should have embeddings");
        assertTrue(stats.getTotalCharacters() > 0, "Should have content");

        System.out.println("Basic processing stats: " + stats);
    }

    /**
     * Test processing with Q&A embeddings enabled.
     */
    @Test
    void testProcessingWithQA() {
        // Arrange
        EmbeddingContext context = EmbeddingContext.builder()
                .library(testLibrary)
                .build();

        ProcessingOptions options = ProcessingOptions.withQA(2);

        // Act
        EmbeddingOrchestrator.ProcessingResult result =
            embeddingOrchestrator.processDocumentWithoutRetry(testDocument, context, options);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isSuccessful(), "Processing should be successful");

        // Should have more embeddings than basic (chapters + Q&A pairs)
        int expectedMinimum = result.getCapitulos().size() + (result.getCapitulos().size() * 2);
        assertTrue(result.getAllEmbeddings().size() >= expectedMinimum,
                "Should have chapter embeddings + Q&A embeddings");

        System.out.println("Q&A processing stats: " + result.getStats());
    }

    /**
     * Test processing with summary embeddings enabled.
     */
    @Test
    void testProcessingWithSummaries() {
        // Arrange
        EmbeddingContext context = EmbeddingContext.builder()
                .library(testLibrary)
                .build();

        ProcessingOptions options = ProcessingOptions.withSummary(300);

        // Act
        EmbeddingOrchestrator.ProcessingResult result =
            embeddingOrchestrator.processDocumentWithoutRetry(testDocument, context, options);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isSuccessful(), "Processing should be successful");

        // Should have chapter embeddings + summary embeddings (for large chapters)
        assertTrue(result.getAllEmbeddings().size() >= result.getCapitulos().size(),
                "Should have at least chapter embeddings");

        System.out.println("Summary processing stats: " + result.getStats());
    }

    /**
     * Test full processing with all features enabled.
     */
    @Test
    void testFullProcessing() {
        // Arrange
        EmbeddingContext context = EmbeddingContext.builder()
                .library(testLibrary)
                .build();

        ProcessingOptions options = ProcessingOptions.fullProcessing();

        // Act
        EmbeddingOrchestrator.ProcessingResult result =
            embeddingOrchestrator.processDocumentWithoutRetry(testDocument, context, options);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertTrue(result.isSuccessful(), "Processing should be successful");

        // Should have maximum embeddings (chapters + Q&A + summaries)
        assertTrue(result.getAllEmbeddings().size() > result.getCapitulos().size(),
                "Should have multiple embedding types");

        System.out.println("Full processing stats: " + result.getStats());
    }

    /**
     * Test asynchronous processing.
     */
    @Test
    void testAsyncProcessing() throws Exception {
        // Arrange
        EmbeddingContext context = EmbeddingContext.builder()
                .library(testLibrary)
                .build();

        ProcessingOptions options = ProcessingOptions.basicOnly();

        // Act
        CompletableFuture<EmbeddingOrchestrator.ProcessingResult> future =
            embeddingOrchestrator.processDocumentFull(testDocument, context, options);

        // Wait for completion (with timeout)
        EmbeddingOrchestrator.ProcessingResult result = future.get(60, TimeUnit.SECONDS);

        // Assert
        assertNotNull(result, "Async result should not be null");
        assertTrue(result.isSuccessful(), "Async processing should be successful");

        System.out.println("Async processing stats: " + result.getStats());
    }

    /**
     * Test synchronous processing with retry logic.
     */
    @Test
    void testSyncProcessingWithRetry() {
        // Arrange
        EmbeddingContext context = EmbeddingContext.builder()
                .library(testLibrary)
                .build();

        ProcessingOptions options = ProcessingOptions.basicOnly();

        // Act
        EmbeddingOrchestrator.ProcessingResult result =
            embeddingOrchestrator.processDocumentSync(testDocument, context, options);

        // Assert
        assertNotNull(result, "Sync result should not be null");
        assertTrue(result.isSuccessful(), "Sync processing should be successful");

        System.out.println("Sync processing stats: " + result.getStats());
    }

    /**
     * Test processing result statistics.
     */
    @Test
    void testProcessingStatistics() {
        // Arrange
        EmbeddingContext context = EmbeddingContext.builder()
                .library(testLibrary)
                .build();

        ProcessingOptions options = ProcessingOptions.basicOnly();

        // Act
        EmbeddingOrchestrator.ProcessingResult result =
            embeddingOrchestrator.processDocumentWithoutRetry(testDocument, context, options);

        EmbeddingOrchestrator.ProcessingStats stats = result.getStats();

        // Assert
        assertTrue(stats.getChaptersCount() > 0, "Should have chapters");
        assertTrue(stats.getEmbeddingsCount() > 0, "Should have embeddings");
        assertTrue(stats.getTotalCharacters() > 0, "Should have characters");
        assertTrue(stats.getAverageEmbeddingsPerChapter() > 0, "Should have average > 0");

        // Verify toString
        String statsString = stats.toString();
        assertNotNull(statsString, "Stats toString should not be null");
        assertTrue(statsString.contains("chapters="), "Should contain chapters info");
        assertTrue(statsString.contains("embeddings="), "Should contain embeddings info");

        System.out.println("Statistics: " + statsString);
    }

    /**
     * Test error handling with invalid document.
     */
    @Test
    void testErrorHandlingWithInvalidDocument() {
        // Arrange
        DocumentoWithAssociationDTO invalidDoc = new DocumentoWithAssociationDTO();
        invalidDoc.setId(999);
        invalidDoc.setTitulo("Invalid Document");
        invalidDoc.setConteudoMarkdown(null); // Invalid: null content

        EmbeddingContext context = EmbeddingContext.builder()
                .library(testLibrary)
                .build();

        ProcessingOptions options = ProcessingOptions.basicOnly();

        // Act & Assert
        assertThrows(Exception.class, () -> {
            embeddingOrchestrator.processDocumentWithoutRetry(invalidDoc, context, options);
        }, "Should throw exception for invalid document");
    }

    // ========== Helper Methods ==========

    /**
     * Creates realistic markdown content for testing.
     */
    private String createTestMarkdownContent() {
        return """
            # Introduction to RAG Systems

            Retrieval-Augmented Generation (RAG) is a powerful technique that combines
            the benefits of retrieval-based and generation-based AI systems.

            ## Key Concepts

            RAG systems work by first retrieving relevant documents from a knowledge base,
            then using those documents as context for generating responses. This approach
            has several advantages:

            1. **Factual Accuracy**: By grounding responses in retrieved documents
            2. **Up-to-date Information**: Knowledge base can be updated without retraining
            3. **Source Attribution**: Responses can cite specific sources

            ## Architecture Components

            A typical RAG system consists of several key components:

            ### Document Processing

            Documents are split into chunks and converted into vector embeddings.
            These embeddings capture the semantic meaning of the text.

            ### Vector Database

            Embeddings are stored in a specialized vector database that supports
            efficient similarity search operations.

            ### Retrieval System

            When a query comes in, it's converted to an embedding and used to find
            the most similar document chunks from the database.

            ### Generation Model

            Retrieved documents are provided as context to a language model,
            which generates a response based on both the query and the context.

            ## Benefits and Use Cases

            RAG systems are particularly useful for:

            - Question answering over large document collections
            - Building chatbots with domain-specific knowledge
            - Automated customer support systems
            - Research assistance and literature review

            The hybrid approach of retrieval + generation provides better results
            than either technique alone.
            """;
    }
}
