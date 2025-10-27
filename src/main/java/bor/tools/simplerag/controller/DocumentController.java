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
import bor.tools.simplerag.dto.UploadTextRequest;
import bor.tools.simplerag.dto.UploadUrlRequest;
import bor.tools.simplerag.entity.MetaDoc;
import bor.tools.simplerag.service.DocumentoService;
import bor.tools.simplerag.service.ProcessingStatusTracker;
import bor.tools.simplerag.service.ProcessingStatusTracker.ProcessingStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;
    private final ProcessingStatusTracker statusTracker;

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
    @Operation(
        summary = "Upload document from text",
        description = """
            Upload document from markdown or plain text content.

            **Recommended for:** Content already in markdown format or plain text

            **Upload Workflow:**
            1. POST /api/v1/documents/upload/text → Returns document with ID
            2. POST /api/v1/documents/{id}/process → Starts async processing
            3. GET /api/v1/documents/{id}/status → Monitor processing status

            **Requirements:**
            - Title: 3-500 characters
            - Content: Minimum 100 characters
            - Library must exist

            **Metadata:** Optional JSON with fields like autor, isbn, palavras_chave

            **Processing:** Document is NOT automatically processed. Call /process endpoint after upload.
            """
    )
    public ResponseEntity<DocumentoDTO> uploadFromText(@Valid @RequestBody UploadTextRequest request) {
        log.info("Uploading document from text: {} (library={})", request.getTitulo(), request.getLibraryId());

        try {
            DocumentoDTO saved = documentoService.uploadFromText(
                    request.getTitulo(),
                    request.getConteudo(),
                    request.getLibraryId(),
                    new MetaDoc( request.getMetadados())
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
    @Operation(
        summary = "Upload document from URL",
        description = """
            Download document from URL and convert to markdown.

            **Supported Formats:**
            - HTML web pages (extracts main content)
            - PDF documents
            - Microsoft Office (DOCX, XLSX, PPTX)
            - Plain text files

            **Automatic Extraction:**
            - Title: Extracted from HTML <title>, PDF metadata, or filename
            - Content: Converted to markdown automatically
            - Metadata: fonte_url and data_download auto-populated

            **Processing:** Document is NOT automatically processed. Call /process endpoint after upload.

            **Note:** URL must be publicly accessible (no authentication required)
            """
    )
    public ResponseEntity<DocumentoDTO> uploadFromUrl(@Valid @RequestBody UploadUrlRequest request) {
        log.info("Uploading document from URL: {} (library={})", request.getUrl(), request.getLibraryId());

        try {
            DocumentoDTO saved = documentoService.uploadFromUrl(
                    request.getUrl(),
                    request.getLibraryId(),
                    request.getTitulo(),
                    new MetaDoc( request.getMetadados())
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
     * @param metadataJson Optional metadata JSON
     * @return Created document DTO
     */
    @PostMapping(value = "/upload/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        summary = "Upload document from file",
        description = """
            Upload document file with automatic conversion to markdown.

            **Supported Formats:**
            - PDF (.pdf)
            - Microsoft Word (.doc, .docx)
            - Text files (.txt, .md)
            - OpenDocument (.odt)
            - Rich Text Format (.rtf)

            **Size Limits:**
            - Maximum file size: 50MB
            - Minimum content: 100 characters after conversion

            **Parameters:**
            - file: The document file (required)
            - libraryId: UUID of target library (required)
            - titulo: Optional title (auto-extracted from filename if not provided)
            - metadata: Optional JSON string with custom metadata

            **Metadata Example:**
            ```json
            {"autor": "John Doe", "tipo_conteudo": "1", "isbn": "978-0132350884"}
            ```

            **Processing:** Document is NOT automatically processed. Call /process endpoint after upload.
            """
    )
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

            String filename = file.getOriginalFilename();                      
	    if (filename != null) {
		if (titulo == null || titulo.isBlank()) {
		    // Derive title from filename if not provided
		    titulo = filename != null ? filename.replaceFirst("[.][^.]+$", "") : "Untitled Document";
		}
		metadata.put("nome_documento", titulo);
		metadata.put("url", filename);
	    }
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
     * Get all documents
     */
    @GetMapping
    @Operation(
        summary = "Get all documents",
        description = """
            Returns all documents across all libraries.

            **Use Cases:**
            - Administrative overview of all documents
            - Global search across libraries
            - Document inventory management

            **Note:** To get documents for a specific library, use /library/{libraryId} endpoint instead.

            **Response includes:**
            - Document ID and title
            - Library ID
            - Active status (flagVigente)
            - Token count
            - Publication date
            - Creation and update timestamps
            """
    )
    public ResponseEntity<List<DocumentoDTO>> findAll() {
        log.debug("Finding all documents");

        try {
            List<DocumentoDTO> documents = documentoService.findAll();

            log.info("Found {} documents", documents.size());

            return ResponseEntity.ok(documents);

        } catch (Exception e) {
            log.error("Error finding all documents: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao buscar documentos: " + e.getMessage(), e);
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
    @Operation(
        summary = "Process document asynchronously",
        description = """
            Initiates asynchronous document processing:
            1. Splits document into chapters (~8k tokens each)
            2. Generates embeddings for document, chapters, and chunks
            3. Persists embeddings to database for search

            **Processing time:** 1-10 minutes depending on document size
            **Returns immediately** with 202 Accepted status
            **Monitor progress:** Use GET /api/v1/documents/{id}/status

            **Optional Parameters:**
            - includeQA: Generate Q&A pairs from content (experimental)
            - includeSummary: Generate chapter summaries (experimental)
            """
    )
    public ResponseEntity<Map<String, Object>> processDocument(
            @PathVariable Integer documentId,
            @RequestParam(defaultValue = "false") boolean includeQA,
            @RequestParam(defaultValue = "false") boolean includeSummary) {

        log.info("Starting document processing: id={}, includeQA={}, includeSummary={}",
                documentId, includeQA, includeSummary);

        try {
            // Verify document exists and get title
            DocumentoDTO documento = documentoService.findById(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

            // Start status tracking
            statusTracker.startProcessing(documentId, documento.getTitulo());

            // Start async processing
            CompletableFuture<DocumentoService.ProcessingStatus> future =
                    documentoService.processDocumentAsync(documentId, includeQA, includeSummary);

            // Add success and error handlers for async processing
            future.thenAccept(status -> {
                log.info("Async processing completed for document {}: status={}, chapters={}, embeddings={}",
                        documentId, status.getStatus(), status.getChaptersCount(), status.getEmbeddingsCount());
                if ("COMPLETED".equals(status.getStatus())) {
                    statusTracker.markCompleted(documentId,
                            "Processed: " + status.getChaptersCount() + " chapters, " +
                            status.getEmbeddingsCount() + " embeddings");
                } else if ("FAILED".equals(status.getStatus())) {
                    statusTracker.markFailed(documentId, status.getErrorMessage());
                }
            }).exceptionally(error -> {
                log.error("Async processing failed for document {}: {}", documentId, error.getMessage(), error);
                statusTracker.markFailed(documentId, error.getMessage());
                return null;
            });

            // Return immediately with status URL
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Document processing started");
            response.put("documentId", documentId);
            response.put("titulo", documento.getTitulo());
            response.put("statusUrl", "/api/v1/documents/" + documentId + "/status");
            response.put("estimatedTime", "1-10 minutes");

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
     * Uses in-memory ProcessingStatusTracker to provide real-time status updates.
     *
     * @param documentId Document ID
     * @return Processing status
     */
    @GetMapping("/{documentId}/status")
    @Operation(
        summary = "Get document processing status",
        description = """
            Returns real-time processing status for a document.

            **Status Values:**
            - NOT_STARTED: Document exists but processing not initiated
            - PROCESSING: Currently generating embeddings and chunks
            - COMPLETED: Processing finished successfully
            - FAILED: Processing encountered an error

            **Progress:** Integer 0-100 indicating completion percentage
            """
    )
    public ResponseEntity<Map<String, Object>> getProcessingStatus(@PathVariable Integer documentId) {
        log.debug("Getting processing status for document: {}", documentId);

        try {
            DocumentoDTO documento = documentoService.findById(documentId)
                    .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

            // Get real-time processing status from tracker
            ProcessingStatus processingStatus = statusTracker.getStatus(documentId);

            Map<String, Object> response = new HashMap<>();
            response.put("documentId", documentId);
            response.put("titulo", documento.getTitulo());
            response.put("status", processingStatus.getStatus().toString());
            response.put("statusDescription", processingStatus.getStatus().getDescription());
            response.put("progress", processingStatus.getProgress());
            response.put("message", processingStatus.getMessage());
            response.put("startedAt", processingStatus.getStartedAt());
            response.put("updatedAt", processingStatus.getUpdatedAt());
            response.put("completedAt", processingStatus.getCompletedAt());

            if (processingStatus.getErrorMessage() != null) {
                response.put("errorMessage", processingStatus.getErrorMessage());
            }

            // Include document metadata
            response.put("tokensTotal", documento.getTokensTotal());
            response.put("flagVigente", documento.getFlagVigente());
            response.put("createdAt", documento.getCreatedAt());

            return ResponseEntity.ok(response);

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
     * Parse metadata JSON string to Map using Jackson ObjectMapper
     */
    private Map<String, Object> parseMetadata(String metadataJson) {	
	if (metadataJson == null || metadataJson.trim().isEmpty()) {
	    return new HashMap<>();
	}
	
	HashMap<String, Object> result = new HashMap<>();
	
	metadataJson = metadataJson.trim();	
	
	// Check for key=value pairs or simple keyword list
	if (!metadataJson.startsWith("{") || !metadataJson.startsWith("[")) {
	    if(metadataJson.contains("=")) {
		// Parse key=value pairs
		String[] pairs = metadataJson.split(",");
		for (String pair : pairs) {
		    String[] keyValue = pair.split("=", 2);
		    if (keyValue.length == 2) {
			result.put(keyValue[0].trim(), keyValue[1].trim());
		    }
		}
	    } else {
		// possible a list of keywords
		String[] keywords = metadataJson.split(",");
		result.put("keywords", List.of(keywords));
	    }	    
	    return result;	    
	}
	
	// Try parsing as JSON
        try {
            return objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse metadata JSON: {}. Returning empty map.", e.getMessage());
            return new HashMap<>();
        }
    }

}
