package bor.tools.simplerag.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import bor.tools.simplerag.entity.Chat;
import bor.tools.simplerag.entity.Metadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Chat entity.
 *
 * Contains chat data for API communication.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatDTO {

    /**
     * Unique identifier for the chat
     */
    private UUID id;

    /**
     * UUID of the user who owns this chat
     */
    private UUID userUuid;

    /**
     * UUID of the privative Library associated with this chat
     * Can be null if the chat does not use a privative Library
     */
    private UUID biblioteca_privativa;

    /**
     * Arbitrary metadata stored as JSONB
     */
    private Metadata metadata;

    /**
     * Summary or description of the chat's purpose or context
     */
    private String resumo;

    /**
     * Title of the chat
     */
    private String titulo;
    
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
    
    private LocalDateTime deletedAt;
    

    /**
     * Create DTO from entity
     * @param src - source entity
     * @return DTO instance
     */
    public static ChatDTO from(Chat src) {
        if (src == null) {
            return null;
        }
        return ChatDTO.builder()
                .id(src.getId())
                .userUuid(src.getUser_uuid())
                .biblioteca_privativa(src.getBiblioteca_privativa())
                .metadata(src.getMetadata())
                .resumo(src.getResumo())
                .titulo(src.getTitulo())
                .createdAt(src.getCreatedAt())
                .updatedAt(src.getUpdatedAt())
                .deletedAt(src.getDeletedAt())
                .build();
    }

    /**
     * Convert DTO to entity
     * @return Chat entity
     */
    public Chat toEntity() {
        Chat entity = new Chat();
        entity.setId(this.id);
        entity.setUser_uuid(this.userUuid);
        entity.setBiblioteca_privativa(this.biblioteca_privativa);
        entity.setMetadata(this.metadata);
        entity.setResumo(this.resumo);
        entity.setTitulo(this.titulo);
        entity.setCreatedAt(this.createdAt);
        entity.setUpdatedAt(this.updatedAt);
        entity.setDeletedAt(this.deletedAt);
        return entity;
    }

    /**
     * Validates if required fields are present
     */
    public boolean isValid() {
        return  userUuid != null
                && titulo != null && !titulo.trim().isEmpty();
    }

    /**
     * Check if chat has a privative library
     */
    public boolean hasPrivativeLibrary() {
        return biblioteca_privativa != null;
    }

    /**
     * Check if chat has metadata
     */
    public boolean hasMetadata() {
        return metadata != null && !metadata.isEmpty();
    }
}
