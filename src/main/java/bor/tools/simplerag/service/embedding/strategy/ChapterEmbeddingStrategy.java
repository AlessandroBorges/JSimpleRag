package bor.tools.simplerag.service.embedding.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import bor.tools.simplellm.Embeddings_Op;
import bor.tools.simplellm.LLMService;
import bor.tools.simplellm.MapParam;
import bor.tools.simplellm.Model_Type;
import bor.tools.simplellm.exceptions.LLMException;
import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocumentEmbeddingDTO;
import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.entity.enums.TipoEmbedding;
import bor.tools.simplerag.service.embedding.model.EmbeddingContext;
import bor.tools.simplerag.service.embedding.model.EmbeddingRequest;
import bor.tools.simplerag.service.llm.LLMServiceManager;
import bor.tools.splitter.SplitterFactory;
import bor.tools.splitter.SplitterGenerico;
import lombok.RequiredArgsConstructor;

/**
 * Strategy for generating chapter embeddings with automatic chunking.
 *
 * <p><b>UPDATED v0.0.3:</b> Migrated from ContentSplitter to SplitterGenerico via Factory.</p>
 *
 * Supports multiple generation modes:
 * - FLAG_FULL_TEXT_METADATA (1): Full text + metadata
 * - FLAG_ONLY_METADATA (2): Metadata only
 * - FLAG_ONLY_TEXT (3): Text only
 * - FLAG_SPLIT_TEXT_METADATA (4): Split into chunks
 * - FLAG_AUTO (5): Automatic selection based on size
 *
 * Integrates with LLMServiceManager for multi-provider support.
 *
 * @since 0.0.1
 */
@Component
@RequiredArgsConstructor
public class ChapterEmbeddingStrategy implements EmbeddingGenerationStrategy {

    private static final Logger log = LoggerFactory.getLogger(ChapterEmbeddingStrategy.class);

    private final LLMServiceManager llmServiceManager;
    private final SplitterFactory splitterFactory;

    @Value("${rag.embedding.default-model:snowflake}")
    private String defaultEmbeddingModel;
    
    @Value("${rag.embedding.default-dimension:768}")
    private Integer defaultEmbeddingDimension;

    // Generation flags
    public static final int FLAG_FULL_TEXT_METADATA = 1;
    public static final int FLAG_ONLY_METADATA = 2;
    public static final int FLAG_ONLY_TEXT = 3;
    public static final int FLAG_SPLIT_TEXT_METADATA = 4;
    public static final int FLAG_AUTO = 5;

    // Size constants
    private static final int DEFAULT_CHUNK_SIZE = 2000; // tokens
    private static final int MIN_CHUNK_SIZE = 512; // tokens
    
    /**
     * Not useful on metadata
     */
    private String[] negativeKeys = {"crc", "checksum","tamanho", "size","dimensions","dimension",
	    			    "page_count","pagecount","num_pages","number_of_pages","file_type","filetype",
	                             "last_modified","modified_date","created_at","updated_at","data_criacao",
	                            "data_modificacao","id","identificador","documento_id","documentoid",
	                            "biblioteca_id","bibliotecaid", "file_path","filepath","path",
	                            "file_size","filesize","encoding","detected_format","format",
	                            //"url","source_url"
    				};        
    private Set<String> ignoredKeys = Set.of(negativeKeys);

    @Override
    public List<DocumentEmbeddingDTO> generate(EmbeddingRequest request) {
        log.debug("Generating chapter embeddings with flag: {}", request.getGenerationFlag());

        if (request.getChapter() == null) {
            throw new IllegalArgumentException("Chapter is required for ChapterEmbeddingStrategy");
        }

        if (request.getContext() == null) {
            throw new IllegalArgumentException("EmbeddingContext is required");
        }

        ChapterDTO chapter = request.getChapter();
        String content = chapter.getConteudo();

        if (content == null || content.trim().isEmpty()) {
            log.warn("Empty content for chapter: {}", chapter.getTitulo());
            return new ArrayList<>();
        }

        try {
            LibraryDTO library = request.getContext().getLibrary();
            int flag = request.getGenerationFlag() != null ? request.getGenerationFlag() : FLAG_AUTO;

            return switch (flag) {
                case FLAG_FULL_TEXT_METADATA -> List.of(createFullTextEmbedding(chapter, library, request));
                case FLAG_ONLY_METADATA -> List.of(createMetadataOnlyEmbedding(chapter, library, request));
                case FLAG_ONLY_TEXT -> List.of(createTextOnlyEmbedding(chapter, library, request));
                case FLAG_SPLIT_TEXT_METADATA -> createSplitTextEmbeddings(chapter, library, request);
                case FLAG_AUTO -> createAutoEmbeddings(chapter, library, request);
                default -> {
                    log.warn("Unknown generation flag: {}, using AUTO", flag);
                    yield createAutoEmbeddings(chapter, library, request);
                }
            };

        } catch (Exception e) {
            log.error("Failed to generate chapter embeddings: {}", e.getMessage(), e);
            throw new RuntimeException("Chapter embedding generation failed", e);
        }
    }

    @Override
    public boolean supports(EmbeddingRequest request) {
        return request.getChapter() != null
                && request.getOperation() == Embeddings_Op.DOCUMENT
                && (request.getNumberOfQAPairs() == null)  // Not Q&A
                && (request.getMaxSummaryLength() == null); // Not summary
    }

    @Override
    public String getStrategyName() {
        return "ChapterEmbeddingStrategy";
    }

    // ========== Private Helper Methods ==========

    /**
     * Creates embedding with full text + metadata
     */
    private DocumentEmbeddingDTO createFullTextEmbedding(ChapterDTO chapter, LibraryDTO library,
                                                         EmbeddingRequest request) throws LLMException {
        String fullText = buildTextWithMetadata(chapter);
        return createEmbeddingFromText(
                fullText,
                chapter.getTitulo(),
                library,
                chapter.getDocumentoId(),
                chapter.getId(),
                TipoEmbedding.CAPITULO,
                request
        );
    }

    /**
     * Creates embedding with metadata only
     */
    private DocumentEmbeddingDTO createMetadataOnlyEmbedding(ChapterDTO chapter, LibraryDTO library,
                                                             EmbeddingRequest request) throws LLMException {
        String metadataText = buildMetadataText(chapter);
        return createEmbeddingFromText(
                metadataText,
                chapter.getTitulo() + " (Metadados)",
                library,
                chapter.getDocumentoId(),
                chapter.getId(),
                TipoEmbedding.METADADOS,
                request
        );
    }

    /**
     * Creates embedding with text only
     */
    private DocumentEmbeddingDTO createTextOnlyEmbedding(ChapterDTO chapter, LibraryDTO library,
                                                         EmbeddingRequest request) throws LLMException {
        return createEmbeddingFromText(
                chapter.getConteudo(),
                chapter.getTitulo(),
                library,
                chapter.getDocumentoId(),
                chapter.getId(),
                TipoEmbedding.CAPITULO,
                request
        );
    }

    /**
     * Creates multiple embeddings by splitting text into chunks.
     *
     * <p><b>UPDATED v0.0.3:</b> Uses SplitterGenerico.splitChapterIntoChunks() instead of ContentSplitter.</p>
     */
    private List<DocumentEmbeddingDTO> createSplitTextEmbeddings(ChapterDTO chapter, LibraryDTO library,
                                                                 EmbeddingRequest request) {
        List<DocumentEmbeddingDTO> embeddings = new ArrayList<>();

        try {
            // Use SplitterGenerico via Factory to split into optimized chunks
            SplitterGenerico splitter = splitterFactory.createGenericSplitter(library);
            List<DocumentEmbeddingDTO> chunkDTOs = splitter.splitChapterIntoChunks(chapter);
                                    
            // Generate embeddings for each chunk
            for (int i = 0; i < chunkDTOs.size(); i++) {
                DocumentEmbeddingDTO chunkDTO = chunkDTOs.get(i);
                String chunkTextWithMetadata = buildTextWithMetadata(chapter);				
                
                DocumentEmbeddingDTO embedding = createEmbeddingFromText(
                        chunkTextWithMetadata,
                        chapter.getTitulo() + " - Chunk " + (i + 1),
                        library,
                        chapter.getDocumentoId(),
                        chapter.getId(),
                        chunkDTO.getTipoEmbedding(),
                        request
                );

                // Add chunk-specific metadata
                embedding.getMetadados().put("chunk_index", String.valueOf(i));
                embedding.getMetadados().put("total_chunks", String.valueOf(chunkDTOs.size()));
                embedding.getMetadados().put("parent_chapter", chapter.getTitulo());

                embeddings.add(embedding);
            }

        } catch (Exception e) {
            log.error("Failed to create split text embeddings: {}", e.getMessage(), e);
        }

        return embeddings;
    }

    /**
     * Automatically chooses embedding type based on content size
     */
    private List<DocumentEmbeddingDTO> createAutoEmbeddings(ChapterDTO chapter, 
	    						    LibraryDTO library,
                                                            EmbeddingRequest request) 
    {
        try {
            int tokenCount = estimateTokenCount(chapter.getConteudo());

            if (tokenCount <= MIN_CHUNK_SIZE) {
                // Very small - use text + metadata
                log.debug("Small chapter ({} tokens) - using full text + metadata", tokenCount);
                return List.of(createFullTextEmbedding(chapter, library, request));
            } else if (tokenCount <= DEFAULT_CHUNK_SIZE) {
                // Ideal size - use text only
                log.debug("Medium chapter ({} tokens) - using text only", tokenCount);
                //return List.of(createTextOnlyEmbedding(chapter, library, request));
                return List.of(createFullTextEmbedding(chapter, library, request));
            } else {
                // Too large - split into chunks
                log.debug("Large chapter ({} tokens) - splitting into chunks", tokenCount);
                return createSplitTextEmbeddings(chapter, library, request);
            }

        } catch (Exception e) {
            log.error("Failed to create auto embeddings: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Creates a DocumentEmbeddingDTO from text using LLMServiceManager
     */
    private DocumentEmbeddingDTO createEmbeddingFromText(
            String text,
            String title,
            LibraryDTO library,
            Integer documentoId,
            Integer capituloId,
            TipoEmbedding tipoEmbedding,
            EmbeddingRequest request) throws LLMException {

        // Resolve embedding model to use
        String modelName = request.getEmbeddingModelName(defaultEmbeddingModel);
        log.debug("Using embedding model: {}", modelName);

        // Get appropriate LLMService from pool
        LLMService llmService = llmServiceManager.getLLMServiceByRegisteredModel(modelName);               
        if (llmService == null) {
            throw new IllegalStateException(
                "No LLM service found for embedding model: " + modelName +
                ". Check if the model is registered in any provider."
            );
        }
        
        var model = llmService.getRegisteredModels().get(modelName);
        if (model == null || !model.isType(Model_Type.EMBEDDING)) {
	    throw new IllegalStateException(
		"Model " + modelName + " from provider " + llmService.getServiceProvider() +
		" does not support embeddings."
	    );
	} else {
	    modelName = model.getName(); // Use exact registered name
	}
        log.debug("Using LLMService from provider: {}", llmService.getServiceProvider());

        // Prepare parameters
        MapParam params = new MapParam();
        params.model(modelName);

        // Add library context
        if (library != null) {
            Integer dimension = library.getEmbeddingDimension();
            if (dimension != null && model.isType(Model_Type.EMBEDDING_DIMENSION)) {
		params.put("dimensions", dimension);
	    }else {
		log.debug("Library embedding dimension is null or model does not support setting it.");
	    }
        }

        // Generate embedding
        float[] embedding = llmService.embeddings(Embeddings_Op.DOCUMENT, text, params);

        // Normalize embedding
        embedding = normalizeEmbedding(embedding, request.getContext());
        
        // Create DTO
        DocumentEmbeddingDTO docEmbedding = new DocumentEmbeddingDTO();
        docEmbedding.setTrechoTexto(text);
        docEmbedding.setEmbeddingVector(embedding);
        docEmbedding.setTipoEmbedding(tipoEmbedding);
        docEmbedding.setBibliotecaId(library != null ? library.getId() : null);
        docEmbedding.setDocumentoId(documentoId);
        docEmbedding.setCapituloId(capituloId);

        // Configure metadata
        docEmbedding.getMetadados().setNomeDocumento(title);
        if (library != null) {
            docEmbedding.getMetadados().put("biblioteca_id", library.getId());
            docEmbedding.getMetadados().put("biblioteca_nome", library.getNome());
        }
        docEmbedding.getMetadados().put("embedding_operation", Embeddings_Op.DOCUMENT.toString());
        docEmbedding.getMetadados().put("embedding_model", modelName);
        docEmbedding.getMetadados().put("created_at", java.time.Instant.now().toString());

        return docEmbedding;
    }

    /**
     * Normalizes embedding vector to specified dimension
     * @param embedding
     * @param context
     * @return
     */
    private float[] normalizeEmbedding(float[] embedding, EmbeddingContext context) {
	Integer length = context != null && context.getEmbeddingDimension() != null ?
		    context.getEmbeddingDimension() : defaultEmbeddingDimension;	
	// Adjust length if necessary
	if (length!=null && embedding != null && embedding.length != length) {
	    log.debug("Normalizing embedding from length {} to {}", embedding.length, length);
	    // normalize source first - just in case
	    embedding = bor.tools.simplerag.util.VectorUtil.normalize(embedding);
	    // then resize
	    float[] normalized = new float[length];
	    System.arraycopy(embedding, 0, normalized, 0, Math.min(embedding.length, length));
	    embedding = normalized;	
	}
	// Normalize vector. always
	return bor.tools.simplerag.util.VectorUtil.normalize(embedding);
    }

    /**
     * Builds text with metadata included
     */
    private String buildTextWithMetadata(ChapterDTO chapter) {
        StringBuilder builder = new StringBuilder();

        // Add title       
        if (chapter.getTitulo() != null) {
            builder.append("Título: ").append(chapter.getTitulo()).append("\n\n");
        }

        // Add relevant metadata
        if (chapter.getMetadados() != null) {
            String metadataText = buildMetadataText(chapter);
            if (!metadataText.isEmpty()) {
        	builder.append("\n**METADADOS DO DOCUMENTO**\n");
                builder.append(metadataText).append("\n\n");
            }
        }

        builder.append("\n---\nConteúdo:\n");
        // Add main content
        builder.append(chapter.getConteudo());

        return builder.toString();
    }

    /**
     * Builds text with metadata only
     */
    private String buildMetadataText(ChapterDTO chapter) {
        StringBuilder builder = new StringBuilder();
        
        if (chapter.getMetadados() != null) {
            // Add most relevant metadata
            chapter.getMetadados().forEach((key, value) -> {
                if (ignoredKeys.contains(key.toLowerCase())==false && value != null && !value.toString().trim().isEmpty()) {                    
                    builder.append(key).append(": ").append(value).append("\n");
                }
            });
        }
        return builder.toString();
    }

    /**
     * Estimates token count in text
     * Simple estimation: 1 token ~ 4.2 characters
     */
    private int estimateTokenCount(String text) {
        if (text == null) return 0;       
        return (int) Math.ceil( ((float)text.length()) / 4.2f);
    }
}
