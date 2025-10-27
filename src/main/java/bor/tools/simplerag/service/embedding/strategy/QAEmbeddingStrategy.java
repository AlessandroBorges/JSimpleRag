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
import bor.tools.splitter.QuestionAnswer;
import lombok.RequiredArgsConstructor;

/**
 * Strategy for generating Q&A embeddings from chapter content.
 *
 * This strategy:
 * 1. Uses a completion model to generate synthetic question-answer pairs from text
 * 2. Combines each Q&A pair into a single text
 * 3. Generates embeddings for each combined Q&A text
 *
 * Improves retrieval for conversational queries by creating question-based
 * representations of the content.
 *
 * Integrates with LLMServiceManager for multi-provider support:
 * - Completion model for Q&A generation
 * - Embedding model for vector generation
 *
 * @since 0.0.1
 */
@Component
@RequiredArgsConstructor
public class QAEmbeddingStrategy implements EmbeddingGenerationStrategy {

    private static final Logger log = LoggerFactory.getLogger(QAEmbeddingStrategy.class);

    private final LLMServiceManager llmServiceManager;

    @Value("${rag.embedding.default-model:snowflake}")
    private String defaultEmbeddingModel;

    @Value("${rag.completion-qa.default-model:phi4-mini}")
    private String defaultCompletionQAModel;
    
    // Q&A generation defaults
    private static final int DEFAULT_QA_PAIRS = 3;
    private static final int MAX_TEXT_LENGTH = 6000;
    private static final int TOKENS_PER_QA = 150;

    @Override
    public List<DocumentEmbeddingDTO> generate(EmbeddingRequest request) {
        log.debug("Generating Q&A embeddings for chapter");

        if (request.getChapter() == null) {
            throw new IllegalArgumentException("Chapter is required for QAEmbeddingStrategy");
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
            int numberOfPairs = request.getNumberOfQAPairs() != null
                ? request.getNumberOfQAPairs()
                : DEFAULT_QA_PAIRS;

            // Step 1: Generate Q&A pairs using completion model
            List<QuestionAnswer> qaPairs = generateQAPairs(content, numberOfPairs, request);

            if (qaPairs.isEmpty()) {
                log.warn("No Q&A pairs generated for chapter: {}", chapter.getTitulo());
                return new ArrayList<>();
            }

            // Step 2: Create embeddings for each Q&A pair
            List<DocumentEmbeddingDTO> embeddings = new ArrayList<>();
            for (int i = 0; i < qaPairs.size(); i++) {
                QuestionAnswer qa = qaPairs.get(i);

                DocumentEmbeddingDTO embedding = createQAEmbedding(
                    qa,
                    i,
                    qaPairs.size(),
                    chapter,
                    library,
                    request
                );

                embeddings.add(embedding);
            }

            log.debug("Generated {} Q&A embeddings for chapter: {}", embeddings.size(), chapter.getTitulo());
            return embeddings;

        } catch (Exception e) {
            log.error("Failed to generate Q&A embeddings: {}", e.getMessage(), e);
            throw new RuntimeException("Q&A embedding generation failed", e);
        }
    }

    @Override
    public boolean supports(EmbeddingRequest request) {
        return request.getChapter() != null
                && request.getOperation() == Embeddings_Op.DOCUMENT
                && request.getNumberOfQAPairs() != null;  // Q&A requested
    }

    @Override
    public String getStrategyName() {
        return "QAEmbeddingStrategy";
    }

    // ========== Private Helper Methods ==========

    /**
     * Generates Q&A pairs from chapter content using completion model.
     */
    private List<QuestionAnswer> generateQAPairs( String content, 
	    					  int numberOfPairs,
                                                  EmbeddingRequest request) throws LLMException {

        // Resolve completion model to use
        String defaultCompletionModel = request.getCompletionModelName(this.defaultCompletionQAModel);
        
        if (defaultCompletionModel == null || defaultCompletionModel.trim().isEmpty()) {
            throw new IllegalStateException("No default completion model configured for Q&A generation");
        }
        
        String modelName = defaultCompletionModel;
        log.debug("Using completion model for Q&A generation: {}", modelName);

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
        String contentForQA = content;
        if (content.length() > MAX_TEXT_LENGTH) {
            contentForQA = content.substring(0, MAX_TEXT_LENGTH) + "...";
            log.debug("Truncated content to {} characters for Q&A generation", MAX_TEXT_LENGTH);
        }

        // Prepare prompt for Q&A generation
        String prompt = String.format(
            "Com base no texto fornecido, gere exatamente %d pares de pergunta e resposta relevantes e informativos. " +
            "As perguntas devem abordar os pontos principais do texto.\n" +
            "Formato: Q: [pergunta]\\nA: [resposta]\\n\\n" +
            "Certifique-se de que as respostas sejam concisas mas informativas.",
            numberOfPairs
        );

        // Prepare parameters
        MapParam params = new MapParam();
        params.model(modelName);
        params.put("max_tokens", numberOfPairs * TOKENS_PER_QA);
        params.put("temperature", 0.4); // Slightly creative but controlled

        // Generate Q&A pairs
        CompletionResponse response = llmService.completion(prompt, contentForQA, params);
        String result = response.getText();

        // Parse response into QuestionAnswer objects
        List<QuestionAnswer> qaPairs = parseQAResponse(result);

        // Validate result
        if (qaPairs.size() < numberOfPairs) {
            log.warn("Generated {} Q&A pairs instead of requested {}", qaPairs.size(), numberOfPairs);
        }

        log.debug("Successfully generated {} Q&A pairs", qaPairs.size());
        return qaPairs;
    }

    /**
     * Checks if the specified model exists in any registered LLM service.
     * @param modelName the name of the model to check
     */
    protected boolean checkModelExists(String modelName) {
	if (modelName == null || modelName.trim().isEmpty()) {
	    return false;
	}
	LLMService llmService = llmServiceManager.getLLMServiceByRegisteredModel(modelName);
	return llmService != null;
    }
    
    /**
     * Creates an embedding for a single Q&A pair.
     */
    private DocumentEmbeddingDTO createQAEmbedding(
            QuestionAnswer qa,
            int index,
            int totalPairs,
            ChapterDTO chapter,
            LibraryDTO library,
            EmbeddingRequest request) throws LLMException {

        // Combine question and answer into single text
        String combinedText = "Pergunta: " + qa.getQuestion() + "\n\nResposta: " + qa.getAnswer();
        String title = chapter.getTitulo() + " - Q&A " + (index + 1);

        // Resolve embedding model to use
        String modelName = request.getEmbeddingModelName(defaultEmbeddingModel);
        log.debug("Using embedding model: {}", modelName);
        
        if (!checkModelExists(modelName)) {
	    throw new IllegalStateException(
		"Embedding model not found: " + modelName +
		". Check if the model is registered in any provider."
	    );
	}

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
        float[] embedding = llmService.embeddings(Embeddings_Op.DOCUMENT, combinedText, params);

        // Create DTO
        DocumentEmbeddingDTO docEmbedding = new DocumentEmbeddingDTO();
        docEmbedding.setTrechoTexto(combinedText);
        docEmbedding.setEmbeddingVector(embedding);
        docEmbedding.setTipoEmbedding(TipoEmbedding.PERGUNTAS_RESPOSTAS);
        docEmbedding.setBibliotecaId(library != null ? library.getId() : null);
        docEmbedding.setDocumentoId(chapter.getDocumentoId());
        docEmbedding.setCapituloId(chapter.getId());

        // Configure metadata
        docEmbedding.getMetadados().setNomeDocumento(title);

        // Q&A specific metadata
        docEmbedding.getMetadados().put("tipo_embedding", "qa_pair");
        docEmbedding.getMetadados().put("pergunta", qa.getQuestion());
        docEmbedding.getMetadados().put("resposta", qa.getAnswer());
        docEmbedding.getMetadados().put("qa_pair_id", String.valueOf(index));
        docEmbedding.getMetadados().put("total_qa_pairs", String.valueOf(totalPairs));

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

    /**
     * Parses LLM response into QuestionAnswer objects.
     *
     * Expected format:
     * Q: [question]
     * A: [answer]
     *
     * Q: [question]
     * A: [answer]
     */
    private List<QuestionAnswer> parseQAResponse(String response) {
        List<QuestionAnswer> qaPairs = new ArrayList<>();
        String[] blocks = response.split("\\n\\s*\\n"); // Split by double newlines

        for (String block : blocks) {
            String[] lines = block.trim().split("\\n");
            String question = null;
            StringBuilder answer = new StringBuilder();

            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("Q:")) {
                    question = line.substring(2).trim();
                } else if (line.startsWith("A:")) {
                    answer.append(line.substring(2).trim());
                } else if (!line.isEmpty() && question != null) {
                    // Continue answer on multiple lines
                    if (answer.length() > 0) {
                        answer.append(" ");
                    }
                    answer.append(line);
                }
            }

            if (question != null && answer.length() > 0) {
                qaPairs.add(new QuestionAnswer(question, answer.toString().trim()));
            }
        }

        return qaPairs;
    }
}
