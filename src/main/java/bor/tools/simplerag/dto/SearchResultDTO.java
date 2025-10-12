package bor.tools.simplerag.dto;

import bor.tools.simplerag.entity.DocumentEmbedding;
import bor.tools.simplerag.entity.enums.TipoEmbedding;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO representing a single search result with scores and metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResultDTO {

    /**
     * Document embedding ID
     */
    private Integer embeddingId;

    /**
     * Document ID
     */
    private Integer documentoId;

    /**
     * Document title
     */
    private String documentoTitulo;

    /**
     * Chapter ID (nullable)
     */
    private Integer capituloId;

    /**
     * Chapter title (nullable)
     */
    private String capituloTitulo;

    /**
     * Text excerpt from the embedding
     */
    private String texto;

    /**
     * Embedding type (DOCUMENT/CHAPTER/CHUNK)
     */
    private TipoEmbedding tipoEmbedding;

    /**
     * Semantic search score
     */
    private Float scoreSemantico;

    /**
     * Textual search score
     */
    private Float scoreTextual;

    /**
     * Combined final score
     */
    private Float score;

    /**
     * Additional metadata
     */
    private Map<String, Object> metadados;

    /**
     * Create SearchResultDTO from DocumentEmbedding
     */
    public static SearchResultDTO from(DocumentEmbedding embedding) {
        if (embedding == null) {
            return null;
        }

        return SearchResultDTO.builder()
                .embeddingId(embedding.getId())
                .documentoId(embedding.getDocumentoId())
                .capituloId(embedding.getChapterId())
                .texto(embedding.getTexto())
                .tipoEmbedding(embedding.getTipoEmbedding())
                .metadados(embedding.getMetadados())
                .build();
    }

    /**
     * Enriches the result with document and chapter information
     */
    public SearchResultDTO enrich(String docTitulo, String capTitulo) {
        this.documentoTitulo = docTitulo;
        this.capituloTitulo = capTitulo;
        return this;
    }

    /**
     * Sets scores from metadata if present
     */
    public SearchResultDTO extractScores() {
        if (metadados != null) {
            this.scoreSemantico = (Float) metadados.get("score_semantic");
            this.scoreTextual = (Float) metadados.get("score_text");
            this.score = (Float) metadados.get("score");
        }
        return this;
    }
}
