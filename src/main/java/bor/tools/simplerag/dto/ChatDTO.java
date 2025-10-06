package bor.tools.simplerag.dto;

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
     * UUID of the client (user) who owns this chat
     */
    private UUID clientUuid;

    /**
     * UUID of the privative Library associated with this chat
     * Can be null if the chat does not use a privative Library
     */
    private UUID bibliotecaPrivativa;

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
                .clientUuid(src.getClient_uuid())
                .bibliotecaPrivativa(src.getBiblioteca_privativa())
                .metadata(src.getMetadata())
                .resumo(src.getResumo())
                .titulo(src.getTitulo())
                .build();
    }

    /**
     * Convert DTO to entity
     * @return Chat entity
     */
    public Chat toEntity() {
        Chat entity = new Chat();
        entity.setId(this.id);
        entity.setClient_uuid(this.clientUuid);
        entity.setBiblioteca_privativa(this.bibliotecaPrivativa);
        entity.setMetadata(this.metadata);
        entity.setResumo(this.resumo);
        entity.setTitulo(this.titulo);
        return entity;
    }

    /**
     * Validates if required fields are present
     */
    public boolean isValid() {
        return clientUuid != null
                && titulo != null && !titulo.trim().isEmpty();
    }

    /**
     * Check if chat has a privative library
     */
    public boolean hasPrivativeLibrary() {
        return bibliotecaPrivativa != null;
    }

    /**
     * Check if chat has metadata
     */
    public boolean hasMetadata() {
        return metadata != null && !metadata.isEmpty();
    }
}
