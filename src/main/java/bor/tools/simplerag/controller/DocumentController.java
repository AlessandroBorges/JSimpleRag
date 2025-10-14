package bor.tools.simplerag.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import bor.tools.simplerag.dto.DocumentoDTO;
import bor.tools.simplerag.service.DocumentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller for Document management.
 *
 * Implements document upload endpoints according to Fluxo_carga_documents.md:
 * - Upload from text (markdown or plain text)
 * - Upload from URL (web documents)
 * - Upload from file (multipart upload)
 *
 * Also provides document processing, status, and retrieval endpoints.
 *
 * @see Fluxo_carga_documents.md
 * @see DOCUMENT_LOADING_IMPLEMENTATION_PLAN.md
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Documents", description = "Document upload, processing, and management")
public class DocumentController {

    private final DocumentoService documentoService;

    /**
     * Upload document from text content
     *
     * Fluxo step (a): Carga Normal
     * Expects markdown or plain text content
     *
     * @param request Upload request with text content
     * @return Created document DTO
     */
    @PostMapping("/upload/text")
    @Operation(summary = "Upload document from text",
               description = "Upload document from markdown or plain text. " +
                            "Recommended for content already in markdown format.")
    public ResponseEntity<DocumentoDTO> uploadFromText(@Valid @RequestBody UploadTextRequest request) {
        log.info("Uploading document from text: {} (library={})", request.getTitulo(), request.getLibraryId());

        try {
            DocumentoDTO saved = documentoService.uploadFromText(
                    request.getTitulo(),
                    request.getConteudo(),
                    request.getLibraryId(),
                    request.getMetadados()
            );

            log.info("Document uploaded from text: id={}", saved.getId());

            return ResponseEntity.status(HttpStatus.CREATED).body(saved);

        } catch (IllegalArgumentException e) {
            log.error("Validation error uploading from text: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error uploading from text: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao fazer upload de texto: " + e.getMessage(), e);
        }
    }

    /**
     * Upload document from URL
     *
     * Fluxo step (a): Carga alternativa (A)
     * Downloads content from URL and converts to markdown
     *
     * @param request Upload request with URL
     * @return Created document DTO
     */
    @PostMapping("/upload/url")
    @Operation(summary = "Upload document from URL",
               description = "Download and convert document from URL. " +
                            "Supports HTML, PDF, MS Office formats.")
    public ResponseEntity<DocumentoDTO> uploadFromUrl(@Valid @RequestBody UploadUrlRequest request) {
        log.info("Uploading document from URL: {} (library={})", request.getUrl(), request.getLibraryId());

        try {
            DocumentoDTO saved = documentoService.uploadFromUrl(
                    request.getUrl(),
                    request.getLibraryId(),
                    request.getTitulo(),
                    request.getMetadados()
            );

            log.info("Document uploaded from URL: id={}", saved.getId());

            return ResponseEntity.status(HttpStatus.CREATED).body(saved);

        } catch (IllegalArgumentException e) {
            log.error("Validation error uploading from URL: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error uploading from URL: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao fazer upload de URL: " + e.getMessage(), e);
        }
    }

    /**
     * Upload document from file (multipart)
     *
     * Fluxo step (a): Carga alternativa (B)
     * Accepts file upload and converts to markdown
     *
     * @param file Uploaded file
     * @param libraryId Library ID
     * @param titulo Optional title (will be derived from filename if null)
     * @param metadata Optional metadata JSON
     * @return Created document DTO
     */
    @PostMapping(value = "/upload/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload document from file",
               description = "Upload document file (PDF, DOCX, TXT, etc.). " +
                            "File will be converted to markdown automatically.")
    public ResponseEntity<DocumentoDTO> uploadFromFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam("libraryId") Integer libraryId,
            @RequestParam(value = "titulo", required = false) String titulo,
            @RequestParam(value = "metadata", required = false) String metadataJson) {

        log.info("Uploading document from file: {} (library={})", file.getOriginalFilename(), libraryId);

        try {
            // Parse metadata if provided
            Map<String, Object> metadata = parseMetadata(metadataJson);

            // Get file bytes
            byte[] fileContent = file.getBytes();

            // Upload
            DocumentoDTO saved = documentoService.uploadFromFile(
                    file.getOriginalFilename(),
                    fileContent,
                    libraryId,
                    metadata
            );

            log.info("Document uploaded from file: id={}", saved.getId());

            return ResponseEntity.status(HttpStatus.CREATED).body(saved);

        } catch (IllegalArgumentException e) {
            log.error("Validation error uploading file: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error uploading file: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao fazer upload de arquivo: " + e.getMessage(), e);
        }
    }

    /**
     * Process document asynchronously
     *
     * Fluxo steps (d) through (g): splitting, embedding generation, persistence
     *
     * @param documentId Document ID
     * @param includeQA Whether to include Q&A generation
     * @param includeSummary Whether to include summary generation
     * @return Processing status (async)
     */
    @PostMapping("/{documentId}/process")
    @Operation(summary = "Process document asynchronously",
               description = "Splits document into chapters, generates embeddings, and persists to database. " +
                            "Returns immediately with status endpoint URL.")
    public ResponseEntity<Map<String, Object>> processDocument(
            @PathVariable Integer documentId,
            @RequestParam(defaultValue = "false") boolean includeQA,
            @RequestParam(defaultValue = "false") boolean includeSummary) {

        log.info("Starting document processing: id={}, includeQA={}, includeSummary={}",
                documentId, includeQA, includeSummary);

        try {
            // Start async processing
            CompletableFuture<DocumentoService.ProcessingStatus> future =
                    documentoService.processDocumentAsync(documentId, includeQA, includeSummary);

            // Return immediately with status URL
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Document processing started");
            response.put("documentId", documentId);
            response.put("statusUrl", "/api/v1/documents/" + documentId + "/status");

            log.info("Document processing started: id={}", documentId);

            return ResponseEntity.accepted().body(response);

        } catch (IllegalArgumentException e) {
            log.error("Validation error processing document: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error starting document processing: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao iniciar processamento: " + e.getMessage(), e);
        }
    }

    /**
     * Get document processing status
     *
     * Note: This is a simplified version. A production implementation would
     * track processing status in a separate table or cache.
     *
     * @param documentId Document ID
     * @return Processing status
     */
    @GetMapping("/{documentId}/status")
    @Operation(summary = "Get document processing status",
               description = "Returns current processing status for a document")
    public ResponseEntity<Map<String, Object>> getProcessingStatus(@PathVariable Integer documentId) {
        log.debug("Getting processing status for document: {}", documentId);

        try {
            DocumentoDTO documento = documentoService.findById(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

            Map<String, Object> status = new HashMap<>();
            status.put("documentId", documentId);
            status.put("titulo", documento.getTitulo());
            status.put("tokensTotal", documento.getTokensTotal());
            status.put("flagVigente", documento.getFlagVigente());
            status.put("createdAt", documento.getCreatedAt());
            status.put("updatedAt", documento.getUpdatedAt());

            // Note: In production, check processing status from a tracking table
            status.put("status", documento.getTokensTotal() != null ? "COMPLETED" : "PENDING");

            return ResponseEntity.ok(status);

        } catch (IllegalArgumentException e) {
            log.error("Document not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error getting processing status: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao obter status: " + e.getMessage(), e);
        }
    }

    /**
     * Get document by ID
     *
     * @param documentId Document ID
     * @return Document DTO
     */
    @GetMapping("/{documentId}")
    @Operation(summary = "Get document by ID")
    public ResponseEntity<DocumentoDTO> getDocument(@PathVariable Integer documentId) {
        log.debug("Getting document: {}", documentId);

        DocumentoDTO documento = documentoService.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

        return ResponseEntity.ok(documento);
    }

    /**
     * Get all documents for a library
     *
     * @param libraryId Library ID
     * @param activeOnly Whether to return only active documents
     * @return List of documents
     */
    @GetMapping("/library/{libraryId}")
    @Operation(summary = "Get documents by library",
               description = "Returns all documents for a library. " +
                            "Use activeOnly=true to filter only active documents.")
    public ResponseEntity<List<DocumentoDTO>> getDocumentsByLibrary(
            @PathVariable Integer libraryId,
            @RequestParam(defaultValue = "false") boolean activeOnly) {

        log.debug("Getting documents for library: {} (activeOnly={})", libraryId, activeOnly);

        List<DocumentoDTO> documents = activeOnly
                ? documentoService.findActiveByLibraryId(libraryId)
                : documentoService.findByLibraryId(libraryId);

        return ResponseEntity.ok(documents);
    }

    /**
     * Update document status (activate/deactivate)
     *
     * @param documentId Document ID
     * @param flagVigente New status
     * @return Success message
     */
    @PostMapping("/{documentId}/status")
    @Operation(summary = "Update document status",
               description = "Activate or deactivate document (flagVigente)")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable Integer documentId,
            @RequestParam boolean flagVigente) {

        log.info("Updating document status: id={}, flagVigente={}", documentId, flagVigente);

        try {
            documentoService.updateStatus(documentId, flagVigente);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Document status updated");
            response.put("documentId", documentId);
            response.put("flagVigente", flagVigente);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Document not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error updating document status: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao atualizar status: " + e.getMessage(), e);
        }
    }

    /**
     * Delete document (soft delete by setting flagVigente=false)
     *
     * @param documentId Document ID
     * @return No content
     */
    @DeleteMapping("/{documentId}")
    @Operation(summary = "Delete document (soft delete)",
               description = "Sets flagVigente=false. Document remains in database.")
    public ResponseEntity<Void> deleteDocument(@PathVariable Integer documentId) {
        log.info("Deleting document: {}", documentId);

        try {
            documentoService.delete(documentId);

            log.info("Document deleted: {}", documentId);

            return ResponseEntity.noContent().build();

        } catch (IllegalArgumentException e) {
            log.error("Document not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error deleting document: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao deletar documento: " + e.getMessage(), e);
        }
    }

    // ============ Helper Methods ============

    /**
     * Parse metadata JSON string
     */
    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.trim().isEmpty()) {
            return new HashMap<>();
        }

        try {
            // Simple JSON parsing (in production, use Jackson ObjectMapper)
            // For now, return empty map
            // TODO: Implement proper JSON parsing
            return new HashMap<>();
        } catch (Exception e) {
            log.warn("Failed to parse metadata JSON: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    // ============ Request DTOs ============

    /**
     * Request DTO for text upload
     */
    public static class UploadTextRequest {
        private String titulo;
        private String conteudo;
        private Integer libraryId;
        private Map<String, Object> metadados;

        // Getters and Setters
        public String getTitulo() { return titulo; }
        public void setTitulo(String titulo) { this.titulo = titulo; }

        public String getConteudo() { return conteudo; }
        public void setConteudo(String conteudo) { this.conteudo = conteudo; }

        public Integer getLibraryId() { return libraryId; }
        public void setLibraryId(Integer libraryId) { this.libraryId = libraryId; }

        public Map<String, Object> getMetadados() { return metadados; }
        public void setMetadados(Map<String, Object> metadados) { this.metadados = metadados; }
    }

    /**
     * Request DTO for URL upload
     */
    public static class UploadUrlRequest {
        private String url;
        private Integer libraryId;
        private String titulo;
        private Map<String, Object> metadados;

        // Getters and Setters
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public Integer getLibraryId() { return libraryId; }
        public void setLibraryId(Integer libraryId) { this.libraryId = libraryId; }

        public String getTitulo() { return titulo; }
        public void setTitulo(String titulo) { this.titulo = titulo; }

        public Map<String, Object> getMetadados() { return metadados; }
        public void setMetadados(Map<String, Object> metadados) { this.metadados = metadados; }
    }
}
