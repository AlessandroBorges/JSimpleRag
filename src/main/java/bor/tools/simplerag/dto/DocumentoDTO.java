package bor.tools.simplerag.dto;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.zip.Adler32;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * Simple DTO for Documento entity WITHOUT associations.
 *
 * Contains only document fields that mirror the entity structure.
 * Use this for simple CRUD operations, REST API responses, and basic entity-DTO conversions.
 *
 * For processing workflows that require associations (Library, Chapters),
 * use DocumentoWithAssociationDTO instead.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "id", "bibliotecaId", "titulo", "flagVigente", "dataPublicacao",
                     "tokensTotal", "metadados", "checksum", "createdAt", "updatedAt", "deletedAt",
                     "contentLength",
                     "url",
                     "vigente",
                     "conteudoMarkdown" })	
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

    private LocalDateTime deletedAt;

    /**
     * Checksum of content for quick comparison
     */
    private String checksum;

    /**
     * Create DTO from entity
     * @param src - source entity
     * @return DTO instance
     */
    public static DocumentoDTO from(bor.tools.simplerag.entity.Documento src) {
        if (src == null) {
            return null;
        }

        DocumentoDTO dto = DocumentoDTO.builder()
                .id(src.getId())
                .bibliotecaId(src.getBibliotecaId())
                .titulo(src.getTitulo())
                .flagVigente(src.getFlagVigente())
                .dataPublicacao(src.getDataPublicacao())
                .tokensTotal(src.getTokensTotal())
                .metadados(src.getMetadados() != null ? new Metadata(src.getMetadados()) : null)
                .createdAt(src.getCreatedAt())
                .updatedAt(src.getUpdatedAt())
                .deletedAt(src.getDeletedAt())
                .build();

        // Set content (this also calculates checksum via setter)
        dto.setConteudoMarkdown(src.getConteudoMarkdown());

        return dto;
    }

    /**
     * Convert DTO to entity
     * @return Documento entity
     */
    public bor.tools.simplerag.entity.Documento toEntity() {
        MetaDoc metadados = this.metadados != null ? new MetaDoc(this.metadados) : new MetaDoc();
        metadados.setChecksum(getChecksum());

        bor.tools.simplerag.entity.Documento entity = new bor.tools.simplerag.entity.Documento();
        entity.setId(this.id);
        entity.setBibliotecaId(this.bibliotecaId);
        entity.setTitulo(this.titulo);
        entity.setConteudoMarkdown(this.conteudoMarkdown);
        entity.setFlagVigente(this.flagVigente);
        entity.setDataPublicacao(this.dataPublicacao);
        entity.setTokensTotal(this.tokensTotal != null ? this.tokensTotal : countTokens(this.conteudoMarkdown));
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
     * Set URL in metadata
     */
    public void setUrl(String url) {
        setMetadataValue("url", url);
    }

    /**
     * Get URL from metadata
     */
    public String getUrl() {
        return (String) getMetadataValue("url");
    }

    /**
     * Get checksum (calculates if not set)
     */
    public String getChecksum() {
        if (this.checksum == null) {
            this.checksum = calculateAdler32Checksum();
        }
        return this.checksum;
    }

    /**
     * Set content markdown and update checksum and token count
     */
    public void setConteudoMarkdown(String conteudoMarkdown) {
        this.conteudoMarkdown = conteudoMarkdown;

        if (conteudoMarkdown == null || conteudoMarkdown.isEmpty()) {
            this.checksum = null;
            this.tokensTotal = 0;
        } else {
            this.checksum = calculateAdler32Checksum();
            this.tokensTotal = countTokens(conteudoMarkdown);
        }
    }

    /**
     * Count tokens in text using RAGUtil.countTokens().
     *
     * Uses OpenAI's cl100k_base tokenizer (used in embedding models and gpt-3.5 to gpt-4),
     * which estimates 3.8 characters per token on average.
     *
     * @param text Text to count tokens
     * @return Token count
     */
    public static int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        try {
            return RAGUtil.countTokens(text);
        } catch (LLMException e) {
            e.printStackTrace();
            // Fallback estimate: 3.8 chars per token
            return Math.round((text.length() * 1.0f) / 3.8f);
        }
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
     * Calculate Adler32 checksum of content for quick comparison
     */
    private String calculateAdler32Checksum() {
        if (conteudoMarkdown == null) {
            return null;
        }

        String textoNormalizado = normalizeText(conteudoMarkdown);
        Adler32 adler = new Adler32();
        adler.update(textoNormalizado.getBytes(StandardCharsets.UTF_8));
        return Long.toHexString(adler.getValue());
    }
}
