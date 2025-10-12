package bor.tools.simplerag.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import bor.tools.simplerag.entity.Metadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Documento entity.
 *
 * Contains document data with references to parent Library and child Capitulos.
 * This provides the complete hierarchical context needed by Splitters.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentoDTO {

    private Integer id;

    private Integer bibliotecaId;

    private String titulo;

    private String conteudoMarkdown;

    @Builder.Default
    private Boolean flagVigente = true;

    private LocalDate dataPublicacao;

    private Integer tokensTotal;

    private Metadata metadados;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /**
     * Parent biblioteca reference for full context
     */
    private LibraryDTO biblioteca;
    
    /**
     * Attached documents (e.g. annexes)
     */
    @Builder.Default
    private List<DocumentoDTO> anexos = new ArrayList<>();

    /**
     * Child chapters with their embeddings
     */
    @Builder.Default
    private List<ChapterDTO> capitulos = new ArrayList<>();

    /**
     * Convenience method to check if document is active
     */
    public boolean isVigente() {
        return Boolean.TRUE.equals(flagVigente);
    }

    /**
     * Get content length in characters
     */
    public int getContentLength() {
        return conteudoMarkdown != null ? conteudoMarkdown.length() : 0;
    }

    /**
     * Check if has content
     */
    public boolean hasContent() {
        return conteudoMarkdown != null && !conteudoMarkdown.trim().isEmpty();
    }

    /**
     * Check if has chapters
     */
    public boolean hasChapters() {
        return capitulos != null && !capitulos.isEmpty();
    }

    /**
     * Get number of chapters
     */
    public int getChaptersCount() {
        return capitulos != null ? capitulos.size() : 0;
    }

    /**
     * Add chapter to this document
     */
    public void addCapitulo(ChapterDTO capitulo) {
        if (capitulos == null) {
            capitulos = new ArrayList<>();
        }
        capitulos.add(capitulo);
    }

    /**
     * Get chapters ordered by ordem_doc
     */
    public List<ChapterDTO> getCapitulosOrdered() {
        if (capitulos == null) {
            return new ArrayList<>();
        }
        return capitulos.stream()
                .sorted((c1, c2) -> {
                    Integer ordem1 = c1.getOrdemDoc();
                    Integer ordem2 = c2.getOrdemDoc();
                    if (ordem1 == null && ordem2 == null) return 0;
                    if (ordem1 == null) return 1;
                    if (ordem2 == null) return -1;
                    return ordem1.compareTo(ordem2);
                })
                .toList();
    }

    /**
     * Get chapter by order
     */
    public ChapterDTO getCapituloByOrdem(Integer ordem) {
        if (capitulos == null) {
            return null;
        }
        return capitulos.stream()
                .filter(c -> ordem.equals(c.getOrdemDoc()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get first chapter
     */
    public ChapterDTO getFirstChapter() {
        return getCapitulosOrdered().stream().findFirst().orElse(null);
    }

    /**
     * Get last chapter
     */
    public ChapterDTO getLastChapter() {
        List<ChapterDTO> ordered = getCapitulosOrdered();
        return ordered.isEmpty() ? null : ordered.get(ordered.size() - 1);
    }

    /**
     * Get all embeddings from all chapters
     */
    public List<DocumentEmbeddingDTO> getAllEmbeddings() {
        if (capitulos == null) {
            return new ArrayList<>();
        }
        return capitulos.stream()
                .flatMap(c -> c.getEmbeddings().stream())
                .toList();
    }

    /**
     * Get total number of embeddings
     */
    public int getTotalEmbeddingsCount() {
        return getAllEmbeddings().size();
    }

    /**
     * Get document-level embeddings only
     */
    public List<DocumentEmbeddingDTO> getDocumentEmbeddings() {
        return getAllEmbeddings().stream()
                .filter(DocumentEmbeddingDTO::isDocumentLevel)
                .toList();
    }

    /**
     * Get chapter-level embeddings only
     */
    public List<DocumentEmbeddingDTO> getChapterEmbeddings() {
        return getAllEmbeddings().stream()
                .filter(DocumentEmbeddingDTO::isChapterLevel)
                .toList();
    }

    /**
     * Get chunk-level embeddings only
     */
    public List<DocumentEmbeddingDTO> getChunkEmbeddings() {
        return getAllEmbeddings().stream()
                .filter(DocumentEmbeddingDTO::isChunkLevel)
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

    /**
     * Get biblioteca language (convenience method)
     */
    public String getBibliotecaLanguage() {
        return biblioteca != null ? biblioteca.getLanguage() : null;
    }

    /**
     * Get biblioteca embedding model (convenience method)
     */
    public String getBibliotecaEmbeddingModel() {
        return biblioteca != null ? biblioteca.getEmbeddingModel() : null;
    }

    /**
     * Get biblioteca embedding dimension (convenience method)
     */
    public Integer getBibliotecaEmbeddingDimension() {
        return biblioteca != null ? biblioteca.getEmbeddingDimension() : null;
    }

    /**
     * Check if has biblioteca reference
     */
    public boolean hasBiblioteca() {
        return biblioteca != null;
    }

    /**
     * Get biblioteca semantic weight
     */
    public Float getBibliotecaSemanticWeight() {
        return biblioteca != null ? biblioteca.getPesoSemantico() : null;
    }

    /**
     * Get biblioteca textual weight
     */
    public Float getBibliotecaTextualWeight() {
        return biblioteca != null ? biblioteca.getPesoTextual() : null;
    }
    
    public void setUrl(String url) {
	setMetadataValue("url", url);
    }

    public String getUrl() {
	return (String) getMetadataValue("url");
    }
    

    @JsonIgnore
    public void setTexto(String textoOriginal) {
	setConteudoMarkdown(textoOriginal);
    }
    

    @JsonIgnore
    public String getTexto() {
	return getConteudoMarkdown();
    }
    
    public void addParte(ChapterDTO parte) {
	this.addCapitulo(parte);	
    }

    
    @JsonIgnore
    public void setDataPublicacao(Date dataStr) {
	this.setDataPublicacao(dataStr.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate());	
    }
    
    public void setDataPublicacao(LocalDate data) {
	this.dataPublicacao = data;
    }

    public void addAnexo(DocumentoDTO doc) {
	if (this.anexos == null) 
	    this.anexos = new ArrayList<>();
	this.anexos.add(doc);		
    }
    
}