package bor.tools.simplerag.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Entity representing a chapter - a section of a document (~8k tokens by default).
 * Maps to the 'capitulo' table in PostgreSQL.
 */
@Entity
@Table(name = "capitulo")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Chapter extends Updatable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;


    @Column(name = "documento_id", nullable = false)
    private Integer documentoId;

    @Column(nullable = false, length = 500)
    private String titulo;

    @Column(nullable = false, columnDefinition = "TEXT")
    @ToString.Exclude // Avoid printing large content in toString
    private String conteudo;

    @Column(name = "ordem_doc", nullable = false)
    private Integer ordemDoc;

    @Column(name = "token_inicio")
    private Integer tokenInicio;

    @Column(name = "token_fim")
    private Integer tokenFim;

    @Column(name = "tokens_total")
    private Integer tokensTotal;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadados;


    /**
     * Calculate tokens total if not set
     */
    @PrePersist
    @PreUpdate
    private void calculateTokensTotal() {
        if (tokensTotal == null && tokenInicio != null && tokenFim != null) {
            tokensTotal = tokenFim - tokenInicio;
        }
    }
}