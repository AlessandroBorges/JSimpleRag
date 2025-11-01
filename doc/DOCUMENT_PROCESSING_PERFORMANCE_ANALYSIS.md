# Análise de Desempenho - Processamento de Documentos e Geração de Embeddings

**Data:** 2025-01-31
**Status:** Análise Preliminar - Aguardando Proposta de Nova Arquitetura

---

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Fluxo Atual de Processamento](#fluxo-atual-de-processamento)
3. [Gargalos Identificados](#gargalos-identificados)
4. [Análise de Impacto](#análise-de-impacto)
5. [Recomendações de Otimização](#recomendações-de-otimização)

---

## Visão Geral

### Problema Identificado

Testes iniciais mostraram **desempenho muito baixo** no processamento de documentos, possivelmente devido a:

- ✗ Validações excessivas de LLMServices e modelos
- ✗ Revalidações repetidas dos mesmos recursos
- ✗ Ausência de cache para informações estáticas
- ✗ Processamento serial sem batching
- ✗ Persistência não otimizada (embeddings salvos um por um)

### Componentes Envolvidos

```
DocumentoService (Controller)
    ↓
EmbeddingOrchestrator (Coordenação)
    ↓
EmbeddingService (Geração de Embeddings)
    ↓
EmbeddingProcessorImpl (Implementação DEPRECATED)
    ↓
LLMServiceManager (Pool de LLM Providers)
    ↓
LLMService (Chamadas aos providers: OpenAI, Ollama, LMStudio, etc.)
```

---

## Fluxo Atual de Processamento

### Fase 1: Upload e Preparação (Síncrono)

```
DocumentoService.uploadFromText/Url/File()
    ├─→ Validação: Library exists
    ├─→ Cálculo: Checksum (CRC64)
    ├─→ Verificação: Duplicados (checksum + biblioteca_id)
    ├─→ Persistência: Documento (JPA)
    └─→ Retorno: DocumentoDTO
```

**Duração:** ~100-500ms
**Chamadas LLM:** 0
**Chamadas DB:** 2-3 (SELECT + INSERT)

---

### Fase 2: Processamento Assíncrono (Iniciado manualmente)

```
DocumentoService.processDocumentAsync(documentId)
    ├─→ Carrega: Documento (DB SELECT)
    ├─→ Carrega: Library (DB SELECT)
    ├─→ Cria: EmbeddingContext.fromLibrary()
    │   └─→ ⚠️ VALIDAÇÃO 1: LLMService disponível
    ├─→ Cria: ProcessingOptions
    └─→ Delega: EmbeddingOrchestrator.processDocumentFull()
```

#### Orquestração com Retry Logic

```
EmbeddingOrchestrator.processDocumentFull()
    └─→ CompletableFuture.supplyAsync(() -> {
            while (attempt <= MAX_RETRIES) {  // 2 retries = 3 tentativas
                try {
                    return executeProcessing(...)
                } catch (Exception e) {
                    Thread.sleep(120_000);  // ⚠️ ESPERA 2 MINUTOS!
                }
            }
        })
```

**⚠️ PROBLEMA:** Se falha no último capítulo, refaz TUDO após 2 minutos!

---

### Fase 3: Splitting e Preparação

```
EmbeddingOrchestrator.executeProcessing()
    ├─→ Detecção: DocumentRouter.detectContentType()
    ├─→ Criação: SplitterFactory.createSplitter(tipo, library)
    │   └─→ ⚠️ VALIDAÇÃO 2: Configuração de LLM para splitter
    ├─→ Splitting: splitter.splitDocumento()
    │   └─→ Retorna: List<ChapterDTO>
    └─→ Para cada capítulo: [FASE 4]
```

**Duração:** ~500-2000ms (depende do tamanho do documento)
**Chamadas LLM:** 0-2 (validações)

---

### Fase 4: Geração de Embeddings (Loop por Capítulo)

```
Para CADA ChapterDTO:
    │
    ├─→ [A] Embeddings Básicos (Sempre gerados)
    │   │
    │   └─→ EmbeddingService.generateChapterEmbeddings(chapter, context)
    │       └─→ EmbeddingProcessorImpl.createChapterEmbeddings()
    │           │
    │           ├─→ createAutoEmbeddings()
    │           │   ├─→ Se pequeno: createFullTextEmbedding()
    │           │   ├─→ Se médio: createTextOnlyEmbedding()
    │           │   └─→ Se grande: createSplitTextEmbeddings()
    │           │       └─→ Para cada chunk:
    │           │           └─→ createEmbeddingFromText()
    │           │               └─→ createEmbeddings(op, text, lib)
    │           │                   ├─→ ⚠️ VALIDAÇÃO 3: llmService != null
    │           │                   └─→ llmService.embeddings(op, text, params)
    │           │                       └─→ LLMServiceManager.embeddings()
    │           │                           └─→ ⚠️ VALIDAÇÃO 4: findServiceByModel()
    │           │                               └─→ serviceSupportsModel()
    │           │                                   └─→ service.getInstalledModels()  // ❌ CHAMADA HTTP!
    │           │
    │           └─→ ⚠️ PROBLEMA: Validação repetida para CADA embedding!
    │
    ├─→ [B] Q&A Embeddings (Se includeQA=true)
    │   │
    │   └─→ EmbeddingService.generateQAEmbeddings(chapter, context, qaCount)
    │       └─→ EmbeddingProcessorImpl.createQAEmbeddings()
    │           │
    │           ├─→ documentSummarizer.generateQA(content, k)
    │           │   └─→ ❌ LLM CALL (completion) - Gera k pares Q&A
    │           │
    │           └─→ Para cada par Q&A (3 por padrão):
    │               │
    │               ├─→ Combina: "Pergunta: ... \n\nResposta: ..."
    │               └─→ createEmbeddingFromText(combinedText, ...)
    │                   └─→ ❌ LLM CALL (embedding)
    │
    │   ⚠️ PROBLEMA: 1 completion + 3 embeddings = 4 chamadas LLM por capítulo!
    │
    └─→ [C] Summary Embeddings (Se includeSummary=true E tokens > 500)
        │
        └─→ EmbeddingService.generateSummaryEmbeddings(chapter, context, ...)
            └─→ EmbeddingProcessorImpl.createSummaryEmbeddings()
                │
                ├─→ documentSummarizer.summarize(content, instructions, maxLength)
                │   └─→ ❌ LLM CALL (completion) - Gera sumário
                │
                ├─→ createEmbeddingFromText(summary, ...)
                │   └─→ ❌ LLM CALL (embedding)
                │
                └─→ Se texto muito longo (> 4000 chars):
                    └─→ createHybridSummaryEmbedding()
                        └─→ ❌ LLM CALL (embedding)

                ⚠️ PROBLEMA: 1 completion + 2 embeddings = 3 chamadas LLM por capítulo!
```

**Duração por capítulo:** ~3-8 segundos
**Chamadas LLM por capítulo:**
- Básico: 5-10 embeddings (chunks)
- Q&A: 1 completion + 3 embeddings = 4 chamadas
- Summary: 1 completion + 1-2 embeddings = 2-3 chamadas
- **TOTAL por capítulo: ~11-17 chamadas LLM**

**Para 10 capítulos: ~110-170 chamadas LLM!**

---

### Fase 5: Persistência (Síncrono)

```
DocumentoService.persistProcessingResult(result, documento)
    │
    ├─→ [A] Salvar Capítulos (Batch com JPA)
    │   │
    │   └─→ List<Chapter> chapters = result.getCapitulos()
    │           .map(dto -> toEntity(dto, documento))
    │
    │       ✅ chapterRepository.saveAll(chapters)
    │           └─→ JPA batch insert (eficiente)
    │
    ├─→ [B] Mapear IDs de Capítulos
    │   │
    │   └─→ Map<String, Integer> chapterIdMap
    │           ├─→ Key: dto.getTitulo()
    │           └─→ Value: savedChapter.getId()
    │
    ├─→ [C] Salvar Embeddings (Serial, um por um!)
    │   │
    │   └─→ List<DocumentEmbedding> embeddings = result.getAllEmbeddings()
    │           .map(dto -> toEntity(dto, documento, chapterIdMap))
    │
    │       ❌ for (DocumentEmbedding emb : embeddings) {
    │           try {
    │               Integer id = embeddingRepository.save(emb);  // INSERT + COMMIT
    │               savedIds.add(id);
    │           } catch (SQLException e) {
    │               throw new RuntimeException("Embedding save failed", e);
    │           }
    │       }
    │
    │       ⚠️ PROBLEMA: 100 embeddings = 100 INSERTs individuais!
    │                   Cada INSERT espera confirmação do DB
    │                   Latência de rede multiplicada!
    │
    └─→ [D] Atualizar Documento
        │
        └─→ documento.setTokensTotal(calculateTotalTokens(result))
            documentoRepository.save(documento)
```

**Duração:** ~2-5 segundos (depende da quantidade de embeddings)
**Chamadas DB:**
- 1 batch INSERT (capítulos) → ~100ms
- 100 INSERTs individuais (embeddings) → ~2-4s
- 1 UPDATE (documento) → ~50ms

---

### Fluxo Completo Simplificado

```
┌─────────────────────────────────────────────────────────────────────┐
│ FASE 1: Upload (Síncrono)                                           │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
│ uploadFromText() → checksum → duplicate check → INSERT documento    │
│ Duração: ~500ms | DB Calls: 3 | LLM Calls: 0                       │
└─────────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│ FASE 2: Validação Inicial (Assíncrono)                              │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
│ processDocumentAsync() → load documento + library → create context  │
│ Duração: ~200-500ms | DB Calls: 2 | LLM Calls: 1-2 (validações)   │
└─────────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│ FASE 3: Splitting (Assíncrono)                                      │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
│ detectContentType() → createSplitter() → splitDocumento()           │
│ Duração: ~1-2s | DB Calls: 0 | LLM Calls: 0-2 (validações)         │
│ Resultado: List<ChapterDTO> (10 capítulos)                          │
└─────────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│ FASE 4: Geração de Embeddings (Loop - Assíncrono)                   │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
│ Para CADA capítulo (10x):                                            │
│   ├─ Embeddings básicos:    5-10 chunks × embedding call            │
│   ├─ Q&A (se enabled):      1 completion + 3 embedding calls        │
│   └─ Summary (se enabled):  1 completion + 2 embedding calls        │
│                                                                       │
│ Duração por capítulo: ~3-8s                                          │
│ Duração total (10 caps): ~30-80s                                     │
│ LLM Calls por capítulo: ~11-17                                       │
│ LLM Calls total: ~110-170 ❌                                         │
└─────────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│ FASE 5: Persistência (Síncrono)                                     │
│ ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ │
│ saveAll(chapters) [batch] → save(embeddings) [serial] → update doc  │
│ Duração: ~2-5s | DB Calls: 102 (1 batch + 100 individual + 1)      │
└─────────────────────────────────────────────────────────────────────┘
                              ↓
                    ✅ DOCUMENTO PROCESSADO
                    Total: ~35-90 segundos
```

---

## Gargalos Identificados

### 🔴 CRÍTICO - Gargalo #1: Validações Repetidas de Modelos

**Localização:** `LLMServiceManager.findServiceByModel()` → `serviceSupportsModel()`

**Código Problemático:**

```java
// LLMServiceManager.java:484-560
private LLMService findServiceByModel(String modelName) {
    String normalizedModelName = modelName.toLowerCase().trim();

    for (LLMService service : services) {
        if (serviceSupportsModel(service, normalizedModelName)) {  // ❌ VALIDA!
            return service;
        }
    }
    return getPrimaryService();
}

private boolean serviceSupportsModel(LLMService service, String normalizedModelName) {
    MapModels models = service.getInstalledModels();  // ❌ CHAMA LLM PROVIDER VIA HTTP!

    if (models == null || models.isEmpty()) {
        return false;
    }

    // Itera por todos os modelos para encontrar match...
}
```

**Problema:**

- **Chamado para CADA embedding gerado** (~100-150 vezes por documento!)
- `getInstalledModels()` faz chamada HTTP ao provider (Ollama, LMStudio, etc.)
- Latência: ~50-200ms por chamada
- **Overhead total: 5-30 segundos APENAS em validações!**

**Cache Existente NÃO Utilizado:**

```java
// Cache existe mas não é usado em findServiceByModel()!
protected java.util.Map<String, LLMService> modelsToService;  // ❌ Não usado!

public java.util.Map<String, LLMService> getRegisteredModelsMap() {
    if (modelsToService == null) {
        modelsToService = new java.util.HashMap<>();
    }

    if(!modelsToService.isEmpty()) {
        return modelsToService;  // Cache hit!
    }

    // Popula cache...
}
```

**Impacto:** **CRÍTICO** - Representa 15-30% do tempo total de processamento

---

### 🔴 CRÍTICO - Gargalo #2: Ausência de Batching de Embeddings

**Localização:** `EmbeddingProcessorImpl.createChapterEmbeddings()` → Loop serial

**Código Problemático:**

```java
// EmbeddingProcessorImpl.java:306-346
private List<DocumentEmbeddingDTO> createSplitTextEmbeddings(ChapterDTO capitulo, ...) {
    List<DocumentEmbeddingDTO> embeddings = new ArrayList<>();

    ContentSplitter contentSplitter = new ContentSplitter();
    List<ChapterDTO> chunks = contentSplitter.splitContent(capitulo.getConteudo(), false);

    // ❌ LOOP SERIAL - Cada chunk é processado individualmente!
    for (int i = 0; i < chunks.size(); i++) {
        ChapterDTO chunk = chunks.get(i);

        DocumentEmbeddingDTO embedding = createEmbeddingFromText(
            chunk.getConteudo(),      // ❌ 1 texto por vez
            chunkTitle,
            biblioteca,
            documentoId,
            capituloId,
            TipoEmbedding.TRECHO,
            Embeddings_Op.DOCUMENT
        );  // ❌ 1 chamada LLM por chunk!

        embeddings.add(embedding);
    }

    return embeddings;
}
```

**Problema:**

- Se 1 capítulo → 5 chunks: **5 chamadas LLM sequenciais**
- Se 10 capítulos → 50 chunks: **50 chamadas LLM sequenciais**
- Latência LLM: ~200-500ms por embedding
- **Tempo total: 10-25 segundos só para embeddings básicos!**

**Solução Possível:** Batch de embeddings

```java
// Proposta: Coletar todos os textos e gerar embeddings em lotes
List<String> allTexts = chunks.stream()
    .map(ChapterDTO::getConteudo)
    .toList();

// 1 chamada LLM para todos os textos (ou lotes de 20)
List<float[]> embeddings = llmService.embeddingsBatch(operation, allTexts, params);
```

**Impacto:** **CRÍTICO** - Representa 25-40% do tempo total de processamento

---

### 🔴 CRÍTICO - Gargalo #3: Q&A e Summary - Múltiplas Chamadas LLM

**Localização:**
- `EmbeddingProcessorImpl.createQAEmbeddings()` (linhas 144-216)
- `EmbeddingProcessorImpl.createSummaryEmbeddings()` (linhas 481-599)

**Código Problemático (Q&A):**

```java
// EmbeddingProcessorImpl.java:144-216
public List<DocumentEmbeddingDTO> createQAEmbeddings(ChapterDTO capitulo, ...) {
    // ❌ LLM CALL #1: Gera pares Q&A via completion
    List<QuestionAnswer> qaList = documentSummarizer.generateQA(capitulo.getConteudo(), k);
    // Latência: ~2-5 segundos

    for (int i = 0; i < qaList.size(); i++) {  // 3 iterações por padrão
        QuestionAnswer qa = qaList.get(i);
        String combinedText = "Pergunta: " + qa.getQuestion() + "\n\nResposta: " + qa.getAnswer();

        // ❌ LLM CALL #2, #3, #4: Embeddings individuais para cada Q&A
        DocumentEmbeddingDTO qaEmbedding = createEmbeddingFromText(
            combinedText, ..., Embeddings_Op.DOCUMENT
        );  // Latência: ~300-500ms CADA

        qaEmbeddings.add(qaEmbedding);
    }

    return qaEmbeddings;  // Total: 1 completion + 3 embeddings = 4 chamadas LLM
}
```

**Problema (Q&A):**

- **Por capítulo:** 1 completion (~2-5s) + 3 embeddings (~1-2s) = **3-7 segundos**
- **10 capítulos:** 40 chamadas LLM = **30-70 segundos APENAS para Q&A!**

**Código Problemático (Summary):**

```java
// EmbeddingProcessorImpl.java:481-599
public List<DocumentEmbeddingDTO> createSummaryEmbeddings(ChapterDTO chapter, ...) {
    // ❌ LLM CALL #1: Gera sumário via completion
    String summary = documentSummarizer.summarize(chapter.getConteudo(), instructions, maxLength);
    // Latência: ~1-3 segundos

    // ❌ LLM CALL #2: Embedding do sumário
    DocumentEmbeddingDTO summaryEmbedding = createEmbeddingFromText(
        summary, ..., TipoEmbedding.RESUMO, Embeddings_Op.DOCUMENT
    );  // Latência: ~300-500ms

    // ❌ LLM CALL #3: Embedding híbrido (se texto longo)
    if (chapter.getConteudo().length() > DEFAULT_CHUNK_SIZE * 2) {
        DocumentEmbeddingDTO hybridEmbedding = createHybridSummaryEmbedding(...);
        // Latência: ~300-500ms
    }

    return summaryEmbeddings;  // Total: 1 completion + 2 embeddings = 3 chamadas LLM
}
```

**Problema (Summary):**

- **Por capítulo:** 1 completion (~1-3s) + 2 embeddings (~1s) = **2-4 segundos**
- **10 capítulos:** 30 chamadas LLM = **20-40 segundos APENAS para sumários!**

**Impacto Combinado:** **CRÍTICO** - Q&A + Summary = 50-110 segundos!

---

### 🟠 ALTO - Gargalo #4: Persistência Serial de Embeddings

**Localização:** `DocumentoService.persistProcessingResult()` (linhas 543-553)

**Código Problemático:**

```java
// DocumentoService.java:543-553
// ✅ Capítulos salvos em batch (eficiente)
List<Chapter> savedChapters = chapterRepository.saveAll(chapters);
log.debug("Saved {} chapters", savedChapters.size());

// ❌ Embeddings salvos UM POR UM!
List<Integer> savedIds = new ArrayList<>();
for (DocumentEmbedding emb : embeddings) {  // 100 iterações!
    try {
        Integer id = embeddingRepository.save(emb);  // ❌ 1 INSERT + COMMIT
        savedIds.add(id);
    } catch (SQLException e) {
        log.error("Failed to save embedding: {}", e.getMessage(), e);
        throw new RuntimeException("Embedding save failed", e);
    }
}

log.debug("Saved {} embeddings with IDs: {}", savedIds.size(), savedIds);
```

**Problema:**

- **100 embeddings = 100 INSERTs individuais**
- Cada INSERT espera confirmação do PostgreSQL
- Latência de rede: ~20-50ms por INSERT
- **Overhead total: 2-5 segundos APENAS em latência de rede!**

**Comparação:**

| Método | Embeddings | INSERTs | Round-trips | Tempo |
|--------|-----------|---------|-------------|-------|
| Atual (serial) | 100 | 100 | 100 | ~2-5s |
| Batch ideal | 100 | 1 | 1 | ~100-200ms |

**Solução:** `DocEmbeddingJdbcRepository.saveAll(List<DocumentEmbedding>)`

```sql
-- Batch INSERT (1 round-trip)
INSERT INTO doc_embedding (biblioteca_id, documento_id, capitulo_id, ...)
VALUES
    (1, 100, 50, ...),
    (1, 100, 50, ...),
    (1, 100, 51, ...),
    ...  -- 100 linhas
RETURNING id;
```

**Impacto:** **ALTO** - Representa 5-10% do tempo total de processamento

---

### 🟡 MÉDIO - Gargalo #5: Retry Logic Global Sem Checkpoint

**Localização:** `EmbeddingOrchestrator.processDocumentFull()` (linhas 68-108)

**Código Problemático:**

```java
// EmbeddingOrchestrator.java:68-108
public CompletableFuture<ProcessingResult> processDocumentFull(...) {
    return CompletableFuture.supplyAsync(() -> {
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= MAX_RETRIES) {  // 2 retries = 3 tentativas totais
            try {
                log.info("Processing document (attempt {}/{}): {}", ...);

                return executeProcessing(documento, context, options);
                // ❌ Se falha no capítulo 10 de 10, refaz TUDO!

            } catch (Exception e) {
                lastException = e;
                attempt++;

                if (attempt <= MAX_RETRIES) {
                    log.warn("Processing failed... Retrying in 2 minutes...");
                    Thread.sleep(RETRY_DELAY_MS);  // ❌ 2 MINUTOS!
                }
            }
        }

        throw new RuntimeException("Failed after " + (MAX_RETRIES + 1) + " attempts", lastException);
    });
}
```

**Problema:**

- **Sem checkpoint:** Falha no último capítulo = refaz tudo
- **Delay fixo:** 2 minutos entre tentativas (muito longo)
- **Desperdício:** Reprocessa capítulos já bem-sucedidos

**Cenário Real:**

```
Tentativa 1:
├─ Capítulos 1-9: ✅ Processados com sucesso (27s)
└─ Capítulo 10: ❌ Falha na geração de Q&A (timeout LLM)

[ESPERA 2 MINUTOS]

Tentativa 2:
├─ Capítulos 1-9: ♻️ Reprocessados desnecessariamente (27s)
└─ Capítulo 10: ✅ Sucesso (3s)

Total: 2min + 27s + 27s + 3s = ~3min 57s
Ideal: 27s + 0s + 3s = ~30s
```

**Impacto:** **MÉDIO** - Só afeta casos de falha (mas aumenta em 4-6x o tempo)

---

## Análise de Impacto

### Cenário Base: Documento com 10 Capítulos

**Configuração:**
- Documento: 10 capítulos (~8k tokens cada)
- Processamento: includeQA=true, includeSummary=true
- Ambiente: PostgreSQL local, Ollama com nomic-embed-text

---

### Tabela de Chamadas LLM (Situação Atual)

| Operação | Qtde Chamadas | Tipo | Tempo Unitário | Tempo Total |
|----------|---------------|------|----------------|-------------|
| **Validações Iniciais** |
| Validação inicial context | 1-2 | getInstalledModels() | 100ms | 100-200ms |
| Validação splitter setup | 1-2 | getInstalledModels() | 100ms | 100-200ms |
| **Embeddings Básicos** |
| Chunks (5 por capítulo) | 50 | embeddings() | 300ms | 15.0s |
| Validações de modelo (por chunk) | 50 | getInstalledModels() | 100ms | 5.0s |
| **Q&A (includeQA=true)** |
| Geração Q&A | 10 | completion() | 2-5s | 20-50s |
| Embeddings Q&A (3 por cap) | 30 | embeddings() | 300ms | 9.0s |
| Validações de modelo (Q&A) | 30 | getInstalledModels() | 100ms | 3.0s |
| **Summary (includeSummary=true)** |
| Geração sumário | 10 | completion() | 1-3s | 10-30s |
| Embeddings sumário | 10 | embeddings() | 300ms | 3.0s |
| Embeddings híbridos (~50% caps) | 5 | embeddings() | 300ms | 1.5s |
| Validações de modelo (summary) | 15 | getInstalledModels() | 100ms | 1.5s |
| **Persistência** |
| Capítulos (batch) | 0 | INSERT batch | - | 100ms |
| Embeddings (serial) | 0 | INSERT × 105 | 30ms | 3.2s |
| Update documento | 0 | UPDATE | - | 50ms |
| **TOTAL** | **~214 chamadas** | | | **71-117s** |

**Distribuição do Tempo:**

- 🔴 **Validações desnecessárias:** ~10-15s (13-18%)
- 🔴 **LLM completions:** ~30-80s (42-68%)
- 🔴 **LLM embeddings:** ~28-30s (39-26%)
- 🟠 **Persistência:** ~3-4s (4-3%)

---

### Tabela de Chamadas LLM (Com Otimizações Propostas)

| Operação | Qtde Chamadas | Tipo | Economia | Tempo Total |
|----------|---------------|------|----------|-------------|
| **Validações (Cache)** |
| Cache populado na inicialização | 1 | getInstalledModels() | -11 chamadas | 100ms |
| **Embeddings Básicos (Batch)** |
| Chunks em lotes de 20 | 3 | embeddingsBatch() | -47 chamadas | 3.0s |
| **Q&A (Batch)** |
| Geração Q&A (mantém) | 10 | completion() | - | 20-50s |
| Embeddings Q&A em lote | 2 | embeddingsBatch() | -28 chamadas | 1.0s |
| **Summary (Batch)** |
| Geração sumário (mantém) | 10 | completion() | - | 10-30s |
| Embeddings sumário + híbrido | 1 | embeddingsBatch() | -14 chamadas | 0.5s |
| **Persistência (Batch)** |
| Embeddings em batch | 0 | INSERT batch | - | 200ms |
| **TOTAL OTIMIZADO** | **~27 chamadas** | | **-187 chamadas** | **35-85s** |

**Ganho de Desempenho:**

- **Redução de chamadas LLM:** 214 → 27 = **87% menos chamadas**
- **Redução de tempo:** 71-117s → 35-85s = **~40-50% mais rápido**
- **Economia em validações:** 10-15s → 0.1s = **99% economia**
- **Economia em persistência:** 3.2s → 0.2s = **94% economia**

---

### Gráfico de Comparação

```
TEMPO TOTAL DE PROCESSAMENTO (10 capítulos)

Atual:       ████████████████████████████████████ 71-117s
             │
             ├─ Validações:    ██████ 10-15s
             ├─ Completions:   ████████████████████ 30-80s
             ├─ Embeddings:    ██████████████ 28-30s
             └─ Persistência:  ██ 3-4s

Otimizado:   ████████████████ 35-85s (↓ 40-50%)
             │
             ├─ Validações:    ⚪ 0.1s (cache)
             ├─ Completions:   ████████████████████ 30-80s (mantém)
             ├─ Embeddings:    ██ 4-6s (batch)
             └─ Persistência:  ⚪ 0.2s (batch)
```

---

## Recomendações de Otimização

### 🎯 Prioridade 1 (CRÍTICA): Cache de Modelos Disponíveis

**Problema:** `getInstalledModels()` chamado ~100 vezes por documento

**Solução:**

```java
// LLMServiceManager.java - Inicializar cache na criação
public LLMServiceManager(List<LLMService> services, ...) {
    // ... existing code ...

    // Popular cache de modelos AGORA (1x na inicialização)
    this.modelsToService = new ConcurrentHashMap<>();
    this.refreshRegisteredModels();

    log.info("Model cache initialized with {} models", modelsToService.size());
}

// Usar o cache em findServiceByModel() ao invés de chamar getInstalledModels()
private LLMService findServiceByModel(String modelName) {
    String normalized = normalizeModelName(modelName);

    // ✅ Usa cache ao invés de chamar LLM provider
    LLMService service = modelsToService.get(normalized);

    if (service != null) {
        return service;
    }

    // Fallback se não encontrar no cache
    log.warn("Model '{}' not in cache, falling back to primary", modelName);
    return getPrimaryService();
}
```

**Ganho Estimado:**
- Elimina ~100 chamadas HTTP por documento
- Reduz tempo em ~10-15 segundos (13-18%)
- Custo: 1 chamada HTTP na inicialização

---

### 🎯 Prioridade 1 (CRÍTICA): Batching de Embeddings

**Problema:** Embeddings gerados 1 por vez (50+ chamadas LLM)

**Solução 1: Adicionar método batch no EmbeddingService**

```java
// EmbeddingService.java
public interface EmbeddingService {

    // Método existente (mantém)
    float[] generateEmbedding(String text, Embeddings_Op operation, EmbeddingContext context);

    // ✅ NOVO: Método batch
    List<float[]> generateEmbeddingsBatch(
        List<String> texts,
        Embeddings_Op operation,
        EmbeddingContext context
    );
}
```

**Solução 2: Modificar createSplitTextEmbeddings() para usar batch**

```java
// EmbeddingProcessorImpl.java
private List<DocumentEmbeddingDTO> createSplitTextEmbeddings(ChapterDTO capitulo, ...) {
    List<DocumentEmbeddingDTO> embeddings = new ArrayList<>();

    ContentSplitter contentSplitter = new ContentSplitter();
    List<ChapterDTO> chunks = contentSplitter.splitContent(capitulo.getConteudo(), false);

    // ✅ Coletar todos os textos
    List<String> allTexts = chunks.stream()
        .map(ChapterDTO::getConteudo)
        .toList();

    // ✅ 1 chamada LLM para todos os chunks (ou lotes de 20)
    List<float[]> embeddingVectors = embeddingService.generateEmbeddingsBatch(
        allTexts,
        Embeddings_Op.DOCUMENT,
        biblioteca
    );

    // Mapear resultados para DTOs
    for (int i = 0; i < chunks.size(); i++) {
        ChapterDTO chunk = chunks.get(i);
        float[] vector = embeddingVectors.get(i);

        DocumentEmbeddingDTO embedding = new DocumentEmbeddingDTO();
        embedding.setTrechoTexto(chunk.getConteudo());
        embedding.setEmbeddingVector(vector);
        // ... set outros campos ...

        embeddings.add(embedding);
    }

    return embeddings;
}
```

**Ganho Estimado:**
- Reduz 50 chamadas → 3-5 chamadas (lotes de 10-20)
- Reduz tempo de embeddings básicos: 15s → 3-4s (~75% economia)

---

### 🎯 Prioridade 1 (CRÍTICA): Batch INSERT de Embeddings

**Problema:** 100 INSERTs individuais (1 round-trip cada)

**Solução 1: Adicionar método saveAll() no repository**

```java
// DocEmbeddingJdbcRepository.java
public List<Integer> saveAll(List<DocumentEmbedding> embeddings) throws SQLException {
    if (embeddings == null || embeddings.isEmpty()) {
        return Collections.emptyList();
    }

    String sql = "INSERT INTO doc_embedding " +
                "(biblioteca_id, documento_id, capitulo_id, texto, embedding_vector, " +
                "tipo_embedding, metadados, order_chapter, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?::vector, ?::tipo_embedding_enum, ?::jsonb, ?, ?, ?) " +
                "RETURNING id";

    List<Integer> generatedIds = new ArrayList<>();

    try (PreparedStatement ps = connection.prepareStatement(sql)) {
        for (DocumentEmbedding emb : embeddings) {
            ps.setInt(1, emb.getLibraryId());
            ps.setObject(2, emb.getDocumentoId());
            ps.setObject(3, emb.getChapterId());
            ps.setString(4, emb.getTexto());
            ps.setString(5, vectorToString(emb.getEmbeddingVector()));
            ps.setString(6, emb.getTipoEmbedding().name());
            ps.setString(7, metadataToJson(emb.getMetadados()));
            ps.setObject(8, emb.getOrderChapter());
            ps.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));

            ps.addBatch();
        }

        // ✅ Executa batch (1 round-trip!)
        ps.executeBatch();

        // Recuperar IDs gerados
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

**Solução 2: Usar saveAll() no DocumentoService**

```java
// DocumentoService.java:persistProcessingResult()
protected void persistProcessingResult(EmbeddingOrchestrator.ProcessingResult result, ...) {
    // ... save chapters ...

    // ✅ Salvar embeddings em batch
    List<DocumentEmbedding> embeddings = result.getAllEmbeddings().stream()
        .map(dto -> toEntity(dto, documento, chapterIdMap))
        .collect(Collectors.toList());

    List<Integer> savedIds = embeddingRepository.saveAll(embeddings);  // ✅ 1 round-trip!

    log.debug("Saved {} embeddings with IDs: {}", savedIds.size(), savedIds);

    // ... update documento ...
}
```

**Ganho Estimado:**
- Reduz 100 round-trips → 1 round-trip
- Reduz tempo de persistência: 3-5s → 0.2-0.5s (~85-90% economia)

---

### 🎯 Prioridade 2 (ALTA): Checkpoint Incremental no Retry Logic

**Problema:** Falha no último capítulo = refaz tudo (3-5 min perdidos)

**Solução: Salvar progresso incremental**

```java
// EmbeddingOrchestrator.java
private ProcessingResult executeProcessing(...) {
    ProcessingResult result = new ProcessingResult();
    result.setDocumento(documento);

    // Detect e split
    TipoConteudo tipoConteudo = documentRouter.detectContentType(...);
    DocumentSplitter splitter = splitterFactory.createSplitter(...);
    List<ChapterDTO> chapters = splitter.splitDocumento(documento);
    result.setCapitulos(chapters);

    // ✅ Processar capítulo por capítulo com checkpoint
    for (int i = 0; i < chapters.size(); i++) {
        ChapterDTO chapter = chapters.get(i);

        try {
            log.debug("Processing chapter {}/{}: {}", i+1, chapters.size(), chapter.getTitulo());

            // Gerar embeddings para este capítulo
            List<DocumentEmbeddingDTO> chapterEmbeddings = processChapter(chapter, context, options);
            result.addEmbeddings(chapterEmbeddings);

            // ✅ CHECKPOINT: Salvar imediatamente após cada capítulo bem-sucedido
            if (options.isIncrementalPersist()) {
                persistChapterResult(chapter, chapterEmbeddings, documento);
                log.debug("Checkpoint saved for chapter {}/{}", i+1, chapters.size());
            }

        } catch (Exception e) {
            log.error("Failed to process chapter {}/{}: {}",
                     i+1, chapters.size(), e.getMessage());

            if (options.isFailFast()) {
                throw e;  // Falhar imediatamente
            } else {
                // Continuar com próximo capítulo
                log.warn("Skipping failed chapter, continuing with next...");
            }
        }
    }

    return result;
}
```

**Ganho Estimado:**
- Elimina reprocessamento desnecessário em caso de falha
- Reduz tempo de recovery de ~3-5 min → ~3-10 segundos
- Permite processamento parcial de documentos grandes

---

### 🎯 Prioridade 3 (MÉDIA): Paralelização de Capítulos

**Problema:** Capítulos processados sequencialmente (não aproveita múltiplos cores)

**Solução: Usar CompletableFuture para processar capítulos em paralelo**

```java
// EmbeddingOrchestrator.java
private ProcessingResult executeProcessing(...) {
    // ... detect, split ...

    // ✅ Processar capítulos em paralelo
    List<CompletableFuture<ChapterProcessingResult>> futures = chapters.stream()
        .map(chapter -> CompletableFuture.supplyAsync(() -> {
            try {
                return processChapter(chapter, context, options);
            } catch (Exception e) {
                log.error("Chapter processing failed: {}", e.getMessage());
                return null;  // ou lançar exceção
            }
        }, taskExecutor))
        .toList();

    // Aguardar todos os capítulos
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    // Coletar resultados
    for (CompletableFuture<ChapterProcessingResult> future : futures) {
        ChapterProcessingResult chapterResult = future.get();
        if (chapterResult != null) {
            result.addEmbeddings(chapterResult.getEmbeddings());
        }
    }

    return result;
}
```

**Ganho Estimado:**
- Com 4 cores: tempo reduzido em ~60-70%
- Exemplo: 10 capítulos × 3s = 30s → 10s (processamento paralelo)
- **ATENÇÃO:** Requer gestão de rate limits do LLM provider!

---

## Conclusão

### Resumo dos Gargalos

| Gargalo | Severidade | Tempo Perdido | Implementação |
|---------|-----------|---------------|---------------|
| Validações repetidas de modelos | 🔴 CRÍTICO | 10-15s (13-18%) | Fácil |
| Ausência de batching (embeddings) | 🔴 CRÍTICO | 12-20s (17-28%) | Média |
| Q&A + Summary sem otimização | 🔴 CRÍTICO | 50-110s (70%) | Complexa |
| Persistência serial | 🟠 ALTO | 3-5s (4-7%) | Fácil |
| Retry sem checkpoint | 🟡 MÉDIO | 0-180s (em falhas) | Média |

### Próximos Passos

1. **⏸️ PAUSA para Proposta de Nova Arquitetura**
   - Este documento serve como base para discussão
   - Usuário vai propor nova abordagem
   - Evitar otimizações incrementais se fluxo fundamental está errado

2. **Após definição da nova arquitetura:**
   - Implementar cache de modelos (Quick Win - 1h)
   - Implementar batch de embeddings (2-4h)
   - Implementar batch INSERT (1-2h)
   - Avaliar necessidade de checkpoint incremental
   - Considerar paralelização (se novo fluxo permitir)

---

**Documento gerado em:** 2025-01-31
**Aguardando:** Proposta de nova arquitetura pelo usuário
