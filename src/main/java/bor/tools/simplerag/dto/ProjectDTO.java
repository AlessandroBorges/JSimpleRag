
package bor.tools.simplerag.dto;

import java.util.UUID;

import bor.tools.simplerag.entity.MetaProject;
import bor.tools.simplerag.entity.Project;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Project entity.
 *
 * Represents a project that can group multiple chats and related resources
 * under a common theme or purpose.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectDTO {

    /**
     * Unique identifier for the project
     */
    private UUID id;

    /**
     * UUID of the privative Library associated with this project
     * Can be null if the project does not use a privative Library
     */
    private UUID bibliotecaPrivativa;

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
    private UUID userId;

    /**
     * Order/sequence number for organizing projects
     */
    private Integer ordem;

    /**
     * Create DTO from entity
     * @param src - source entity
     * @return DTO instance
     */
    public static ProjectDTO from(Project src) {
        if (src == null) {
            return null;
        }
        return ProjectDTO.builder()
                .id(src.getId())
                .bibliotecaPrivativa(src.getBiblioteca_privativa())
                .titulo(src.getTitulo())
                .descricao(src.getDescricao())
                .metadata(src.getMetadata())
                .userId(src.getUser_id())
                .ordem(src.getOrdem())
                .build();
    }

    /**
     * Convert DTO to entity
     * @return Project entity
     */
    public Project toEntity() {
        Project entity = new Project();
        entity.setId(this.id);
        entity.setBiblioteca_privativa(this.bibliotecaPrivativa);
        entity.setTitulo(this.titulo);
        entity.setDescricao(this.descricao);
        entity.setMetadata(this.metadata);
        entity.setUser_id(this.userId);
        entity.setOrdem(this.ordem);
        return entity;
    }

    /**
     * Validates if required fields are present
     */
    public boolean isValid() {
        return userId != null
                && titulo != null && !titulo.trim().isEmpty();
    }

    /**
     * Check if project has a privative library
     */
    public boolean hasPrivativeLibrary() {
        return bibliotecaPrivativa != null;
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
