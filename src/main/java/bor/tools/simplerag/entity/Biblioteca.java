package bor.tools.simplerag.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Entity representing a library - a collection of documents organized by knowledge area.
 * Maps to the 'biblioteca' table in PostgreSQL.
 */
@Entity
@Table(name = "biblioteca")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Biblioteca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;

    @Column(nullable = false)
    private String nome;

    @Column(name = "area_conhecimento", nullable = false)
    private String areaConhecimento;

    @Column(name = "peso_semantico", precision = 3, scale = 2)
    @Builder.Default
    private Float pesoSemantico = 0.60f;

    @Column(name = "peso_textual", precision = 3, scale = 2)
    @Builder.Default
    private Float pesoTextual = 0.40f;

    /**
     * Arbitrary metadata stored as JSONB.
     * It MUST include the key 'lingua' (language) for document processing.
     */
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private MetaBiblioteca metadados;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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
}