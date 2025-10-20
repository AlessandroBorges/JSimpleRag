package bor.tools.simplerag.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import bor.tools.simplerag.entity.enums.TipoAssociacao;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pairing a User with their UserLibrary association.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserWithAssociationDTO {

    /**
     * User information
     */
    private UserDTO user;

    /**
     * Association type (PROPRIETARIO, COLABORADOR, LEITOR)
     */
    private TipoAssociacao tipoAssociacao;

    /**
     * User-Library association ID
     */
    private Integer associationId;
}
