package bor.tools.simplerag.controller;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import bor.tools.simplerag.dto.DocumentoDTO;
import bor.tools.simplerag.dto.UploadTextRequest;
import bor.tools.simplerag.dto.UploadUrlRequest;
import bor.tools.simplerag.entity.MetaDoc;
import bor.tools.simplerag.service.DocumentoService;
import bor.tools.simplerag.service.ProcessingStatusTracker;
import bor.tools.simplerag.service.ProcessingStatusTracker.ProcessingStatus;
import bor.tools.simplerag.service.ProcessingStatusTracker.Status;
import bor.tools.simplerag.service.processing.DocumentProcessingService;
import bor.tools.simplerag.service.processing.EnrichmentOptions;
import bor.tools.simplerag.service.processing.EnrichmentResult;

/**
 * Comprehensive integration tests for DocumentController.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Document upload endpoints (text, URL, file)</li>
 *   <li>Document processing endpoints (Phase 1 and Phase 2)</li>
 *   <li>Status monitoring endpoints</li>
 *   <li>Document retrieval endpoints</li>
 *   <li>Document management endpoints (update, delete)</li>
 * </ul>
 *
 * <p>Based on the API structure defined in DocumentController.java</p>
 *
 * @since 0.0.3
 */
@WebMvcTest(DocumentController.class)
@DisplayName("DocumentController Integration Tests")
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DocumentoService documentoService;

    @MockBean
    private ProcessingStatusTracker statusTracker;

    @MockBean
    private DocumentProcessingService documentProcessingService;

    // Test data
    private DocumentoDTO testDocumentoDTO;
    private Integer testLibraryId = 1;
    private Integer testDocumentId = 100;
    private String testTitle = "Test Document";
    private String testContent = "This is a test document with sufficient content for processing.";

    @BeforeEach
    void setUp() {
        testDocumentoDTO = DocumentoDTO.builder()
                .id(testDocumentId)
                .bibliotecaId(testLibraryId)
                .titulo(testTitle)
                .conteudoMarkdown(testContent)
                .flagVigente(true)
                .dataPublicacao(LocalDate.now())
                .tokensTotal(100)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ========== Upload Endpoints Tests ==========

    @Nested
    @DisplayName("POST /api/v1/documents/upload/text")
    class UploadTextEndpointTests {

        @Test
        @DisplayName("Should successfully upload document from text")
        void uploadFromText_ValidRequest_ReturnsCreated() throws Exception {
            // Arrange
            UploadTextRequest request = new UploadTextRequest();
            request.setTitulo(testTitle);
            request.setConteudo(testContent);
            request.setLibraryId(testLibraryId);

            when(documentoService.uploadFromText(anyString(), anyString(), anyInt(), any()))
                    .thenReturn(testDocumentoDTO);

            // Act & Assert
            mockMvc.perform(post("/api/v1/documents/upload/text")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(testDocumentId))
                    .andExpect(jsonPath("$.titulo").value(testTitle))
                    .andExpect(jsonPath("$.bibliotecaId").value(testLibraryId))
                    .andExpect(jsonPath("$.flagVigente").value(true));

            verify(documentoService, times(1))
                    .uploadFromText(eq(testTitle), eq(testContent), eq(testLibraryId), any());
        }

        @Test
        @DisplayName("Should reject request with empty title")
        void uploadFromText_EmptyTitle_ReturnsBadRequest() throws Exception {
            // Arrange
            UploadTextRequest request = new UploadTextRequest();
            request.setTitulo("");  // Empty title
            request.setConteudo(testContent);
            request.setLibraryId(testLibraryId);

            // Act & Assert
            mockMvc.perform(post("/api/v1/documents/upload/text")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());

            verify(documentoService, never())
                    .uploadFromText(anyString(), anyString(), anyInt(), any());
        }

        @Test
        @DisplayName("Should reject request with missing library ID")
        void uploadFromText_MissingLibraryId_ReturnsBadRequest() throws Exception {
            // Arrange
            UploadTextRequest request = new UploadTextRequest();
            request.setTitulo(testTitle);
            request.setConteudo(testContent);
            // libraryId not set

            // Act & Assert
            mockMvc.perform(post("/api/v1/documents/upload/text")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should handle library not found error")
        void uploadFromText_LibraryNotFound_ThrowsException() throws Exception {
            // Arrange
            UploadTextRequest request = new UploadTextRequest();
            request.setTitulo(testTitle);
            request.setConteudo(testContent);
            request.setLibraryId(999);  // Non-existent library

            when(documentoService.uploadFromText(anyString(), anyString(), anyInt(), any()))
                    .thenThrow(new IllegalArgumentException("Library not found: 999"));

            // Act & Assert
            mockMvc.perform(post("/api/v1/documents/upload/text")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/documents/upload/url")
    class UploadUrlEndpointTests {

        @Test
        @DisplayName("Should successfully upload document from URL")
        void uploadFromUrl_ValidRequest_ReturnsCreated() throws Exception {
            // Arrange
            String testUrl = "https://example.com/document.html";
            UploadUrlRequest request = new UploadUrlRequest();
            request.setUrl(testUrl);
            request.setLibraryId(testLibraryId);

            when(documentoService.uploadFromUrl(anyString(), anyInt(), any(), any()))
                    .thenReturn(testDocumentoDTO);

            // Act & Assert
            mockMvc.perform(post("/api/v1/documents/upload/url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(testDocumentId))
                    .andExpect(jsonPath("$.titulo").value(testTitle));

            verify(documentoService, times(1))
                    .uploadFromUrl(eq(testUrl), eq(testLibraryId), any(), any());
        }

        @Test
        @DisplayName("Should reject invalid URL format")
        void uploadFromUrl_InvalidUrl_ReturnsBadRequest() throws Exception {
            // Arrange
            UploadUrlRequest request = new UploadUrlRequest();
            request.setUrl("not-a-valid-url");  // Invalid URL
            request.setLibraryId(testLibraryId);

            // Act & Assert
            mockMvc.perform(post("/api/v1/documents/upload/url")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/documents/upload/file")
    class UploadFileEndpointTests {

        @Test
        @DisplayName("Should successfully upload document from file")
        void uploadFromFile_ValidFile_ReturnsCreated() throws Exception {
            // Arrange
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test-document.pdf",
                    "application/pdf",
                    "Test PDF content".getBytes()
            );

            when(documentoService.uploadFromFile(anyString(), any(byte[].class), anyInt(), anyMap()))
                    .thenReturn(testDocumentoDTO);

            // Act & Assert
            mockMvc.perform(multipart("/api/v1/documents/upload/file")
                            .file(file)
                            .param("libraryId", testLibraryId.toString()))
                    .andDo(print())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(testDocumentId))
                    .andExpect(jsonPath("$.titulo").value(testTitle));

            verify(documentoService, times(1))
                    .uploadFromFile(eq("test-document.pdf"), any(byte[].class), eq(testLibraryId), anyMap());
        }

        @Test
        @DisplayName("Should use provided title instead of filename")
        void uploadFromFile_WithCustomTitle_UsesProvidedTitle() throws Exception {
            // Arrange
            String customTitle = "Custom Document Title";
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "original-filename.pdf",
                    "application/pdf",
                    "Test content".getBytes()
            );

            when(documentoService.uploadFromFile(anyString(), any(byte[].class), anyInt(), anyMap()))
                    .thenReturn(testDocumentoDTO);

            // Act & Assert
            mockMvc.perform(multipart("/api/v1/documents/upload/file")
                            .file(file)
                            .param("libraryId", testLibraryId.toString())
                            .param("titulo", customTitle))
                    .andDo(print())
                    .andExpect(status().isCreated());

            verify(documentoService, times(1))
                    .uploadFromFile(anyString(), any(byte[].class), eq(testLibraryId), anyMap());
        }

        @Test
        @DisplayName("Should accept metadata as JSON string")
        void uploadFromFile_WithMetadata_ParsesCorrectly() throws Exception {
            // Arrange
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "test.pdf",
                    "application/pdf",
                    "Test content".getBytes()
            );

            String metadataJson = "{\"author\":\"John Doe\",\"year\":2024}";

            when(documentoService.uploadFromFile(anyString(), any(byte[].class), anyInt(), anyMap()))
                    .thenReturn(testDocumentoDTO);

            // Act & Assert
            mockMvc.perform(multipart("/api/v1/documents/upload/file")
                            .file(file)
                            .param("libraryId", testLibraryId.toString())
                            .param("metadata", metadataJson))
                    .andDo(print())
                    .andExpect(status().isCreated());
        }
    }

    // ========== Processing Endpoints Tests ==========

    @Nested
    @DisplayName("POST /api/v1/documents/{documentId}/process")
    class ProcessDocumentEndpointTests {

        @Test
        @DisplayName("Should start document processing and return 202 Accepted")
        void processDocument_ValidDocument_ReturnsAccepted() throws Exception {
            // Arrange
            when(documentoService.findById(testDocumentId)).thenReturn(Optional.of(testDocumentoDTO));

            DocumentoService.ProcessingStatus processingStatus = new DocumentoService.ProcessingStatus();
            processingStatus.setDocumentId(testDocumentId);
            processingStatus.setStatus("COMPLETED");
            processingStatus.setChaptersCount(4);
            processingStatus.setEmbeddingsCount(11);

            when(documentoService.processDocumentAsyncV2(testDocumentId))
                    .thenReturn(CompletableFuture.completedFuture(processingStatus));

            // Act & Assert
            mockMvc.perform(post("/api/v1/documents/{documentId}/process", testDocumentId)
                            .param("includeQA", "false")
                            .param("includeSummary", "false"))
                    .andDo(print())
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.message").value("Document processing started"))
                    .andExpect(jsonPath("$.documentId").value(testDocumentId))
                    .andExpect(jsonPath("$.titulo").value(testTitle))
                    .andExpect(jsonPath("$.statusUrl").value("/api/v1/documents/" + testDocumentId + "/status"));

            verify(statusTracker, times(1)).startProcessing(eq(testDocumentId), anyString());
            verify(documentoService, times(1)).processDocumentAsyncV2(testDocumentId);
        }

        @Test
        @DisplayName("Should start processing with Q&A and Summary enrichment")
        void processDocument_WithEnrichment_ReturnsAccepted() throws Exception {
            // Arrange
            when(documentoService.findById(testDocumentId)).thenReturn(Optional.of(testDocumentoDTO));

            DocumentoService.ProcessingStatus processingStatus = new DocumentoService.ProcessingStatus();
            processingStatus.setStatus("COMPLETED");

            when(documentoService.processDocumentAsyncV2(testDocumentId))
                    .thenReturn(CompletableFuture.completedFuture(processingStatus));

            // Act & Assert
            mockMvc.perform(post("/api/v1/documents/{documentId}/process", testDocumentId)
                            .param("includeQA", "true")
                            .param("includeSummary", "true"))
                    .andDo(print())
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.phase1").exists())
                    .andExpect(jsonPath("$.phase2").exists())
                    .andExpect(jsonPath("$.enrichmentOptions.includeQA").value(true))
                    .andExpect(jsonPath("$.enrichmentOptions.includeSummary").value(true))
                    .andExpect(jsonPath("$.estimatedTime").value("3-30 minutes"));
        }

        @Test
        @DisplayName("Should reject processing for non-existent document")
        void processDocument_DocumentNotFound_ThrowsException() throws Exception {
            // Arrange
            when(documentoService.findById(testDocumentId)).thenReturn(Optional.empty());

            // Act & Assert
            mockMvc.perform(post("/api/v1/documents/{documentId}/process", testDocumentId))
                    .andDo(print())
                    .andExpect(status().is4xxClientError());

            verify(documentoService, never()).processDocumentAsyncV2(anyInt());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/documents/{documentId}/enrich")
    class EnrichDocumentEndpointTests {

        @Test
        @DisplayName("Should start document enrichment and return 202 Accepted")
        void enrichDocument_ValidOptions_ReturnsAccepted() throws Exception {
            // Arrange
            when(documentoService.findById(testDocumentId)).thenReturn(Optional.of(testDocumentoDTO));

            EnrichmentResult enrichmentResult = EnrichmentResult.builder()
                    .documentId(testDocumentId)
                    .success(true)
                    .chaptersProcessed(4)
                    .qaEmbeddingsGenerated(12)
                    .summaryEmbeddingsGenerated(4)
                    .duration("8.5s")
                    .build();

            when(documentoService.enrichDocumentAsync(eq(testDocumentId), any(EnrichmentOptions.class)))
                    .thenReturn(CompletableFuture.completedFuture(enrichmentResult));

            // Act & Assert
            mockMvc.perform(post("/api/v1/documents/{documentId}/enrich", testDocumentId)
                            .param("generateQA", "true")
                            .param("numberOfQAPairs", "3")
                            .param("generateSummary", "true")
                            .param("maxSummaryLength", "500"))
                    .andDo(print())
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.message").value("Document enrichment started"))
                    .andExpect(jsonPath("$.documentId").value(testDocumentId))
                    .andExpect(jsonPath("$.options.generateQA").value(true))
                    .andExpect(jsonPath("$.options.numberOfQAPairs").value(3))
                    .andExpect(jsonPath("$.options.generateSummary").value(true))
                    .andExpect(jsonPath("$.estimatedTime").value("2-20 minutes"));

            verify(statusTracker, times(1)).startProcessing(eq(testDocumentId), anyString());
            verify(documentoService, times(1))
                    .enrichDocumentAsync(eq(testDocumentId), any(EnrichmentOptions.class));
        }

        @Test
        @DisplayName("Should enrich with Q&A only")
        void enrichDocument_QAOnly_ReturnsAccepted() throws Exception {
            // Arrange
            when(documentoService.findById(testDocumentId)).thenReturn(Optional.of(testDocumentoDTO));

            EnrichmentResult enrichmentResult = EnrichmentResult.builder()
                    .documentId(testDocumentId)
                    .success(true)
                    .qaEmbeddingsGenerated(15)
                    .summaryEmbeddingsGenerated(0)
                    .build();

            when(documentoService.enrichDocumentAsync(eq(testDocumentId), any(EnrichmentOptions.class)))
                    .thenReturn(CompletableFuture.completedFuture(enrichmentResult));

            // Act & Assert
            mockMvc.perform(post("/api/v1/documents/{documentId}/enrich", testDocumentId)
                            .param("generateQA", "true")
                            .param("numberOfQAPairs", "5")
                            .param("generateSummary", "false"))
                    .andDo(print())
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.options.generateQA").value(true))
                    .andExpect(jsonPath("$.options.generateSummary").value(false));
        }

        @Test
        @DisplayName("Should reject enrichment for non-existent document")
        void enrichDocument_DocumentNotFound_ThrowsException() throws Exception {
            // Arrange
            when(documentoService.findById(testDocumentId)).thenReturn(Optional.empty());

            // Act & Assert
            mockMvc.perform(post("/api/v1/documents/{documentId}/enrich", testDocumentId)
                            .param("generateQA", "true"))
                    .andDo(print())
                    .andExpect(status().is4xxClientError());

            verify(documentoService, never())
                    .enrichDocumentAsync(anyInt(), any(EnrichmentOptions.class));
        }
    }

    // ========== Status Monitoring Tests ==========

    @Nested
    @DisplayName("GET /api/v1/documents/{documentId}/status")
    class GetStatusEndpointTests {

        @Test
        @DisplayName("Should return processing status for document")
        void getStatus_ValidDocument_ReturnsStatus() throws Exception {
            // Arrange
            when(documentoService.findById(testDocumentId)).thenReturn(Optional.of(testDocumentoDTO));

            ProcessingStatus processingStatus = new ProcessingStatus();
            processingStatus.setStatus(Status.PROCESSING);
            processingStatus.setProgress(50);
            processingStatus.setMessage("Generating embeddings...");
            processingStatus.setStartedAt(LocalDateTime.now());
            processingStatus.setUpdatedAt(LocalDateTime.now());

            when(statusTracker.getStatus(testDocumentId)).thenReturn(processingStatus);

            // Act & Assert
            mockMvc.perform(get("/api/v1/documents/{documentId}/status", testDocumentId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.documentId").value(testDocumentId))
                    .andExpect(jsonPath("$.titulo").value(testTitle))
                    .andExpect(jsonPath("$.status").value("PROCESSING"))
                    .andExpect(jsonPath("$.progress").value(50))
                    .andExpect(jsonPath("$.message").value("Generating embeddings..."));
        }

        @Test
        @DisplayName("Should return completed status")
        void getStatus_CompletedProcessing_ReturnsCompletedStatus() throws Exception {
            // Arrange
            when(documentoService.findById(testDocumentId)).thenReturn(Optional.of(testDocumentoDTO));

            ProcessingStatus processingStatus = new ProcessingStatus();
            processingStatus.setStatus(Status.COMPLETED);
            processingStatus.setProgress(100);
            processingStatus.setMessage("Processing completed successfully");
            processingStatus.setCompletedAt(LocalDateTime.now());

            when(statusTracker.getStatus(testDocumentId)).thenReturn(processingStatus);

            // Act & Assert
            mockMvc.perform(get("/api/v1/documents/{documentId}/status", testDocumentId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.progress").value(100))
                    .andExpect(jsonPath("$.completedAt").exists());
        }

        @Test
        @DisplayName("Should return failed status with error message")
        void getStatus_FailedProcessing_ReturnsFailedStatus() throws Exception {
            // Arrange
            when(documentoService.findById(testDocumentId)).thenReturn(Optional.of(testDocumentoDTO));

            ProcessingStatus processingStatus = new ProcessingStatus();
            processingStatus.setStatus(Status.FAILED);
            processingStatus.setErrorMessage("LLM service unavailable");

            when(statusTracker.getStatus(testDocumentId)).thenReturn(processingStatus);

            // Act & Assert
            mockMvc.perform(get("/api/v1/documents/{documentId}/status", testDocumentId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.errorMessage").value("LLM service unavailable"));
        }
    }

    // ========== Retrieval Endpoints Tests ==========

    @Nested
    @DisplayName("Document Retrieval Endpoints")
    class RetrievalEndpointsTests {

        @Test
        @DisplayName("GET /api/v1/documents - Should return all documents")
        void findAll_ReturnsAllDocuments() throws Exception {
            // Arrange
            DocumentoDTO doc1 = DocumentoDTO.builder().id(1).titulo("Doc 1").build();
            DocumentoDTO doc2 = DocumentoDTO.builder().id(2).titulo("Doc 2").build();

            when(documentoService.findAll()).thenReturn(Arrays.asList(doc1, doc2));

            // Act & Assert
            mockMvc.perform(get("/api/v1/documents"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[1].id").value(2));
        }

        @Test
        @DisplayName("GET /api/v1/documents/{id} - Should return document by ID")
        void getDocument_ValidId_ReturnsDocument() throws Exception {
            // Arrange
            when(documentoService.findById(testDocumentId)).thenReturn(Optional.of(testDocumentoDTO));

            // Act & Assert
            mockMvc.perform(get("/api/v1/documents/{documentId}", testDocumentId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(testDocumentId))
                    .andExpect(jsonPath("$.titulo").value(testTitle));
        }

        @Test
        @DisplayName("GET /api/v1/documents/{id} - Should return 404 when not found")
        void getDocument_NotFound_ThrowsException() throws Exception {
            // Arrange
            when(documentoService.findById(testDocumentId)).thenReturn(Optional.empty());

            // Act & Assert
            mockMvc.perform(get("/api/v1/documents/{documentId}", testDocumentId))
                    .andDo(print())
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("GET /api/v1/documents/library/{libraryId} - Should return documents for library")
        void getDocumentsByLibrary_ReturnsDocuments() throws Exception {
            // Arrange
            when(documentoService.findByLibraryId(testLibraryId))
                    .thenReturn(Arrays.asList(testDocumentoDTO));

            // Act & Assert
            mockMvc.perform(get("/api/v1/documents/library/{libraryId}", testLibraryId)
                            .param("activeOnly", "false"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].bibliotecaId").value(testLibraryId));
        }

        @Test
        @DisplayName("GET /api/v1/documents/library/{libraryId}?activeOnly=true - Should return only active")
        void getDocumentsByLibrary_ActiveOnly_ReturnsActiveDocuments() throws Exception {
            // Arrange
            when(documentoService.findActiveByLibraryId(testLibraryId))
                    .thenReturn(Arrays.asList(testDocumentoDTO));

            // Act & Assert
            mockMvc.perform(get("/api/v1/documents/library/{libraryId}", testLibraryId)
                            .param("activeOnly", "true"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].flagVigente").value(true));

            verify(documentoService, times(1)).findActiveByLibraryId(testLibraryId);
        }
    }

    // ========== Management Endpoints Tests ==========

    @Nested
    @DisplayName("Document Management Endpoints")
    class ManagementEndpointsTests {

        @Test
        @DisplayName("POST /api/v1/documents/{id}/status - Should update document status")
        void updateStatus_ValidRequest_ReturnsOk() throws Exception {
            // Arrange
            doNothing().when(documentoService).updateStatus(testDocumentId, false);

            // Act & Assert
            mockMvc.perform(post("/api/v1/documents/{documentId}/status", testDocumentId)
                            .param("flagVigente", "false"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Document status updated"))
                    .andExpect(jsonPath("$.documentId").value(testDocumentId))
                    .andExpect(jsonPath("$.flagVigente").value(false));

            verify(documentoService, times(1)).updateStatus(testDocumentId, false);
        }

        @Test
        @DisplayName("DELETE /api/v1/documents/{id} - Should soft delete document")
        void deleteDocument_ValidId_ReturnsNoContent() throws Exception {
            // Arrange
            doNothing().when(documentoService).delete(testDocumentId);

            // Act & Assert
            mockMvc.perform(delete("/api/v1/documents/{documentId}", testDocumentId))
                    .andDo(print())
                    .andExpect(status().isNoContent());

            verify(documentoService, times(1)).delete(testDocumentId);
        }

        @Test
        @DisplayName("DELETE /api/v1/documents/{id} - Should handle not found")
        void deleteDocument_NotFound_ThrowsException() throws Exception {
            // Arrange
            doThrow(new IllegalArgumentException("Document not found"))
                    .when(documentoService).delete(testDocumentId);

            // Act & Assert
            mockMvc.perform(delete("/api/v1/documents/{documentId}", testDocumentId))
                    .andDo(print())
                    .andExpect(status().is4xxClientError());
        }
    }
}
