# Novo Framework de Testes - JSimpleRag

**Data**: 2025-10-14
**Status**: 📋 Proposta
**Decisão**: Migrar de testes puramente unitários (Mockito) para abordagem híbrida com testes de integração

---

## 🎯 Contexto

O sistema `LLMServiceManager` atualmente possui testes unitários usando Mockito para mockar `LLMService`. No entanto, essa abordagem possui limitações:

- **Testes superficiais**: Apenas validam lógica de roteamento, não a integração real
- **Falsos positivos**: Testes passam mesmo com problemas de integração
- **Manutenção complexa**: Mudanças na API do `LLMService` requerem atualização de todos os mocks
- **Não testa o valor real**: O diferencial do sistema está na integração correta com múltiplos provedores LLM

### Escopo

> **IMPORTANTE**: A API `JSimpleLLM` (`LLMService` e implementações) **JÁ POSSUI** seu próprio conjunto de testes.
>
> Este framework foca **APENAS** em testar o `LLMServiceManager` e componentes do JSimpleRag que **USAM** a API JSimpleLLM, não em testar a API em si.

---

## 🏗️ Arquitetura Proposta: Testes em Camadas

```
┌─────────────────────────────────────────────────────────┐
│  Layer 1: Unit Tests (JUnit + Mockito)                  │
│  ------------------------------------------------        │
│  Escopo:                                                │
│  - Lógica de roteamento (estratégias)                   │
│  - Validações de configuração                           │
│  - Contadores de estatísticas                           │
│  - Descoberta de modelos (getAllModels, etc)            │
│                                                          │
│  Quando executar: SEMPRE (pre-commit, CI)               │
│  Duração: < 1 segundo                                   │
│  Comando: mvn test                                      │
└─────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────┐
│  Layer 2: Integration Tests (Ollama + LM Studio)        │
│  ------------------------------------------------        │
│  Escopo:                                                │
│  - Comunicação REAL com Ollama (localhost:11434)        │
│  - Comunicação REAL com LM Studio (localhost:1234)      │
│  - Validação de roteamento multi-provedor end-to-end    │
│  - Failover real entre provedores heterogêneos          │
│  - MODEL_BASED routing com modelos distribuídos         │
│  - Tratamento de erros reais (timeout, modelo não       │
│    disponível, provedor offline, etc)                   │
│                                                          │
│  Quando executar: Em Pull Requests, pre-merge           │
│  Duração: ~30-60 segundos                               │
│  Comando: mvn verify -P integration-tests               │
└─────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────┐
│  Layer 3: E2E Tests (Ambiente Staging/Pré-Produção)     │
│  ------------------------------------------------        │
│  Escopo:                                                │
│  - Fluxo completo de busca RAG                          │
│  - Múltiplos provedores (local + cloud)                 │
│  - Performance e latência                               │
│                                                          │
│  Quando executar: Noturno, antes de releases            │
│  Duração: ~5 minutos                                    │
│  Comando: mvn verify -P e2e-tests                       │
└─────────────────────────────────────────────────────────┘
```

---

## 📁 Estrutura de Diretórios Proposta

```
src/
├── main/
│   └── java/
│       └── bor/tools/simplerag/
│           └── service/llm/
│               ├── LLMServiceManager.java
│               ├── LLMServiceStrategy.java
│               └── LLMServiceException.java
│
└── test/
    ├── java/
    │   └── bor/tools/simplerag/
    │       └── service/llm/
    │           │
    │           ├── unit/                                    ← NOVO
    │           │   ├── LLMServiceManagerUnitTest.java
    │           │   ├── StrategySelectionTest.java
    │           │   ├── StatisticsTrackingTest.java
    │           │   └── ModelDiscoveryTest.java
    │           │
    │           ├── integration/                             ← NOVO
    │           │   ├── providers/
    │           │   │   ├── OllamaProviderTest.java
    │           │   │   ├── LMStudioProviderTest.java
    │           │   │   └── ProviderCompatibilityTest.java
    │           │   │
    │           │   ├── strategies/
    │           │   │   ├── FailoverStrategyIntegrationTest.java
    │           │   │   ├── ModelBasedStrategyIntegrationTest.java
    │           │   │   ├── RoundRobinStrategyIntegrationTest.java
    │           │   │   └── SpecializedStrategyIntegrationTest.java
    │           │   │
    │           │   └── utils/
    │           │       └── TestProviderUtils.java
    │           │
    │           └── e2e/                                     ← NOVO
    │               ├── RAGSearchE2ETest.java
    │               └── MultiModelE2ETest.java
    │
    └── resources/
        ├── application-test.properties                      ← NOVO
        ├── application-integration-test.properties          ← NOVO
        └── scripts/                                         ← NOVO
            ├── setup-ollama.sh
            ├── setup-lmstudio.sh
            └── check-providers.sh
```

---

## 🧪 Layer 1: Unit Tests (Mantém Mockito)

### Objetivo
Testar **lógica de negócio pura** do `LLMServiceManager` sem dependências externas.

### O que testar

✅ **Lógica de Seleção de Estratégia**
- PRIMARY_ONLY usa apenas primário
- FAILOVER tenta secundário após falha
- ROUND_ROBIN alterna entre provedores
- SPECIALIZED roteia embeddings vs completions
- SMART_ROUTING decide por complexidade
- MODEL_BASED seleciona por nome do modelo

✅ **Validações de Configuração**
- Rejeita lista de serviços nula/vazia
- Aplica valores padrão corretos
- Estratégia padrão é FAILOVER

✅ **Contadores de Estatísticas**
- Incrementa primaryRequests corretamente
- Incrementa secondaryRequests corretamente
- Conta failoverEvents
- Calcula percentuais corretos
- Reset limpa contadores

✅ **Descoberta de Modelos**
- `getAllModels()` combina modelos de todos os provedores
- `getAllAvailableModels()` retorna por provedor
- `findProviderIndexByModel()` retorna índice correto
- `getServiceByModel()` retorna serviço correto

### Ferramentas
- JUnit 5
- Mockito
- AssertJ (opcional, para asserções fluentes)

### Exemplo

```java
// src/test/java/bor/tools/simplerag/service/llm/unit/StrategySelectionTest.java

@DisplayName("Strategy Selection - Unit Tests")
class StrategySelectionTest {

    private LLMService primaryService;
    private LLMService secondaryService;
    private LLMServiceManager manager;

    @BeforeEach
    void setUp() {
        primaryService = mock(LLMService.class);
        secondaryService = mock(LLMService.class);

        // Setup básico com any()
        when(primaryService.embeddings(any(), anyString(), any()))
            .thenReturn(new float[]{0.1f, 0.2f});
    }

    @Test
    @DisplayName("PRIMARY_ONLY strategy should only use primary service")
    void testPrimaryOnlyStrategy() {
        // Given
        manager = new LLMServiceManager(
            Arrays.asList(primaryService, secondaryService),
            LLMServiceStrategy.PRIMARY_ONLY,
            3, 30
        );

        // When
        manager.embeddings(Embeddings_Op.QUERY, "test");

        // Then
        verify(primaryService, times(1)).embeddings(any(), anyString(), any());
        verify(secondaryService, never()).embeddings(any(), anyString(), any());
    }
}
```

### Comando de Execução
```bash
# Apenas unit tests (rápido, sempre)
mvn test
```

---

## 🔌 Layer 2: Integration Tests (Ollama + LM Studio)

### Objetivo
Testar **integração REAL** do `LLMServiceManager` com provedores LLM locais rodando na LAN (Ollama e LM Studio).

### Por que Ollama + LM Studio?

🎯 **Testa Cenários Multi-Provedor Reais**
- Dois provedores diferentes rodando simultaneamente
- Modelos diferentes em cada provedor
- Permite testar MODEL_BASED com roteamento real
- Valida failover entre provedores heterogêneos
- Sem custos de API cloud

🚀 **Benefícios**
- Ambos rodam localmente na LAN (sem custos)
- Rápidos e controláveis
- Diferentes APIs/formatos (valida abstração do LLMServiceManager)
- Disponibilidade de modelos complementares

### O que testar

✅ **Comunicação Real com Provedores**
- Conexão com Ollama (localhost:11434)
- Conexão com LM Studio (localhost:1234)
- Geração de embeddings reais de ambos
- Geração de completions reais de ambos
- Parsing correto de respostas de ambos

✅ **Roteamento End-to-End**
- MODEL_BASED roteia para provedor correto baseado em modelo real
- Modelo específico do Ollama (llama2) roteia corretamente
- Modelo específico do LM Studio (qwen) roteia corretamente
- Modelo não encontrado faz fallback correto
- Partial matching funciona com modelos reais

✅ **Failover Real Entre Provedores**
- Ollama offline → failover para LM Studio
- LM Studio offline → failover para Ollama
- Ambos offline → erro apropriado
- Retries funcionam corretamente
- Tratamento de timeout real

✅ **Estratégias Multi-Provedor**
- ROUND_ROBIN alterna entre Ollama e LM Studio
- SPECIALIZED: embeddings no Ollama, completions no LM Studio
- DUAL_VERIFICATION compara respostas de ambos
- SMART_ROUTING escolhe provedor por complexidade

✅ **Erros Reais**
- Modelo não carregado retorna erro apropriado
- Timeout é tratado corretamente
- Provedor offline é detectado (isOnline())
- Recuperação após reconexão

### Pré-requisitos

**1. Ollama instalado e rodando (Primary Provider):**
```bash
# Instalar Ollama
curl -fsSL https://ollama.ai/install.sh | sh

# Iniciar Ollama
ollama serve &

# Baixar modelos de teste (pequenos e rápidos)
ollama pull llama2           # ~3.8GB, modelo completo
ollama pull tinyllama        # ~600MB, modelo menor/rápido
ollama pull nomic-embed-text # ~274MB, embeddings

# Verificar instalação
ollama list
curl http://localhost:11434/api/tags
```

**2. LM Studio instalado e rodando (Secondary Provider):**
```bash
# Baixar LM Studio
# https://lmstudio.ai/

# Iniciar LM Studio e carregar modelos:
# 1. Abrir LM Studio
# 2. Ir em "Local Server"
# 3. Start Server (porta padrão: 1234)
# 4. Baixar modelos sugeridos:
#    - qwen2.5-7b-instruct (ou similar)
#    - nomic-embed-text (embeddings)

# Verificar
curl http://localhost:1234/v1/models
```

### Configuração de Testes

**application-integration-test.properties:**
```properties
# ========================================
# Primary Provider: Ollama
# ========================================
llmservice.provider.name=OLLAMA
llmservice.provider.api.url=http://localhost:11434/v1
llmservice.provider.llm.models=llama2,tinyllama
llmservice.provider.embedding.model=nomic-embed-text
llmservice.provider.embedding.dimension=768

# ========================================
# Secondary Provider: LM Studio
# ========================================
llmservice.provider2.enabled=true
llmservice.provider2.name=LM_STUDIO
llmservice.provider2.api.url=http://localhost:1234/v1
llmservice.provider2.llm.models=qwen2.5-7b-instruct
llmservice.provider2.embedding.model=nomic-embed-text
llmservice.provider2.embedding.dimension=768

# ========================================
# Strategy (will be overridden per test)
# ========================================
llmservice.strategy=FAILOVER
llmservice.max-retries=3
llmservice.timeout-seconds=30
```

### Distribuição de Modelos Recomendada

| Provedor | Porta | Modelos LLM | Modelos Embedding | Uso nos Testes |
|----------|-------|-------------|-------------------|----------------|
| Ollama | 11434 | llama2, tinyllama | nomic-embed-text | Primary, cenários rápidos |
| LM Studio | 1234 | qwen2.5-7b | nomic-embed-text | Secondary, MODEL_BASED tests |

### Estrutura de Testes Integration

```
src/test/java/bor/tools/simplerag/service/llm/integration/
├── providers/
│   ├── OllamaProviderTest.java              # Testa apenas Ollama
│   ├── LMStudioProviderTest.java            # Testa apenas LM Studio
│   └── ProviderCompatibilityTest.java       # Valida ambos seguem API
├── strategies/
│   ├── FailoverStrategyIntegrationTest.java # Ollama → LM Studio
│   ├── ModelBasedStrategyIntegrationTest.java # Roteamento por modelo
│   ├── RoundRobinStrategyIntegrationTest.java # Alterna provedores
│   └── SpecializedStrategyIntegrationTest.java # Emb vs Completion
└── utils/
    └── TestProviderUtils.java               # Helpers
```

### Exemplo 1: Teste de Provedor Único (Ollama)

```java
// src/test/java/bor/tools/simplerag/service/llm/integration/providers/OllamaProviderTest.java

@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("Ollama Provider Integration Tests")
@Tag("integration")
@Tag("ollama")
class OllamaProviderTest {

    private LLMServiceManager manager;
    private LLMService ollamaService;

    @BeforeAll
    static void checkOllamaAvailable() {
        assumeTrue(TestProviderUtils.isOllamaRunning(),
            "Ollama must be running on localhost:11434");
    }

    @BeforeEach
    void setUp() {
        // Cria LLMService REAL apontando para Ollama
        LLMConfig config = LLMConfig.builder()
            .baseUrl("http://localhost:11434/v1")
            .build();

        ollamaService = LLMServiceFactory.createLLMService(
            SERVICE_PROVIDER.OLLAMA,
            config
        );

        manager = new LLMServiceManager(
            Arrays.asList(ollamaService),
            LLMServiceStrategy.PRIMARY_ONLY,
            3, 30
        );
    }

    @Test
    @DisplayName("Should generate real embeddings from Ollama")
    void testRealEmbeddings() {
        // When - Chama Ollama REAL
        float[] embeddings = manager.embeddings(
            Embeddings_Op.QUERY,
            "What is machine learning?",
            "nomic-embed-text"
        );

        // Then - Valida resposta real
        assertNotNull(embeddings);
        assertEquals(768, embeddings.length); // nomic-embed-text dimension
        assertTrue(Arrays.stream(embeddings).anyMatch(v -> v != 0.0f));
    }

    @Test
    @DisplayName("Should generate real completion from Ollama")
    void testRealCompletion() {
        // When
        String response = manager.generateCompletion(
            "You are a helpful assistant",
            "What is 2+2?",
            "tinyllama"
        );

        // Then
        assertNotNull(response);
        assertFalse(response.isEmpty());
        assertTrue(response.length() > 5);
    }

    @Test
    @DisplayName("Should list available Ollama models")
    void testListModels() {
        // When
        List<String> models = manager.getAllModels();

        // Then
        assertNotNull(models);
        assertFalse(models.isEmpty());
        assertTrue(models.contains("llama2") || models.contains("tinyllama"));
    }
}
```

### Exemplo 2: Teste Multi-Provedor (Ollama + LM Studio)

```java
// src/test/java/bor/tools/simplerag/service/llm/integration/strategies/FailoverStrategyIntegrationTest.java

@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("Failover Strategy - Ollama to LM Studio")
@Tag("integration")
@Tag("multi-provider")
class FailoverStrategyIntegrationTest {

    private LLMServiceManager manager;
    private LLMService ollamaService;
    private LLMService lmStudioService;

    @BeforeAll
    static void checkProvidersAvailable() {
        assumeTrue(TestProviderUtils.isOllamaRunning(),
            "Ollama must be running on localhost:11434");
        assumeTrue(TestProviderUtils.isLMStudioRunning(),
            "LM Studio must be running on localhost:1234");
    }

    @BeforeEach
    void setUp() {
        // Primary: Ollama
        LLMConfig ollamaConfig = LLMConfig.builder()
            .baseUrl("http://localhost:11434/v1")
            .build();
        ollamaService = LLMServiceFactory.createLLMService(
            SERVICE_PROVIDER.OLLAMA,
            ollamaConfig
        );

        // Secondary: LM Studio
        LLMConfig lmStudioConfig = LLMConfig.builder()
            .baseUrl("http://localhost:1234/v1")
            .build();
        lmStudioService = LLMServiceFactory.createLLMService(
            SERVICE_PROVIDER.LM_STUDIO,
            lmStudioConfig
        );

        // Manager with FAILOVER strategy
        manager = new LLMServiceManager(
            Arrays.asList(ollamaService, lmStudioService),
            LLMServiceStrategy.FAILOVER,
            3, 30
        );
    }

    @Test
    @DisplayName("Primary success - should use Ollama only")
    void testPrimarySuccess() {
        // When - Modelo disponível no Ollama
        String response = manager.generateCompletion(
            "You are a helpful assistant",
            "Say 'Hello from Ollama'",
            "llama2"  // Modelo do Ollama
        );

        // Then
        assertNotNull(response);
        assertFalse(response.isEmpty());

        // Verificar estatísticas
        LLMServiceStats stats = manager.getStatistics();
        assertEquals(1, stats.getPrimaryRequests());
        assertEquals(0, stats.getSecondaryRequests());
        assertEquals(0, stats.getFailoverEvents());
    }

    @Test
    @DisplayName("Primary unavailable - should failover to LM Studio")
    void testFailoverToSecondary() {
        // Given - Manager com primary offline
        // (Simulamos desligando Ollama ou usando modelo inexistente)
        manager = new LLMServiceManager(
            Arrays.asList(ollamaService, lmStudioService),
            LLMServiceStrategy.FAILOVER,
            1, 5  // Retry rápido
        );

        // When - Solicita modelo que só existe no LM Studio
        String response = manager.generateCompletion(
            "You are a helpful assistant",
            "Say 'Hello from LM Studio'",
            "qwen2.5-7b-instruct"  // Modelo só no LM Studio
        );

        // Then
        assertNotNull(response);
        assertFalse(response.isEmpty());

        // Verificar que usou secondary
        LLMServiceStats stats = manager.getStatistics();
        assertTrue(stats.getSecondaryRequests() > 0);
    }

    @Test
    @DisplayName("Both providers work - failover maintains availability")
    void testBothProvidersWork() {
        // When - Múltiplas requisições
        for (int i = 0; i < 5; i++) {
            float[] embeddings = manager.embeddings(
                Embeddings_Op.QUERY,
                "Test query " + i,
                "nomic-embed-text"
            );
            assertNotNull(embeddings);
            assertEquals(768, embeddings.length);
        }

        // Then - Todas devem ter sucesso
        LLMServiceStats stats = manager.getStatistics();
        assertEquals(5, stats.getTotalRequests());
    }
}
```

### Exemplo 3: MODEL_BASED com Múltiplos Provedores

```java
// src/test/java/bor/tools/simplerag/service/llm/integration/strategies/ModelBasedStrategyIntegrationTest.java

@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("MODEL_BASED Strategy - Model-Based Routing")
@Tag("integration")
@Tag("model-based")
class ModelBasedStrategyIntegrationTest {

    private LLMServiceManager manager;

    @BeforeAll
    static void checkProvidersAvailable() {
        assumeTrue(TestProviderUtils.isOllamaRunning(),
            "Ollama must be running");
        assumeTrue(TestProviderUtils.isLMStudioRunning(),
            "LM Studio must be running");
    }

    @BeforeEach
    void setUp() {
        // Primary: Ollama (llama2, tinyllama)
        LLMConfig ollamaConfig = LLMConfig.builder()
            .baseUrl("http://localhost:11434/v1")
            .build();
        LLMService ollamaService = LLMServiceFactory.createLLMService(
            SERVICE_PROVIDER.OLLAMA,
            ollamaConfig
        );

        // Secondary: LM Studio (qwen2.5-7b)
        LLMConfig lmStudioConfig = LLMConfig.builder()
            .baseUrl("http://localhost:1234/v1")
            .build();
        LLMService lmStudioService = LLMServiceFactory.createLLMService(
            SERVICE_PROVIDER.LM_STUDIO,
            lmStudioConfig
        );

        // Manager with MODEL_BASED strategy
        manager = new LLMServiceManager(
            Arrays.asList(ollamaService, lmStudioService),
            LLMServiceStrategy.MODEL_BASED,
            3, 30
        );
    }

    @Test
    @DisplayName("Should route llama2 requests to Ollama")
    void testRouteLlama2ToOllama() {
        // When
        String response = manager.generateCompletion(
            "You are a helpful assistant",
            "What is AI?",
            "llama2"  // Modelo específico do Ollama
        );

        // Then
        assertNotNull(response);

        // Verificar que usou Ollama (primary)
        LLMServiceStats stats = manager.getStatistics();
        assertEquals(1, stats.getPrimaryRequests());
        assertEquals(0, stats.getSecondaryRequests());
    }

    @Test
    @DisplayName("Should route qwen requests to LM Studio")
    void testRouteQwenToLMStudio() {
        // When
        String response = manager.generateCompletion(
            "You are a helpful assistant",
            "What is machine learning?",
            "qwen2.5-7b-instruct"  // Modelo específico do LM Studio
        );

        // Then
        assertNotNull(response);

        // Verificar que usou LM Studio (secondary)
        LLMServiceStats stats = manager.getStatistics();
        assertEquals(0, stats.getPrimaryRequests());
        assertEquals(1, stats.getSecondaryRequests());
    }

    @Test
    @DisplayName("Should handle partial model name matching")
    void testPartialModelMatching() {
        // When - Usa apenas "qwen" (parcial)
        String response = manager.generateCompletion(
            "You are a helpful assistant",
            "Hello",
            "qwen"  // Parcial - deve encontrar qwen2.5-7b-instruct
        );

        // Then
        assertNotNull(response);

        // Deve ter roteado para LM Studio
        LLMServiceStats stats = manager.getStatistics();
        assertEquals(1, stats.getSecondaryRequests());
    }

    @Test
    @DisplayName("Should fallback to primary when model not found")
    void testFallbackWhenModelNotFound() {
        // When - Modelo inexistente
        String response = manager.generateCompletion(
            "You are a helpful assistant",
            "Hello",
            "nonexistent-model-xyz"
        );

        // Then - Deve usar fallback (primary)
        assertNotNull(response);

        LLMServiceStats stats = manager.getStatistics();
        assertEquals(1, stats.getPrimaryRequests());
    }

    @Test
    @DisplayName("Should discover all models from both providers")
    void testDiscoverAllModels() {
        // When
        List<String> allModels = manager.getAllModels();

        // Then - Deve conter modelos de ambos os provedores
        assertNotNull(allModels);
        assertFalse(allModels.isEmpty());

        // Verificar presença de modelos específicos
        assertTrue(allModels.stream()
            .anyMatch(m -> m.contains("llama2") || m.contains("tinyllama")),
            "Should contain Ollama models");
        assertTrue(allModels.stream()
            .anyMatch(m -> m.contains("qwen")),
            "Should contain LM Studio models");
    }

    @Test
    @DisplayName("Should find correct provider index by model name")
    void testFindProviderIndex() {
        // When
        int ollamaIndex = manager.findProviderIndexByModel("llama2");
        int lmStudioIndex = manager.findProviderIndexByModel("qwen2.5-7b-instruct");
        int notFoundIndex = manager.findProviderIndexByModel("nonexistent");

        // Then
        assertEquals(0, ollamaIndex, "llama2 should be in Ollama (index 0)");
        assertEquals(1, lmStudioIndex, "qwen should be in LM Studio (index 1)");
        assertEquals(-1, notFoundIndex, "nonexistent should return -1");
    }
}
```

### Exemplo 4: Classe Helper TestProviderUtils

```java
// src/test/java/bor/tools/simplerag/service/llm/integration/utils/TestProviderUtils.java

public class TestProviderUtils {

    private static final String OLLAMA_URL = "http://localhost:11434";
    private static final String LMSTUDIO_URL = "http://localhost:1234";
    private static final int TIMEOUT_MS = 2000;

    /**
     * Checks if Ollama is running and accessible
     */
    public static boolean isOllamaRunning() {
        return isProviderRunning(OLLAMA_URL + "/api/tags");
    }

    /**
     * Checks if LM Studio is running and accessible
     */
    public static boolean isLMStudioRunning() {
        return isProviderRunning(LMSTUDIO_URL + "/v1/models");
    }

    /**
     * Generic provider availability check
     */
    private static boolean isProviderRunning(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Waits for provider to be available (useful for container startup)
     */
    public static boolean waitForProvider(String url, int maxWaitSeconds) {
        long endTime = System.currentTimeMillis() + (maxWaitSeconds * 1000L);
        while (System.currentTimeMillis() < endTime) {
            if (isProviderRunning(url)) {
                return true;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Gets list of models from Ollama
     */
    public static List<String> getOllamaModels() {
        return getModelsFromProvider(OLLAMA_URL + "/api/tags");
    }

    /**
     * Gets list of models from LM Studio
     */
    public static List<String> getLMStudioModels() {
        return getModelsFromProvider(LMSTUDIO_URL + "/v1/models");
    }

    private static List<String> getModelsFromProvider(String url) {
        // Implementation depends on JSON parsing library
        // Return empty list if provider not available
        return Collections.emptyList();
    }
}
```

### Profile Maven

**pom.xml:**
```xml
<profiles>
    <!-- Profile 1: Only Ollama (básico) -->
    <profile>
        <id>integration-tests-ollama</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-failsafe-plugin</artifactId>
                    <version>3.0.0</version>
                    <configuration>
                        <includes>
                            <include>**/*IntegrationTest.java</include>
                        </includes>
                        <groups>integration &amp; ollama</groups>
                        <systemPropertyVariables>
                            <ollama.url>http://localhost:11434</ollama.url>
                        </systemPropertyVariables>
                    </configuration>
                    <executions>
                        <execution>
                            <goals>
                                <goal>integration-test</goal>
                                <goal>verify</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>

    <!-- Profile 2: Ollama + LM Studio (completo) -->
    <profile>
        <id>integration-tests</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-failsafe-plugin</artifactId>
                    <version>3.0.0</version>
                    <configuration>
                        <includes>
                            <include>**/*IntegrationTest.java</include>
                        </includes>
                        <groups>integration</groups>
                        <systemPropertyVariables>
                            <ollama.url>http://localhost:11434</ollama.url>
                            <lmstudio.url>http://localhost:1234</lmstudio.url>
                        </systemPropertyVariables>
                    </configuration>
                    <executions>
                        <execution>
                            <goals>
                                <goal>integration-test</goal>
                                <goal>verify</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>

    <!-- Profile 3: Multi-provider tests only -->
    <profile>
        <id>multi-provider-tests</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-failsafe-plugin</artifactId>
                    <version>3.0.0</version>
                    <configuration>
                        <includes>
                            <include>**/*IntegrationTest.java</include>
                        </includes>
                        <groups>multi-provider</groups>
                        <systemPropertyVariables>
                            <ollama.url>http://localhost:11434</ollama.url>
                            <lmstudio.url>http://localhost:1234</lmstudio.url>
                        </systemPropertyVariables>
                    </configuration>
                    <executions>
                        <execution>
                            <goals>
                                <goal>integration-test</goal>
                                <goal>verify</goal>
                            </goals>
                        </execution>
                    </executions>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

### Comandos de Execução

```bash
# 1. Apenas unit tests (sempre, sem provedores)
mvn test

# 2. Integration tests com Ollama apenas
mvn verify -P integration-tests-ollama

# 3. Integration tests completos (Ollama + LM Studio)
mvn verify -P integration-tests

# 4. Apenas testes multi-provedor
mvn verify -P multi-provider-tests

# 5. Testes específicos por tag
mvn verify -P integration-tests -Dgroups="integration & model-based"

# 6. Pular integration tests
mvn verify -DskipITs=true

# 7. Executar teste específico
mvn verify -P integration-tests -Dit.test=ModelBasedStrategyIntegrationTest

# 8. Testes com log debug
mvn verify -P integration-tests -X
```

---

## 🐳 Opção Avançada: Testcontainers

### Objetivo
Executar testes de integração com Ollama em container Docker (CI/CD friendly).

### Vantagens
- ✅ Ambiente 100% isolado e reproduzível
- ✅ Não requer instalação local de Ollama
- ✅ Perfeito para CI/CD (GitHub Actions, GitLab CI)
- ✅ Setup/teardown automático

### Exemplo

```java
@Testcontainers
@DisplayName("Ollama Testcontainers Integration")
class OllamaTestcontainersTest {

    @Container
    static GenericContainer<?> ollama = new GenericContainer<>("ollama/ollama:latest")
        .withExposedPorts(11434)
        .withCommand("serve")
        .waitingFor(Wait.forHttp("/api/tags").forStatusCode(200));

    @Test
    void testWithContainerizedOllama() {
        String ollamaUrl = String.format(
            "http://%s:%d",
            ollama.getHost(),
            ollama.getMappedPort(11434)
        );

        LLMConfig config = LLMConfig.builder()
            .baseUrl(ollamaUrl + "/v1")
            .build();

        LLMService service = LLMServiceFactory.createLLMService(
            SERVICE_PROVIDER.OLLAMA,
            config
        );

        // Teste com Ollama em container
        assertNotNull(service.getModels());
    }
}
```

**Dependência:**
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.0</version>
    <scope>test</scope>
</dependency>
```

---

## 🌐 Layer 3: E2E Tests

### Objetivo
Testar fluxo completo da aplicação em ambiente staging/pré-produção.

### O que testar
- Fluxo completo de busca RAG
- Múltiplos provedores (local + cloud)
- Performance e latência
- Cenários de erro end-to-end

### Quando executar
- Builds noturnos
- Antes de releases
- Em ambiente staging

**Exemplo:**
```java
@Tag("e2e")
@DisplayName("End-to-End RAG Search Tests")
class RAGSearchE2ETest {

    @Test
    @Disabled("Run only in staging environment")
    void testFullRAGSearchFlow() {
        // 1. Upload documento
        // 2. Processa com embeddings (LLMServiceManager)
        // 3. Busca semântica
        // 4. Gera resposta com LLM
        // 5. Valida resultado completo
    }
}
```

---

## 📊 Matriz de Decisão de Testes

| Aspecto | Unit Test | Integration Test (Ollama) | Integration Test (Dual) | E2E Test |
|---------|-----------|---------------------------|-------------------------|----------|
| **Escopo** | Lógica isolada | Single provider | Multi-provider | Fluxo completo |
| **Ferramenta** | Mockito | Ollama | Ollama + LM Studio | Staging |
| **Duração** | < 1s | ~15s | ~30-60s | ~5min |
| **Quando** | Sempre | PR | PR/Merge | Release |
| **CI/CD** | ✅ Sempre | ✅ Recomendado | ⚠️ Opcional | ⚠️ Staging only |
| **Setup** | ❌ Não | ⚠️ Ollama | ⚠️ Ollama + LM Studio | ⚠️ Infraestrutura |
| **Confiabilidade** | ⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **Manutenção** | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐ |
| **Casos Testados** | Lógica | Comunicação | Routing real | Produção |

---

## 🚀 Plano de Migração

### Fase 1: Preparação (Sprint 1)
- [ ] Criar estrutura de diretórios (unit/integration/e2e)
- [ ] Configurar profile Maven para integration-tests
- [ ] Documentar setup de Ollama para desenvolvedores
- [ ] Adicionar dependências necessárias (Testcontainers, etc)

### Fase 2: Refatoração Unit Tests (Sprint 1)
- [ ] Mover testes atuais para `unit/` package
- [ ] Renomear `LLMServiceManagerTest` → `LLMServiceManagerUnitTest`
- [ ] Adicionar tags `@Tag("unit")`
- [ ] **Manter** testes unitários com Mockito

### Fase 3: Integration Tests (Sprint 2)
- [ ] Criar `providers/OllamaProviderTest`
- [ ] Criar `providers/LMStudioProviderTest`
- [ ] Criar `providers/ProviderCompatibilityTest`
- [ ] Criar `strategies/FailoverStrategyIntegrationTest`
- [ ] Criar `strategies/ModelBasedStrategyIntegrationTest`
- [ ] Criar `strategies/RoundRobinStrategyIntegrationTest`
- [ ] Criar `strategies/SpecializedStrategyIntegrationTest`
- [ ] Criar `utils/TestProviderUtils`
- [ ] Adicionar script `setup-ollama.sh`
- [ ] Adicionar script `setup-lmstudio.sh`
- [ ] Adicionar script `check-providers.sh`

### Fase 4: CI/CD (Sprint 2)
- [ ] Configurar GitHub Actions com Ollama (profile: integration-tests-ollama)
- [ ] Adicionar Testcontainers (opcional)
- [ ] Configurar reports de cobertura separados
- [ ] Documentar setup dual-provider para devs locais

### Fase 5: E2E Tests (Sprint 3)
- [ ] Criar testes E2E básicos
- [ ] Configurar ambiente staging
- [ ] Integrar com pipeline de release

---

## 📝 Convenções de Nomenclatura

### Packages
```
bor.tools.simplerag.service.llm.unit
bor.tools.simplerag.service.llm.integration
bor.tools.simplerag.service.llm.e2e
```

### Classes de Teste
```
*UnitTest.java       - Testes unitários
*IntegrationTest.java - Testes de integração
*E2ETest.java        - Testes end-to-end
```

### Tags JUnit
```java
@Tag("unit")         - Testes unitários (sempre executar)
@Tag("integration")  - Testes de integração (requer Ollama)
@Tag("e2e")          - Testes E2E (requer staging)
@Tag("slow")         - Testes lentos (> 5s)
```

---

## 🔧 Configuração de CI/CD

### GitHub Actions

**.github/workflows/test.yml:**
```yaml
name: Tests

on: [push, pull_request]

jobs:
  unit-tests:
    name: Unit Tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Run unit tests
        run: mvn test

  integration-tests:
    name: Integration Tests
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'

      - name: Setup Ollama
        run: |
          curl -fsSL https://ollama.ai/install.sh | sh
          ollama serve &
          sleep 5
          ollama pull tinyllama
          ollama pull nomic-embed-text

      - name: Run integration tests
        run: mvn verify -P integration-tests
```

---

## 📚 Recursos e Referências

### Setup de Ollama
- [Ollama Installation](https://ollama.ai/download)
- [Ollama Models](https://ollama.ai/library)
- [Ollama API](https://github.com/ollama/ollama/blob/main/docs/api.md)

### Frameworks de Teste
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Testcontainers](https://www.testcontainers.org/)
- [AssertJ](https://assertj.github.io/doc/)

### Maven
- [Maven Surefire Plugin](https://maven.apache.org/surefire/maven-surefire-plugin/) (unit tests)
- [Maven Failsafe Plugin](https://maven.apache.org/surefire/maven-failsafe-plugin/) (integration tests)

---

## ✅ Checklist de Implementação

### Developer Setup
- [ ] Instalar Ollama localmente
- [ ] Instalar LM Studio localmente
- [ ] Baixar modelos Ollama (llama2, tinyllama, nomic-embed-text)
- [ ] Baixar modelos LM Studio (qwen2.5-7b-instruct, nomic-embed-text)
- [ ] Iniciar Ollama em `localhost:11434`
- [ ] Iniciar LM Studio em `localhost:1234`
- [ ] Executar script `check-providers.sh` (verificar ambos online)
- [ ] Executar `mvn test` (deve passar - unit tests)
- [ ] Executar `mvn verify -P integration-tests-ollama` (deve passar - Ollama only)
- [ ] Executar `mvn verify -P integration-tests` (deve passar - dual provider)

### CI/CD Setup
- [ ] Configurar GitHub Actions com Ollama
- [ ] Configurar reports de cobertura
- [ ] Configurar notificações de falha
- [ ] Documentar processo no README

---

## 🎯 Resultados Esperados

### Cobertura de Testes
```
Unit Tests:                    27 testes  (~100% lógica de roteamento)
Integration Tests (Ollama):    8 testes   (~60% single provider)
Integration Tests (Dual):      15 testes  (~90% multi-provider)
E2E Tests:                     5 testes   (~50% fluxos principais)
───────────────────────────────────────────────────────────────────
TOTAL:                        55 testes
```

### Performance
```
Unit Tests:                    < 1 segundo     (sempre executar)
Integration Tests (Ollama):    ~15 segundos    (PR)
Integration Tests (Dual):      ~30-60 segundos (PR/merge)
E2E Tests:                     ~5 minutos      (noturno/release)
```

### Distribuição de Testes por Profile

| Profile | Tags | Testes | Duração | Uso |
|---------|------|--------|---------|-----|
| test (default) | unit | 27 | < 1s | Sempre, pre-commit |
| integration-tests-ollama | integration & ollama | 8 | ~15s | PR, CI/CD |
| integration-tests | integration | 23 | ~45s | Pre-merge, dev local |
| multi-provider-tests | multi-provider | 15 | ~30s | Testes específicos |
| e2e-tests | e2e | 5 | ~5min | Release, staging |

### Confiabilidade
- ✅ Detecta bugs de integração real com LLMs
- ✅ Valida compatibilidade com provedores heterogêneos (Ollama ≠ LM Studio)
- ✅ Testa cenários multi-provedor reais (failover, MODEL_BASED, etc)
- ✅ Testes reproduzíveis em qualquer ambiente (LAN)
- ✅ Feedback rápido em desenvolvimento
- ✅ Sem custos de APIs cloud

### Vantagens da Abordagem Dual-Provider

1. **Realismo**: Testa cenários reais de múltiplos provedores
2. **MODEL_BASED**: Valida roteamento por modelo com modelos realmente diferentes
3. **Failover**: Testa failover entre provedores heterogêneos
4. **Custo Zero**: Ambos rodam localmente, sem APIs pagas
5. **Velocidade**: Inferência local é rápida para modelos pequenos
6. **Controle**: Desenvolvedores têm controle total sobre o ambiente
7. **Abstração**: Valida que LLMServiceManager funciona com APIs diferentes

---

**Preparado por**: Claude Code
**Data**: 2025-10-14
**Atualizado**: 2025-10-14 (Dual-Provider: Ollama + LM Studio)
**Status**: ✅ Proposta Atualizada - Pronta para Implementação

