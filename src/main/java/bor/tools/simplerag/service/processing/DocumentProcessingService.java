package bor.tools.simplerag.service.processing;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bor.tools.simplellm.Embeddings_Op;
import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.entity.Chapter;
import bor.tools.simplerag.entity.DocumentEmbedding;
import bor.tools.simplerag.entity.Documento;
import bor.tools.simplerag.entity.enums.TipoConteudo;
import bor.tools.simplerag.entity.enums.TipoEmbedding;
import bor.tools.simplerag.repository.ChapterRepository;
import bor.tools.simplerag.repository.DocEmbeddingJdbcRepository;
import bor.tools.simplerag.service.LibraryService;
import bor.tools.simplerag.service.llm.LLMServiceManager;
import bor.tools.simplerag.service.processing.context.EmbeddingContext;
import bor.tools.simplerag.service.processing.context.LLMContext;
import bor.tools.simplerag.dto.DocumentoWithAssociationDTO;
import bor.tools.splitter.AbstractSplitter;
import bor.tools.splitter.ContentSplitter;
import bor.tools.splitter.DocumentRouter;
import bor.tools.splitter.SplitterFactory;
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

    // ========== Dependencies ==========

    private final DocumentRouter documentRouter;
    private final SplitterFactory splitterFactory;
    private final ContentSplitter contentSplitter;
    private final ChapterRepository chapterRepository;
    private final DocEmbeddingJdbcRepository embeddingRepository;
    private final LLMServiceManager llmServiceManager;
    private final LibraryService libraryService;

    // ========== Constants (REVISED v1.1) ==========

    /**
     * Maximum number of texts per batch (REVISED: was 5, now 10).
     */
    private static final int BATCH_SIZE = 10;

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
    private static final int SUMMARY_MAX_TOKENS = 1024;

    /**
     * Ideal chunk size for chapter splitting (aligns with ContentSplitter.IDEAL_TOKENS).
     */
    private static final int IDEAL_CHUNK_SIZE_TOKENS = 2000;

    /**
     * Percentage threshold for deciding to summarize vs truncate oversized text.
     * If text exceeds context length by more than this percentage, generate summary.
     * Otherwise, truncate.
     */
    private static final double OVERSIZE_THRESHOLD_PERCENT = 2.0;

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
    public CompletableFuture<ProcessingResult> processDocument(
            Documento documento,
            LibraryDTO library) {

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
                    embeddingContext.getModelName(),
                    embeddingContext.getContextLength());

            // ETAPA 2.2: Split and persist (uses llmContext for token counting)
            log.debug("Splitting document into chapters and chunks...");
            SplitResult splitResult = splitAndPersist(documento, library, llmContext);

            log.info("Split completed: {} chapters, {} embeddings created",
                    splitResult.getChaptersCount(),
                    splitResult.getEmbeddingsCount());

            // ETAPA 2.3: Calculate embeddings and update vectors
            log.debug("Calculating embeddings in batches...");
            int processed = calculateAndUpdateEmbeddings(
                    splitResult.getEmbeddings(),
                    embeddingContext,
                    llmContext);

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
    protected SplitResult splitAndPersist(
            Documento documento,
            LibraryDTO library,
            LLMContext llmContext) throws Exception {

        // Detect content type
        TipoConteudo tipoConteudo = documentRouter.detectContentType(documento.getConteudoMarkdown());
        log.debug("Detected content type: {}", tipoConteudo);

        // Create appropriate splitter
        AbstractSplitter splitter = splitterFactory.createSplitter(tipoConteudo, library);

        // Convert to DocumentoWithAssociationDTO for splitter
        DocumentoWithAssociationDTO documentoDTO = DocumentoWithAssociationDTO.builder()
                .id(documento.getId())
                .bibliotecaId(documento.getBibliotecaId())
                .titulo(documento.getTitulo())
                .conteudoMarkdown(documento.getConteudoMarkdown())
                .flagVigente(documento.getFlagVigente())
                .dataPublicacao(documento.getDataPublicacao())
                .tokensTotal(documento.getTokensTotal())
                .metadados(documento.getMetadados())
                .biblioteca(library)
                .build();

        // Split document into chapters
        List<ChapterDTO> chapterDTOs = splitter.splitDocumento(documentoDTO);
        log.debug("Document split into {} chapters", chapterDTOs.size());

        // Convert to entities and persist
        List<Chapter> chapters = new ArrayList<>();
        List<DocumentEmbedding> allEmbeddings = new ArrayList<>();

        int ordem = 0;
        for (ChapterDTO chapterDTO : chapterDTOs) {
            // Create chapter entity
            Chapter chapter = Chapter.builder()
                    .documentoId(documento.getId())
                    .titulo(chapterDTO.getTitulo())
                    .conteudo(chapterDTO.getConteudo())
                    .ordemDoc(ordem++)
                    .build();

            chapters.add(chapter);

            // Create embeddings for this chapter (will be persisted after chapter)
            List<DocumentEmbedding> chapterEmbeddings = createChapterEmbeddings(
                    chapter,
                    chapterDTO,
                    documento,
                    library,
                    llmContext);

            allEmbeddings.addAll(chapterEmbeddings);
        }

        // Persist chapters (generates IDs)
        chapterRepository.saveAll(chapters);
        log.debug("Persisted {} chapters", chapters.size());

        // Update chapter IDs in embeddings
        int embIndex = 0;
        for (Chapter chapter : chapters) {
            // Find embeddings belonging to this chapter
            while (embIndex < allEmbeddings.size()) {
                DocumentEmbedding emb = allEmbeddings.get(embIndex);
                if (emb.getChapterId() == null) {
                    emb.setChapterId(chapter.getId());
                    embIndex++;
                } else {
                    break;
                }
            }
        }

        // Persist embeddings with NULL vectors
        embeddingRepository.saveAll(allEmbeddings);
        log.debug("Persisted {} embeddings (vectors=NULL)", allEmbeddings.size());

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
     * @param chapter the chapter entity
     * @param chapterDTO the chapter DTO with content
     * @param documento the parent document
     * @param library the library configuration
     * @param llmContext LLM context for token counting and summarization
     * @return list of embeddings (with NULL vectors)
     */
    private List<DocumentEmbedding> createChapterEmbeddings(
            Chapter chapter,
            ChapterDTO chapterDTO,
            Documento documento,
            LibraryDTO library,
            LLMContext llmContext) throws Exception {

        List<DocumentEmbedding> embeddings = new ArrayList<>();

        // Count tokens in chapter
        int chapterTokens = llmContext.tokenCount(chapterDTO.getConteudo(), TOKEN_MODEL);
        log.debug("Chapter '{}' has {} tokens", chapter.getTitulo(), chapterTokens);

        // CASE 1: Small chapter (≤ 2000 tokens) - Create single TRECHO
        if (chapterTokens <= IDEAL_CHUNK_SIZE_TOKENS) {
            log.debug("Chapter is small (≤{} tokens), creating single TRECHO", IDEAL_CHUNK_SIZE_TOKENS);
            DocumentEmbedding trecho = criarTrechoUnico(
                    chapterDTO,
                    documento,
                    0 // orderChapter
            );
            embeddings.add(trecho);
            return embeddings;
        }

        // CASE 2: Large chapter (> 2000 tokens) - Split into chunks
        log.debug("Chapter is large (>{} tokens), splitting into chunks", IDEAL_CHUNK_SIZE_TOKENS);

        // Step 1: Generate RESUMO if chapter > 2500 tokens
        if (chapterTokens > SUMMARY_THRESHOLD_TOKENS) {
            log.debug("Chapter exceeds summary threshold ({}), generating RESUMO", SUMMARY_THRESHOLD_TOKENS);
            try {
                DocumentEmbedding resumo = criarResumo(
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

        // Step 2: Split chapter into chunks using ContentSplitter
        List<ChapterDTO> chunks = contentSplitter.splitContent(
                chapterDTO.getConteudo(),
                true // Assume markdown format for better splitting
        );

        log.debug("Chapter split into {} chunks", chunks.size());

        // Step 3: Create TRECHO embedding for each chunk
        int orderChapter = 0;
        for (ChapterDTO chunk : chunks) {
            DocumentEmbedding trecho = DocumentEmbedding.builder()
                    .libraryId(documento.getBibliotecaId())
                    .documentoId(documento.getId())
                    .chapterId(null) // Will be set after chapter persistence
                    .tipoEmbedding(TipoEmbedding.TRECHO)
                    .texto(chunk.getConteudo())
                    .orderChapter(orderChapter)
                    .embeddingVector(null) // NULL - calculated later
                    .build();

            // Add metadata
            trecho.getMetadados().put("chunk_index", orderChapter);
            trecho.getMetadados().put("total_chunks", chunks.size());
            trecho.getMetadados().put("parent_chapter_title", chapterDTO.getTitulo());
            trecho.getMetadados().put("chunk_title", chunk.getTitulo());

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
    private DocumentEmbedding criarResumo(
            ChapterDTO chapterDTO,
            Documento documento,
            LLMContext llmContext) throws Exception {

        log.debug("Generating summary for chapter: {}", chapterDTO.getTitulo());

        // Build summary prompt
        String systemPrompt = "Você é um assistente especializado em resumir conteúdo técnico. "
                + "Crie um resumo conciso e informativo do texto fornecido, "
                + "capturando os pontos principais e conceitos chave.";

        String userPrompt = String.format(
                "Resuma o seguinte capítulo em até %d tokens:\n\n"
                + "Título: %s\n\n"
                + "Conteúdo:\n%s",
                SUMMARY_MAX_TOKENS,
                chapterDTO.getTitulo(),
                chapterDTO.getConteudo()
        );

        // Generate summary
        String summary = llmContext.generateCompletion(systemPrompt, userPrompt);

        log.debug("Generated summary with {} characters", summary.length());

        // Create RESUMO embedding
        DocumentEmbedding resumo = DocumentEmbedding.builder()
                .libraryId(documento.getBibliotecaId())
                .documentoId(documento.getId())
                .chapterId(null) // Will be set after chapter persistence
                .tipoEmbedding(TipoEmbedding.RESUMO)
                .texto(summary)
                .orderChapter(-1) // RESUMO comes before chunks
                .embeddingVector(null) // NULL - calculated later
                .build();

        // Add metadata
        resumo.getMetadados().put("is_summary", true);
        resumo.getMetadados().put("original_chapter_title", chapterDTO.getTitulo());
        resumo.getMetadados().put("summary_generated_at", java.time.Instant.now().toString());

        return resumo;
    }

    /**
     * Creates a single TRECHO embedding for a small chapter.
     *
     * <p>Used when the chapter is small enough (≤ 2000 tokens) to be
     * represented as a single chunk without splitting.</p>
     *
     * @param chapterDTO the chapter content
     * @param documento the parent document
     * @param orderChapter the order of this chunk within the chapter
     * @return TRECHO embedding with NULL vector
     */
    private DocumentEmbedding criarTrechoUnico(
            ChapterDTO chapterDTO,
            Documento documento,
            int orderChapter) {

        DocumentEmbedding trecho = DocumentEmbedding.builder()
                .libraryId(documento.getBibliotecaId())
                .documentoId(documento.getId())
                .chapterId(null) // Will be set after chapter persistence
                .tipoEmbedding(TipoEmbedding.TRECHO)
                .texto(chapterDTO.getConteudo())
                .orderChapter(orderChapter)
                .embeddingVector(null) // NULL - calculated later
                .build();

        // Add metadata
        trecho.getMetadados().put("is_single_chunk", true);
        trecho.getMetadados().put("chapter_title", chapterDTO.getTitulo());

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
     * @param embeddings list of embeddings with NULL vectors
     * @param embeddingContext embedding context for generation
     * @param llmContext LLM context for summarizing oversized texts
     * @return number of successfully processed embeddings
     */
    private int calculateAndUpdateEmbeddings(
            List<DocumentEmbedding> embeddings,
            EmbeddingContext embeddingContext,
            LLMContext llmContext) {

        int processed = 0;
        int contextLength = embeddingContext.getContextLength();

        log.debug("Processing {} embeddings with contextLength={}",
                embeddings.size(), contextLength);

        // Group into batches
        List<List<DocumentEmbedding>> batches = createBatches(embeddings, BATCH_SIZE);

        log.debug("Created {} batches (max {} texts per batch)",
                batches.size(), BATCH_SIZE);

        for (int batchNum = 0; batchNum < batches.size(); batchNum++) {
            List<DocumentEmbedding> batch = batches.get(batchNum);

            try {
                log.debug("Processing batch {}/{} with {} texts",
                        batchNum + 1, batches.size(), batch.size());

                // Prepare texts, handling oversized ones
                String[] texts = new String[batch.size()];
                for (int i = 0; i < batch.size(); i++) {
                    texts[i] = handleOversizedText(
                            batch.get(i),
                            contextLength,
                            llmContext);
                }

                // Generate embeddings
                List<float[]> vectors = embeddingContext.generateEmbeddingsBatch(
                        texts,
                        Embeddings_Op.DOCUMENT);

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
     *   <li>If excedente > 2%: Generate summary via LLM</li>
     *   <li>If excedente ≤ 2%: Truncate text</li>
     * </ul>
     *
     * @param embedding the embedding with text
     * @param contextLength maximum tokens allowed
     * @param llmContext LLM context for summarization
     * @return processed text (original, summarized, or truncated)
     */
    private String handleOversizedText(
            DocumentEmbedding embedding,
            int contextLength,
            LLMContext llmContext) {

        String text = embedding.getTexto();

        try {
            int tokens = llmContext.tokenCount(text, TOKEN_MODEL);

            if (tokens > contextLength) {
                int excedente = tokens - contextLength;
                double percentual = (excedente * 100.0) / tokens;

                log.debug("Text exceeds context length: {} > {} ({:.1f}% over)",
                        tokens, contextLength, percentual);

                if (percentual > OVERSIZE_THRESHOLD_PERCENT) {
                    // Generate summary via LLM
                    log.debug("Generating summary for oversized text ({}% over limit)",
                            String.format("%.1f", percentual));

                    String summary = llmContext.generateCompletion(
                            "Resuma o seguinte texto de forma concisa, mantendo as informações principais:",
                            text);

                    // Store original truncation flag in metadata
                    if (embedding.getMetadados() == null) {
                        embedding.setMetadados(new bor.tools.simplerag.entity.MetaDoc());
                    }
                    embedding.getMetadados().put("texto_original_truncado", true);
                    embedding.getMetadados().put("resumo_gerado", true);
                    embedding.getMetadados().put("tokens_originais", tokens);

                    return summary;

                } else {
                    // Truncate (excedente <= 2%)
                    log.debug("Truncating text ({}% over limit, ≤2%)",
                            String.format("%.1f", percentual));

                    int maxChars = contextLength * 4; // Approx 4 chars/token
                    String truncated = text.substring(0, Math.min(maxChars, text.length()));

                    if (embedding.getMetadados() == null) {
                        embedding.setMetadados(new bor.tools.simplerag.entity.MetaDoc());
                    }
                    embedding.getMetadados().put("texto_truncado", true);
                    embedding.getMetadados().put("tokens_originais", tokens);

                    return truncated;
                }
            }

            return text;

        } catch (Exception e) {
            log.warn("Token counting failed for embedding #{}, using text as-is: {}",
                    embedding.getId(), e.getMessage());
            return text;
        }
    }

    // ========== Helper Methods ==========

    /**
     * Creates batches of embeddings for batch processing.
     *
     * @param embeddings all embeddings to process
     * @param batchSize maximum size per batch
     * @return list of batches
     */
    private List<List<DocumentEmbedding>> createBatches(
            List<DocumentEmbedding> embeddings,
            int batchSize) {

        List<List<DocumentEmbedding>> batches = new ArrayList<>();

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

    // ========== Result Classes ==========

    /**
     * Result of split operation.
     */
    @Data
    @Builder
    private static class SplitResult {
        private List<Chapter> chapters;
        private List<DocumentEmbedding> embeddings;

        public int getChaptersCount() {
            return chapters != null ? chapters.size() : 0;
        }

        public int getEmbeddingsCount() {
            return embeddings != null ? embeddings.size() : 0;
        }
    }

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
    }
}
