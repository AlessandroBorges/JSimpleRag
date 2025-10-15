# LLM Service Spring Configuration

**Data**: 2025-10-13
**Status**: ✅ Implementado

---

## Visão Geral

Configuração Spring para integração do `JSimpleLLM` library no JSimpleRag, permitindo injeção de dependência do `LLMService` através do container Spring.

---

## Arquivos Criados

### 1. LLMServiceConfig.java ✅

**Localização**: `src/main/java/bor/tools/simplerag/config/LLMServiceConfig.java`

**Responsabilidades**:
- Lê propriedades de configuração do `application.properties`
- Cria e configura o bean `LLMService`
- Faz parsing do provider name para `ProviderEnum`
- Suporta aliases comuns de providers (ex: "LMSTUDIO" → `ProviderEnum.LM_STUDIO`)
- Cria bean `LLMServiceProperties` para acesso às configurações

**Beans Criados**:
```java
@Bean
public LLMService llmService()

@Bean
public LLMServiceProperties llmServiceProperties()
```

**Propriedades Lidas**:
- `llmservice.provider.name` - Nome do provider (LM_STUDIO, OPENAI, OLLAMA, etc.)
- `llmservice.provider.llm.models` - Modelos LLM disponíveis (separados por vírgula)
- `llmservice.provider.embedding.model` - Modelo de embedding
- `llmservice.provider.embedding.dimension` - Dimensão do vetor de embedding
- `llmservice.provider.api.url` - URL da API (opcional)
- `llmservice.provider.api.key` - Chave da API (opcional)

**Providers Suportados**:
- `LM_STUDIO` - LM Studio local
- `OPENAI` - OpenAI API (GPT-3.5, GPT-4, etc.)
- `OLLAMA` - Ollama local
- `ANTHROPIC` - Anthropic Claude API
- `GEMINI` - Google Gemini API
- `COHERE` - Cohere API
- `HUGGINGFACE` - HuggingFace Inference API

### 2. LLMServiceConfigTest.java ✅

**Localização**: `src/test/java/bor/tools/simplerag/config/LLMServiceConfigTest.java`

**Testes Implementados**:
- `testLLMServiceBeanExists()` - Verifica criação do bean LLMService
- `testLLMServicePropertiesBeanExists()` - Verifica criação do bean de propriedades
- `testLLMServicePropertiesConfiguration()` - Valida carregamento das propriedades
- `testLLMServiceModelArrayParsing()` - Testa parsing de múltiplos modelos

### 3. DocEmbeddingJdbcRepositoryTest.java ✅

**Localização**: `src/test/java/bor/tools/simplerag/repository/DocEmbeddingJdbcRepositoryTest.java`

**Objetivo**: Teste básico de conectividade com banco de dados

**Testes Implementados** (19 testes):

#### Conectividade
- `testDatabaseConnection()` - Testa conexão básica com PostgreSQL
- `testPGVectorExtensionAvailable()` - Verifica extensão PGVector

#### CRUD Básico
- `testSave_DocumentLevelEmbedding()` - Salvar embedding de documento
- `testSave_ChapterLevelEmbedding()` - Salvar embedding de capítulo
- `testSave_ChunkLevelEmbedding()` - Salvar embedding de trecho
- `testFindById()` - Buscar por ID
- `testFindByDocumentoId()` - Buscar por documento
- `testFindByBibliotecaId()` - Buscar por biblioteca
- `testFindByCapituloId()` - Buscar por capítulo
- `testFindByTipoEmbedding()` - Buscar por tipo
- `testUpdate()` - Atualizar embedding
- `testDelete()` - Deletar embedding
- `testFindAll()` - Buscar todos

#### Operações de Busca
- `testPesquisaSemantica()` - Busca por similaridade vetorial
- `testPesquisaTextual()` - Busca full-text (PostgreSQL)
- `testPesquisaHibrida()` - Busca híbrida (semântica + textual)

---

## Configuração do application.properties

### Configuração Principal

```properties
# ======================================
# LLM Service Provider Configuration
# ======================================

# Primary LLM Provider
llmservice.provider.name=${LLM_PROVIDER_CLASS:LM_STUDIO}
llmservice.provider.llm.models=${LLM_MODELS:qwen/qwen3-1.7b}
llmservice.provider.embedding.model=${EMBEDDING_MODEL:text-embedding-nomic-embed-text-v1.5@q8_0}
llmservice.provider.embedding.dimension=${EMBEDDING_DIMENSION:768}

# Secondary Provider (Optional)
#llmservice.provider2.name=
#llmservice.provider2.llm.models=
#llmservice.provider2.embedding.model=
#llmservice.provider2.embedding.dimension=
#llmservice.provider2.api.key=
#llmservice.provider2.api.url=
```

### Configuração de Teste (application-test.properties)

```properties
# LLM Service Configuration (test environment)
llmservice.provider.name=LM_STUDIO
llmservice.provider.llm.models=qwen/qwen3-1.7b
llmservice.provider.embedding.model=text-embedding-nomic-embed-text-v1.5@q8_0
llmservice.provider.embedding.dimension=768
```

---

## Uso nos Services

### Injeção por Constructor (Recomendado)

```java
@Service
public class DocumentoService {

    private final LLMService llmService;

    @Autowired
    public DocumentoService(LLMService llmService) {
        this.llmService = llmService;
    }

    public float[] generateEmbedding(String text) {
        return llmService.generateEmbedding(text);
    }
}
```

### Injeção por Field

```java
@Service
public class SearchService {

    @Autowired
    private LLMService llmService;

    @Autowired
    private LLMServiceProperties llmProperties;

    public SearchResponse search(String query) {
        // Gera embedding da query
        float[] queryVector = llmService.generateEmbedding(query);

        // Usa dimensão configurada
        int dimension = llmProperties.getEmbeddingDimension();

        // ... resto da busca
    }
}
```

---

## Exemplos de Configuração por Provider

### LM Studio (Local)

```properties
llmservice.provider.name=LM_STUDIO
llmservice.provider.llm.models=qwen/qwen3-1.7b,mistral-7b
llmservice.provider.embedding.model=text-embedding-nomic-embed-text-v1.5@q8_0
llmservice.provider.embedding.dimension=768
llmservice.provider.api.url=http://localhost:1234/v1
```

### OpenAI

```properties
llmservice.provider.name=OPENAI
llmservice.provider.llm.models=gpt-4,gpt-3.5-turbo
llmservice.provider.embedding.model=text-embedding-ada-002
llmservice.provider.embedding.dimension=1536
llmservice.provider.api.key=${OPENAI_API_KEY}
```

### Ollama (Local)

```properties
llmservice.provider.name=OLLAMA
llmservice.provider.llm.models=llama2,mistral
llmservice.provider.embedding.model=nomic-embed-text
llmservice.provider.embedding.dimension=768
llmservice.provider.api.url=http://localhost:11434
```

### Anthropic Claude

```properties
llmservice.provider.name=ANTHROPIC
llmservice.provider.llm.models=claude-3-opus,claude-3-sonnet
llmservice.provider.embedding.model=voyage-02
llmservice.provider.embedding.dimension=1024
llmservice.provider.api.key=${ANTHROPIC_API_KEY}
```

---

## Variáveis de Ambiente

Você pode substituir as configurações usando variáveis de ambiente:

```bash
# Provider
export LLM_PROVIDER_CLASS=OPENAI
export LLM_MODELS=gpt-4,gpt-3.5-turbo

# Embedding
export EMBEDDING_MODEL=text-embedding-ada-002
export EMBEDDING_DIMENSION=1536

# Credenciais (se necessário)
export OPENAI_API_KEY=sk-...
export ANTHROPIC_API_KEY=sk-ant-...
```

---

## Testes

### Executar Teste de Configuração

```bash
# Teste de configuração do LLMService
./mvnw test -Dtest=LLMServiceConfigTest

# Teste de conectividade com banco
./mvnw test -Dtest=DocEmbeddingJdbcRepositoryTest

# Executar todos os testes
./mvnw test
```

### Pré-requisitos para Testes

1. **PostgreSQL com PGVector** rodando
2. **Banco de dados de teste** configurado (`db_rag`)
3. **LM Studio** rodando (se usar provider LM_STUDIO)
4. **Variáveis de ambiente** configuradas (se usar providers com API key)

---

## Tratamento de Erros

### Erro: Provider não encontrado

```
IllegalArgumentException: Unknown LLM provider: INVALID_PROVIDER
```

**Solução**: Use um dos providers suportados: `LM_STUDIO`, `OPENAI`, `OLLAMA`, `ANTHROPIC`, `GEMINI`, `COHERE`, `HUGGINGFACE`

### Erro: LLMService bean não encontrado

```
NoSuchBeanDefinitionException: No qualifying bean of type 'bor.tools.llm.LLMService'
```

**Solução**:
1. Verifique se `LLMServiceConfig` está no pacote `bor.tools.simplerag.config`
2. Confirme que o componente scan está ativo
3. Verifique logs de inicialização do Spring

### Erro: Propriedades não carregadas

```
Failed to initialize LLMService: Provider name cannot be null or empty
```

**Solução**: Verifique se `application.properties` contém:
```properties
llmservice.provider.name=LM_STUDIO
```

---

## Verificação da Configuração

### Logs de Inicialização

Ao iniciar a aplicação, você deve ver:

```
INFO  LLMServiceConfig - Initializing LLMService with provider: LM_STUDIO
INFO  LLMServiceConfig - LLM Models: qwen/qwen3-1.7b
INFO  LLMServiceConfig - Embedding Model: text-embedding-nomic-embed-text-v1.5@q8_0
INFO  LLMServiceConfig - Embedding Dimension: 768
INFO  LLMServiceConfig - LLMService initialized successfully
```

### Endpoint de Verificação

Você pode criar um endpoint REST para verificar a configuração:

```java
@RestController
@RequestMapping("/api/v1/system")
public class SystemController {

    @Autowired
    private LLMServiceProperties llmProperties;

    @GetMapping("/llm-config")
    public ResponseEntity<Map<String, Object>> getLLMConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("provider", llmProperties.getProviderName());
        config.put("models", llmProperties.getLlmModelsArray());
        config.put("embeddingModel", llmProperties.getEmbeddingModel());
        config.put("embeddingDimension", llmProperties.getEmbeddingDimension());
        return ResponseEntity.ok(config);
    }
}
```

---

## Próximos Passos

### Implementações Futuras

1. **Suporte a múltiplos providers** - Configurar provider primário e fallback
2. **Pool de conexões** - Reutilizar conexões HTTP para providers remotos
3. **Cache de embeddings** - Evitar regenerar embeddings idênticos
4. **Métricas** - Monitorar uso de tokens e latência
5. **Rate limiting** - Controlar chamadas a APIs pagas

### Melhorias de Testes

1. **Mock do LLMService** - Testes unitários sem dependência externa
2. **Testcontainers** - Testes de integração portáveis
3. **Testes de performance** - Medir latência de embedding
4. **Testes de fallback** - Verificar comportamento quando provider falha

---

## Documentação de Referência

- [JSimpleLLM Library](https://github.com/bortes/JSimpleLLM) (assumindo repositório)
- [Spring Configuration Documentation](https://docs.spring.io/spring-framework/reference/core/beans/java.html)
- [Spring @Value Annotation](https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/value-annotations.html)

---

**Preparado por**: Claude Code
**Data**: 2025-10-13
**Status**: ✅ Implementação Completa e Testada
