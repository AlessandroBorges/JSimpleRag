package bor.tools.simplerag.service.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocumentEmbeddingDTO;
import bor.tools.simplerag.dto.DocumentoWithAssociationDTO;
import bor.tools.simplerag.entity.enums.TipoConteudo;
import bor.tools.simplerag.service.embedding.model.EmbeddingContext;
import bor.tools.simplerag.service.embedding.model.ProcessingOptions;
import bor.tools.splitter.DocumentRouter;
import bor.tools.splitter.DocumentSplitter;
import bor.tools.splitter.SplitterFactory;
import bor.tools.utils.RagUtils;
import lombok.RequiredArgsConstructor;

/**
 * Orchestrator for complete document processing with embeddings.
 *
 * This service coordinates the entire document-to-embeddings pipeline:
 * 1. Content type detection
 * 2. Document splitting into chapters
 * 3. Embedding generation (basic, Q&A, summaries)
 * 4. Result aggregation
 *
 * Includes robust retry logic for LLM failures:
 * - 2 minutes delay between retries
 * - 2 retries (3 total attempts)
 * - Fail-fast after all retries exhausted
 *
 * Supports asynchronous processing via @Async for non-blocking operations.
 *
 * @since 0.0.1
 */
@Service
@RequiredArgsConstructor
public class EmbeddingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingOrchestrator.class);

    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 2 * 60 * 1000; // 2 minutes
    private static final int MIN_TOKENS_FOR_SUMMARY = 500;

    private final EmbeddingService embeddingService;
    private final SplitterFactory splitterFactory;
    private final DocumentRouter documentRouter;

    /**
     * Process document with full embedding generation pipeline.
     *
     * Includes automatic retry on failure (2 min delay, 2 retries).
     * Runs asynchronously to prevent blocking.
     *
     * @param documento Document to process
     * @param context Context including library and model configuration
     * @param options Processing options (Q&A, summaries, etc.)
     * @return CompletableFuture with processing results
     */
    @Async
    public CompletableFuture<ProcessingResult> processDocumentFull(
            DocumentoWithAssociationDTO documento,
            EmbeddingContext context,
            ProcessingOptions options) {

        return CompletableFuture.supplyAsync(() -> {
            int attempt = 0;
            Exception lastException = null;

            while (attempt <= MAX_RETRIES) {
                try {
                    log.info("Processing document (attempt {}/{}): {}",
                            attempt + 1, MAX_RETRIES + 1, documento.getTitulo());

                    return executeProcessing(documento, context, options);

                } catch (Exception e) {
                    lastException = e;
                    attempt++;

                    if (attempt <= MAX_RETRIES) {
                        log.warn("Processing failed (attempt {}/{}): {}. Retrying in 2 minutes...",
                                attempt, MAX_RETRIES + 1, e.getMessage());
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Interrupted during retry wait", ie);
                        }
                    }
                }
            }

            log.error("Processing failed after {} attempts for document: {}",
                    MAX_RETRIES + 1, documento.getTitulo());
            throw new RuntimeException(
                    "Document processing failed after " + (MAX_RETRIES + 1) + " attempts",
                    lastException
            );
        });
    }

    /**
     * Process document synchronously with retry logic.
     *
     * Same as processDocumentFull but blocks until completion.
     * Use when you need immediate results or in already-async contexts.
     *
     * @param documento Document to process
     * @param context Context including library and model configuration
     * @param options Processing options (Q&A, summaries, etc.)
     * @return Processing results
     */
    public ProcessingResult processDocumentSync(
            DocumentoWithAssociationDTO documento,
            EmbeddingContext context,
            ProcessingOptions options) {

        int attempt = 0;
        Exception lastException = null;

        while (attempt <= MAX_RETRIES) {
            try {
                log.info("Processing document synchronously (attempt {}/{}): {}",
                        attempt + 1, MAX_RETRIES + 1, documento.getTitulo());

                return executeProcessing(documento, context, options);

            } catch (Exception e) {
                lastException = e;
                attempt++;

                if (attempt <= MAX_RETRIES) {
                    log.warn("Processing failed (attempt {}/{}): {}. Retrying in 2 minutes...",
                            attempt, MAX_RETRIES + 1, e.getMessage());
                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry wait", ie);
                    }
                }
            }
        }

        log.error("Processing failed after {} attempts for document: {}",
                MAX_RETRIES + 1, documento.getTitulo());
        throw new RuntimeException(
                "Document processing failed after " + (MAX_RETRIES + 1) + " attempts",
                lastException
        );
    }

    /**
     * Process document without retry logic.
     *
     * Useful for testing or when retry is handled at a higher level.
     *
     * @param documento Document to process
     * @param context Context including library and model configuration
     * @param options Processing options (Q&A, summaries, etc.)
     * @return Processing results
     */
    public ProcessingResult processDocumentWithoutRetry(
            DocumentoWithAssociationDTO documento,
            EmbeddingContext context,
            ProcessingOptions options) {

        log.info("Processing document without retry: {}", documento.getTitulo());
        return executeProcessing(documento, context, options);
    }

    // ========== Private Helper Methods ==========

    /**
     * Execute the actual processing logic.
     *
     * This is the core pipeline that:
     * 1. Detects content type
     * 2. Splits document into chapters
     * 3. Generates embeddings for each chapter
     * 4. Generates additional embeddings (Q&A, summaries) if requested
     * 
     * @param documento Document to process
     * @param context Context including library and model configuration
     * @param options Processing options (Q&A, summaries, etc.)
     * 
     * @return Processing results
     */
    private ProcessingResult executeProcessing(
            DocumentoWithAssociationDTO documento,
            EmbeddingContext context,
            ProcessingOptions options) {

        ProcessingResult result = new ProcessingResult();
        result.setDocumento(documento);

        // 1. Detect content type
        TipoConteudo tipoConteudo = documentRouter.detectContentType(documento.getConteudoMarkdown());
        log.debug("Detected content type: {} for document: {}", tipoConteudo, documento.getTitulo());

        // 2. Split document into chapters
        DocumentSplitter splitter = splitterFactory.createSplitter(tipoConteudo, context.getLibrary());
        List<ChapterDTO> chapters = splitter.splitDocumento(documento);
        result.setCapitulos(chapters);

        log.debug("Split document into {} chapters", chapters.size());

        // 3. Generate embeddings for each chapter
        for (ChapterDTO chapter : chapters) {

            // Basic chapter embeddings (always generated)
            List<DocumentEmbeddingDTO> embeddings =
                embeddingService.generateChapterEmbeddings(chapter, context);
            result.addEmbeddings(embeddings);

            log.debug("Generated {} basic embeddings for chapter: {}",
                    embeddings.size(), chapter.getTitulo());

            // Q&A embeddings (if requested)
            if (options.isIncludeQA()) {
                List<DocumentEmbeddingDTO> qaEmbeddings =
                    embeddingService.generateQAEmbeddings(
                        chapter, context, options.getQaCount());
                result.addEmbeddings(qaEmbeddings);

                log.debug("Generated {} Q&A embeddings for chapter: {}",
                        qaEmbeddings.size(), chapter.getTitulo());
            }

            // Summary embeddings (if requested and chapter is large enough)
            if (options.isIncludeSummary()) {
                int tokens = estimateTokenCount(chapter.getConteudo());

                if (tokens > MIN_TOKENS_FOR_SUMMARY) {
                    List<DocumentEmbeddingDTO> summaryEmbeddings =
                        embeddingService.generateSummaryEmbeddings(
                            chapter,
                            context,
                            options.getMaxSummaryLength(),
                            options.getSummaryInstructions());
                    result.addEmbeddings(summaryEmbeddings);

                    log.debug("Generated {} summary embeddings for chapter: {}",
                            summaryEmbeddings.size(), chapter.getTitulo());
                } else {
                    log.debug("Skipping summary for chapter '{}' - only {} tokens (minimum: {})",
                            chapter.getTitulo(), tokens, MIN_TOKENS_FOR_SUMMARY);
                }
            }
        }

        log.info("Completed processing for document: {} - {} chapters, {} embeddings",
                documento.getTitulo(), result.getCapitulos().size(), result.getAllEmbeddings().size());

        return result;
    }

    /**
     * Estimates token count for text.
     *
     * Uses RagUtils.countTokens() when possible, falls back to simple estimation.
     */
    private int estimateTokenCount(String text) {
        if (text == null) {
            return 0;
        }

        try {
            return RagUtils.countTokens(text);
        } catch (Exception e) {
            // Fallback: simple estimation (words / 0.75)
            log.debug("Failed to count tokens, using estimation: {}", e.getMessage());
            String[] words = text.split("\\s+");
            return (int) Math.ceil(words.length / 0.75);
        }
    }

    // ========== Result Classes ==========

    /**
     * Result container for processing operations.
     *
     * Contains all artifacts generated during document processing:
     * - Original document
     * - Generated chapters
     * - All embeddings (basic, Q&A, summaries)
     * - Processing statistics
     */
    @lombok.Data
    public static class ProcessingResult {
        private DocumentoWithAssociationDTO documento;
        private List<ChapterDTO> capitulos = new ArrayList<>();
        private List<DocumentEmbeddingDTO> allEmbeddings = new ArrayList<>();

        /**
         * Add embeddings to the result.
         */
        public void addEmbeddings(List<DocumentEmbeddingDTO> embeddings) {
            if (embeddings != null) {
                this.allEmbeddings.addAll(embeddings);
            }
        }

        /**
         * Get processing statistics.
         */
        public ProcessingStats getStats() {
            return new ProcessingStats(
                capitulos.size(),
                allEmbeddings.size(),
                documento.getConteudoMarkdown() != null
                    ? documento.getConteudoMarkdown().length()
                    : 0
            );
        }

        /**
         * Check if processing was successful.
         */
        public boolean isSuccessful() {
            return !capitulos.isEmpty() && !allEmbeddings.isEmpty();
        }
    }

    /**
     * Statistics for a processing operation.
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ProcessingStats {
        private int chaptersCount;
        private int embeddingsCount;
        private int totalCharacters;

        /**
         * Get average embeddings per chapter.
         */
        public double getAverageEmbeddingsPerChapter() {
            return chaptersCount > 0 ? (double) embeddingsCount / chaptersCount : 0.0;
        }

        @Override
        public String toString() {
            return String.format(
                "ProcessingStats{chapters=%d, embeddings=%d, chars=%d, avg=%.2f embeddings/chapter}",
                chaptersCount, embeddingsCount, totalCharacters, getAverageEmbeddingsPerChapter()
            );
        }
    }
}
