/**
 * 
 */
package bor.tools.simplerag.entity;

import java.util.UUID;

import bor.tools.simplerag.dto.UserDTO;
import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Entity representing a user in the system.
 * Maps to the 'user' table in PostgreSQL.
 * 
 */
@Entity
@Table(name = "\"user\"")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
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
    
    /**
     * Hash da senha do usuário.
     */
    @Column(nullable = false)
    private String passwordHash;
    
    @Column(nullable = false)
    private Boolean ativo;
    
    /**
     * Retorna id, ou null se id < 1
     * @return
     */
    public Integer getId() {
	return id == null || id < 1 ? null : id;
    }

       
    /**
     * Converte User entity para UserDTO
     * @return
     */
    public UserDTO toDTO() {
	return UserDTO.builder()
		.id(this.id)
		.uuid(this.uuid)
		.nome(this.nome)
		.email(this.email)
		.ativo(this.ativo)
		.createdAt(this.getCreatedAt())
		.updatedAt(this.getUpdatedAt())
		.build();
    }
    
    /**
     * Converte UserDTO para User entity
     * @param dto
     * @return
     */
    public static User fromDTO(UserDTO dto) {
	return User.builder()
		.id(dto.getId())
		.uuid(dto.getUuid())
		.nome(dto.getNome())
		.email(dto.getEmail())
		.ativo(dto.getAtivo())
		.build();
    }
    

}