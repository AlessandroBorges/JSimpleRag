package bor.tools.simplerag.controller;

import bor.tools.simplerag.dto.DocumentoDTO;
import bor.tools.simplerag.service.DocumentoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for DocumentController
 *
 * Tests REST API endpoints using MockMvc
 */
@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DocumentoService documentoService;

    private DocumentoDTO testDocument;

    @BeforeEach
    void setUp() {
        testDocument = DocumentoDTO.builder()
                .id(1)
                .bibliotecaId(1)
                .titulo("Test Document")
                .conteudoMarkdown("# Test Content")
                .flagVigente(true)
                .dataPublicacao(LocalDate.now())
                .tokensTotal(100)
                .build();
    }

    // ============ Upload from Text Tests ============

    @Test
    void testUploadFromText_Success() throws Exception {
        // Given
        DocumentController.UploadTextRequest request = new DocumentController.UploadTextRequest();
        request.setTitulo("Test Document");
        request.setConteudo("# Test\nContent");
        request.setLibraryId(1);

        when(documentoService.uploadFromText(anyString(), anyString(), anyInt(), any()))
                .thenReturn(testDocument);

        // When/Then
        mockMvc.perform(post("/api/v1/documents/upload/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.titulo").value("Test Document"));

        verify(documentoService).uploadFromText(
                eq("Test Document"),
                eq("# Test\nContent"),
                eq(1),
                any()
        );
    }

    @Test
    void testUploadFromText_WithMetadata() throws Exception {
        // Given
        DocumentController.UploadTextRequest request = new DocumentController.UploadTextRequest();
        request.setTitulo("Test");
        request.setConteudo("Content");
        request.setLibraryId(1);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("autor", "Test Author");
        request.setMetadados(metadata);

        when(documentoService.uploadFromText(anyString(), anyString(), anyInt(), any()))
                .thenReturn(testDocument);

        // When/Then
        mockMvc.perform(post("/api/v1/documents/upload/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(documentoService).uploadFromText(
                anyString(),
                anyString(),
                anyInt(),
                argThat(map -> map != null && map.containsKey("autor"))
        );
    }

    @Test
    void testUploadFromText_ValidationError() throws Exception {
        // Given
        when(documentoService.uploadFromText(anyString(), anyString(), anyInt(), any()))
                .thenThrow(new IllegalArgumentException("Library not found"));

        DocumentController.UploadTextRequest request = new DocumentController.UploadTextRequest();
        request.setTitulo("Test");
        request.setConteudo("Content");
        request.setLibraryId(999);

        // When/Then
        mockMvc.perform(post("/api/v1/documents/upload/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());
    }

    // ============ Upload from URL Tests ============

    @Test
    void testUploadFromUrl_Success() throws Exception {
        // Given
        DocumentController.UploadUrlRequest request = new DocumentController.UploadUrlRequest();
        request.setUrl("https://example.com/doc.pdf");
        request.setLibraryId(1);
        request.setTitulo("Test Document");

        when(documentoService.uploadFromUrl(anyString(), anyInt(), anyString(), any()))
                .thenReturn(testDocument);

        // When/Then
        mockMvc.perform(post("/api/v1/documents/upload/url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));

        verify(documentoService).uploadFromUrl(
                eq("https://example.com/doc.pdf"),
                eq(1),
                eq("Test Document"),
                any()
        );
    }

    @Test
    void testUploadFromUrl_NoTitle() throws Exception {
        // Given
        DocumentController.UploadUrlRequest request = new DocumentController.UploadUrlRequest();
        request.setUrl("https://example.com/document.pdf");
        request.setLibraryId(1);

        when(documentoService.uploadFromUrl(anyString(), anyInt(), any(), any()))
                .thenReturn(testDocument);

        // When/Then
        mockMvc.perform(post("/api/v1/documents/upload/url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(documentoService).uploadFromUrl(
                eq("https://example.com/document.pdf"),
                eq(1),
                isNull(),
                any()
        );
    }

    // ============ Upload from File Tests ============

    @Test
    void testUploadFromFile_Success() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                "PDF content".getBytes()
        );

        when(documentoService.uploadFromFile(anyString(), any(byte[].class), anyInt(), any()))
                .thenReturn(testDocument);

        // When/Then
        mockMvc.perform(multipart("/api/v1/documents/upload/file")
                        .file(file)
                        .param("libraryId", "1")
                        .param("titulo", "Test Document"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.titulo").value("Test Document"));

        verify(documentoService).uploadFromFile(
                eq("test.pdf"),
                any(byte[].class),
                eq(1),
                any()
        );
    }

    @Test
    void testUploadFromFile_WithoutTitle() throws Exception {
        // Given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "content".getBytes()
        );

        when(documentoService.uploadFromFile(anyString(), any(byte[].class), anyInt(), any()))
                .thenReturn(testDocument);

        // When/Then
        mockMvc.perform(multipart("/api/v1/documents/upload/file")
                        .file(file)
                        .param("libraryId", "1"))
                .andExpect(status().isCreated());

        verify(documentoService).uploadFromFile(
                eq("document.pdf"),
                any(byte[].class),
                eq(1),
                any()
        );
    }

    // ============ Process Document Tests ============

    @Test
    void testProcessDocument_Success() throws Exception {
        // Given
        DocumentoService.ProcessingStatus status = new DocumentoService.ProcessingStatus();
        status.setDocumentId(1);
        status.setStatus("COMPLETED");

        when(documentoService.processDocumentAsync(anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(status));

        // When/Then
        mockMvc.perform(post("/api/v1/documents/1/process")
                        .param("includeQA", "true")
                        .param("includeSummary", "false"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value("Document processing started"))
                .andExpect(jsonPath("$.documentId").value(1))
                .andExpect(jsonPath("$.statusUrl").value("/api/v1/documents/1/status"));

        verify(documentoService).processDocumentAsync(1, true, false);
    }

    @Test
    void testProcessDocument_DefaultParameters() throws Exception {
        // Given
        DocumentoService.ProcessingStatus status = new DocumentoService.ProcessingStatus();
        status.setDocumentId(1);

        when(documentoService.processDocumentAsync(anyInt(), anyBoolean(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(status));

        // When/Then
        mockMvc.perform(post("/api/v1/documents/1/process"))
                .andExpect(status().isAccepted());

        verify(documentoService).processDocumentAsync(1, false, false);
    }

    @Test
    void testProcessDocument_DocumentNotFound() throws Exception {
        // Given
        when(documentoService.processDocumentAsync(anyInt(), anyBoolean(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("Document not found"));

        // When/Then
        mockMvc.perform(post("/api/v1/documents/999/process"))
                .andExpect(status().is4xxClientError());
    }

    // ============ Get Processing Status Tests ============

    @Test
    void testGetProcessingStatus_Success() throws Exception {
        // Given
        when(documentoService.findById(1)).thenReturn(Optional.of(testDocument));

        // When/Then
        mockMvc.perform(get("/api/v1/documents/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(1))
                .andExpect(jsonPath("$.titulo").value("Test Document"))
                .andExpect(jsonPath("$.tokensTotal").value(100))
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void testGetProcessingStatus_DocumentNotFound() throws Exception {
        // Given
        when(documentoService.findById(999)).thenReturn(Optional.empty());

        // When/Then
        mockMvc.perform(get("/api/v1/documents/999/status"))
                .andExpect(status().is4xxClientError());
    }

    // ============ Get Document Tests ============

    @Test
    void testGetDocument_Success() throws Exception {
        // Given
        when(documentoService.findById(1)).thenReturn(Optional.of(testDocument));

        // When/Then
        mockMvc.perform(get("/api/v1/documents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.titulo").value("Test Document"))
                .andExpect(jsonPath("$.conteudoMarkdown").value("# Test Content"));
    }

    @Test
    void testGetDocument_NotFound() throws Exception {
        // Given
        when(documentoService.findById(999)).thenReturn(Optional.empty());

        // When/Then
        mockMvc.perform(get("/api/v1/documents/999"))
                .andExpect(status().is4xxClientError());
    }

    // ============ Get Documents by Library Tests ============

    @Test
    void testGetDocumentsByLibrary_Success() throws Exception {
        // Given
        List<DocumentoDTO> documents = Arrays.asList(testDocument);
        when(documentoService.findByLibraryId(1)).thenReturn(documents);

        // When/Then
        mockMvc.perform(get("/api/v1/documents/library/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].titulo").value("Test Document"));

        verify(documentoService).findByLibraryId(1);
    }

    @Test
    void testGetDocumentsByLibrary_ActiveOnly() throws Exception {
        // Given
        List<DocumentoDTO> documents = Arrays.asList(testDocument);
        when(documentoService.findActiveByLibraryId(1)).thenReturn(documents);

        // When/Then
        mockMvc.perform(get("/api/v1/documents/library/1")
                        .param("activeOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flagVigente").value(true));

        verify(documentoService).findActiveByLibraryId(1);
        verify(documentoService, never()).findByLibraryId(anyInt());
    }

    @Test
    void testGetDocumentsByLibrary_EmptyList() throws Exception {
        // Given
        when(documentoService.findByLibraryId(1)).thenReturn(Collections.emptyList());

        // When/Then
        mockMvc.perform(get("/api/v1/documents/library/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ============ Update Status Tests ============

    @Test
    void testUpdateStatus_Success() throws Exception {
        // Given
        doNothing().when(documentoService).updateStatus(1, false);

        // When/Then
        mockMvc.perform(post("/api/v1/documents/1/status")
                        .param("flagVigente", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Document status updated"))
                .andExpect(jsonPath("$.documentId").value(1))
                .andExpect(jsonPath("$.flagVigente").value(false));

        verify(documentoService).updateStatus(1, false);
    }

    @Test
    void testUpdateStatus_Activate() throws Exception {
        // Given
        doNothing().when(documentoService).updateStatus(1, true);

        // When/Then
        mockMvc.perform(post("/api/v1/documents/1/status")
                        .param("flagVigente", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagVigente").value(true));

        verify(documentoService).updateStatus(1, true);
    }

    @Test
    void testUpdateStatus_DocumentNotFound() throws Exception {
        // Given
        doThrow(new IllegalArgumentException("Document not found"))
                .when(documentoService).updateStatus(999, false);

        // When/Then
        mockMvc.perform(post("/api/v1/documents/999/status")
                        .param("flagVigente", "false"))
                .andExpect(status().is4xxClientError());
    }

    // ============ Delete Document Tests ============

    @Test
    void testDeleteDocument_Success() throws Exception {
        // Given
        doNothing().when(documentoService).delete(1);

        // When/Then
        mockMvc.perform(delete("/api/v1/documents/1"))
                .andExpect(status().isNoContent());

        verify(documentoService).delete(1);
    }

    @Test
    void testDeleteDocument_NotFound() throws Exception {
        // Given
        doThrow(new IllegalArgumentException("Document not found"))
                .when(documentoService).delete(999);

        // When/Then
        mockMvc.perform(delete("/api/v1/documents/999"))
                .andExpect(status().is4xxClientError());
    }

    // ============ Request DTO Tests ============

    @Test
    void testUploadTextRequest_Serialization() throws Exception {
        // Test that request DTO can be properly serialized/deserialized
        DocumentController.UploadTextRequest request = new DocumentController.UploadTextRequest();
        request.setTitulo("Test");
        request.setConteudo("Content");
        request.setLibraryId(1);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("key", "value");
        request.setMetadados(metadata);

        String json = objectMapper.writeValueAsString(request);
        DocumentController.UploadTextRequest deserialized =
                objectMapper.readValue(json, DocumentController.UploadTextRequest.class);

        assertEquals(request.getTitulo(), deserialized.getTitulo());
        assertEquals(request.getConteudo(), deserialized.getConteudo());
        assertEquals(request.getLibraryId(), deserialized.getLibraryId());
        assertEquals(request.getMetadados().get("key"), deserialized.getMetadados().get("key"));
    }

    @Test
    void testUploadUrlRequest_Serialization() throws Exception {
        DocumentController.UploadUrlRequest request = new DocumentController.UploadUrlRequest();
        request.setUrl("https://example.com");
        request.setLibraryId(1);
        request.setTitulo("Test");

        String json = objectMapper.writeValueAsString(request);
        DocumentController.UploadUrlRequest deserialized =
                objectMapper.readValue(json, DocumentController.UploadUrlRequest.class);

        assertEquals(request.getUrl(), deserialized.getUrl());
        assertEquals(request.getLibraryId(), deserialized.getLibraryId());
        assertEquals(request.getTitulo(), deserialized.getTitulo());
    }

    // ============ Error Handling Tests ============

    @Test
    void testUploadFromText_InternalServerError() throws Exception {
        // Given
        DocumentController.UploadTextRequest request = new DocumentController.UploadTextRequest();
        request.setTitulo("Test");
        request.setConteudo("Content");
        request.setLibraryId(1);

        when(documentoService.uploadFromText(anyString(), anyString(), anyInt(), any()))
                .thenThrow(new RuntimeException("Database error"));

        // When/Then
        mockMvc.perform(post("/api/v1/documents/upload/text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void testUploadFromUrl_ConversionError() throws Exception {
        // Given
        DocumentController.UploadUrlRequest request = new DocumentController.UploadUrlRequest();
        request.setUrl("https://example.com/doc.pdf");
        request.setLibraryId(1);

        when(documentoService.uploadFromUrl(anyString(), anyInt(), anyString(), any()))
                .thenThrow(new RuntimeException("Conversion failed"));

        // When/Then
        mockMvc.perform(post("/api/v1/documents/upload/url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is5xxServerError());
    }
}
