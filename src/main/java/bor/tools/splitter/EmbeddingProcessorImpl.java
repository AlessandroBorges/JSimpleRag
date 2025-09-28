package bor.tools.splitter;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import bor.tools.simplellm.LLMService;
import bor.tools.simplellm.MapParam;
import bor.tools.simplellm.ModelEmbedding.Embeddings_Op;
import bor.tools.simplellm.exceptions.LLMException;
import bor.tools.simplerag.dto.BibliotecaDTO;
import bor.tools.simplerag.dto.CapituloDTO;
import bor.tools.simplerag.dto.DocEmbeddingDTO;
import bor.tools.simplerag.entity.enums.TipoEmbedding;

/**
 * Implementação completa da interface EmbeddingProcessorInterface.
 *
 * Esta classe fornece funcionalidades completas para criação e processamento
 * de embeddings no contexto do JSimpleRag, integrando com o LLMService
 * e seguindo a arquitetura hierárquica do sistema.
 */
@Service
public class EmbeddingProcessorImpl implements EmbeddingProcessorInterface {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingProcessorImpl.class);

    private final LLMService llmService;
    private final DocumentSummarizerImpl documentSummarizer;

    /**
     * Tamanho máximo padrão para chunks de embedding (em tokens)
     */
    private static final int DEFAULT_CHUNK_SIZE = 2000;

    /**
     * Tamanho mínimo para chunks (em tokens)
     */
    private static final int MIN_CHUNK_SIZE = 100;

    /**
     * Construtor com injeção de dependências
     */
    public EmbeddingProcessorImpl(LLMService llmService, DocumentSummarizerImpl documentSummarizer) {
        this.llmService = llmService;
        this.documentSummarizer = documentSummarizer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DocEmbeddingDTO> createChapterEmbeddings(@NonNull CapituloDTO capitulo,
                                                        @NonNull BibliotecaDTO biblioteca,
                                                        int flagGeneration) {
        logger.debug("Creating simple embeddings for chapter: {} with flag: {}", capitulo.getTitulo(), flagGeneration);

        List<DocEmbeddingDTO> embeddings = new ArrayList<>();
        String content = capitulo.getConteudo();

        if (content == null || content.trim().isEmpty()) {
            logger.warn("Empty content for chapter: {}", capitulo.getTitulo());
            return embeddings;
        }

        try {
            switch (flagGeneration) {
                case FLAG_FULL_TEXT_METADATA -> {
                    // Criar embedding com texto completo + metadados
                    embeddings.add(createFullTextEmbedding(capitulo, biblioteca));
                }
                case FLAG_ONLY_METADATA -> {
                    // Criar embedding apenas com metadados
                    embeddings.add(createMetadataOnlyEmbedding(capitulo, biblioteca));
                }
                case FLAG_ONLY_TEXT -> {
                    // Criar embedding apenas com texto
                    embeddings.add(createTextOnlyEmbedding(capitulo, biblioteca));
                }
                case FLAG_SPLIT_TEXT_METADATA -> {
                    // Dividir texto em chunks e criar embeddings
                    embeddings.addAll(createSplitTextEmbeddings(capitulo, biblioteca));
                }
                case FLAG_AUTO -> {
                    // Escolher automaticamente baseado no tamanho do conteúdo
                    embeddings.addAll(createAutoEmbeddings(capitulo, biblioteca));
                }
                default -> {
                    logger.warn("Unknown flag generation: {}, using AUTO mode", flagGeneration);
                    embeddings.addAll(createAutoEmbeddings(capitulo, biblioteca));
                }
            }

            logger.debug("Created {} embeddings for chapter: {}", embeddings.size(), capitulo.getTitulo());

        } catch (Exception e) {
            logger.error("Failed to create embeddings for chapter {}: {}", capitulo.getTitulo(), e.getMessage());
            // Retornar lista vazia em caso de erro
        }

        return embeddings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DocEmbeddingDTO> createChunkEmbeddings(@NonNull DocEmbeddingDTO document,
                                                        @NonNull BibliotecaDTO biblioteca,
                                                        int flagGeneration) {
        logger.debug("Creating simple embeddings for document embedding with flag: {}", flagGeneration);

        // Criar um CapituloDTO temporário para reutilizar a lógica existente
        CapituloDTO tempCapitulo = new CapituloDTO();
        tempCapitulo.setTitulo(document.getMetadados().getNomeDocumento());
        tempCapitulo.setConteudo(document.getTrechoTexto());
        tempCapitulo.addMetadata(document.getMetadados());

        return createChapterEmbeddings(tempCapitulo, biblioteca, flagGeneration);
    }

    /**
     * {@inheritDoc}
     * Gera um embedding por par Q&A, combinando pergunta e resposta em um único embedding
     */
    @Override
    public List<DocEmbeddingDTO> createQAEmbeddings(@NonNull CapituloDTO capitulo,
                                                    @NonNull BibliotecaDTO biblioteca,
                                                    Integer k) {
        logger.debug("Creating Q&A embeddings for chapter: {}", capitulo.getTitulo());

        List<DocEmbeddingDTO> qaEmbeddings = new ArrayList<>();
        k = k==null ? 3 : k;
        try {
            // Gerar pares Q&A usando o DocumentSummarizer
            List<QuestionAnswer> qaList = documentSummarizer.generateQA(capitulo.getConteudo(), k);

            for (int i = 0; i < qaList.size(); i++) {
                QuestionAnswer qa = qaList.get(i);

                // Combinar pergunta e resposta em um único texto
                String combinedText = "Pergunta: " + qa.getQuestion() + "\n\nResposta: " + qa.getAnswer();

                // Criar embedding único para o par Q&A
                DocEmbeddingDTO qaEmbedding = createEmbeddingFromText(
                    combinedText,
                    capitulo.getTitulo() + " - Q&A " + (i + 1),
                    biblioteca,
                    TipoEmbedding.PERGUNTAS_RESPOSTAS,
                    Embeddings_Op.DOCUMENT
                );

                // Adicionar metadados específicos do Q&A
                qaEmbedding.getMetadados().put("tipo_embedding", "qa_pair");
                qaEmbedding.getMetadados().put("pergunta", qa.getQuestion());
                qaEmbedding.getMetadados().put("resposta", qa.getAnswer());
                qaEmbedding.getMetadados().put("qa_pair_id", String.valueOf(i));
                qaEmbedding.getMetadados().put("total_qa_pairs", String.valueOf(qaList.size()));

                // Adicionar metadados do capítulo
                if (capitulo.getId() != null) {
                    qaEmbedding.getMetadados().put("capitulo_id", capitulo.getId().toString());
                }
                if (capitulo.getDocumentoId() != null) {
                    qaEmbedding.getMetadados().put("documento_id", capitulo.getDocumentoId().toString());
                }
                qaEmbedding.getMetadados().put("capitulo_titulo", capitulo.getTitulo());
                if (capitulo.getOrdemDoc() != null) {
                    qaEmbedding.getMetadados().put("capitulo_ordem", capitulo.getOrdemDoc().toString());
                }
                if (capitulo.getTokensTotal() != null) {
                    qaEmbedding.getMetadados().put("capitulo_tokens_total", capitulo.getTokensTotal().toString());
                }

                // Adicionar metadados adicionais do capítulo se existirem
                if (capitulo.getMetadados() != null) {
                    capitulo.getMetadados().forEach((key, value) -> {
                        if (value != null && !value.toString().trim().isEmpty()) {
                            qaEmbedding.getMetadados().put("capitulo_" + key, value.toString());
                        }
                    });
                }

                qaEmbeddings.add(qaEmbedding);
            }

            logger.debug("Created {} Q&A embeddings for chapter: {}", qaEmbeddings.size(), capitulo.getTitulo());

        } catch (Exception e) {
            logger.error("Failed to create Q&A embeddings for chapter {}: {}", capitulo.getTitulo(), e.getMessage());
        }

        return qaEmbeddings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float[] createSearchEmbeddings(@NonNull String pesquisa,
                                         @NonNull BibliotecaDTO biblioteca) {
        return createEmbeddings(Embeddings_Op.QUERY, pesquisa, biblioteca);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float[] createEmbeddings(@NonNull Embeddings_Op operation,
                                   @NonNull String text,
                                   @NonNull BibliotecaDTO biblioteca) {
        logger.debug("Creating {} embedding for text of {} characters", operation, text.length());

        if (llmService == null) {
            logger.error("LLM service not available for embedding creation");
            return new float[0];
        }

        try {
            MapParam params = new MapParam();

            // Configurar parâmetros baseados na biblioteca se necessário
            if (biblioteca.getMetadados() != null) {
                // Adicionar contexto da biblioteca se disponível
                params.put("library_context", biblioteca.getNome());
            }

            float[] embedding = llmService.embeddings(operation, text, params);

            logger.debug("Successfully created embedding with {} dimensions", embedding.length);
            return embedding;

        } catch (LLMException e) {
            logger.error("Failed to create embedding: {}", e.getMessage());
            return new float[0];
        }
    }

    // ================ MÉTODOS AUXILIARES ================

    /**
     * Cria embedding com texto completo + metadados
     */
    private DocEmbeddingDTO createFullTextEmbedding(CapituloDTO capitulo, BibliotecaDTO biblioteca) throws LLMException {
        String fullText = buildTextWithMetadata(capitulo);
        return createEmbeddingFromText(fullText, capitulo.getTitulo(), biblioteca,
                                     TipoEmbedding.CAPITULO, Embeddings_Op.DOCUMENT);
    }

    /**
     * Cria embedding apenas com metadados
     */
    private DocEmbeddingDTO createMetadataOnlyEmbedding(CapituloDTO capitulo, BibliotecaDTO biblioteca) throws LLMException {
        String metadataText = buildMetadataText(capitulo);
        return createEmbeddingFromText(metadataText, capitulo.getTitulo() + " (Metadados)", biblioteca,
                                     TipoEmbedding.METADATA, Embeddings_Op.DOCUMENT);
    }

    /**
     * Cria embedding apenas com texto
     */
    private DocEmbeddingDTO createTextOnlyEmbedding(CapituloDTO capitulo, BibliotecaDTO biblioteca) throws LLMException {
        return createEmbeddingFromText(capitulo.getConteudo(), capitulo.getTitulo(), biblioteca,
                                     TipoEmbedding.CAPITULO, Embeddings_Op.DOCUMENT);
    }

    /**
     * Cria múltiplos embeddings dividindo o texto em chunks
     */
    private List<DocEmbeddingDTO> createSplitTextEmbeddings(CapituloDTO capitulo, BibliotecaDTO biblioteca) {
        List<DocEmbeddingDTO> embeddings = new ArrayList<>();

        try {
            // Usar ContentSplitter para dividir em chunks otimizados
            ContentSplitter contentSplitter = new ContentSplitter();
            List<CapituloDTO> chunks = contentSplitter.splitContent(capitulo.getConteudo(), false);

            for (int i = 0; i < chunks.size(); i++) {
                CapituloDTO chunk = chunks.get(i);
                String chunkTitle = capitulo.getTitulo() + " - Chunk " + (i + 1);

                DocEmbeddingDTO embedding = createEmbeddingFromText(
                    chunk.getConteudo(),
                    chunkTitle,
                    biblioteca,
                    TipoEmbedding.TRECHO,
                    Embeddings_Op.DOCUMENT
                );

                // Adicionar metadados específicos do chunk
                embedding.getMetadados().put("chunk_index", String.valueOf(i));
                embedding.getMetadados().put("total_chunks", String.valueOf(chunks.size()));
                embedding.getMetadados().put("parent_chapter", capitulo.getTitulo());

                embeddings.add(embedding);
            }

        } catch (Exception e) {
            logger.error("Failed to create split text embeddings: {}", e.getMessage());
        }

        return embeddings;
    }

    /**
     * Escolhe automaticamente o tipo de embedding baseado no tamanho do conteúdo
     */
    private List<DocEmbeddingDTO> createAutoEmbeddings(CapituloDTO capitulo, BibliotecaDTO biblioteca) {
        try {
            int tokenCount = estimateTokenCount(capitulo.getConteudo());

            if (tokenCount <= MIN_CHUNK_SIZE) {
                // Muito pequeno, usar texto + metadados
                return List.of(createFullTextEmbedding(capitulo, biblioteca));
            } else if (tokenCount <= DEFAULT_CHUNK_SIZE) {
                // Tamanho ideal, usar apenas texto
                return List.of(createTextOnlyEmbedding(capitulo, biblioteca));
            } else {
                // Muito grande, dividir em chunks
                return createSplitTextEmbeddings(capitulo, biblioteca);
            }

        } catch (Exception e) {
            logger.error("Failed to create auto embeddings: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Cria um DocEmbeddingDTO a partir de texto
     */
    private DocEmbeddingDTO createEmbeddingFromText(String text, String title, 
	    					BibliotecaDTO biblioteca,
                                                TipoEmbedding tipoEmbedding, 
                                                Embeddings_Op operation) 
                                                	   throws LLMException {
        float[] embedding = createEmbeddings(operation, text, biblioteca);

        DocEmbeddingDTO docEmbedding = new DocEmbeddingDTO();       
        docEmbedding.setTrechoTexto(text);
        docEmbedding.setEmbeddingVector(embedding); 
        docEmbedding.setTipoEmbedding(tipoEmbedding);

        // Configurar metadados
        docEmbedding.getMetadados().setNomeDocumento(title);
        docEmbedding.getMetadados().put("biblioteca_id", biblioteca.getId());
        docEmbedding.getMetadados().put("biblioteca_nome", biblioteca.getNome());
        docEmbedding.getMetadados().put("embedding_operation", operation.toString());
        docEmbedding.getMetadados().put("created_at", java.time.Instant.now().toString());

        return docEmbedding;
    }

    /**
     * Constrói texto com metadados incluídos
     */
    private String buildTextWithMetadata(CapituloDTO capitulo) {
        StringBuilder builder = new StringBuilder();

        // Adicionar título
        if (capitulo.getTitulo() != null) {
            builder.append("Título: ").append(capitulo.getTitulo()).append("\n\n");
        }

        // Adicionar metadados relevantes
        if (capitulo.getMetadados() != null) {
            String metadataText = buildMetadataText(capitulo);
            if (!metadataText.isEmpty()) {
                builder.append(metadataText).append("\n\n");
            }
        }

        // Adicionar conteúdo principal
        builder.append(capitulo.getConteudo());

        return builder.toString();
    }

    /**
     * Constrói texto apenas com metadados
     */
    private String buildMetadataText(CapituloDTO capitulo) {
        StringBuilder builder = new StringBuilder();

        if (capitulo.getMetadados() != null) {
            // Adicionar metadados mais relevantes
            capitulo.getMetadados().forEach((key, value) -> {
                if (value != null && !value.toString().trim().isEmpty()) {
                    builder.append(key).append(": ").append(value).append("\n");
                }
            });
        }

        return builder.toString();
    }

    /**
     * Estima o número de tokens em um texto
     */
    private int estimateTokenCount(String text) {
        if (text == null) return 0;

        // Estimativa simples: palavras / 0.75
        String[] words = text.split("\\s+");
        return (int) Math.ceil(words.length / 0.75);
    }

    /**
     * Verifica se o serviço de LLM está disponível
     */
    public boolean isLLMServiceAvailable() {
        return llmService != null;
    }

    /**
     * Obtém estatísticas do processor
     */
    public java.util.Map<String, Object> getProcessorStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("llm_service_available", llmService != null);
        stats.put("default_chunk_size", DEFAULT_CHUNK_SIZE);
        stats.put("min_chunk_size", MIN_CHUNK_SIZE);
        stats.put("service_class", this.getClass().getSimpleName());

        return stats;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DocEmbeddingDTO> createSummaryEmbeddings(CapituloDTO chapter, 
                                                        BibliotecaDTO library,
                                                        Integer maxSummaryLength, 
                                                        String summaryInstructions) {
        logger.debug("Creating summary embeddings for chapter: {}", chapter.getTitulo());

        List<DocEmbeddingDTO> summaryEmbeddings = new ArrayList<>();

        if (chapter.getConteudo() == null || chapter.getConteudo().trim().isEmpty()) {
            logger.warn("Empty content for chapter: {}, cannot create summary", chapter.getTitulo());
            return summaryEmbeddings;
        }

        try {
            // Configurar parâmetros padrão se não fornecidos
            int summaryMaxLength = maxSummaryLength != null ? maxSummaryLength : DEFAULT_CHUNK_SIZE;
            String instructions = summaryInstructions != null ? summaryInstructions : 
                "Crie um sumário conciso e informativo do texto fornecido, " +
                "destacando os pontos principais e informações mais relevantes.";

            // Gerar o sumário usando DocumentSummarizer
            String summary;
            if (summaryInstructions != null && !summaryInstructions.trim().isEmpty()) {
                // Usar instruções customizadas se fornecidas
                summary = documentSummarizer.summarize(chapter.getConteudo(), instructions, summaryMaxLength);
            } else {
                // Usar método simples se não há instruções customizadas
                summary = documentSummarizer.summarize(chapter.getConteudo(), summaryMaxLength);
            }

            if (summary == null || summary.trim().isEmpty()) {
                logger.warn("Failed to generate summary for chapter: {}", chapter.getTitulo());
                return summaryEmbeddings;
            }

            logger.debug("Generated summary with {} characters for chapter: {}", 
                        summary.length(), chapter.getTitulo());

            // Criar embedding do sumário
            DocEmbeddingDTO summaryEmbedding = createEmbeddingFromText(
                summary,
                chapter.getTitulo() + " - Sumário",
                library,
                TipoEmbedding.RESUMO,
                Embeddings_Op.DOCUMENT
            );

            // Adicionar metadados específicos do sumário
            summaryEmbedding.getMetadados().put("tipo_embedding", "summary");
            summaryEmbedding.getMetadados().put("summary_length", String.valueOf(summary.length()));
            summaryEmbedding.getMetadados().put("original_content_length", 
                                               String.valueOf(chapter.getConteudo().length()));
            summaryEmbedding.getMetadados().put("compression_ratio", 
                String.valueOf((double) summary.length() / chapter.getConteudo().length()));
            
            if (maxSummaryLength != null) {
                summaryEmbedding.getMetadados().put("max_summary_length", maxSummaryLength.toString());
            }
            
            if (summaryInstructions != null && !summaryInstructions.trim().isEmpty()) {
                summaryEmbedding.getMetadados().put("custom_instructions", "true");
                // Armazenar hash das instruções para rastreamento
                summaryEmbedding.getMetadados().put("instructions_hash", 
                    String.valueOf(summaryInstructions.hashCode()));
            }

            // Adicionar metadados do capítulo original
            if (chapter.getId() != null) {
                summaryEmbedding.getMetadados().put("capitulo_id", chapter.getId().toString());
            }
            if (chapter.getDocumentoId() != null) {
                summaryEmbedding.getMetadados().put("documento_id", chapter.getDocumentoId().toString());
            }
            summaryEmbedding.getMetadados().put("capitulo_titulo", chapter.getTitulo());
            
            if (chapter.getOrdemDoc() != null) {
                summaryEmbedding.getMetadados().put("capitulo_ordem", chapter.getOrdemDoc().toString());
            }
            if (chapter.getTokensTotal() != null) {
                summaryEmbedding.getMetadados().put("capitulo_tokens_total", chapter.getTokensTotal().toString());
            }

            // Adicionar metadados adicionais do capítulo se existirem
            if (chapter.getMetadados() != null) {
                chapter.getMetadados().forEach((key, value) -> {
                    if (value != null && !value.toString().trim().isEmpty()) {
                        summaryEmbedding.getMetadados().put("capitulo_" + key, value.toString());
                    }
                });
            }

            // Criar embedding híbrido (sumário + contexto) se o texto original for muito longo
            if (chapter.getConteudo().length() > DEFAULT_CHUNK_SIZE * 2) {
                DocEmbeddingDTO hybridEmbedding = createHybridSummaryEmbedding(
                    chapter, summary, library, instructions);
                if (hybridEmbedding != null) {
                    summaryEmbeddings.add(hybridEmbedding);
                }
            }

            summaryEmbeddings.add(summaryEmbedding);

            logger.debug("Successfully created {} summary embeddings for chapter: {}", 
                        summaryEmbeddings.size(), chapter.getTitulo());

        } catch (Exception e) {
            logger.error("Failed to create summary embeddings for chapter {}: {}", 
                        chapter.getTitulo(), e.getMessage(), e);
        }

        return summaryEmbeddings;
    }

    /**
     * Cria um embedding híbrido combinando sumário com contexto estrutural
     */
    private DocEmbeddingDTO createHybridSummaryEmbedding(CapituloDTO chapter, String summary, 
                                                        BibliotecaDTO library, String instructions) {
        try {
            StringBuilder hybridContent = new StringBuilder();
            
            // Adicionar contexto estrutural
            hybridContent.append("Documento: ").append(chapter.getTitulo()).append("\n");
            
            if (chapter.getMetadados() != null) {
                // Adicionar metadados relevantes para contexto
                chapter.getMetadados().forEach((key, value) -> {
                    if (value != null && !value.toString().trim().isEmpty() && 
                        (key.toLowerCase().contains("categoria") || 
                         key.toLowerCase().contains("tipo") ||
                         key.toLowerCase().contains("assunto"))) {
                        hybridContent.append(key).append(": ").append(value).append("\n");
                    }
                });
            }
            
            hybridContent.append("\nSumário:\n").append(summary);
            
            // Adicionar início do texto original para contexto adicional
            String originalStart = chapter.getConteudo();
            if (originalStart.length() > 300) {
                originalStart = originalStart.substring(0, 300) + "...";
            }
            hybridContent.append("\n\nContexto inicial:\n").append(originalStart);

            DocEmbeddingDTO hybridEmbedding = createEmbeddingFromText(
                hybridContent.toString(),
                chapter.getTitulo() + " - Sumário Contextualizado",
                library,
                TipoEmbedding.RESUMO,
                Embeddings_Op.DOCUMENT
            );

            // Metadados específicos do embedding híbrido
            hybridEmbedding.getMetadados().put("tipo_embedding", "hybrid_summary");
            hybridEmbedding.getMetadados().put("includes_context", "true");
            hybridEmbedding.getMetadados().put("hybrid_content_length", 
                                              String.valueOf(hybridContent.length()));

            return hybridEmbedding;

        } catch (Exception e) {
            logger.error("Failed to create hybrid summary embedding: {}", e.getMessage());
            return null;
        }
    }
}