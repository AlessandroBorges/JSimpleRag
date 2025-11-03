# Configuração de Chunks - Referência Rápida

**Última Atualização:** 2025-11-02

---

## 🎯 TL;DR - Onde Configurar?

### Para alterar tamanho dos chunks (DocEmbeddings):

**📁 Arquivo:** `src/main/java/bor/tools/splitter/SplitterGenerico.java`

```java
// Linha 39 - TAMANHO IDEAL DO CHUNK
private static final int IDEAL_TOKENS = 512;  // ← ALTERE AQUI

// Linha 44 - TAMANHO MÁXIMO
private static final int MAX_TOKENS = 2048;

// Linha 49 - TAMANHO MÍNIMO
private static final int MIN_TOKENS = 300;
```

**✅ Atualmente configurado para ~512 tokens por chunk**

---

## 📊 Mapa de Constantes

```
┌─────────────────────────────────────────────────────────────┐
│                    SplitterGenerico.java                    │
│                                                             │
│  ┌───────────────────────────────────────────────────┐     │
│  │  CONSTANTES PARA CHUNKS (DocEmbeddings)          │     │
│  │                                                   │     │
│  │  IDEAL_TOKENS = 512      ← Tamanho ideal         │     │
│  │  MAX_TOKENS = 2048       ← Máximo permitido      │     │
│  │  MIN_TOKENS = 300        ← Mínimo permitido      │     │
│  │                                                   │     │
│  │  CHUNK_IDEAL_TOKENS = 512  (alias)               │     │
│  │  CHUNK_MAX_TOKENS = 2048   (alias)               │     │
│  └───────────────────────────────────────────────────┘     │
│                                                             │
│  ┌───────────────────────────────────────────────────┐     │
│  │  CONSTANTES PARA CHAPTERS                        │     │
│  │  (não altere estas - são para chapters!)         │     │
│  │                                                   │     │
│  │  CHAPTER_IDEAL_TOKENS = 8192                     │     │
│  │  CHAPTER_MIN_TOKENS = 4096                       │     │
│  │  CHAPTER_MAX_TOKENS = 16384                      │     │
│  └───────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│              DocumentProcessingService.java                 │
│                                                             │
│  ┌───────────────────────────────────────────────────┐     │
│  │  THRESHOLD PARA DECIDIR SE DIVIDE CHAPTER        │     │
│  │  (não confundir com tamanho do chunk!)           │     │
│  │                                                   │     │
│  │  IDEAL_CHUNK_SIZE_TOKENS = 2000                  │     │
│  │  ↑                                                │     │
│  │  Se chapter ≤ 2000: NÃO divide (1 TRECHO único)  │     │
│  │  Se chapter > 2000: Divide usando SplitterGen.   │     │
│  └───────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔄 Fluxo de Decisão

```
Chapter (3750 tokens)
    ↓
    ┌─────────────────────────────────────────┐
    │ DocumentProcessingService               │
    │                                         │
    │ IF chapterTokens ≤ 2000                 │ ← IDEAL_CHUNK_SIZE_TOKENS
    │    → criarTrechoUnico() (NÃO divide)    │
    │ ELSE                                    │
    │    → SplitterGenerico.splitChapter...() │
    └─────────────────┬───────────────────────┘
                      │
                      ▼
    ┌─────────────────────────────────────────┐
    │ SplitterGenerico                        │
    │                                         │
    │ IF tokenCount ≤ 512                     │ ← IDEAL_TOKENS
    │    → 1 chunk (não divide)               │
    │ ELSE                                    │
    │    → Divide em chunks de ~512 tokens    │ ← IDEAL_TOKENS
    │                                         │
    │ Cada chunk:                             │
    │   • Mínimo: 300 tokens                  │ ← MIN_TOKENS
    │   • Ideal: 512 tokens                   │ ← IDEAL_TOKENS
    │   • Máximo: 2048 tokens                 │ ← MAX_TOKENS
    └─────────────────────────────────────────┘
                      ↓
         7 chunks de ~512 tokens cada
```

---

## 📝 Exemplos de Configuração

### ✅ Padrão Atual (Recomendado)

```java
// SplitterGenerico.java
IDEAL_TOKENS = 512
MAX_TOKENS = 2048
MIN_TOKENS = 300
```

**Resultado:** ~7 chunks para chapter de 3750 tokens

---

### 🔹 Chunks Pequenos (Alta Precisão)

```java
// SplitterGenerico.java
IDEAL_TOKENS = 256   // ← ALTERE
MAX_TOKENS = 1024    // ← ALTERE (proporcional)
MIN_TOKENS = 150     // ← ALTERE (proporcional)
```

**Resultado:** ~15 chunks para chapter de 3750 tokens

**Quando usar:**
- Documentos legais/técnicos
- Busca muito precisa
- Definições e conceitos específicos

---

### 🔸 Chunks Grandes (Baixo Custo)

```java
// SplitterGenerico.java
IDEAL_TOKENS = 1024  // ← ALTERE
MAX_TOKENS = 2048
MIN_TOKENS = 500     // ← ALTERE (proporcional)
```

**Resultado:** ~4 chunks para chapter de 3750 tokens

**Quando usar:**
- Documentos narrativos/gerais
- Reduzir custos de armazenamento
- Manter mais contexto

---

## ⚠️ Não Confunda!

| Constante | Arquivo | Propósito |
|-----------|---------|-----------|
| **IDEAL_CHUNK_SIZE_TOKENS** = 2000 | DocumentProcessingService | Decidir **SE** divide chapter |
| **IDEAL_TOKENS** = 512 | SplitterGenerico | **TAMANHO** do chunk |

**São diferentes!**

---

## 🛠️ Como Alterar (Passo a Passo)

### 1. Edite o arquivo

```bash
vim src/main/java/bor/tools/splitter/SplitterGenerico.java
```

### 2. Localize as constantes (linha 36-49)

```java
public class SplitterGenerico extends AbstractSplitter {

    // ← ALTERE AQUI
    private static final int IDEAL_TOKENS = 512;
    private static final int MAX_TOKENS = 2048;
    private static final int MIN_TOKENS = 300;

    // Aliases (altere também)
    protected static final int CHUNK_IDEAL_TOKENS = 512;
    protected static final int CHUNK_MAX_TOKENS = 2048;
}
```

### 3. Recompile

```bash
mvn clean compile
```

### 4. Reinicie a aplicação

```bash
mvn spring-boot:run
```

---

## 🧪 Como Testar

### 1. Upload de documento

```bash
curl -X POST http://localhost:8080/api/v1/documents/upload/text \
  -H "Content-Type: application/json" \
  -d '{
    "titulo": "Teste Chunks",
    "conteudo": "...",
    "libraryId": 1
  }'
```

### 2. Processar

```bash
curl -X POST http://localhost:8080/api/v1/documents/123/process
```

### 3. Verificar chunks no banco

```sql
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

**Esperado (IDEAL_TOKENS = 512):**
- chars: ~2048
- estimated_tokens: ~512

---

## 📊 Tabela de Impacto

| Config | Chunks/Chapter (3750 tokens) | Custo Relativo | Precisão |
|--------|------------------------------|----------------|----------|
| 256 tokens | ~15 | 2× | ⭐⭐⭐⭐⭐ |
| **512 tokens** | **~7** | **1×** | **⭐⭐⭐⭐** |
| 1024 tokens | ~4 | 0.5× | ⭐⭐⭐ |

---

## 📚 Links Úteis

- **Documentação completa:** [CHUNK_SIZE_CONFIGURATION.md](./CHUNK_SIZE_CONFIGURATION.md)
- **Fluxo de processamento:** [DOCUMENT_PROCESSING_FLOW_DIAGRAM.md](./DOCUMENT_PROCESSING_FLOW_DIAGRAM.md)
- **Uso dos Splitters:** [SPLITTER_USAGE_VISUAL.md](./SPLITTER_USAGE_VISUAL.md)

---

## ✅ Checklist

Antes de alterar:

- [ ] Entendi a diferença entre `IDEAL_CHUNK_SIZE_TOKENS` (threshold) e `IDEAL_TOKENS` (tamanho)
- [ ] Decidi o novo tamanho baseado no tipo de documento
- [ ] Alterei `IDEAL_TOKENS` em `SplitterGenerico.java`
- [ ] Alterei `MAX_TOKENS` e `MIN_TOKENS` proporcionalmente
- [ ] Recompilei: `mvn clean compile`
- [ ] Testei com documento real
- [ ] Verifiquei chunks no banco de dados

---

**Mantido por:** Claude Code
**Última revisão:** 2025-11-02
