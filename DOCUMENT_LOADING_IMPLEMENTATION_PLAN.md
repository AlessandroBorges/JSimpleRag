# Document Loading Implementation Plan

**Project**: JSimpleRag - Document Loading & Preparation
**Date**: 2025-10-13
**Status**: Planning Phase
**Estimated Duration**: 3-4 weeks (full-time)

---

## Executive Summary

This plan addresses the gaps identified in the document loading and preparation workflow. The implementation is divided into 2 priorities with 6 major tasks, creating a complete end-to-end document ingestion pipeline.

**Current State**:
- ✅ Excellent infrastructure (Router, Splitters, Processors)
- ❌ Missing API layer and persistence integration

**Target State**:
- Complete REST API for document upload (text, URL, file)
- Full workflow orchestration with transactional persistence
- Standard document conversion interface
- Production-ready async processing pipeline

---

## Priority 1: Critical MVP Components (Weeks 1-2)

### Task 1.1: Create DocumentController.java
**Location**: `src/main/java/bor/tools/simplerag/controller/DocumentController.java`
**Duration**: 3 days
**Dependencies**: None

#### Endpoints to Implement

```java
/**
 * REST Controller for document upload and management.
 * Provides endpoints for document ingestion via text, URL, or file upload.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentoService documentoService;

    // ENDPOINT 1: Upload document as text
    @PostMapping("/upload/text")
    @Operation(summary = "Upload document as text",
               description = """
                   Upload a document by providing text content directly.
                   Supports HTML, XHTML, Markdown, and plain text.

                   **Required fields**:
                   - texto: Document text content
                   - libraryId or libraryUuid: Target library

                   **Optional fields**:
                   - titulo: Document title
                   - url: Document source URL (for metadata)
                   - metadados: Additional metadata (JSON object)
                   - tipoConteudo: Content type hint (LIVRO, ARTIGO, MANUAL, etc.)
                   - flagVigente: Mark as current version (default: true)
                   """)
    public ResponseEntity<DocumentoDTO> uploadText(
            @Valid @RequestBody TextUploadRequest request) {
        // Implementation
    }

    // ENDPOINT 2: Upload document from URL
    @PostMapping("/upload/url")
    @Operation(summary = "Load document from URL",
               description = """
                   Load and process a document from a URL.
                   Automatically detects format and converts to Markdown.

                   **Supported formats**: PDF, DOCX, XLSX, PPTX, HTML, TXT, MD

                   **Required fields**:
                   - url: Document URL
                   - libraryId or libraryUuid: Target library
                   """)
    public ResponseEntity<DocumentoDTO> uploadFromUrl(
            @Valid @RequestBody UrlUploadRequest request) {
        // Implementation
    }

    // ENDPOINT 3: Upload document as multipart file
    @PostMapping("/upload/file")
    @Operation(summary = "Upload document file",
               description = """
                   Upload a document file (multipart/form-data).
                   Automatically detects format and converts to Markdown.

                   **Supported formats**: PDF, DOCX, XLSX, PPTX, HTML, TXT, MD
                   **Max file size**: 50MB
                   """)
    public ResponseEntity<DocumentoDTO> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Integer libraryId,
            @RequestParam(required = false) UUID libraryUuid,
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) String tipoConteudo,
            @RequestParam(required = false, defaultValue = "{}") String metadados) {
        // Implementation
    }

    // ENDPOINT 4: Trigger document processing
    @PostMapping("/{documentId}/process")
    @Operation(summary = "Process document asynchronously",
               description = """
                   Triggers full document processing pipeline:
                   1. Split into chapters
                   2. Generate embeddings
                   3. Generate Q&A (optional)
                   4. Generate summaries (optional)

                   Returns immediately with processing job ID.
                   Use GET /api/v1/documents/{documentId}/status to check progress.
                   """)
    public ResponseEntity<ProcessingJobResponse> processDocument(
            @PathVariable Integer documentId,
            @RequestParam(required = false, defaultValue = "false") boolean generateQA,
            @RequestParam(required = false, defaultValue = "false") boolean generateSummary) {
        // Implementation
    }

    // ENDPOINT 5: Check processing status
    @GetMapping("/{documentId}/status")
    @Operation(summary = "Get document processing status")
    public ResponseEntity<ProcessingStatusResponse> getProcessingStatus(
            @PathVariable Integer documentId) {
        // Implementation
    }

    // ENDPOINT 6: Get document by ID
    @GetMapping("/{documentId}")
    @Operation(summary = "Get document details")
    public ResponseEntity<DocumentoDTO> getDocument(@PathVariable Integer documentId) {
        // Implementation
    }

    // ENDPOINT 7: List documents in library
    @GetMapping("/library/{libraryId}")
    @Operation(summary = "List documents in library")
    public ResponseEntity<Page<DocumentoDTO>> listDocuments(
            @PathVariable Integer libraryId,
            @RequestParam(required = false, defaultValue = "true") Boolean apenasVigentes,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        // Implementation
    }
}
```

#### Request DTOs

**File**: `src/main/java/bor/tools/simplerag/dto/TextUploadRequest.java`

```java
public class TextUploadRequest {
    @NotBlank(message = "Texto não pode ser vazio")
    @Size(min = 100, message = "Texto deve ter no mínimo 100 caracteres")
    private String texto;

    private Integer libraryId;
    private UUID libraryUuid;

    @Size(max = 500)
    private String titulo;

    private String url; // Metadata: document source

    private Map<String, Object> metadados = new HashMap<>();

    private TipoConteudo tipoConteudo;

    private Boolean flagVigente = true;

    // Validation method
    @AssertTrue(message = "Deve fornecer libraryId ou libraryUuid")
    public boolean isLibraryProvided() {
        return libraryId != null || libraryUuid != null;
    }
}
```

**File**: `src/main/java/bor/tools/simplerag/dto/UrlUploadRequest.java`

```java
public class UrlUploadRequest {
    @NotBlank(message = "URL não pode ser vazia")
    @Pattern(regexp = "^https?://.*", message = "URL deve começar com http:// ou https://")
    private String url;

    private Integer libraryId;
    private UUID libraryUuid;

    @Size(max = 500)
    private String titulo;

    private Map<String, Object> metadados = new HashMap<>();

    private TipoConteudo tipoConteudo;

    private Boolean flagVigente = true;

    @AssertTrue(message = "Deve fornecer libraryId ou libraryUuid")
    public boolean isLibraryProvided() {
        return libraryId != null || libraryUuid != null;
    }
}
```

**File**: `src/main/java/bor/tools/simplerag/dto/ProcessingJobResponse.java`

```java
public class ProcessingJobResponse {
    private Integer documentId;
    private UUID jobId;
    private String status; // QUEUED, PROCESSING, COMPLETED, FAILED
    private LocalDateTime startedAt;
    private String message;
}
```

**File**: `src/main/java/bor/tools/simplerag/dto/ProcessingStatusResponse.java`

```java
public class ProcessingStatusResponse {
    private Integer documentId;
    private UUID jobId;
    private String status; // QUEUED, PROCESSING, COMPLETED, FAILED
    private Integer totalChapters;
    private Integer processedChapters;
    private Integer totalEmbeddings;
    private Integer generatedEmbeddings;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String errorMessage;
    private Map<String, Object> statistics;
}
```

#### Validation Rules

1. **Text Upload**:
   - Minimum 100 characters
   - Must provide library reference (ID or UUID)
   - Optional: titulo, metadados, tipoConteudo

2. **URL Upload**:
   - Valid HTTP/HTTPS URL
   - Must provide library reference
   - URL must be accessible (validation done async)

3. **File Upload**:
   - Max size: 50MB
   - Allowed formats: PDF, DOCX, XLSX, PPTX, HTML, TXT, MD
   - Must provide library reference

#### Error Handling

```java
@ExceptionHandler(InvalidDocumentException.class)
public ResponseEntity<ErrorResponse> handleInvalidDocument(InvalidDocumentException ex) {
    return ResponseEntity.badRequest().body(new ErrorResponse(
        "INVALID_DOCUMENT",
        ex.getMessage(),
        LocalDateTime.now()
    ));
}

@ExceptionHandler(DocumentProcessingException.class)
public ResponseEntity<ErrorResponse> handleProcessingError(DocumentProcessingException ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(
        "PROCESSING_FAILED",
        ex.getMessage(),
        LocalDateTime.now()
    ));
}
```

---

### Task 1.2: Create DocumentoService.java
**Location**: `src/main/java/bor/tools/simplerag/service/DocumentoService.java`
**Duration**: 4 days
**Dependencies**: Task 1.1, existing repositories

#### Service Interface

```java
/**
 * Service for document management and processing orchestration.
 * Coordinates the complete document ingestion workflow:
 * 1. Document validation and conversion
 * 2. Splitting into chapters
 * 3. Embedding generation (async)
 * 4. Persistence coordination
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DocumentoService {

    // Dependencies
    private final DocumentoRepository documentoRepository;
    private final ChapterRepository chapterRepository;
    private final DocumentEmbeddingRepository embeddingRepository;
    private final LibraryService libraryService;
    private final TikaDocumentConverter documentConverter;
    private final DocumentRouter documentRouter;
    private final AsyncSplitterService asyncSplitterService;
    private final EmbeddingProcessorImpl embeddingProcessor;

    // Processing job tracking
    private final Map<UUID, ProcessingJob> activeJobs = new ConcurrentHashMap<>();

    /**
     * Upload document from text content
     */
    public DocumentoDTO uploadFromText(TextUploadRequest request) {
        log.debug("Uploading document from text: {} characters", request.getTexto().length());

        // 1. Validate library
        LibraryDTO library = validateAndGetLibrary(request.getLibraryId(), request.getLibraryUuid());

        // 2. Detect format and convert if needed
        String markdownContent = convertToMarkdown(request.getTexto());

        // 3. Create Documento entity
        Documento documento = buildDocumento(request, library, markdownContent);

        // 4. Persist documento
        Documento saved = documentoRepository.save(documento);

        log.debug("Document saved with ID: {}", saved.getId());
        return DocumentoDTO.from(saved);
    }

    /**
     * Upload document from URL
     */
    public DocumentoDTO uploadFromUrl(UrlUploadRequest request) throws Exception {
        log.debug("Loading document from URL: {}", request.getUrl());

        // 1. Validate library
        LibraryDTO library = validateAndGetLibrary(request.getLibraryId(), request.getLibraryUuid());

        // 2. Fetch document from URL
        byte[] content = fetchFromUrl(request.getUrl());

        // 3. Detect format
        String format = documentConverter.detectFormat(content);
        log.debug("Detected format: {}", format);

        // 4. Convert to Markdown
        String markdownContent = documentConverter.convertToMarkdown(content, format);

        // 5. Create Documento entity
        Documento documento = buildDocumentoFromUrl(request, library, markdownContent);

        // 6. Persist documento
        Documento saved = documentoRepository.save(documento);

        log.debug("Document from URL saved with ID: {}", saved.getId());
        return DocumentoDTO.from(saved);
    }

    /**
     * Upload document from multipart file
     */
    public DocumentoDTO uploadFromFile(MultipartFile file, Integer libraryId, UUID libraryUuid,
                                       String titulo, String tipoConteudo, String metadadosJson)
                                       throws Exception {
        log.debug("Uploading file: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        // 1. Validate file size
        if (file.getSize() > 50 * 1024 * 1024) { // 50MB
            throw new InvalidDocumentException("Arquivo muito grande (máximo 50MB)");
        }

        // 2. Validate library
        LibraryDTO library = validateAndGetLibrary(libraryId, libraryUuid);

        // 3. Read file content
        byte[] content = file.getBytes();

        // 4. Detect format
        String format = documentConverter.detectFormat(content);
        log.debug("Detected format: {}", format);

        // 5. Convert to Markdown
        String markdownContent = documentConverter.convertToMarkdown(content, format);

        // 6. Create Documento entity
        Documento documento = buildDocumentoFromFile(file, library, markdownContent,
                                                     titulo, tipoConteudo, metadadosJson);

        // 7. Persist documento
        Documento saved = documentoRepository.save(documento);

        log.debug("Document from file saved with ID: {}", saved.getId());
        return DocumentoDTO.from(saved);
    }

    /**
     * Process document asynchronously - FULL PIPELINE
     *
     * This is the main orchestration method that implements the workflow
     * from Fluxo_carga_documents.md steps (d) through (g).
     */
    public ProcessingJobResponse processDocumentAsync(Integer documentId,
                                                      boolean generateQA,
                                                      boolean generateSummary) {
        log.debug("Starting async processing for document ID: {}", documentId);

        // 1. Load document
        Documento documento = documentoRepository.findById(documentId)
            .orElseThrow(() -> new EntityNotFoundException("Documento não encontrado: " + documentId));

        // 2. Load library
        LibraryDTO library = libraryService.findById(documento.getBibliotecaId())
            .map(LibraryDTO::from)
            .orElseThrow(() -> new EntityNotFoundException("Biblioteca não encontrada"));

        // 3. Create processing job
        UUID jobId = UUID.randomUUID();
        ProcessingJob job = new ProcessingJob(jobId, documentId);
        activeJobs.put(jobId, job);

        // 4. Execute async processing
        CompletableFuture<ProcessingResult> future = asyncSplitterService.fullProcessingAsync(
            DocumentoDTO.from(documento),
            library,
            documento.getTipoConteudo(),
            generateQA,
            generateSummary
        );

        // 5. Handle completion
        future.whenComplete((result, error) -> {
            if (error != null) {
                log.error("Processing failed for document {}: {}", documentId, error.getMessage(), error);
                job.setStatus(ProcessingStatus.FAILED);
                job.setErrorMessage(error.getMessage());
            } else {
                log.debug("Processing completed for document {}", documentId);
                persistProcessingResult(result, documento);
                job.setStatus(ProcessingStatus.COMPLETED);
            }
            job.setCompletedAt(LocalDateTime.now());
        });

        // 6. Return job response
        return new ProcessingJobResponse(
            documentId,
            jobId,
            ProcessingStatus.PROCESSING.name(),
            LocalDateTime.now(),
            "Processamento iniciado"
        );
    }

    /**
     * Persist processing results (chapters + embeddings)
     * Implements Fluxo_carga_documents.md steps (e) and (g)
     */
    @Transactional
    protected void persistProcessingResult(ProcessingResult result, Documento documento) {
        log.debug("Persisting processing result for document: {}", documento.getId());

        // 1. Save chapters
        List<Chapter> chapters = result.getCapitulos().stream()
            .map(dto -> toEntity(dto, documento))
            .collect(Collectors.toList());

        List<Chapter> savedChapters = chapterRepository.saveAll(chapters);
        log.debug("Saved {} chapters", savedChapters.size());

        // 2. Map chapter DTOs to saved entities for embedding association
        Map<String, Integer> chapterIdMap = new HashMap<>();
        for (int i = 0; i < result.getCapitulos().size(); i++) {
            ChapterDTO dto = result.getCapitulos().get(i);
            Chapter saved = savedChapters.get(i);
            chapterIdMap.put(dto.getTitulo(), saved.getId());
        }

        // 3. Save embeddings
        List<DocumentEmbedding> embeddings = result.getAllEmbeddings().stream()
            .map(dto -> toEntity(dto, documento, chapterIdMap))
            .collect(Collectors.toList());

        List<DocumentEmbedding> savedEmbeddings = embeddingRepository.saveAll(embeddings);
        log.debug("Saved {} embeddings", savedEmbeddings.size());

        // 4. Update document status
        documento.setProcessado(true);
        documento.setDataProcessamento(LocalDateTime.now());
        documentoRepository.save(documento);

        log.debug("Document {} fully processed and persisted", documento.getId());
    }

    /**
     * Get processing status
     */
    public ProcessingStatusResponse getProcessingStatus(Integer documentId) {
        // Find job by document ID
        ProcessingJob job = activeJobs.values().stream()
            .filter(j -> j.getDocumentId().equals(documentId))
            .findFirst()
            .orElse(null);

        if (job == null) {
            // Check if document is already processed
            Documento doc = documentoRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Documento não encontrado"));

            if (doc.getProcessado()) {
                return buildCompletedStatus(doc);
            } else {
                throw new EntityNotFoundException("Nenhum processamento encontrado para documento: " + documentId);
            }
        }

        return buildStatusResponse(job);
    }

    // Helper methods

    private LibraryDTO validateAndGetLibrary(Integer libraryId, UUID libraryUuid) {
        if (libraryId != null) {
            return libraryService.findById(libraryId)
                .map(LibraryDTO::from)
                .orElseThrow(() -> new EntityNotFoundException("Biblioteca não encontrada: " + libraryId));
        } else if (libraryUuid != null) {
            return libraryService.findByUuid(libraryUuid)
                .orElseThrow(() -> new EntityNotFoundException("Biblioteca não encontrada: " + libraryUuid));
        } else {
            throw new IllegalArgumentException("Deve fornecer libraryId ou libraryUuid");
        }
    }

    private String convertToMarkdown(String content) {
        try {
            return documentConverter.convertToMarkdown(content, null);
        } catch (Exception e) {
            log.warn("Failed to convert content to markdown, using as-is: {}", e.getMessage());
            return content;
        }
    }

    private byte[] fetchFromUrl(String url) throws IOException {
        // Use OkHttpProvider
        OkHttpClient client = OkHttpProvider.getClient();
        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Falha ao carregar URL: " + response.code());
            }
            return response.body().bytes();
        }
    }

    private Documento buildDocumento(TextUploadRequest request, LibraryDTO library,
                                     String markdownContent) {
        Documento doc = new Documento();
        doc.setBibliotecaId(library.getId());
        doc.setTitulo(request.getTitulo() != null ? request.getTitulo() : "Documento sem título");
        doc.setTexto(markdownContent);
        doc.setFlagVigente(request.getFlagVigente());
        doc.setTipoConteudo(request.getTipoConteudo() != null ? request.getTipoConteudo() : TipoConteudo.GENERICO);
        doc.setDataPublicacao(LocalDate.now());
        doc.setProcessado(false);

        // Metadata
        if (request.getMetadados() != null && !request.getMetadados().isEmpty()) {
            doc.setMetadados(request.getMetadados());
        }
        if (request.getUrl() != null) {
            doc.getMetadados().put("source_url", request.getUrl());
        }

        return doc;
    }

    private Chapter toEntity(ChapterDTO dto, Documento documento) {
        Chapter chapter = new Chapter();
        chapter.setDocumentoId(documento.getId());
        chapter.setTitulo(dto.getTitulo());
        chapter.setConteudo(dto.getConteudo());
        chapter.setOrdemDoc(dto.getOrdemDoc());
        chapter.setTokensTotal(dto.getTokensTotal());
        chapter.setMetadados(dto.getMetadados());
        return chapter;
    }

    private DocumentEmbedding toEntity(DocumentEmbeddingDTO dto, Documento documento,
                                       Map<String, Integer> chapterIdMap) {
        DocumentEmbedding emb = new DocumentEmbedding();
        emb.setBibliotecaId(documento.getBibliotecaId());
        emb.setDocumentoId(documento.getId());

        // Try to find chapter ID from metadata
        String chapterTitle = (String) dto.getMetadados().get("capitulo_titulo");
        if (chapterTitle != null && chapterIdMap.containsKey(chapterTitle)) {
            emb.setCapituloId(chapterIdMap.get(chapterTitle));
        }

        emb.setTrechoTexto(dto.getTrechoTexto());
        emb.setEmbeddingVector(dto.getEmbeddingVector());
        emb.setTipoEmbedding(dto.getTipoEmbedding());
        emb.setMetadados(dto.getMetadados());

        return emb;
    }

    // Inner classes for job tracking

    private static class ProcessingJob {
        private final UUID jobId;
        private final Integer documentId;
        private ProcessingStatus status;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private String errorMessage;

        public ProcessingJob(UUID jobId, Integer documentId) {
            this.jobId = jobId;
            this.documentId = documentId;
            this.status = ProcessingStatus.PROCESSING;
            this.startedAt = LocalDateTime.now();
        }

        // Getters and setters
    }

    private enum ProcessingStatus {
        QUEUED, PROCESSING, COMPLETED, FAILED
    }
}
```

#### Repository Requirements

**New Repository**: `ChapterRepository.java`

```java
public interface ChapterRepository extends JpaRepository<Chapter, Integer> {
    List<Chapter> findByDocumentoIdOrderByOrdemDoc(Integer documentoId);
    long countByDocumentoId(Integer documentoId);
}
```

**New Repository**: `DocumentEmbeddingRepository.java`

```java
public interface DocumentEmbeddingRepository extends JpaRepository<DocumentEmbedding, Integer> {
    List<DocumentEmbedding> findByDocumentoId(Integer documentoId);
    List<DocumentEmbedding> findByCapituloId(Integer capituloId);
    long countByDocumentoId(Integer documentoId);
    long countByBibliotecaId(Integer bibliotecaId);
}
```

**Existing**: `DocumentoRepository.java` (may need enhancements)

---

### Task 1.3: Implement TikaDocumentConverter
**Location**: `src/main/java/bor/tools/utils/TikaDocumentConverter.java`
**Duration**: 2 days
**Dependencies**: Task 1.2

#### Implementation

```java
/**
 * Apache Tika-based implementation of DocumentConverter interface.
 * Provides standard API for document format detection and conversion.
 *
 * Wraps existing RAGConverter functionality with configuration support.
 */
@Service
public class TikaDocumentConverter implements DocumentConverter {

    private static final Logger logger = LoggerFactory.getLogger(TikaDocumentConverter.class);

    private Properties config;
    private boolean removeStrikethrough = true;
    private int maxStringLength = 1024 * 200; // 200KB

    /**
     * Constructor - loads default configuration
     */
    public TikaDocumentConverter() {
        try {
            loadConfiguration(DEFAULT_CONFIG_FILE);
        } catch (Exception e) {
            logger.warn("Failed to load default configuration, using defaults: {}", e.getMessage());
            config = new Properties();
        }
    }

    @Override
    public void loadConfiguration(String configFilePath) throws Exception {
        config = new Properties();

        // Try classpath first
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(configFilePath)) {
            if (is != null) {
                config.load(is);
                logger.debug("Loaded configuration from classpath: {}", configFilePath);
            }
        }

        // Try file system if not found in classpath
        if (config.isEmpty()) {
            try (FileInputStream fis = new FileInputStream(configFilePath)) {
                config.load(fis);
                logger.debug("Loaded configuration from file system: {}", configFilePath);
            }
        }

        // Apply configuration
        applyConfiguration();
    }

    private void applyConfiguration() {
        removeStrikethrough = Boolean.parseBoolean(
            config.getProperty("converter.remove.strikethrough", "true"));

        maxStringLength = Integer.parseInt(
            config.getProperty("converter.max.string.length", "204800"));

        RAGConverter.removerTachado = removeStrikethrough;
        RAGConverter.MAX_STRING_LENGTH = maxStringLength;

        logger.debug("Applied configuration: removeStrikethrough={}, maxStringLength={}",
                    removeStrikethrough, maxStringLength);
    }

    @Override
    public String convertToMarkdown(String inputContent, String inputFormat) throws Exception {
        if (inputContent == null || inputContent.trim().isEmpty()) {
            throw new IllegalArgumentException("Input content cannot be null or empty");
        }

        logger.debug("Converting content to markdown: {} characters, format: {}",
                    inputContent.length(), inputFormat);

        // Use RAGConverter
        return RAGConverter.convertToMarkdown(inputContent);
    }

    @Override
    public String convertToMarkdown(URI contentSource, String inputFormat) throws Exception {
        if (contentSource == null) {
            throw new IllegalArgumentException("Content source URI cannot be null");
        }

        logger.debug("Converting content from URI to markdown: {}", contentSource);

        // Fetch content from URI
        byte[] content = fetchFromUri(contentSource);

        // Convert using byte array method
        return convertToMarkdown(content, inputFormat);
    }

    @Override
    public String convertToMarkdown(byte[] content, String inputFormat) throws Exception {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Content byte array cannot be null or empty");
        }

        logger.debug("Converting byte array to markdown: {} bytes, format: {}",
                    content.length, inputFormat);

        // Detect format if not provided
        if (inputFormat == null || inputFormat.trim().isEmpty()) {
            inputFormat = detectFormat(content);
            logger.debug("Auto-detected format: {}", inputFormat);
        }

        // Use RAGConverter
        return RAGConverter.convertToMarkdown(content);
    }

    @Override
    public String detectFormat(URI contentSource) throws Exception {
        if (contentSource == null) {
            throw new IllegalArgumentException("Content source URI cannot be null");
        }

        logger.debug("Detecting format from URI: {}", contentSource);

        // Fetch first 256 bytes for format detection
        byte[] sample = fetchSampleFromUri(contentSource, 256);

        return detectFormat(sample);
    }

    @Override
    public String detectFormat(byte[] contentSample) throws Exception {
        if (contentSample == null || contentSample.length == 0) {
            throw new IllegalArgumentException("Content sample cannot be null or empty");
        }

        logger.debug("Detecting format from byte array: {} bytes", contentSample.length);

        // Use RAGConverter's Tika-based detection
        RAGConverter.MimeType mimeType = RAGConverter.detectMimeTypeTika(contentSample);

        String format = mapMimeTypeToFormat(mimeType);
        logger.debug("Detected format: {} (MIME: {})", format, mimeType);

        return format;
    }

    // Helper methods

    private byte[] fetchFromUri(URI uri) throws IOException {
        if ("file".equals(uri.getScheme())) {
            return Files.readAllBytes(Paths.get(uri));
        } else if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
            return fetchFromUrl(uri.toString());
        } else {
            throw new IllegalArgumentException("Unsupported URI scheme: " + uri.getScheme());
        }
    }

    private byte[] fetchSampleFromUri(URI uri, int sampleSize) throws IOException {
        byte[] full = fetchFromUri(uri);
        int size = Math.min(full.length, sampleSize);
        byte[] sample = new byte[size];
        System.arraycopy(full, 0, sample, 0, size);
        return sample;
    }

    private byte[] fetchFromUrl(String url) throws IOException {
        OkHttpClient client = OkHttpProvider.getClient();
        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch URL: " + response.code());
            }
            return response.body().bytes();
        }
    }

    private String mapMimeTypeToFormat(RAGConverter.MimeType mimeType) {
        return switch (mimeType) {
            case MIME_PDF -> "pdf";
            case MIME_MS_WORD, MIME_MS_DOCX, MIME_MS_DOC -> "docx";
            case MIME_MS_EXCEL, MIME_MS_XLS, MIME_MS_XLSX -> "xlsx";
            case MIME_MS_PPTX, MIME_MS_PPT, MIME_MS_PPT_LEGACY -> "pptx";
            case MIME_HTML, MIME_XHTML -> "html";
            case MIME_TEXT -> "txt";
            case MIME_MARKDOWN -> "md";
            case MIME_RTF -> "rtf";
            default -> "unknown";
        };
    }

    /**
     * Get converter statistics
     */
    public Map<String, Object> getConverterStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("remove_strikethrough", removeStrikethrough);
        stats.put("max_string_length", maxStringLength);
        stats.put("config_loaded", config != null && !config.isEmpty());
        return stats;
    }
}
```

#### Configuration File

**File**: `src/main/resources/document-converter.properties`

```properties
# Document Converter Configuration
# Apache Tika-based implementation

# General settings
converter.remove.strikethrough=true
converter.max.string.length=204800

# Timeout settings (milliseconds)
converter.url.fetch.timeout=30000

# Fallback strategies
converter.fallback.enabled=true
converter.fallback.return.plain.text=true

# Image handling
converter.images.embed.as.base64=false
converter.images.keep.external.links=true

# Supported formats
converter.supported.formats=pdf,docx,doc,xlsx,xls,pptx,ppt,html,xhtml,txt,md,rtf

# Format-specific settings
converter.pdf.extract.images=false
converter.pdf.preserve.structure=true

converter.office.extract.comments=false
converter.office.extract.hidden.text=false
```

---

## Priority 2: Enhanced Functionality (Weeks 3-4)

### Task 2.1: Implement carregaDocumento in AbstractSplitter
**Location**: `src/main/java/bor/tools/splitter/AbstractSplitter.java`
**Duration**: 2 days
**Dependencies**: Task 1.3

#### Implementation

```java
/**
 * Loads a document from a URL.
 * Implements DocumentSplitter interface method.
 *
 * @param url Document URL
 * @param docStub Document's stub with metadata (can be null)
 * @return Loaded document with content
 */
@Override
public DocumentoDTO carregaDocumento(URL url, DocumentoDTO docStub) throws Exception {
    logger.debug("Loading document from URL: {}", url);

    // 1. Fetch content from URL
    byte[] content = fetchFromUrl(url);

    // 2. Detect format
    TikaDocumentConverter converter = new TikaDocumentConverter();
    String format = converter.detectFormat(content);
    logger.debug("Detected format: {}", format);

    // 3. Convert to Markdown
    String markdownContent = converter.convertToMarkdown(content, format);

    // 4. Create or update DocumentoDTO
    DocumentoDTO documento = docStub != null ? docStub : new DocumentoDTO();
    documento.setTexto(markdownContent);

    // Set metadata from URL
    if (documento.getMetadados() == null) {
        documento.initializeMetadata();
    }
    documento.getMetadados().put("source_url", url.toString());
    documento.getMetadados().put("detected_format", format);
    documento.getMetadados().put("loaded_at", LocalDateTime.now().toString());

    // Set title from URL if not provided
    if (documento.getTitulo() == null || documento.getTitulo().isEmpty()) {
        String fileName = extractFileNameFromUrl(url);
        documento.setTitulo(fileName);
    }

    logger.debug("Document loaded successfully from URL: {}", url);
    return documento;
}

/**
 * Loads a document from a string path (file path or URL).
 *
 * @param path Document path
 * @param docStub Document's stub with metadata (can be null)
 * @return Loaded document with content
 */
@Override
public DocumentoDTO carregaDocumento(String path, DocumentoDTO docStub) throws Exception {
    logger.debug("Loading document from path: {}", path);

    // Check if path is a URL
    if (path.startsWith("http://") || path.startsWith("https://")) {
        return carregaDocumento(new URL(path), docStub);
    }

    // Treat as file path
    Path filePath = Paths.get(path);
    if (!Files.exists(filePath)) {
        throw new FileNotFoundException("File not found: " + path);
    }

    // Read file content
    byte[] content = Files.readAllBytes(filePath);

    // Detect format
    TikaDocumentConverter converter = new TikaDocumentConverter();
    String format = converter.detectFormat(content);
    logger.debug("Detected format: {}", format);

    // Convert to Markdown
    String markdownContent = converter.convertToMarkdown(content, format);

    // Create or update DocumentoDTO
    DocumentoDTO documento = docStub != null ? docStub : new DocumentoDTO();
    documento.setTexto(markdownContent);

    // Set metadata from file
    if (documento.getMetadados() == null) {
        documento.initializeMetadata();
    }
    documento.getMetadados().put("source_file", path);
    documento.getMetadados().put("detected_format", format);
    documento.getMetadados().put("file_size", String.valueOf(content.length));
    documento.getMetadados().put("loaded_at", LocalDateTime.now().toString());

    // Set title from file name if not provided
    if (documento.getTitulo() == null || documento.getTitulo().isEmpty()) {
        String fileName = filePath.getFileName().toString();
        documento.setTitulo(removeExtension(fileName));
    }

    logger.debug("Document loaded successfully from file: {}", path);
    return documento;
}

// Helper methods

protected byte[] fetchFromUrl(URL url) throws IOException {
    OkHttpClient client = OkHttpProvider.getClient();
    Request request = new Request.Builder().url(url).build();

    try (Response response = client.newCall(request).execute()) {
        if (!response.isSuccessful()) {
            throw new IOException("Failed to fetch URL: " + response.code() + " " + url);
        }
        return response.body().bytes();
    }
}

protected String extractFileNameFromUrl(URL url) {
    String path = url.getPath();
    int lastSlash = path.lastIndexOf('/');
    String fileName = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    return removeExtension(fileName);
}

protected String removeExtension(String fileName) {
    int lastDot = fileName.lastIndexOf('.');
    return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
}
```

---

### Task 2.2: Add Repository Integration to AsyncSplitterService
**Location**: Enhance `src/main/java/bor/tools/splitter/AsyncSplitterService.java`
**Duration**: 2 days
**Dependencies**: Task 1.2

#### Enhancements

```java
/**
 * Enhanced AsyncSplitterService with repository integration.
 * Now persists results directly to database.
 */
@Service
public class AsyncSplitterService {

    // Existing dependencies
    private final SplitterFactory splitterFactory;
    private final EmbeddingProcessorImpl embeddingProcessor;
    private final DocumentSummarizerImpl documentSummarizer;
    private final Executor taskExecutor;

    // NEW: Repository dependencies
    private final ChapterRepository chapterRepository;
    private final DocumentEmbeddingRepository embeddingRepository;
    private final DocumentoRepository documentoRepository;

    /**
     * NEW: Full processing with persistence
     */
    @Async
    @Transactional
    public CompletableFuture<ProcessingResultWithPersistence> fullProcessingWithPersistence(
            DocumentoDTO documento,
            LibraryDTO biblioteca,
            TipoConteudo tipoConteudo,
            boolean includeQA,
            boolean includeSummary) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. Process document (existing logic)
                ProcessingResult result = fullProcessingAsync(
                    documento, biblioteca, tipoConteudo, includeQA, includeSummary
                ).get();

                // 2. Persist chapters
                List<Chapter> chapters = persistChapters(result.getCapitulos(), documento);

                // 3. Persist embeddings
                List<DocumentEmbedding> embeddings = persistEmbeddings(
                    result.getAllEmbeddings(), documento, chapters
                );

                // 4. Update document status
                updateDocumentStatus(documento.getId(), true);

                // 5. Return result with persistence info
                return new ProcessingResultWithPersistence(result, chapters, embeddings);

            } catch (Exception e) {
                logger.error("Failed full processing with persistence: {}", e.getMessage(), e);
                updateDocumentStatus(documento.getId(), false);
                throw new RuntimeException("Processing with persistence failed", e);
            }
        }, taskExecutor);
    }

    @Transactional
    protected List<Chapter> persistChapters(List<ChapterDTO> chapterDTOs, DocumentoDTO documento) {
        logger.debug("Persisting {} chapters for document {}", chapterDTOs.size(), documento.getId());

        List<Chapter> chapters = chapterDTOs.stream()
            .map(dto -> {
                Chapter chapter = new Chapter();
                chapter.setDocumentoId(documento.getId());
                chapter.setTitulo(dto.getTitulo());
                chapter.setConteudo(dto.getConteudo());
                chapter.setOrdemDoc(dto.getOrdemDoc());
                chapter.setTokensTotal(dto.getTokensTotal());
                chapter.setMetadados(dto.getMetadados());
                return chapter;
            })
            .collect(Collectors.toList());

        List<Chapter> saved = chapterRepository.saveAll(chapters);
        logger.debug("Successfully persisted {} chapters", saved.size());
        return saved;
    }

    @Transactional
    protected List<DocumentEmbedding> persistEmbeddings(List<DocumentEmbeddingDTO> embeddingDTOs,
                                                        DocumentoDTO documento,
                                                        List<Chapter> chapters) {
        logger.debug("Persisting {} embeddings for document {}", embeddingDTOs.size(), documento.getId());

        // Create chapter title -> ID mapping
        Map<String, Integer> chapterIdMap = chapters.stream()
            .collect(Collectors.toMap(Chapter::getTitulo, Chapter::getId));

        List<DocumentEmbedding> embeddings = embeddingDTOs.stream()
            .map(dto -> {
                DocumentEmbedding emb = new DocumentEmbedding();
                emb.setBibliotecaId(documento.getLibraryId());
                emb.setDocumentoId(documento.getId());

                // Try to find chapter ID
                String chapterTitle = (String) dto.getMetadados().get("capitulo_titulo");
                if (chapterTitle != null && chapterIdMap.containsKey(chapterTitle)) {
                    emb.setCapituloId(chapterIdMap.get(chapterTitle));
                }

                emb.setTrechoTexto(dto.getTrechoTexto());
                emb.setEmbeddingVector(dto.getEmbeddingVector());
                emb.setTipoEmbedding(dto.getTipoEmbedding());
                emb.setMetadados(dto.getMetadados());

                return emb;
            })
            .collect(Collectors.toList());

        List<DocumentEmbedding> saved = embeddingRepository.saveAll(embeddings);
        logger.debug("Successfully persisted {} embeddings", saved.size());
        return saved;
    }

    @Transactional
    protected void updateDocumentStatus(Integer documentId, boolean success) {
        documentoRepository.findById(documentId).ifPresent(doc -> {
            doc.setProcessado(success);
            doc.setDataProcessamento(LocalDateTime.now());
            documentoRepository.save(doc);
        });
    }

    /**
     * Result wrapper with persistence info
     */
    public static class ProcessingResultWithPersistence extends ProcessingResult {
        private final List<Chapter> persistedChapters;
        private final List<DocumentEmbedding> persistedEmbeddings;

        public ProcessingResultWithPersistence(ProcessingResult result,
                                              List<Chapter> chapters,
                                              List<DocumentEmbedding> embeddings) {
            this.setDocumento(result.getDocumento());
            this.setBiblioteca(result.getBiblioteca());
            this.setCapitulos(result.getCapitulos());
            this.setAllEmbeddings(result.getAllEmbeddings());
            this.persistedChapters = chapters;
            this.persistedEmbeddings = embeddings;
        }

        public List<Chapter> getPersistedChapters() { return persistedChapters; }
        public List<DocumentEmbedding> getPersistedEmbeddings() { return persistedEmbeddings; }
    }
}
```

---

### Task 2.3: Create Document Processing Monitoring Endpoint
**Location**: Add to DocumentController.java
**Duration**: 1 day
**Dependencies**: Task 1.1, Task 1.2

#### Additional Endpoints

```java
/**
 * Get detailed processing statistics for a document
 */
@GetMapping("/{documentId}/statistics")
@Operation(summary = "Get document processing statistics")
public ResponseEntity<DocumentStatisticsResponse> getDocumentStatistics(
        @PathVariable Integer documentId) {

    DocumentStatisticsResponse stats = documentoService.getDocumentStatistics(documentId);
    return ResponseEntity.ok(stats);
}

/**
 * List all processing jobs
 */
@GetMapping("/jobs")
@Operation(summary = "List all processing jobs")
public ResponseEntity<Page<ProcessingJobSummary>> listProcessingJobs(
        @RequestParam(required = false) String status,
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "20") int size) {

    Page<ProcessingJobSummary> jobs = documentoService.listProcessingJobs(status, page, size);
    return ResponseEntity.ok(jobs);
}

/**
 * Cancel a processing job
 */
@DeleteMapping("/jobs/{jobId}")
@Operation(summary = "Cancel processing job")
public ResponseEntity<Void> cancelProcessingJob(@PathVariable UUID jobId) {
    documentoService.cancelProcessingJob(jobId);
    return ResponseEntity.noContent().build();
}
```

---

## Testing Strategy

### Unit Tests (Week 2-3)

#### Test Coverage Targets
- **Controllers**: 80% coverage
- **Services**: 90% coverage
- **Converters**: 85% coverage

#### Key Test Classes

1. **DocumentControllerTest.java**

```java
@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Test
    void uploadText_ValidRequest_ReturnsCreated() {
        // Test successful text upload
    }

    @Test
    void uploadText_InvalidLibrary_ReturnsBadRequest() {
        // Test validation
    }

    @Test
    void uploadFromUrl_ValidUrl_ReturnsCreated() {
        // Test URL upload
    }

    @Test
    void uploadFile_ValidFile_ReturnsCreated() {
        // Test file upload
    }

    @Test
    void processDocument_ValidId_ReturnsJobResponse() {
        // Test async processing trigger
    }
}
```

2. **DocumentoServiceTest.java**

```java
@SpringBootTest
@Transactional
class DocumentoServiceTest {

    @Test
    void uploadFromText_ValidRequest_SavesDocument() {
        // Test service logic
    }

    @Test
    void processDocumentAsync_ValidDocument_CompletesProcessing() {
        // Test full pipeline
    }

    @Test
    void persistProcessingResult_ValidResult_SavesAllEntities() {
        // Test persistence
    }
}
```

3. **TikaDocumentConverterTest.java**

```java
class TikaDocumentConverterTest {

    @Test
    void convertToMarkdown_PdfContent_ReturnsMarkdown() {
        // Test PDF conversion
    }

    @Test
    void detectFormat_PdfSample_ReturnsPdf() {
        // Test format detection
    }

    @Test
    void loadConfiguration_ValidFile_LoadsSuccessfully() {
        // Test configuration loading
    }
}
```

### Integration Tests (Week 3-4)

#### Test Scenarios

1. **End-to-End Document Upload Flow**

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
class DocumentUploadIntegrationTest {

    @Test
    void fullDocumentUploadFlow_TextUpload_ProcessesSuccessfully() {
        // 1. Upload document via REST API
        // 2. Trigger processing
        // 3. Wait for completion
        // 4. Verify chapters created
        // 5. Verify embeddings generated
        // 6. Verify searchable
    }
}
```

2. **URL Document Loading**

```java
@Test
void fullDocumentUploadFlow_UrlUpload_ProcessesSuccessfully() {
    // Test with mock server
}
```

3. **File Upload with Format Conversion**

```java
@Test
void fullDocumentUploadFlow_PdfUpload_ConvertsAndProcesses() {
    // Test PDF conversion and processing
}
```

### Performance Tests (Week 4)

#### Benchmarks
- Document upload: < 500ms (text), < 2s (file conversion)
- Processing trigger: < 100ms
- Status check: < 50ms
- Full processing: Depends on document size (target: 10s for 100 pages)

---

## Configuration Files

### application.properties Additions

```properties
# Document Upload Configuration
document.upload.max-file-size=52428800
document.upload.max-request-size=52428800
document.upload.supported-formats=pdf,docx,doc,xlsx,xls,pptx,ppt,html,xhtml,txt,md,rtf

# Document Processing
document.processing.async.enabled=true
document.processing.async.pool.size=5
document.processing.async.queue.capacity=100
document.processing.timeout.seconds=300

# Document Conversion
document.converter.implementation=tika
document.converter.config.file=document-converter.properties
```

---

## Documentation Updates

### Update Fluxo_carga_documents.md

Add sections:
1. **Current Implementation Status** (checklist)
2. **API Endpoints Reference** (with examples)
3. **Error Handling** (error codes and recovery)
4. **Configuration Guide** (properties files)
5. **Sequence Diagrams** (visual workflow)

### Create API_DOCUMENT_LOADING.md

New documentation file with:
- Complete API reference
- Request/response examples
- Error code reference
- Client integration guide
- Performance tuning guide

---

## Risk Management

### Identified Risks

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Large file processing timeout | High | Medium | Implement streaming, chunked processing |
| Async processing failures | Medium | Medium | Add retry logic, dead letter queue |
| Database transaction deadlocks | Medium | Low | Optimize transaction boundaries |
| Format detection failures | Medium | Medium | Fallback to manual format specification |
| URL fetching timeouts | Low | Medium | Configure timeouts, add retries |

---

## Deployment Checklist

### Before Deployment
- [ ] All unit tests passing (>80% coverage)
- [ ] All integration tests passing
- [ ] Performance benchmarks met
- [ ] Security audit completed
- [ ] API documentation updated
- [ ] Database migration scripts tested
- [ ] Configuration files reviewed

### Deployment Steps
1. Run database migrations
2. Update application.properties
3. Deploy new service version
4. Verify health endpoints
5. Run smoke tests
6. Monitor logs for errors

---

## Success Criteria

### Functional
- ✅ All 3 upload methods working (text, URL, file)
- ✅ Format auto-detection working for all supported formats
- ✅ Async processing completes successfully
- ✅ All entities persisted correctly
- ✅ Processing status tracking functional

### Non-Functional
- ✅ API response time < 500ms (upload endpoints)
- ✅ Processing throughput > 10 documents/minute
- ✅ Error rate < 1%
- ✅ Test coverage > 80%
- ✅ Zero data loss during processing

---

## Timeline Summary

| Week | Focus | Deliverables |
|------|-------|--------------|
| Week 1 | Priority 1 - Part 1 | DocumentController, Request DTOs |
| Week 2 | Priority 1 - Part 2 | DocumentoService, TikaDocumentConverter |
| Week 3 | Priority 2 + Testing | AbstractSplitter enhancements, Unit tests |
| Week 4 | Integration + Polish | Integration tests, Documentation, Performance tuning |

---

## Next Steps After Implementation

1. **Phase 2: Advanced Features**
   - Batch document upload
   - Document versioning UI
   - Processing queue management dashboard
   - Retry failed processing jobs

2. **Phase 3: Optimization**
   - Caching layer for converted documents
   - Parallel chapter processing
   - Incremental embedding updates
   - Document diff for versions

3. **Phase 4: Enterprise Features**
   - Multi-tenant support
   - Role-based document access
   - Audit logging
   - Document lifecycle management

---

**Document prepared by**: Claude Code
**Last updated**: 2025-10-13
**Status**: Ready for Implementation Approval
