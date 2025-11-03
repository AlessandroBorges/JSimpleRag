# Diagrama de Fluxo - Processamento de Documentos

**Data:** 2025-11-02
**Versão:** 1.1
**Baseado em:** DocumentProcessingService.java

---

## 🎯 Resposta Direta

**SIM**, o novo fluxo de processamento **utiliza Splitters** para criar tanto **Chapters** quanto **DocEmbeddings**:

1. **AbstractSplitter** (específico do tipo de documento) → Cria **CHAPTERS**
2. **SplitterGenerico** → Cria **DocEmbeddings (TRECHOS)** a partir dos Chapters

---

## 📊 Diagrama Simplificado - Visão Geral

```
┌─────────────────────────────────────────────────────────────────────┐
│                    DOCUMENTO (markdown, ~15k tokens)                │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
        ┌────────────────────────────────────────────────┐
        │  ETAPA 2.1: Criar Contextos                    │
        │  • LLMContext (para token counting, summaries) │
        │  • EmbeddingContext (para gerar embeddings)    │
        └────────────────┬───────────────────────────────┘
                         │
                         ▼
        ┌────────────────────────────────────────────────┐
        │  ETAPA 2.2: Split and Persist                  │
        │                                                │
        │  ┌──────────────────────────────────────┐      │
        │  │  DocumentRouter.detectContentType()  │      │
        │  │  → TipoConteudo (LEI, WIKI, OUTROS)  │      │
        │  └──────────────┬───────────────────────┘      │
        │                 │                              │
        │                 ▼                              │
        │  ┌──────────────────────────────────────┐      │
        │  │  SplitterFactory.createSplitter()    │      │
        │  │  → AbstractSplitter (específico)     │      │
        │  │     • SplitterNorma                  │      │
        │  │     • SplitterWiki                   │      │
        │  │     • SplitterGenerico               │      │
        │  └──────────────┬───────────────────────┘      │
        │                 │                              │
        │                 ▼                              │
        │  ┌──────────────────────────────────────┐      │
        │  │  splitter.splitDocumento()           │      │
        │  │  → List<ChapterDTO> (4 capítulos)    │      │
        │  └──────────────┬───────────────────────┘      │
        │                 │                              │
        │                 ▼                              │
        │     Para cada ChapterDTO:                      │
        │     ┌────────────────────────────────┐         │
        │     │  createChapterEmbeddings()     │         │
        │     │                                │         │
        │     │  IF chapter ≤ 2000 tokens:     │         │
        │     │    → criarTrechoUnico()        │         │
        │     │    → 1 TRECHO (capítulo todo)  │         │
        │     │                                │         │
        │     │  IF chapter > 2000 tokens:     │         │
        │     │    1. IF > 2500 tokens:        │         │
        │     │       → criarResumo() via LLM  │         │
        │     │       → 1 RESUMO               │         │
        │     │                                │         │
        │     │    2. SplitterGenerico.        │         │
        │     │       splitChapterIntoChunks() │         │
        │     │       → List<DocEmbeddingDTO>  │         │
        │     │       → N TRECHOS (~512 tokens)│         │
        │     └────────────┬───────────────────┘         │
        │                  │                             │
        │                  ▼                             │
        │  ┌──────────────────────────────────────┐      │
        │  │  chapterRepository.saveAll()         │      │
        │  │  → Chapters com IDs persistidos      │      │
        │  └──────────────┬───────────────────────┘      │
        │                 │                              │
        │                 ▼                              │
        │  ┌──────────────────────────────────────┐      │
        │  │  embeddingRepository.saveAll()       │      │
        │  │  → DocEmbeddings com vectors=NULL    │      │
        │  └──────────────┬───────────────────────┘      │
        └─────────────────┼──────────────────────────────┘
                          │
                          ▼
        ┌────────────────────────────────────────────────┐
        │  ETAPA 2.3: Calculate Embeddings               │
        │                                                │
        │  Para cada batch (até 10 embeddings):          │
        │  ┌──────────────────────────────────────┐      │
        │  │  handleOversizedText()               │      │
        │  │  (se > contextLength)                │      │
        │  └──────────────┬───────────────────────┘      │
        │                 │                              │
        │                 ▼                              │
        │  ┌──────────────────────────────────────┐      │
        │  │  embeddingContext.                   │      │
        │  │    generateEmbeddingsBatch()         │      │
        │  │  → List<float[]> (vetores 1536-dim)  │      │
        │  └──────────────┬───────────────────────┘      │
        │                 │                              │
        │                 ▼                              │
        │  ┌──────────────────────────────────────┐      │
        │  │  embeddingRepository.                │      │
        │  │    updateEmbeddingVector()           │      │
        │  │  → Vetores persistidos no banco      │      │
        │  └──────────────────────────────────────┘      │
        └────────────────────────────────────────────────┘
```

---

## 🔍 Detalhamento: Uso dos Splitters

### 1 - AbstractSplitter → Criação de CHAPTERS

**Localização:** `DocumentProcessingService.splitAndPersist()` (linha 216-304)

```java
// Detecta o tipo de conteúdo
TipoConteudo tipoConteudo = documentRouter.detectContentType(documento.getConteudoMarkdown());
// → LEI, DECRETO, WIKI, LIVRO, ARTIGO, OUTROS, etc.

// Cria o splitter apropriado
AbstractSplitter splitter = splitterFactory.createSplitter(tipoConteudo, library);
// → SplitterNorma (para leis/decretos)
// → SplitterWiki (para wikis)
// → SplitterGenerico (para outros)

// Divide documento em capítulos
List<ChapterDTO> chapterDTOs = splitter.splitDocumento(documentoDTO);
```

**Resultado:**
- Documento de 15k tokens → **4 capítulos** (~3750 tokens cada)
- Critérios de divisão dependem do tipo:
  - **SplitterNorma:** Divide por artigos/seções
  - **SplitterWiki:** Divide por títulos H1, H2
  - **SplitterGenerico:** Divide por títulos markdown ou tamanho

---

### 2 - SplitterGenerico → Criação de DocEmbeddings (TRECHOS)

**Localização:** `DocumentProcessingService.createChapterEmbeddings()` (linha 323-401)

```java
// Para cada capítulo criado:
for (ChapterDTO chapterDTO : chapterDTOs) {

    // Conta tokens no capítulo
    int chapterTokens = llmContext.tokenCount(chapterDTO.getConteudo(), "fast");

    // CASO 1: Capítulo pequeno (≤ 2000 tokens)
    if (chapterTokens <= 2000) {
        // Cria 1 TRECHO único com todo o conteúdo do capítulo
        DocumentEmbedding trecho = criarTrechoUnico(chapterDTO, documento, 0);
        embeddings.add(trecho);
        return embeddings;
    }

    // CASO 2: Capítulo grande (> 2000 tokens)

    // Passo 1: Se > 2500 tokens, gera RESUMO via LLM
    if (chapterTokens > 2500) {
        DocumentEmbedding resumo = criarResumo(chapterDTO, documento, llmContext);
        embeddings.add(resumo);
    }

    // Passo 2: Divide capítulo em chunks usando SplitterGenerico
    SplitterGenerico splitter = splitterFactory.createGenericSplitter(library);
    List<DocumentEmbeddingDTO> chunkDTOs = splitter.splitChapterIntoChunks(chapterDTO);
    // → Divide em chunks de ~512 tokens (ideal)
    // → Max 2048 tokens por chunk

    // Converte DTOs em entities
    for (DocumentEmbeddingDTO chunkDTO : chunkDTOs) {
        DocumentEmbedding trecho = DocumentEmbedding.builder()
            .tipoEmbedding(TipoEmbedding.TRECHO)
            .texto(chunkDTO.getTrechoTexto())
            .embeddingVector(null)  // Calculado depois
            .build();
        embeddings.add(trecho);
    }
}
```

**Resultado para capítulo de 3750 tokens:**
- **1 RESUMO** (se > 2500 tokens, gerado via LLM)
- **~7 TRECHOS** (3750 / 512 ≈ 7 chunks de ~512 tokens cada)

---

## 📈 Exemplo Prático: Documento de 15k Tokens

```
┌─────────────────────────────────────────────────────────┐
│  DOCUMENTO: "Manual Técnico" (15,000 tokens)            │
└────────────────┬────────────────────────────────────────┘
                 │
                 │ AbstractSplitter (SplitterGenerico)
                 ▼
    ┌────────────────────────────────────┐
    │  CAPÍTULO 1: "Introdução"          │
    │  3,750 tokens                      │
    └────────┬───────────────────────────┘
             │
             │ SplitterGenerico.splitChapterIntoChunks()
             ▼
      ┌──────────────────────────────────────┐
      │  • 1 RESUMO (gerado via LLM)         │
      │  • 7 TRECHOS (~512 tokens cada)      │
      └──────────────────────────────────────┘

    ┌────────────────────────────────────┐
    │  CAPÍTULO 2: "Arquitetura"         │
    │  3,750 tokens                      │
    └────────┬───────────────────────────┘
             │
             │ SplitterGenerico.splitChapterIntoChunks()
             ▼
      ┌──────────────────────────────────────┐
      │  • 1 RESUMO                          │
      │  • 7 TRECHOS                         │
      └──────────────────────────────────────┘

    ┌────────────────────────────────────┐
    │  CAPÍTULO 3: "Componentes"         │
    │  1,200 tokens                      │
    └────────┬───────────────────────────┘
             │
             │ Capítulo pequeno (≤ 2000 tokens)
             ▼
      ┌──────────────────────────────────────┐
      │  • 1 TRECHO (capítulo completo)      │
      │    (sem divisão, sem resumo)         │
      └──────────────────────────────────────┘

    ┌────────────────────────────────────┐
    │  CAPÍTULO 4: "API Reference"       │
    │  6,300 tokens                      │
    └────────┬───────────────────────────┘
             │
             │ SplitterGenerico.splitChapterIntoChunks()
             ▼
      ┌──────────────────────────────────────┐
      │  • 1 RESUMO                          │
      │  • 12 TRECHOS (~512 tokens cada)     │
      └──────────────────────────────────────┘

═══════════════════════════════════════════════
  TOTAL:
  • 4 CHAPTERS
  • 3 RESUMOS (capítulos 1, 2, 4 > 2500 tokens)
  • 27 TRECHOS (7+7+1+12)
  = 30 DocEmbeddings
═══════════════════════════════════════════════
```

---

## 🔧 Constantes Importantes

### SplitterGenerico (para Chapters)

```java
CHAPTER_IDEAL_TOKENS = 8192    // Tamanho ideal de capítulo
CHAPTER_MIN_TOKENS   = 4096    // Mínimo para criar novo capítulo
CHAPTER_MAX_TOKENS   = 16384   // Máximo permitido
```

### SplitterGenerico (para Chunks/Trechos)

```java
CHUNK_IDEAL_TOKENS = 512       // Tamanho ideal de chunk
CHUNK_MAX_TOKENS   = 2048      // Máximo permitido
MIN_TOKENS         = 300       // Mínimo para criar chunk
```

### DocumentProcessingService

```java
IDEAL_CHUNK_SIZE_TOKENS      = 2000   // Threshold para dividir capítulo
SUMMARY_THRESHOLD_TOKENS     = 2500   // Threshold para gerar resumo
SUMMARY_MAX_TOKENS           = 1024   // Tamanho máximo do resumo gerado
BATCH_SIZE                   = 10     // Embeddings por batch
OVERSIZE_THRESHOLD_PERCENT   = 2.0    // % para resumir vs truncar
```

---

## 🎨 Diagrama Detalhado - Criação de Embeddings

```
┌──────────────────────────────────────────────────────────────┐
│  ChapterDTO (3750 tokens)                                    │
└────────────────┬─────────────────────────────────────────────┘
                 │
                 ▼
    ┌────────────────────────────────────┐
    │  llmContext.tokenCount()           │
    │  → chapterTokens = 3750            │
    └────────────┬───────────────────────┘
                 │
                 ▼
       ┌─────────────────┐
       │ chapterTokens   │
       │    > 2000?      │
       └────┬───────┬────┘
            │ SIM   │ NÃO
            │       │
            │       ▼
            │  ┌─────────────────────────────┐
            │  │  criarTrechoUnico()         │
            │  │  • tipo: TRECHO             │
            │  │  • texto: capítulo completo │
            │  │  • orderChapter: 0          │
            │  │  • vector: NULL             │
            │  └─────────────────────────────┘
            │
            ▼
    ┌─────────────────┐
    │ chapterTokens   │
    │    > 2500?      │
    └────┬───────┬────┘
         │ SIM   │ NÃO (pula resumo)
         │       │
         ▼       │
    ┌────────────────────────────┐
    │  criarResumo()             │
    │  • LLM summarization       │
    │  • tipo: RESUMO            │
    │  • texto: summary gerado   │
    │  • orderChapter: -1        │
    │  • vector: NULL            │
    └────────────┬───────────────┘
                 │
                 │ (continua para ambos os casos)
                 ▼
    ┌─────────────────────────────────────────────┐
    │  SplitterGenerico.splitChapterIntoChunks()  │
    │                                             │
    │  1. Conta tokens no capítulo                │
    │  2. Detecta títulos markdown (##, ###)      │
    │  3. Divide por títulos OU por tamanho       │
    │  4. Cada chunk ~512 tokens (ideal)          │
    │  5. Máximo 2048 tokens por chunk            │
    └────────────┬────────────────────────────────┘
                 │
                 ▼
    ┌────────────────────────────────────────┐
    │  List<DocumentEmbeddingDTO>            │
    │  [chunk1, chunk2, ..., chunk7]         │
    └────────────┬───────────────────────────┘
                 │
                 ▼
    Para cada chunk:
    ┌────────────────────────────────────────┐
    │  DocumentEmbedding.builder()           │
    │  • tipo: TRECHO                        │
    │  • texto: chunk content                │
    │  • orderChapter: 0, 1, 2, ...          │
    │  • metadata: chunk_index, total_chunks │
    │  • vector: NULL                        │
    └────────────┬───────────────────────────┘
                 │
                 ▼
    ┌────────────────────────────────────────┐
    │  List<DocumentEmbedding>               │
    │  [RESUMO?, TRECHO1, TRECHO2, ...]      │
    │  Total: 1 RESUMO + 7 TRECHOS = 8       │
    └────────────────────────────────────────┘
```

---

## 📝 Tipos de Embeddings Gerados

| Tipo | Quando Criado | Conteúdo | orderChapter |
|------|---------------|----------|--------------|
| **RESUMO** | Chapter > 2500 tokens | Summary gerado via LLM (~1024 tokens) | -1 |
| **TRECHO** | Chapter > 2000 tokens | Chunk do capítulo (~512 tokens) | 0, 1, 2, ... |
| **TRECHO** | Chapter ≤ 2000 tokens | Capítulo completo (sem divisão) | 0 |
| **CAPITULO** | Chunk ≤ 512 tokens | Chunk pequeno (uso raro) | 1 |

---

## 🔄 Sequência de Operações

```
1. DocumentRouter.detectContentType(markdown)
   → TipoConteudo

2. SplitterFactory.createSplitter(tipoConteudo, library)
   → AbstractSplitter (SplitterNorma, SplitterWiki, ou SplitterGenerico)

3. splitter.splitDocumento(documentoDTO)
   → List<ChapterDTO>
   → CRIA CHAPTERS (entidades Chapter)

4. Para cada ChapterDTO:

   4.1. llmContext.tokenCount(chapter.conteudo)
        → int chapterTokens

   4.2. IF chapterTokens ≤ 2000:
          criarTrechoUnico()
          → 1 TRECHO (capítulo completo)

        ELSE:

          4.2.1. IF chapterTokens > 2500:
                   criarResumo() via LLM
                   → 1 RESUMO

          4.2.2. SplitterGenerico.splitChapterIntoChunks(chapterDTO)
                 → List<DocumentEmbeddingDTO>
                 → N TRECHOS (~512 tokens cada)

5. chapterRepository.saveAll(chapters)
   → Persiste Chapters no banco (gera IDs)

6. embeddingRepository.saveAll(embeddings)
   → Persiste DocEmbeddings com vectors=NULL

7. Para cada batch (até 10 embeddings):

   7.1. handleOversizedText() se necessário

   7.2. embeddingContext.generateEmbeddingsBatch(texts[])
        → List<float[]> (vetores 1536-dim)

   7.3. embeddingRepository.updateEmbeddingVector(id, vector)
        → Atualiza vetores no banco
```

---

## ✅ Conclusão

### ✔️ Uso de Splitters CONFIRMADO:

1. **AbstractSplitter** (linha 226, 242):
   - Divide **DOCUMENTO → CHAPTERS**
   - Tipo específico baseado em `TipoConteudo`
   - Retorna `List<ChapterDTO>`

2. **SplitterGenerico** (linha 370, 373):
   - Divide **CHAPTER → CHUNKS (DocEmbeddings)**
   - Apenas para capítulos grandes (> 2000 tokens)
   - Retorna `List<DocumentEmbeddingDTO>`

### 📊 Hierarquia Completa:

```
Documento (15k tokens)
  │
  ├─ AbstractSplitter.splitDocumento()
  │
  ├─► Chapter 1 (3750 tokens)
  │     │
  │     ├─ criarResumo() via LLM
  │     │   └─► DocEmbedding (RESUMO)
  │     │
  │     └─ SplitterGenerico.splitChapterIntoChunks()
  │         ├─► DocEmbedding (TRECHO 1)
  │         ├─► DocEmbedding (TRECHO 2)
  │         └─► ... (7 trechos total)
  │
  ├─► Chapter 2 (3750 tokens)
  │     └─► 1 RESUMO + 7 TRECHOS
  │
  ├─► Chapter 3 (1200 tokens) ← Pequeno!
  │     └─ criarTrechoUnico()
  │         └─► 1 TRECHO (capítulo completo)
  │
  └─► Chapter 4 (6300 tokens)
        └─► 1 RESUMO + 12 TRECHOS
```

**Total:** 4 Chapters, 30 DocEmbeddings (3 RESUMOS + 27 TRECHOS)

---

**Referências:**
- `DocumentProcessingService.java` (linhas 202-493)
- `SplitterGenerico.java` (linhas 259-289)
- `NEW_PROCESSING_FLOW_PROPOSAL.md`
