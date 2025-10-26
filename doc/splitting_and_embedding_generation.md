# Estratégia de Refatoração: Splitting e Geração de Embeddings

**Versão**: 1.0
**Data**: 2025-01-25
**Status**: Proposta para Revisão
**Autor**: Claude Code

---

## 📋 Sumário Executivo

Este documento propõe uma refatoração arquitetural para separar as responsabilidades de **splitting de documentos** e **geração de embeddings** em componentes independentes e bem definidos.

**Motivação**: O pacote `bor.tools.splitter` atualmente mistura múltiplas responsabilidades (splitting, embedding, Q&A, sumarização, orquestração), violando o princípio de responsabilidade única (SRP) e dificultando manutenção e evolução.

**Objetivo**: Criar uma arquitetura limpa, escalável e testável que separe claramente:
- **Splitting** → Divisão de documentos em capítulos/chunks
- **Embedding Generation** → Geração de vetores semânticos
- **Orchestration** → Coordenação de processamento assíncrono

---

## 🔍 Análise do Problema Atual

### Estado Atual do Código

```
bor.tools.splitter/
├── DocumentSplitter.java              ✅ Splitting (correto)
├── SplitterFactory.java               ✅ Splitting (correto)
├── SplitterWiki.java                  ✅ Splitting (correto)
├── SplitterNorma.java                 ✅ Splitting (correto)
├── SplitterGenerico.java              ✅ Splitting (correto)
├── ContentSplitter.java               ✅ Splitting (correto)
│
├── EmbeddingProcessorImpl.java        ❌ FORA DO ESCOPO (649 linhas)
├── EmbeddingProcessorInterface.java   ❌ FORA DO ESCOPO (108 linhas)
├── AsyncSplitterService.java          ❌ FORA DO ESCOPO (349 linhas)
├── DocumentSummarizer.java            ❌ FORA DO ESCOPO
└── DocumentSummarizerImpl.java        ❌ FORA DO ESCOPO (400+ linhas)
```

### Problemas Identificados

#### 1. **Violação de SRP (Single Responsibility Principle)**

O pacote `splitter` tem **QUATRO responsabilidades diferentes**:
- ✅ Splitting de documentos (correto)
- ❌ Geração de embeddings (não deveria estar aqui)
- ❌ Sumarização de documentos (não deveria estar aqui)
- ❌ Orquestração assíncrona (não deveria estar aqui)

#### 2. **Acoplamento Excessivo**

Controllers dependem diretamente do pacote `splitter`:

```java
// SearchController.java:120
private final EmbeddingProcessorInterface embeddingProcessor;

// SearchController.java:168
float[] queryEmbedding = embeddingProcessor.createSearchEmbeddings(request.getQuery(), library);
```

Isso cria uma dependência semântica confusa: um **Controller de Busca** depende de um **Processador de Splitting**.

#### 3. **Falta de Camada de Serviço**

Estrutura atual de serviços:

```
src/main/java/bor/tools/simplerag/service/
├── DocumentoService.java       ✅ Existe
├── LibraryService.java         ✅ Existe
├── ChatService.java            ✅ Existe
├── UserService.java            ✅ Existe
└── EmbeddingService.java       ❌ NÃO EXISTE!
```

Não há um serviço dedicado para embeddings na camada de serviços apropriada.

#### 4. **Dificuldade de Extensão**

Para adicionar novos tipos de embeddings (ex: chat messages, metadados standalone):
- ❌ Teríamos que modificar `EmbeddingProcessorImpl` (atualmente com 649 linhas)
- ❌ Ou duplicar código em outros lugares
- ❌ Testes de embedding estão misturados com testes de splitting

#### 5. **Confusão Semântica**

O nome `AsyncSplitterService` sugere splitting, mas o método principal é:

```java
// AsyncSplitterService.java:204
public CompletableFuture<ProcessingResult> fullProcessingAsync(
    DocumentoWithAssociationDTO documento,
    LibraryDTO biblioteca,
    TipoConteudo tipoConteudo,
    boolean includeQA,        // ← Geração de Q&A
    boolean includeSummary)   // ← Geração de sumário
```

Este método faz: splitting, embedding, Q&A, sumarização e orquestração!

---

## 🎯 Proposta de Nova Arquitetura

### Princípios de Design

1. **Separation of Concerns**: Cada pacote tem uma responsabilidade clara
2. **Dependency Inversion**: Controllers dependem de abstrações (interfaces), não de implementações
3. **Open/Closed Principle**: Fácil adicionar novos tipos de embedding sem modificar código existente
4. **Strategy Pattern**: Diferentes estratégias de embedding são plugáveis
5. **Clean Architecture**: Camadas bem definidas (Controller → Service → Repository)

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
        │   ├── EmbeddingOrchestrator.java      ⭐ Orquestração assíncrona
        │   │
        │   ├── strategy/                        ← STRATEGY PATTERN
        │   │   ├── EmbeddingGenerationStrategy.java     (Interface)
        │   │   ├── ChapterEmbeddingStrategy.java        (Capítulos)
        │   │   ├── QueryEmbeddingStrategy.java          (Queries)
        │   │   ├── QAEmbeddingStrategy.java             (Q&A)
        │   │   └── SummaryEmbeddingStrategy.java        (Resumos)
        │   │
        │   └── model/
        │       └── EmbeddingRequest.java        ⭐ Request object
        │
        └── summarization/                       ← NOVA CAMADA DE SUMARIZAÇÃO
            ├── SummarizationService.java
            └── SummarizationServiceImpl.java
```

### Diagrama de Dependências

```
┌─────────────────────────────────────────────────────────────┐
│                     CONTROLLER LAYER                         │
├─────────────────────────────────────────────────────────────┤
│  DocumentController    SearchController    ChatController    │
│         │                    │                    │           │
│         └────────────────────┼────────────────────┘           │
└──────────────────────────────┼──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                      SERVICE LAYER                           │
├─────────────────────────────────────────────────────────────┤
│  DocumentoService                                            │
│         │                                                     │
│         ├──────────► EmbeddingService (interface)            │
│         │                    │                                │
│         │                    ├──► EmbeddingOrchestrator      │
│         │                    │           │                    │
│         │                    │           ├─► ChapterStrategy │
│         │                    │           ├─► QueryStrategy   │
│         │                    │           ├─► QAStrategy      │
│         │                    │           └─► SummaryStrategy │
│         │                                                     │
│         └──────────► SplitterFactory                         │
│                              │                                │
│                              ├──► SplitterWiki               │
│                              ├──► SplitterNorma              │
│                              └──► SplitterGenerico           │
└─────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                   INFRASTRUCTURE LAYER                       │
├─────────────────────────────────────────────────────────────┤
│  LLMService    DocEmbeddingJdbcRepository    ChapterRepo     │
└─────────────────────────────────────────────────────────────┘
```

---

## 📐 Componentes da Nova Arquitetura

### 1. EmbeddingService (Interface)

**Localização**: `service/embedding/EmbeddingService.java`

**Responsabilidade**: API pública para geração de embeddings

**Métodos principais**:
```java
public interface EmbeddingService {
    // Gerar embeddings de capítulo (automático)
    List<DocumentEmbeddingDTO> generateChapterEmbeddings(ChapterDTO chapter, LibraryDTO library);

    // Gerar embeddings de capítulo (com flag específica)
    List<DocumentEmbeddingDTO> generateChapterEmbeddings(ChapterDTO chapter, LibraryDTO library, int flag);

    // Gerar embedding de query (para busca)
    float[] generateQueryEmbedding(String query, LibraryDTO library);

    // Gerar embeddings Q&A
    List<DocumentEmbeddingDTO> generateQAEmbeddings(ChapterDTO chapter, LibraryDTO library, Integer k);

    // Gerar embeddings de resumo
    List<DocumentEmbeddingDTO> generateSummaryEmbeddings(ChapterDTO chapter, LibraryDTO library,
                                                          Integer maxLength, String instructions);

    // Método genérico (para casos avançados)
    float[] generateEmbedding(Embeddings_Op operation, String text, LibraryDTO library);
}
```

**Consumidores**:
- `DocumentoService` (processamento de documentos)
- `SearchController` (geração de query embeddings)
- `ChatService` (futuramente, para chat messages)

---

### 2. EmbeddingServiceImpl

**Localização**: `service/embedding/EmbeddingServiceImpl.java`

**Responsabilidade**: Implementação principal, delega para strategies

**Lógica**:
```java
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private final List<EmbeddingGenerationStrategy> strategies;
    private final LLMService llmService;

    @Override
    public List<DocumentEmbeddingDTO> generateChapterEmbeddings(ChapterDTO chapter, LibraryDTO library) {
        EmbeddingRequest request = EmbeddingRequest.builder()
            .chapter(chapter)
            .library(library)
            .generationFlag(FLAG_AUTO)
            .build();

        return findStrategy(request).generate(request);
    }

    private EmbeddingGenerationStrategy findStrategy(EmbeddingRequest request) {
        return strategies.stream()
            .filter(s -> s.supports(request))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No strategy found"));
    }
}
```

---

### 3. EmbeddingGenerationStrategy (Interface)

**Localização**: `service/embedding/strategy/EmbeddingGenerationStrategy.java`

**Responsabilidade**: Contrato para strategies de geração

**Interface**:
```java
public interface EmbeddingGenerationStrategy {
    // Gerar embeddings
    List<DocumentEmbeddingDTO> generate(EmbeddingRequest request);

    // Verificar se suporta o request
    boolean supports(EmbeddingRequest request);

    // Nome da strategy (para logs)
    String getStrategyName();
}
```

---

### 4. Strategies Específicas

#### ChapterEmbeddingStrategy

**Responsabilidade**: Gerar embeddings de capítulos (com chunking se necessário)

**Migra lógica de**: `EmbeddingProcessorImpl.createChapterEmbeddings()`

**Suporta**:
- `FLAG_FULL_TEXT_METADATA` - Texto completo + metadados
- `FLAG_ONLY_METADATA` - Apenas metadados
- `FLAG_ONLY_TEXT` - Apenas texto
- `FLAG_SPLIT_TEXT_METADATA` - Dividir em chunks
- `FLAG_AUTO` - Seleção automática baseada em tamanho

#### QueryEmbeddingStrategy

**Responsabilidade**: Gerar embeddings otimizados para queries

**Migra lógica de**: `EmbeddingProcessorImpl.createSearchEmbeddings()`

**Usa**: `Embeddings_Op.QUERY`

#### QAEmbeddingStrategy

**Responsabilidade**: Gerar pares de Q&A sintéticos e embeddings

**Migra lógica de**: `EmbeddingProcessorImpl.createQAEmbeddings()`

**Integra com**: `DocumentSummarizerImpl` para geração de perguntas

#### SummaryEmbeddingStrategy

**Responsabilidade**: Gerar resumos e embeddings condensados

**Migra lógica de**: `EmbeddingProcessorImpl.createSummaryEmbeddings()`

**Integra com**: `SummarizationService`

---

### 5. EmbeddingOrchestrator

**Localização**: `service/embedding/EmbeddingOrchestrator.java`

**Responsabilidade**: Coordenação de processamento assíncrono completo

**Migra lógica de**: `AsyncSplitterService.fullProcessingAsync()`

**Fluxo**:
```java
@Service
public class EmbeddingOrchestrator {

    private final EmbeddingService embeddingService;
    private final SplitterFactory splitterFactory;

    @Async
    public CompletableFuture<ProcessingResult> processDocumentFull(
            DocumentoWithAssociationDTO documento,
            LibraryDTO biblioteca,
            ProcessingOptions options) {

        // 1. Splitting (delega para SplitterFactory)
        List<ChapterDTO> chapters = splitDocument(documento, biblioteca);

        // 2. Embeddings básicos
        List<DocumentEmbeddingDTO> embeddings = new ArrayList<>();
        for (ChapterDTO chapter : chapters) {
            embeddings.addAll(embeddingService.generateChapterEmbeddings(chapter, biblioteca));
        }

        // 3. Q&A (se solicitado)
        if (options.isIncludeQA()) {
            for (ChapterDTO chapter : chapters) {
                embeddings.addAll(embeddingService.generateQAEmbeddings(chapter, biblioteca, 3));
            }
        }

        // 4. Resumos (se solicitado)
        if (options.isIncludeSummary()) {
            for (ChapterDTO chapter : chapters) {
                embeddings.addAll(embeddingService.generateSummaryEmbeddings(chapter, biblioteca, null, null));
            }
        }

        return CompletableFuture.completedFuture(
            new ProcessingResult(chapters, embeddings)
        );
    }
}
```

---

### 6. SummarizationService

**Localização**: `service/summarization/SummarizationService.java`

**Responsabilidade**: Geração de resumos (separado de embeddings)

**Migra de**: `DocumentSummarizerImpl`

**Motivação**: Sumarização é uma responsabilidade independente que pode ser usada fora do contexto de embeddings.

---

## 🔄 Estratégia de Migração

### Fase 1: Criar Nova Infraestrutura (Sem Breaking Changes)

**Objetivo**: Criar novos componentes sem modificar código existente

**Ações**:
1. ✅ Criar `service/embedding/` package
2. ✅ Criar interfaces: `EmbeddingService`, `EmbeddingGenerationStrategy`
3. ✅ Criar modelo: `EmbeddingRequest`
4. ✅ Implementar strategies migrando lógica de `EmbeddingProcessorImpl`
5. ✅ Criar `EmbeddingServiceImpl`
6. ✅ Criar `EmbeddingOrchestrator`
7. ✅ Criar `SummarizationService`

**Resultado**: Código antigo e novo coexistem

**Tempo estimado**: 1-2 dias

---

### Fase 2: Atualizar Consumidores

**Objetivo**: Modificar classes que usam a infraestrutura antiga

**Ações**:

#### 2.1. Atualizar DocumentoService

**Antes**:
```java
@Service
public class DocumentoService {
    private final AsyncSplitterService asyncSplitterService;

    public CompletableFuture<ProcessingStatus> processDocumentAsync(...) {
        AsyncSplitterService.ProcessingResult result =
            asyncSplitterService.fullProcessingAsync(...).get();
        persistProcessingResult(result, documento);
    }
}
```

**Depois**:
```java
@Service
public class DocumentoService {
    private final EmbeddingOrchestrator embeddingOrchestrator;
    private final SplitterFactory splitterFactory;

    public CompletableFuture<ProcessingStatus> processDocumentAsync(...) {
        ProcessingResult result =
            embeddingOrchestrator.processDocumentFull(...).get();
        persistProcessingResult(result, documento);
    }
}
```

#### 2.2. Atualizar SearchController

**Antes**:
```java
@RestController
public class SearchController {
    private final EmbeddingProcessorInterface embeddingProcessor;

    public ResponseEntity<SearchResponse> hybridSearch(...) {
        float[] queryEmbedding = embeddingProcessor.createSearchEmbeddings(query, library);
    }
}
```

**Depois**:
```java
@RestController
public class SearchController {
    private final EmbeddingService embeddingService;

    public ResponseEntity<SearchResponse> hybridSearch(...) {
        float[] queryEmbedding = embeddingService.generateQueryEmbedding(query, library);
    }
}
```

**Tempo estimado**: 1 dia

---

### Fase 3: Deprecar Código Antigo

**Objetivo**: Marcar código antigo como deprecated

**Ações**:
1. Adicionar `@Deprecated` em:
   - `EmbeddingProcessorInterface`
   - `EmbeddingProcessorImpl`
   - `AsyncSplitterService.fullProcessingAsync()`
2. Adicionar comentários de migração
3. Atualizar documentação

**Exemplo**:
```java
/**
 * @deprecated Use {@link EmbeddingService} instead.
 * This class will be removed in version 2.0.
 *
 * Migration guide:
 * - For chapter embeddings: Use EmbeddingService.generateChapterEmbeddings()
 * - For query embeddings: Use EmbeddingService.generateQueryEmbedding()
 * - For Q&A embeddings: Use EmbeddingService.generateQAEmbeddings()
 */
@Deprecated(since = "1.5", forRemoval = true)
public class EmbeddingProcessorImpl implements EmbeddingProcessorInterface {
    // ...
}
```

**Tempo estimado**: 2-3 horas

---

### Fase 4: Limpar Pacote Splitter

**Objetivo**: Mover componentes não relacionados a splitting

**Ações**:
1. Mover `DocumentSummarizerImpl` → `service/summarization/`
2. Simplificar `AsyncSplitterService` para focar apenas em splitting
3. Atualizar imports e referências

**Resultado**: Pacote `splitter` contém apenas splitting

**Tempo estimado**: 4-6 horas

---

### Fase 5: Remover Código Deprecated (Futuro)

**Objetivo**: Remover código antigo completamente

**Quando**: Versão 2.0 (após várias releases com código deprecated)

**Ações**:
1. Deletar `EmbeddingProcessorImpl`
2. Deletar `EmbeddingProcessorInterface`
3. Remover métodos deprecated de `AsyncSplitterService`

---

## 📊 Comparação: Antes vs Depois

### Geração de Query Embedding

#### Antes
```java
// SearchController precisa conhecer EmbeddingProcessorInterface do pacote splitter
@RestController
public class SearchController {
    private final EmbeddingProcessorInterface embeddingProcessor;  // ← De splitter!

    float[] embedding = embeddingProcessor.createSearchEmbeddings(query, library);
}
```

#### Depois
```java
// SearchController usa serviço apropriado da camada de service
@RestController
public class SearchController {
    private final EmbeddingService embeddingService;  // ← De service!

    float[] embedding = embeddingService.generateQueryEmbedding(query, library);
}
```

### Processamento Completo de Documento

#### Antes
```java
// DocumentoService usa AsyncSplitterService (nome confuso)
@Service
public class DocumentoService {
    private final AsyncSplitterService asyncSplitterService;  // ← Nome confuso

    ProcessingResult result = asyncSplitterService.fullProcessingAsync(
        documento, biblioteca, tipoConteudo, includeQA, includeSummary).get();
}
```

#### Depois
```java
// DocumentoService usa EmbeddingOrchestrator (nome claro)
@Service
public class DocumentoService {
    private final EmbeddingOrchestrator embeddingOrchestrator;  // ← Nome claro

    ProcessingOptions options = ProcessingOptions.builder()
        .includeQA(includeQA)
        .includeSummary(includeSummary)
        .build();

    ProcessingResult result = embeddingOrchestrator.processDocumentFull(
        documento, biblioteca, options).get();
}
```

### Adicionar Novo Tipo de Embedding

#### Antes
```java
// Precisaria modificar EmbeddingProcessorImpl (649 linhas)
// Adicionar novo método e lógica inline
// Testes misturados com outros tipos

@Service
public class EmbeddingProcessorImpl {
    // ... 649 linhas existentes

    // Adicionar aqui ↓
    public List<DocumentEmbeddingDTO> createChatMessageEmbeddings(...) {
        // Lógica nova
    }
}
```

#### Depois
```java
// Criar nova strategy independente
@Component
public class ChatMessageEmbeddingStrategy implements EmbeddingGenerationStrategy {

    @Override
    public List<DocumentEmbeddingDTO> generate(EmbeddingRequest request) {
        // Lógica isolada
    }

    @Override
    public boolean supports(EmbeddingRequest request) {
        return request.getTipoEmbedding() == TipoEmbedding.CHAT_MESSAGE;
    }
}
```

**Vantagens**:
- ✅ Código isolado e testável
- ✅ Não modifica classes existentes (Open/Closed)
- ✅ Spring auto-detecta nova strategy via @Component
- ✅ Testes independentes

---

## 🎯 Benefícios da Refatoração

### 1. Clareza Arquitetural
- ✅ Pacote `splitter` contém apenas splitting
- ✅ Serviços de embedding na camada correta (`service/`)
- ✅ Nomenclatura clara e semântica

### 2. Extensibilidade
- ✅ Adicionar novos tipos de embedding = criar nova strategy
- ✅ Não modificar código existente (Open/Closed Principle)
- ✅ Strategies são auto-descobertas por Spring

### 3. Testabilidade
- ✅ Testes de embedding separados de testes de splitting
- ✅ Cada strategy testável independentemente
- ✅ Mock de dependencies mais simples

### 4. Reutilização
- ✅ EmbeddingService pode ser usado por:
  - DocumentoService (processamento de docs)
  - SearchController (queries)
  - ChatService (mensagens de chat)
  - Qualquer outro componente futuro

### 5. Manutenibilidade
- ✅ Código organizado por responsabilidade
- ✅ Menos acoplamento entre camadas
- ✅ Mais fácil para novos desenvolvedores

---

## ⚠️ Riscos e Mitigações

### Risco 1: Regressão Funcional

**Descrição**: Bugs introduzidos durante migração

**Probabilidade**: Média
**Impacto**: Alto

**Mitigação**:
- ✅ Criar nova infraestrutura sem modificar a antiga (Fase 1)
- ✅ Testes de integração antes de deprecar código antigo
- ✅ Migração gradual (controllers um por um)
- ✅ Manter código deprecated por várias releases

### Risco 2: Performance

**Descrição**: Nova arquitetura com overhead adicional

**Probabilidade**: Baixa
**Impacto**: Médio

**Mitigação**:
- ✅ Strategy selection é O(n) onde n = número de strategies (< 10)
- ✅ LLM calls continuam iguais (não mudam)
- ✅ Benchmark antes e depois da migração

### Risco 3: Tempo de Implementação

**Descrição**: Estimativa otimista, pode levar mais tempo

**Probabilidade**: Média
**Impacto**: Baixo

**Mitigação**:
- ✅ Plano faseado permite pausar entre fases
- ✅ Cada fase entrega valor independentemente
- ✅ Código antigo funciona durante migração

### Risco 4: Resistência à Mudança

**Descrição**: Desenvolvedores continuam usando código deprecated

**Probabilidade**: Média
**Impacto**: Baixo

**Mitigação**:
- ✅ Documentação clara de migração
- ✅ Warnings de deprecated no IDE
- ✅ Code review exige uso de código novo
- ✅ Exemplos práticos na documentação

---

## 📅 Cronograma Proposto

### Semana 1: Fase 1 (Nova Infraestrutura)

**Dia 1**:
- ✅ Criar packages e interfaces
- ✅ Criar `EmbeddingRequest` model
- ✅ Criar `EmbeddingService` interface

**Dia 2**:
- ✅ Implementar `QueryEmbeddingStrategy`
- ✅ Implementar `ChapterEmbeddingStrategy`
- ✅ Testes unitários das strategies

**Dia 3**:
- ✅ Implementar `QAEmbeddingStrategy`
- ✅ Implementar `SummaryEmbeddingStrategy`
- ✅ Criar `EmbeddingServiceImpl`

**Dia 4**:
- ✅ Criar `EmbeddingOrchestrator`
- ✅ Criar `SummarizationService`
- ✅ Testes de integração

### Semana 2: Fase 2 e 3 (Migração e Deprecation)

**Dia 5**:
- ✅ Atualizar `SearchController`
- ✅ Atualizar `DocumentController`
- ✅ Testes de regressão

**Dia 6**:
- ✅ Atualizar `DocumentoService`
- ✅ Testes de integração end-to-end

**Dia 7**:
- ✅ Marcar código antigo como @Deprecated
- ✅ Adicionar documentação de migração
- ✅ Code review

**Dia 8**:
- ✅ Ajustes baseados em review
- ✅ Merge para main

### Semana 3: Fase 4 (Limpeza - Opcional)

**Dia 9-10**:
- ✅ Mover código de sumarização
- ✅ Simplificar AsyncSplitterService
- ✅ Atualizar imports

---

## ✅ Critérios de Aceite

### Must Have (Obrigatório)

- [ ] Todos os testes existentes continuam passando
- [ ] `SearchController` usa `EmbeddingService`
- [ ] `DocumentoService` usa `EmbeddingOrchestrator`
- [ ] Zero warnings de compilação
- [ ] Documentação de migração criada
- [ ] Código antigo marcado como @Deprecated

### Should Have (Desejável)

- [ ] Testes unitários para todas as strategies
- [ ] Testes de integração para `EmbeddingOrchestrator`
- [ ] Benchmark de performance (antes vs depois)
- [ ] Atualização do README.md

### Could Have (Opcional)

- [ ] Mover código de sumarização para `service/summarization/`
- [ ] Simplificar `AsyncSplitterService`
- [ ] Diagrama UML da nova arquitetura

---

## 🔍 Pontos de Decisão para Revisão

### Decisão 1: Manter EmbeddingProcessorImpl?

**Opção A**: Marcar como @Deprecated mas manter funcionando
**Opção B**: Deletar imediatamente após migração

**Recomendação**: Opção A (mais seguro)

### Decisão 2: Granularidade das Strategies

**Opção A**: Uma strategy por tipo (Chapter, Query, QA, Summary)
**Opção B**: Strategies mais granulares (FullTextChapter, MetadataOnlyChapter, etc.)

**Recomendação**: Opção A (mais simples, flags dentro da strategy)

### Decisão 3: Onde colocar SummarizationService?

**Opção A**: `service/summarization/` (package próprio)
**Opção B**: `service/embedding/` (junto com embedding)
**Opção C**: Manter em `splitter/` por enquanto

**Recomendação**: Opção A (responsabilidade independente)

### Decisão 4: Migração de AsyncSplitterService

**Opção A**: Simplificar para apenas splitting, mover orquestração para `EmbeddingOrchestrator`
**Opção B**: Manter como está mas deprecated
**Opção C**: Renomear para `DocumentProcessingOrchestrator`

**Recomendação**: Opção A (mais clean)

---

## 📖 Próximos Passos

1. **Revisão deste documento** pelo time
2. **Aprovação** da estratégia proposta
3. **Ajustes** baseados em feedback
4. **Kick-off** da implementação (Fase 1)
5. **Daily check-ins** durante implementação
6. **Code review** incremental por fase

---

## 📝 Notas Adicionais

### Compatibilidade com JSimpleLLM

A refatoração **não afeta** a integração com JSimpleLLM:
- ✅ Continua usando `LLMService` injetado
- ✅ Continua usando `Embeddings_Op` (QUERY, DOCUMENT, CLUSTERING)
- ✅ Continua usando `MapParam` para parâmetros
- ✅ Apenas **reorganiza** onde a lógica está, não como funciona

### Impacto em Testes Existentes

Testes que usam `EmbeddingProcessorImpl` diretamente:
- ⚠️ Precisam ser atualizados para usar `EmbeddingService`
- ⚠️ Ou mantidos como testes de regressão até remoção do código deprecated

### Retrocompatibilidade

Durante as Fases 1-3:
- ✅ Código antigo continua funcionando
- ✅ Código novo é adicionado sem quebrar o antigo
- ✅ Zero downtime durante migração

---

## 🤔 Questões em Aberto

1. Devemos criar um `EmbeddingContext` object para passar biblioteca + metadados?
2. Precisamos de cache de embeddings no `EmbeddingService`?
3. Devemos criar eventos de domínio para conclusão de processamento?
4. O `EmbeddingOrchestrator` deve ter retry logic para falhas de LLM?

---

**Documento preparado para revisão. Aguardando aprovação para prosseguir com implementação.**
