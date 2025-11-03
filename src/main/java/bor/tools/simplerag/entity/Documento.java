package bor.tools.simplerag.entity;

import java.time.LocalDate;

import org.hibernate.annotations.Type;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Entity representing a document - complete content like books, articles, manuals, etc.
 * Maps to the 'documento' table in PostgreSQL.
 */
@Entity
@Table(name = "documento")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Documento extends Updatable {

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
    private MetaDoc metadados;


    /**
     * Convenience method to check if document is active
     */
    public boolean isVigente() {
        return Boolean.TRUE.equals(flagVigente);
    }

    /**
     * Ensure metadados is initialized
     * @return
     */
    public MetaDoc getMetadados() {
	if (metadados == null) {
	    metadados = new MetaDoc();
	}
	return metadados;
    }

}