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
import bor.tools.simplerag.entity.DocumentEmbedding;
import bor.tools.simplerag.entity.Documento;
import bor.tools.simplerag.entity.MetaDoc;
import bor.tools.simplerag.entity.Metadata;
import bor.tools.simplerag.repository.ChapterRepository;
import bor.tools.simplerag.repository.DocEmbeddingJdbcRepository;
import bor.tools.simplerag.repository.DocumentoRepository;
import bor.tools.simplerag.service.embedding.EmbeddingOrchestrator;
import bor.tools.simplerag.service.embedding.model.EmbeddingContext;
import bor.tools.simplerag.service.embedding.model.ProcessingOptions;
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
 * IMPORTANT: Uses DocEmbeddingJdbcRepository (not JPA) for embedding
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
    private final DocEmbeddingJdbcRepository embeddingRepository;

    // Service dependencies
    private final LibraryService libraryService;
    private final DocumentConverter documentConverter;
    private final DocumentRouter documentRouter;
    private final EmbeddingOrchestrator embeddingOrchestrator;

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
      
        String detectedFormat = simpleFormatDetector(fileName);
        
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

    /**
     * Simple format detector based on file extension
     * @param fileName
     * @return MIME Types or null if unknown
     */
    private String simpleFormatDetector(String fileName) {
	String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
	
	switch (ext) {
	    case "pdf":
		return "application/pdf";
		
	    case "doc":
		return "application/msword";
		
	    case "docx":
		return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
		
	    case "txt":
	    case "sql":
	    case "log":
		return "text/plain";
		
	    case "java":
		return "text/x-java-source";
		
	    case "py":
		return "text/x-python";
		
	    case "js":	
		return "application/javascript";
		
	    case "csv":
		return "text/csv";
		
	    case "md":
	    case "markdown":
		return "text/markdown";
		
	    case "html":
	    case "htm":
		return "text/html";
		
	    case "xml":
		return "application/xml";
		
	    case "xhtml":	
		return "application/xhtml+xml";
		
	    case "ppt":
		return "application/vnd.ms-powerpoint";
		
	    case "pptx":
		return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
		
	    case "xls":
	    case "xlm":
		return "application/vnd.ms-excel";
		
	    case "xlsx":
		return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";	    
	    default:
		return null;
	}
    }

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
     */
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
                EmbeddingContext context = EmbeddingContext.fromLibrary(biblioteca);

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

    /**
     * Persist processing results (chapters + embeddings)
     * Implements Fluxo_carga_documents.md steps (e) and (g)
     *
     * ✅ UPDATED VERSION: Uses EmbeddingOrchestrator and DocEmbeddingJdbcRepository
     */
    @Transactional
    protected void persistProcessingResult(EmbeddingOrchestrator.ProcessingResult result, Documento documento) {
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
        List<DocumentEmbedding> embeddings = result.getAllEmbeddings().stream()
                .map(dto -> toEntity(dto, documento, chapterIdMap))
                .collect(Collectors.toList());

        // ✅ CORRECT WAY: Save using JDBC repository
        List<Integer> savedIds = new ArrayList<>();
        for (DocumentEmbedding emb : embeddings) {
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
    private DocumentEmbedding toEntity(DocumentEmbeddingDTO dto, 
	    				Documento documento,
	    				Map<String, Integer> chapterIdMap) 
    {
        DocumentEmbedding emb = new DocumentEmbedding();

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
        return DocumentoDTO.builder()
                .id(entity.getId())
                .bibliotecaId(entity.getBibliotecaId())
                .titulo(entity.getTitulo())
                .conteudoMarkdown(entity.getConteudoMarkdown())
                .flagVigente(entity.getFlagVigente())
                .dataPublicacao(entity.getDataPublicacao())
                .tokensTotal(entity.getTokensTotal())
                .metadados(entity.getMetadados() != null ? new Metadata(entity.getMetadados()) : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    /**
     * Convert Documento Entity to DTO with associations (for processing workflows)
     */
    private DocumentoWithAssociationDTO toDTOWithAssociation(Documento entity) {
        return DocumentoWithAssociationDTO.builder()
                .id(entity.getId())
                .bibliotecaId(entity.getBibliotecaId())
                .titulo(entity.getTitulo())
                .conteudoMarkdown(entity.getConteudoMarkdown())
                .flagVigente(entity.getFlagVigente())
                .dataPublicacao(entity.getDataPublicacao())
                .tokensTotal(entity.getTokensTotal())
                .metadados(entity.getMetadados() != null ? new Metadata(entity.getMetadados()) : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .deletedAt(entity.getDeletedAt())
                .build();
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
}
