# Análise de Impacto: LLMServiceManager na Refatoração de Embeddings

**Data**: 2025-01-25
**Versão**: 1.0
**Status**: Análise Técnica

---

## 📋 Contexto

Após revisar os documentos:
- `LLMSERVICE_POOL_ANALYSIS.md`
- `LLMServiceManager.java`
- `MultiLLMServiceConfig.java`

Identifiquei que o sistema JSimpleRag utiliza um **pool de LLMServices** gerenciado pelo `LLMServiceManager`, com roteamento baseado em modelos através do método:

```java
LLMService llmServiceManager.getLLMServiceByRegisteredModel(String modelName)
```

Isso tem **impactos significativos** na proposta de refatoração original (`splitting_and_embedding_generation.md`).

---

## 🎯 Descobertas Principais

### 1. LLMServiceManager como Pool

**O que é:**
- Gerenciador de **múltiplos** LLMService (não apenas um)
- Suporta primary, secondary, tertiary, etc.
- Pool de serviços: `List<LLMService> services`

**Como funciona:**

```java
// Não se usa LLMService diretamente
// Mas sim LLMServiceManager que roteia para o LLMService correto

LLMService service = llmServiceManager.getLLMServiceByRegisteredModel("nomic-embed-text");
float[] embedding = service.embeddings(op, text, params);
```

### 2. Roteamento Baseado em Modelo

**Método principal:**

```java
public LLMService getLLMServiceByRegisteredModel(String modelName)
```

**Características:**
- Busca no pool qual LLMService tem o modelo registrado
- Retorna o LLMService apropriado
- Retorna `null` se modelo não encontrado
- Matching: exact → partial → alias

**Exemplo de uso:**

```java
// Buscar service que tenha o modelo de embedding
LLMService embeddingService = llmServiceManager
    .getLLMServiceByRegisteredModel("nomic-embed-text");

if (embeddingService != null) {
    MapParam params = new MapParam().model("nomic-embed-text");
    float[] vector = embeddingService.embeddings(Embeddings_Op.QUERY, text, params);
}
```

### 3. Múltiplas Estratégias de Roteamento

O LLMServiceManager suporta várias estratégias:
- `PRIMARY_ONLY` - usa apenas o primeiro
- `FAILOVER` - failover automático
- `ROUND_ROBIN` - distribuição de carga
- `MODEL_BASED` - roteamento por modelo disponível ⭐ **RELEVANTE**
- `SMART_ROUTING` - roteamento inteligente
- `DUAL_VERIFICATION` - comparação de resultados

**A estratégia MODEL_BASED é a mais relevante para embeddings!**

### 4. Configuração de Múltiplos Providers

```properties
# Primary (LM Studio)
llmservice.provider.name=LM_STUDIO
llmservice.provider.embedding.model=nomic-embed-text
llmservice.provider.llm.models=qwen3-1.7b

# Secondary (Ollama)
llmservice.provider2.enabled=true
llmservice.provider2.name=OLLAMA
llmservice.provider2.embedding.model=mxbai-embed-large
llmservice.provider2.llm.models=mistral

# Strategy
llmservice.strategy=MODEL_BASED
```

---

## ⚠️ Impactos na Proposta Original

### Impacto 1: Injeção de Dependências nas Strategies

**Proposta Original (INCORRETA):**

```java
@Component
public class QueryEmbeddingStrategy implements EmbeddingGenerationStrategy {
    private final LLMService llmService;  // ❌ ERRADO

    public QueryEmbeddingStrategy(LLMService llmService) {
        this.llmService = llmService;
    }

    @Override
    public List<DocumentEmbeddingDTO> generate(EmbeddingRequest request) {
        float[] embedding = llmService.embeddings(op, text, params);
    }
}
```

**Proposta Corrigida (CORRETA):**

```java
@Component
public class QueryEmbeddingStrategy implements EmbeddingGenerationStrategy {
    private final LLMServiceManager llmServiceManager;  // ✅ CORRETO

    public QueryEmbeddingStrategy(LLMServiceManager llmServiceManager) {
        this.llmServiceManager = llmServiceManager;
    }

    @Override
    public List<DocumentEmbeddingDTO> generate(EmbeddingRequest request) {
        // Obter o service apropriado para o modelo
        String embeddingModel = request.getEmbeddingModelName();
        LLMService llmService = llmServiceManager
            .getLLMServiceByRegisteredModel(embeddingModel);

        if (llmService == null) {
            throw new IllegalStateException(
                "No LLM service found for embedding model: " + embeddingModel
            );
        }

        MapParam params = new MapParam().model(embeddingModel);
        float[] embedding = llmService.embeddings(op, text, params);
    }
}
```

**Mudanças necessárias:**
- ✅ Injetar `LLMServiceManager` ao invés de `LLMService`
- ✅ Usar `getLLMServiceByRegisteredModel(modelName)` para obter service
- ✅ Tratar caso de modelo não encontrado (`null`)
- ✅ Passar `modelName` no `MapParam`

---

### Impacto 2: Modelo de Dados - EmbeddingRequest

**Proposta Original:**

```java
@Data
@Builder
public class EmbeddingRequest {
    private ChapterDTO chapter;
    private String text;
    private LibraryDTO library;
    private Embeddings_Op operation;
    private TipoEmbedding tipoEmbedding;
    private Integer generationFlag;
    // ... outros campos
}
```

**Proposta Corrigida:**

```java
@Data
@Builder
public class EmbeddingRequest {
    private ChapterDTO chapter;
    private String text;
    private LibraryDTO library;
    private Embeddings_Op operation;
    private TipoEmbedding tipoEmbedding;
    private Integer generationFlag;

    // ⭐ NOVOS CAMPOS NECESSÁRIOS
    /**
     * Nome do modelo de embedding a ser usado.
     * Exemplo: "nomic-embed-text", "text-embedding-ada-002"
     */
    private String embeddingModelName;

    /**
     * Nome do modelo de completion para Q&A e sumarização.
     * Exemplo: "qwen3-1.7b", "gpt-4"
     */
    private String completionModelName;

    // ... outros campos
}
```

**Mudanças necessárias:**
- ✅ Adicionar campo `embeddingModelName`
- ✅ Adicionar campo `completionModelName` (para Q&A e sumarização)
- ✅ Esses campos devem ser populados a partir de `LibraryDTO` ou configuração global

---

### Impacto 3: Modelo de Dados - LibraryDTO

A `LibraryDTO` deve incluir configuração de modelos por biblioteca:

**Proposta:**

```java
@Data
public class LibraryDTO {
    private Integer id;
    private String nome;
    private TipoBiblioteca tipoBiblioteca;
    private String areaConhecimento;
    private Double pesoSemantico;
    private Double pesoTextual;
    private MetaBiblioteca metadados;

    // ⭐ NOVOS CAMPOS NECESSÁRIOS
    /**
     * Modelo de embedding padrão para esta biblioteca.
     * Se null, usa configuração global.
     * Exemplo: "nomic-embed-text"
     */
    private String defaultEmbeddingModel;

    /**
     * Modelo de completion padrão para esta biblioteca.
     * Se null, usa configuração global.
     * Exemplo: "qwen3-1.7b"
     */
    private String defaultCompletionModel;
}
```

**Alternativa: Configuração Global**

Se todas as bibliotecas usam os mesmos modelos, podemos usar configuração global:

```properties
# Modelo padrão para embeddings
rag.embedding.default-model=nomic-embed-text

# Modelo padrão para completions (Q&A, sumarização)
rag.completion.default-model=qwen3-1.7b
```

**Decisão necessária:**
- Modelos por biblioteca OU modelos globais?
- Ou híbrido (global com override por biblioteca)?

---

### Impacto 4: EmbeddingService Interface

**Proposta Original:**

```java
public interface EmbeddingService {
    float[] generateQueryEmbedding(String query, LibraryDTO library);
}
```

**Proposta Corrigida - Opção A (passar modelo explicitamente):**

```java
public interface EmbeddingService {
    float[] generateQueryEmbedding(String query, LibraryDTO library, String modelName);
}
```

**Proposta Corrigida - Opção B (obter modelo do library):**

```java
public interface EmbeddingService {
    // Usa library.getDefaultEmbeddingModel() internamente
    float[] generateQueryEmbedding(String query, LibraryDTO library);
}
```

**Proposta Corrigida - Opção C (sobrecarga de métodos):**

```java
public interface EmbeddingService {
    // Usa modelo padrão da biblioteca
    float[] generateQueryEmbedding(String query, LibraryDTO library);

    // Permite override do modelo
    float[] generateQueryEmbedding(String query, LibraryDTO library, String modelName);
}
```

**Recomendação: Opção C** (flexibilidade + conveniência)

---

### Impacto 5: EmbeddingServiceImpl

**Lógica necessária:**

```java
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private final LLMServiceManager llmServiceManager;
    private final List<EmbeddingGenerationStrategy> strategies;

    @Value("${rag.embedding.default-model:nomic-embed-text}")
    private String defaultEmbeddingModel;

    @Value("${rag.completion.default-model:qwen3-1.7b}")
    private String defaultCompletionModel;

    @Override
    public float[] generateQueryEmbedding(String query, LibraryDTO library) {
        String modelName = resolveEmbeddingModel(library);
        return generateQueryEmbedding(query, library, modelName);
    }

    @Override
    public float[] generateQueryEmbedding(String query, LibraryDTO library, String modelName) {
        // Obter LLMService apropriado
        LLMService llmService = llmServiceManager.getLLMServiceByRegisteredModel(modelName);

        if (llmService == null) {
            throw new IllegalStateException(
                "No LLM service found for embedding model: " + modelName
            );
        }

        // Criar request e delegar para strategy
        EmbeddingRequest request = EmbeddingRequest.builder()
            .text(query)
            .library(library)
            .operation(Embeddings_Op.QUERY)
            .embeddingModelName(modelName)
            .build();

        EmbeddingGenerationStrategy strategy = findStrategy(request);

        // Strategy usa o modelName do request para obter LLMService
        // e gera o embedding
        return strategy.generateQueryVector(query, modelName);
    }

    /**
     * Resolve qual modelo de embedding usar.
     * Prioridade: 1) LibraryDTO override, 2) Configuração global
     */
    private String resolveEmbeddingModel(LibraryDTO library) {
        if (library.getDefaultEmbeddingModel() != null) {
            return library.getDefaultEmbeddingModel();
        }
        return defaultEmbeddingModel;
    }

    private String resolveCompletionModel(LibraryDTO library) {
        if (library.getDefaultCompletionModel() != null) {
            return library.getDefaultCompletionModel();
        }
        return defaultCompletionModel;
    }
}
```

---

### Impacto 6: Strategies - Padrão de Implementação

Todas as strategies seguirão este padrão:

```java
@Component
public class ChapterEmbeddingStrategy implements EmbeddingGenerationStrategy {

    private static final Logger log = LoggerFactory.getLogger(ChapterEmbeddingStrategy.class);

    private final LLMServiceManager llmServiceManager;
    private final ContentSplitter contentSplitter;

    @Value("${rag.embedding.default-model:nomic-embed-text}")
    private String defaultEmbeddingModel;

    public ChapterEmbeddingStrategy(LLMServiceManager llmServiceManager,
                                   ContentSplitter contentSplitter) {
        this.llmServiceManager = llmServiceManager;
        this.contentSplitter = contentSplitter;
    }

    @Override
    public List<DocumentEmbeddingDTO> generate(EmbeddingRequest request) {
        // 1. Resolver modelo a ser usado
        String modelName = request.getEmbeddingModelName() != null
            ? request.getEmbeddingModelName()
            : defaultEmbeddingModel;

        // 2. Obter LLMService apropriado
        LLMService llmService = llmServiceManager.getLLMServiceByRegisteredModel(modelName);

        if (llmService == null) {
            log.error("No LLM service found for model: {}", modelName);
            throw new IllegalStateException("Embedding model not available: " + modelName);
        }

        log.debug("Using LLMService from provider: {} for model: {}",
                 llmService.getServiceProvider(), modelName);

        // 3. Lógica específica da strategy
        ChapterDTO chapter = request.getChapter();
        List<DocumentEmbeddingDTO> embeddings = new ArrayList<>();

        // ... lógica de chunking e geração de embeddings

        MapParam params = new MapParam().model(modelName);
        float[] vector = llmService.embeddings(
            request.getOperation(),
            textToEmbed,
            params
        );

        // ... construir DocumentEmbeddingDTO

        return embeddings;
    }

    // ... outros métodos
}
```

**Padrão comum a todas as strategies:**
1. ✅ Injetar `LLMServiceManager`
2. ✅ Injetar configuração de modelo padrão (`@Value`)
3. ✅ Resolver modelo: request → library → default
4. ✅ Obter `LLMService` via `getLLMServiceByRegisteredModel()`
5. ✅ Validar se service existe
6. ✅ Usar `MapParam().model(modelName)` ao chamar embeddings/completion
7. ✅ Log do provider usado

---

### Impacto 7: Q&A e Sumarização

Para Q&A e sumarização, além de embeddings, precisamos de **completions**:

```java
@Component
public class QAEmbeddingStrategy implements EmbeddingGenerationStrategy {

    private final LLMServiceManager llmServiceManager;

    @Value("${rag.completion.default-model:qwen3-1.7b}")
    private String defaultCompletionModel;

    @Value("${rag.embedding.default-model:nomic-embed-text}")
    private String defaultEmbeddingModel;

    @Override
    public List<DocumentEmbeddingDTO> generate(EmbeddingRequest request) {
        // 1. Gerar Q&A pairs usando COMPLETION
        String completionModel = request.getCompletionModelName() != null
            ? request.getCompletionModelName()
            : defaultCompletionModel;

        LLMService completionService = llmServiceManager
            .getLLMServiceByRegisteredModel(completionModel);

        MapParam completionParams = new MapParam().model(completionModel);
        String qaPairsJson = completionService.completion(
            systemPrompt,
            userPrompt,
            completionParams
        ).getText();

        List<QAPair> pairs = parseQAPairs(qaPairsJson);

        // 2. Gerar embeddings dos pares usando EMBEDDING
        String embeddingModel = request.getEmbeddingModelName() != null
            ? request.getEmbeddingModelName()
            : defaultEmbeddingModel;

        LLMService embeddingService = llmServiceManager
            .getLLMServiceByRegisteredModel(embeddingModel);

        List<DocumentEmbeddingDTO> embeddings = new ArrayList<>();
        for (QAPair pair : pairs) {
            MapParam embeddingParams = new MapParam().model(embeddingModel);
            float[] vector = embeddingService.embeddings(
                Embeddings_Op.DOCUMENT,
                pair.getQuestion() + "\n" + pair.getAnswer(),
                embeddingParams
            );

            // ... criar DocumentEmbeddingDTO
        }

        return embeddings;
    }
}
```

**Nota importante:**
- Q&A e Sumarização usam **DOIS modelos diferentes**:
  1. Completion model (para gerar Q&A ou resumo)
  2. Embedding model (para vetorizar o resultado)

---

### Impacto 8: SearchController

**Antes (proposta original):**

```java
@RestController
public class SearchController {
    private final EmbeddingService embeddingService;

    float[] embedding = embeddingService.generateQueryEmbedding(query, library);
}
```

**Agora (correto com modelo):**

```java
@RestController
public class SearchController {
    private final EmbeddingService embeddingService;

    // Opção A: Usar modelo padrão da biblioteca
    float[] embedding = embeddingService.generateQueryEmbedding(query, library);

    // Opção B: Permitir override do modelo na request
    String customModel = request.getEmbeddingModel(); // Se usuário especificou
    float[] embedding = customModel != null
        ? embeddingService.generateQueryEmbedding(query, library, customModel)
        : embeddingService.generateQueryEmbedding(query, library);
}
```

---

### Impacto 9: Configuração Global

Precisamos adicionar configurações:

```properties
# application.properties

# ============ Default Models ============

# Modelo padrão para geração de embeddings
rag.embedding.default-model=nomic-embed-text

# Modelo padrão para completions (Q&A, sumarização)
rag.completion.default-model=qwen3-1.7b

# ============ LLM Service Manager ============

# Estratégia de roteamento
llmservice.strategy=MODEL_BASED

# Provider primário (LM Studio)
llmservice.provider.name=LM_STUDIO
llmservice.provider.api.url=http://localhost:1234/v1
llmservice.provider.embedding.model=nomic-embed-text
llmservice.provider.llm.models=qwen3-1.7b

# Provider secundário (Ollama - opcional)
llmservice.provider2.enabled=false
llmservice.provider2.name=OLLAMA
llmservice.provider2.api.url=http://localhost:11434
llmservice.provider2.embedding.model=mxbai-embed-large
llmservice.provider2.llm.models=mistral
```

---

## 📊 Resumo dos Impactos

| Componente | Mudança Necessária | Prioridade |
|------------|-------------------|------------|
| **EmbeddingRequest** | Adicionar `embeddingModelName` e `completionModelName` | 🔴 ALTA |
| **LibraryDTO** | Adicionar `defaultEmbeddingModel` e `defaultCompletionModel` (opcional) | 🟡 MÉDIA |
| **EmbeddingService** | Métodos com sobrecarga para aceitar modelName | 🔴 ALTA |
| **EmbeddingServiceImpl** | Lógica de resolução de modelo + uso de LLMServiceManager | 🔴 ALTA |
| **Todas as Strategies** | Injetar `LLMServiceManager` + usar `getLLMServiceByRegisteredModel()` | 🔴 ALTA |
| **SearchController** | Passar modelo ao gerar query embedding (opcional override) | 🟡 MÉDIA |
| **application.properties** | Configurar modelos padrão (`rag.embedding.default-model`) | 🔴 ALTA |
| **Entity Library** | Adicionar colunas `default_embedding_model` e `default_completion_model` | 🟢 BAIXA |
| **Liquibase migration** | Adicionar colunas na tabela `library` | 🟢 BAIXA |

---

## ✅ Decisões Arquiteturais Necessárias

### Decisão 1: Configuração de Modelos

**Opção A: Global apenas**

```properties
rag.embedding.default-model=nomic-embed-text
rag.completion.default-model=qwen3-1.7b
```

**Opção B: Por biblioteca (com fallback global)**

```java
// Library entity
private String defaultEmbeddingModel;  // null = usa global
private String defaultCompletionModel; // null = usa global
```

**Opção C: Híbrida (recomendado)**
- Configuração global como padrão
- Override opcional por biblioteca
- Prioridade: request → library → global

**Recomendação:** Opção C

---

### Decisão 2: API do EmbeddingService

**Opção A: Sempre passar modelo**

```java
float[] generateQueryEmbedding(String query, LibraryDTO library, String modelName);
```

**Opção B: Modelo opcional (sobrecarga)**

```java
float[] generateQueryEmbedding(String query, LibraryDTO library);
float[] generateQueryEmbedding(String query, LibraryDTO library, String modelName);
```

**Recomendação:** Opção B (conveniência + flexibilidade)

---

### Decisão 3: Tratamento de Modelo Não Encontrado

**Opção A: Lançar exceção imediatamente**

```java
LLMService service = manager.getLLMServiceByRegisteredModel(model);
if (service == null) {
    throw new IllegalStateException("Model not found: " + model);
}
```

**Opção B: Fallback para modelo padrão**

```java
LLMService service = manager.getLLMServiceByRegisteredModel(model);
if (service == null) {
    log.warn("Model {} not found, falling back to default", model);
    service = manager.getLLMServiceByRegisteredModel(defaultModel);
}
```

**Opção C: Fallback para primary service**

```java
LLMService service = manager.getLLMServiceByRegisteredModel(model);
if (service == null) {
    log.warn("Model {} not found, using primary service", model);
    service = manager.getPrimaryService();
}
```

**Recomendação:** Opção A (fail-fast, mais claro para debugging)

---

## 🎯 Próximos Passos

1. ✅ **Revisar este documento** com o time
2. ✅ **Tomar decisões arquiteturais** (Decisões 1, 2, 3)
3. ✅ **Atualizar documento principal** (`splitting_and_embedding_generation.md`)
4. ✅ **Aprovar estratégia completa**
5. ✅ **Iniciar implementação**

---

## 📝 Conclusão

O uso de `LLMServiceManager` com pool de serviços **NÃO invalida a proposta de refatoração**, mas requer ajustes importantes:

**✅ Mantém-se:**
- Separação de responsabilidades (splitter vs embedding)
- Strategy pattern para tipos de embedding
- EmbeddingService como API pública
- EmbeddingOrchestrator para coordenação assíncrona

**🔄 Modificações necessárias:**
- Injeção de `LLMServiceManager` ao invés de `LLMService`
- Adição de `embeddingModelName` e `completionModelName` em requests
- Lógica de resolução de modelo (request → library → global)
- Uso de `getLLMServiceByRegisteredModel()` em todas as strategies
- Configuração de modelos padrão em `application.properties`

A arquitetura proposta **continua válida e necessária**, apenas com ajustes para integração adequada com o pool de LLMServices.
