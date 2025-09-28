package bor.tools.simplerag.dto;

import bor.tools.simplerag.entity.Biblioteca;
import bor.tools.simplerag.entity.MetaBiblioteca;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for Biblioteca entity.
 *
 * Contains only biblioteca data without references to documents for performance.
 * To get documents, query by bibliotecaId using DocumentoRepository.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BibliotecaDTO {

    private Integer id;
    
    private String uuid;

    private String nome;

    private String areaConhecimento;

    @Builder.Default
    private Float pesoSemantico = 0.60f;

    @Builder.Default
    private Float pesoTextual = 0.40f;

    private MetaBiblioteca metadados;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    
    /**
     * Create DTO from src
     * @param src - source
     * @return
     */
    public static BibliotecaDTO from(Biblioteca src) {
	if (src == null) {
	    return null;
	}
	return BibliotecaDTO.builder()
		.id(src.getId())
		.uuid(src.getUuid())
		.nome(src.getNome())
		.areaConhecimento(src.getAreaConhecimento())
		.pesoSemantico(src.getPesoSemantico())
		.pesoTextual(src.getPesoTextual())
		.metadados(src.getMetadados())
		.createdAt(src.getCreatedAt())
		.updatedAt(src.getUpdatedAt())
		.build();
    }
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

    /**
     * Gets the language from metadata
     */
    public String getLanguage() {
        return metadados != null ? metadados.getLanguage() : null;
    }

    /**
     * Gets the embedding model from metadata
     */
    public String getEmbeddingModel() {
        return metadados != null ? metadados.getEmbeddingModel() : null;
    }

    /**
     * Gets the embedding dimension from metadata
     */
    public Integer getEmbeddingDimension() {
        return metadados != null ? metadados.getEmbeddingDimension() : null;
    }
}