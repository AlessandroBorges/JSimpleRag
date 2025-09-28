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

/**
 * DTO for Capitulo entity.
 *
 * Contains chapter data with references to child embeddings.
 * Does NOT contain reference to parent Documento to avoid circular references.
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class CapituloDTO {

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
    private List<DocEmbeddingDTO> embeddings = new ArrayList<>();

    /**
     * Constructor with title and content
     */
    public CapituloDTO(String titulo, String conteudo) {
	this.titulo = titulo;
	this.conteudo = conteudo;
    }
    
    /**
     * Constructor with title and content
     */
    public CapituloDTO(Integer orderDoc, String titulo, String conteudo) {
	this.ordemDoc = orderDoc;
	this.titulo = titulo;
	this.conteudo = conteudo;
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
    public void addEmbedding(DocEmbeddingDTO embedding) {
        if (embeddings == null) {
            embeddings = new ArrayList<>();
        }
        embeddings.add(embedding);
    }

    /**
     * Get chapter-level embeddings only
     */
    public List<DocEmbeddingDTO> getChapterEmbeddings() {
        if (embeddings == null) {
            return new ArrayList<>();
        }
        return embeddings.stream()
                .filter(DocEmbeddingDTO::isChapterLevel)
                .toList();
    }

    /**
     * Get chunk-level embeddings only
     */
    public List<DocEmbeddingDTO> getChunkEmbeddings() {
        if (embeddings == null) {
            return new ArrayList<>();
        }
        return embeddings.stream()
                .filter(DocEmbeddingDTO::isChunkLevel)
                .toList();
    }

    /**
     * Get embeddings ordered by ordem_cap
     */
    public List<DocEmbeddingDTO> getEmbeddingsOrdered() {
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
}
