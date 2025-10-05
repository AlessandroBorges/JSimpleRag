package bor.tools.simplerag.entity;

import bor.tools.simplerag.entity.enums.TipoAssociacao;
import jakarta.persistence.*;
import lombok.*;

/**
 * Entity representing the association between User and Library.
 * Maps to the 'usuario_biblioteca' table in PostgreSQL.
 */
@Entity
@Table(name = "user_library")
@Data
@NoArgsConstructor
public class UserLibrary extends Updatable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;
    
    @Column(name = "user_id", nullable = false)
    private Integer userId;
    
    @Column(name = "library_id", nullable = false)
    private Integer libraryId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_associacao", nullable = false)
    TipoAssociacao tipoAssociacao;	

}
