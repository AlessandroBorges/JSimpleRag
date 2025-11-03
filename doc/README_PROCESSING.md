# Documentação - Processamento de Documentos

**Última Atualização:** 2025-11-02

Este diretório contém a documentação completa sobre o novo fluxo de processamento de documentos (v1.1).

---

## 📚 Documentos Principais

### 1. 🎯 Especificação do Fluxo
- **[NEW_PROCESSING_FLOW_PROPOSAL.md](./NEW_PROCESSING_FLOW_PROPOSAL.md)**
  - Proposta completa do novo fluxo sequencial (v1.1)
  - Especificação técnica detalhada
  - Definição das etapas 2.1, 2.2 e 2.3
  - Critérios de decisão (quando dividir, quando resumir)

### 2. 📊 Diagramas Visuais

#### **[DOCUMENT_PROCESSING_FLOW_DIAGRAM.md](./DOCUMENT_PROCESSING_FLOW_DIAGRAM.md)** ⭐ NOVO
- **Diagrama completo** do fluxo de processamento
- Detalhamento de onde e como são criados Chapters e DocEmbeddings
- Uso dos **Splitters** (AbstractSplitter e SplitterGenerico)
- Exemplo prático com documento de 15k tokens
- Constantes e thresholds importantes
- Referências ao código-fonte (linha por linha)

#### **[SPLITTER_USAGE_VISUAL.md](./SPLITTER_USAGE_VISUAL.md)** ⭐ NOVO
- **Visualização simplificada** em ASCII art
- Resposta rápida: onde os Splitters são usados
- Matriz de decisão (quando dividir capítulos)
- Exemplo numérico completo
- Hierarquia: Documento → Chapters → DocEmbeddings

### 2.1 ⚙️ Configuração

#### **[CHUNK_SIZE_CONFIGURATION.md](./CHUNK_SIZE_CONFIGURATION.md)** ⭐ NOVO
- **Guia completo** de configuração de tamanho de chunks
- Onde configurar: `SplitterGenerico.java`
- Constantes: `IDEAL_TOKENS`, `MAX_TOKENS`, `MIN_TOKENS`
- Exemplos de configuração (256, 512, 1024 tokens)
- Impacto no custo e qualidade de busca
- Como testar alterações

#### **[CHUNK_CONFIG_QUICK_REFERENCE.md](./CHUNK_CONFIG_QUICK_REFERENCE.md)** ⭐ NOVO
- **Referência rápida** para configuração
- Mapa visual de constantes
- Fluxo de decisão simplificado
- Exemplos práticos de alteração
- Tabela de impacto

### 3. 🧪 Testes

#### **[DOCUMENT_PROCESSING_TESTS.md](./DOCUMENT_PROCESSING_TESTS.md)** ⭐ NOVO
- **46 testes JUnit** criados
- Cobertura completa: DocumentoService (18), DocumentController (24), Integration (4)
- Guia de execução
- Casos de uso cobertos
- Estrutura e padrões de testes

### 4. 📖 Fluxos e Implementações

#### **[Fluxo_carga_documents.md](./Fluxo_carga_documents.md)**
- Fluxo original de carga de documentos
- Upload via texto, URL e arquivo
- Etapas de processamento

#### **[DOCUMENT_LOADING_IMPLEMENTATION_COMPLETE.md](./DOCUMENT_LOADING_IMPLEMENTATION_COMPLETE.md)**
- Implementação completa do fluxo de carga
- Endpoints REST
- Validações e tratamento de erros

---

## 🎯 Por Onde Começar?

### Se você quer entender...

#### **...o novo fluxo de processamento:**
1. Leia: [NEW_PROCESSING_FLOW_PROPOSAL.md](./NEW_PROCESSING_FLOW_PROPOSAL.md)
2. Veja o diagrama: [DOCUMENT_PROCESSING_FLOW_DIAGRAM.md](./DOCUMENT_PROCESSING_FLOW_DIAGRAM.md)

#### **...como os Splitters são usados:**
1. Veja: [SPLITTER_USAGE_VISUAL.md](./SPLITTER_USAGE_VISUAL.md) ⭐
2. Confirme no código: `DocumentProcessingService.java` linhas 226, 370

#### **...os testes implementados:**
1. Leia: [DOCUMENT_PROCESSING_TESTS.md](./DOCUMENT_PROCESSING_TESTS.md) ⭐
2. Execute: `mvn test -Dtest=DocumentoServiceTest`

---

## 🔍 Perguntas Frequentes

### 0. **Onde configurar o tamanho dos chunks (DocEmbeddings)?** ⭐ NOVO

**Resposta:** Em **SplitterGenerico.java**, linha 39:

```java
private static final int IDEAL_TOKENS = 512;  // ← Tamanho ideal do chunk
```

**Atualmente configurado:** ~512 tokens por chunk

**Para alterar:**
1. Edite `src/main/java/bor/tools/splitter/SplitterGenerico.java`
2. Altere `IDEAL_TOKENS` para o valor desejado (ex: 256, 1024)
3. Recompile: `mvn clean compile`

**Documentação completa:**
- [CHUNK_SIZE_CONFIGURATION.md](./CHUNK_SIZE_CONFIGURATION.md) - Guia detalhado
- [CHUNK_CONFIG_QUICK_REFERENCE.md](./CHUNK_CONFIG_QUICK_REFERENCE.md) - Referência rápida

---

### 1. **Onde são criados os Chapters?**

**Resposta:** Na **ETAPA 2.2** (Split and Persist), linha 242 de `DocumentProcessingService.java`

```java
AbstractSplitter splitter = splitterFactory.createSplitter(tipoConteudo, library);
List<ChapterDTO> chapterDTOs = splitter.splitDocumento(documentoDTO);
// → CRIA CHAPTERS aqui
```

**Documentação:** [DOCUMENT_PROCESSING_FLOW_DIAGRAM.md](./DOCUMENT_PROCESSING_FLOW_DIAGRAM.md#1️⃣-abstractsplitter--criação-de-chapters)

---

### 2. **Onde são criados os DocEmbeddings?**

**Resposta:** Em **2 momentos** dentro do método `createChapterEmbeddings()`:

**A) Capítulo pequeno (≤ 2000 tokens):**
```java
// Linha 339
DocumentEmbedding trecho = criarTrechoUnico(chapterDTO, documento, 0);
// → Cria 1 TRECHO com capítulo completo
```

**B) Capítulo grande (> 2000 tokens):**
```java
// Linha 355: Opcional - RESUMO
if (chapterTokens > 2500) {
    DocumentEmbedding resumo = criarResumo(chapterDTO, documento, llmContext);
    // → Cria 1 RESUMO via LLM
}

// Linha 370-373: Obrigatório - TRECHOS
SplitterGenerico splitter = splitterFactory.createGenericSplitter(library);
List<DocumentEmbeddingDTO> chunkDTOs = splitter.splitChapterIntoChunks(chapterDTO);
// → Cria N TRECHOS (~512 tokens cada)
```

**Documentação:** [SPLITTER_USAGE_VISUAL.md](./SPLITTER_USAGE_VISUAL.md#2️⃣-splittergenerico--chapter--chunks)

---

### 3. **Qual a diferença entre AbstractSplitter e SplitterGenerico?**

| Aspecto | AbstractSplitter | SplitterGenerico |
|---------|------------------|------------------|
| **Input** | Documento completo | 1 Chapter |
| **Output** | List\<ChapterDTO\> | List\<DocumentEmbeddingDTO\> |
| **Cria** | **Chapters** (entities) | **DocEmbeddings** (entities) |
| **Tipos** | SplitterNorma, SplitterWiki, SplitterGenerico | Único (genérico) |
| **Critério** | Específico do tipo de documento | Por títulos ou tamanho |
| **Linha de código** | 226, 242 | 370, 373 |

**Documentação:** [SPLITTER_USAGE_VISUAL.md](./SPLITTER_USAGE_VISUAL.md#-detalhes-dos-splitters)

---

### 4. **Como funciona a hierarquia completa?**

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

TOTAL: 4 Chapters, 30 DocEmbeddings (3 RESUMOS + 27 TRECHOS)
```

**Documentação:** [DOCUMENT_PROCESSING_FLOW_DIAGRAM.md](./DOCUMENT_PROCESSING_FLOW_DIAGRAM.md#-exemplo-prático-documento-de-15k-tokens)

---

### 5. **Quais os thresholds importantes?**

| Threshold | Valor | Significado |
|-----------|-------|-------------|
| **IDEAL_CHUNK_SIZE_TOKENS** | 2000 | Se chapter ≤ 2000: não divide |
| **SUMMARY_THRESHOLD_TOKENS** | 2500 | Se chapter > 2500: gera RESUMO |
| **CHUNK_IDEAL_TOKENS** | 512 | Tamanho ideal de chunk |
| **CHUNK_MAX_TOKENS** | 2048 | Máximo permitido por chunk |
| **BATCH_SIZE** | 10 | Embeddings por batch |

**Documentação:** [DOCUMENT_PROCESSING_FLOW_DIAGRAM.md](./DOCUMENT_PROCESSING_FLOW_DIAGRAM.md#-constantes-importantes)

---

### 6. **Onde encontrar os testes?**

**Testes criados:**
- `src/test/java/bor/tools/simplerag/service/DocumentoServiceTest.java` (18 testes)
- `src/test/java/bor/tools/simplerag/controller/DocumentControllerTest.java` (24 testes)
- `src/test/java/bor/tools/simplerag/integration/DocumentProcessingIntegrationTest.java` (4 testes)

**Executar:**
```bash
# Todos os testes
mvn test

# Teste específico
mvn test -Dtest=DocumentoServiceTest#processDocumentAsyncV2_Success
```

**Documentação:** [DOCUMENT_PROCESSING_TESTS.md](./DOCUMENT_PROCESSING_TESTS.md)

---

## 🗂️ Estrutura de Arquivos

```
doc/
├── README_PROCESSING.md                          ← Você está aqui!
│
├── 📋 Especificações
│   ├── NEW_PROCESSING_FLOW_PROPOSAL.md           ⭐ Fluxo v1.1
│   └── Fluxo_carga_documents.md                  Fluxo original
│
├── 📊 Diagramas
│   ├── DOCUMENT_PROCESSING_FLOW_DIAGRAM.md       ⭐ Diagrama completo
│   └── SPLITTER_USAGE_VISUAL.md                  ⭐ Visualização simplificada
│
├── ⚙️ Configuração
│   ├── CHUNK_SIZE_CONFIGURATION.md               ⭐ Guia de configuração
│   └── CHUNK_CONFIG_QUICK_REFERENCE.md           ⭐ Referência rápida
│
├── 🧪 Testes
│   └── DOCUMENT_PROCESSING_TESTS.md              ⭐ 46 testes JUnit
│
└── 📖 Implementações
    ├── DOCUMENT_LOADING_IMPLEMENTATION_COMPLETE.md
    ├── EMBEDDING_SERVICE_IMPLEMENTATION_COMPLETE.md
    └── ... (outros docs)
```

---

## 🔗 Links Rápidos

### Código-Fonte Principal

- **DocumentProcessingService.java**
  - ETAPA 2.1: `processDocument()` linha 119
  - ETAPA 2.2: `splitAndPersist()` linha 216
  - ETAPA 2.3: `calculateAndUpdateEmbeddings()` linha 517

- **SplitterGenerico.java**
  - `splitChapterIntoChunks()` linha 259

- **DocumentoService.java**
  - `processDocumentAsyncV2()` - novo método
  - `enrichDocumentAsync()` - Phase 2

### Testes

- **DocumentoServiceTest.java** (18 testes)
- **DocumentControllerTest.java** (24 testes)
- **DocumentProcessingIntegrationTest.java** (4 testes)

---

## 📝 Changelog

### 2025-11-02 - v1.2 ⭐ NOVO
- ✅ Criado **CHUNK_SIZE_CONFIGURATION.md** - Guia completo de configuração
- ✅ Criado **CHUNK_CONFIG_QUICK_REFERENCE.md** - Referência rápida
- ✅ Documentado como configurar tamanho dos chunks (512 tokens padrão)
- ✅ Exemplos de configuração alternativas (256, 1024 tokens)
- ✅ Tabelas de impacto (custo vs precisão)

### 2025-11-02 - v1.1
- ✅ Criado **DOCUMENT_PROCESSING_FLOW_DIAGRAM.md**
- ✅ Criado **SPLITTER_USAGE_VISUAL.md**
- ✅ Criado **DOCUMENT_PROCESSING_TESTS.md**
- ✅ Criado **README_PROCESSING.md** (este arquivo)
- ✅ **46 testes JUnit** implementados e compilando
- ✅ Diagrama completo do uso dos Splitters

### 2025-XX-XX - v1.0
- Implementação inicial do novo fluxo
- NEW_PROCESSING_FLOW_PROPOSAL.md
- DocumentProcessingService.java

---

**Mantido por:** Claude Code
**Última Revisão:** 2025-11-02
