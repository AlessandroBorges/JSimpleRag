package bor.tools.simplerag.service;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocumentEmbeddingDTO;
import bor.tools.simplerag.dto.DocumentoDTO;
import bor.tools.simplerag.dto.DocumentoWithAssociationDTO;
import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.entity.Chapter;
import bor.tools.simplerag.entity.DocChunk;
import bor.tools.simplerag.entity.Documento;
import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.entity.MetaDoc;
import bor.tools.simplerag.repository.ChapterRepository;
import bor.tools.simplerag.repository.DocChunkJdbcRepository;
import bor.tools.simplerag.repository.DocumentoRepository;
import bor.tools.simplerag.service.embedding.EmbeddingOrchestrator;
import bor.tools.simplerag.service.llm.LLMServiceManager;
import bor.tools.simplerag.service.processing.DocumentProcessingService;
import bor.tools.splitter.DocumentRouter;
import bor.tools.utils.DocumentConverter;
import bor.tools.utils.RagUtils;
import lombok.RequiredArgsConstructor;

/**
 * Service for Documento entity operations.
 *
 * Handles document management, processing, and persistence according to
 * the workflow defined in Fluxo_carga_documents.md:
 *
 * <ol>
 *   <li>(a) Document upload (text, URL, or file)</li>
 *   <li>(b) Format detection</li>
 *   <li>(c) Convert to Markdown using DocumentConverter</li>
 *   <li>(d) Choose appropriate splitter via DocumentRouter</li>
 *   <li>(e) Persist Documento, Chapter, DocEmbeddings</li>
 *   <li>(f) Generate embeddings asynchronously</li>
 *   <li>(g) Update embeddings in database</li>
 * </ol>
 *
 * IMPORTANT: Uses DocChunkJdbcRepository (not JPA) for embedding
 * persistence to support PGVector operations and custom SQL.
 *
 * @see Fluxo_carga_documents.md
 * @see PLAN_CORRECTION_JDBC_REPOSITORY.md
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DocumentoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoService.class);

    // Repository dependencies
    private final DocumentoRepository documentoRepository;
    private final ChapterRepository chapterRepository;

    // ✅ USE THE EXISTING JDBC REPOSITORY (NOT JPA)
    private final DocChunkJdbcRepository embeddingRepository;

    // Service dependencies
    private final LibraryService libraryService;
    private final DocumentConverter documentConverter;
    private final DocumentRouter documentRouter;
    private final EmbeddingOrchestrator embeddingOrchestrator;
    private final LLMServiceManager llmServiceManager;

    // ✅ NEW: Sequential processing service (v0.0.3+)
    private final DocumentProcessingService documentProcessingService;

    // ========== Checksum Utility Methods ==========

    /**
     * Calculate checksum for document content using CRC64 algorithm.
     * CRC64 provides a good balance between speed and collision resistance.
     *
     * @param content Document content
     * @return CRC64 checksum in hexadecimal format, or null if content is null/empty
     */
    private String calculateChecksum(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        // Normalize content: lowercase, collapse whitespace, trim
        String normalized = normalizeTextForChecksum(content);
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);

        // Use CRC64 from RagUtils - fast and good enough for duplicate detection
        return RagUtils.getCRC64Checksum(bytes);
    }

    /**
     * @TODO FUTURE IMPROVEMENT: Implement SHA-256 checksum calculation
     * 
     * Calculate SHA-256 checksum for document content (more secure, slower).
     * Use this for critical documents where collision resistance is paramount.
     *
     * @param content Document content
     * @return SHA-256 checksum in hexadecimal format, or null if content is null/empty
     * 
     * @see RagUtils#getSHA256Checksum(byte[])
     * @see RagUtils#isChecksumValid(byte[], String)
     */
    protected String calculateSecureChecksum(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        try {
            String normalized = normalizeTextForChecksum(content);
            byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
            return RagUtils.getSHA256Checksum(bytes);
        } catch (Exception e) {
            log.error("Failed to calculate SHA-256 checksum: {}", e.getMessage());
            // Fallback to CRC64
            return calculateChecksum(content);
        }
    }

    /**
     * Normalize text for checksum calculation.
     * Ensures same content produces same checksum regardless of formatting.
     *
     * @param text Text to normalize
     * @return Normalized text
     */
    private String normalizeTextForChecksum(String text) {
        if (text == null) {
            return "";
        }
        // Lowercase, replace all whitespace with single space, trim
        return text.toLowerCase()
                   .replaceAll("\\s+", " ")
                   .trim();
    }

    /**
     * Check if a document with the same checksum and biblioteca_id already exists.
     * This prevents duplicate documents from being stored.
     *
     * @param checksum Document checksum
     * @param bibliotecaId Library ID
     * @return Optional containing existing document if found, empty otherwise
     */
    private Optional<Documento> findDuplicateDocument(String checksum, Integer bibliotecaId) {
        if (checksum == null || checksum.trim().isEmpty() || bibliotecaId == null) {
            return Optional.empty();
        }

        // Search for documents with same checksum in the same library (only vigentes)
        List<Documento> documents = documentoRepository
                .findByBibliotecaIdAndFlagVigenteTrue(bibliotecaId);

        return documents.stream()
                .filter(doc -> {
                    if (doc.getMetadados() == null) {
                        return false;
                    }
                    MetaDoc meta = new MetaDoc(doc.getMetadados());
                    String docChecksum = meta.getChecksum();
                    return checksum.equalsIgnoreCase(docChecksum);
                })
                .findFirst();
    }

    /**
     * Store checksum in document metadata.
     *
     * @param documento Document entity
     * @param checksum Checksum to store
     */
    private void storeChecksum(Documento documento, String checksum) {
        if (checksum == null) {
            return;
        }

        MetaDoc metadata = documento.getMetadados();
        if (metadata == null) {
            metadata = new MetaDoc();
            documento.setMetadados(metadata);
        }

        metadata.setChecksum(checksum);
        log.debug("Stored checksum {} for document: {}", checksum, documento.getTitulo());
    }

    // ========== End of Checksum Utility Methods ==========

    /**
     * Upload document from text content (Fluxo step a)
     *
     * @param titulo Document title
     * @param conteudoMarkdown Markdown content
     * @param libraryId Library ID
     * @param metadata Optional metadata
     * @return Saved document DTO
     */
    public DocumentoDTO uploadFromText(String titulo, String conteudoMarkdown,
                                      Integer libraryId, MetaDoc metadata) {
        log.debug("Uploading document from text: {}", titulo);

        // Validate library exists
        Optional<bor.tools.simplerag.entity.Library> library = libraryService.findById(libraryId);
        if (library.isEmpty()) {
            throw new IllegalArgumentException("Library not found: " + libraryId);
        }

        // Calculate checksum for duplicate detection
        String checksum = calculateChecksum(conteudoMarkdown);
        log.debug("Calculated checksum: {} for document: {}", checksum, titulo);

        // Check for duplicate document (same checksum + biblioteca_id)
        if (checksum != null) {
            Optional<Documento> duplicate = findDuplicateDocument(checksum, libraryId);
            if (duplicate.isPresent()) {
                log.warn("Duplicate document detected: {} (ID: {}). Checksum: {}",
                        duplicate.get().getTitulo(), duplicate.get().getId(), checksum);
                throw new IllegalArgumentException(
                        String.format("Document with identical content already exists in this library: '%s' (ID: %d)",
                                duplicate.get().getTitulo(), duplicate.get().getId()));
            }
        }

        // Initialize metadata if null
        if (metadata == null) {
            metadata = new MetaDoc();
        }

        // Create documento entity
        Documento documento = Documento.builder()
                .bibliotecaId(libraryId)
                .titulo(titulo)
                .conteudoMarkdown(conteudoMarkdown)
                .flagVigente(true)
                .dataPublicacao(java.time.LocalDate.now())
                .metadados(metadata)
                .build();

        // Calculate token count
        int tokenCount = estimateTokenCount(conteudoMarkdown);
        documento.setTokensTotal(tokenCount);

        // Store checksum in metadata
        storeChecksum(documento, checksum);

        // Save documento
        Documento saved = documentoRepository.save(documento);
        log.info("Document uploaded with ID: {} and checksum: {}", saved.getId(), checksum);

        return toDTO(saved);
    }

    /**
     * Upload document from URL (Fluxo step a)
     *
     * @param url Document URL
     * @param libraryId Library ID
     * @param titulo Optional title (will be derived from URL if null)
     * @param metadata Optional metadata
     * @return Saved document DTO
     * @throws Exception If download or conversion fails
     */
    public DocumentoDTO uploadFromUrl(String url, Integer libraryId,
                                     String titulo, MetaDoc metadata) throws Exception {
        log.debug("Uploading document from URL: {}", url);

        // Validate library exists
        Optional<bor.tools.simplerag.entity.Library> library = libraryService.findById(libraryId);
        if (library.isEmpty()) {
            throw new IllegalArgumentException("Library not found: " + libraryId);
        }

        // Download and convert content (Fluxo steps b and c)
        java.net.URI uri = new java.net.URI(url);

        // Detect format (Fluxo step b)
        String detectedFormat = documentConverter.detectFormat(uri);
        log.debug("Detected format: {}", detectedFormat);

        // Convert to Markdown (Fluxo step c)
        String markdown = documentConverter.convertToMarkdown(uri, detectedFormat);

        // Derive title if not provided
        if (titulo == null || titulo.trim().isEmpty()) {
            titulo = deriveTitle(url, markdown);
        }

        // Add URL to metadata
        if (metadata == null) {
            metadata = new MetaDoc();
        }
        metadata.put("url", url);
        metadata.put("detected_format", detectedFormat);

        // Create and save document
        return uploadFromText(titulo, markdown, libraryId, metadata);
    }

    /**
     * Upload document from file bytes (Fluxo step a)
     *
     * @param fileName File name
     * @param fileContent File content as bytes
     * @param libraryId Library ID
     * @param metadata Optional metadata
     * @return Saved document DTO
     * @throws Exception If conversion fails
     */
    public DocumentoDTO uploadFromFile(String fileName, byte[] fileContent,
                                      Integer libraryId, Map<String, Object> metadata_) throws Exception {
        log.debug("Uploading document from file: {}", fileName);

        // Validate library exists
        Optional<bor.tools.simplerag.entity.Library> library = libraryService.findById(libraryId);
        if (library.isEmpty()) {
            throw new IllegalArgumentException("Library not found: " + libraryId);
        }
      
        String detectedFormat = RagUtils.simpleFormatDetector(fileName);
        
        if (detectedFormat != null && detectedFormat.contains("text/plain")) {
		detectedFormat = "markdown"; // Fallback to txt
	    }
        
	if (detectedFormat == null || detectedFormat.isEmpty()) {
	    // Try to detect from byte sample
	    byte[] sample = new byte[Math.min(fileContent.length, 256)];
	    System.arraycopy(fileContent, 0, sample, 0, sample.length);
	    detectedFormat = documentConverter.detectFormat(sample);
	    log.debug("Detected format: {} for file: {}", detectedFormat, fileName);
	}
	
	
        // Convert to Markdown (Fluxo step c)
        String markdown = documentConverter.convertToMarkdown(fileContent, detectedFormat);

        // Derive title from filename
        String titulo = deriveTitle(fileName, markdown);

        MetaDoc metadata = new MetaDoc();
        if (metadata_ != null) {
	    metadata.putAll(metadata_);
	}       
        metadata.put("file_name", fileName);
        metadata.put("detected_format", detectedFormat);
        metadata.put("file_size_bytes", fileContent.length);

        // Create and save document
        return uploadFromText(titulo, markdown, libraryId, metadata);
    }

    // ========== NEW SEQUENTIAL PROCESSING (v0.0.3+) ==========

    /**
     * Process document asynchronously using new sequential flow (v0.0.3+).
     *
     * <p>This method uses {@link DocumentProcessingService} which provides:</p>
     * <ul>
     *   <li>Sequential processing (no complex retry logic)</li>
     *   <li>Context-based approach (creates contexts once, reuses)</li>
     *   <li>Batch embedding generation (up to 10 texts per call)</li>
     *   <li>Dynamic context length handling</li>
     *   <li>Fault-tolerant (individual failures don't stop processing)</li>
     * </ul>
     *
     * @TODO - Add support for includeQA and includeSummary options
     *  
     * @param documentId Document ID to process
     * @return CompletableFuture with processing status
     * @since 0.0.3
     */
    @Async
    public CompletableFuture<ProcessingStatus> processDocumentAsyncV2(Integer documentId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Starting NEW async processing (v2) for document ID: {}", documentId);

                // Load document
                Documento documento = documentoRepository.findById(documentId)
                        .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

                // Load library
                Optional<Library> libraryOpt = libraryService.findById(documento.getBibliotecaId());
                if (libraryOpt.isEmpty()) {
                    throw new IllegalArgumentException("Library not found: " + documento.getBibliotecaId());
                }
                LibraryDTO biblioteca = LibraryDTO.from(libraryOpt.get());

                // Delegate to new processing service
                DocumentProcessingService.ProcessingResult result =
                        documentProcessingService.processDocument(documento, biblioteca).get();

                // Create status response
                ProcessingStatus status = new ProcessingStatus();
                status.setDocumentId(documentId);
                status.setStatus(result.isSuccess() ? "COMPLETED" : "FAILED");
                status.setChaptersCount(result.getChaptersCount());
                status.setEmbeddingsCount(result.getEmbeddingsCount());
                status.setProcessedAt(LocalDateTime.now());
                if (!result.isSuccess()) {
                    status.setErrorMessage(result.getErrorMessage());
                }

                log.info("Document {} processing completed (v2): {} chapters, {}/{} embeddings processed, duration={}",
                        documentId, 
                        result.getChaptersCount(),
                        result.getEmbeddingsProcessed(), 
                        result.getEmbeddingsCount(),
                        result.getDuration());

                return status;
            } catch (Exception e) {
                log.error("Failed to process document {} (v2): {}", documentId, e.getMessage(), e);

                ProcessingStatus status = new ProcessingStatus();
                status.setDocumentId(documentId);
                status.setStatus("FAILED");
                status.setErrorMessage(e.getMessage());
                status.setProcessedAt(LocalDateTime.now());

                return status;
            }
        });
    }

    /**
     * Enriches a document with Q&A and/or summary embeddings (Phase 2 processing).
     *
     * <p>This is a wrapper method that:</p>
     * <ol>
     *   <li>Loads the document entity</li>
     *   <li>Loads the library configuration</li>
     *   <li>Delegates to {@link DocumentProcessingService#enrichDocument(Documento, LibraryDTO, bor.tools.simplerag.service.processing.EnrichmentOptions)}</li>
     * </ol>
     *
     * <p><b>Prerequisites:</b> Document must have been processed (Phase 1) to have chapters.</p>
     *
     * @param documentId Document ID to enrich
     * @param options Enrichment configuration (Q&A, summary, etc.)
     * @return CompletableFuture with enrichment result and statistics
     * @since 0.0.3
     * @see bor.tools.simplerag.service.processing.DocumentProcessingService#enrichDocument(Documento, LibraryDTO, bor.tools.simplerag.service.processing.EnrichmentOptions)
     */
    @Async
    public CompletableFuture<bor.tools.simplerag.service.processing.EnrichmentResult> enrichDocumentAsync(
            Integer documentId,
            bor.tools.simplerag.service.processing.EnrichmentOptions options) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Starting document enrichment (async wrapper) for document ID: {}", documentId);

                // Load document
                Documento documento = documentoRepository.findById(documentId)
                        .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

                // Load library
                Optional<bor.tools.simplerag.entity.Library> libraryOpt =
                        libraryService.findById(documento.getBibliotecaId());
                if (libraryOpt.isEmpty()) {
                    throw new IllegalArgumentException("Library not found: " + documento.getBibliotecaId());
                }

                LibraryDTO biblioteca = LibraryDTO.from(libraryOpt.get());
                // Delegate to enrichment service
                bor.tools.simplerag.service.processing.EnrichmentResult result =
                        documentProcessingService.enrichDocument(documento, biblioteca, options).get();

                log.info("Document {} enrichment completed: {} Q&A, {} summaries, {} chapters processed, duration={}",
                        documentId,
                        result.getQaEmbeddingsGenerated(),
                        result.getSummaryEmbeddingsGenerated(),
                        result.getChaptersProcessed(),
                        result.getDuration());

                return result;

            } catch (Exception e) {
                log.error("Failed to enrich document {}: {}", documentId, e.getMessage(), e);

                return bor.tools.simplerag.service.processing.EnrichmentResult.builder()
                        .documentId(documentId)
                        .success(false)
                        .errorMessage(e.getMessage())
                        .duration("0s")
                        .build();
            }
        });
    }

    // ========== OLD PROCESSING (DEPRECATED) ==========

    /**
     * Process document asynchronously (Fluxo steps d, e, f, g)
     *
     * Performs:
     * - Document splitting via DocumentRouter
     * - Chapter persistence
     * - Embedding generation
     * - Embedding persistence
     *
     * @param documentId Document ID
     * @param includeQA Whether to include Q&A generation
     * @param includeSummary Whether to include summary generation
     * @return CompletableFuture with processing result
     * @deprecated Use {@link #processDocumentAsyncV2(Integer)} instead (v0.0.3+)
     */
    /*
    @Deprecated(since = "0.0.3", forRemoval = true)
    @Async
    public CompletableFuture<ProcessingStatus> processDocumentAsync(Integer documentId,
                                                                    boolean includeQA,
                                                                    boolean includeSummary) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Starting async processing for document ID: {}", documentId);

                // Load document
                Documento documento = documentoRepository.findById(documentId)
                        .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));

                // Load library
                Optional<bor.tools.simplerag.entity.Library> libraryOpt =
                    libraryService.findById(documento.getBibliotecaId());
                if (libraryOpt.isEmpty()) {
                    throw new IllegalArgumentException("Library not found: " + documento.getBibliotecaId());
                }

                LibraryDTO biblioteca = LibraryDTO.from(libraryOpt.get());
                DocumentoWithAssociationDTO documentoDTO = toDTOWithAssociation(documento);
                documentoDTO.setBiblioteca(biblioteca);

                // Create embedding context with library defaults
                EmbeddingContext context = EmbeddingContext.create(biblioteca, 
                							llmServiceManager);

                // Create processing options
                ProcessingOptions options = ProcessingOptions.builder()
                        .includeQA(includeQA)
                        .includeSummary(includeSummary)
                        .build();

                // Full async processing using new orchestrator (Fluxo steps d, e, f, g)
                EmbeddingOrchestrator.ProcessingResult result = embeddingOrchestrator
                        .processDocumentFull(documentoDTO, context, options)
                        .get();

                // Persist processing results (Fluxo step e and g)
                persistProcessingResult(result, documento);

                ProcessingStatus status = new ProcessingStatus();
                status.setDocumentId(documentId);
                status.setStatus("COMPLETED");
                status.setChaptersCount(result.getCapitulos().size());
                status.setEmbeddingsCount(result.getAllEmbeddings().size());
                status.setProcessedAt(LocalDateTime.now());

                log.info("Document {} processing completed: {} chapters, {} embeddings",
                        documentId, status.getChaptersCount(), status.getEmbeddingsCount());

                return status;

            } catch (Exception e) {
                log.error("Failed to process document {}: {}", documentId, e.getMessage(), e);

                ProcessingStatus status = new ProcessingStatus();
                status.setDocumentId(documentId);
                status.setStatus("FAILED");
                status.setErrorMessage(e.getMessage());
                status.setProcessedAt(LocalDateTime.now());

                return status;
            }
        });
    }
*/
    /**
     * Persist processing results (chapters + embeddings)
     * Implements Fluxo_carga_documents.md steps (e) and (g)
     *
     * ✅ UPDATED VERSION: Uses EmbeddingOrchestrator and DocChunkJdbcRepository
     */
    @Transactional
    protected void persistProcessingResult(EmbeddingOrchestrator.ProcessingResult result, Documento documento) 
    {
        log.debug("Persisting processing result for document: {}", documento.getId());

        // 1. Save chapters (using JPA repository - this is fine)
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

        // 3. Save embeddings - ✅ USE JDBC REPOSITORY ONE BY ONE
        List<DocChunk> embeddings = result.getAllEmbeddings().stream()
                .map(dto -> toEntity(dto, documento, chapterIdMap))
                .collect(Collectors.toList());

        // ✅ CORRECT WAY: Save using JDBC repository
        List<Integer> savedIds = new ArrayList<>();
        for (DocChunk emb : embeddings) {
            try {
                Integer id = embeddingRepository.save(emb);  // Returns generated ID
                savedIds.add(id);
            } catch (SQLException e) {
                log.error("Failed to save embedding: {}", e.getMessage(), e);
                throw new RuntimeException("Embedding save failed", e);
            }
        }

        log.debug("Saved {} embeddings with IDs: {}", savedIds.size(), savedIds);

        // 4. Update document status
        documento.setTokensTotal(calculateTotalTokens(result));
        documentoRepository.save(documento);

        log.debug("Document {} fully processed and persisted", documento.getId());
    }

    /**
     * Convert DocumentEmbeddingDTO to Entity for embedding
     */
    private DocChunk toEntity(DocumentEmbeddingDTO dto, 
	    				Documento documento,
	    				Map<String, Integer> chapterIdMap) 
    {
        DocChunk emb = new DocChunk();

        // Set library and document IDs
        emb.setLibraryId(documento.getBibliotecaId());
        emb.setDocumentoId(documento.getId());

        // Try to find chapter ID from metadata
        if (dto.getMetadados() != null) {
            Object chapterTitleObj = dto.getMetadados().get("capitulo_titulo");
            if (chapterTitleObj instanceof String) {
                String chapterTitle = (String) chapterTitleObj;
                if (chapterIdMap.containsKey(chapterTitle)) {
                    emb.setChapterId(chapterIdMap.get(chapterTitle));
                }
            }
        }

        // Set text content (campo 'texto' no banco, não 'trecho_texto')
        emb.setTexto(dto.getTrechoTexto());

        // Set embedding vector
        emb.setEmbeddingVector(dto.getEmbeddingVector());

        // Set embedding type
        emb.setTipoEmbedding(dto.getTipoEmbedding());

        // Set metadata
        emb.setMetadados(dto.getMetadados());

        // Set order if present
        if (dto.getMetadados() != null && dto.getMetadados().containsKey("ordem_cap")) {
            Object ordemObj = dto.getMetadados().get("ordem_cap");
            if (ordemObj instanceof Integer) {
                emb.setOrderChapter((Integer) ordemObj);
            }
        }
        
        emb.setCreatedAt(LocalDateTime.now());	
        emb.setUpdatedAt(LocalDateTime.now());
        emb.setDeletedAt(null);

        return emb;
    }

    /**
     * Convert ChapterDTO to Entity
     */
    private Chapter toEntity(ChapterDTO dto, Documento documento) {
        return Chapter.builder()
                .documentoId(documento.getId())
                .bibliotecaId(documento.getBibliotecaId())
                .titulo(dto.getTitulo())
                .conteudo(dto.getConteudo())
                .ordemDoc(dto.getOrdemDoc())
               // .tokens(dto.getTokens())
                .metadados(dto.getMetadados() != null ? dto.getMetadados() : new MetaDoc())
                .build();
    }

    /**
     * Convert Documento Entity to simple DTO (without associations)
     */
    private DocumentoDTO toDTO(Documento entity) {
        return DocumentoDTO.from(entity);
    }

    /**
     * Convert Documento Entity to DTO with associations (for processing workflows)
     */
    private DocumentoWithAssociationDTO toDTOWithAssociation(Documento entity) {
        return DocumentoWithAssociationDTO.from(entity);
    }

    /**
     * Get all documents
     */
    public List<DocumentoDTO> findAll() {
        return documentoRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get document by ID
     */
    public Optional<DocumentoDTO> findById(Integer id) {
        return documentoRepository.findById(id).map(this::toDTO);
    }

    /**
     * Get all documents for a library
     */
    public List<DocumentoDTO> findByLibraryId(Integer libraryId) {
        return documentoRepository.findByBibliotecaId(libraryId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get active documents for a library
     */
    public List<DocumentoDTO> findActiveByLibraryId(Integer libraryId) {
        return documentoRepository.findByBibliotecaIdAndFlagVigenteTrue(libraryId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Update document status
     */
    @Transactional
    public void updateStatus(Integer documentId, boolean flagVigente) {
        Documento documento = documentoRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        documento.setFlagVigente(flagVigente);
        documentoRepository.save(documento);
        log.info("Updated document {} status to vigente={}", documentId, flagVigente);
    }

    /**
     * Delete document (soft delete by setting flagVigente=false)
     */
    @Transactional
    public void delete(Integer documentId) {
        updateStatus(documentId, false);
    }

    /**
     * Derive title from URL or content
     */
    private String deriveTitle(String source, String content) {
        // Try to extract from filename
        if (source != null) {
            String[] parts = source.split("[/\\\\]");
            String filename = parts[parts.length - 1];
            // Remove extension
            int dotIndex = filename.lastIndexOf('.');
            if (dotIndex > 0) {
                filename = filename.substring(0, dotIndex);
            }
            if (!filename.trim().isEmpty()) {
                return filename.replace('-', ' ').replace('_', ' ');
            }
        }

        // Try to extract from first line of content
        if (content != null && !content.trim().isEmpty()) {
            String[] lines = content.split("\\n");
            for (String line : lines) {
                line = line.trim().replaceAll("^#+\\s*", ""); // Remove markdown headers
                if (!line.isEmpty() && line.length() < 200) {
                    return line;
                }
            }
        }

        return "Untitled Document";
    }

    /**
     * Estimate token count for content
     */
    private int estimateTokenCount(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        // Simple estimation: words / 0.75
        String[] words = content.split("\\s+");
        return (int) Math.ceil(words.length / 0.75);
    }

    /**
     * Calculate total tokens from processing result
     */
    private int calculateTotalTokens(EmbeddingOrchestrator.ProcessingResult result) {
        if (result.getCapitulos() == null) {
            return 0;
        }
        return result.getCapitulos().stream()
                .mapToInt(cap -> cap.getTokensTotal() != null ? cap.getTokensTotal() : 0)
                .sum();
    }

    /**
     * Processing status DTO
     */
    public static class ProcessingStatus {
        private Integer documentId;
        private String status;
        private Integer chaptersCount;
        private Integer embeddingsCount;
        private String errorMessage;
        private LocalDateTime processedAt;

        // Getters and Setters
        public Integer getDocumentId() { return documentId; }
        public void setDocumentId(Integer documentId) { this.documentId = documentId; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public Integer getChaptersCount() { return chaptersCount; }
        public void setChaptersCount(Integer chaptersCount) { this.chaptersCount = chaptersCount; }

        public Integer getEmbeddingsCount() { return embeddingsCount; }
        public void setEmbeddingsCount(Integer embeddingsCount) { this.embeddingsCount = embeddingsCount; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

        public LocalDateTime getProcessedAt() { return processedAt; }
        public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
    }

    // ========== OVERWRITE FEATURE (v1.0) ==========

    /**
     * Result of checking existing processing data.
     *
     * Used by overwrite feature to determine if document needs reprocessing.
     *
     * @since 1.0 (overwrite feature)
     */
    @lombok.Data
    @lombok.Builder
    public static class ProcessingCheckResult {
        private Integer documentId;
        private int chaptersCount;
        private int embeddingsCount;
        private boolean hasChapters;
        private boolean hasEmbeddings;
    }

    /**
     * Checks if document has existing Chapters and DocEmbeddings.
     *
     * Used by overwrite feature to determine processing strategy.
     *
     * @param documentId Document ID
     * @return Result with counts and flags
     * @since 1.0 (overwrite feature)
     */
    public ProcessingCheckResult checkExistingProcessing(Integer documentId) {
        log.debug("Checking existing processing for document: {}", documentId);

        // Count chapters
        int chaptersCount = chapterRepository.countByDocumentoId(documentId);

        // Count embeddings
        int embeddingsCount = 0;
        try {
            embeddingsCount = embeddingRepository.countByDocumentoId(documentId);
        } catch (Exception e) {
            log.warn("Failed to count embeddings for document {}: {}", documentId, e.getMessage());
        }

        log.debug("Document {} has {} chapters and {} embeddings",
                documentId, chaptersCount, embeddingsCount);

        return ProcessingCheckResult.builder()
                .documentId(documentId)
                .chaptersCount(chaptersCount)
                .embeddingsCount(embeddingsCount)
                .hasChapters(chaptersCount > 0)
                .hasEmbeddings(embeddingsCount > 0)
                .build();
    }

    /**
     * Deletes all Chapters and DocEmbeddings for a document.
     *
     * Uses ON DELETE CASCADE to automatically delete related DocEmbeddings.
     *
     * IMPORTANT: This method ONLY deletes existing data. The caller (DocumentController)
     * is responsible for continuing the processing flow to create NEW Chapters and
     * DocEmbeddings from the existing Documento.conteudoMarkdown.
     *
     * @param documentId Document ID
     * @throws RuntimeException if deletion fails
     * @since 1.0 (overwrite feature)
     */
    @Transactional
    public void deleteExistingProcessing(Integer documentId) {
        log.info("Deleting existing processing data for document: {}", documentId);

        try {
            // Count before deletion (for logging)
            int chaptersCount = chapterRepository.countByDocumentoId(documentId);
            int embeddingsCount = 0;
            try {
                embeddingsCount = embeddingRepository.countByDocumentoId(documentId);
            } catch (Exception e) {
                log.warn("Failed to count embeddings before deletion: {}", e.getMessage());
            }

            // Delete chapters (CASCADE will delete embeddings automatically)
            int deletedChapters = chapterRepository.deleteByDocumentoId(documentId);

            log.info("Deleted {} chapters and {} embeddings (via CASCADE) for document {}",
                    deletedChapters, embeddingsCount, documentId);

            // Note: Processing will continue in DocumentController to create NEW entities

        } catch (Exception e) {
            log.error("Failed to delete processing data for document {}: {}",
                    documentId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete existing processing data: " + e.getMessage(), e);
        }
    }
}
