# Proposta de Implementação - Novo Fluxo de Processamento de Documentos

**Data:** 2025-01-31
**Status:** ✅ APROVADO COM REVISÕES
**Versão:** 1.1 (Revisado)

---

## 📋 Índice

1. [Resumo Executivo](#resumo-executivo)
2. [Hierarquia Confirmada](#hierarquia-confirmada)
3. [Diagrama de Fluxo Detalhado](#diagrama-de-fluxo-detalhado)
4. [Arquitetura de Classes](#arquitetura-de-classes)
5. [Estimativa de Implementaão](#estimativa-de-implementacao)
6. [Plano de Implementação](#plano-de-implementacao)

---

## 📝 Revisões Aprovadas (v1.1)

### 1. Ordem de Execução Ajustada ✅

**Original:** Contextos criados APÓS split
**Revisado:** **Contextos criados ANTES do split**

**Razão:** Split precisa usar `LLMService.tokenCount(texto, "fast")` para contagem precisa de tokens

**Nova ordem:**
```
ETAPA 2.1: Criação de Contextos (PRIMEIRO)
    ↓
ETAPA 2.2: Split em Capítulos e Chunks (USA os contextos)
    ↓
ETAPA 2.3: Cálculo de Vetores de Embeddings
```

---

### 2. Contagem de Tokens via LLMService ✅

**Original:** Estimativa simples (palavras / 0.75)
**Revisado:** `LLMService.tokenCount(texto, "fast")`

**Uso:**
```java
// Sempre usar modelo "fast" para contagem
int tokens = llmContext.getLLMService().tokenCount(texto, "fast");
```

**Nota:** Valor "fast" pode ser reconfigurado posteriormente via propriedades

---

### 3. Batching Dinâmico com Contexto do Modelo ✅

**Original:**
- Batch size fixo: 5 textos
- Limite fixo: 4000 tokens

**Revisado:**
- **Batch size:** 10 textos (aumentado para reduzir ciclos)
- **Limite dinâmico:** `ModelEmbedding.getContextLength()` (pode ser 2048, 8192, etc.)

**Lógica para textos grandes:**
```java
int contextLength = modelEmbedding.getContextLength();
int textTokens = tokenCount(text);

if (textTokens > contextLength) {
    int excedente = textTokens - contextLength;
    double percentual = (excedente * 100.0) / textTokens;

    if (percentual > 2.0) {
        // Excedente > 2%: Gerar resumo via LLM
        String resumo = llmContext.generateCompletion("Resuma:", text);
        // Salvar resumo em metadados["resumo"]
        // Usar resumo para embedding
    } else {
        // Excedente <= 2%: Truncar texto
        text = text.substring(0, contextLength * 4); // ~4 chars/token
    }
}
```

---

### 4. Splitters Mantidos Como Estão ✅

**Decisão:** NÃO revisar splitters existentes

**Razão:** Implementações atuais adequadas para POC. Risco de particionamento não-ótimo é aceitável nesta fase.

**Splitters mantidos:**
- `SplitterGenerico.java`
- `SplitterNorma.java`
- `SplitterWiki.java`

---

## Resumo Executivo

### Objetivos da Nova Arquitetura

- ✅ **Simplicidade:** Fluxo sequencial, fácil de manter
- ✅ **Separação de responsabilidades:** Contextos dedicados para LLM e Embeddings
- ✅ **Tolerância a falhas:** Continua processando mesmo com erros individuais
- ✅ **Sem retry global:** Remove complexidade desnecessária
- ✅ **Batch processing:** Reduz chamadas LLM (10 textos por batch, dinâmico)

### Hierarquia Confirmada

```
Documento (1)
    ├─→ Capítulo (N) [entidade Chapter]
    │   ├─→ DocEmbedding tipo RESUMO (0 ou 1) [se capítulo > 2500 tokens]
    │   └─→ DocEmbedding tipo TRECHO (M) [chunks do capítulo]
```

### Exemplo Concreto - Documento de 15.000 tokens

**Configuração:**
- Documento: 15.000 tokens total
- 4 capítulos criados pelo splitter

**Distribuição:**
```
Documento: 15.000 tokens
├─ Capítulo 1: 3.750 tokens
│  ├─ DocEmb #1: RESUMO (~1024 tokens) ← capítulo > 2500
│  ├─ DocEmb #2: TRECHO chunk 1 (1875 tokens)
│  └─ DocEmb #3: TRECHO chunk 2 (1875 tokens)
│
├─ Capítulo 2: 3.750 tokens
│  ├─ DocEmb #4: RESUMO (~1024 tokens) ← capítulo > 2500
│  ├─ DocEmb #5: TRECHO chunk 1 (1875 tokens)
│  └─ DocEmb #6: TRECHO chunk 2 (1875 tokens)
│
├─ Capítulo 3: 1.200 tokens
│  └─ DocEmb #7: TRECHO (1200 tokens) ← sem resumo (< 2500)
│
└─ Capítulo 4: 6.350 tokens
   ├─ DocEmb #8: RESUMO (~1024 tokens) ← capítulo > 2500
   ├─ DocEmb #9: TRECHO chunk 1 (2117 tokens)
   ├─ DocEmb #10: TRECHO chunk 2 (2117 tokens)
   └─ DocEmb #11: TRECHO chunk 3 (2116 tokens)

RESULTADO NO BANCO:
- documento: 1 registro
- capitulo: 4 registros
- doc_embedding: 11 registros
  - 3 tipo RESUMO (capítulos 1, 2, 4)
  - 8 tipo TRECHO (chunks dos capítulos)
```

### Regras de Negócio Confirmadas

#### Capítulos
- **Tamanho ideal:** 2.000 - 4.200 tokens
- **Documentos pequenos:** < 4.200 tokens = 1 capítulo único

#### DocEmbeddings (Chunks)
- **Tamanho mínimo:** 512 tokens
- **Tamanho máximo:** 4.100 tokens
- **Tamanho ideal:** 2.048 tokens

#### Resumos
- **Threshold:** Só criar se capítulo > 2.500 tokens
- **Tamanho:** Máximo de 1.024 tokens
- **Tipo:** `TipoEmbedding.RESUMO`

#### Q&A (NÃO implementado nesta release)
- Funcionalidade adiada para versões futuras

---

## Diagrama de Fluxo Detalhado

### Visão Geral do Fluxo

```txt
┌─────────────────────────────────────────────────────────────────┐
│ FASE 1: Upload e Persistência do Documento (Síncrono)           │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │
│ DocumentoService.uploadFromText/Url/File()                      │
│   ├─→ Validação: Library exists                                 │
│   ├─→ Cálculo: Checksum (CRC64)                                 │
│   ├─→ Verificação: Duplicados (checksum + biblioteca_id)        │
│   ├─→ Persistência: INSERT documento (JPA)                      │
│   └─→ Retorno: DocumentoDTO                                     │
│                                                                 │
│ Duração: ~500ms | DB: 3 queries | LLM: 0 calls                  │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ FASE 2: Processamento Assíncrono (Iniciado manualmente)         │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │
│ DocumentoService.processDocumentAsync(documentId)               │
│   ├─→ Carrega: Documento + Library (DB)                         │
│   └─→ Delega: DocumentProcessingService.process()               │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ ETAPA 2.1: Criação de Contextos (1x por documento) ✅ PRIMEIRO! │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │
│ DocumentProcessingService.createContexts()                      │
│   │                                                             │
│   ├─→ LLMContext.create(library, llmServiceManager)             │
│   │   ├─→ LLMServiceManager.getBestCompletionModelName()        │
│   │   │   └─→ Busca modelo de completion no cache               │
│   │   │                                                         │
│   │   ├─→ LLMServiceManager.getServiceByModel(modelName)        │
│   │   │   └─→ Retorna: LLMService configurado                   │
│   │   │                                                         │
│   │   └─→ Retorna: LLMContext {                                 │
│   │           llmService: LLMService,                           │
│   │           model: Model,                                     │
│   │           modelName: String,                                │
│   │           params: MapParam                                  │
│   │       }                                                     │
│   │                                                             │
│   └─→ EmbeddingContext.create(library, llmServiceManager)       │
│       ├─→ LLMServiceManager.getLLMServiceByRegisteredModel()    │
│       │   └─→ Busca modelo de embedding no cache                │
│       │                                                         │
│       └─→ Retorna: EmbeddingContext {                           │
│               llmService: LLMService,                           │
│               modelEmbedding: ModelEmbedding,                   │
│               modelName: String,                                │
│               params: MapParam,                                 │
│               contextLength: Integer  ← getContextLength()      │
│           }                                                     │
│                                                                 │
│ Duração: ~100-200ms (lookup em cache)                           │
│ LLM: 0 calls (só leitura de cache)                              │
│ ⚠️ IMPORTANTE: Contextos criados ANTES do split!                │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ ETAPA 2.2: Split em Capítulos e Chunks (Sem vetores!)           │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │
│ DocumentProcessingService.splitAndPersist(llmContext)  ← USA!   │
│   │                                                             │
│   ├─→ DocumentRouter.detectContentType(markdown)                │
│   │   └─→ Retorna: TipoConteudo (GENERICO, NORMATIVO, WIKI)     │
│   │                                                             │
│   ├─→ SplitterFactory.createSplitter(tipo, library)             │
│   │   └─→ Retorna: SplitterGenerico/Norma/Wiki                  │
│   │                                                             │
│   ├─→ splitter.splitDocumento(documento)                        │
│   │   │                                                         │
│   │   ├─→ Divide em capítulos (~2000-4200 tokens)               │
│   │   │   └─→ List<ChapterDTO>                                  │
│   │   │                                                         │
│   │   └─→ Para cada capítulo:                                   │
│   │       │                                                     │
│   │       ├─→ Se capítulo > 2500 tokens:                        │
│   │       │   ├─→ Gera RESUMO via LLM (~1024 tokens)            │
│   │       │   │   └─→ DocEmbedding(tipo=RESUMO, vector=NULL)    │
│   │       │   │                                                 │
│   │       │   └─→ Divide em chunks (~2048 tokens)               │
│   │       │       └─→ N × DocEmbedding(tipo=TRECHO, vector=NULL)│
│   │       │                                                     │
│   │       └─→ Se capítulo ≤ 2500 tokens:                        │
│   │           └─→ 1 × DocEmbedding(tipo=TRECHO, vector=NULL)    │
│   │                                                             │
│   ├─→ chapterRepository.saveAll(chapters)                       │
│   │   └─→ Batch INSERT capítulos                                │
│   │                                                             │
│   └─→ embeddingRepository.saveAll(embeddings)  ← vector=NULL!   │
│       └─→ Batch INSERT embeddings (SEM vetores)                 │
│                                                                 │
│ Duração: ~5-15s (com LLM para resumos)                          │
│ DB: 2 batch INSERTs                                             │
│ LLM: ~4 completions (resumos) se todos caps > 2500 tokens       │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ ETAPA 2.3: Cálculo de Vetores de Embeddings (DINÂMICO)          │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │
│ DocumentProcessingService.calculateEmbeddings()                 │
│   │                                                             │
│   ├─→ Carrega: DocEmbeddings com vector=NULL (DB query)         │
│   │   └─→ List<DocumentEmbedding> (11 embeddings no exemplo)    │
│   │                                                             │
│   ├─→ Obtém: contextLength = embContext.getContextLength()      │
│   │   └─→ Exemplo: 8192 tokens (depende do modelo)              │
│   │                                                             │
│   ├─→ Agrupa em batches de até 10 textos (respeita contextLength)│
│   │   └─→ Batch 1: [emb1...emb10] (10 textos)                   │
│   │   └─→ Batch 2: [emb11] (1 texto)                            │
│   │                                                             │
│   └─→ Para cada batch:                                          │
│       │                                                         │
│       ├─→ Para cada embedding no batch:                         │
│       │   ├─→ tokens = llmContext.tokenCount(texto, "fast")     │
│       │   └─→ Se tokens > contextLength:                        │
│       │       ├─→ Se excedente > 2%: gera resumo via LLM        │
│       │       └─→ Se excedente <= 2%: trunca texto              │
│       │                                                         │
│       ├─→ String[] texts = batch.map(e -> processar texto)      │
│       │                                                         │
│       ├─→ float[][] vectors = embeddingContext.llmService       │
│       │       .embeddings(                                      │
│       │           Embeddings_Op.DOCUMENT,                       │
│       │           texts,  ← Array de textos processados         │
│       │           embeddingContext.params                       │
│       │       )                                                 │
│       │   └─→ Retorna: N vetores de embeddings                  │
│       │                                                         │
│       └─→ Para cada embedding + vector:                         │
│           │                                                     │
│           ├─→ try {                                             │
│           │       embeddingRepository.updateEmbeddingVector(    │
│           │           embedding.getId(),                        │
│           │           vector                                    │
│           │       )                                             │
│           │       log.debug("Updated embedding #{}", id)        │
│           │   }                                                 │
│           │                                                     │
│           └─→ catch (Exception e) {                             │
│                   log.error("Failed embedding #{}", id, e)      │
│                   e.printStackTrace()                           │
│                   // Continua com próximo embedding             │
│               }                                                 │
│                                                                 │
│ Duração: ~2-5s (2 batches × ~1-2s cada)                         │
│ LLM: 2 calls de embeddings (batch) + N resumos de textos grandes│
│ DB: 11 UPDATEs (serial, tolerante a falhas)                     │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│ ETAPA 2.4: Finalização                                          │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━   │
│ DocumentProcessingService.finalize()                            │
│   ├─→ Conta: embeddings processados vs total                    │
│   ├─→ Atualiza: documento.tokensTotal                           │
│   └─→ Retorna: ProcessingResult {                               │
│           documentId: Integer,                                  │
│           chaptersCount: 4,                                     │
│           embeddingsCount: 11,                                  │
│           embeddingsProcessed: 11,                              │
│           embeddingsFailed: 0,                                  │
│           duration: "12.5s"                                     │
│       }                                                         │
│                                                                 │
│ Duração: ~100ms                                                 │
└─────────────────────────────────────────────────────────────────┘

═══════════════════════════════════════════════════════════════════
TEMPO TOTAL ESTIMADO: ~8-16 segundos (melhoria de ~15-20%)
- Upload: 0.5s
- Contextos: 0.2s (ANTES do split!)
- Split + Resumos: 5-15s (depende de quantos resumos)
- Embeddings: 2-5s (com batching dinâmico)
- Finalização: 0.1s

CHAMADAS LLM TOTAL: ~6-8 calls (vs ~7-9 antes)
- Resumos: 3-4 completions (se caps > 2500 tokens)
- Embeddings: 2 batch calls (11 textos / batches de até 10)
- Textos grandes: 0-2 resumos extras (se texto > contextLength com +2%)

CHAMADAS DB TOTAL: ~18 queries
- Upload: 3 (validation + insert)
- Split: 2 (batch inserts)
- Embeddings: 1 (select NULL vectors) + 11 (updates)
- Finalização: 1 (update documento)

MELHORIAS COM REVISÃO:
- ✅ Batches maiores (10 vs 5) = menos ciclos
- ✅ Contagem precisa de tokens via tokenCount("fast")
- ✅ Contexto dinâmico do modelo respeitado
- ✅ Textos grandes tratados inteligentemente (resumo ou truncamento)
═══════════════════════════════════════════════════════════════════
```

---

## Arquitetura de Classes

### Novos Componentes

#### 1. LLMContext.java

**Localização:** `src/main/java/bor/tools/simplerag/service/processing/context/LLMContext.java`

**Responsabilidade:** Encapsula LLMService validado + modelo + parâmetros para operações de completion (resumos, Q&A)

**Campos:**

```java
private LLMService llmService;
private Model model;
private String modelName;  // Alias ou nome do modelo
private MapParam params;
```

**Métodos Principais:**

```java
// Factory method - cria contexto validado
public static LLMContext create(LibraryDTO library, LLMServiceManager manager)

// Gera completion usando contexto
public String generateCompletion(String systemPrompt, String userPrompt)

// Contagem precisa de tokens (REVISÃO v1.1)
public int tokenCount(String text, String model) throws Exception

// Valida se contexto está pronto
public boolean isValid()
```

**Uso:**

```java
// Criar 1x por documento
LLMContext llmContext = LLMContext.create(library, llmServiceManager);

// Reutilizar para todos os resumos
String summary1 = llmContext.generateCompletion(systemPrompt, chapter1Text);
String summary2 = llmContext.generateCompletion(systemPrompt, chapter2Text);
// ...
```

---

#### 2. EmbeddingContext.java

**Localização:** `src/main/java/bor/tools/simplerag/service/processing/context/EmbeddingContext.java`

**Responsabilidade:** Encapsula LLMService validado + modelo de embedding + parâmetros para geração de vetores

**Campos:**

```java
private LLMService llmService;
private ModelEmbedding modelEmbedding;  // NÃO usar Model genérico
private String modelName;  // Alias ou nome do modelo
private MapParam params;
```

**Métodos Principais:**

```java
// Factory method - cria contexto validado
public static EmbeddingContext create(LibraryDTO library, LLMServiceManager manager)

// Gera embedding individual
public float[] generateEmbedding(String text, Embeddings_Op operation)

// Gera batch de embeddings (ATÉ 10 TEXTOS, respeitando contextLength)
public float[][] generateEmbeddingsBatch(String[] texts, Embeddings_Op operation)

// Valida se contexto está pronto
public boolean isValid()

// Retorna dimensão do modelo
public Integer getEmbeddingDimension()

// Retorna limite dinâmico do contexto (REVISÃO v1.1)
public Integer getContextLength()
```

**Uso:**

```java
// Criar 1x por documento
EmbeddingContext embContext = EmbeddingContext.create(library, llmServiceManager);

// Obter limite dinâmico do modelo
int contextLength = embContext.getContextLength(); // Ex: 8192

// Reutilizar para todos os embeddings (batch de até 10 textos)
String[] batch1 = {text1, text2, text3, text4, text5,
                   text6, text7, text8, text9, text10};
float[][] vectors1 = embContext.generateEmbeddingsBatch(batch1, Embeddings_Op.DOCUMENT);

String[] batch2 = {text11};
float[][] vectors2 = embContext.generateEmbeddingsBatch(batch2, Embeddings_Op.DOCUMENT);
```

---

#### 3. DocumentProcessingService.java

**Localização:** `src/main/java/bor/tools/simplerag/service/processing/DocumentProcessingService.java`

**Responsabilidade:** Novo orquestrador sequencial que substitui `EmbeddingOrchestrator`

**Dependências:**

```java
private final DocumentRouter documentRouter;
private final SplitterFactory splitterFactory;
private final ChapterRepository chapterRepository;
private final DocEmbeddingJdbcRepository embeddingRepository;
private final LLMServiceManager llmServiceManager;
private final LibraryService libraryService;
```

**Constantes (ATUALIZADAS v1.1):**

```java
private static final int BATCH_SIZE = 10;  // Textos por batch (REVISADO: era 5)
private static final String TOKEN_MODEL = "fast";  // Modelo para tokenCount()
private static final int SUMMARY_THRESHOLD_TOKENS = 2500;  // Threshold para resumo
private static final int SUMMARY_MAX_TOKENS = 1024;  // Tamanho máximo do resumo
private static final double OVERSIZE_THRESHOLD_PERCENT = 2.0;  // % para resumir vs truncar
```

**Métodos Principais:**

```java
// Método público assíncrono - ponto de entrada
@Async
public CompletableFuture<ProcessingResult> processDocument(
    Documento documento,
    LibraryDTO library
)

// ETAPA 2.1: Split e persistência (vector=NULL)
@Transactional
protected SplitResult splitAndPersist(Documento documento, LibraryDTO library)

// Helper: cria embeddings para 1 capítulo
private List<DocumentEmbedding> createChapterEmbeddings(
    Chapter chapter,
    ChapterDTO chapterDTO,
    Documento documento,
    LibraryDTO library
)

// ETAPA 2.3: Calcula vetores e atualiza DB
private int calculateAndUpdateEmbeddings(
    List<DocumentEmbedding> embeddings,
    EmbeddingContext context
)

// Helper: estima tokens
private int estimateTokens(String text)
```

**Fluxo Interno (REVISADO v1.1):**

```java
processDocument()
    ├─→ LLMContext.create()          ← PRIMEIRO! (REVISADO)
    ├─→ EmbeddingContext.create()    ← PRIMEIRO! (REVISADO)
    │
    ├─→ splitAndPersist(llmContext)  ← USA llmContext para tokenCount()
    │   ├─→ DocumentRouter.detectContentType()
    │   ├─→ SplitterFactory.createSplitter()
    │   ├─→ splitter.splitDocumento()
    │   ├─→ createChapterEmbeddings() [para cada cap]
    │   │   └─→ USA llmContext.tokenCount(texto, "fast")
    │   ├─→ chapterRepository.saveAll()
    │   └─→ embeddingRepository.saveAll() [vector=NULL]
    │
    ├─→ calculateAndUpdateEmbeddings(embContext, llmContext)
    │   ├─→ Obtém contextLength = embContext.getContextLength()
    │   ├─→ Agrupa em batches (até 10 textos, respeita contextLength)
    │   └─→ Para cada batch:
    │       ├─→ handleOversizedText() [tokenCount + resumo/trunca se necessário]
    │       ├─→ embContext.generateEmbeddingsBatch()
    │       └─→ embeddingRepository.updateEmbeddingVector() [cada um]
    │
    └─→ Retorna ProcessingResult
```

---

### Componentes a Modificar

#### DocumentoService.java

**Mudanças:**

1. **Adicionar dependência:**

```java
private final DocumentProcessingService documentProcessingService;
```

2. **Adicionar novo método:**

```java
/**
 * Process document asynchronously using new sequential flow.
 *
 * @param documentId Document ID
 * @return CompletableFuture with processing result
 */
@Async
public CompletableFuture<DocumentProcessingService.ProcessingResult>
    processDocumentAsync(Integer documentId) {

    log.info("Starting async processing for document ID: {}", documentId);

    // Load document
    Documento documento = documentoRepository.findById(documentId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Document not found: " + documentId));

    // Load library
    Optional<Library> libraryOpt = libraryService.findById(documento.getBibliotecaId());
    if (libraryOpt.isEmpty()) {
        throw new IllegalArgumentException(
            "Library not found: " + documento.getBibliotecaId());
    }

    LibraryDTO biblioteca = LibraryDTO.from(libraryOpt.get());

    // Delegate to new processing service
    return documentProcessingService.processDocument(documento, biblioteca);
}
```

3. **Deprecar método antigo:**

```java
@Deprecated(since = "0.0.3", forRemoval = true)
public CompletableFuture<ProcessingStatus> processDocumentAsyncOld(
        Integer documentId,
        boolean includeQA,
        boolean includeSummary) {
    // Código antigo mantido por compatibilidade
    // Será removido em versão futura
}
```

---

#### DocEmbeddingJdbcRepository.java

**Mudanças:**

1. **Adicionar método batch saveAll():**

```java
/**
 * Batch insert embeddings with NULL vectors.
 *
 * Optimized for initial persistence - vectors calculated later.
 *
 * @param embeddings List of embeddings (vector can be NULL)
 * @return List of generated IDs
 * @throws SQLException if batch insert fails
 */
public List<Integer> saveAll(List<DocumentEmbedding> embeddings) throws SQLException {
    if (embeddings == null || embeddings.isEmpty()) {
        return Collections.emptyList();
    }

    String sql = "INSERT INTO doc_embedding " +
                "(biblioteca_id, documento_id, capitulo_id, texto, " +
                "embedding_vector, tipo_embedding, metadados, order_chapter, " +
                "created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?::vector, ?::tipo_embedding_enum, " +
                "?::jsonb, ?, ?, ?) " +
                "RETURNING id";

    List<Integer> generatedIds = new ArrayList<>();

    try (PreparedStatement ps = connection.prepareStatement(sql)) {
        for (DocumentEmbedding emb : embeddings) {
            ps.setInt(1, emb.getLibraryId());
            ps.setObject(2, emb.getDocumentoId());
            ps.setObject(3, emb.getChapterId());
            ps.setString(4, emb.getTexto());

            // Vector can be NULL
            if (emb.getEmbeddingVector() != null) {
                ps.setString(5, vectorToString(emb.getEmbeddingVector()));
            } else {
                ps.setNull(5, java.sql.Types.VARCHAR);
            }

            ps.setString(6, emb.getTipoEmbedding().name());
            ps.setString(7, metadataToJson(emb.getMetadados()));
            ps.setObject(8, emb.getOrderChapter());
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));

            ps.addBatch();
        }

        // Execute batch
        ps.executeBatch();

        // Retrieve generated IDs
        try (ResultSet rs = ps.getGeneratedKeys()) {
            while (rs.next()) {
                generatedIds.add(rs.getInt(1));
            }
        }
    }

    log.debug("Batch inserted {} embeddings", generatedIds.size());
    return generatedIds;
}
```

2. **Método updateEmbeddingVector() já existe?**

Se NÃO existir, adicionar:

```java
/**
 * Update embedding vector for existing embedding.
 *
 * Used after batch insert with NULL vectors.
 *
 * @param embeddingId Embedding ID
 * @param vector Embedding vector
 * @throws SQLException if update fails
 */
public void updateEmbeddingVector(Integer embeddingId, float[] vector)
        throws SQLException {

    if (embeddingId == null || vector == null) {
        throw new IllegalArgumentException("ID and vector cannot be null");
    }

    String sql = "UPDATE doc_embedding SET embedding_vector = ?::vector, " +
                "updated_at = ? WHERE id = ?";

    try (PreparedStatement ps = connection.prepareStatement(sql)) {
        ps.setString(1, vectorToString(vector));
        ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
        ps.setInt(3, embeddingId);

        int rows = ps.executeUpdate();
        if (rows == 0) {
            throw new SQLException("Embedding not found: " + embeddingId);
        }
    }
}
```

---

### Componentes a Deprecar

#### 1. EmbeddingOrchestrator.java

```java
@Deprecated(since = "0.0.3", forRemoval = true)
@Service
public class EmbeddingOrchestrator {
    // Manter código existente
    // Adicionar mensagem de depreciação nos logs

    public EmbeddingOrchestrator(...) {
        log.warn("EmbeddingOrchestrator is deprecated. " +
                 "Use DocumentProcessingService instead.");
    }
}
```

#### 2. AsyncSplitterService.java

```java
// Já está @Deprecated(since = "0.0.2", forRemoval = true)
// Nenhuma mudança necessária
```

#### 3. EmbeddingProcessorImpl.java

```java
// Já está @Deprecated(since = "0.0.2", forRemoval = true)
// Nenhuma mudança necessária
```

---

## Estimativa de Implementacao

### Breakdown Detalhado

| # | Tarefa | Complexidade | Tempo | Pré-requisitos |
|---|--------|--------------|-------|----------------|
| 1 | Criar package `service.processing.context` | Trivial | 5min | - |
| 2 | Implementar `LLMContext.java` | Baixa | 1-2h | - |
| 3 | Implementar `EmbeddingContext.java` | Baixa | 1-2h | - |
| 4 | Testes unitários dos contextos | Média | 2-3h | 2, 3 |
| 5 | Adicionar `saveAll()` em `DocEmbeddingJdbcRepository` | Baixa | 1h | - |
| 6 | Verificar/Adicionar `updateEmbeddingVector()` | Trivial | 30min | - |
| 7 | Criar package `service.processing` | Trivial | 5min | - |
| 8 | Implementar `DocumentProcessingService` (estrutura) | Média | 2h | 2, 3, 5, 6 |
| 9 | Implementar `splitAndPersist()` | Média | 2-3h | 8 |
| 10 | Implementar `createChapterEmbeddings()` | Média | 1-2h | 9 |
| 11 | Implementar geração de resumos (LLM) | Média | 2-3h | 2, 10 |
| 12 | Implementar `calculateAndUpdateEmbeddings()` | Média | 2-3h | 3, 8 |
| 13 | Modificar `DocumentoService.processDocumentAsync()` | Baixa | 1h | 8 |
| 14 | Deprecar `EmbeddingOrchestrator` | Trivial | 30min | - |
| 15 | Testes de integração (fluxo completo) | Alta | 3-4h | 8-13 |
| 16 | Documentação JavaDoc | Baixa | 1-2h | Todos |
| 17 | Atualização de README/docs | Baixa | 1h | Todos |
| **TOTAL** | | | **22-32h** | |

### Distribuição por Fase

#### Fase 1: Fundação (6-9h)
- ✅ Criar contextos (LLMContext + EmbeddingContext)
- ✅ Adicionar métodos batch no repository
- ✅ Testes unitários dos contextos

**Entregas:**
- `LLMContext.java` funcional
- `EmbeddingContext.java` funcional
- `DocEmbeddingJdbcRepository.saveAll()` funcional
- Testes passando

---

#### Fase 2: Processamento Core (10-14h)
- ✅ Criar `DocumentProcessingService`
- ✅ Implementar `splitAndPersist()`
- ✅ Implementar `calculateAndUpdateEmbeddings()`
- ✅ Testes de integração

**Entregas:**
- Fluxo completo funcional (sem resumos)
- Documento processado do início ao fim
- Embeddings calculados e persistidos

---

#### Fase 3: Resumos e Finalização (6-9h)
- ✅ Adicionar geração de resumos via LLM
- ✅ Modificar `DocumentoService`
- ✅ Deprecar componentes antigos
- ✅ Documentação

**Entregas:**
- Sistema completo com resumos
- Documentação atualizada
- Componentes antigos deprecados

---

## Plano de Implementacao

### Cronograma Sugerido

#### Semana 1 - Fundação

**Dia 1: Contextos**
- [ ] Criar `LLMContext.java` (2h)
- [ ] Criar `EmbeddingContext.java` (2h)
- [ ] Testes básicos (2h)

**Dia 2: Repository**
- [ ] Adicionar `saveAll()` em Repository (1h)
- [ ] Verificar `updateEmbeddingVector()` (30min)
- [ ] Testes de repository (2h)
- [ ] Integração contextos + repository (1h)

---

#### Semana 2 - Processamento Core

**Dia 3: Service Base**
- [ ] Criar `DocumentProcessingService` (estrutura) (2h)
- [ ] Implementar `splitAndPersist()` (3h)
- [ ] Testes de splitting (2h)

**Dia 4: Embeddings**
- [ ] Implementar `calculateAndUpdateEmbeddings()` (3h)
- [ ] Implementar batch processing (2h)
- [ ] Testes de embeddings (2h)

**Dia 5: Integração**
- [ ] Teste end-to-end (documento pequeno) (2h)
- [ ] Teste end-to-end (documento grande) (2h)
- [ ] Correções e ajustes (2h)

---

#### Semana 3 - Resumos e Finalização

**Dia 6: Resumos**
- [ ] Implementar geração de resumos (3h)
- [ ] Integrar resumos no fluxo (2h)
- [ ] Testes com resumos (2h)

**Dia 7: Integração Final**
- [ ] Modificar `DocumentoService` (1h)
- [ ] Deprecar componentes antigos (30min)
- [ ] Testes de regressão (2h)
- [ ] Documentação JavaDoc (2h)

**Dia 8: Documentação**
- [ ] Atualizar README (1h)
- [ ] Atualizar docs técnicos (1h)
- [ ] Code review (2h)
- [ ] Deploy/Release (1h)

---

### Estratégia de Testes

#### Testes Unitários (por componente)

**LLMContext:**
- ✅ Criação com modelo válido
- ✅ Criação sem modelo disponível (exception)
- ✅ Geração de completion
- ✅ Validação de contexto

**EmbeddingContext:**
- ✅ Criação com modelo válido
- ✅ Criação sem modelo disponível (exception)
- ✅ Geração de embedding individual
- ✅ Geração de batch
- ✅ Validação de contexto

**DocumentProcessingService:**
- ✅ Split de documento pequeno (1 capítulo)
- ✅ Split de documento grande (N capítulos)
- ✅ Criação de resumos (cap > 2500 tokens)
- ✅ Sem resumos (cap < 2500 tokens)
- ✅ Batch de embeddings
- ✅ Tolerância a falhas (embedding individual falha)

---

#### Testes de Integração

**Fluxo Completo:**

```java
@Test
void testProcessDocument_SmallDocument() {
    // Documento: 3000 tokens (1 capítulo, sem resumo)
    // Esperado:
    // - 1 capítulo
    // - 2 DocEmbeddings tipo TRECHO
    // - 0 DocEmbeddings tipo RESUMO
}

@Test
void testProcessDocument_LargeDocument() {
    // Documento: 15000 tokens (4 capítulos)
    // Esperado:
    // - 4 capítulos
    // - 3 DocEmbeddings tipo RESUMO (caps 1,2,4 > 2500)
    // - 8 DocEmbeddings tipo TRECHO (chunks)
    // - Total: 11 embeddings
}

@Test
void testProcessDocument_WithEmbeddingFailure() {
    // Simular falha em 1 embedding
    // Verificar que outros embeddings são processados
    // Verificar que resultado reporta falhas
}

@Test
void testProcessDocument_WithSummaryGeneration() {
    // Documento com capítulo > 2500 tokens
    // Verificar geração de resumo via LLM
    // Verificar persistência do resumo
    // Verificar cálculo de embedding do resumo
}
```

---

## Riscos e Mitigações

### Riscos Identificados

| Risco | Probabilidade | Impacto | Mitigação |
|-------|---------------|---------|-----------|
| Splitters existentes não retornam chunks adequados | Média | Alto | Revisar splitters antes de implementar; ajustar se necessário |
| Batch embeddings falha com textos grandes | Baixa | Médio | Validar tamanho total antes de chamar LLM; limitar a 4000 tokens |
| LLMService.embeddings(String[]) não existe | Baixa | Alto | Verificar existência do método antes de iniciar; criar se necessário |
| Geração de resumos muito lenta | Média | Médio | Implementar timeout; considerar resumos opcionais |
| Falhas em cascade (1 erro para tudo) | Baixa | Alto | Implementado try-catch individual; processamento continua |
| Repository batch insert não retorna IDs | Baixa | Alto | Testar RETURNING clause; fallback para insert individual se necessário |

---

## Métricas de Sucesso

### Performance

- ✅ Documento de 15k tokens processado em **< 20 segundos**
- ✅ Redução de **80%+** em chamadas LLM vs implementação atual
- ✅ Batch embeddings reduz tempo em **70%+** vs serial

### Qualidade

- ✅ **100%** de cobertura de testes nos novos componentes
- ✅ **0** warnings no SonarQube
- ✅ Documentação JavaDoc completa

### Funcionalidade

- ✅ Resumos gerados para capítulos > 2500 tokens
- ✅ Chunks persistidos corretamente (512-4100 tokens)
- ✅ Embeddings calculados em batch (5 textos/call)
- ✅ Falhas individuais não param o processamento

---

## Próximos Passos Após Aprovação

1. **Criar branch de feature:**

   ```bash
   git checkout -b feature/new-processing-flow
   ```

2. **Implementar Fase 1** (6-9h):
   - Contextos + Repository + Testes
   - Commit incremental após cada componente

3. **Implementar Fase 2** (10-14h):
   - DocumentProcessingService
   - Testes de integração
   - Commit após cada método funcional

4. **Implementar Fase 3** (6-9h):
   - Resumos + Integração final
   - Deprecação + Documentação
   - Commit final

5. **Code Review + Merge:**
   - Pull Request com descrição detalhada
   - Review por time
   - Merge para main após aprovação

---

## Anexos

### A. Diagrama de Classes UML

```
┌─────────────────────────────────────────┐
│         DocumentoService                │
│ ─────────────────────────────────────── │
│ + uploadFromText()                      │
│ + processDocumentAsync()   [MODIFIED]   │
│ + processDocumentAsyncOld() [DEPRECATED]│
└────────────┬────────────────────────────┘
             │ delega
             ↓
┌─────────────────────────────────────────┐
│    DocumentProcessingService   [NEW]    │
│ ─────────────────────────────────────── │
│ + processDocument()                     │
│ # splitAndPersist()                     │
│ - createChapterEmbeddings()             │
│ - calculateAndUpdateEmbeddings()        │
└───┬─────────────────────┬───────────────┘
    │ usa                 │ usa
    ↓                     ↓
┌──────────────┐    ┌──────────────────┐
│  LLMContext  │    │ EmbeddingContext │
│    [NEW]     │    │      [NEW]       │
│ ──────────── │    │ ──────────────── │
│ - llmService │    │ - llmService     │
│ - model      │    │ - modelEmbedding │
│ - modelName  │    │ - modelName      │
│ - params     │    │ - params         │
│              │    │                  │
│ + create()   │    │ + create()       │
│ + generate() │    │ + generateBatch()│
└──────────────┘    └──────────────────┘
```

### B. Exemplo de Uso Completo

```java
// 1. Upload documento
DocumentoDTO doc = documentoService.uploadFromText(
    "Manual do Usuário",
    markdownContent,
    libraryId,
    null
);

// 2. Processar documento (assíncrono)
CompletableFuture<ProcessingResult> future =
    documentoService.processDocumentAsync(doc.getId());

// 3. Aguardar resultado
ProcessingResult result = future.get();

// 4. Verificar resultado
System.out.println("Capítulos: " + result.getChaptersCount());
System.out.println("Embeddings: " + result.getEmbeddingsCount());
System.out.println("Processados: " + result.getEmbeddingsProcessed());
System.out.println("Falhas: " + result.getEmbeddingsFailed());
System.out.println("Tempo: " + result.getDuration());
```

---

**Documento Preparado Por:** Claude Code
**Data de Criação:** 2025-01-31
**Data de Revisão:** 2025-01-31
**Versão:** 1.1 (Revisado)
**Status:** ✅ APROVADO COM REVISÕES (pelo usuário)
