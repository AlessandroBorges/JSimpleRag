package bor.tools.simplerag.service.processing;

import lombok.Builder;
import lombok.Data;

/**
 * Result object containing statistics and status of document enrichment processing.
 *
 * <p>Enrichment is Phase 2 processing that generates Q&A and summary embeddings
 * for improved retrieval quality.</p>
 *
 * <h3>Example Result:</h3>
 * <pre>
 * {
 *   "documentId": 123,
 *   "chaptersProcessed": 10,
 *   "qaEmbeddingsGenerated": 30,
 *   "summaryEmbeddingsGenerated": 10,
 *   "chaptersFailed": 1,
 *   "duration": "2m 15s",
 *   "success": true
 * }
 * </pre>
 *
 * @since 0.0.3
 * @see DocumentProcessingService#enrichDocument(bor.tools.simplerag.entity.Documento, bor.tools.simplerag.dto.LibraryDTO, EnrichmentOptions)
 */
@Data
@Builder
public class EnrichmentResult {

    /**
     * Document ID that was enriched
     */
    private Integer documentId;

    /**
     * Total number of chapters processed
     */
    private int chaptersProcessed;

    /**
     * Number of chapters that failed enrichment
     */
    @Builder.Default
    private int chaptersFailed = 0;

    /**
     * Number of Q&A embeddings generated
     */
    @Builder.Default
    private int qaEmbeddingsGenerated = 0;

    /**
     * Number of summary embeddings generated
     */
    @Builder.Default
    private int summaryEmbeddingsGenerated = 0;

    /**
     * Total embeddings generated (Q&A + summaries)
     */
    public int getTotalEmbeddingsGenerated() {
        return qaEmbeddingsGenerated + summaryEmbeddingsGenerated;
    }

    /**
     * Processing duration in human-readable format (e.g., "2m 15s")
     */
    private String duration;

    /**
     * Whether enrichment completed successfully.
     *
     * <p>Success criteria:</p>
     * <ul>
     *   <li>At least one embedding was generated</li>
     *   <li>No fatal errors occurred</li>
     *   <li>In continueOnError mode: partial success is still true</li>
     * </ul>
     */
    private boolean success;

    /**
     * Error message if enrichment failed completely
     */
    private String errorMessage;

    /**
     * List of chapter-level errors (for partial failures)
     */
    @Builder.Default
    private java.util.List<String> chapterErrors = new java.util.ArrayList<>();

    /**
     * Adds a chapter-level error to the result.
     *
     * @param chapterId Chapter ID that failed
     * @param error Error message
     */
    public void addChapterError(Integer chapterId, String error) {
        chapterErrors.add(String.format("Chapter %d: %s", chapterId, error));
        chaptersFailed++;
    }

    /**
     * Returns a human-readable summary of the enrichment result.
     *
     * @return Summary string
     */
    public String getSummary() {
        if (!success) {
            return String.format("Enrichment failed for document %d: %s",
                    documentId, errorMessage);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Document %d enriched successfully in %s:\n",
                documentId, duration));
        sb.append(String.format("  - Chapters processed: %d/%d\n",
                chaptersProcessed - chaptersFailed, chaptersProcessed));
        sb.append(String.format("  - Q&A embeddings: %d\n", qaEmbeddingsGenerated));
        sb.append(String.format("  - Summary embeddings: %d\n", summaryEmbeddingsGenerated));
        sb.append(String.format("  - Total embeddings: %d", getTotalEmbeddingsGenerated()));

        if (chaptersFailed > 0) {
            sb.append(String.format("\n  - Chapters failed: %d", chaptersFailed));
        }

        return sb.toString();
    }

    /**
     * Calculates success rate as percentage.
     *
     * @return Success rate (0.0 to 1.0)
     */
    public double getSuccessRate() {
        if (chaptersProcessed == 0) {
            return 0.0;
        }
        return (double) (chaptersProcessed - chaptersFailed) / chaptersProcessed;
    }
}
