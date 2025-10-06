package bor.tools.simplerag.dto;

import java.util.UUID;

import bor.tools.simplerag.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for User entity.
 *
 * Contains user data without sensitive information like password hash.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {

    private Integer id;

    private UUID uuid;

    private String nome;

    private String email;

    private Boolean ativo;
    
    /**
     *  Plain text password for input only (not stored or output)
     */
    private String password; // Only used for input, not output
    
    /**
     *  IDs of libraries associated with the user
     */
    private Integer[] libraryIds; //


    /**
     * Create DTO from entity
     * @param src - source entity
     * @return DTO instance
     */
    public static UserDTO from(User src) {
        if (src == null) {
            return null;
        }
        return UserDTO.builder()
                .id(src.getId())
                .uuid(src.getUuid())
                .nome(src.getNome())
                .email(src.getEmail())
                .ativo(src.getAtivo())                
                .build();
    }

    /**
     * Convert DTO to entity (without password)
     * @return User entity
     */
    public User toEntity() {
        return User.builder()
                .id(this.id)
                .uuid(this.uuid)
                .nome(this.nome)
                .email(this.email)
                .ativo(this.ativo)                
                .build();
    }

    /**
     * Validates if the email has a valid format
     */
    public boolean isEmailValid() {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        // Basic email validation
        return email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    /**
     * Validates if required fields are present
     */
    public boolean isValid() {
        return nome != null && !nome.trim().isEmpty()
                && isEmailValid()
                && ativo != null;
    }
}
