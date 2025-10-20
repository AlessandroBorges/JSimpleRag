package bor.tools.simplerag.dto;

import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.entity.MetaBiblioteca;
import bor.tools.simplerag.entity.enums.TipoBiblioteca;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.*;

/**
 * DTO for Library entity.
 *
 * Contains only biblioteca data without references to documents for performance.
 * To get documents, query by bibliotecaId using DocumentoRepository.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "id", "uuid", 
    		     "nome", 
                     "areaConhecimento", 
                     "pesoSemantico", "pesoTextual",
                     "language",
                     "embeddingModel",                     
		     "embeddingDimension",
                     "metadados", 
                     "createdAt", "updatedAt", "deletedAt" })
public class LibraryDTO {

    private Integer id;
    
    private UUID uuid;

    private String nome;

    private String areaConhecimento;

    @Builder.Default
    private Float pesoSemantico = 0.60f;

    @Builder.Default
    private Float pesoTextual = 0.40f;
    
    @Builder.Default
    private TipoBiblioteca tipo = TipoBiblioteca.PESSOAL;

    @Builder.Default
    private MetaBiblioteca metadados = new MetaBiblioteca();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;


    /**
     * Create DTO from src
     * @param src - source
     * @return
     */
    public static LibraryDTO from(Library src) {
	if (src == null) {
	    return null;
	}
	return LibraryDTO.builder()
		.id(src.getId())
		.uuid(src.getUuid())
		.nome(src.getNome())
		.areaConhecimento(src.getAreaConhecimento())
		.pesoSemantico(src.getPesoSemantico())
		.pesoTextual(src.getPesoTextual())
		.tipo(src.getTipo())
		.metadados(src.getMetadados())
		.createdAt(src.getCreatedAt())
		.updatedAt(src.getUpdatedAt())
		.deletedAt(src.getDeletedAt())
		.build();
    }

    /**
     * Convert DTO to entity
     * @return Library entity
     */
    public Library toEntity() {
        Library entity = new Library();
        entity.setId(this.id);
        entity.setUuid(getUuid());
        entity.setNome(this.nome);
        entity.setAreaConhecimento(this.areaConhecimento);
        entity.setPesoSemantico(this.pesoSemantico);
        entity.setPesoTextual(this.pesoTextual);
        entity.setMetadados(getMetadados());
        entity.setTipo(getTipo());
        entity.setCreatedAt(this.createdAt);
        entity.setUpdatedAt(this.updatedAt);
        entity.setDeletedAt(this.deletedAt);
        return entity;
    }

    /**
     * Validates that semantic and textual weights sum to 1.0
     */
    @JsonIgnore
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