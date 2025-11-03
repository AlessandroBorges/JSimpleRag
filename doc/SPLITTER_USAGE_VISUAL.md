# Uso dos Splitters - Visualização Simplificada

## 🎯 Resposta Rápida

✅ **SIM**, o fluxo usa **2 tipos de Splitters**:

| Splitter | Divide | Cria | Localização |
|----------|--------|------|-------------|
| **AbstractSplitter** (específico) | Documento → Chapters | **Entities `Chapter`** | `splitAndPersist()` L226 |
| **SplitterGenerico** | Chapter → Chunks | **Entities `DocEmbedding`** | `createChapterEmbeddings()` L370 |

---

## 📊 Fluxo Visual Simplificado

```
                    ╔═══════════════════════════════╗
                    ║  DOCUMENTO (15,000 tokens)    ║
                    ║  "Manual Técnico.md"          ║
                    ╚═══════════════╤═══════════════╝
                                    │
                                    │ ① DocumentRouter.detectContentType()
                                    │    → TipoConteudo.LIVRO
                                    │
                                    ▼
                    ┌───────────────────────────────┐
                    │  SplitterFactory              │
                    │  .createSplitter(LIVRO)       │
                    └──────────────┬────────────────┘
                                   │
                                   ▼
                    ╔═══════════════════════════════╗
                    ║  AbstractSplitter             ║
                    ║  (SplitterGenerico)           ║
                    ║                               ║
                    ║  .splitDocumento()            ║
                    ╚═══════════════╤═══════════════╝
                                    │
                                    │ Analisa markdown
                                    │ Detecta títulos H1, H2
                                    │ Divide por seções
                                    │
                                    ▼
        ┌───────────────────────────────────────────────────┐
        │         List<ChapterDTO> (4 capítulos)            │
        └───┬───────────┬───────────┬────────────┬──────────┘
            │           │           │            │
            ▼           ▼           ▼            ▼
    ┏━━━━━━━━━┓ ┏━━━━━━━━━┓ ┏━━━━━━━━━┓ ┏━━━━━━━━━┓
    ┃Chapter 1┃ ┃Chapter 2┃ ┃Chapter 3┃ ┃Chapter 4┃
    ┃3750 tok ┃ ┃3750 tok ┃ ┃1200 tok ┃ ┃6300 tok ┃
    ┗━━━┯━━━━━┛ ┗━━━┯━━━━━┛ ┗━━━┯━━━━━┛ ┗━━━┯━━━━━┛
        │           │           │            │
        │           │           │            │
        │② createChapterEmbeddings()          │
        │   para cada capítulo               │
        │                                    │
        ▼                                    ▼
   ┌─────────────────────┐           ┌─────────────────────┐
   │ IF tokens > 2500:   │           │ IF tokens > 2500:   │
   │   criarResumo()     │           │   criarResumo()     │
   │   via LLM           │           │   via LLM           │
   └──────┬──────────────┘           └──────┬──────────────┘
          │                                  │
          ▼                                  ▼
      ╔═════════════════╗              ╔═════════════════╗
      ║ DocEmbedding    ║              ║ DocEmbedding    ║
      ║ tipo: RESUMO    ║              ║ tipo: RESUMO    ║
      ║ order: -1       ║              ║ order: -1       ║
      ║ vector: NULL    ║              ║ vector: NULL    ║
      ╚═════════════════╝              ╚═════════════════╝
          │                                  │
          ▼                                  ▼
   ┌─────────────────────┐           ┌─────────────────────┐
   │ IF tokens > 2000:   │           │ IF tokens > 2000:   │
   │ SplitterGenerico    │           │ SplitterGenerico    │
   │ .splitChapterInto   │           │ .splitChapterInto   │
   │    Chunks()         │           │    Chunks()         │
   └──────┬──────────────┘           └──────┬──────────────┘
          │                                  │
          │ Divide em chunks                │ Divide em chunks
          │ de ~512 tokens                  │ de ~512 tokens
          │                                  │
          ▼                                  ▼
   ┏━━━━━━━━━━━━━━━━━┓             ┏━━━━━━━━━━━━━━━━━┓
   ┃ 7 DocEmbeddings ┃             ┃ 12 DocEmbeddings┃
   ┃ tipo: TRECHO    ┃             ┃ tipo: TRECHO    ┃
   ┃ order: 0-6      ┃             ┃ order: 0-11     ┃
   ┃ vector: NULL    ┃             ┃ vector: NULL    ┃
   ┗━━━━━━━━━━━━━━━━━┛             ┗━━━━━━━━━━━━━━━━━┛


   Chapter 3 (1200 tokens - PEQUENO!)
          │
          │ ② createChapterEmbeddings()
          │    tokens ≤ 2000
          │
          ▼
   ┌─────────────────────┐
   │ criarTrechoUnico()  │
   │ (sem divisão)       │
   └──────┬──────────────┘
          │
          ▼
      ╔═════════════════╗
      ║ 1 DocEmbedding  ║
      ║ tipo: TRECHO    ║
      ║ texto: capítulo ║
      ║       completo  ║
      ║ order: 0        ║
      ║ vector: NULL    ║
      ╚═════════════════╝


═══════════════════════════════════════════════════════════
  RESULTADO FINAL:

  ✓ 4 Chapters (entities persistidas)
  ✓ 30 DocEmbeddings:
    • 3 RESUMOS (capítulos 1, 2, 4)
    • 27 TRECHOS (7+7+1+12)

  Todos com vectors=NULL → Calculados na ETAPA 2.3
═══════════════════════════════════════════════════════════
```

---

## 🔍 Detalhes dos Splitters

### 1️⃣ AbstractSplitter (Documento → Chapters)

**📍 Localização:** `DocumentProcessingService.splitAndPersist()` linha 226

```java
// 1. Detecta tipo de conteúdo
TipoConteudo tipo = documentRouter.detectContentType(markdown);

// 2. Cria splitter específico
AbstractSplitter splitter = splitterFactory.createSplitter(tipo, library);
//    → SplitterNorma   (para LEI, DECRETO, INSTRUCAO_NORMATIVA)
//    → SplitterWiki    (para WIKI)
//    → SplitterGenerico (para LIVRO, ARTIGO, MANUAL, OUTROS)

// 3. Divide em capítulos
List<ChapterDTO> chapters = splitter.splitDocumento(documentoDTO);
//    → Cria 4 ChapterDTO
```

**🎯 Critérios de Divisão:**

| Splitter | Critério | Exemplo |
|----------|----------|---------|
| **SplitterNorma** | Artigos, Seções, Capítulos de lei | Art. 1º, Seção II |
| **SplitterWiki** | Títulos H1 (`#`), H2 (`##`) | # Introdução<br>## Conceitos |
| **SplitterGenerico** | Títulos markdown ou tamanho | Detecta `##`, `###` ou divide por tokens |

**📏 Tamanhos:**
- Ideal: **8192 tokens** por chapter
- Mínimo: **4096 tokens**
- Máximo: **16384 tokens**

---

### 2️⃣ SplitterGenerico (Chapter → Chunks)

**📍 Localização:** `DocumentProcessingService.createChapterEmbeddings()` linha 370

```java
// Para capítulos GRANDES (> 2000 tokens):

// 1. Gera RESUMO se necessário
if (chapterTokens > 2500) {
    DocumentEmbedding resumo = criarResumo(chapterDTO, documento, llmContext);
    embeddings.add(resumo);
}

// 2. Divide capítulo em chunks
SplitterGenerico splitter = splitterFactory.createGenericSplitter(library);
List<DocumentEmbeddingDTO> chunks = splitter.splitChapterIntoChunks(chapterDTO);

// 3. Converte para entities
for (DocumentEmbeddingDTO chunk : chunks) {
    DocumentEmbedding emb = DocumentEmbedding.builder()
        .tipoEmbedding(TipoEmbedding.TRECHO)
        .texto(chunk.getTrechoTexto())
        .embeddingVector(null)  // Calculado depois
        .build();
    embeddings.add(emb);
}
```

**🎯 Lógica de Divisão:**

```
Chapter (3750 tokens)
    │
    ├─ Detecta títulos markdown (##, ###)
    │  Se encontrou: divide por títulos
    │  Se não: divide por tamanho
    │
    ├─ Cada chunk: ~512 tokens (ideal)
    │              máx 2048 tokens
    │              mín 300 tokens
    │
    └─► 7 chunks de ~536 tokens cada
```

**📏 Tamanhos:**
- Ideal: **512 tokens** por chunk
- Máximo: **2048 tokens**
- Mínimo: **300 tokens**

---

## 📊 Matriz de Decisão

```
                     ┌─────────────────────────────────┐
                     │  Chapter Tokens                 │
                     └────────┬────────────────────────┘
                              │
                  ┌───────────┴───────────┐
                  │                       │
            ≤ 2000 tokens           > 2000 tokens
                  │                       │
                  ▼                       ▼
         ┌────────────────┐      ┌────────────────┐
         │ criarTrecho    │      │ Dividir        │
         │ Unico()        │      │                │
         │                │      │ IF > 2500:     │
         │ 1 TRECHO       │      │   + RESUMO     │
         │ (completo)     │      │                │
         └────────────────┘      │ SEMPRE:        │
                                 │   + SplitterG. │
                                 │     .splitChap │
                                 │     terInto    │
                                 │     Chunks()   │
                                 │                │
                                 │ = RESUMO? +    │
                                 │   N TRECHOS    │
                                 └────────────────┘
```

---

## 🔢 Exemplo Numérico

### Documento: "Manual Técnico" (15,000 tokens)

```
Etapa 1: AbstractSplitter
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Input:  1 documento (15k tokens)
  Output: 4 chapters

  Chapter 1: 3,750 tokens  ┐
  Chapter 2: 3,750 tokens  │ → 4 × 3750 = 15,000 ✓
  Chapter 3: 1,200 tokens  │
  Chapter 4: 6,300 tokens  ┘


Etapa 2: SplitterGenerico (para cada chapter)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

  Chapter 1 (3,750 tokens)
  ├─ > 2500? SIM → criarResumo()
  │  └─ 1 RESUMO (~1024 tokens)
  └─ > 2000? SIM → splitChapterIntoChunks()
     └─ 7 TRECHOS (~536 tokens cada)
     = 8 embeddings total

  Chapter 2 (3,750 tokens)
  ├─ 1 RESUMO
  └─ 7 TRECHOS
  = 8 embeddings total

  Chapter 3 (1,200 tokens) ← PEQUENO!
  └─ ≤ 2000? SIM → criarTrechoUnico()
     └─ 1 TRECHO (1200 tokens)
     = 1 embedding total

  Chapter 4 (6,300 tokens)
  ├─ 1 RESUMO
  └─ 12 TRECHOS (~525 tokens cada)
  = 13 embeddings total


TOTAIS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  Chapters:       4
  RESUMOS:        3 (capítulos 1, 2, 4)
  TRECHOS:       27 (7 + 7 + 1 + 12)
  DocEmbeddings: 30 (3 + 27)
```

---

## 🎯 Código-Fonte: Onde Acontece

### AbstractSplitter - Criação de Chapters

**Arquivo:** `DocumentProcessingService.java`
**Método:** `splitAndPersist()`
**Linhas:** 216-304

```java
// Linha 222: Detecta tipo
TipoConteudo tipoConteudo = documentRouter.detectContentType(
    documento.getConteudoMarkdown()
);

// Linha 226: Cria splitter específico
AbstractSplitter splitter = splitterFactory.createSplitter(
    tipoConteudo,
    library
);

// Linha 242: DIVIDE DOCUMENTO → CHAPTERS
List<ChapterDTO> chapterDTOs = splitter.splitDocumento(documentoDTO);
log.debug("Document split into {} chapters", chapterDTOs.size());

// Linha 250-268: Para cada chapter, cria embeddings
for (ChapterDTO chapterDTO : chapterDTOs) {
    Chapter chapter = Chapter.builder()...build();
    chapters.add(chapter);

    // Linha 258: CRIA EMBEDDINGS PARA ESTE CHAPTER
    List<DocumentEmbedding> chapterEmbeddings =
        createChapterEmbeddings(chapter, chapterDTO, documento, library, llmContext);

    allEmbeddings.addAll(chapterEmbeddings);
}

// Linha 276: Persiste chapters
chapterRepository.saveAll(chapters);

// Linha 296: Persiste embeddings (vectors=NULL)
embeddingRepository.saveAll(allEmbeddings);
```

---

### SplitterGenerico - Criação de Chunks

**Arquivo:** `DocumentProcessingService.java`
**Método:** `createChapterEmbeddings()`
**Linhas:** 323-401

```java
// Linha 333: Conta tokens
int chapterTokens = llmContext.tokenCount(chapterDTO.getConteudo(), "fast");

// Linha 337: CASO 1 - Chapter pequeno
if (chapterTokens <= IDEAL_CHUNK_SIZE_TOKENS) {
    DocumentEmbedding trecho = criarTrechoUnico(chapterDTO, documento, 0);
    embeddings.add(trecho);
    return embeddings;
}

// Linha 352: CASO 2 - Gera RESUMO se necessário
if (chapterTokens > SUMMARY_THRESHOLD_TOKENS) {
    DocumentEmbedding resumo = criarResumo(chapterDTO, documento, llmContext);
    embeddings.add(resumo);
}

// Linha 370: DIVIDE CHAPTER → CHUNKS usando SplitterGenerico
SplitterGenerico splitter = splitterFactory.createGenericSplitter(library);

// Linha 373: AQUI ACONTECE A DIVISÃO!
List<DocumentEmbeddingDTO> chunkDTOs = splitter.splitChapterIntoChunks(chapterDTO);

log.debug("Chapter split into {} chunks", chunkDTOs.size());

// Linha 378-397: Converte DTOs em entities
int orderChapter = 0;
for (DocumentEmbeddingDTO chunkDTO : chunkDTOs) {
    DocumentEmbedding trecho = DocumentEmbedding.builder()
        .tipoEmbedding(TipoEmbedding.TRECHO)
        .texto(chunkDTO.getTrechoTexto())
        .orderChapter(orderChapter++)
        .embeddingVector(null)  // Calculado depois
        .build();
    embeddings.add(trecho);
}
```

---

**Arquivo:** `SplitterGenerico.java`
**Método:** `splitChapterIntoChunks()`
**Linhas:** 259-289

```java
public List<DocumentEmbeddingDTO> splitChapterIntoChunks(ChapterDTO chapter) {
    String conteudo = chapter.getConteudo();
    List<DocumentEmbeddingDTO> chunks = new ArrayList<>();

    // Linha 267: Conta tokens via LLM
    int tokenCount = getLlmServices().tokenCount(conteudo, "fast");

    // Linha 276: Se pequeno, não divide
    if (tokenCount <= IDEAL_TOKENS) {
        DocumentEmbeddingDTO chunk = DocumentEmbeddingDTO.builder()
            .tipoEmbedding(TipoEmbedding.CAPITULO)
            .trechoTexto(conteudo)
            .build();
        chunks.add(chunk);
        return chunks;
    }

    // Linha 289+: Divide por títulos detectados OU por tamanho
    // ... lógica de splitting ...

    return chunks;
}
```

---

## ✅ Resumo Executivo

### ✔️ Splitters Utilizados: 2

1. **AbstractSplitter** (específico do tipo de documento)
   - **Input:** Documento completo (~15k tokens)
   - **Output:** List\<ChapterDTO\> (4 chapters)
   - **Cria:** Entities `Chapter` no banco de dados
   - **Localização:** `splitAndPersist()` linha 226-242

2. **SplitterGenerico** (genérico para todos os chapters)
   - **Input:** ChapterDTO (1 chapter, ex: 3750 tokens)
   - **Output:** List\<DocumentEmbeddingDTO\> (N chunks)
   - **Cria:** Entities `DocumentEmbedding` (tipo TRECHO)
   - **Localização:** `createChapterEmbeddings()` linha 370-373

### 📊 Hierarquia Completa

```
Documento (entity)
  ↓ [AbstractSplitter]
Chapters (entities) ← CRIADOS AQUI
  ↓ [SplitterGenerico]
DocEmbeddings (entities) ← CRIADOS AQUI
  ↓ [EmbeddingContext]
DocEmbeddings (com vectors) ← VETORES CALCULADOS AQUI
```

---

**Documentos Relacionados:**
- [DOCUMENT_PROCESSING_FLOW_DIAGRAM.md](./DOCUMENT_PROCESSING_FLOW_DIAGRAM.md) - Diagrama completo
- [NEW_PROCESSING_FLOW_PROPOSAL.md](./NEW_PROCESSING_FLOW_PROPOSAL.md) - Especificação do fluxo
- [DOCUMENT_PROCESSING_TESTS.md](./DOCUMENT_PROCESSING_TESTS.md) - Testes implementados
