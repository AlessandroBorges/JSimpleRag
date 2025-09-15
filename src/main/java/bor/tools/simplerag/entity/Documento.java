package bor.tools.simplerag.entity;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Entity representing a document - complete content like books, articles, manuals, etc.
 * Maps to the 'documento' table in PostgreSQL.
 */
@Entity
@Table(name = "documento")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Documento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;

    @Column(name = "biblioteca_id", nullable = false)
    @ToString.Exclude
    private Integer bibliotecaId;

    @Column(nullable = false, length = 500)
    private String titulo;

    @Column(name = "conteudo_markdown", nullable = false, columnDefinition = "TEXT")
    @ToString.Exclude // Avoid printing large content in toString
    private String conteudoMarkdown;

    @Column(name = "flag_vigente")
    @Builder.Default
    private Boolean flagVigente = true;

    @Column(name = "data_publicacao", nullable = false)
    private LocalDate dataPublicacao;

    @Column(name = "tokens_total")
    private Integer tokensTotal;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadados;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;


    /**
     * Convenience method to check if document is active
     */
    public boolean isVigente() {
        return Boolean.TRUE.equals(flagVigente);
    }


}