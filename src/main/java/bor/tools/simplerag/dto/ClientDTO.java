/**
 * 
 */
package bor.tools.simplerag.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import bor.tools.simplerag.entity.Metadata;
import bor.tools.simplerag.entity.enums.TipoAssociacao;

/**
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientDTO {

    private Integer id;
    
    private UUID uuid;

    private String nome;
    
    private String email;
    
    private Metadata metadata;
    
    private TipoAssociacao tipoAssociacao;
    
    private String apiKey; 
    
    private LocalDateTime apiKeyExpiresAt;
    
    private String passwordHash;
    
    private Boolean ativo;
    
}