package bor.tools.splitter;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocumentEmbeddingDTO;
import bor.tools.simplerag.dto.DocumentoWithAssociationDTO;
import bor.tools.simplerag.entity.enums.TipoConteudo;
import bor.tools.utils.RAGUtil;

/**
 * Serviço de processamento assíncrono para operações de splitting.
 *
 * Este serviço integra o pacote bor.tools.splitter com o sistema de processamento
 * assíncrono do JSimpleRag, fornecendo operações não-bloqueantes para:
 * - Divisão de documentos
 * - Geração de embeddings
 * - Sumarização
 * - Geração de Q&A
 */
@Service
public class AsyncSplitterService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncSplitterService.class);

    private static final int MIN_TOKENS_FOR_SUMMARY = 512;

    private final SplitterFactory splitterFactory;
    private final EmbeddingProcessorImpl embeddingProcessor;
    private final DocumentSummarizerImpl documentSummarizer;
    private final Executor taskExecutor;

    /**
     * Construtor com injeção de dependências
     */
    public AsyncSplitterService(SplitterFactory splitterFactory,
                               EmbeddingProcessorImpl embeddingProcessor,
                               DocumentSummarizerImpl documentSummarizer,
                               @Qualifier("applicationTaskExecutor") Executor taskExecutor) {
        this.splitterFactory = splitterFactory;
        this.embeddingProcessor = embeddingProcessor;
        this.documentSummarizer = documentSummarizer;
        this.taskExecutor = taskExecutor;
        logger.debug("AsyncSplitterService initialized");
    }

    /**
     * Processa documento de forma assíncrona - operação completa
     *
     * @param documento - documento a ser processado
     * @param biblioteca - biblioteca de destino
     * @param tipoConteudo - tipo de conteúdo (opcional, será detectado se null)
     * @return Future com lista de capítulos processados
     */
    @Async
    public CompletableFuture<List<ChapterDTO>> processDocumentAsync(DocumentoWithAssociationDTO documento,
                                                                    LibraryDTO biblioteca,
                                                                    TipoConteudo tipoConteudo) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Starting async document processing for: {}", documento.getTitulo());

                // 1. Criar splitter apropriado
                AbstractSplitter splitter;
                if (tipoConteudo != null) {
                    splitter = splitterFactory.createSplitter(tipoConteudo, biblioteca);
                } else {
                    splitter = splitterFactory.createSplitter(documento.getTexto(), biblioteca);
                }

                // 2. Dividir documento em capítulos
                List<ChapterDTO> capitulos = splitter.splitBySize(documento,
                					splitterFactory.getSplitterConfig().getEffectiveChunkSize(biblioteca.getUuid().toString(),           	    
                					tipoConteudo));

                logger.debug("Document {} split into {} chapters", documento.getTitulo(), capitulos.size());

                // 3. Enriquecer capítulos com metadados
                for (ChapterDTO capitulo : capitulos) {
                    enrichChapterMetadata(capitulo, documento, biblioteca, tipoConteudo);
                }

                return capitulos;

            } catch (Exception e) {
                logger.error("Failed to process document {}: {}", documento.getTitulo(), e.getMessage(), e);
                throw new RuntimeException("Document processing failed", e);
            }
        }, taskExecutor);
    }

    /**
     * Gera embeddings para capítulo de forma assíncrona
     *
     * @param capitulo - capítulo para gerar embeddings
     * @param biblioteca - biblioteca de origem
     * @param flagGeneration - modo de geração de embeddings
     * @return Future com lista de embeddings gerados
     */
    @Async
    public CompletableFuture<List<DocumentEmbeddingDTO>> generateEmbeddingsAsync(ChapterDTO capitulo,
                                                                           LibraryDTO biblioteca,
                                                                           int flagGeneration) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Generating embeddings for chapter: {}", capitulo.getTitulo());

                List<DocumentEmbeddingDTO> embeddings = embeddingProcessor.createChapterEmbeddings(
                    capitulo, biblioteca, flagGeneration);

                logger.debug("Generated {} embeddings for chapter: {}", embeddings.size(), capitulo.getTitulo());
                return embeddings;

            } catch (Exception e) {
                logger.error("Failed to generate embeddings for chapter {}: {}",
                           capitulo.getTitulo(), e.getMessage(), e);
                throw new RuntimeException("Embedding generation failed", e);
            }
        }, taskExecutor);
    }

    /**
     * Gera Q&A para capítulo de forma assíncrona
     *
     * @param capitulo - capítulo para gerar Q&A
     * @param biblioteca - biblioteca de origem
     * @param numQuestions - número de perguntas a gerar
     * @return Future com lista de embeddings Q&A
     */
    @Async
    public CompletableFuture<List<DocumentEmbeddingDTO>> generateQAAsync(ChapterDTO capitulo,
                                                                   LibraryDTO biblioteca,
                                                                   Integer numQuestions) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Generating Q&A for chapter: {}", capitulo.getTitulo());

                List<DocumentEmbeddingDTO> qaEmbeddings = embeddingProcessor.createQAEmbeddings(
                    capitulo, biblioteca, numQuestions);

                logger.debug("Generated {} Q&A embeddings for chapter: {}", qaEmbeddings.size(), capitulo.getTitulo());
                return qaEmbeddings;

            } catch (Exception e) {
                logger.error("Failed to generate Q&A for chapter {}: {}",
                           capitulo.getTitulo(), e.getMessage(), e);
                throw new RuntimeException("Q&A generation failed", e);
            }
        }, taskExecutor);
    }

    /**
     * Gera sumário para capítulo de forma assíncrona
     *
     * @param capitulo - capítulo para sumarizar
     * @param biblioteca - biblioteca de origem
     * @param maxLength - tamanho máximo do sumário
     * @param instructions - instruções customizadas (opcional)
     * @return Future com lista de embeddings de sumário
     */
    @Async
    public CompletableFuture<List<DocumentEmbeddingDTO>> generateSummaryAsync(ChapterDTO capitulo,
                                                                        LibraryDTO biblioteca,
                                                                        Integer maxLength,
                                                                        String instructions) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Generating summary for chapter: {}", capitulo.getTitulo());

                List<DocumentEmbeddingDTO> summaryEmbeddings = embeddingProcessor.createSummaryEmbeddings(
                    capitulo, biblioteca, maxLength, instructions);

                logger.debug("Generated {} summary embeddings for chapter: {}",
                           summaryEmbeddings.size(), capitulo.getTitulo());
                return summaryEmbeddings;

            } catch (Exception e) {
                logger.error("Failed to generate summary for chapter {}: {}",
                           capitulo.getTitulo(), e.getMessage(), e);
                throw new RuntimeException("Summary generation failed", e);
            }
        }, taskExecutor);
    }

    /**
     * Processamento completo assíncrono: splitting + embeddings + Q&A + sumário
     *
     * @param documento - documento a ser processado
     * @param biblioteca - biblioteca de destino
     * @param tipoConteudo - tipo de conteúdo
     * @param includeQA - se deve incluir geração de Q&A
     * @param includeSummary - se deve incluir geração de sumário
     * @return Future com resultado completo do processamento
     */
    @Async
    public CompletableFuture<ProcessingResult> fullProcessingAsync(DocumentoWithAssociationDTO documento,
                                                                  LibraryDTO biblioteca,
                                                                  TipoConteudo tipoConteudo,
                                                                  boolean includeQA,
                                                                  boolean includeSummary) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Starting full async processing for document: {}", documento.getTitulo());

                ProcessingResult result = new ProcessingResult();
                result.setDocumento(documento);
                result.setBiblioteca(biblioteca);

                // 1. Processar documento (splitting)
                List<ChapterDTO> capitulos = processDocumentAsync(documento, biblioteca, tipoConteudo).get();
                result.setCapitulos(capitulos);

                // 2. Para cada capítulo, gerar embeddings
                for (ChapterDTO capitulo : capitulos) {
                    // Embeddings básicos
                    List<DocumentEmbeddingDTO> embeddings = generateEmbeddingsAsync(
                        capitulo, biblioteca, EmbeddingProcessorInterface.FLAG_AUTO).get();
                    result.addEmbeddings(embeddings);

                    // Q&A se solicitado
                    if (includeQA) {
                        List<DocumentEmbeddingDTO> qaEmbeddings = generateQAAsync(capitulo, biblioteca, null).get();
                        result.addEmbeddings(qaEmbeddings);
                    }

                    // Sumário se solicitado
                    int tokens = capitulo.getConteudo() != null ? RAGUtil.countTokens(capitulo.getConteudo()) : 0;
                    
                    if (includeSummary && capitulo.getConteudo() != null && tokens > MIN_TOKENS_FOR_SUMMARY) {
                        List<DocumentEmbeddingDTO> summaryEmbeddings = generateSummaryAsync(
                            capitulo, biblioteca, null, null).get();
                        result.addEmbeddings(summaryEmbeddings);
                    }
                }

                logger.debug("Completed full processing for document: {} - {} chapters, {} embeddings",
                           documento.getTitulo(), result.getCapitulos().size(), result.getAllEmbeddings().size());

                return result;

            } catch (Exception e) {
                logger.error("Failed full processing for document {}: {}",
                           documento.getTitulo(), e.getMessage(), e);
                throw new RuntimeException("Full processing failed", e);
            }
        }, taskExecutor);
    }

    /**
     * Enriquece metadados do capítulo com informações de processamento
     */
    private void enrichChapterMetadata(ChapterDTO capitulo, DocumentoWithAssociationDTO documento,
                                     LibraryDTO biblioteca, TipoConteudo tipoConteudo) {
        if (capitulo.getMetadados() == null) {
            capitulo.initializeMetadata();
        }

        // Adicionar metadados de processamento
        capitulo.addMetadata("documento_id", documento.getId());
        capitulo.addMetadata("documento_titulo", documento.getTitulo());
        capitulo.addMetadata("biblioteca_id", biblioteca.getId());
        capitulo.addMetadata("biblioteca_nome", biblioteca.getNome());
        capitulo.addMetadata("processed_at", java.time.Instant.now().toString());

        if (tipoConteudo != null) {
            capitulo.addMetadata("tipo_conteudo", tipoConteudo.toString());
        }

        // Estatísticas do capítulo
        if (capitulo.getConteudo() != null) {
            capitulo.addMetadata("character_count", String.valueOf(capitulo.getConteudo().length()));
            capitulo.addMetadata("word_count", String.valueOf(capitulo.getConteudo().split("\\s+").length));
        }
    }

    /**
     * Resultado completo do processamento
     */
    public static class ProcessingResult {
        private DocumentoWithAssociationDTO documento;
        private LibraryDTO biblioteca;
        private List<ChapterDTO> capitulos;
        private List<DocumentEmbeddingDTO> allEmbeddings = new java.util.ArrayList<>();

        // Getters e Setters
        public DocumentoWithAssociationDTO getDocumento() { return documento; }
        public void setDocumento(DocumentoWithAssociationDTO documento) { this.documento = documento; }

        public LibraryDTO getBiblioteca() { return biblioteca; }
        public void setBiblioteca(LibraryDTO biblioteca) { this.biblioteca = biblioteca; }

        public List<ChapterDTO> getCapitulos() { return capitulos; }
        public void setCapitulos(List<ChapterDTO> capitulos) { this.capitulos = capitulos; }

        public List<DocumentEmbeddingDTO> getAllEmbeddings() { return allEmbeddings; }
        public void setAllEmbeddings(List<DocumentEmbeddingDTO> allEmbeddings) { this.allEmbeddings = allEmbeddings; }

        public void addEmbeddings(List<DocumentEmbeddingDTO> embeddings) {
            if (embeddings != null) {
                this.allEmbeddings.addAll(embeddings);
            }
        }

        /**
         * Obtém estatísticas do processamento
         */
        public ProcessingStats getStats() {
            ProcessingStats stats = new ProcessingStats();
            stats.setTotalChapters(capitulos != null ? capitulos.size() : 0);
            stats.setTotalEmbeddings(allEmbeddings != null ? allEmbeddings.size() : 0);

            if (capitulos != null) {
                int totalChars = capitulos.stream()
                    .mapToInt(cap -> cap.getConteudo() != null ? cap.getConteudo().length() : 0)
                    .sum();
                stats.setTotalCharacters(totalChars);
            }

            return stats;
        }
    }

    /**
     * Estatísticas de processamento
     */
    public static class ProcessingStats {
        private int totalChapters;
        private int totalEmbeddings;
        private int totalCharacters;

        // Getters e Setters
        public int getTotalChapters() { return totalChapters; }
        public void setTotalChapters(int totalChapters) { this.totalChapters = totalChapters; }

        public int getTotalEmbeddings() { return totalEmbeddings; }
        public void setTotalEmbeddings(int totalEmbeddings) { this.totalEmbeddings = totalEmbeddings; }

        public int getTotalCharacters() { return totalCharacters; }
        public void setTotalCharacters(int totalCharacters) { this.totalCharacters = totalCharacters; }
    }

    /**
     * Verifica se o serviço está configurado corretamente
     */
    public boolean isFullyConfigured() {
        return splitterFactory != null &&
               embeddingProcessor != null &&
               documentSummarizer != null &&
               taskExecutor != null;
    }

    /**
     * Obtém estatísticas do serviço
     */
    public java.util.Map<String, Object> getServiceStats() {
        java.util.Map<String, Object> stats = new java.util.concurrent.ConcurrentHashMap<>();
        stats.put("splitter_factory_available", splitterFactory != null);
        stats.put("embedding_processor_available", embeddingProcessor != null);
        stats.put("document_summarizer_available", documentSummarizer != null);
        stats.put("task_executor_available", taskExecutor != null);
        stats.put("fully_configured", isFullyConfigured());

        if (splitterFactory != null) {
            stats.put("factory_stats", splitterFactory.getFactoryStats());
        }

        return stats;
    }
}