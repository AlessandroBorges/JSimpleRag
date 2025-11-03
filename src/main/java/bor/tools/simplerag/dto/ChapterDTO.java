package bor.tools.simplerag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import bor.tools.simplerag.entity.Chapter;
import bor.tools.simplerag.entity.MetaDoc;
import bor.tools.simplerag.entity.Metadata;

/**
 * DTO for Chapter entity.
 *
 * Contains chapter data with references to child embeddings.
 * Does NOT contain reference to parent Documento to avoid circular references.
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class ChapterDTO {

    private Integer id;

    private Integer documentoId;

    private Integer bibliotecaId;

    private String titulo;

    private String conteudo;

    private Integer ordemDoc;

    /**
     * In Chapter context, 1 token = 1 char (utf8).
     * For Embedding Tokens, consider using LLMService instead, 
     */
    private Integer tokenInicio;

    /**
     * In Chapter context, 1 token = 1 char (utf8).
     * For Embedding Tokens, consider using LLMService instead, 
     */
    private Integer tokenFim;

    private Integer tokensTotal;

    @Builder.Default
    private MetaDoc metadados = new MetaDoc();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
    
    private LocalDateTime deletedAt;
    

    /**
     * Child embeddings for this chapter.
     * Includes both chapter-level and chunk-level embeddings.
     */
    @Builder.Default
    private List<DocumentEmbeddingDTO> embeddings = new ArrayList<>();

    /**
     * Constructor with title and content
     */
    public ChapterDTO(String titulo, String conteudo) {
	this.titulo = titulo;
	this.conteudo = conteudo;
    }
    
    /**
     * Constructor with title and content
     */
    public ChapterDTO(Integer orderDoc, String titulo, String conteudo) {
	this.ordemDoc = orderDoc;
	this.titulo = titulo;
	this.conteudo = conteudo;
    }
    
    /**
     * Convert Chapter entity to ChapterDTO
     * @param src
     * @return
     */
    public static ChapterDTO from(Chapter src) {
        if (src == null) {
            return null;
        }
        
        ChapterDTO dto = ChapterDTO.builder()
                .id(src.getId())
                .documentoId(src.getDocumentoId())
                .bibliotecaId(src.getBibliotecaId())
                .titulo(src.getTitulo())
                .conteudo(src.getConteudo())
                .ordemDoc(src.getOrdemDoc())
                .tokenInicio(src.getTokenInicio())
                .tokenFim(src.getTokenFim())
                .metadados(src.getMetadados())
                .createdAt(src.getCreatedAt())
                .updatedAt(src.getUpdatedAt())
                .deletedAt(src.getDeletedAt())
                .build();
        
        // Convert metadata if present
        if (src.getMetadados() != null) {
            dto.setMetadados(src.getMetadados());
        } else {
	    dto.setMetadados(new MetaDoc());
	}
        
        return dto;
    }

    /**
     * Convert DTO to entity
     * @return Chapter entity
     */
    public Chapter toEntity() {
        Chapter entity = new Chapter();
        entity.setId(this.id);
        entity.setDocumentoId(this.documentoId);
        entity.setBibliotecaId(this.bibliotecaId);
        entity.setTitulo(this.titulo);
        entity.setConteudo(this.conteudo);
        entity.setOrdemDoc(this.ordemDoc);
        entity.setTokenInicio(this.tokenInicio);
        entity.setTokenFim(this.tokenFim);
        entity.setMetadados(new MetaDoc(this.metadados));
        entity.setCreatedAt(this.createdAt);
        entity.setUpdatedAt(this.updatedAt);
        entity.setDeletedAt(this.deletedAt);
        return entity;
    }

    /**
     * Calculate tokens total if not set.<br>
     * In Chapter context, 1 token = 1 char (utf8).
     * For Embedding Tokens, consider using LLMService instead. <br>
     * 
     * In this case, "tokensTotal" = "tokenFim" - "tokenInicio"'
     */
    public Integer calculateTokensTotal() {
        if (tokensTotal == null && tokenInicio != null && tokenFim != null) {
            tokensTotal = tokenFim - tokenInicio;
        }else if(conteudo != null) {
		tokensTotal = conteudo.length();
	} else {
		tokensTotal = 0;
	}
        return tokensTotal;
    }

    /**
     * Get content length in characters
     */
    public int getContentLength() {
        return conteudo != null ? conteudo.length() : 0;
    }

    /**
     * Check if has content
     */
    public boolean hasContent() {
        return conteudo != null && !conteudo.trim().isEmpty();
    }

    /**
     * Check if has embeddings
     */
    public boolean hasEmbeddings() {
        return embeddings != null && !embeddings.isEmpty();
    }

    /**
     * Get number of embeddings
     */
    public int getEmbeddingsCount() {
        return embeddings != null ? embeddings.size() : 0;
    }

    /**
     * Add embedding to this chapter
     */
    public void addEmbedding(DocumentEmbeddingDTO embedding) {
        if (embeddings == null) {
            embeddings = new ArrayList<>();
        }
        embeddings.add(embedding);
    }

    /**
     * Get chapter-level embeddings only
     */
    public List<DocumentEmbeddingDTO> getChapterEmbeddings() {
        if (embeddings == null) {
            return new ArrayList<>();
        }
        return embeddings.stream()
                .filter(DocumentEmbeddingDTO::isChapterLevel)
                .toList();
    }

    /**
     * Get chunk-level embeddings only
     */
    public List<DocumentEmbeddingDTO> getChunkEmbeddings() {
        if (embeddings == null) {
            return new ArrayList<>();
        }
        return embeddings.stream()
                .filter(DocumentEmbeddingDTO::isChunkLevel)
                .toList();
    }

    /**
     * Get embeddings ordered by ordem_cap
     */
    public List<DocumentEmbeddingDTO> getEmbeddingsOrdered() {
        if (embeddings == null) {
            return new ArrayList<>();
        }
        return embeddings.stream()
                .sorted((e1, e2) -> {
                    Integer ordem1 = e1.getOrdemCap();
                    Integer ordem2 = e2.getOrdemCap();
                    if (ordem1 == null && ordem2 == null) return 0;
                    if (ordem1 == null) return 1;
                    if (ordem2 == null) return -1;
                    return ordem1.compareTo(ordem2);
                })
                .toList();
    }

    /**
     * Get metadata value by key
     */
    public Object getMetadataValue(String key) {
        return metadados != null ? metadados.get(key) : null;
    }

    
    public MetaDoc getMetadados() {
        if (metadados == null) {
            metadados = new MetaDoc();
        }
        return metadados;
    }
    
    /**
     * Set metadata value
     */
    public void setMetadataValue(String key, Object value) {        
	getMetadados().put(key, value);
    }

    public void addMetadata(Map<String, Object> meta) {	
	getMetadados().putAll(meta);
    }
    
    public void addMetadata(String key, Object value) {	
	getMetadados().put(key, value);
    }
    
    public void addMetadata(Metadata meta) {
	getMetadados().putAll(meta);
    }
    
    /**
     * Check if token range is valid
     */
    @JsonIgnore	
    public boolean isTokenRangeValid() {
        return tokenInicio != null && tokenFim != null && tokenFim > tokenInicio;
    }

    /**
     * Get token range size
     */
    @JsonIgnore	
    public Integer getTokenRangeSize() {
        return isTokenRangeValid() ? tokenFim - tokenInicio : null;
    }

    /**
     * Initialize metadata with default values
     */
    public void initializeMetadata() {
	addMetadata("titulo", titulo);		
    }
}