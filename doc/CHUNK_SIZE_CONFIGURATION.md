# Configuração de Tamanho de Chunks (DocEmbeddings)

**Data:** 2025-11-02
**Versão:** 1.1
**Status:** ✅ Documentado

---

## 🎯 Resposta Rápida

Para criar **DocEmbeddings com 512 tokens**, a configuração já está correta:

**Arquivo:** `SplitterGenerico.java`
**Constante:** `IDEAL_TOKENS = 512` (linha 39)

✅ **O sistema já está configurado para gerar chunks de ~512 tokens!**

---

## 📋 Índice de Constantes

### 🔧 SplitterGenerico.java (Criação de Chunks)

| Constante | Valor | Uso | Linha |
|-----------|-------|-----|-------|
| **IDEAL_TOKENS** | 512 | Tamanho ideal do chunk | 39 |
| **CHUNK_IDEAL_TOKENS** | 512 | Alias de IDEAL_TOKENS | 73 |
| **MIN_TOKENS** | 300 | Tamanho mínimo do chunk | 49 |
| **MAX_TOKENS** | 2048 | Tamanho máximo do chunk | 44 |
| **CHUNK_MAX_TOKENS** | 2048 | Alias de MAX_TOKENS | 79 |

### 📏 DocumentProcessingService.java (Decisão de Dividir Chapter)

| Constante | Valor | Uso | Linha |
|-----------|-------|-----|-------|
| **IDEAL_CHUNK_SIZE_TOKENS** | 2000 | Threshold para dividir chapter | 108 |
| **SUMMARY_THRESHOLD_TOKENS** | 2500 | Threshold para gerar resumo | 98 |

---

## 🔍 Como Funciona o Tamanho dos Chunks?

### Hierarquia de Configurações

```
DocumentProcessingService.IDEAL_CHUNK_SIZE_TOKENS = 2000
    │
    │ Decide SE o chapter será dividido
    │
    ├─ Chapter ≤ 2000 tokens → NÃO divide (1 TRECHO único)
    │
    └─ Chapter > 2000 tokens → Divide usando SplitterGenerico
                                    ↓
                    SplitterGenerico.IDEAL_TOKENS = 512
                            ↓
                    Cria chunks de ~512 tokens
```

### Exemplo Prático

```
Chapter com 3750 tokens
    │
    ├─ > 2000? SIM → Chama SplitterGenerico.splitChapterIntoChunks()
    │
    └─► SplitterGenerico divide em:
        • Chunk 1: ~512 tokens
        • Chunk 2: ~512 tokens
        • Chunk 3: ~512 tokens
        • Chunk 4: ~512 tokens
        • Chunk 5: ~512 tokens
        • Chunk 6: ~512 tokens
        • Chunk 7: ~534 tokens (resto)
        ════════════════════════
        TOTAL: 7 chunks (~536 tokens média)
```

---

## 📊 Código-Fonte: Onde Está?

### 1️⃣ SplitterGenerico.java - Definição das Constantes

**Arquivo:** `src/main/java/bor/tools/splitter/SplitterGenerico.java`

```java
public class SplitterGenerico extends AbstractSplitter {

    /**
     * Chunk ideal de tokens.
     */
    private static final int IDEAL_TOKENS = 512;  // ← AQUI! Tamanho ideal

    /**
     * Número máximo de tokens em um chunk.
     */
    private static final int MAX_TOKENS = 2048;  // ← Máximo permitido

    /**
     * Número mínimo de tokens em um chunk.
     */
    private static final int MIN_TOKENS = 300;   // ← Mínimo permitido

    // Aliases (mesmos valores)
    protected static final int CHUNK_IDEAL_TOKENS = 512;
    protected static final int CHUNK_MAX_TOKENS = 2048;
}
```

**Linhas:** 36-79

---

### 2️⃣ SplitterGenerico.splitChapterIntoChunks() - Uso das Constantes

**Arquivo:** `src/main/java/bor/tools/splitter/SplitterGenerico.java`
**Método:** `splitChapterIntoChunks(ChapterDTO chapter)`
**Linha:** 259

```java
public List<DocumentEmbeddingDTO> splitChapterIntoChunks(ChapterDTO chapter) {
    String conteudo = chapter.getConteudo();
    List<DocumentEmbeddingDTO> chunks = new ArrayList<>();

    // Linha 267: Conta tokens via LLM
    int tokenCount = getLlmServices().tokenCount(conteudo, "fast");

    // Linha 276: Se pequeno, não divide
    if (tokenCount <= IDEAL_TOKENS) {  // ← Usa IDEAL_TOKENS = 512
        DocumentEmbeddingDTO chunk = DocumentEmbeddingDTO.builder()
            .tipoEmbedding(TipoEmbedding.CAPITULO)
            .trechoTexto(conteudo)
            .build();
        chunks.add(chunk);
        return chunks;
    }

    // Linha 289+: Divide em chunks de ~512 tokens
    // Detecta títulos markdown (##, ###) ou divide por tamanho
    // Cada chunk terá aproximadamente IDEAL_TOKENS = 512 tokens

    // Linha 313: Calcula tamanho ideal em caracteres
    int idealChunkSize = IDEAL_TOKENS * 4;  // ← ~2048 caracteres

    // ... lógica de splitting ...

    return chunks;
}
```

---

### 3️⃣ DocumentProcessingService - Threshold para Dividir

**Arquivo:** `src/main/java/bor/tools/simplerag/service/processing/DocumentProcessingService.java`
**Método:** `createChapterEmbeddings()`
**Linha:** 323

```java
private List<DocumentEmbedding> createChapterEmbeddings(
        Chapter chapter,
        ChapterDTO chapterDTO,
        Documento documento,
        LibraryDTO library,
        LLMContext llmContext) throws Exception {

    // Linha 333: Conta tokens no chapter
    int chapterTokens = llmContext.tokenCount(chapterDTO.getConteudo(), "fast");

    // Linha 336: DECISÃO - Dividir ou não?
    if (chapterTokens <= IDEAL_CHUNK_SIZE_TOKENS) {  // ← 2000 tokens
        // NÃO divide - cria 1 TRECHO único
        DocumentEmbedding trecho = criarTrechoUnico(chapterDTO, documento, 0);
        embeddings.add(trecho);
        return embeddings;
    }

    // Linha 368-373: SIM, divide usando SplitterGenerico
    SplitterGenerico splitter = splitterFactory.createGenericSplitter(library);
    List<DocumentEmbeddingDTO> chunkDTOs = splitter.splitChapterIntoChunks(chapterDTO);
    //                                      ↑
    //                          Aqui dentro usa IDEAL_TOKENS = 512
}
```

---

## 🎨 Diagrama de Fluxo

```
┌─────────────────────────────────────────────────────────────┐
│  Chapter (3750 tokens)                                      │
└────────────────┬────────────────────────────────────────────┘
                 │
                 ▼
    ┌────────────────────────────────────────────────┐
    │  DocumentProcessingService                     │
    │  createChapterEmbeddings()                     │
    │                                                │
    │  chapterTokens = 3750                          │
    │                                                │
    │  IF chapterTokens ≤ IDEAL_CHUNK_SIZE_TOKENS    │
    │     (≤ 2000)                                   │
    │     → criarTrechoUnico() (NÃO divide)          │
    │                                                │
    │  ELSE (> 2000)                                 │
    │     → SplitterGenerico.splitChapterIntoChunks()│
    └────────────────┬───────────────────────────────┘
                     │
                     ▼
    ┌────────────────────────────────────────────────┐
    │  SplitterGenerico.splitChapterIntoChunks()     │
    │                                                │
    │  tokenCount = 3750                             │
    │                                                │
    │  IF tokenCount ≤ IDEAL_TOKENS (≤ 512)          │
    │     → Retorna 1 chunk (não divide)             │
    │                                                │
    │  ELSE (> 512)                                  │
    │     → Divide em chunks de ~IDEAL_TOKENS        │
    │                                                │
    │  idealChunkSize = IDEAL_TOKENS × 4             │
    │                 = 512 × 4                      │
    │                 = 2048 caracteres              │
    │                                                │
    │  Divide texto em chunks de ~2048 chars         │
    │  (equivalente a ~512 tokens)                   │
    └────────────────┬───────────────────────────────┘
                     │
                     ▼
    ┌────────────────────────────────────────────────┐
    │  List<DocumentEmbeddingDTO>                    │
    │                                                │
    │  • Chunk 1: ~512 tokens                        │
    │  • Chunk 2: ~512 tokens                        │
    │  • Chunk 3: ~512 tokens                        │
    │  • Chunk 4: ~512 tokens                        │
    │  • Chunk 5: ~512 tokens                        │
    │  • Chunk 6: ~512 tokens                        │
    │  • Chunk 7: ~534 tokens (resto)                │
    │                                                │
    │  TOTAL: 7 chunks (~536 tokens média)           │
    └────────────────────────────────────────────────┘
```

---

## ⚙️ Como Alterar o Tamanho dos Chunks?

### Cenário 1: Quero chunks MENORES (ex: 256 tokens)

**Arquivo:** `SplitterGenerico.java`

```java
// ANTES:
private static final int IDEAL_TOKENS = 512;

// DEPOIS:
private static final int IDEAL_TOKENS = 256;  // ← Altere aqui
```

**Impacto:**
- Documento de 3750 tokens → ~15 chunks de 256 tokens (em vez de 7)
- Mais chunks = mais embeddings = mais custo de armazenamento
- Busca mais granular (pode ser melhor para perguntas específicas)

---

### Cenário 2: Quero chunks MAIORES (ex: 1024 tokens)

**Arquivo:** `SplitterGenerico.java`

```java
// ANTES:
private static final int IDEAL_TOKENS = 512;

// DEPOIS:
private static final int IDEAL_TOKENS = 1024;  // ← Altere aqui
```

**Impacto:**
- Documento de 3750 tokens → ~4 chunks de 1024 tokens (em vez de 7)
- Menos chunks = menos embeddings = menos custo
- Busca menos granular (pode perder precisão)

**⚠️ CUIDADO:** Respeite o `MAX_TOKENS = 2048` (limite máximo)

---

### Cenário 3: Alterar quando dividir Chapter

**Arquivo:** `DocumentProcessingService.java`

```java
// ANTES:
private static final int IDEAL_CHUNK_SIZE_TOKENS = 2000;

// DEPOIS:
private static final int IDEAL_CHUNK_SIZE_TOKENS = 1000;  // ← Mais agressivo
```

**Impacto:**
- Chapters pequenos (≤ 1000 tokens) não serão divididos
- Chapters maiores (> 1000 tokens) serão divididos
- Mais chapters serão divididos em chunks

---

## 📊 Comparação de Configurações

### Configuração Atual (Padrão)

```
IDEAL_CHUNK_SIZE_TOKENS = 2000 (threshold para dividir chapter)
IDEAL_TOKENS = 512 (tamanho do chunk)
MAX_TOKENS = 2048 (máximo permitido)
MIN_TOKENS = 300 (mínimo permitido)
```

**Resultado para documento de 15k tokens:**
- 4 Chapters
- Chapters grandes divididos em chunks de ~512 tokens
- Total: ~27 TRECHOS

---

### Configuração Alternativa: Chunks Pequenos (256 tokens)

```
IDEAL_CHUNK_SIZE_TOKENS = 2000
IDEAL_TOKENS = 256  ← ALTERADO
MAX_TOKENS = 2048
MIN_TOKENS = 150
```

**Resultado para documento de 15k tokens:**
- 4 Chapters
- Chapters grandes divididos em chunks de ~256 tokens
- Total: ~54 TRECHOS (dobro!)

**Prós:**
- ✅ Busca mais granular e precisa
- ✅ Melhor para perguntas muito específicas

**Contras:**
- ❌ Mais embeddings = mais custo de armazenamento
- ❌ Mais chamadas à API de embedding
- ❌ Contexto menor por chunk (pode perder informação)

---

### Configuração Alternativa: Chunks Grandes (1024 tokens)

```
IDEAL_CHUNK_SIZE_TOKENS = 2000
IDEAL_TOKENS = 1024  ← ALTERADO
MAX_TOKENS = 2048
MIN_TOKENS = 500
```

**Resultado para documento de 15k tokens:**
- 4 Chapters
- Chapters grandes divididos em chunks de ~1024 tokens
- Total: ~13 TRECHOS (menos da metade!)

**Prós:**
- ✅ Menos embeddings = menos custo
- ✅ Mais contexto por chunk

**Contras:**
- ❌ Busca menos granular
- ❌ Pode retornar chunks muito grandes

---

## 🎯 Recomendações

### Para a maioria dos casos (atual):
```java
IDEAL_TOKENS = 512
MAX_TOKENS = 2048
MIN_TOKENS = 300
```

**Razão:** Equilíbrio entre precisão e custo.

---

### Para documentos técnicos/legais (alta precisão):
```java
IDEAL_TOKENS = 256
MAX_TOKENS = 1024
MIN_TOKENS = 150
```

**Razão:** Chunks menores capturam melhor definições e conceitos específicos.

---

### Para documentos narrativos/gerais (baixo custo):
```java
IDEAL_TOKENS = 1024
MAX_TOKENS = 2048
MIN_TOKENS = 500
```

**Razão:** Chunks maiores mantêm mais contexto e reduzem custos.

---

## 📝 Exemplo Real: Alterando para 256 tokens

### 1. Editar SplitterGenerico.java

```java
public class SplitterGenerico extends AbstractSplitter {

    // ALTERAÇÃO:
    private static final int IDEAL_TOKENS = 256;  // ← Era 512
    private static final int MAX_TOKENS = 1024;   // ← Era 2048 (ajuste proporcional)
    private static final int MIN_TOKENS = 150;    // ← Era 300

    // Aliases (atualize também)
    protected static final int CHUNK_IDEAL_TOKENS = 256;
    protected static final int CHUNK_MAX_TOKENS = 1024;
}
```

### 2. Recompilar

```bash
mvn clean compile
```

### 3. Testar

```bash
# Upload de documento
curl -X POST http://localhost:8080/api/v1/documents/upload/text \
  -H "Content-Type: application/json" \
  -d '{
    "titulo": "Teste Chunks Pequenos",
    "conteudo": "... conteúdo de teste ...",
    "libraryId": 1
  }'

# Processar
curl -X POST http://localhost:8080/api/v1/documents/123/process

# Verificar chunks criados (devem ter ~256 tokens)
```

---

## 🔍 Verificação: Chunks estão corretos?

### Query SQL para verificar tamanho dos chunks

```sql
-- Conta caracteres e estima tokens
SELECT
    id,
    tipo_embedding,
    LENGTH(texto) as chars,
    LENGTH(texto) / 4 as estimated_tokens,
    order_chapter
FROM doc_embedding
WHERE documento_id = 123
ORDER BY order_chapter;
```

**Esperado para IDEAL_TOKENS = 512:**
- chars: ~2048 (512 × 4)
- estimated_tokens: ~512

---

## ⚠️ Considerações Importantes

### 1. Não confundir as constantes!

| Constante | Classe | Usa Para |
|-----------|--------|----------|
| `IDEAL_CHUNK_SIZE_TOKENS` | DocumentProcessingService | Decidir SE divide chapter |
| `IDEAL_TOKENS` | SplitterGenerico | Definir TAMANHO do chunk |

**Diferentes propósitos!**

---

### 2. Impacto no custo

**Custo de embedding é proporcional ao número de chunks:**

| Config | Chunks/Doc | Custo Relativo |
|--------|------------|----------------|
| 256 tokens | ~60 | 2× |
| 512 tokens | ~30 | 1× (baseline) |
| 1024 tokens | ~15 | 0.5× |

---

### 3. Impacto na qualidade de busca

**Chunks menores = busca mais precisa**
**Chunks maiores = mais contexto**

**Teste e ajuste conforme necessário!**

---

## 📚 Documentos Relacionados

- [DOCUMENT_PROCESSING_FLOW_DIAGRAM.md](./DOCUMENT_PROCESSING_FLOW_DIAGRAM.md) - Fluxo completo
- [SPLITTER_USAGE_VISUAL.md](./SPLITTER_USAGE_VISUAL.md) - Uso dos Splitters
- [NEW_PROCESSING_FLOW_PROPOSAL.md](./NEW_PROCESSING_FLOW_PROPOSAL.md) - Especificação

---

## ✅ Resumo

### Onde configurar tamanho dos chunks?

**📍 Arquivo:** `SplitterGenerico.java` (linha 39)
**🔧 Constante:** `IDEAL_TOKENS = 512`

### Configuração atual está correta?

✅ **SIM!** O sistema já está configurado para gerar chunks de ~512 tokens.

### Precisa alterar?

Apenas se quiser:
- **Chunks menores:** `IDEAL_TOKENS = 256` (mais precisão, mais custo)
- **Chunks maiores:** `IDEAL_TOKENS = 1024` (menos custo, menos precisão)

---

**Última atualização:** 2025-11-02
**Mantido por:** Claude Code
