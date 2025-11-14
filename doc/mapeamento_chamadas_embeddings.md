# Mapeamento de Chamadas para Embeddings no JSimpleRag

## 📖 Índice
1. [Introdução](#introdução)
2. [Visão Geral da Arquitetura](#visão-geral-da-arquitetura)
3. [Todos os Locais de Chamada (Produção)](#todos-os-locais-de-chamada-produção)
4. [Estratégias de Montagem de Texto](#estratégias-de-montagem-de-texto)
5. [Processamento em Lote](#processamento-em-lote)
6. [Camada de Gerenciamento LLM](#camada-de-gerenciamento-llm)
7. [Aliases e Variáveis Especiais](#aliases-e-variáveis-especiais)
8. [Operações Suportadas (Emb_Operation)](#operações-suportadas-emb_operation)
9. [Referências Cruzadas](#referências-cruzadas)

---

## Introdução

Este documento mapeia **todos os locais** onde a função `embeddings()` é chamada no JSimpleRag para gerar vetores de embeddings. O objetivo é facilitar a compreensão do fluxo de dados: desde a coleta de texto (documentos, capítulos, metadados) até a chamada final ao `LLMProvider.embeddings()`.

### Contexto
- **Sistema**: JSimpleRag (Hierarchical RAG com PostgreSQL + PGVector)
- **Total de Locais de Chamada**: 11 em produção
- **Padrão de Chamada**: `.embeddings(operation, text, params)`
- **Provedor Base**: JSimpleLLM (`bor.tools.simplellm.LLMProvider`)

---

## Visão Geral da Arquitetura

### Fluxo em Camadas

```
┌────────────────────────────────────────────────────────────────┐
│ ENTRADA DE DADOS                                               │
│ ├─ Documento.conteudo_markdown                                │
│ ├─ Chapter.conteudo                                           │
│ ├─ Chapter.metadados (JSONB)                                  │
│ └─ SearchRequest.query                                        │
└───────────────────────┬────────────────────────────────────────┘
                        ↓
┌────────────────────────────────────────────────────────────────┐
│ CAMADA 1: ESTRATÉGIAS DE EMBEDDING                             │
│ (Strategy Pattern)                                             │
│ ├─ ChapterEmbeddingStrategy        (5 locais)                │
│ ├─ QAEmbeddingStrategy                                        │
│ ├─ SummaryEmbeddingStrategy                                   │
│ ├─ QueryEmbeddingStrategy           (2 locais)               │
│ └─ EmbeddingServiceImpl              (1 local)                │
└───────────────────────┬────────────────────────────────────────┘
                        ↓
┌────────────────────────────────────────────────────────────────┐
│ CAMADA 2: CONTEXTO E ORQUESTRAÇÃO                              │
│ ├─ EmbeddingContext (processing/context) (2 locais)          │
│ └─ EmbeddingContext (embedding/model)    (1 local)           │
│    - Agrupa até 10 textos em batch                           │
│    - Aplica filtros de metadados                             │
└───────────────────────┬────────────────────────────────────────┘
                        ↓
┌────────────────────────────────────────────────────────────────┐
│ CAMADA 3: GERENCIAMENTO E ROTEAMENTO                           │
│ ├─ LLMServiceManager                (3 locais)               │
│   - Failover automático entre provedores                     │
│   - Round-robin load balancing                               │
│   - Model-based routing                                      │
│   - Dual verification                                        │
└───────────────────────┬────────────────────────────────────────┘
                        ↓
┌────────────────────────────────────────────────────────────────┐
│ INTERFACE FINAL: LLMProvider (JSimpleLLM)                      │
│ → embeddings(operation, text, params)                          │
│   ├─ Suporta OpenAI, Ollama, LM Studio, etc.                 │
│   ├─ Dimensões: 768 ou 1536 (configurável)                   │
│   └─ Retorna: float[] com o vetor de embedding               │
└────────────────────────────────────────────────────────────────┘
```

---

## Todos os Locais de Chamada (Produção)

### Resumo Executivo
| Categoria | Arquivo | Linha | Variável | Tipo |
|-----------|---------|-------|----------|------|
| **Estratégias** | ChapterEmbeddingStrategy.java | 317 | `llmService` | DOCUMENT |
| | QAEmbeddingStrategy.java | 295 | `llmService` | DOCUMENT |
| | SummaryEmbeddingStrategy.java | 217 | `llmService` | DOCUMENT |
| | QueryEmbeddingStrategy.java | 82 | `llmService` | QUERY |
| | QueryEmbeddingStrategy.java | 144 | `llmService` | QUERY |
| | EmbeddingServiceImpl.java | 301 | `llmService` | Dinâmica |
| **Contexto** | EmbeddingContext.java (context) | 165 | `llmService` | Batch-single |
| | EmbeddingContext.java (context) | 200 | `llmService` | Batch-múltiplos |
| | EmbeddingContext.java (model) | 369 | `llmServiceEmbedding` | Batch |
| **Gerenciamento** | LLMServiceManager.java | 276 | `getPrimaryService()` | Genérica |
| | LLMServiceManager.java | 286 | `getPrimaryService()` | Genérica |
| | LLMServiceManager.java | 526 | `service` | Genérica |

### Detalhamento Completo

#### 1️⃣ ChapterEmbeddingStrategy.java:317
**Localização**: `src/main/java/bor/tools/simplerag/service/embedding/strategy/ChapterEmbeddingStrategy.java:317`

**Tipo de Operação**: `DOCUMENT`

**Texto Montado**:
```
"[Título do Capítulo]\n\n[Metadados Filtrados]\n\n[Conteúdo do Chunk]"
```

**Contexto**: Processa chunks de documentos (~2k tokens). É a estratégia **principal** para embeddings de conteúdo real.

**Variável**: `llmService` (injetada via Spring)

---

#### 2️⃣ QAEmbeddingStrategy.java:295
**Localização**: `src/main/java/bor/tools/simplerag/service/embedding/strategy/QAEmbeddingStrategy.java:295`

**Tipo de Operação**: `DOCUMENT` (reutiliza operação DOCUMENT, mas para Q&A sintético)

**Texto Montado**:
```
"Pergunta: [gerada por LLM]\nResposta: [gerada por LLM]"
```

**Contexto**: Gera pares pergunta/resposta sintéticos via LLM, depois cria embeddings para esses pares.

**Variável**: `llmService`

---

#### 3️⃣ SummaryEmbeddingStrategy.java:217
**Localização**: `src/main/java/bor/tools/simplerag/service/embedding/strategy/SummaryEmbeddingStrategy.java:217`

**Tipo de Operação**: `DOCUMENT`

**Texto Montado**:
```
"[Sumário gerado por LLM do capítulo inteiro]"
```

**Contexto**: Gera sumário do capítulo via LLM, depois cria embedding para o sumário (CLUSTERING).

**Variável**: `llmService`

---

#### 4️⃣ QueryEmbeddingStrategy.java:82
**Localização**: `src/main/java/bor/tools/simplerag/service/embedding/strategy/QueryEmbeddingStrategy.java:82`

**Tipo de Operação**: `QUERY`

**Texto Montado**:
```
"[Query do usuário - SEM montagem adicional]"
```

**Contexto**: Processa queries de busca do usuário. Sem transformação de texto.

**Variável**: `llmService`

---

#### 5️⃣ QueryEmbeddingStrategy.java:144
**Localização**: `src/main/java/bor/tools/simplerag/service/embedding/strategy/QueryEmbeddingStrategy.java:144`

**Tipo de Operação**: `QUERY`

**Texto Montado**:
```
"[Query do usuário - direto]"
```

**Contexto**: Variante alternativa para processamento de queries (fallback/branch diferente).

**Variável**: `llmService`

---

#### 6️⃣ EmbeddingServiceImpl.java:301
**Localização**: `src/main/java/bor/tools/simplerag/service/embedding/EmbeddingServiceImpl.java:301`

**Tipo de Operação**: Dinâmica (parametrizada)

**Contexto**: Camada de baixo nível que permite operações customizadas de embedding.

**Variável**: `llmService`

---

#### 7️⃣ EmbeddingContext.java:165 (processing/context)
**Localização**: `src/main/java/bor/tools/simplerag/service/processing/context/EmbeddingContext.java:165`

**Tipo de Operação**: Parametrizada via argumento

**Especificidade**: **Processamento em Lote (Batch) - Single**
```java
List<float[]> vectors = llmService.embeddings(operation, texts, params);
```

**Contexto**: Processa um único lote de textos (até X elementos).

**Variável**: `llmService`

---

#### 8️⃣ EmbeddingContext.java:200 (processing/context)
**Localização**: `src/main/java/bor/tools/simplerag/service/processing/context/EmbeddingContext.java:200`

**Tipo de Operação**: Parametrizada via argumento

**Especificidade**: **Processamento em Lote (Batch) - Múltiplos**
```java
List<float[]> vectors = llmService.embeddings(operation, texts, params);
```

**Contexto**: Processa múltiplos lotes de textos (agrupa até 10 por chamada para otimização).

**Variável**: `llmService`

---

#### 9️⃣ EmbeddingContext.java:369 (embedding/model)
**Localização**: `src/main/java/bor/tools/simplerag/service/embedding/model/EmbeddingContext.java:369`

**Tipo de Operação**: Parametrizada

**Método**: `generateEmbeddingsBatch(String[] texts, Embeddings_Op document)`

```java
if(this.llmServiceEmbedding != null) {
    return this.llmServiceEmbedding.embeddings(document, texts, this.mapParams);
}
```

**Contexto**: Versão alternativa/complementar com alias **diferente**: `llmServiceEmbedding`

**Variável**: `llmServiceEmbedding` ⚠️ **ALIAS ESPECIAL**

---

#### 🔟 LLMServiceManager.java:276
**Localização**: `src/main/java/bor/tools/simplerag/service/llm/LLMServiceManager.java:276`

**Contexto de Chamada**:
```java
public float[] embeddings(Embeddings_Op op, String text, String modelName) {
    switch (strategy) {
        case PRIMARY_ONLY:
            return executeOnPrimaryOnly(() -> getPrimaryService().embeddings(op, text, param));
```

**Variável**: `getPrimaryService()` → retorna primeiro `LLMProvider` da lista

**Estratégia**: PRIMARY_ONLY

---

#### 1️⃣1️⃣ LLMServiceManager.java:286
**Localização**: `src/main/java/bor/tools/simplerag/service/llm/LLMServiceManager.java:286`

**Contexto de Chamada**:
```java
case SPECIALIZED:
    return executeOnPrimaryOnly(() -> getPrimaryService().embeddings(op, text, param));
```

**Variável**: `getPrimaryService()` → retorna primeiro `LLMProvider`

**Estratégia**: SPECIALIZED (usa serviço especializado para embeddings)

---

#### 1️⃣2️⃣ LLMServiceManager.java:526 (Auxiliar)
**Localização**: `src/main/java/bor/tools/simplerag/service/llm/LLMServiceManager.java:526`

**Contexto de Chamada**:
```java
private float[] generateEmbeddingInternal(Embeddings_Op op, String text, String model) {
    LLMProvider service = getPrimaryService();
    return service.embeddings(op, text, null);
}
```

**Variável**: `service` (variável local que armazena o resultado de `getPrimaryService()`)

**Contexto**: Método auxiliar interno usado pelas estratégias (FAILOVER, ROUND_ROBIN, etc.)

---

## Estratégias de Montagem de Texto

### ChapterEmbeddingStrategy
**Arquivo**: `src/main/java/bor/tools/simplerag/service/embedding/strategy/ChapterEmbeddingStrategy.java`

**Fluxo**:
1. Recebe `ChapterDTO` com conteúdo e metadados
2. Divide em chunks (~2000 tokens)
3. Para cada chunk, monta:
   ```
   Título: [titulo do capítulo]

   [Metadados filtrados - nome, capitulo, descricao, area_conhecimento, etc.]

   [Conteúdo do chunk truncado]
   ```
4. Chama `llmService.embeddings(Embeddings_Op.DOCUMENT, texto, params)` com linha 317

**Tipo**: DOCUMENT

---

### QAEmbeddingStrategy
**Arquivo**: `src/main/java/bor/tools/simplerag/service/embedding/strategy/QAEmbeddingStrategy.java`

**Fluxo**:
1. Recebe `ChapterDTO` com conteúdo
2. **Primeiro**: chama LLM para gerar 3-5 pares pergunta/resposta
3. Monta texto combinado:
   ```
   Pergunta: [gerada pelo LLM]
   Resposta: [gerada pelo LLM]

   [repete para cada par]
   ```
4. Chama `llmService.embeddings(Embeddings_Op.DOCUMENT, combinedText, params)` com linha 295

**Tipo**: DOCUMENT (reutiliza operação, mas semântica é QA)

---

### SummaryEmbeddingStrategy
**Arquivo**: `src/main/java/bor/tools/simplerag/service/embedding/strategy/SummaryEmbeddingStrategy.java`

**Fluxo**:
1. Recebe `ChapterDTO` com conteúdo
2. **Primeiro**: chama LLM para gerar sumário do capítulo
3. Texto montado:
   ```
   [Sumário gerado - resumo compacto do capítulo]
   ```
4. Chama `llmService.embeddings(Embeddings_Op.DOCUMENT, summary, params)` com linha 217

**Tipo**: DOCUMENT (trata como CLUSTERING internamente)

---

### QueryEmbeddingStrategy
**Arquivo**: `src/main/java/bor/tools/simplerag/service/embedding/strategy/QueryEmbeddingStrategy.java`

**Fluxo**:
1. Recebe `SearchRequest.query` (string simples)
2. **Sem montagem** - usa texto direto
3. Chama `llmService.embeddings(Embeddings_Op.QUERY, queryText, params)` com linhas 82 ou 144

**Tipo**: QUERY

**Nota**: Há dois locais porque há dois branches/paths de processamento (um principal, um fallback).

---

## Processamento em Lote

### EmbeddingContext (processing/context)

**Arquivo**: `src/main/java/bor/tools/simplerag/service/processing/context/EmbeddingContext.java`

**Método 1 - Linha 165**: `generateSingleEmbedding(...)`
```java
List<float[]> vectors = llmService.embeddings(operation, texts, params);
```
- Processa UM lote
- Quantidade: até X textos em um batch
- Retorna: lista de vetores correspondentes

**Método 2 - Linha 200**: `generateEmbeddingsBatch(...)`
```java
List<float[]> vectors = llmService.embeddings(operation, texts, params);
```
- Processa MÚLTIPLOS lotes
- Agrupa até 10 textos por chamada (otimização)
- Retorna: lista completa de todos os vetores

**Otimização**: Reduz número de chamadas ao LLM ao combinar vários textos em uma única requisição.

---

### EmbeddingContext (embedding/model)

**Arquivo**: `src/main/java/bor/tools/simplerag/service/embedding/model/EmbeddingContext.java`

**Método**: `generateEmbeddingsBatch(String[] texts, Embeddings_Op document)` - Linha 369

```java
public List<float[]> generateEmbeddingsBatch(String[] texts, Embeddings_Op document) throws LLMException {
    if(this.llmServiceEmbedding != null) {
        return this.llmServiceEmbedding.embeddings(document, texts, this.mapParams);
    }
}
```

**Especificidade**:
- Usa alias **diferente**: `llmServiceEmbedding` (não `llmService`)
- Pode ser um serviço separado otimizado para embeddings
- Processa arrays de textos com operação parametrizada

---

## Camada de Gerenciamento LLM

### LLMServiceManager
**Arquivo**: `src/main/java/bor/tools/simplerag/service/llm/LLMServiceManager.java`

**Responsabilidade**: Gerenciar múltiplos provedores LLM com diferentes estratégias de falha e roteamento.

**Estratégias Suportadas**:
1. **PRIMARY_ONLY** (Linha 276)
   - Sempre usa serviço primário
   - Se falhar: erro

2. **FAILOVER** (Não tem linha explícita para embeddings neste arquivo)
   - Tenta primário, se falhar tenta secundário

3. **ROUND_ROBIN**
   - Alterna entre serviços

4. **SPECIALIZED** (Linha 286)
   - Usa serviço especializado em embeddings
   - Equivalente a PRIMARY_ONLY para embeddings

5. **DUAL_VERIFICATION**
   - Verifica resultados com dois serviços

6. **SMART_ROUTING**
   - Roteia baseado em inteligência interna

7. **MODEL_BASED**
   - Roteia baseado no nome do modelo

**Método Auxiliar** (Linha 526):
```java
private float[] generateEmbeddingInternal(Embeddings_Op op, String text, String model) {
    LLMProvider service = getPrimaryService();
    return service.embeddings(op, text, null);
}
```
- Usado por várias estratégias
- Centraliza chamada ao embeddings

---

## Aliases e Variáveis Especiais

### Tabela de Mapeamento

| Alias/Variável | Tipo | Localização | Observação |
|---|---|---|---|
| `llmService` | Campo injetado | Estratégias, EmbeddingContext (context) | Padrão mais comum |
| `llmServiceEmbedding` | Campo injetado | EmbeddingContext (model) | ⚠️ **ALIAS DIFERENTE** - serviço especializado |
| `getPrimaryService()` | Método | LLMServiceManager | Retorna primeiro LLMProvider da lista |
| `service` | Variável local | LLMServiceManager:526 | Armazena resultado de getPrimaryService() |

### Por Quê Múltiplos Aliases?

1. **`llmService`** (Padrão): Injetado via Spring, disponível em estratégias
2. **`llmServiceEmbedding`**: Possível serviço separado, otimizado apenas para embeddings (SOLID - separação de responsabilidades)
3. **`getPrimaryService()`**: Abstração em LLMServiceManager para suportar múltiplos provedores
4. **`service` (local)**: Variável temporária para melhor legibilidade

---

## Operações Suportadas (Emb_Operation)

Enum `Embeddings_Op` define tipos de operação para embeddings:

| Operação | Localização | Usado Por | Propósito |
|---|---|---|---|
| `DOCUMENT` | JSimpleLLM | ChapterEmbedding, QAEmbedding, SummaryEmbedding | Embeddings de conteúdo real |
| `QUERY` | JSimpleLLM | QueryEmbeddingStrategy | Embeddings de queries de busca |
| `CLUSTERING` | JSimpleLLM | SummaryEmbeddingStrategy (internamente) | Embeddings para sumários/resumos |
| Outras (CUSTOM, etc) | JSimpleLLM | EmbeddingServiceImpl | Operações customizadas |

**Detalhes**:
- Cada operação pode ter tratamento diferente no provedor LLM
- Define como o provedor processa o texto (normalizações, tokenização, etc.)
- Impacta na qualidade do embedding para cada caso de uso

---

## Referências Cruzadas

### Diagramas de Fluxo Relacionados
- `doc/DOCUMENT_PROCESSING_FLOW_DIAGRAM.md` - Fluxo geral de processamento
- `doc/EMBEDDING_SERVICE_IMPLEMENTATION_COMPLETE.md` - Implementação do serviço

### Configuração de Embeddings
- `doc/LLM_SERVICE_CONFIGURATION.md` - Como configurar provedores LLM
- `doc/MULTI_LLM_PROVIDER_GUIDE.md` - Usar múltiplos provedores

### Arquivos-Fonte Principais
```
src/main/java/bor/tools/simplerag/
├── service/
│   ├── embedding/
│   │   ├── EmbeddingServiceImpl.java
│   │   ├── strategy/
│   │   │   ├── ChapterEmbeddingStrategy.java
│   │   │   ├── QAEmbeddingStrategy.java
│   │   │   ├── SummaryEmbeddingStrategy.java
│   │   │   ├── QueryEmbeddingStrategy.java
│   │   │   └── EmbeddingGenerationStrategy.java (interface)
│   │   ├── model/
│   │   │   └── EmbeddingContext.java ← ALIAS llmServiceEmbedding
│   │   └── EmbeddingOrchestrator.java
│   ├── llm/
│   │   └── LLMServiceManager.java ← Gerenciamento multi-provider
│   └── processing/
│       ├── DocumentProcessingService.java
│       └── context/
│           └── EmbeddingContext.java ← Batch processing
└── ...
```

### Classes Principais Envolvidas
- **`LLMProvider`** (JSimpleLLM): Interface base para embedding
- **`Embeddings_Op`** (JSimpleLLM): Enum de operações
- **`MapParam`** (JSimpleLLM): Parametrização customizada
- **`EmbeddingGenerationStrategy`**: Interface para estratégias
- **`LLMServiceManager`**: Orquestrador de provedores

---

## Notas de Implementação

### Dimensões de Vetores
- **Padrão**: 1536 (OpenAI)
- **Alternativo**: 768 (outros modelos)
- **Configurável**: Via `application.properties` → `rag.embedding.dimensoes`

### Timeout de Chamadas
- Embeddings podem levar alguns segundos
- Processamento é **assíncrono** para não bloquear a UI
- Ver `ProcessingStatusTracker` para monitorar progresso

### Custo Computacional
- Cada chamada a `.embeddings()` = uma requisição ao provedor LLM
- Processamento em lote reduz número de requisições
- QA e Summary geram **2 chamadas LLM**: uma para gerar conteúdo, outra para embedding

### Filtragem de Metadados
- ChapterEmbeddingStrategy filtra metadados antes de montar
- Nem todo campo JSONB é incluído
- Consultar código da estratégia para ver exatamente quais campos

### Estratégias de Failover
- Se serviço primário falhar, LLMServiceManager pode tentar secundário
- Configurável via `rag.llm.estrategia-roteamento` (PRIMARY_ONLY, FAILOVER, etc.)

---

## Conclusão

O sistema JSimpleRag implementa um **arquitetura em camadas bem definida** para criar embeddings:

1. **Estratégias de Montagem** (alto nível): definem como texto é preparado
2. **Orquestração** (intermediário): agrupa em lotes, otimiza chamadas
3. **Gerenciamento** (baixo nível): suporta múltiplos provedores, failover automático
4. **Interface Final**: chamada ao `LLMProvider.embeddings()`

Com **11 pontos de chamada distribuídos** de forma estratégica, o sistema permite:
- Diferentes tipos de embedding (documento, query, QA sintético, sumário)
- Reutilização de código via estratégias
- Otimização via processamento em lote
- Flexibilidade para múltiplos provedores LLM

---

**Última atualização**: 2025-01-13
**Versão**: 1.0
**Autor**: Mapeamento via Claude Code
