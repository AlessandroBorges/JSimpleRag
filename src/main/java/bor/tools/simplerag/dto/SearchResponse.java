package bor.tools.simplerag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for search operations containing results and metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResponse {

    /**
     * Original query text (echo)
     */
    private String query;

    /**
     * Libraries searched
     */
    private Integer[] libraryIds;

    /**
     * Total number of results
     */
    private Integer totalResults;

    /**
     * Semantic weight applied
     */
    private Float pesoSemantico;

    /**
     * Textual weight applied
     */
    private Float pesoTextual;

    /**
     * Search results ordered by score (descending)
     */
    private List<SearchResultDTO> results;

    /**
     * Search execution time in milliseconds
     */
    private Long executionTimeMs;

    /**
     * Create response from search results
     */
    public static SearchResponse from(String query, Integer[] libraryIds,
                                      Float pesoSemantico, Float pesoTextual,
                                      List<SearchResultDTO> results) {
        return SearchResponse.builder()
                .query(query)
                .libraryIds(libraryIds)
                .totalResults(results != null ? results.size() : 0)
                .pesoSemantico(pesoSemantico)
                .pesoTextual(pesoTextual)
                .results(results)
                .build();
    }
}
