package bor.tools.simplerag.dto;

import bor.tools.simplerag.entity.enums.TipoEmbedding;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for DocumentEmbedding entity.
 *
 * Contains basic embedding data without parent references.
 * Used as leaf nodes in the hierarchy for Splitters and processing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentEmbeddingDTO {

    private Integer id;

    private Integer bibliotecaId;

    private Integer documentoId;

    private Integer capituloId;

    private TipoEmbedding tipoEmbedding;

    private String trechoTexto;

    private Integer ordemCap;

    private float[] embeddingVector;

    private String textoIndexado;

    private Metadata metadados;

    private LocalDateTime createdAt;

    // Search scores (populated by search queries)
    private Float scoreSemantico;
    private Float scoreTextual;
    private Float score;

    /**
     * Check if this embedding has a vector
     */
    public boolean hasVector() {
        return embeddingVector != null && embeddingVector.length > 0;
    }

    /**
     * Get vector dimension
     */
    public int getVectorDimension() {
        return embeddingVector != null ? embeddingVector.length : 0;
    }

    /**
     * Check if this is a document-level embedding
     */
    public boolean isDocumentLevel() {
        return tipoEmbedding == TipoEmbedding.DOCUMENTO;
    }

    /**
     * Check if this is a chapter-level embedding
     */
    public boolean isChapterLevel() {
        return tipoEmbedding == TipoEmbedding.CAPITULO;
    }

    /**
     * Check if this is a chunk-level embedding
     */
    public boolean isChunkLevel() {
        return tipoEmbedding == TipoEmbedding.TRECHO;
    }

    /**
     * Get metadata value by key
     */
    public Object getMetadataValue(String key) {
        return metadados != null ? metadados.get(key) : null;
    }

    /**
     * Set metadata value
     */
    public void setMetadataValue(String key, Object value) {
        if (metadados == null) {
            metadados = new Metadata();
        }
        metadados.put(key, value);
    }

    /**
     * Get text content length
     */
    public int getTextLength() {
        return trechoTexto != null ? trechoTexto.length() : 0;
    }

    /**
     * Check if has search scores
     */
    public boolean hasSearchScores() {
        return score != null || scoreSemantico != null || scoreTextual != null;
    }
}