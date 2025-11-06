package bor.tools.simplerag.service.processing;

import lombok.Builder;
import lombok.Data;

/**
 * Configuration options for document enrichment (Phase 2 processing).
 *
 * <p>Enrichment is an optional post-processing step that generates additional
 * embeddings beyond the standard chapter/chunk embeddings:</p>
 * <ul>
 *   <li><b>Q&A Generation:</b> Creates synthetic question-answer pairs for improved conversational retrieval</li>
 *   <li><b>Summary Generation:</b> Creates condensed summaries for conceptual retrieval</li>
 * </ul>
 *
 * <p><b>Cost Consideration:</b> Both Q&A and summary generation use completion models
 * (more expensive than embedding-only operations). Enable selectively based on use case.</p>
 *
 * <h3>Usage Example:</h3>
 * <pre>
 * EnrichmentOptions options = EnrichmentOptions.builder()
 *     .generateQA(true)
 *     .numberOfQAPairs(5)
 *     .generateSummary(true)
 *     .maxSummaryLength(400)
 *     .build();
 * </pre>
 *
 * @since 0.0.3
 * @see DocumentProcessingService#enrichDocument(bor.tools.simplerag.entity.Documento, bor.tools.simplerag.dto.LibraryDTO, EnrichmentOptions)
 */
@Data
@Builder
public class EnrichmentOptions {

    /**
     * Whether to generate Q&A embeddings.
     * Default: false (disabled due to cost).
     */
    @Builder.Default
    private boolean generateQA = false;

    /**
     * Number of question-answer pairs to generate per chapter.
     * Default: 3 pairs.
     *
     * <p>Recommended values:</p>
     * <ul>
     *   <li>3-5: Small chapters (&lt;2000 tokens)</li>
     *   <li>5-10: Medium chapters (2000-4000 tokens)</li>
     *   <li>10-15: Large chapters (&gt;4000 tokens)</li>
     * </ul>
     */
    @Builder.Default
    private Integer numberOfQAPairs = 3;

    /**
     * Whether to generate summary embeddings.
     * Default: false (disabled due to cost).
     */
    @Builder.Default
    private boolean generateSummary = false;

    /**
     * Maximum length of generated summary in tokens.
     * Default: 500 tokens.
     *
     * <p>Recommended values:</p>
     * <ul>
     *   <li>300-400: Very concise summaries</li>
     *   <li>500-800: Balanced summaries (default)</li>
     *   <li>1000+: Detailed summaries</li>
     * </ul>
     */
    @Builder.Default
    private Integer maxSummaryLength = 500;

    /**
     * Custom instructions for summary generation.
     * If null, uses default instructions from SummaryEmbeddingStrategy.
     *
     * <p>Example:</p>
     * <pre>
     * "Crie um resumo técnico focado em conceitos principais e definições,
     *  mantendo termos específicos do domínio em inglês"
     * </pre>
     */
    private String summaryInstructions;

    /**
     * Whether to continue enrichment if individual chapters fail.
     * Default: true (fault-tolerant mode).
     *
     * <p>When true:</p>
     * <ul>
     *   <li>Failed chapters are logged but don't stop the process</li>
     *   <li>Partial enrichment is possible</li>
     * </ul>
     *
     * <p>When false:</p>
     * <ul>
     *   <li>First failure stops entire enrichment</li>
     *   <li>All-or-nothing approach</li>
     * </ul>
     */
    @Builder.Default
    private boolean continueOnError = true;
    
    
    String llmModelName;

    /**
     * Validates the configuration and returns error message if invalid.
     *
     * @return Error message if invalid, null if valid
     */
    public String validate() {
        if (!generateQA && !generateSummary) {
            return "At least one enrichment type (Q&A or Summary) must be enabled";
        }

        if (generateQA && (numberOfQAPairs == null || numberOfQAPairs < 1)) {
            return "numberOfQAPairs must be at least 1 when Q&A generation is enabled";
        }

        if (generateQA && numberOfQAPairs > 20) {
            return "numberOfQAPairs cannot exceed 20 (too expensive)";
        }

        if (generateSummary && (maxSummaryLength == null || maxSummaryLength < 100)) {
            return "maxSummaryLength must be at least 100 tokens when summary generation is enabled";
        }

        if (generateSummary && maxSummaryLength > 2000) {
            return "maxSummaryLength cannot exceed 2000 tokens";
        }

        return null; // Valid
    }

    /**
     * Creates a default enrichment configuration with both Q&A and summaries enabled.
     *
     * @return Default configuration (3 Q&A pairs, 500 token summaries)
     */
    public static EnrichmentOptions defaults() {
        return EnrichmentOptions.builder()
                .generateQA(true)
                .numberOfQAPairs(3)
                .generateSummary(true)
                .maxSummaryLength(500)
                .continueOnError(true)
                .build();
    }

    /**
     * Creates a Q&A-only enrichment configuration.
     *
     * @param numberOfPairs Number of Q&A pairs to generate per chapter
     * @return Q&A-only configuration
     */
    public static EnrichmentOptions qaOnly(int numberOfPairs) {
        return EnrichmentOptions.builder()
                .generateQA(true)
                .numberOfQAPairs(numberOfPairs)
                .generateSummary(false)
                .continueOnError(true)
                .build();
    }

    /**
     * Creates a summary-only enrichment configuration.
     *
     * @param maxLength Maximum summary length in tokens
     * @return Summary-only configuration
     */
    public static EnrichmentOptions summaryOnly(int maxLength) {
        return EnrichmentOptions.builder()
                .generateQA(false)
                .generateSummary(true)
                .maxSummaryLength(maxLength)
                .continueOnError(true)
                .build();
    }

    /**
     * Get the name of the Q&A model to use.
     * @return
     */
    public String getLLMmodelName() {	
	return this.llmModelName;
    }
}
