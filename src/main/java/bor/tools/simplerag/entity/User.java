/**
 * 
 */
package bor.tools.simplerag.entity;

import java.util.UUID;

import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.*;

/**
 * 
 */
@Entity
@Table(name = "usuario")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User extends Updatable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;
    
    /**
     * Identificador único para biblioteca
     */
    @Column(name = "uuid", nullable = false, unique = true)
    private UUID uuid;

    @Column(nullable = false)
    private String nome;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(nullable = false)
    private String passwordHash;
    
    @Column(nullable = false)
    private Boolean ativo;
    

}
