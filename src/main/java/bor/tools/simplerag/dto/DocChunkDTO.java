package bor.tools.simplerag.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import bor.tools.simplerag.entity.MetaDoc;
import bor.tools.simplerag.entity.enums.TipoEmbedding;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for DocChunk entity.
 *
 * Contains basic embedding data without parent references.
 * Used as leaf nodes in the hierarchy for Splitters and processing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocChunkDTO {

    private Integer id;

    private Integer bibliotecaId;

    private Integer documentoId;

    private Integer capituloId;

    private TipoEmbedding tipoEmbedding;

    private String trechoTexto;

    private Integer ordemCap;

    private float[] embeddingVector;

    private String textoIndexado;

    @Builder.Default
    private MetaDoc metadados = new MetaDoc();

    private LocalDateTime createdAt;

    // Search scores (populated by search queries)
    private Float scoreSemantico;
    private Float scoreTextual;
    private Float score;
    
    // campos declarados
 
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    /**
     * Create DTO from entity
     * @param src - source entity
     * @return DTO instance
     */
    public static DocChunkDTO from(bor.tools.simplerag.entity.DocChunk src) {
        if (src == null) {
            return null;
        }
        return DocChunkDTO.builder()
                .id(src.getId())
                .bibliotecaId(src.getLibraryId())
                .documentoId(src.getDocumentoId())
                .capituloId(src.getChapterId())
                .tipoEmbedding(src.getTipoEmbedding())
                .trechoTexto(src.getTexto())
                .ordemCap(src.getOrderChapter())
                .embeddingVector(src.getEmbeddingVector())
                .metadados(src.getMetadados() != null ? new MetaDoc(src.getMetadados()) : null)
                .createdAt(src.getCreatedAt())
                .updatedAt(src.getUpdatedAt())
                .deletedAt(src.getDeletedAt())
                .build();
    }

    /**
     * Convert DTO to entity
     * @return DocChunk entity
     */
    public bor.tools.simplerag.entity.DocChunk toEntity() {
        bor.tools.simplerag.entity.DocChunk entity = new bor.tools.simplerag.entity.DocChunk();
        entity.setId(this.id);
        entity.setLibraryId(this.bibliotecaId);
        entity.setDocumentoId(this.documentoId);
        entity.setChapterId(this.capituloId);
        entity.setTipoEmbedding(this.tipoEmbedding);
        entity.setTexto(this.trechoTexto);
        entity.setOrderChapter(this.ordemCap);
        entity.setEmbeddingVector(this.embeddingVector);
        entity.setMetadados(getMetadados());
        entity.setCreatedAt(this.createdAt);
        entity.setUpdatedAt(this.updatedAt);
        entity.setDeletedAt(this.deletedAt);
        return entity;
    }

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

    
    public MetaDoc getMetadados() {
	if (metadados == null) {
	    metadados = new MetaDoc();
	}
	return metadados;
    }
    
    /**
     * Get metadata value by key
     */
    public Object getMetadataValue(String key) {
        return getMetadados().get(key) ;
    }

    /**
     * Set metadata value
     */
    public void setMetadataValue(String key, Object value) {
	getMetadados().put(key, value);
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