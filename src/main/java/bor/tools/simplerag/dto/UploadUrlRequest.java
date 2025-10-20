package bor.tools.simplerag.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for uploading documents from URL.
 * Downloads content from URL and converts to markdown.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Request to upload document by downloading from URL")
public class UploadUrlRequest {

    @NotBlank(message = "URL is required")
    @Pattern(
        regexp = "^https?://.*",
        message = "URL must start with http:// or https://"
    )
    @Schema(
        description = "URL of the document to download. Supports HTML, PDF, DOCX, TXT formats.",
        example = "https://example.com/documents/technical-specification.pdf",
        required = true
    )
    private String url;

    @NotNull(message = "Library ID is required")
    @Schema(
        description = "UUID of the library to which this document belongs",
        example = "550e8400-e29b-41d4-a716-446655440000",
        required = true
    )
    private Integer libraryId;

    @Size(max = 500, message = "Title must not exceed 500 characters")
    @Schema(
        description = """
            Optional document title. If not provided, will be extracted from:
            1. HTML <title> tag (for web pages)
            2. PDF metadata (for PDF files)
            3. Filename from URL (fallback)
            """,
        example = "Technical Specification Document"
    )
    private String titulo;

    @Schema(
        description = """
            Optional metadata as key-value pairs. Common fields:
            - autor: Document author
            - fonte_url: Original source URL (auto-populated if not provided)
            - data_download: Download timestamp (auto-populated)
            - tipo_conteudo: Content type (1=livro, 2=normativo, 3=artigo, 4=manual, 5=outros)
            """,
        example = """
            {
              "autor": "Technical Team",
              "tipo_conteudo": "4",
              "versao": "2.1"
            }
            """
    )
    private Map<String, Object> metadados;
}
