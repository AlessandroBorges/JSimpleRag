package bor.tools.simplerag.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
@JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "id", "uuid", "nome", "email", "ativo", "libraryIds", 
                     
    "createdAt", "updatedAt", "deletedAt" })	
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
    private Integer[] libraryIds;

    // Timestamp fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;


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
                .createdAt(src.getCreatedAt())
                .updatedAt(src.getUpdatedAt())
                .deletedAt(src.getDeletedAt())
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
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .deletedAt(this.deletedAt)
                .build();
    }

    /**
     * Validates if the email has a valid format
     */
    @JsonIgnore
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
    @JsonIgnore
    public boolean isValid() {
        return nome != null && !nome.trim().isEmpty()
                && isEmailValid()
                && ativo != null;
    }
}
