package bor.tools.simplerag.dto;

import bor.tools.simplerag.entity.enums.TipoAssociacao;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pairing a Library with the UserLibrary association.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LibraryWithAssociationDTO {

    /**
     * Library information
     */
    private LibraryDTO library;

    /**
     * Association type (PROPRIETARIO, COLABORADOR, LEITOR)
     */
    private TipoAssociacao tipoAssociacao;

    /**
     * User-Library association ID
     */
    private Integer associationId;
}
