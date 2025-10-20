/**
 * 
 */
package bor.tools.simplerag.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import bor.tools.simplerag.dto.ClientDTO;
import bor.tools.simplerag.entity.enums.TipoAssociacao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonType;

/**
 * 
 */
@Entity
@Table(name = "client")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Client extends Updatable {

    private static final String APIKEY_HISTORY_KEY = "apikey_history";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Integer id;
    
    /**
     * Identificador único para biblioteca
     */
    @Column(name = "uuid", nullable = false, unique = true)
    private UUID uuid;

    @Column(nullable = false)
    private String nome;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Type(JsonType.class)
    @Column(nullable = false, columnDefinition = "JSONB")
    @Builder.Default
    private Metadata metadata = new Metadata();
    
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_associacao", nullable = false, length = 50)
    @Builder.Default
    private TipoAssociacao tipoAssociacao = TipoAssociacao.LEITOR;
    
    
    @Column(name="api_key", nullable = false, unique = true)
    private String apiKey; 
    
    @Column(name="api_key_expires_at", nullable = true)
    private LocalDateTime apiKeyExpiresAt;
    
    /**
     * Hash da senha do usuário.
     */
    @Column(nullable = false)
    private String passwordHash;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = Boolean.TRUE;
    
    
    /** 
     * User who is changing this instance.
     * Not persisted in the database.
     */
    @Transient
    @JsonIgnore
    private String changedBy;
   
        
    /**
     * Retorna id, ou null se id < 1
     * @return
     */
    public Integer getId() {
	return id == null || id < 1 ? null : id;
    }

       
    /**
     * Converte Client entity para ClientDTO
     * @return
     */
    public ClientDTO toDTO() {
	return ClientDTO.builder()
		.id(this.id)
		.uuid(this.uuid)
		.nome(this.nome)
		.email(this.email)
		.metadata(this.metadata)
		.tipoAssociacao(this.tipoAssociacao)
		.apiKey(this.apiKey)
		.apiKeyExpiresAt(this.apiKeyExpiresAt)
		.passwordHash(this.passwordHash)
		.ativo(this.ativo)
		.build();
    }
    
    /**
     * Converte ClientDTO para Client entity
     * @param dto
     * @return
     */
    public static Client fromDTO(ClientDTO dto) {
	return Client.builder()
		.id(dto.getId())
		.uuid(dto.getUuid())
		.nome(dto.getNome())
		.email(dto.getEmail())
		.metadata(dto.getMetadata())
		.tipoAssociacao(dto.getTipoAssociacao())
		.apiKey(dto.getApiKey())
		.apiKeyExpiresAt(dto.getApiKeyExpiresAt())
		.passwordHash(dto.getPasswordHash())
		.ativo(dto.getAtivo())
		.build();
    }

    /**
     * Returns the metadata, initializing it if null.
     * @return
     */
    public Metadata getMetadata() {
	if (this.metadata == null) {
	    this.metadata = new Metadata();
	}
	return this.metadata;
    }
    
    /**
     * Returns the identifier of the user who is changing this instance.
     * Defaults to "system" if not set.
     * @return
     */
     public String getCreatedBy() {
	this.changedBy = this.changedBy == null ? "system" : this.changedBy;
	return changedBy;
     }
        
    /**
     * Sets a new API key, storing the previous key in history.
     * This change is also reflected in the current API key change history record.
     * @param apiKey - new API key
     * 
     * @throws IllegalStateException if the API key has already been set. Use {@link #updateApiKey(String, LocalDateTime, String)} instead.
     * 
     * @see #updateApiKey(String, LocalDateTime, String)
     */
    public void setApiKey(String apiKey) {
	if (this.apiKey != null) {
	  throw new IllegalStateException("API key already set. Use updateApiKey() "
	  	+ "to change the API key along with its expiration date and changedBy.");
	}
	this.apiKey = apiKey;
    }

    /**
     * Sets the API key expiration date.<br>
     * This change is also reflected in the current API key change history record.
     * 
     * @param apiKeyExpiresAt - expiration date of the API key
     * @throws IllegalStateException if the API key has already been set. Use {@link #updateApiKey(String, LocalDateTime, String)}
     * 
     * @see #updateApiKey(String, LocalDateTime, String)
     */
    public void setApiKeyExpiresAt(LocalDateTime apiKeyExpiresAt) {
	if (this.apiKeyExpiresAt != null) {
	    throw new IllegalStateException("API key already set. Use updateApiKey() "
		  	+ "to change the API key along with its expiration date and changedBy.");
	}
	this.apiKeyExpiresAt = apiKeyExpiresAt;
	
    }

    
    
    /**
     * Updates the API key, storing the previous key in history.
     * 
     * @param newApiKey - new API - if null or blank, the current key is kept
     * @param newExpiresAt - expiration date of the new API key
     * @param changedBy - identifier of the user who is changing the API key
     * 
     */
    public void updateApiKey(String newApiKey, LocalDateTime newExpiresAt,  String changedBy) {
	this.changedBy = changedBy == null ? "system" : changedBy;
	this.apiKeyExpiresAt = newExpiresAt;
	if(newApiKey == null || newApiKey.isBlank()) {
	  newApiKey = this.apiKey; // keep current key if new is null or blank
	}
	if(newExpiresAt == null) {
	    throw new IllegalArgumentException("API key expiration date cannot be null when updating the API key.");
	}
	
	storeApiKeyHistory(this.apiKey, newApiKey, changedBy);
	this.apiKey = newApiKey;
    }
    
    /**
     * API Key history record, for audit purposes.
     * Placed at Metadata or another logging mechanism.
     */    
    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    @Setter
    @ToString   
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApiKeyHistory {	
	/**
	 * New API key
	 */
	private String apiKey;
	
	/**
	 * Previous API key being replaced
	 */
	private String previousApiKey;
	
	/**
	 * UUID or name of the user who created the new API key
	 * 
	 */
	private String createdBy;	
	
	/**
	 * This is the date of creation of <b>new apiKey</b>  
	 * as well the expiration date of the <b>old API key.</b>
	 */
	private LocalDateTime createdAt;
	
	/**
	 * Expiration date of the new API key
	 */
	private LocalDateTime expiresAt;
	
	
	@Override
	public boolean equals(Object obj) {
	    if (this == obj) return true;
	    if (obj == null) return false;
	    if (getClass() != obj.getClass()) return false;
	    
	    ApiKeyHistory other = (ApiKeyHistory) obj;
	    
	    if (apiKey == null) {
	        return other.apiKey == null;
	    } else {
	        return apiKey.equalsIgnoreCase(other.apiKey);
	    }
	}
	
    }// class ApiKeyHistory
    
   
    /**
     * Stores the API key change in history.
     * @param oldApiKey
     * @param newApiKey
     * @param changedBy
     */
    private void storeApiKeyHistory(String oldApiKey, String newApiKey, String changedBy) {
	// Store the API key history record in Metadata or logging mechanism
	
	ApiKeyHistory historyNew = ApiKeyHistory.builder()
		.apiKey(newApiKey)
		.previousApiKey(oldApiKey)
		.createdBy(changedBy)	
		.createdAt(LocalDateTime.now())
		.expiresAt(this.apiKeyExpiresAt)
		.build();
		
	@SuppressWarnings("unchecked")
	List<ApiKeyHistory> history = (List<ApiKeyHistory>) getMetadata().getOrDefault(APIKEY_HISTORY_KEY, new ArrayList<ApiKeyHistory>());
	history.add(historyNew);
	getMetadata().put(APIKEY_HISTORY_KEY,history);	
    }
    
    /**
     * Returns a unmodifiable the API key history records.
     * @return List of ApiKeyHistory
     */
    @Transient
    @JsonIgnore
    public List<ApiKeyHistory> getApiKeyHistory() {	
	Object historyObj = getMetadata().get(APIKEY_HISTORY_KEY);	
	
	
	// check type - it can be a list of ApiKeyHistory or a list of LinkedHashMap 
	// or array of ApiKeyHistory or a array of LinkedHashMap
	if(historyObj == null) {
	    return Collections.emptyList();
	}	
	List<ApiKeyHistory> history = new ArrayList<>();
	if(historyObj instanceof List<?>) {
	    List<?> list = (List<?>) historyObj;	   
	    for (int i = 0; i < list.size(); i++) {
		Object item = list.get(i);
		if (item instanceof ApiKeyHistory itemHistory) {
		    history.add(itemHistory);
		} else if (item instanceof LinkedHashMap<?,?> map) {		    
		    ApiKeyHistory historyItem = ApiKeyHistory.builder()
			    .apiKey((String) map.get("apiKey"))
			    .previousApiKey((String) map.get("previousApiKey"))
			    .createdBy((String) map.get("createdBy"))
			    .createdAt(parseLocalDateTime(map.get("createdAt")))
			    .expiresAt(parseLocalDateTime(map.get("expiresAt")))
			    .build();
		    history.add(historyItem);
		}
	    }
	    getMetadata().put(APIKEY_HISTORY_KEY, List.of(history)); // update to typed list	
	 } 	

	return history;
    }

    private LocalDateTime parseLocalDateTime(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (obj instanceof List<?> list && list.size() >= 7) {
            // Jackson serializes LocalDateTime as [year, month, day, hour, minute, second, nano]
            return LocalDateTime.of(
                (Integer) list.get(0),
                (Integer) list.get(1),
                (Integer) list.get(2),
                (Integer) list.get(3),
                (Integer) list.get(4),
                (Integer) list.get(5),
                (Integer) list.get(6)
            );
        }
        if (obj instanceof String str) {
            return LocalDateTime.parse(str);
        }
        // Fallback: try to convert to string and parse
        return LocalDateTime.parse(obj.toString());
    }
    
}