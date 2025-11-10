package bor.tools.simplerag.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import bor.tools.simplerag.dto.DocumentoDTO;
import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.entity.Documento;
import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.entity.MetaDoc;
import bor.tools.simplerag.repository.ChapterRepository;
import bor.tools.simplerag.repository.DocChunkJdbcRepository;
import bor.tools.simplerag.repository.DocumentoRepository;
import bor.tools.simplerag.service.embedding.EmbeddingOrchestrator;
import bor.tools.simplerag.service.llm.LLMServiceManager;
import bor.tools.simplerag.service.processing.DocumentProcessingService;
import bor.tools.simplerag.service.processing.EnrichmentOptions;
import bor.tools.simplerag.service.processing.EnrichmentResult;
import bor.tools.simplerag.service.processing.GenerationFlag;
import bor.tools.splitter.DocumentRouter;
import bor.tools.utils.DocumentConverter;

/**
 * Comprehensive unit tests for DocumentoService.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Document upload operations (text, URL, file)</li>
 *   <li>New processing flow v2 (processDocumentAsyncV2)</li>
 *   <li>Document enrichment (Q&A and summary)</li>
 *   <li>Document retrieval operations</li>
 *   <li>Status management (activate/deactivate)</li>
 * </ul>
 *
 * <p>Based on the workflow defined in:</p>
 * <ul>
 *   <li>NEW_PROCESSING_FLOW_PROPOSAL.md (v1.1)</li>
 *   <li>Fluxo_carga_documents.md</li>
 * </ul>
 *
 * @since 0.0.3
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentoService Tests")
class DocumentoServiceTest {

    @Mock
    private DocumentoRepository documentoRepository;

    @Mock
    private ChapterRepository chapterRepository;

    @Mock
    private DocChunkJdbcRepository embeddingRepository;

    @Mock
    private LibraryService libraryService;

    @Mock
    private DocumentConverter documentConverter;

    @Mock
    private DocumentRouter documentRouter;

    @Mock
    private EmbeddingOrchestrator embeddingOrchestrator;

    @Mock
    private LLMServiceManager llmServiceManager;

    @Mock
    private DocumentProcessingService documentProcessingService;

    @InjectMocks
    private DocumentoService documentoService;

    // Test data
    private Library testLibrary;
    private LibraryDTO testLibraryDTO;
    private Documento testDocumento;
    private Integer testLibraryId = 1;
    private Integer testDocumentId = 100;
    private String testTitle = "Test Document";
    private String testContent = "This is a test document with sufficient content for processing. "
            + "It contains multiple sentences and paragraphs to ensure proper tokenization and processing.";

    @BeforeEach
    void setUp() {
        // Setup test library
        testLibrary = new Library();
        testLibrary.setId(testLibraryId);
        testLibrary.setNome("Test Library");

        testLibraryDTO = LibraryDTO.builder()
                .id(testLibraryId)
                .nome("Test Library")
                .build();

        // Setup test documento
        testDocumento = Documento.builder()
                .id(testDocumentId)
                .bibliotecaId(testLibraryId)
                .titulo(testTitle)
                .conteudoMarkdown(testContent)
                .flagVigente(true)
                .dataPublicacao(LocalDate.now())
                .tokensTotal(100)
                .metadados(new MetaDoc())
                .build();
    }

    // ========== Upload Operations Tests ==========

    @Nested
    @DisplayName("Upload Operations")
    class UploadOperationsTests {

        @Test
        @DisplayName("uploadFromText - Should successfully upload document with valid data")
        void uploadFromText_ValidData_Success() {
            // Arrange
            when(libraryService.findById(testLibraryId)).thenReturn(Optional.of(testLibrary));
            when(documentoRepository.findByBibliotecaIdAndFlagVigenteTrue(testLibraryId))
                    .thenReturn(Collections.emptyList());
            when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

            // Act
            DocumentoDTO result = documentoService.uploadFromText(
                    testTitle,
                    testContent,
                    testLibraryId,
                    null);

            // Assert
            assertNotNull(result);
            assertEquals(testDocumentId, result.getId());
            assertEquals(testTitle, result.getTitulo());
            assertEquals(testLibraryId, result.getBibliotecaId());
            assertTrue(result.getFlagVigente());

            verify(libraryService, times(1)).findById(testLibraryId);
            verify(documentoRepository, times(1)).save(any(Documento.class));
        }

        @Test
        @DisplayName("uploadFromText - Should fail when library not found")
        void uploadFromText_LibraryNotFound_ThrowsException() {
            // Arrange
            when(libraryService.findById(testLibraryId)).thenReturn(Optional.empty());

            // Act & Assert
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> documentoService.uploadFromText(testTitle, testContent, testLibraryId, null)
            );

            assertTrue(exception.getMessage().contains("Library not found"));
            verify(documentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("uploadFromText - Should store checksum in metadata")
        void uploadFromText_ShouldStoreChecksum() {
            // Arrange
            when(libraryService.findById(testLibraryId)).thenReturn(Optional.of(testLibrary));
            when(documentoRepository.findByBibliotecaIdAndFlagVigenteTrue(testLibraryId))
                    .thenReturn(Collections.emptyList());

            when(documentoRepository.save(any(Documento.class))).thenAnswer(invocation -> {
                Documento doc = invocation.getArgument(0);
                doc.setId(testDocumentId);
                return doc;
            });

            // Act
            DocumentoDTO result = documentoService.uploadFromText(
                    testTitle,
                    testContent,
                    testLibraryId,
                    null);

            // Assert
            verify(documentoRepository, times(1)).save(argThat(doc -> {
                MetaDoc meta = new MetaDoc(doc.getMetadados());
                String checksum = meta.getChecksum();
                return checksum != null && !checksum.isEmpty();
            }));
        }

        @Test
        @DisplayName("uploadFromUrl - Should successfully download and upload from URL")
        void uploadFromUrl_ValidUrl_Success() throws Exception {
            // Arrange
            String testUrl = "https://example.com/document.html";
            String convertedMarkdown = "# Example Document\n\nContent from URL";

            when(libraryService.findById(testLibraryId)).thenReturn(Optional.of(testLibrary));
            when(documentConverter.detectFormat(any(java.net.URI.class))).thenReturn("html");
            when(documentConverter.convertToMarkdown(any(java.net.URI.class), eq("html")))
                    .thenReturn(convertedMarkdown);
            when(documentoRepository.findByBibliotecaIdAndFlagVigenteTrue(testLibraryId))
                    .thenReturn(Collections.emptyList());
            when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

            // Act
            DocumentoDTO result = documentoService.uploadFromUrl(testUrl, testLibraryId, null, null);

            // Assert
            assertNotNull(result);
            verify(documentConverter, times(1)).detectFormat(any(java.net.URI.class));
            verify(documentConverter, times(1)).convertToMarkdown(any(java.net.URI.class), eq("html"));
            verify(documentoRepository, times(1)).save(argThat(doc ->
                    doc.getMetadados().get("url").equals(testUrl)
            ));
        }

        @Test
        @DisplayName("uploadFromFile - Should successfully upload from file bytes")
        void uploadFromFile_ValidFile_Success() throws Exception {
            // Arrange
            String fileName = "test.pdf";
            byte[] fileContent = "Test PDF content".getBytes();
            String convertedMarkdown = "# Test PDF\n\nConverted content";

            when(libraryService.findById(testLibraryId)).thenReturn(Optional.of(testLibrary));
            when(documentConverter.convertToMarkdown(eq(fileContent), anyString()))
                    .thenReturn(convertedMarkdown);
            when(documentoRepository.findByBibliotecaIdAndFlagVigenteTrue(testLibraryId))
                    .thenReturn(Collections.emptyList());
            when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

            // Act
            Map<String, Object> metadata = new HashMap<>();
            DocumentoDTO result = documentoService.uploadFromFile(fileName, fileContent, testLibraryId, metadata);

            // Assert
            assertNotNull(result);
            verify(documentConverter, times(1)).convertToMarkdown(eq(fileContent), anyString());
            verify(documentoRepository, times(1)).save(argThat(doc -> {
                Map<String, Object> meta = doc.getMetadados();
                return meta.get("file_name").equals(fileName) &&
                       meta.get("file_size_bytes").equals(fileContent.length);
            }));
        }
    }

    // ========== New Processing Flow v2 Tests ==========

    @Nested
    @DisplayName("Processing Flow v2 (Sequential)")
    class ProcessingFlowV2Tests {

        @Test
        @DisplayName("processDocumentAsyncV2 - Should successfully process document using new flow")
        void processDocumentAsyncV2_Success() throws Exception {
            // Arrange
            when(documentoRepository.findById(testDocumentId)).thenReturn(Optional.of(testDocumento));
            when(libraryService.findById(testLibraryId)).thenReturn(Optional.of(testLibrary));

            DocumentProcessingService.ProcessingResult processingResult =
                    DocumentProcessingService.ProcessingResult.builder()
                            .documentId(testDocumentId)
                            .chaptersCount(4)
                            .embeddingsCount(11)
                            .embeddingsProcessed(11)
                            .embeddingsFailed(0)
                            .duration("12.5s")
                            .success(true)
                            .build();

            when(documentProcessingService.processDocument(any(Documento.class), any(LibraryDTO.class),any(GenerationFlag.class) ) )
                    .thenReturn(CompletableFuture.completedFuture(processingResult));

            // Act
            CompletableFuture<DocumentoService.ProcessingStatus> future =
                    documentoService.processDocumentAsyncV2(testDocumentId,GenerationFlag.FULL_TEXT_METADATA);
            DocumentoService.ProcessingStatus result = future.get();

            // Assert
            assertNotNull(result);
            assertEquals(testDocumentId, result.getDocumentId());
            assertEquals("COMPLETED", result.getStatus());
            assertEquals(4, result.getChaptersCount());
            assertEquals(11, result.getEmbeddingsCount());

            verify(documentoRepository, times(1)).findById(testDocumentId);
            verify(libraryService, times(1)).findById(testLibraryId);
            verify(documentProcessingService, times(1))
                    .processDocument(any(Documento.class), any(LibraryDTO.class), any(GenerationFlag.class));
        }

        @Test
        @DisplayName("processDocumentAsyncV2 - Should fail when document not found")
        void processDocumentAsyncV2_DocumentNotFound_ThrowsException() throws Exception {
            // Arrange
            when(documentoRepository.findById(testDocumentId)).thenReturn(Optional.empty());

            // Act
            CompletableFuture<DocumentoService.ProcessingStatus> future =
                    documentoService.processDocumentAsyncV2(testDocumentId,GenerationFlag.FULL_TEXT_METADATA);
            DocumentoService.ProcessingStatus result = future.get();

            // Assert
            assertNotNull(result);
            assertEquals("FAILED", result.getStatus());
            assertNotNull(result.getErrorMessage());
            assertTrue(result.getErrorMessage().contains("Document not found"));

            verify(documentProcessingService, never())
                    .processDocument(any(Documento.class), any(LibraryDTO.class), any(GenerationFlag.class));
        }

        @Test
        @DisplayName("processDocumentAsyncV2 - Should fail when library not found")
        void processDocumentAsyncV2_LibraryNotFound_ThrowsException() throws Exception {
            // Arrange
            when(documentoRepository.findById(testDocumentId)).thenReturn(Optional.of(testDocumento));
            when(libraryService.findById(testLibraryId)).thenReturn(Optional.empty());

            // Act
            CompletableFuture<DocumentoService.ProcessingStatus> future =
                    documentoService.processDocumentAsyncV2(testDocumentId,GenerationFlag.FULL_TEXT_METADATA);
            DocumentoService.ProcessingStatus result = future.get();

            // Assert
            assertNotNull(result);
            assertEquals("FAILED", result.getStatus());
            assertNotNull(result.getErrorMessage());
            assertTrue(result.getErrorMessage().contains("Library not found"));

            verify(documentProcessingService, never())
                    .processDocument(any(Documento.class), any(LibraryDTO.class),any(GenerationFlag.class));
        }

        @Test
        @DisplayName("processDocumentAsyncV2 - Should handle processing errors gracefully")
        void processDocumentAsyncV2_ProcessingError_ReturnsFailedStatus() throws Exception {
            // Arrange
            when(documentoRepository.findById(testDocumentId)).thenReturn(Optional.of(testDocumento));
            when(libraryService.findById(testLibraryId)).thenReturn(Optional.of(testLibrary));

            DocumentProcessingService.ProcessingResult processingResult =
                    DocumentProcessingService.ProcessingResult.builder()
                            .documentId(testDocumentId)
                            .success(false)
                            .errorMessage("LLM service unavailable")
                            .duration("2.1s")
                            .build();

            when(documentProcessingService.processDocument(any(Documento.class),
        	    							any(LibraryDTO.class),
        	    							any(GenerationFlag.class)))	
                    .thenReturn(CompletableFuture.completedFuture(processingResult));

            // Act
            CompletableFuture<DocumentoService.ProcessingStatus> future =
                    documentoService.processDocumentAsyncV2(testDocumentId,GenerationFlag.FULL_TEXT_METADATA);
            DocumentoService.ProcessingStatus result = future.get();

            // Assert
            assertNotNull(result);
            assertEquals("FAILED", result.getStatus());
            assertEquals("LLM service unavailable", result.getErrorMessage());

            verify(documentProcessingService, times(1))
                    .processDocument(any(Documento.class), any(LibraryDTO.class), any(GenerationFlag.class));
        }
    }

    // ========== Document Enrichment Tests ==========

    @Nested
    @DisplayName("Document Enrichment (Phase 2)")
    class EnrichmentTests {

        @Test
        @DisplayName("enrichDocumentAsync - Should successfully enrich with Q&A and summaries")
        void enrichDocumentAsync_WithQAAndSummary_Success() throws Exception {
            // Arrange
            when(documentoRepository.findById(testDocumentId)).thenReturn(Optional.of(testDocumento));
            when(libraryService.findById(testLibraryId)).thenReturn(Optional.of(testLibrary));

            EnrichmentOptions options = EnrichmentOptions.builder()
                    .generateQA(true)
                    .numberOfQAPairs(3)
                    .generateSummary(true)
                    .maxSummaryLength(500)
                    .continueOnError(true)
                    .build();

            EnrichmentResult enrichmentResult = EnrichmentResult.builder()
                    .documentId(testDocumentId)
                    .success(true)
                    .chaptersProcessed(4)
                    .qaEmbeddingsGenerated(12)  // 4 chapters × 3 Q&A pairs
                    .summaryEmbeddingsGenerated(4)  // 4 chapters × 1 summary
                    .duration("8.3s")
                    .build();

            when(documentProcessingService.enrichDocument(
                    any(Documento.class),
                    any(LibraryDTO.class),
                    any(EnrichmentOptions.class)))
                    .thenReturn(CompletableFuture.completedFuture(enrichmentResult));

            // Act
            CompletableFuture<EnrichmentResult> future =
                    documentoService.enrichDocumentAsync(testDocumentId, options);
            EnrichmentResult result = future.get();

            // Assert
            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals(testDocumentId, result.getDocumentId());
            assertEquals(4, result.getChaptersProcessed());
            assertEquals(12, result.getQaEmbeddingsGenerated());
            assertEquals(4, result.getSummaryEmbeddingsGenerated());

            verify(documentProcessingService, times(1))
                    .enrichDocument(any(Documento.class), any(LibraryDTO.class), any(EnrichmentOptions.class));
        }

        @Test
        @DisplayName("enrichDocumentAsync - Should enrich with Q&A only")
        void enrichDocumentAsync_WithQAOnly_Success() throws Exception {
            // Arrange
            when(documentoRepository.findById(testDocumentId)).thenReturn(Optional.of(testDocumento));
            when(libraryService.findById(testLibraryId)).thenReturn(Optional.of(testLibrary));

            EnrichmentOptions options = EnrichmentOptions.builder()
                    .generateQA(true)
                    .numberOfQAPairs(5)
                    .generateSummary(false)
                    .continueOnError(true)
                    .build();

            EnrichmentResult enrichmentResult = EnrichmentResult.builder()
                    .documentId(testDocumentId)
                    .success(true)
                    .chaptersProcessed(4)
                    .qaEmbeddingsGenerated(20)  // 4 chapters × 5 Q&A pairs
                    .summaryEmbeddingsGenerated(0)
                    .duration("6.5s")
                    .build();

            when(documentProcessingService.enrichDocument(
                    any(Documento.class),
                    any(LibraryDTO.class),
                    any(EnrichmentOptions.class)))
                    .thenReturn(CompletableFuture.completedFuture(enrichmentResult));

            // Act
            CompletableFuture<EnrichmentResult> future =
                    documentoService.enrichDocumentAsync(testDocumentId, options);
            EnrichmentResult result = future.get();

            // Assert
            assertNotNull(result);
            assertTrue(result.isSuccess());
            assertEquals(20, result.getQaEmbeddingsGenerated());
            assertEquals(0, result.getSummaryEmbeddingsGenerated());
        }

        @Test
        @DisplayName("enrichDocumentAsync - Should fail when document not found")
        void enrichDocumentAsync_DocumentNotFound_ReturnsFailure() throws Exception {
            // Arrange
            when(documentoRepository.findById(testDocumentId)).thenReturn(Optional.empty());

            EnrichmentOptions options = EnrichmentOptions.builder()
                    .generateQA(true)
                    .build();

            // Act
            CompletableFuture<EnrichmentResult> future =
                    documentoService.enrichDocumentAsync(testDocumentId, options);
            EnrichmentResult result = future.get();

            // Assert
            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertNotNull(result.getErrorMessage());
            assertTrue(result.getErrorMessage().contains("Document not found"));

            verify(documentProcessingService, never())
                    .enrichDocument(any(), any(), any());
        }

        @Test
        @DisplayName("enrichDocumentAsync - Should handle enrichment errors gracefully")
        void enrichDocumentAsync_EnrichmentError_ReturnsFailure() throws Exception {
            // Arrange
            when(documentoRepository.findById(testDocumentId)).thenReturn(Optional.of(testDocumento));
            when(libraryService.findById(testLibraryId)).thenReturn(Optional.of(testLibrary));

            EnrichmentOptions options = EnrichmentOptions.builder()
                    .generateSummary(true)
                    .build();

            EnrichmentResult enrichmentResult = EnrichmentResult.builder()
                    .documentId(testDocumentId)
                    .success(false)
                    .errorMessage("Completion model not available")
                    .duration("1.2s")
                    .build();

            when(documentProcessingService.enrichDocument(
                    any(Documento.class),
                    any(LibraryDTO.class),
                    any(EnrichmentOptions.class)))
                    .thenReturn(CompletableFuture.completedFuture(enrichmentResult));

            // Act
            CompletableFuture<EnrichmentResult> future =
                    documentoService.enrichDocumentAsync(testDocumentId, options);
            EnrichmentResult result = future.get();

            // Assert
            assertNotNull(result);
            assertFalse(result.isSuccess());
            assertEquals("Completion model not available", result.getErrorMessage());
        }
    }

    // ========== Retrieval Operations Tests ==========

    @Nested
    @DisplayName("Retrieval Operations")
    class RetrievalOperationsTests {

        @Test
        @DisplayName("findAll - Should return all documents")
        void findAll_ShouldReturnAllDocuments() {
            // Arrange
            Documento doc1 = Documento.builder()
                    .id(1)
                    .titulo("Document 1")
                    .bibliotecaId(testLibraryId)
                    .build();

            Documento doc2 = Documento.builder()
                    .id(2)
                    .titulo("Document 2")
                    .bibliotecaId(testLibraryId)
                    .build();

            when(documentoRepository.findAll()).thenReturn(Arrays.asList(doc1, doc2));

            // Act
            List<DocumentoDTO> results = documentoService.findAll();

            // Assert
            assertNotNull(results);
            assertEquals(2, results.size());
            verify(documentoRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("findById - Should return document when exists")
        void findById_DocumentExists_ReturnsDocument() {
            // Arrange
            when(documentoRepository.findById(testDocumentId)).thenReturn(Optional.of(testDocumento));

            // Act
            Optional<DocumentoDTO> result = documentoService.findById(testDocumentId);

            // Assert
            assertTrue(result.isPresent());
            assertEquals(testDocumentId, result.get().getId());
            assertEquals(testTitle, result.get().getTitulo());
        }

        @Test
        @DisplayName("findById - Should return empty when document not found")
        void findById_DocumentNotFound_ReturnsEmpty() {
            // Arrange
            when(documentoRepository.findById(testDocumentId)).thenReturn(Optional.empty());

            // Act
            Optional<DocumentoDTO> result = documentoService.findById(testDocumentId);

            // Assert
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("findByLibraryId - Should return documents for library")
        void findByLibraryId_ShouldReturnDocumentsForLibrary() {
            // Arrange
            when(documentoRepository.findByBibliotecaId(testLibraryId))
                    .thenReturn(Arrays.asList(testDocumento));

            // Act
            List<DocumentoDTO> results = documentoService.findByLibraryId(testLibraryId);

            // Assert
            assertNotNull(results);
            assertEquals(1, results.size());
            assertEquals(testLibraryId, results.get(0).getBibliotecaId());
        }

        @Test
        @DisplayName("findActiveByLibraryId - Should return only active documents")
        void findActiveByLibraryId_ShouldReturnOnlyActiveDocuments() {
            // Arrange
            when(documentoRepository.findByBibliotecaIdAndFlagVigenteTrue(testLibraryId))
                    .thenReturn(Arrays.asList(testDocumento));

            // Act
            List<DocumentoDTO> results = documentoService.findActiveByLibraryId(testLibraryId);

            // Assert
            assertNotNull(results);
            assertEquals(1, results.size());
            assertTrue(results.get(0).getFlagVigente());
        }
    }

    // ========== Status Management Tests ==========

    @Nested
    @DisplayName("Status Management")
    class StatusManagementTests {

        @Test
        @DisplayName("updateStatus - Should successfully update document status")
        void updateStatus_ValidDocument_Success() {
            // Arrange
            when(documentoRepository.findById(testDocumentId)).thenReturn(Optional.of(testDocumento));
            when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

            // Act
            documentoService.updateStatus(testDocumentId, false);

            // Assert
            verify(documentoRepository, times(1)).findById(testDocumentId);
            verify(documentoRepository, times(1)).save(argThat(doc ->
                    !doc.getFlagVigente()
            ));
        }

        @Test
        @DisplayName("updateStatus - Should throw exception when document not found")
        void updateStatus_DocumentNotFound_ThrowsException() {
            // Arrange
            when(documentoRepository.findById(testDocumentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                    () -> documentoService.updateStatus(testDocumentId, false)
            );

            verify(documentoRepository, never()).save(any());
        }

        @Test
        @DisplayName("delete - Should soft delete document by setting flagVigente to false")
        void delete_ValidDocument_SoftDelete() {
            // Arrange
            when(documentoRepository.findById(testDocumentId)).thenReturn(Optional.of(testDocumento));
            when(documentoRepository.save(any(Documento.class))).thenReturn(testDocumento);

            // Act
            documentoService.delete(testDocumentId);

            // Assert
            verify(documentoRepository, times(1)).save(argThat(doc ->
                    !doc.getFlagVigente()
            ));
        }
    }

    // ========== OVERWRITE FEATURE TESTS (v1.0) ==========

    @Nested
    @DisplayName("Overwrite Feature Tests")
    class OverwriteFeatureTests {

        @Test
        @DisplayName("checkExistingProcessing - Should return correct counts for document with chapters and embeddings")
        void checkExistingProcessing_WithChaptersAndEmbeddings_ReturnsCorrectCounts() throws Exception {
            // Arrange: Document with 4 chapters and 30 embeddings
            when(chapterRepository.countByDocumentoId(testDocumentId)).thenReturn(4);
            when(embeddingRepository.countByDocumentoId(testDocumentId)).thenReturn(30);

            // Act
            DocumentoService.ProcessingCheckResult result =
                    documentoService.checkExistingProcessing(testDocumentId);

            // Assert
            assertNotNull(result, "Result should not be null");
            assertEquals(testDocumentId, result.getDocumentId(), "Document ID should match");
            assertEquals(4, result.getChaptersCount(), "Chapters count should be 4");
            assertEquals(30, result.getEmbeddingsCount(), "Embeddings count should be 30");
            assertTrue(result.isHasChapters(), "Should indicate chapters exist");
            assertTrue(result.isHasEmbeddings(), "Should indicate embeddings exist");

            verify(chapterRepository, times(1)).countByDocumentoId(testDocumentId);
            verify(embeddingRepository, times(1)).countByDocumentoId(testDocumentId);
        }

        @Test
        @DisplayName("checkExistingProcessing - Should return zero counts for unprocessed document")
        void checkExistingProcessing_UnprocessedDocument_ReturnsZeroCounts() throws Exception {
            // Arrange: Document with no chapters or embeddings
            when(chapterRepository.countByDocumentoId(testDocumentId)).thenReturn(0);
            when(embeddingRepository.countByDocumentoId(testDocumentId)).thenReturn(0);

            // Act
            DocumentoService.ProcessingCheckResult result =
                    documentoService.checkExistingProcessing(testDocumentId);

            // Assert
            assertNotNull(result, "Result should not be null");
            assertEquals(0, result.getChaptersCount(), "Chapters count should be 0");
            assertEquals(0, result.getEmbeddingsCount(), "Embeddings count should be 0");
            assertFalse(result.isHasChapters(), "Should indicate no chapters exist");
            assertFalse(result.isHasEmbeddings(), "Should indicate no embeddings exist");
        }

        @Test
        @DisplayName("checkExistingProcessing - Should handle embeddings count error gracefully")
        void checkExistingProcessing_EmbeddingsCountError_HandlesGracefully() throws Exception {
            // Arrange
            when(chapterRepository.countByDocumentoId(testDocumentId)).thenReturn(4);
            when(embeddingRepository.countByDocumentoId(testDocumentId))
                    .thenThrow(new RuntimeException("Database error"));

            // Act
            DocumentoService.ProcessingCheckResult result =
                    documentoService.checkExistingProcessing(testDocumentId);

            // Assert: Should still return result with chapters count, embeddings = 0
            assertNotNull(result, "Result should not be null");
            assertEquals(4, result.getChaptersCount(), "Chapters count should be 4");
            assertEquals(0, result.getEmbeddingsCount(), "Embeddings count should default to 0 on error");
            assertTrue(result.isHasChapters(), "Should indicate chapters exist");
            assertFalse(result.isHasEmbeddings(), "Should indicate no embeddings due to error");
        }

        @Test
        @DisplayName("deleteExistingProcessing - Should delete chapters successfully")
        void deleteExistingProcessing_Success() throws Exception {
            // Arrange
            when(chapterRepository.countByDocumentoId(testDocumentId)).thenReturn(4);
            when(embeddingRepository.countByDocumentoId(testDocumentId)).thenReturn(30);
            when(chapterRepository.deleteByDocumentoId(testDocumentId)).thenReturn(4);

            // Act
            documentoService.deleteExistingProcessing(testDocumentId);

            // Assert
            verify(chapterRepository, times(1)).countByDocumentoId(testDocumentId);
            verify(embeddingRepository, times(1)).countByDocumentoId(testDocumentId);
            verify(chapterRepository, times(1)).deleteByDocumentoId(testDocumentId);
            // CASCADE will handle embeddings automatically - no direct call needed
        }

        @Test
        @DisplayName("deleteExistingProcessing - Should throw RuntimeException on deletion failure")
        void deleteExistingProcessing_DeletionFailure_ThrowsException() {
            // Arrange
            when(chapterRepository.countByDocumentoId(testDocumentId)).thenReturn(4);
            when(chapterRepository.deleteByDocumentoId(testDocumentId))
                    .thenThrow(new RuntimeException("Database constraint violation"));

            // Act & Assert
            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> documentoService.deleteExistingProcessing(testDocumentId),
                    "Should throw RuntimeException on deletion failure"
            );

            assertTrue(
                    exception.getMessage().contains("Failed to delete existing processing data"),
                    "Exception message should indicate deletion failure"
            );

            verify(chapterRepository, times(1)).deleteByDocumentoId(testDocumentId);
        }

        @Test
        @DisplayName("deleteExistingProcessing - Should handle embeddings count error during deletion")
        void deleteExistingProcessing_EmbeddingsCountErrorDuringDeletion_HandlesGracefully() throws Exception {
            // Arrange
            when(chapterRepository.countByDocumentoId(testDocumentId)).thenReturn(4);
            when(embeddingRepository.countByDocumentoId(testDocumentId))
                    .thenThrow(new RuntimeException("Count error"));
            when(chapterRepository.deleteByDocumentoId(testDocumentId)).thenReturn(4);

            // Act - should not throw exception, just log warning
            assertDoesNotThrow(() -> documentoService.deleteExistingProcessing(testDocumentId),
                    "Should handle embeddings count error gracefully");

            // Assert
            verify(chapterRepository, times(1)).deleteByDocumentoId(testDocumentId);
        }
    }
}
