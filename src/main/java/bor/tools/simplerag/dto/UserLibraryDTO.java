package bor.tools.simplerag.dto;

import bor.tools.simplerag.entity.UserLibrary;
import bor.tools.simplerag.entity.enums.TipoAssociacao;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for UserLibrary entity.
 *
 * Represents the N:N relationship between User and Library.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserLibraryDTO {

    /**
     * Unique identifier for the association
     */
    private Integer id;

    /**
     * User ID
     */
    private Integer userId;

    /**
     * Library ID
     */	
    private Integer libraryId;

    private TipoAssociacao tipoAssociacao;

    /**
     * Create DTO from entity
     * @param src - source entity
     * @return DTO instance
     */
    public static UserLibraryDTO from(UserLibrary src) {
        if (src == null) {
            return null;
        }
        return UserLibraryDTO.builder()
                .id(src.getId())
                .userId(src.getUserId())
                .libraryId(src.getLibraryId())
                .tipoAssociacao(src.getTipoAssociacao())
                .build();
    }

    /**
     * Convert DTO to entity
     * @return UserLibrary entity
     */
    public UserLibrary toEntity() {
        UserLibrary entity = new UserLibrary();
        entity.setId(this.id);
        entity.setUserId(this.userId);
        entity.setLibraryId(this.libraryId);
        entity.setTipoAssociacao(this.tipoAssociacao);
        return entity;
    }

    /**
     * Validates if required fields are present
     */
    public boolean isValid() {
        return userId != null
                && libraryId != null
                && tipoAssociacao != null;
    }

    /**
     * Check if association is PROPRIETARIO
     */
    public boolean isProprietario() {
        return TipoAssociacao.PROPRIETARIO.equals(tipoAssociacao);
    }

    /**
     * Check if association is COLABORADOR
     */
    public boolean isColaborador() {
        return TipoAssociacao.COLABORADOR.equals(tipoAssociacao);
    }

    /**
     * Check if association is LEITOR
     */
    public boolean isLeitor() {
        return TipoAssociacao.LEITOR.equals(tipoAssociacao);
    }

    /**
     * Check if user has write permission (PROPRIETARIO or COLABORADOR)
     */
    public boolean hasWritePermission() {
        return isProprietario() || isColaborador();
    }

    /**
     * Check if user has only read permission
     */
    public boolean isReadOnly() {
        return isLeitor();
    }
}
