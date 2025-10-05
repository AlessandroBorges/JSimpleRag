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

    private String titulo;

    private String conteudo;

    private Integer ordemDoc;

    private Integer tokenInicio;

    private Integer tokenFim;

    private Integer tokensTotal;

    @Builder.Default
    private Metadata metadados = new Metadata();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

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
                .titulo(src.getTitulo())
                .conteudo(src.getConteudo())
                .ordemDoc(src.getOrdemDoc())
                .tokenInicio(src.getTokenInicio())
                .tokenFim(src.getTokenFim())
                .tokensTotal(src.getTokensTotal())
                .createdAt(src.getCreatedAt())
                .updatedAt(src.getUpdatedAt())
                .build();
        
        // Convert metadata Map to Metadata object
        if (src.getMetadados() != null) {
            dto.setMetadados(new Metadata(src.getMetadados()));
        }
        
        return dto;
    }
    
    /**
     * Calculate tokens total if not set
     */
    public Integer calculateTokensTotal() {
        if (tokensTotal == null && tokenInicio != null && tokenFim != null) {
            tokensTotal = tokenFim - tokenInicio;
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

    /**
     * Set metadata value
     */
    public void setMetadataValue(String key, Object value) {
        if (metadados == null) {
            metadados = new Metadata();
        }
        metadados.put(key, value);
    }

    public void addMetadata(Map<String, Object> meta) {
	if (metadados == null) {
	    metadados = new Metadata();
	}
	metadados.putAll(meta);
    }
    
    public void addMetadata(String key, Object value) {
	if (metadados == null)
	    metadados = new Metadata();
	metadados.put(key, value);
    }
    
    public void addMetadata(Metadata meta) {
	if (metadados == null)
	    metadados = new Metadata();
	 metadados.putAll(meta);
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
