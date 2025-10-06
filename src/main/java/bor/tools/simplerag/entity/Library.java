package bor.tools.simplerag.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import bor.tools.simplerag.entity.enums.TipoBiblioteca;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing a library - a collection of documents organized by knowledge area.
 * Maps to the 'biblioteca' table in PostgreSQL.
 */
@Entity
@Table(name = "library")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
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
    @Column(name = "peso_semantico", precision = 3, scale = 2)
    @Builder.Default
    private Float pesoSemantico = 0.60f;

    /**
     * Weight for textual search (e.g., BM25).
     * The sum of semantic and textual weights must equal 1.0.
     */
    @Column(name = "peso_textual", precision = 3, scale = 2)
    @Builder.Default
    private Float pesoTextual = 0.40f;
       
    /**
     * Type of library: PUBLIC (shared) or PRIVATE (user-specific).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false)
    private TipoBiblioteca tipo;

    /**
     * Arbitrary metadata stored as JSONB.
     * It MUST include the key 'lingua' (language) for document processing.
     */
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private MetaBiblioteca metadados;


     /**
     * Validates that semantic and textual weights sum to 1.0
     */
    @PrePersist
    @PreUpdate
    private void validateWeights() {
        if (pesoSemantico != null && pesoTextual != null) {
            float sum = pesoSemantico + pesoTextual;
            if (Math.abs(sum - 1.0f) > 0.001f)
		throw new IllegalStateException("A soma dos pesos semântico e textual deve ser igual a 1.0");
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
}