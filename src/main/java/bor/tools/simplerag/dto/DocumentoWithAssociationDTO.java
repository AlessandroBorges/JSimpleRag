package bor.tools.simplerag.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.zip.Adler32;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import bor.tools.simplellm.exceptions.LLMException;
import bor.tools.simplerag.entity.MetaDoc;
import bor.tools.simplerag.entity.Metadata;
import bor.tools.utils.RAGUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Documento entity WITH associations (Library, Chapters, Attachments).
 *
 * Contains document data with references to parent Library and child Capitulos.
 * This provides the complete hierarchical context needed by Splitters and processing workflows.
 *
 * For simple CRUD operations without associations, use DocumentoDTO instead.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "id", "bibliotecaId", "titulo", "flagVigente", "dataPublicacao", "tokensTotal", "metadados", "createdAt", "updatedAt", "deletedAt", "biblioteca", "anexos", "capitulos", "conteudoMarkdown" })
public class DocumentoWithAssociationDTO {

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

    private LocalDateTime deletedAt;
    
    /**
     * checksum of content for quick comparison
     */
    private String checksum;

    /**
     * Parent biblioteca reference for full context
     */
    private LibraryDTO biblioteca;
    
    /**
     * Attached documents (e.g. annexes)
     */
    @Builder.Default
    private List<DocumentoWithAssociationDTO> anexos = new ArrayList<>();

    /**
     * Child chapters with their embeddings
     */
    @Builder.Default
    private List<ChapterDTO> capitulos = new ArrayList<>();

    /**
     * Create DTO from entity
     * @param src - source entity
     * @return DTO instance
     */
    public static DocumentoWithAssociationDTO from(bor.tools.simplerag.entity.Documento src) {
        if (src == null) {
            return null;
        }
        var dto = DocumentoWithAssociationDTO.builder()
                .id(src.getId())
                .bibliotecaId(src.getBibliotecaId())
                .titulo(src.getTitulo())                
                //.conteudoMarkdown(src.getConteudoMarkdown())
                .flagVigente(src.getFlagVigente())
                .dataPublicacao(src.getDataPublicacao())
                //.checksum(null) // we don't set checksum here to avoid recalculation
                //.tokensTotal(src.getTokensTotal()) 
                .metadados(new Metadata(src.getMetadados()))
                .createdAt(src.getCreatedAt())
                .updatedAt(src.getUpdatedAt())
                .deletedAt(src.getDeletedAt())
                .build();
        
        // Set content and also tokensTotal and checksum
        dto.setConteudoMarkdown(src.getConteudoMarkdown());
        
        return dto;
    }

    /**
     * Convert DTO to entity
     * @return Documento entity
     */
    public bor.tools.simplerag.entity.Documento toEntity() {
	
	var metadados = new MetaDoc(this.metadados);
	metadados.setChecksum(getChecksum());
		
        bor.tools.simplerag.entity.Documento entity = new bor.tools.simplerag.entity.Documento();
        entity.setId(this.id);
        entity.setBibliotecaId(this.bibliotecaId);
        entity.setTitulo(this.titulo);
        entity.setConteudoMarkdown(this.conteudoMarkdown);
        entity.setFlagVigente(this.flagVigente);
        entity.setDataPublicacao(this.dataPublicacao);
        entity.setTokensTotal(getTokensTotal());        
        entity.setMetadados(metadados);
        entity.setCreatedAt(this.createdAt);
        entity.setUpdatedAt(this.updatedAt);
        entity.setDeletedAt(this.deletedAt);
        return entity;
    }

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
    
    /**
     * Set checksum
     * @param checksum the checksum to set
     */
    public void setChecksum(String checksum) {
	this.checksum = checksum;
    }
    
    
    public String getChecksum() {
	if (this.checksum == null) {
	    this.checksum = getAdler32Checksum();
	}
	return this.checksum;
    }

    /**
     * Set content markdown and update checksum
     */
    public void setConteudoMarkdown(String conteudoMarkdown) {
	this.conteudoMarkdown = conteudoMarkdown;
	if (conteudoMarkdown == null || conteudoMarkdown.isEmpty()) {
	    this.checksum = null;
	    this.tokensTotal = 0;
	}
	else {
	    this.checksum = getAdler32Checksum();
	    this.tokensTotal = countTokens(conteudoMarkdown);	    
	}
    }
    
    /**
     * Count tokens in text using RAGUtil.countTokens(). <br>
     * 
     * It uses the traditional OpenAI's cl100k_base, 
     * used on OpenAI's embedding models and gpt-3.5 to gpt-4, 
     * which estimates 3.8 characters per token on average.
     * 
     * @param text
     * @return
     */
     public static int countTokens(String text) {
	if (text == null || text.isEmpty()) {
	    return 0;
	}
	// Simple tokenization by splitting on whitespace
	int tokens;
	try {
	    tokens = RAGUtil.countTokens(text);
	} catch (LLMException e) {	    
	    e.printStackTrace();
	    tokens = Math.round((text.length() * 1.0f) / 3.8f); // fallback estimate
	}
	return tokens;	
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

    public void addAnexo(DocumentoWithAssociationDTO doc) {
	if (this.anexos == null)
	    this.anexos = new ArrayList<>();
	this.anexos.add(doc);
    }
    
   
    /**
     * Normalize text: lowercase, replace multiple whitespaces with single space, trim
     */
    private String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        // Replace all whitespace (including \n, \t, spaces) with single space
        return text.toLowerCase().replaceAll("\\s+", " ").trim();
    }

    
    
        
    /**
     * Get Adler32 checksum of content for quick comparison
     */
    public String getAdler32Checksum() {
        if (conteudoMarkdown == null) {
            return null;
        }
        String textoNormalizado = normalizeText(conteudoMarkdown);
        Adler32 adler = new Adler32();
        adler.update(textoNormalizado.getBytes(StandardCharsets.UTF_8));
        String hexa = Long.toHexString(adler.getValue());
        return hexa;
    }
}