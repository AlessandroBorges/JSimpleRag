package bor.tools.simplerag.service.embedding.strategy;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import bor.tools.simplellm.CompletionResponse;
import bor.tools.simplellm.LLMService;
import bor.tools.simplellm.MapParam;
import bor.tools.simplellm.ModelEmbedding.Embeddings_Op;
import bor.tools.simplellm.exceptions.LLMException;
import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocumentEmbeddingDTO;
import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.entity.enums.TipoEmbedding;
import bor.tools.simplerag.service.embedding.model.EmbeddingRequest;
import bor.tools.simplerag.service.llm.LLMServiceManager;
import lombok.RequiredArgsConstructor;

/**
 * Strategy for generating summary-based embeddings from chapter content.
 *
 * This strategy:
 * 1. Uses a completion model to generate a condensed summary of the text
 * 2. Generates an embedding for the summary
 *
 * Improves retrieval of main concepts from large chapters by creating
 * compact, information-dense representations.
 *
 * Integrates with LLMServiceManager for multi-provider support:
 * - Completion model for summarization
 * - Embedding model for vector generation
 *
 * @since 0.0.1
 */
@Component
@RequiredArgsConstructor
public class SummaryEmbeddingStrategy implements EmbeddingGenerationStrategy {

    private static final Logger log = LoggerFactory.getLogger(SummaryEmbeddingStrategy.class);

    private final LLMServiceManager llmServiceManager;

    @Value("${rag.embedding.default-model:nomic-embed-text}")
    private String defaultEmbeddingModel;

    // Summarization defaults
    private static final int DEFAULT_SUMMARY_LENGTH = 500; // tokens
    private static final int MAX_TEXT_LENGTH = 8000; // characters
    private static final String DEFAULT_INSTRUCTIONS =
        "Resuma o texto de forma concisa, mantendo as informações mais importantes e preservando o contexto principal";

    @Override
    public List<DocumentEmbeddingDTO> generate(EmbeddingRequest request) {
        log.debug("Generating summary embedding for chapter");

        if (request.getChapter() == null) {
            throw new IllegalArgumentException("Chapter is required for SummaryEmbeddingStrategy");
        }

        if (request.getContext() == null) {
            throw new IllegalArgumentException("EmbeddingContext is required");
        }

        ChapterDTO chapter = request.getChapter();
        String content = chapter.getConteudo();

        if (content == null || content.trim().isEmpty()) {
            log.warn("Empty content for chapter: {}", chapter.getTitulo());
            return new ArrayList<>();
        }

        try {
            LibraryDTO library = request.getContext().getLibrary();
            int maxLength = request.getMaxSummaryLength() != null
                ? request.getMaxSummaryLength()
                : DEFAULT_SUMMARY_LENGTH;
            String instructions = request.getCustomSummaryInstructions() != null
                ? request.getCustomSummaryInstructions()
                : DEFAULT_INSTRUCTIONS;

            // Step 1: Generate summary using completion model
            String summary = generateSummary(content, instructions, maxLength, request);

            if (summary == null || summary.trim().isEmpty()) {
                log.warn("No summary generated for chapter: {}", chapter.getTitulo());
                return new ArrayList<>();
            }

            // Step 2: Create embedding for the summary
            DocumentEmbeddingDTO embedding = createSummaryEmbedding(
                summary,
                chapter,
                library,
                request
            );

            log.debug("Generated summary embedding for chapter: {}", chapter.getTitulo());
            return List.of(embedding);

        } catch (Exception e) {
            log.error("Failed to generate summary embedding: {}", e.getMessage(), e);
            throw new RuntimeException("Summary embedding generation failed", e);
        }
    }

    @Override
    public boolean supports(EmbeddingRequest request) {
        return request.getChapter() != null
                && request.getOperation() == Embeddings_Op.DOCUMENT
                && request.getMaxSummaryLength() != null;  // Summary requested
    }

    @Override
    public String getStrategyName() {
        return "SummaryEmbeddingStrategy";
    }

    // ========== Private Helper Methods ==========

    /**
     * Generates a summary from chapter content using completion model.
     */
    private String generateSummary(String content, String instructions, int maxLength,
                                    EmbeddingRequest request) throws LLMException {

        // Resolve completion model to use
        String defaultCompletionModel = llmServiceManager.getDefaultCompletionModelName();
        String modelName = request.getCompletionModelName(defaultCompletionModel);
        log.debug("Using completion model for summarization: {}", modelName);

        // Get appropriate LLMService from pool
        LLMService llmService = llmServiceManager.getLLMServiceByRegisteredModel(modelName);

        if (llmService == null) {
            throw new IllegalStateException(
                "No LLM service found for completion model: " + modelName +
                ". Check if the model is registered in any provider."
            );
        }

        log.debug("Using LLMService from provider: {}", llmService.getServiceProvider());

        // Truncate content if too long
        String contentToSummarize = content;
        if (content.length() > MAX_TEXT_LENGTH) {
            contentToSummarize = content.substring(0, MAX_TEXT_LENGTH) + "...";
            log.debug("Truncated content to {} characters for summarization", MAX_TEXT_LENGTH);
        }

        // Prepare parameters
        MapParam params = new MapParam();
        params.model(modelName);
        params.put("max_tokens", Math.min(maxLength, 2000)); // Safety limit
        params.put("temperature", 0.3); // More deterministic for summaries

        // Generate summary
        CompletionResponse response = llmService.completion(instructions, contentToSummarize, params);
        String summary = response.getText().trim();

        log.debug("Successfully generated summary of {} characters", summary.length());
        return summary;
    }

    /**
     * Creates an embedding for the summary text.
     */
    private DocumentEmbeddingDTO createSummaryEmbedding(
            String summary,
            ChapterDTO chapter,
            LibraryDTO library,
            EmbeddingRequest request) throws LLMException {

        String title = chapter.getTitulo() + " (Resumo)";

        // Resolve embedding model to use
        String modelName = request.getEmbeddingModelName(defaultEmbeddingModel);
        log.debug("Using embedding model: {}", modelName);

        // Get appropriate LLMService from pool
        LLMService llmService = llmServiceManager.getLLMServiceByRegisteredModel(modelName);

        if (llmService == null) {
            throw new IllegalStateException(
                "No LLM service found for embedding model: " + modelName +
                ". Check if the model is registered in any provider."
            );
        }

        log.debug("Using LLMService from provider: {}", llmService.getServiceProvider());

        // Prepare parameters
        MapParam params = new MapParam();
        params.model(modelName);

        // Add library context
        if (library != null) {
            params.put("library_context", library.getNome());
        }

        // Generate embedding
        float[] embedding = llmService.embeddings(Embeddings_Op.DOCUMENT, summary, params);

        // Create DTO
        DocumentEmbeddingDTO docEmbedding = new DocumentEmbeddingDTO();
        docEmbedding.setTrechoTexto(summary);
        docEmbedding.setEmbeddingVector(embedding);
        docEmbedding.setTipoEmbedding(TipoEmbedding.RESUMO);
        docEmbedding.setBibliotecaId(library != null ? library.getId() : null);
        docEmbedding.setDocumentoId(chapter.getDocumentoId());
        docEmbedding.setCapituloId(chapter.getId());

        // Configure metadata
        docEmbedding.getMetadados().setNomeDocumento(title);

        // Summary specific metadata
        docEmbedding.getMetadados().put("tipo_embedding", "summary");
        docEmbedding.getMetadados().put("summary_length", String.valueOf(summary.length()));
        docEmbedding.getMetadados().put("original_length", String.valueOf(chapter.getConteudo().length()));

        // Library metadata
        if (library != null) {
            docEmbedding.getMetadados().put("biblioteca_id", library.getId());
            docEmbedding.getMetadados().put("biblioteca_nome", library.getNome());
        }

        // Chapter metadata
        if (chapter.getId() != null) {
            docEmbedding.getMetadados().put("capitulo_id", chapter.getId().toString());
        }
        if (chapter.getDocumentoId() != null) {
            docEmbedding.getMetadados().put("documento_id", chapter.getDocumentoId().toString());
        }
        docEmbedding.getMetadados().put("capitulo_titulo", chapter.getTitulo());
        if (chapter.getOrdemDoc() != null) {
            docEmbedding.getMetadados().put("capitulo_ordem", chapter.getOrdemDoc().toString());
        }
        if (chapter.getTokensTotal() != null) {
            docEmbedding.getMetadados().put("capitulo_tokens_total", chapter.getTokensTotal().toString());
        }

        // Additional chapter metadata
        if (chapter.getMetadados() != null) {
            chapter.getMetadados().forEach((key, value) -> {
                if (value != null && !value.toString().trim().isEmpty()) {
                    docEmbedding.getMetadados().put("capitulo_" + key, value.toString());
                }
            });
        }

        // Technical metadata
        docEmbedding.getMetadados().put("embedding_operation", Embeddings_Op.DOCUMENT.toString());
        docEmbedding.getMetadados().put("embedding_model", modelName);
        docEmbedding.getMetadados().put("created_at", java.time.Instant.now().toString());

        return docEmbedding;
    }
}
