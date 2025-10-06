package bor.tools.simplerag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for hybrid search (semantic + textual).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchRequest {

    /**
     * Query text for search
     */
    @NotBlank(message = "Query text is required")
    private String query;

    /**
     * Library IDs to search in
     */
    @NotNull(message = "Library IDs are required")
    private Integer[] libraryIds;

    /**
     * Maximum number of results (default: 10)
     */
    @Builder.Default
    private Integer limit = 10;

    /**
     * Semantic search weight (default: 0.6)
     * Must sum with pesoTextual to 1.0
     */
    @Builder.Default
    private Float pesoSemantico = 0.6f;

    /**
     * Textual search weight (default: 0.4)
     * Must sum with pesoSemantico to 1.0
     */
    @Builder.Default
    private Float pesoTextual = 0.4f;

    /**
     * Validates that semantic and textual weights sum to 1.0
     */
    public boolean isWeightValid() {
        if (pesoSemantico != null && pesoTextual != null) {
            float sum = pesoSemantico + pesoTextual;
            return Math.abs(sum - 1.0f) <= 0.001f;
        }
        return false;
    }
}
