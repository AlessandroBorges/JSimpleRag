package bor.tools.simplerag.service.embedding.model;

import lombok.Builder;
import lombok.Data;

/**
 * Options for document processing and embedding generation.
 *
 * Configures which types of embeddings should be generated
 * during document processing (Q&A, summaries, etc.).
 *
 * @since 0.0.1
 * @see bor.tools.simplerag.service.embedding.EmbeddingOrchestrator
 */
@Data
@Builder
@Deprecated(since = "0.0.3", forRemoval = true) 
public class ProcessingOptions {

    /**
     * Whether to generate Q&A embeddings from document content.
     *
     * Q&A embeddings create synthetic question-answer pairs that
     * improve retrieval for conversational queries.
     *
     * Default: false
     */
    @Builder.Default
    private boolean includeQA = false;

    /**
     * Whether to generate summary embeddings from document content.
     *
     * Summary embeddings condense large chapters into compact representations,
     * improving retrieval of main concepts.
     *
     * Default: false
     */
    @Builder.Default
    private boolean includeSummary = false;

    /**
     * Number of Q&A pairs to generate per chapter.
     *
     * If null, uses strategy default (typically 3).
     * Only applicable when includeQA is true.
     */
    private Integer qaCount;

    /**
     * Maximum length for generated summaries (in characters).
     *
     * If null, uses strategy default.
     * Only applicable when includeSummary is true.
     */
    private Integer maxSummaryLength;

    /**
     * Custom instructions for summarization.
     *
     * Allows fine-tuning of summary generation (e.g., "Focus on technical details").
     * If null, uses default summarization prompt.
     * Only applicable when includeSummary is true.
     */
    private String summaryInstructions;

    /**
     * Creates processing options with all features enabled.
     *
     * Generates:
     * - Basic chapter embeddings
     * - Q&A embeddings (default count)
     * - Summary embeddings (default length)
     *
     * @return ProcessingOptions with all features enabled
     */
    public static ProcessingOptions fullProcessing() {
        return ProcessingOptions.builder()
                .includeQA(true)
                .includeSummary(true)
                .build();
    }

    /**
     * Creates processing options with only basic embeddings.
     *
     * Generates only standard chapter/chunk embeddings,
     * skipping Q&A and summaries.
     *
     * @return ProcessingOptions with basic processing only
     */
    public static ProcessingOptions basicOnly() {
        return ProcessingOptions.builder()
                .includeQA(false)
                .includeSummary(false)
                .build();
    }

    /**
     * Creates processing options with Q&A only.
     *
     * @param qaCount Number of Q&A pairs to generate
     * @return ProcessingOptions with Q&A enabled
     */
    public static ProcessingOptions withQA(int qaCount) {
        return ProcessingOptions.builder()
                .includeQA(true)
                .qaCount(qaCount)
                .includeSummary(false)
                .build();
    }

    /**
     * Creates processing options with summaries only.
     *
     * @param maxLength Maximum summary length
     * @return ProcessingOptions with summaries enabled
     */
    public static ProcessingOptions withSummary(Integer maxLength) {
        return ProcessingOptions.builder()
                .includeQA(false)
                .includeSummary(true)
                .maxSummaryLength(maxLength)
                .build();
    }
}
