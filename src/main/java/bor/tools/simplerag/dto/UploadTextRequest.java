package bor.tools.simplerag.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for uploading documents from text content.
 * Supports markdown and plain text formats.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Request to upload document from text content (markdown or plain text)")
public class UploadTextRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 500, message = "Title must be between 3 and 500 characters")
    @Schema(
        description = "Document title",
        example = "Clean Code: A Handbook of Agile Software Craftsmanship",
        required = true
    )
    private String titulo;

    @NotBlank(message = "Content is required")
    @Size(min = 100, message = "Content must have at least 100 characters")
    @Schema(
        description = "Document content in markdown or plain text format. Minimum 100 characters.",
        example = """
            # Clean Code

            ## Introduction

            This book is about good programming. It's filled with code. We're going to be looking at code from every different direction...

            ### What is Clean Code?

            There are probably as many definitions as there are programmers...
            """,
        required = true
    )
    private String conteudo;

    @NotNull(message = "Library ID is required")
    @Schema(
        description = "UUID of the library to which this document belongs",
        example = "550e8400-e29b-41d4-a716-446655440000",
        required = true
    )
    private Integer libraryId;

    @Schema(
        description = """
            Optional metadata as key-value pairs. Common fields:
            - autor: Document author
            - isbn: ISBN number (for books)
            - palavras_chave: Comma-separated keywords
            - tipo_conteudo: Content type (1=livro, 2=normativo, 3=artigo, 4=manual, 5=outros)
            - data_publicacao: Publication date (YYYY-MM-DD)
            """,
        example = """
            {
              "autor": "Robert C. Martin",
              "isbn": "978-0132350884",
              "palavras_chave": "clean code,software craftsmanship,agile",
              "tipo_conteudo": "1"
            }
            """
    )
    private Map<String, Object> metadados;
}
