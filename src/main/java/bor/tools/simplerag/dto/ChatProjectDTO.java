
package bor.tools.simplerag.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import bor.tools.simplerag.entity.MetaProject;
import bor.tools.simplerag.entity.ChatProject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for ChatProject entity.
 *
 * Represents a project that can group multiple chats and related resources
 * under a common theme or purpose.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatProjectDTO {

    /**
     * Unique identifier for the project
     */
    private UUID id;

    /**
     * UUID of the privative Library associated with this project
     * Can be null if the project does not use a privative Library
     */
    private UUID biblioteca_privativa;

    /**
     * Title of the project
     */
    private String titulo;

    /**
     * Summary or description of the project's purpose or context
     */
    private String descricao;

    /**
     * Arbitrary metadata stored as JSONB
     * May include language, model, temperature, top_p, stats, libraries, documents
     */
    private MetaProject metadata;

    /**
     * UUID of the user who owns this project
     */
    private UUID userUuid;

    /**
     * Order/sequence number for organizing projects
     */
    private Integer ordem;
    
	// campos declarados
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;    
    private LocalDateTime deletedAt;

    /**
     * Create DTO from entity
     * @param src - source entity
     * @return DTO instance
     */
    public static ChatProjectDTO from(ChatProject src) {
        if (src == null) {
            return null;
        }
        return ChatProjectDTO.builder()
                .id(src.getId())
                .biblioteca_privativa(src.getBiblioteca_privativa())
                .titulo(src.getTitulo())
                .descricao(src.getDescricao())
                .metadata(src.getMetadata())
                .userUuid(src.getUser_uuid())
                .ordem(src.getOrdem())
        	.createdAt(src.getCreatedAt())
        	.updatedAt(src.getUpdatedAt())
        	.deletedAt(src.getDeletedAt())
                .build();
    }

    /**
     * Convert DTO to entity
     * @return ChatProject entity
     */
    public ChatProject toEntity() {
        ChatProject entity = new ChatProject();
        entity.setId(this.id);
        entity.setBiblioteca_privativa(this.biblioteca_privativa);
        entity.setTitulo(this.titulo);
        entity.setDescricao(this.descricao);
        entity.setMetadata(this.metadata);
        entity.setUser_uuid(this.userUuid);
        entity.setOrdem(this.ordem);
	entity.setCreatedAt(this.createdAt);
	entity.setUpdatedAt(this.updatedAt);
	entity.setDeletedAt(this.deletedAt);
        return entity;
    }

    /**
     * Validates if required fields are present
     */
    public boolean isValid() {
        return userUuid != null
                && titulo != null && !titulo.trim().isEmpty();
    }

    /**
     * Check if project has a privative library
     */
    public boolean hasPrivativeLibrary() {
        return biblioteca_privativa != null;
    }

    /**
     * Check if project has a description
     */
    public boolean hasDescription() {
        return descricao != null && !descricao.trim().isEmpty();
    }

    /**
     * Check if project has metadata
     */
    public boolean hasMetadata() {
        return metadata != null && !metadata.isEmpty();
    }

    /**
     * Check if project has an order defined
     */
    public boolean hasOrder() {
        return ordem != null;
    }
}
