package bor.tools.simplerag.service.processing;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bor.tools.simplellm.Embeddings_Op;
import bor.tools.simplellm.MapParam;
import bor.tools.simplellm.Model;
import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocChunkDTO;
import bor.tools.simplerag.dto.DocumentoWithAssociationDTO;
import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.entity.Chapter;
import bor.tools.simplerag.entity.DocChunk;
import bor.tools.simplerag.entity.Documento;
import bor.tools.simplerag.entity.enums.TipoConteudo;
import bor.tools.simplerag.entity.enums.TipoEmbedding;
import bor.tools.simplerag.repository.ChapterRepository;
import bor.tools.simplerag.repository.DocChunkJdbcRepository;
import bor.tools.simplerag.service.embedding.model.EmbeddingContext;
import bor.tools.simplerag.service.embedding.model.EmbeddingRequest;
import bor.tools.simplerag.service.embedding.strategy.QAEmbeddingStrategy;
import bor.tools.simplerag.service.embedding.strategy.SummaryEmbeddingStrategy;
import bor.tools.simplerag.service.llm.LLMServiceManager;
import bor.tools.simplerag.service.processing.context.LLMContext;
import bor.tools.splitter.AbstractSplitter;
import bor.tools.splitter.DocumentRouter;
import bor.tools.splitter.SplitterFactory;
import bor.tools.splitter.SplitterGenerico;
import bor.tools.utils.RagUtils;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * New document processing service with sequential flow.
 *
 * <p>This service replaces {@link bor.tools.simplerag.service.embedding.EmbeddingOrchestrator}
 * with a simpler, more maintainable sequential approach. Key features:</p>
 *
 * <ul>
 *   <li><b>Sequential processing:</b> No complex retry logic or parallelization</li>
 *   <li><b>Context-based:</b> Creates LLM and Embedding contexts once, reuses throughout</li>
 *   <li><b>Fault-tolerant:</b> Individual failures don't stop the entire process</li>
 *   <li><b>Batch processing:</b> Up to 10 texts per embedding call</li>
 *   <li><b>Dynamic limits:</b> Respects model's context length via getContextLength()</li>
 * </ul>
 *
 * <h2>Processing Flow (Revision 1.1):</h2>
 * <pre>
 * 1. Create contexts (LLM + Embedding) ← FIRST!
 * 2. Split document into chapters/chunks (uses LLM context for tokenCount)
 * 3. Persist chapters and embeddings (with NULL vectors)
 * 4. Calculate embeddings in batches (up to 10 texts)
 * 5. Update embedding vectors (fault-tolerant)
 * </pre>
 *
 * @since 0.0.3
 * @version 1.1
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentProcessingService {
    
    
    
    // Size constants
    private static final int DEFAULT_CHUNK_SIZE = 512; // tokens
    private static final int MIN_CHUNK_SIZE = 256; // tokens

    private static int DEFAULT_EMBED_DIMENSION = 768; // tokens
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
    /** facility */								
    private Set<String> ignoredKeys = Set.of(negativeKeys);
    
    // ========== Dependencies ==========

    private final DocumentRouter documentRouter;
    private final SplitterFactory splitterFactory;
    private final ChapterRepository chapterRepository;
    private final DocChunkJdbcRepository embeddingRepository;
    private final LLMServiceManager llmServiceManager;
 // private final LibraryService libraryService;
    private final QAEmbeddingStrategy qaEmbeddingStrategy;
    private final SummaryEmbeddingStrategy summaryEmbeddingStrategy;

    // ========== Constants (REVISED v1.1) ==========

    /**
     * Maximum number of texts per batch (REVISED: was 5, now 10).
     */
    private static final int BATCH_SIZE = 5;

    /**
     * Model name for token counting (REVISED: use "fast" model).
     */
    private static final String TOKEN_MODEL = "fast";

    /**
     * Threshold in tokens for generating chapter summaries.
     */
    private static final int SUMMARY_THRESHOLD_TOKENS = 2500;

    /**
     * Maximum tokens for generated summaries.
     */
    private static final int SUMMARY_MAX_TOKENS = 1024*2;

    /**
     * Ideal chunk size for chapter splitting (aligns with ContentSplitter.IDEAL_TOKENS).
     */
    private static final int IDEAL_CHUNK_SIZE_TOKENS = 512;

    /**
     * Percentage threshold for deciding to summarize vs truncate oversized text.
     * If text exceeds context length by more than this percentage, generate summary.
     * Otherwise, truncate.
     */
    private static final double OVERSIZE_THRESHOLD_PERCENT = 5.0;

    // ========== Main Processing Method ==========

    /**
     * Processes a document asynchronously using the new sequential flow.
     *
     * <p><b>Revision 1.1:</b> Contexts are created BEFORE splitting, because
     * the split process needs access to tokenCount() functionality.</p>
     *
     * @param documento the document to process
     * @param library the library configuration
     * @return CompletableFuture with processing results
     */
    @Async
    public CompletableFuture<ProcessingResult> processDocument( Documento documento,
	    							LibraryDTO library,
	    							GenerationFlag generationFlag) {

        log.info("Starting document processing: docId={}, library={}",
                documento.getId(), library.getNome());

        Instant startTime = Instant.now();

        try {
            // ETAPA 2.1: Create contexts FIRST (REVISED!)
            log.debug("Creating LLM and Embedding contexts...");
            LLMContext llmContext = LLMContext.create(library, llmServiceManager);
            EmbeddingContext embeddingContext = EmbeddingContext.create(library, llmServiceManager);

            log.info("Contexts created: llm={}, embedding={}, contextLength={}",
                    llmContext.getModelName(),
                    embeddingContext.getEmbeddingModelName(),
                    embeddingContext.getContextLength());

            // ETAPA 2.2: Split and persist (uses llmContext for token counting)
            log.debug("Splitting document into chapters and chunks...");
            SplitResult splitResult = splitAndPersist(documento, 
        	    					library, 
        	    					llmContext, 
        	    					generationFlag);

            log.info("Split completed: {} chapters, {} embeddings created",
                    splitResult.getChaptersCount(),
                    splitResult.getEmbeddingsCount());

            // ETAPA 2.3: Calculate embeddings and update vectors
            log.debug("Calculating embeddings in batches...");
            int processed = calculateAndUpdateEmbeddings(
                                        splitResult.getEmbeddings(),
                                        embeddingContext,
                                        llmContext,
                                        generationFlag);

            log.info("Embeddings processed: {}/{} successful",
                    processed, splitResult.getEmbeddingsCount());

            // Build result
            Duration duration = Duration.between(startTime, Instant.now());

            ProcessingResult result = ProcessingResult.builder()
                    .documentId(documento.getId())
                    .chaptersCount(splitResult.getChaptersCount())
                    .embeddingsCount(splitResult.getEmbeddingsCount())
                    .embeddingsProcessed(processed)
                    .embeddingsFailed(splitResult.getEmbeddingsCount() - processed)
                    .duration(formatDuration(duration))
                    .success(true)
                    .build();

            log.info("Document processing completed successfully: {}", result);

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("Document processing failed for docId={}: {}",
                    documento.getId(), e.getMessage(), e);

            Duration duration = Duration.between(startTime, Instant.now());

            ProcessingResult result = ProcessingResult.builder()
                    .documentId(documento.getId())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .duration(formatDuration(duration))
                    .build();

            return CompletableFuture.completedFuture(result);
        }
    }

    // ========== ETAPA 2.2: Split and Persist ==========

    /**
     * Splits document into chapters and chunks, persists to database with NULL vectors.
     *
     * <p><b>REVISED:</b> Uses llmContext to count tokens via tokenCount("fast").</p>
     *
     * @param documento the document to split
     * @param library the library configuration
     * @param llmContext LLM context for token counting
     * @return split result with chapters and embeddings
     * @throws Exception if split or persistence fails
     */
    @Transactional
    protected SplitResult splitAndPersist( Documento documento,
	    				   LibraryDTO library,
	    				   LLMContext llmContext,
	    				   GenerationFlag generationFlag) throws Exception 
    {

        // Detect content type
	String header = documento.getText().substring(0, Math.min(500, documento.getText().length()));
	
        TipoConteudo tipoConteudo = documentRouter.detectContentType(llmContext.getModel(), header);
        log.debug("Detected content type: {}", tipoConteudo);

        // Create appropriate splitter
        AbstractSplitter splitter = splitterFactory.createSplitter(tipoConteudo, library);

        // Convert to DocumentoWithAssociationDTO for splitter
        DocumentoWithAssociationDTO documentoDTO = DocumentoWithAssociationDTO.from(documento);
        
        // Split document into chapters
        List<ChapterDTO> chapterDTOs = splitter.splitDocumento(documentoDTO);
        log.debug("Document split into {} chapters", chapterDTOs.size());

        // Convert to entities and persist
        List<Chapter> chapters = new ArrayList<>();
        List<DocChunk> allEmbeddings = new ArrayList<>();
        List<Integer> embeddingsPerChapter = new ArrayList<>(); // Track count per chapter
                
        for (ChapterDTO chapterDTO : chapterDTOs) {
            // Create chapter entity
            Chapter chapter = chapterDTO.toEntity();
            chapters.add(chapter);
            if(chapter.getBibliotecaId()==null) {
        	log.debug("Chapter bibliotecaId: {}", chapter.getBibliotecaId());
            }

            // Create embeddings for this chapter (will be persisted after chapter)
            List<DocChunk> chapterEmbeddings = createChapterEmbeddings( chapter,
                                                                        chapterDTO,
                                                                        documento,
                                                                        library,
                                                                        llmContext,
                                                                        generationFlag);
            
                       
            // Track how many embeddings this chapter generated
            embeddingsPerChapter.add(chapterEmbeddings.size());
            allEmbeddings.addAll(chapterEmbeddings);
        }

        // Persist chapters (generates IDs)
        chapters = chapterRepository.saveAll(chapters);
        log.debug("Persisted {} chapters", chapters.size());

        // Update chapter IDs in embeddings using the count tracking
        int embIndex = 0;
        for (int i = 0; i < chapters.size(); i++) {
            Chapter chapter = chapters.get(i);
            int embCount = embeddingsPerChapter.get(i);

            log.trace("Assigning {} embeddings to chapter: {}", embCount, chapter.getTitulo());

            // Assign chapter ID to the exact number of embeddings for this chapter
            for (int j = 0; j < embCount; j++) {
                if (embIndex < allEmbeddings.size()) {
                    DocChunk emb = allEmbeddings.get(embIndex);
                    emb.setChapterId(chapter.getId());
                    embIndex++;
                } else {
                    log.error("Embedding index out of bounds: {} >= {}", embIndex, allEmbeddings.size());
                    break;
                }
            }
        }

        // Persist embeddings with NULL vectors
        embeddingRepository.saveAll(allEmbeddings);
        log.debug("Persisted {} embeddings (vectors=NULL) across {} chapters",
                allEmbeddings.size(), chapters.size());

        return SplitResult.builder()
                .chapters(chapters)
                .embeddings(allEmbeddings)
                .build();
    }

    /**
     * Creates embeddings for a single chapter.
     *
     * <p><b>Revision 1.2:</b> Implements real chunking logic:</p>
     * <ul>
     *   <li>If chapter ≤ 2000 tokens: Create 1 TRECHO only</li>
     *   <li>If chapter > 2000 tokens: Split into chunks + optional RESUMO</li>
     *   <li>If chapter > 2500 tokens: Create RESUMO first, then chunks</li>
     * </ul>
     * 
     * @TODO implement use of GenerationFlag
     * 
     * @param chapter the chapter entity
     * @param chapterDTO the chapter DTO with content
     * @param documento the parent document
     * @param library the library configuration
     * @param llmContext LLM context for token counting and summarization
     * @param generationFlag generation flag for embedding strategy
     * 
     * @return list of embeddings (with NULL vectors)
     */
    private List<DocChunk> createChapterEmbeddings( Chapter chapter,
                                                    ChapterDTO chapterDTO,
                                                    Documento documento,
                                                    LibraryDTO library,
                                                    LLMContext llmContext,
                                                    GenerationFlag generationFlag
                                                    ) throws Exception 
    {

        List<DocChunk> embeddings = new ArrayList<>();

        // Count tokens in chapter
        int chapterTokens = llmContext.tokenCount(chapterDTO.getConteudo(), TOKEN_MODEL);
        log.debug("Chapter '{}' has {} tokens", chapter.getTitulo(), chapterTokens);

        // CASE 1: Small chapter (≤ 512 tokens) - Create single TRECHO
        if (chapterTokens <= IDEAL_CHUNK_SIZE_TOKENS) {
            log.debug("Chapter is small (≤{} tokens), creating single TRECHO", IDEAL_CHUNK_SIZE_TOKENS);
            DocChunk trecho = criarChunkUnicoChapter( chapterDTO,
                                                documento,
                                                1 // orderChapter
                                        	);
            embeddings.add(trecho);
            return embeddings;
        }

        // CASE 2: Large chapter (> 2000 tokens) - Split into chunks
        log.debug("Chapter is large (>{} tokens), splitting into chunks", IDEAL_CHUNK_SIZE_TOKENS);

        // Step 1: Generate additional RESUMO if chapter > 2500 tokens
        if (chapterTokens > SUMMARY_THRESHOLD_TOKENS) {
            log.debug("Chapter exceeds summary threshold ({}), generating RESUMO", 
        	    	SUMMARY_THRESHOLD_TOKENS);
            try {
                DocChunk resumo = criarResumo(
                        chapterDTO,
                        documento,
                        llmContext
                );
                embeddings.add(resumo);
            } catch (Exception e) {
                log.warn("Failed to generate RESUMO for chapter '{}': {}",
                        chapter.getTitulo(), e.getMessage());
                // Continue without RESUMO
            }
        }

        // Step 2: Split chapter into chunks using SplitterGenerico
        // Get SplitterGenerico via Factory
        SplitterGenerico splitter = splitterFactory.createGenericSplitter(library);

        // Split chapter into DocumentEmbeddingDTOs
        List<DocChunkDTO> chunkDTOs = splitter.splitChapterIntoChunks(chapterDTO);

        log.debug("Chapter split into {} chunks", chunkDTOs.size());

        // Step 3: Convert DTOs to entities
        int orderChapter = 1;
        for (DocChunkDTO chunkDTO : chunkDTOs) {
            DocChunk trecho = DocChunk.builder()
                    .libraryId(documento.getBibliotecaId())
                    .documentoId(documento.getId())
                    .chapterId(null) // Will be set after chapter persistence
                    .tipoEmbedding(chunkDTO.getTipoEmbedding())
                    .texto(chunkDTO.getTrechoTexto())
                    .orderChapter(orderChapter)
                    .embeddingVector(null) // NULL - calculated later
                    .build();          
         
            // Add metadata
            var meta = trecho.getMetadados();
            meta.addMetadata(chapterDTO.getMetadados());
            meta.addMetadata("chunk_index", orderChapter);
            meta.addMetadata("total_chunks", chunkDTOs.size());
            meta.addMetadata("tokens", RagUtils.countTokensFast(chunkDTO.getTrechoTexto()));
            meta.addMetadata("parent_chapter_title", chapterDTO.getTitulo());
            meta.addMetadata("is_single_chunk", false);            
            
            embeddings.add(trecho);
            orderChapter++;
        }

        log.debug("Created {} embeddings for chapter '{}'", embeddings.size(), chapter.getTitulo());
        return embeddings;
    }

    /**
     * Creates a RESUMO embedding for a chapter by generating a summary via LLM.
     *
     * <p>The summary is generated using the LLM service and is limited to
     * {@value SUMMARY_MAX_TOKENS} tokens. The RESUMO is useful for providing
     * a high-level overview of large chapters.</p>
     *
     * @param chapterDTO the chapter to summarize
     * @param documento the parent document
     * @param llmContext LLM context for generating the summary
     * @return RESUMO embedding with NULL vector
     * @throws Exception if summary generation fails
     */
    private DocChunk criarResumo(
            ChapterDTO chapterDTO,
            Documento documento,
            LLMContext llmContext) throws Exception {

        log.debug("Generating summary for chapter: {}", chapterDTO.getTitulo());

        // Build summary prompt
        String systemPrompt = String.format("Você é um assistente especializado em resumir conteúdo técnico. "
                + "Crie um resumo conciso e informativo do texto fornecido, com até %d tokens, "
                + "capturando os pontos principais, as entidades citadas e os conceitos chave.\n"
                + "Use os metadados fornecidos para apoiar a sumarização,"
                + " mas não é necessário inclui-los no resumo.\n",
                SUMMARY_MAX_TOKENS
                );

        String userPrompt = String.format(                
                "Título: %s\n\n"
                + "Conteúdo:\n%s"
                +"\n\n METADADOS do texto acima: %s",             
                chapterDTO.getTitulo(),
                chapterDTO.getConteudo(),
                buildMetadataText(chapterDTO)
        );

        MapParam extraParams = new MapParam();
        extraParams.maxTokens(SUMMARY_MAX_TOKENS);
        extraParams.temperature(0.45f); // More focused summary
        extraParams.repeat_penalty(1.1f);
       // extraParams.top_p(0.95f);
       // extraParams.top_k(40);
       // extraParams.min_p(0.05f);
        extraParams.model(llmContext.getModelName());
        
        
	// Generate summary
        String summary = llmContext.generateCompletion(systemPrompt, userPrompt, extraParams );
        
        if(chapterDTO.getTitulo()!=null && !chapterDTO.getTitulo().isEmpty()) {
            summary = "Resumo do Capitulo  " + chapterDTO.getTitulo() + "\n" +  summary.trim();
        }

        log.debug("Generated summary with {} characters", summary.length());

        // Create RESUMO embedding
        DocChunk resumo = DocChunk.builder()
                .libraryId(documento.getBibliotecaId())
                .documentoId(documento.getId())
                .chapterId(null) // Will be set after chapter persistence
                .tipoEmbedding(TipoEmbedding.RESUMO)
                .texto(summary)
                .metadados(new bor.tools.simplerag.entity.MetaDoc(chapterDTO.getMetadados())) // Copy chapter metadata
                .orderChapter(0) // RESUMO comes before chunks
                .embeddingVector(null) // NULL - calculated later
                .build();

        // Add metadata
        resumo.getMetadados().put("tokens", llmContext.tokenCount(summary, TOKEN_MODEL));
        resumo.getMetadados().put("is_summary", true);
        resumo.getMetadados().put("original_chapter_title", chapterDTO.getTitulo());
        resumo.getMetadados().put("summary_generated_at", java.time.Instant.now().toString());

        return resumo;
    }

    /**
     * Creates a single CAPITULO embedding for a small chapter.
     *
     * <p>Used when the chapter is small enough (≤ 2000 tokens) to be
     * represented as a single chunk without splitting.</p>
     *
     * @param chapterDTO the chapter content
     * @param documento the parent document
     * @param orderChapter the order of this chunk within the chapter
     * @return TRECHO embedding with NULL vector
     */
    private DocChunk criarChunkUnicoChapter(
            ChapterDTO chapterDTO,
            Documento documento,
            int orderChapter) {

        DocChunk trecho = DocChunk.builder()
                .libraryId(documento.getBibliotecaId())
                .documentoId(documento.getId())
                .chapterId(null) // Will be set after chapter persistence
                .tipoEmbedding(TipoEmbedding.CAPITULO)
                .texto(chapterDTO.getConteudo())
                .orderChapter(orderChapter)
                .embeddingVector(null) // NULL - calculated later
                .build();

        // Add metadata
        trecho.getMetadados().addMetadata(chapterDTO.getMetadados());
        trecho.getMetadados().put("is_single_chunk", true);
        trecho.getMetadados().put("chapter_title", chapterDTO.getTitulo());
        trecho.getMetadados().put("tokens", RagUtils.countTokensFast(chapterDTO.getConteudo()));
        
        return trecho;
    }

    // ========== ETAPA 2.3: Calculate and Update Embeddings ==========

    /**
     * Calculates embeddings for all texts and updates database.
     *
     * <p><b>REVISED v1.1:</b></p>
     * <ul>
     *   <li>Batch size: up to 10 texts (was 5)</li>
     *   <li>Respects dynamic contextLength from ModelEmbedding</li>
     *   <li>Handles oversized texts: summarize if >2% over, truncate if ≤2%</li>
     * </ul>
     *
     * @TODO implement use of GenerationFlag
     * 
     * @param embeddings list of embeddings with NULL vectors
     * @param embeddingContext embedding context for generation
     * @param llmContext LLM context for summarizing oversized texts
     * @param generationFlag generation flag for embedding strategy
     * 
     * @return number of successfully processed embeddings
     */
    private int calculateAndUpdateEmbeddings(
                                            List<DocChunk> embeddings,
                                            EmbeddingContext embeddingContext,
                                            LLMContext llmContext,
                                            GenerationFlag generationFlag) 
    {
        int processed = 0;
        int contextLength = embeddingContext.getContextLength();

        log.debug("Processing {} embeddings with contextLength={}", embeddings.size(), contextLength);

        // Group into batches
        List<List<DocChunk>> batches = createBatches(embeddings, BATCH_SIZE);

        log.debug("Created {} batches (max {} texts per batch)", batches.size(), BATCH_SIZE);

        for (int batchNum = 0; batchNum < batches.size(); batchNum++) {
            List<DocChunk> batch = batches.get(batchNum);

            try {
                log.debug("Processing batch {}/{} with {} texts",
                        batchNum + 1, batches.size(), batch.size());

                // Prepare texts, handling oversized ones
                String[] texts = new String[batch.size()];                
                
                for (int i = 0; i < batch.size(); i++) {
                    texts[i] = handleOversizedText(
                                                    batch.get(i),
                                                    contextLength,
                                                    llmContext,
                                                    generationFlag);
                }

                // Generate embeddings
                List<float[]> vectors = 
                	embeddingContext.generateEmbeddingsBatch( texts, Embeddings_Op.DOCUMENT);

                // Update each embedding (fault-tolerant)
                
                for (int i = 0; i < batch.size(); i++) {
                    try {
                        embeddingRepository.updateEmbeddingVector(
                                batch.get(i).getId(),
                                vectors.get(i));
                        processed++;

                    } catch (Exception e) {
                        log.error("Failed to update embedding #{}: {}",
                                batch.get(i).getId(), e.getMessage());
                        e.printStackTrace();
                        // Continue with next
                    }
                }

                log.debug("Batch {}/{} completed successfully",
                        batchNum + 1, batches.size());

            } catch (Exception e) {
                log.error("Batch {}/{} failed: {}",
                        batchNum + 1, batches.size(), e.getMessage());
                e.printStackTrace();
                // Continue with next batch
            }
        }

        return processed;
    }

    /**
     * Handles text that exceeds model's context length.
     *
     * <p><b>REVISED v1.1:</b> If text exceeds contextLength:</p>
     * <ul>
     *   <li>If excedente > 5%: Generate summary via LLM</li>
     *   <li>If excedente ≤ 5%: Truncate text</li>
     * </ul>
     *
     * @param docChunk the embedding with text
     * @param contextLength maximum tokens allowed
     * @param llmContext LLM context for summarization
     * @param generationFlag generation flag for text generation
     * 
     * @return processed text (original, summarized, or truncated)
     * 
     * @see GenerationFlag
     * @see #generateText(DocChunk, GenerationFlag)
     * @see #buildTextWithMetadata(DocChunk)
     */
    private String handleOversizedText( DocChunk docChunk,
            				int contextLength,
            				LLMContext llmContext,
            				GenerationFlag generationFlag) 
    {
        // Generate initial text based on generation flag
        String text = generateText(docChunk, generationFlag);        

        try {
            int tokens = llmContext.tokenCount(text, TOKEN_MODEL);

            if (tokens > contextLength) {
                int excedente = tokens - contextLength;
                float percentual = (excedente * 100.0f) / tokens;

                log.debug("Text exceeds context length: {} > {} ({:.1f}% over)",
                        tokens, contextLength, percentual);

                if (percentual > OVERSIZE_THRESHOLD_PERCENT) {
                    // Generate summary via LLM
                    log.debug("Generating summary for oversized text ({}% over limit)", 
                	    	String.format("%.1f", percentual));
                    
                    // Generate metadata before summarization
                    text = buildTextWithMetadata(docChunk);
                    
                    MapParam extraParams = new MapParam();
                    extraParams.maxTokens(SUMMARY_MAX_TOKENS);
                    extraParams.temperature(0.3f);
                    extraParams.repeat_penalty(1.2f);
                    extraParams.model(llmContext.getModelName());                    
                    
                    
		    String summary = llmContext.generateCompletion(
                            "Resuma o seguinte texto de forma concisa, "
                            +"mantendo as informações principais, "
                            + "tais como fatos, entidades citadas, datas e ações.\n",
                            text,
                            extraParams);

                    // Store original truncation flag in metadata                  
                    docChunk.getMetadados().put("texto_original_truncado", true);
                    docChunk.getMetadados().put("resumo_gerado", true);
                    docChunk.getMetadados().put("tokens_originais", tokens);
                    return summary;

                } else {
                    // Truncate (excedente <= 2%)
                    log.debug("Truncating text ({}% over limit, ≤2%)",
                            String.format("%.1f", percentual));

                    int maxChars = contextLength * 4; // Approx 4 chars/token
                    String truncated = text.substring(0, Math.min(maxChars, text.length()));
                    docChunk.getMetadados().put("texto_truncado", true);
                    docChunk.getMetadados().put("tokens_originais", tokens);
                    return truncated;
                }
            }

            return text;

        } catch (Exception e) {
            log.warn("Token counting failed for embedding #{}, using text as-is: {}",
                    docChunk.getId(), e.getMessage());
            return text;
        }
    }

    // ========== Helper Methods ==========

    /**
     * Generates text based on the specified generation flag. <br>
     * 
     * Currently supports:
     * <li>ONLY_TEXT: returns only the text content</li>
     * <li>FULL_TEXT_METADATA: returns text with metadata appended</li>	
     * <li>ONLY_METADATA: returns only metadata as text</li>
     * <li> all others defaults to ONLY_TEXT 	
     * 
     * @param docChunk the document chunk
     * @param generationFlag the generation flag
     * @return generated text
     */
    private String generateText(DocChunk docChunk, GenerationFlag generationFlag) {

	switch(generationFlag) {
	case ONLY_TEXT:
		return docChunk.getTexto();
	case FULL_TEXT_METADATA:
		return buildTextWithMetadata(docChunk);
	case ONLY_METADATA:
		return buildMetadataText(docChunk);	
	default:
	    	return docChunk.getTexto();
	}
    }

    /**
     * Creates batches of embeddings for batch processing.
     *
     * @param embeddings all embeddings to process
     * @param batchSize maximum size per batch
     * @return list of batches
     */
    private List<List<DocChunk>> createBatches(
            List<DocChunk> embeddings,
            int batchSize) {

        List<List<DocChunk>> batches = new ArrayList<>();

        for (int i = 0; i < embeddings.size(); i += batchSize) {
            int end = Math.min(i + batchSize, embeddings.size());
            batches.add(embeddings.subList(i, end));
        }

        return batches;
    }

    /**
     * Formats duration for human-readable output.
     *
     * @param duration the duration
     * @return formatted string (e.g., "12.5s")
     */
    private String formatDuration(Duration duration) {
        double seconds = duration.toMillis() / 1000.0;
        return String.format("%.1fs", seconds);
    }

    // ========== PHASE 2: Document Enrichment ==========

    /**
     * Enriches a document with Q&A and/or summary embeddings (Phase 2 processing).
     *
     * <p>This is an optional post-processing step that generates additional embeddings
     * beyond the standard chapter/chunk embeddings. Enrichment improves retrieval quality
     * but uses more expensive completion models.</p>
     *
     * <p><b>Prerequisites:</b></p>
     * <ul>
     *   <li>Document must have been processed (Phase 1) to have chapters</li>
     *   <li>Library must have configured completion models for Q&A/summary generation</li>
     * </ul>
     *
     * <p><b>Processing Flow:</b></p>
     * <ol>
     *   <li>Load document chapters from database</li>
     *   <li>For each chapter: generate Q&A and/or summary embeddings</li>
     *   <li>Persist embeddings with tipo=PERGUNTAS_RESPOSTAS or RESUMO</li>
     *   <li>Return statistics</li>
     * </ol>
     *
     * <p><b>Fault Tolerance:</b> By default, continues processing if individual chapters fail
     * (controlled by {@link EnrichmentOptions#isContinueOnError()}).</p>
     *
     * @param documento the document to enrich (must have chapters)
     * @param library the library configuration
     * @param options enrichment configuration (Q&A, summary, etc.)
     * @return CompletableFuture with enrichment results and statistics
     * @since 0.0.3
     */
    @Async
    public CompletableFuture<EnrichmentResult> enrichDocument( Documento documento,
                                                               LibraryDTO library,
                                                               EnrichmentOptions options) 
    {

        log.info("Starting document enrichment: docId={}, library={}, qa={}, summary={}",
                documento.getId(), library.getNome(), options.isGenerateQA(), options.isGenerateSummary());

        Instant startTime = Instant.now();

        try {
            // Validate options
            String validationError = options.validate();
            if (validationError != null) {
                log.error("Invalid enrichment options: {}", validationError);
                return CompletableFuture.completedFuture(EnrichmentResult.builder()
                        .documentId(documento.getId())
                        .success(false)
                        .errorMessage("Invalid options: " + validationError)
                        .duration(formatDuration(Duration.between(startTime, Instant.now())))
                        .build());
            }

            // Load chapters for this document
            List<Chapter> chapters = 
        	    chapterRepository.findByDocumentoIdOrderByOrdemDoc(documento.getId());

            if (chapters.isEmpty()) {
                log.warn("No chapters found for document {}."
                	+ " Document must be processed (Phase 1) before enrichment.",
                        documento.getId());
                
                return CompletableFuture.completedFuture(EnrichmentResult.builder()
                        .documentId(documento.getId())
                        .success(false)
                        .errorMessage("No chapters found. Run Phase 1 processing first.")
                        .duration(formatDuration(Duration.between(startTime, Instant.now())))
                        .build());
            }

            log.debug("Found {} chapters to enrich", chapters.size());

            
            String llmModelName = options.getLLMmodelName()!=null? options.getLLMmodelName() :
        	                   library.getCompletionQAModel();
            
            if(llmModelName==null || llmModelName.isEmpty()) {
        	llmModelName = llmServiceManager.getDefaultCompletionModelName();
            }
            
            // Create contexts
            EmbeddingContext embeddingContext = EmbeddingContext.builder()
                    .library(library)
                    .embeddingModelName(library.getEmbeddingModel())
                    .embeddingDimension(library.getEmbeddingDimension())
                    .completionQAModelName(llmModelName)
                    .build();

            // Process each chapter
            EnrichmentResult result = EnrichmentResult.builder()
                    .documentId(documento.getId())
                    .chaptersProcessed(chapters.size())
                    .build();

            int totalQA = 0;
            int totalSummary = 0;

            for (Chapter chapter : chapters) {
                try {
                    ChapterEnrichmentResult chapterResult = enrichChapter(
                            chapter,
                            documento,
                            library,
                            embeddingContext,
                            options);

                    totalQA += chapterResult.qaEmbeddingsCount;
                    totalSummary += chapterResult.summaryEmbeddingsCount;

                } catch (Exception e) {
                    log.error("Failed to enrich chapter {}: {}", chapter.getId(), e.getMessage(), e);

                    if (options.isContinueOnError()) {
                        result.addChapterError(chapter.getId(), e.getMessage());
                    } else {
                        // Fail fast mode
                        throw new RuntimeException("Chapter enrichment failed: " + e.getMessage(), e);
                    }
                }
            }

            Duration duration = Duration.between(startTime, Instant.now());

            result.setQaEmbeddingsGenerated(totalQA);
            result.setSummaryEmbeddingsGenerated(totalSummary);
            result.setDuration(formatDuration(duration));
            result.setSuccess(true);

            log.info("Document enrichment completed: {}", result.getSummary());

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("Document enrichment failed for docId={}: {}", documento.getId(), e.getMessage(), e);

            Duration duration = Duration.between(startTime, Instant.now());

            EnrichmentResult result = EnrichmentResult.builder()
                    .documentId(documento.getId())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .duration(formatDuration(duration))
                    .build();

            return CompletableFuture.completedFuture(result);
        }
    }

    /**
     * Enriches a single chapter with Q&A and/or summary embeddings.
     *
     * @param chapter the chapter to enrich
     * @param documento parent document
     * @param library library configuration
     * @param embeddingContext embedding context
     * @param options enrichment options
     * @return result with count of embeddings generated
     */
    private ChapterEnrichmentResult enrichChapter(
                                        Chapter chapter,
                                        Documento documento,
                                        LibraryDTO library,
                                        EmbeddingContext embeddingContext,
                                        EnrichmentOptions options) 
    {
        log.debug("Enriching chapter {}: {}", chapter.getId(), chapter.getTitulo());
        int qaCount = 0;
        int summaryCount = 0;

        // Convert Chapter entity to ChapterDTO
        ChapterDTO chapterDTO = ChapterDTO.from(chapter);

        // Generate Q&A embeddings if requested
        if (options.isGenerateQA()) {
            try {
                EmbeddingRequest qaRequest = EmbeddingRequest.builder()
                        .chapter(chapterDTO)
                        .context(embeddingContext)
                        .operation(Embeddings_Op.DOCUMENT)
                        .tipoEmbedding(TipoEmbedding.PERGUNTAS_RESPOSTAS)
                        .numberOfQAPairs(options.getNumberOfQAPairs())
                        .documentId(documento.getId())
                        .chapterId(chapter.getId())
                        .build();

                List<DocChunkDTO> qaEmbeddings = qaEmbeddingStrategy.generate(qaRequest);

                // Persist Q&A embeddings
                for (DocChunkDTO embDTO : qaEmbeddings) {
                    DocChunk embedding = toEmbeddingEntity(embDTO, documento, chapter);
                    embeddingRepository.save(embedding);
                    qaCount++;
                }

                log.debug("Generated {} Q&A embeddings for chapter {}", qaCount, chapter.getId());

            } catch (Exception e) {
                log.error("Failed to generate Q&A for chapter {}: {}", chapter.getId(), e.getMessage());
                throw new RuntimeException("Q&A generation failed", e);
            }
        }

        // Generate summary embedding if requested
        if (options.isGenerateSummary()) {
            try {
                EmbeddingRequest summaryRequest = EmbeddingRequest.builder()
                        .chapter(chapterDTO)
                        .context(embeddingContext)
                        .operation(Embeddings_Op.DOCUMENT)
                        .tipoEmbedding(TipoEmbedding.RESUMO)
                        .maxSummaryLength(options.getMaxSummaryLength())
                        .customSummaryInstructions(options.getSummaryInstructions())
                        .documentId(documento.getId())
                        .chapterId(chapter.getId())
                        .build();

                List<DocChunkDTO> summaryEmbeddings = summaryEmbeddingStrategy.generate(summaryRequest);

                // Persist summary embeddings
                for (DocChunkDTO embDTO : summaryEmbeddings) {
                    DocChunk embedding = toEmbeddingEntity(embDTO, documento, chapter);
                    embeddingRepository.save(embedding);
                    summaryCount++;
                }

                log.debug("Generated {} summary embeddings for chapter {}", summaryCount, chapter.getId());

            } catch (Exception e) {
                log.error("Failed to generate summary for chapter {}: {}", chapter.getId(), e.getMessage());
                throw new RuntimeException("Summary generation failed", e);
            }
        }

        return new ChapterEnrichmentResult(qaCount, summaryCount);
    }

    /**
     * Converts DocChunkDTO to DocChunk entity.
     */
    private DocChunk toEmbeddingEntity(DocChunkDTO dto, Documento documento, Chapter chapter) {
        return DocChunk.builder()
                .libraryId(documento.getBibliotecaId())
                .documentoId(documento.getId())
                .chapterId(chapter.getId())
                .tipoEmbedding(dto.getTipoEmbedding())
                .texto(dto.getTrechoTexto())
                .embeddingVector(dto.getEmbeddingVector())
                .metadados(dto.getMetadados())
                .build();
    }

    /**
     * Builds string text with metadata only
     */
    private String buildMetadataText(ChapterDTO chapter) {
	StringBuilder builder = new StringBuilder();

	if (chapter.getMetadados() != null) {
	    // Add most relevant metadata
	    chapter.getMetadados().forEach((key, value) -> {
		if (ignoredKeys.contains(key.toLowerCase()) == false && value != null
			&& !value.toString().trim().isEmpty()) {
		    builder.append(key).append(": ").append(value).append("\n");
		}
	    });
	}
	return builder.toString();
    }
    
    /**
     * Builds string text with metadata only.
     */
    private String buildMetadataText(DocChunk docChunk) {
	StringBuilder builder = new StringBuilder();

	if (docChunk.getMetadados() != null) {
	    // Add most relevant metadata
	    docChunk.getMetadados().forEach((key, value) -> {
		if (ignoredKeys.contains(key.toLowerCase()) == false && value != null
			&& !value.toString().trim().isEmpty()) {
		    builder.append(key).append(": ").append(value).append("\n");
		}
	    });
	}
	return builder.toString();
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
     * Builds text with metadata included
     *
     * @param docChunk
     * @return
     */
    private String buildTextWithMetadata(DocChunk docChunk) {
        StringBuilder builder = new StringBuilder();

        var metaDoc = docChunk.getMetadados();
        String title = metaDoc.getCapitulo();
        // Add title       
        if (title != null) {
            builder.append("Título: ")
            	   .append(title)
            	   .append("\n\n");
        }

        // Add relevant metadata
        if (metaDoc != null) {
            String metadataText = buildMetadataText(docChunk);
            if (!metadataText.isEmpty()) {
        	builder.append("\n**METADADOS DO DOCUMENTO**\n");
                builder.append(metadataText).append("\n\n");
            }
        }

        builder.append("\n---\nConteúdo:\n");
        // Add main content
        builder.append(docChunk.getTexto());

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

	/**
     * Normalizes embedding vector to specified dimension
     * @param embedding
     * @param context
     * @return
     */
    private float[] normalizeEmbedding(float[] embedding, EmbeddingContext context) {
		Integer length = (context != null && context.getEmbeddingDimension() != null) ?
					context.getEmbeddingDimension() 
					: DEFAULT_EMBED_DIMENSION;	
		
		// Adjust length if necessary
		if (length!=null && embedding != null && embedding.length != length) {
			log.debug("Normalizing embedding from length {} to {}", embedding.length, length);
			// normalize source first - just in case
			embedding = bor.tools.simplerag.util.VectorUtil.normalize(embedding);
			// then resize
			float[] normalized = new float[length];
			System.arraycopy(embedding, 0,
					normalized, 0, 
					Math.min(embedding.length, length));
			embedding = normalized;	
		}
		// Normalize vector. always
		return bor.tools.simplerag.util.VectorUtil.normalize(embedding);
    }

    
    
    
    /**
     * Helper class for chapter enrichment result.
     */
    private static class ChapterEnrichmentResult {
        final int qaEmbeddingsCount;
        final int summaryEmbeddingsCount;

        ChapterEnrichmentResult(int qaCount, int summaryCount) {
            this.qaEmbeddingsCount = qaCount;
            this.summaryEmbeddingsCount = summaryCount;
        }
    } // class ChapterEnrichmentResult

    // ========== Result Classes ==========

    /**
     * Result of split operation.
     */
    @Data
    @Builder
    private static class SplitResult {
        private List<Chapter> chapters;
        private List<DocChunk> embeddings;

        public int getChaptersCount() {
            return chapters != null ? chapters.size() : 0;
        }

        public int getEmbeddingsCount() {
            return embeddings != null ? embeddings.size() : 0;
        }
    } // class SplitResult

    /**
     * Result of document processing.
     */
    @Data
    @Builder
    public static class ProcessingResult {
        private Integer documentId;
        private Integer chaptersCount;
        private Integer embeddingsCount;
        private Integer embeddingsProcessed;
        private Integer embeddingsFailed;
        private String duration;
        private boolean success;
        private String errorMessage;
    } // class ProcessingResult
} // class DocumentProcessingService
