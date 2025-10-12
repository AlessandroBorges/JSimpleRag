package bor.tools.simplerag.entity;

import org.hibernate.annotations.Type;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * Entity representing a chapter - a section of a document (~8k tokens by default).
 * Maps to the 'capitulo' table in PostgreSQL.
 */
@Entity
@Table(name = "chapter")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
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

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Metadata metadados;


}