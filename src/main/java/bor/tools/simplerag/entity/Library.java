package bor.tools.simplerag.entity;

import java.util.UUID;

import org.hibernate.annotations.Type;

import bor.tools.simplerag.entity.enums.TipoBiblioteca;
import io.hypersistence.utils.hibernate.type.json.JsonType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Entity representing a library - a collection of documents organized by knowledge area.
 * Maps to the 'biblioteca' table in PostgreSQL.
 */
@Entity
@Table(name = "library")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Library extends Updatable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;
    
    /**
     * Identificador único para biblioteca
     */
    @Column(name="uuid", nullable=false, unique=true)
    private UUID uuid;

    /**
     * Name of the library
     */	
    @Column(nullable = false)
    private String nome;

    /**
     * Knowledge area of the library (e.g., Law, Medicine, Engineering).
     * Used to guide LLM responses.
     */	
    @Column(name = "area_conhecimento", nullable = false)
    private String areaConhecimento;

    /**
     * Weight for semantic search (e.g., embeddings).
     * The sum of semantic and textual weights must equal 1.0.
     */
    @Column(name = "peso_semantico")
    @Builder.Default
    private Float pesoSemantico = 0.60f;

    /**
     * Weight for textual search (e.g., BM25).
     * The sum of semantic and textual weights must equal 1.0.
     */
    @Column(name = "peso_textual")
    @Builder.Default
    private Float pesoTextual = 0.40f;
       
    /**
     * Type of library: PUBLIC (shared) or PRIVATE (user-specific).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 50)
    private TipoBiblioteca tipo;

    /**
     * Arbitrary metadata stored as JSONB.
     * It MUST include the key 'lingua' (language) for document processing.
     */
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private MetaBiblioteca metadados = new MetaBiblioteca();


     /**
     * Validates that semantic and textual weights sum to 1.0
     */
    @PrePersist
    @PreUpdate
    private void validateWeights() {
        if (pesoSemantico != null && pesoTextual != null) {
            Float sum = pesoSemantico + pesoTextual;
            if (Math.abs(sum - 1.0f) > 0.01f) {
        	throw new IllegalStateException("A soma dos pesos semântico e textual deve ser igual a 1.0");
            }
        }
    }
    
    /**
     * Get or create UUID
     * @return
     */
    public UUID getUuid() {
	if(this.uuid==null)
	    uuid = UUID.randomUUID();
	return this.uuid;
    }
    
    /**
     * Get or create metadata
     * @return
     */   
    public MetaBiblioteca getMetadados() {
	if (metadados == null) {
	    metadados = new MetaBiblioteca();
	}
	return metadados;
    }
    
    @Transient 
    public void setLanguage(String language) {	
	getMetadados().setLanguage(language);
    }
    /**
     * Gets the language from metadata
     */
    @Transient 
    public String getLanguage() {
        return metadados != null ? metadados.getLanguage() : null;
    }

    @Transient 
    public void setEmbeddingModel(String model) {
	getMetadados().setEmbeddingModel(model);    
    }
    /**
     * Gets the embedding model from metadata
     */
    @Transient 
    public String getEmbeddingModel() {
        return metadados != null ? metadados.getEmbeddingModel() : null;
    }

    @Transient 
    public void setEmbeddingDimension(Integer dimension) {	
	getMetadados().setEmbeddingDimension(dimension);	    
    }
    /**
     * Gets the embedding dimension from metadata
     */
    @Transient 
    public Integer getEmbeddingDimension() {
        return getMetadados().getEmbeddingDimension();
    }
    
    /**
     * Gets the description from metadata
     */
    @Transient
    public String getDescription() {
	return getMetadados().getDescricao();
    }
    
    /**
     * Sets the description in metadata
     */
    @Transient
    public void setDescription(String descricao) {
    	getMetadados().setDescricao(descricao);
    }
    
    /**
     * Gets the max tokens from metadata
     * @return
     */
    @Transient
    public Integer getMaxTokens() {	
	return getMetadados().getMaxTokens();
    }	
    
    /**
     * Sets the max tokens in metadata
     * @param maxTokens
     */
    @Transient
    public void setMaxTokens(Integer maxTokens) {
    	getMetadados().setMaxTokens(maxTokens);
    }
    /**
     * Gets the completion QA model from metadata
     * @return
     */
    @Transient
    public void setCompletionQAModel(String model) {
	getMetadados().setCompletionQAModel(model);
    }
    
    /**
     * Gets the suggested Completion Model for QA from the metadata.
     *
     * @return the completion model string, or null if not set
     */
    @Transient
    public String getCompletionQAModel() {
    	return getMetadados().getCompletionQAModel();
    }
    
}