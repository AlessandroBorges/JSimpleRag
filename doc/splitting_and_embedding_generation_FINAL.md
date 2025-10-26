# Estratégia de Refatoração: Splitting e Geração de Embeddings - VERSÃO FINAL

**Versão**: 2.0 FINAL
**Data**: 2025-01-25
**Status**: ✅ APROVADO PARA IMPLEMENTAÇÃO
**Autor**: Claude Code

---

## 📋 Sumário de Decisões Tomadas

Este documento é a **versão final aprovada** da estratégia de refatoração, incorporando:
- ✅ Análise de impacto do `LLMServiceManager`
- ✅ Decisões arquiteturais tomadas
- ✅ Questões respondidas
- ✅ Plano de implementação atualizado

**Documentos de referência:**
- `splitting_and_embedding_generation.md` (proposta original)
- `splitting_and_embedding_generation_IMPACT_ANALYSIS.md` (análise de impacto)
- `LLMSERVICE_POOL_ANALYSIS.md` (contexto do pool)

---

## ✅ Decisões Arquiteturais APROVADAS

### Decisão 1: Configuração de Modelos
**Escolhida: Opção C - Híbrida** ⭐

- Configuração global como padrão
- Override opcional por biblioteca
- Prioridade: request → library → global

**Implementação:**

```properties
# application.properties
rag.embedding.default-model=nomic-embed-text
rag.completion.default-model=qwen3-1.7b
```

```java
// LibraryDTO (campos opcionais)
private String defaultEmbeddingModel;   // null = usa global
private String defaultCompletionModel;  // null = usa global
```

---

### Decisão 2: API do EmbeddingService
**Escolhida: Opção B - Sobrecarga de Métodos** ⭐

```java
public interface EmbeddingService {
    // Usa modelo padrão da biblioteca (ou global)
    float[] generateQueryEmbedding(String query, LibraryDTO library);

    // Permite override explícito do modelo
    float[] generateQueryEmbedding(String query, LibraryDTO library, String modelName);
}
```

**Benefício:** Conveniência (uso simples) + Flexibilidade (override quando necessário)

---

### Decisão 3: Tratamento de Modelo Não Encontrado
**Escolhida: Opção A - Fail-Fast** ⭐

```java
LLMService service = llmServiceManager.getLLMServiceByRegisteredModel(modelName);

if (service == null) {
    throw new IllegalStateException(
        "No LLM service found for model: " + modelName +
        ". Check if the model is registered in any provider."
    );
}
```

**Benefício:** Erro claro e imediato, facilita debugging

---

## ✅ Questões Respondidas

### 1. EmbeddingContext Object
**Resposta: SIM** ✅

Criar um `EmbeddingContext` para encapsular biblioteca + metadados + configurações:

```java
@Data
@Builder
public class EmbeddingContext {
    private LibraryDTO library;
    private String embeddingModelName;
    private String completionModelName;
    private Map<String, Object> additionalMetadata;

    /**
     * Resolve o modelo de embedding a ser usado.
     * Prioridade: explícito → library → global
     */
    public String resolveEmbeddingModel(String globalDefault) {
        if (embeddingModelName != null) return embeddingModelName;
        if (library != null && library.getDefaultEmbeddingModel() != null) {
            return library.getDefaultEmbeddingModel();
        }
        return globalDefault;
    }

    public String resolveCompletionModel(String globalDefault) {
        if (completionModelName != null) return completionModelName;
        if (library != null && library.getDefaultCompletionModel() != null) {
            return library.getDefaultCompletionModel();
        }
        return globalDefault;
    }
}
```

---

### 2. Cache de Embeddings
**Resposta: NÃO** ❌

Não implementar cache de embeddings no `EmbeddingService`.

**Motivação:**
- Embeddings são gerados uma vez e persistidos no banco
- Cache adiciona complexidade desnecessária
- Se necessário no futuro, pode ser adicionado em camada separada

---

### 3. Eventos de Domínio
**Resposta: NÃO** ❌

Não criar eventos de domínio para conclusão de processamento nesta fase.

**Motivação:**
- YAGNI (You Aren't Gonna Need It) - adicionar quando houver necessidade real
- Pode ser adicionado posteriormente sem impactar arquitetura

---

### 4. Retry Logic no EmbeddingOrchestrator
**Resposta: SIM** ✅

**Especificação:**
- Aguardar **2 minutos** entre tentativas
- Máximo de **2 tentativas** (3 tentativas totais: original + 2 retries)

**Implementação:**

```java
@Service
public class EmbeddingOrchestrator {

    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 2 * 60 * 1000; // 2 minutos

    @Async
    public CompletableFuture<ProcessingResult> processDocumentFull(
            DocumentoDTO documento,
            EmbeddingContext context,
            ProcessingOptions options) {

        return CompletableFuture.supplyAsync(() -> {
            int attempt = 0;
            Exception lastException = null;

            while (attempt <= MAX_RETRIES) {
                try {
                    return executeProcessing(documento, context, options);

                } catch (LLMServiceException | RuntimeException e) {
                    lastException = e;
                    attempt++;

                    if (attempt <= MAX_RETRIES) {
                        log.warn("Processing failed (attempt {}/{}): {}. Retrying in 2 minutes...",
                                attempt, MAX_RETRIES + 1, e.getMessage());
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Interrupted during retry wait", ie);
                        }
                    }
                }
            }

            log.error("Processing failed after {} attempts", MAX_RETRIES + 1);
            throw new RuntimeException(
                "Document processing failed after " + (MAX_RETRIES + 1) + " attempts",
                lastException
            );
        }, taskExecutor);
    }
}
```

---

## 🏗️ Arquitetura Final com LLMServiceManager

### Nova Estrutura de Pacotes

```
src/main/java/bor/tools/
│
├── splitter/                                    ← APENAS SPLITTING
│   ├── DocumentSplitter.java
│   ├── SplitterFactory.java
│   ├── SplitterWiki.java
│   ├── SplitterNorma.java
│   ├── SplitterGenerico.java
│   ├── ContentSplitter.java
│   └── DocumentRouter.java
│
└── simplerag/
    └── service/
        ├── DocumentoService.java
        ├── LibraryService.java
        ├── ChatService.java
        │
        ├── embedding/                           ← NOVA CAMADA DE EMBEDDING
        │   ├── EmbeddingService.java           ⭐ Interface principal
        │   ├── EmbeddingServiceImpl.java       ⭐ Implementação
        │   ├── EmbeddingOrchestrator.java      ⭐ Orquestração + Retry
        │   │
        │   ├── strategy/                        ← STRATEGY PATTERN
        │   │   ├── EmbeddingGenerationStrategy.java     (Interface)
        │   │   ├── ChapterEmbeddingStrategy.java        (Capítulos)
        │   │   ├── QueryEmbeddingStrategy.java          (Queries)
        │   │   ├── QAEmbeddingStrategy.java             (Q&A)
        │   │   └── SummaryEmbeddingStrategy.java        (Resumos)
        │   │
        │   └── model/
        │       ├── EmbeddingRequest.java        ⭐ Request object
        │       ├── EmbeddingContext.java        ⭐ Context object
        │       └── ProcessingOptions.java       ⭐ Options object
        │
        ├── summarization/                       ← NOVA CAMADA DE SUMARIZAÇÃO
        │   ├── SummarizationService.java
        │   └── SummarizationServiceImpl.java
        │
        └── llm/                                 ← JÁ EXISTE
            ├── LLMServiceManager.java           ✅ Pool de LLMServices
            ├── LLMServiceStrategy.java
            └── LLMServiceStats.java
```

---

## 📐 Componentes Detalhados com LLMServiceManager

### 1. EmbeddingContext (NOVO)

```java
package bor.tools.simplerag.service.embedding.model;

import java.util.HashMap;
import java.util.Map;

import bor.tools.simplerag.dto.LibraryDTO;
import lombok.Builder;
import lombok.Data;

/**
 * Context object for embedding operations.
 * Encapsulates library, model configuration, and metadata.
 */
@Data
@Builder
public class EmbeddingContext {

    /**
     * Library context for the embedding operation
     */
    private LibraryDTO library;

    /**
     * Override for embedding model name (optional)
     * If null, uses library.defaultEmbeddingModel or global config
     */
    private String embeddingModelName;

    /**
     * Override for completion model name (optional)
     * If null, uses library.defaultCompletionModel or global config
     */
    private String completionModelName;

    /**
     * Additional metadata for the operation
     */
    @Builder.Default
    private Map<String, Object> additionalMetadata = new HashMap<>();

    /**
     * Resolves which embedding model to use.
     * Priority: explicit override → library default → global default
     */
    public String resolveEmbeddingModel(String globalDefault) {
        if (embeddingModelName != null && !embeddingModelName.trim().isEmpty()) {
            return embeddingModelName;
        }
        if (library != null && library.getDefaultEmbeddingModel() != null) {
            return library.getDefaultEmbeddingModel();
        }
        return globalDefault;
    }

    /**
     * Resolves which completion model to use.
     * Priority: explicit override → library default → global default
     */
    public String resolveCompletionModel(String globalDefault) {
        if (completionModelName != null && !completionModelName.trim().isEmpty()) {
            return completionModelName;
        }
        if (library != null && library.getDefaultCompletionModel() != null) {
            return library.getDefaultCompletionModel();
        }
        return globalDefault;
    }

    public void addMetadata(String key, Object value) {
        this.additionalMetadata.put(key, value);
    }

    public Object getMetadata(String key) {
        return this.additionalMetadata.get(key);
    }
}
```

---

### 2. EmbeddingRequest (ATUALIZADO)

```java
package bor.tools.simplerag.service.embedding.model;

import bor.tools.simplellm.ModelEmbedding.Embeddings_Op;
import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.entity.enums.TipoEmbedding;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmbeddingRequest {

    // Dados de entrada
    private ChapterDTO chapter;
    private String text;

    // Contexto
    private EmbeddingContext context;  // ⭐ Usa EmbeddingContext

    // Tipo de operação
    private Embeddings_Op operation;
    private TipoEmbedding tipoEmbedding;

    // Configuração de geração
    @Builder.Default
    private Integer generationFlag = 5; // FLAG_AUTO

    // Q&A specific
    private Integer numberOfQAPairs;

    // Summary specific
    private Integer maxSummaryLength;
    private String customSummaryInstructions;

    // Metadata options
    @Builder.Default
    private boolean includeMetadata = true;

    // IDs para contexto
    private Integer documentId;
    private Integer chapterId;

    /**
     * Convenience method to get embedding model from context
     */
    public String getEmbeddingModelName(String globalDefault) {
        return context != null
            ? context.resolveEmbeddingModel(globalDefault)
            : globalDefault;
    }

    /**
     * Convenience method to get completion model from context
     */
    public String getCompletionModelName(String globalDefault) {
        return context != null
            ? context.resolveCompletionModel(globalDefault)
            : globalDefault;
    }
}
```

---

### 3. ProcessingOptions (NOVO)

```java
package bor.tools.simplerag.service.embedding.model;

import lombok.Builder;
import lombok.Data;

/**
 * Options for document processing.
 */
@Data
@Builder
public class ProcessingOptions {

    /**
     * Whether to generate Q&A embeddings
     */
    @Builder.Default
    private boolean includeQA = false;

    /**
     * Whether to generate summary embeddings
     */
    @Builder.Default
    private boolean includeSummary = false;

    /**
     * Number of Q&A pairs to generate (null = default)
     */
    private Integer qaCount;

    /**
     * Maximum summary length (null = default)
     */
    private Integer maxSummaryLength;

    /**
     * Custom summarization instructions
     */
    private String summaryInstructions;
}
```

---

### 4. EmbeddingService (ATUALIZADO)

```java
package bor.tools.simplerag.service.embedding;

import java.util.List;

import bor.tools.simplellm.ModelEmbedding.Embeddings_Op;
import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocumentEmbeddingDTO;
import bor.tools.simplerag.service.embedding.model.EmbeddingContext;
import bor.tools.simplerag.service.embedding.model.EmbeddingRequest;

/**
 * Service interface for embedding generation operations.
 *
 * Integrates with LLMServiceManager pool for multi-provider support.
 */
public interface EmbeddingService {

    // ========== Chapter Embeddings ==========

    /**
     * Generate embeddings for a chapter using automatic strategy.
     * Uses default embedding model from context or global config.
     */
    List<DocumentEmbeddingDTO> generateChapterEmbeddings(
            ChapterDTO chapter,
            EmbeddingContext context);

    /**
     * Generate embeddings for a chapter with specific generation flag.
     */
    List<DocumentEmbeddingDTO> generateChapterEmbeddings(
            ChapterDTO chapter,
            EmbeddingContext context,
            int generationFlag);

    // ========== Query Embeddings ==========

    /**
     * Generate query-optimized embedding for search operations.
     * Uses default embedding model from context or global config.
     */
    float[] generateQueryEmbedding(String query, EmbeddingContext context);

    /**
     * Generate query embedding with explicit model override.
     */
    float[] generateQueryEmbedding(String query, EmbeddingContext context, String modelName);

    // ========== Q&A Embeddings ==========

    /**
     * Generate Q&A embeddings from chapter content.
     * Uses completion model for generation and embedding model for vectors.
     */
    List<DocumentEmbeddingDTO> generateQAEmbeddings(
            ChapterDTO chapter,
            EmbeddingContext context,
            Integer numberOfPairs);

    // ========== Summary Embeddings ==========

    /**
     * Generate summary-based embeddings from chapter content.
     * Uses completion model for summarization and embedding model for vectors.
     */
    List<DocumentEmbeddingDTO> generateSummaryEmbeddings(
            ChapterDTO chapter,
            EmbeddingContext context,
            Integer maxSummaryLength,
            String customInstructions);

    // ========== Low-level Methods ==========

    /**
     * Generate embedding using custom operation type and text.
     */
    float[] generateEmbedding(
            Embeddings_Op operation,
            String text,
            EmbeddingContext context);

    /**
     * Generate embedding with explicit model override.
     */
    float[] generateEmbedding(
            Embeddings_Op operation,
            String text,
            EmbeddingContext context,
            String modelName);

    /**
     * Generate embeddings using custom request configuration.
     */
    List<DocumentEmbeddingDTO> generateEmbeddings(EmbeddingRequest request);
}
```

---

### 5. EmbeddingServiceImpl (IMPLEMENTAÇÃO)

```java
package bor.tools.simplerag.service.embedding;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import bor.tools.simplellm.LLMService;
import bor.tools.simplellm.MapParam;
import bor.tools.simplellm.ModelEmbedding.Embeddings_Op;
import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocumentEmbeddingDTO;
import bor.tools.simplerag.service.embedding.model.EmbeddingContext;
import bor.tools.simplerag.service.embedding.model.EmbeddingRequest;
import bor.tools.simplerag.service.embedding.strategy.EmbeddingGenerationStrategy;
import bor.tools.simplerag.service.llm.LLMServiceManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingServiceImpl implements EmbeddingService {

    private final LLMServiceManager llmServiceManager;
    private final List<EmbeddingGenerationStrategy> strategies;

    @Value("${rag.embedding.default-model:nomic-embed-text}")
    private String defaultEmbeddingModel;

    @Value("${rag.completion.default-model:qwen3-1.7b}")
    private String defaultCompletionModel;

    // ========== Chapter Embeddings ==========

    @Override
    public List<DocumentEmbeddingDTO> generateChapterEmbeddings(
            ChapterDTO chapter,
            EmbeddingContext context) {
        return generateChapterEmbeddings(chapter, context, 5); // FLAG_AUTO
    }

    @Override
    public List<DocumentEmbeddingDTO> generateChapterEmbeddings(
            ChapterDTO chapter,
            EmbeddingContext context,
            int generationFlag) {

        EmbeddingRequest request = EmbeddingRequest.builder()
                .chapter(chapter)
                .context(context)
                .generationFlag(generationFlag)
                .operation(Embeddings_Op.DOCUMENT)
                .build();

        return findStrategy(request).generate(request);
    }

    // ========== Query Embeddings ==========

    @Override
    public float[] generateQueryEmbedding(String query, EmbeddingContext context) {
        String modelName = context.resolveEmbeddingModel(defaultEmbeddingModel);
        return generateQueryEmbedding(query, context, modelName);
    }

    @Override
    public float[] generateQueryEmbedding(String query, EmbeddingContext context, String modelName) {
        log.debug("Generating query embedding with model: {}", modelName);

        LLMService llmService = llmServiceManager.getLLMServiceByRegisteredModel(modelName);

        if (llmService == null) {
            throw new IllegalStateException(
                "No LLM service found for embedding model: " + modelName +
                ". Check if the model is registered in any provider."
            );
        }

        try {
            MapParam params = new MapParam().model(modelName);

            // Add library context if available
            if (context != null && context.getLibrary() != null) {
                params.put("library_context", context.getLibrary().getNome());
            }

            return llmService.embeddings(Embeddings_Op.QUERY, query, params);

        } catch (Exception e) {
            log.error("Failed to generate query embedding: {}", e.getMessage(), e);
            throw new RuntimeException("Query embedding generation failed", e);
        }
    }

    // ========== Q&A Embeddings ==========

    @Override
    public List<DocumentEmbeddingDTO> generateQAEmbeddings(
            ChapterDTO chapter,
            EmbeddingContext context,
            Integer numberOfPairs) {

        EmbeddingRequest request = EmbeddingRequest.builder()
                .chapter(chapter)
                .context(context)
                .numberOfQAPairs(numberOfPairs)
                .operation(Embeddings_Op.DOCUMENT)
                .build();

        return findStrategy(request).generate(request);
    }

    // ========== Summary Embeddings ==========

    @Override
    public List<DocumentEmbeddingDTO> generateSummaryEmbeddings(
            ChapterDTO chapter,
            EmbeddingContext context,
            Integer maxSummaryLength,
            String customInstructions) {

        EmbeddingRequest request = EmbeddingRequest.builder()
                .chapter(chapter)
                .context(context)
                .maxSummaryLength(maxSummaryLength)
                .customSummaryInstructions(customInstructions)
                .operation(Embeddings_Op.DOCUMENT)
                .build();

        return findStrategy(request).generate(request);
    }

    // ========== Low-level Methods ==========

    @Override
    public float[] generateEmbedding(Embeddings_Op operation, String text, EmbeddingContext context) {
        String modelName = context.resolveEmbeddingModel(defaultEmbeddingModel);
        return generateEmbedding(operation, text, context, modelName);
    }

    @Override
    public float[] generateEmbedding(
            Embeddings_Op operation,
            String text,
            EmbeddingContext context,
            String modelName) {

        LLMService llmService = llmServiceManager.getLLMServiceByRegisteredModel(modelName);

        if (llmService == null) {
            throw new IllegalStateException("No LLM service found for model: " + modelName);
        }

        try {
            MapParam params = new MapParam().model(modelName);
            return llmService.embeddings(operation, text, params);
        } catch (Exception e) {
            log.error("Failed to generate embedding: {}", e.getMessage(), e);
            throw new RuntimeException("Embedding generation failed", e);
        }
    }

    @Override
    public List<DocumentEmbeddingDTO> generateEmbeddings(EmbeddingRequest request) {
        return findStrategy(request).generate(request);
    }

    // ========== Helper Methods ==========

    private EmbeddingGenerationStrategy findStrategy(EmbeddingRequest request) {
        return strategies.stream()
                .filter(s -> s.supports(request))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                    "No strategy found for request: " + request));
    }
}
```

---

### 6. EmbeddingOrchestrator com Retry Logic

```java
package bor.tools.simplerag.service.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import bor.tools.simplerag.dto.ChapterDTO;
import bor.tools.simplerag.dto.DocumentEmbeddingDTO;
import bor.tools.simplerag.dto.DocumentoWithAssociationDTO;
import bor.tools.simplerag.entity.enums.TipoConteudo;
import bor.tools.simplerag.service.embedding.model.EmbeddingContext;
import bor.tools.simplerag.service.embedding.model.ProcessingOptions;
import bor.tools.splitter.DocumentRouter;
import bor.tools.splitter.DocumentSplitter;
import bor.tools.splitter.SplitterFactory;
import bor.tools.utils.RAGUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrator for complete document processing with embeddings.
 * Includes retry logic for LLM failures.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingOrchestrator {

    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 2 * 60 * 1000; // 2 minutes
    private static final int MIN_TOKENS_FOR_SUMMARY = 500;

    private final EmbeddingService embeddingService;
    private final SplitterFactory splitterFactory;
    private final DocumentRouter documentRouter;

    /**
     * Process document with full embedding generation pipeline.
     * Includes automatic retry on failure.
     */
    @Async
    public CompletableFuture<ProcessingResult> processDocumentFull(
            DocumentoWithAssociationDTO documento,
            EmbeddingContext context,
            ProcessingOptions options) {

        return CompletableFuture.supplyAsync(() -> {
            int attempt = 0;
            Exception lastException = null;

            while (attempt <= MAX_RETRIES) {
                try {
                    log.info("Processing document (attempt {}/{}): {}",
                            attempt + 1, MAX_RETRIES + 1, documento.getTitulo());

                    return executeProcessing(documento, context, options);

                } catch (Exception e) {
                    lastException = e;
                    attempt++;

                    if (attempt <= MAX_RETRIES) {
                        log.warn("Processing failed (attempt {}/{}): {}. Retrying in 2 minutes...",
                                attempt, MAX_RETRIES + 1, e.getMessage());
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Interrupted during retry wait", ie);
                        }
                    }
                }
            }

            log.error("Processing failed after {} attempts for document: {}",
                    MAX_RETRIES + 1, documento.getTitulo());
            throw new RuntimeException(
                    "Document processing failed after " + (MAX_RETRIES + 1) + " attempts",
                    lastException
            );
        });
    }

    /**
     * Execute the actual processing logic.
     */
    private ProcessingResult executeProcessing(
            DocumentoWithAssociationDTO documento,
            EmbeddingContext context,
            ProcessingOptions options) {

        ProcessingResult result = new ProcessingResult();
        result.setDocumento(documento);

        // 1. Detect content type
        TipoConteudo tipoConteudo = documentRouter.detectContentType(documento.getConteudoMarkdown());
        log.debug("Detected content type: {} for document: {}", tipoConteudo, documento.getTitulo());

        // 2. Split document into chapters
        DocumentSplitter splitter = splitterFactory.createSplitter(tipoConteudo, context.getLibrary());
        List<ChapterDTO> chapters = splitter.split(documento);
        result.setCapitulos(chapters);

        log.debug("Split document into {} chapters", chapters.size());

        // 3. Generate embeddings for each chapter
        for (ChapterDTO chapter : chapters) {

            // Basic embeddings
            List<DocumentEmbeddingDTO> embeddings =
                embeddingService.generateChapterEmbeddings(chapter, context);
            result.addEmbeddings(embeddings);

            // Q&A embeddings (if requested)
            if (options.isIncludeQA()) {
                List<DocumentEmbeddingDTO> qaEmbeddings =
                    embeddingService.generateQAEmbeddings(
                        chapter, context, options.getQaCount());
                result.addEmbeddings(qaEmbeddings);
            }

            // Summary embeddings (if requested and chapter is large enough)
            if (options.isIncludeSummary()) {
                int tokens = chapter.getConteudo() != null
                    ? RAGUtil.countTokens(chapter.getConteudo())
                    : 0;

                if (tokens > MIN_TOKENS_FOR_SUMMARY) {
                    List<DocumentEmbeddingDTO> summaryEmbeddings =
                        embeddingService.generateSummaryEmbeddings(
                            chapter,
                            context,
                            options.getMaxSummaryLength(),
                            options.getSummaryInstructions());
                    result.addEmbeddings(summaryEmbeddings);
                }
            }
        }

        log.info("Completed processing for document: {} - {} chapters, {} embeddings",
                documento.getTitulo(), result.getCapitulos().size(), result.getAllEmbeddings().size());

        return result;
    }

    /**
     * Result container for processing operations.
     */
    @lombok.Data
    public static class ProcessingResult {
        private DocumentoWithAssociationDTO documento;
        private List<ChapterDTO> capitulos = new ArrayList<>();
        private List<DocumentEmbeddingDTO> allEmbeddings = new ArrayList<>();

        public void addEmbeddings(List<DocumentEmbeddingDTO> embeddings) {
            this.allEmbeddings.addAll(embeddings);
        }

        public ProcessingStats getStats() {
            return new ProcessingStats(
                capitulos.size(),
                allEmbeddings.size(),
                documento.getConteudoMarkdown() != null
                    ? documento.getConteudoMarkdown().length()
                    : 0
            );
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ProcessingStats {
        private int chaptersCount;
        private int embeddingsCount;
        private int totalCharacters;
    }
}
```

---

## 📊 Configuração Completa

### application.properties

```properties
# ============ RAG Embedding Configuration ============

# Default models
rag.embedding.default-model=nomic-embed-text
rag.completion.default-model=qwen3-1.7b

# Processing configuration
rag.processamento.chunk-size-maximo=2000
rag.processamento.capitulo-size-padrao=8000

# ============ LLM Service Manager Configuration ============

# Strategy
llmservice.strategy=MODEL_BASED

# Retry configuration
llmservice.failover.max-retries=3
llmservice.failover.timeout-seconds=30

# Primary Provider (LM Studio)
llmservice.provider.name=LM_STUDIO
llmservice.provider.api.url=http://localhost:1234/v1
llmservice.provider.embedding.model=nomic-embed-text
llmservice.provider.embedding.dimension=768
llmservice.provider.llm.models=qwen3-1.7b

# Secondary Provider (Ollama - Optional)
llmservice.provider2.enabled=false
llmservice.provider2.name=OLLAMA
llmservice.provider2.api.url=http://localhost:11434
llmservice.provider2.embedding.model=mxbai-embed-large
llmservice.provider2.embedding.dimension=1024
llmservice.provider2.llm.models=mistral,codellama
```

---

## 🔄 Plano de Implementação FINAL

### Fase 1: Criar Nova Infraestrutura (Dias 1-3)

**Dia 1:**
- ✅ Criar `EmbeddingContext.java`
- ✅ Atualizar `EmbeddingRequest.java`
- ✅ Criar `ProcessingOptions.java`
- ✅ Criar `EmbeddingService.java` (interface)
- ✅ Testes unitários dos models

**Dia 2:**
- ✅ Criar `EmbeddingGenerationStrategy.java` (interface)
- ✅ Implementar `QueryEmbeddingStrategy.java` (com LLMServiceManager)
- ✅ Implementar `ChapterEmbeddingStrategy.java` (com LLMServiceManager)
- ✅ Testes unitários das strategies

**Dia 3:**
- ✅ Implementar `QAEmbeddingStrategy.java`
- ✅ Implementar `SummaryEmbeddingStrategy.java`
- ✅ Criar `EmbeddingServiceImpl.java`
- ✅ Criar `EmbeddingOrchestrator.java` (com retry logic)
- ✅ Testes de integração

---

### Fase 2: Migração de Consumidores (Dias 4-5)

**Dia 4:**
- ✅ Atualizar `SearchController` para usar `EmbeddingService`
- ✅ Atualizar `DocumentController` (se necessário)
- ✅ Testes de regressão

**Dia 5:**
- ✅ Atualizar `DocumentoService` para usar `EmbeddingOrchestrator`
- ✅ Testes de integração end-to-end
- ✅ Verificar que todos os testes passam

---

### Fase 3: Deprecation e Limpeza (Dias 6-7)

**Dia 6:**
- ✅ Marcar `EmbeddingProcessorInterface` como @Deprecated
- ✅ Marcar `EmbeddingProcessorImpl` como @Deprecated
- ✅ Marcar `AsyncSplitterService.fullProcessingAsync()` como @Deprecated
- ✅ Adicionar documentação de migração

**Dia 7:**
- ✅ Code review completo
- ✅ Ajustes baseados em feedback
- ✅ Atualizar README e documentação
- ✅ Merge para main

---

## ✅ Critérios de Aceite FINAL

### Must Have (Obrigatório)

- [ ] Todos os testes existentes continuam passando
- [ ] `EmbeddingContext` criado e funcional
- [ ] `EmbeddingService` usa `LLMServiceManager` corretamente
- [ ] `EmbeddingOrchestrator` tem retry logic (2 min, 2 retries)
- [ ] `SearchController` usa novo `EmbeddingService`
- [ ] `DocumentoService` usa novo `EmbeddingOrchestrator`
- [ ] Configuração de modelos padrão funciona (global + library override)
- [ ] Fail-fast quando modelo não encontrado
- [ ] Zero warnings de compilação
- [ ] Código antigo marcado como @Deprecated

### Should Have (Desejável)

- [ ] Testes unitários para todas as strategies
- [ ] Testes de integração para `EmbeddingOrchestrator`
- [ ] Teste de retry logic
- [ ] Teste de resolução de modelos (request → library → global)
- [ ] Atualização do README.md

### Could Have (Opcional)

- [ ] Mover código de sumarização para `service/summarization/`
- [ ] Diagrama UML da nova arquitetura
- [ ] Migration para adicionar campos de modelo em Library entity

---

## 🎯 Próximos Passos IMEDIATOS

1. ✅ **Aprovação final** deste documento
2. ✅ **Kick-off da implementação** - Fase 1, Dia 1
3. ✅ **Criação de branch**: `feat/embedding-service-refactoring`
4. ✅ **Começar implementação** seguindo o plano

---

## 📝 Notas Finais

### Compatibilidade com JSimpleLLM

A refatoração **NÃO afeta** a integração com JSimpleLLM:
- ✅ Continua usando `LLMServiceManager` para acesso ao pool
- ✅ Continua usando `getLLMServiceByRegisteredModel()` para roteamento
- ✅ Continua usando `Embeddings_Op` (QUERY, DOCUMENT, CLUSTERING)
- ✅ Continua usando `MapParam` para parâmetros
- ✅ Apenas **reorganiza** onde a lógica está, não como funciona

### Retrocompatibilidade

Durante as Fases 1-2:
- ✅ Código antigo continua funcionando
- ✅ Código novo é adicionado sem quebrar o antigo
- ✅ Zero downtime durante migração
- ✅ Possível rollback a qualquer momento

---

**Documento APROVADO para implementação.**
**Versão Final - 2025-01-25**
