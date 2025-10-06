package bor.tools.simplerag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for textual search (full-text search only).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TextualSearchRequest {

    /**
     * Query text for full-text search
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
}
