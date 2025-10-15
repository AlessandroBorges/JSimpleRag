# Guia de Múltiplos Provedores LLM

**Data**: 2025-10-13
**Status**: ✅ Implementado

---

## Visão Geral

O JSimpleRag suporta configuração de múltiplos provedores LLM (primário e secundário) com estratégias flexíveis de uso:

- **Backup/Failover**: Provedor secundário como backup quando o primário falha
- **Load Balancing**: Distribuição de carga entre provedores
- **Especialização**: Provedores diferentes para tarefas diferentes
- **Verificação Dupla**: Validação cruzada de resultados
- **Roteamento Inteligente**: Escolha automática baseada na complexidade

---

## Arquitetura

```
┌─────────────────────────────────────┐
│    LLMServiceManager (Bean)         │
│  - Gerencia estratégias             │
│  - Failover automático              │
│  - Estatísticas de uso              │
└──────────┬──────────────────────────┘
           │
    ┌──────┴──────┐
    │             │
┌───▼────┐   ┌───▼────┐
│Primary │   │Secondary│
│Provider│   │Provider│
│(Local) │   │(Cloud) │
└────────┘   └────────┘
```

---

## Configuração

### application.properties

```properties
# ======================================
# Primary Provider (LM Studio Local)
# ======================================
llmservice.provider.name=LM_STUDIO
llmservice.provider.llm.models=qwen/qwen3-1.7b
llmservice.provider.embedding.model=text-embedding-nomic-embed-text-v1.5@q8_0
llmservice.provider.embedding.dimension=768
llmservice.provider.api.url=http://localhost:1234/v1
llmservice.provider.api.key=

# ======================================
# Secondary Provider (OpenAI Cloud)
# ======================================
llmservice.provider2.enabled=true
llmservice.provider2.name=OPENAI
llmservice.provider2.llm.models=gpt-3.5-turbo,gpt-4
llmservice.provider2.embedding.model=text-embedding-ada-002
llmservice.provider2.embedding.dimension=1536
llmservice.provider2.api.url=https://api.openai.com/v1
llmservice.provider2.api.key=${OPENAI_API_KEY}

# ======================================
# Strategy Configuration
# ======================================
llmservice.strategy=FAILOVER
llmservice.failover.max-retries=3
llmservice.failover.timeout-seconds=30
```

### Variáveis de Ambiente (.env)

```bash
# Primary Provider
LLM_PROVIDER_CLASS=LM_STUDIO
LLM_MODELS=qwen/qwen3-1.7b
EMBEDDING_MODEL=text-embedding-nomic-embed-text-v1.5@q8_0
EMBEDDING_DIMENSION=768
LLM_API_URL=http://localhost:1234/v1

# Secondary Provider
LLM_PROVIDER2_ENABLED=true
LLM_PROVIDER2_CLASS=OPENAI
LLM_PROVIDER2_MODELS=gpt-3.5-turbo
LLM_PROVIDER2_EMBEDDING_MODEL=text-embedding-ada-002
LLM_PROVIDER2_EMBEDDING_DIMENSION=1536
LLM_PROVIDER2_API_URL=https://api.openai.com/v1
LLM_PROVIDER2_API_KEY=sk-...

# Strategy
LLM_STRATEGY=FAILOVER
LLM_MAX_RETRIES=3
LLM_TIMEOUT_SECONDS=30
```

---

## Estratégias Disponíveis

### 1. PRIMARY_ONLY
**Uso**: Apenas o provedor primário, secundário ignorado

```properties
llmservice.strategy=PRIMARY_ONLY
```

**Caso de Uso**:
- Ambiente de desenvolvimento
- Testes locais
- Custos mínimos

**Comportamento**:
```
Request → Primary Provider → Response
```

---

### 2. FAILOVER (Padrão)
**Uso**: Primário com backup automático

```properties
llmservice.strategy=FAILOVER
llmservice.failover.max-retries=3
```

**Caso de Uso**:
- Alta disponibilidade
- Backup automático
- Produção confiável

**Comportamento**:
```
Request → Primary Provider
   ├─ Success → Response
   └─ Failure → Secondary Provider
       ├─ Success → Response
       └─ Failure → Exception
```

**Exemplo de Uso**:
```java
@Service
public class DocumentService {

    @Autowired
    private LLMServiceManager llmManager;

    public float[] generateEmbedding(String text) {
        // Tenta primário, faz failover se falhar
        return llmManager.generateEmbedding(text);
    }
}
```

---

### 3. ROUND_ROBIN
**Uso**: Distribuição alternada entre provedores

```properties
llmservice.strategy=ROUND_ROBIN
```

**Caso de Uso**:
- Balanceamento de carga
- Distribuição de custos
- Evitar rate limits

**Comportamento**:
```
Request 1 → Primary Provider
Request 2 → Secondary Provider
Request 3 → Primary Provider
Request 4 → Secondary Provider
...
```

**Estatísticas Esperadas**:
```
Primary: 50% das requisições
Secondary: 50% das requisições
```

---

### 4. SPECIALIZED
**Uso**: Provedores especializados por tipo de operação

```properties
llmservice.strategy=SPECIALIZED
```

**Caso de Uso**:
- Embeddings locais (rápido) + Completions cloud (qualidade)
- Modelos especializados por tarefa

**Comportamento**:
```
Embedding Request → Primary Provider (fast local)
Completion Request → Secondary Provider (powerful cloud)
```

**Exemplo de Configuração**:
```properties
# Primary: Local para embeddings rápidos
llmservice.provider.name=LM_STUDIO
llmservice.provider.embedding.model=nomic-embed-text

# Secondary: Cloud para completions complexos
llmservice.provider2.name=OPENAI
llmservice.provider2.llm.models=gpt-4
```

---

### 5. DUAL_VERIFICATION
**Uso**: Executa em ambos e compara resultados

```properties
llmservice.strategy=DUAL_VERIFICATION
```

**Caso de Uso**:
- Quality assurance
- A/B testing
- Validação de consistência

**Comportamento**:
```
Request → Primary Provider   ─┐
       → Secondary Provider ─┤
                             ├─ Compare → Response
                             │  (log similarity)
```

**Custo**: 2x (executa em ambos)

**Exemplo de Log**:
```
INFO  LLMServiceManager - Dual verification: Vector similarity = 0.94
WARN  LLMServiceManager - Dual verification: Low similarity (0.65)
```

---

### 6. SMART_ROUTING
**Uso**: Roteamento baseado na complexidade

```properties
llmservice.strategy=SMART_ROUTING
```

**Caso de Uso**:
- Otimização de custos
- Performance híbrida
- Ambiente misto local/cloud

**Comportamento**:
```
Simple Query (< 1000 chars) → Primary (fast, free)
Complex Query (> 1000 chars) → Secondary (powerful, paid)

Keywords "explain", "analyze" → Secondary
Normal queries → Primary
```

**Heurísticas**:
- Comprimento do texto
- Palavras-chave complexas
- Tipo de operação

---

### 7. MODEL_BASED ⭐ **NOVO**
**Uso**: Roteamento automático pelo nome do modelo

```properties
llmservice.strategy=MODEL_BASED
```

**Caso de Uso**:
- Múltiplos provedores com modelos diferentes
- Roteamento transparente baseado no modelo solicitado
- Aplicações que usam modelos específicos dinamicamente

**Comportamento**:
```
Request com model="gpt-4" → Busca provedor com GPT-4
Request com model="llama2" → Busca provedor com Llama2
Request com model="mistral" → Busca provedor com Mistral

Se modelo não encontrado → Fallback para Primary
```

**Exemplo de Configuração**:
```properties
# Primary: LM Studio com modelos locais
llmservice.provider.name=LM_STUDIO
llmservice.provider.llm.models=llama2,mistral,qwen

# Secondary: OpenAI com modelos cloud
llmservice.provider2.enabled=true
llmservice.provider2.name=OPENAI
llmservice.provider2.llm.models=gpt-3.5-turbo,gpt-4

# Estratégia
llmservice.strategy=MODEL_BASED
```

**Uso no Código**:
```java
@Service
public class ChatService {
    @Autowired
    private LLMServiceManager llmManager;

    public String chat(String message, String preferredModel) {
        // Automaticamente roteia para o provedor correto
        return llmManager.generateCompletion(
            "You are a helpful assistant",
            message,
            preferredModel  // "gpt-4" ou "llama2"
        );
    }
}
```

**Recursos Adicionais**:

Você pode consultar quais modelos estão disponíveis:

```java
// Listar todos os modelos de todos os provedores
List<String> allModels = llmManager.getAllModels();
// Exemplo: ["llama2", "mistral", "gpt-3.5-turbo", "gpt-4"]

// Listar modelos por provedor
Map<Integer, List<String>> modelsByProvider = llmManager.getAllAvailableModels();
// {0: ["llama2", "mistral"], 1: ["gpt-3.5-turbo", "gpt-4"]}

// Descobrir qual provedor tem um modelo específico
int providerIndex = llmManager.findProviderIndexByModel("gpt-4");
// Retorna: 1 (secondary)

// Obter o serviço diretamente
LLMService service = llmManager.getServiceByModel("llama2");
```

**Vantagens**:
- ✅ Roteamento transparente - não precisa saber qual provedor tem qual modelo
- ✅ Flexibilidade - fácil trocar entre modelos sem mudar código
- ✅ Suporte a partial matching - "gpt-4" encontra "gpt-4-turbo"
- ✅ Fallback automático - usa primary se modelo não encontrado
- ✅ Case-insensitive - "GPT-4" == "gpt-4"

**Matching Inteligente**:
O sistema usa matching inteligente para encontrar modelos:
- Exact match: "gpt-4" == "gpt-4" ✅
- Contains: "gpt-4" encontra "gpt-4-turbo" ✅
- Partial: "llama" encontra "llama2" ✅
- Case-insensitive: "GPT-4" == "gpt-4" ✅

---

## Cenários de Uso Comuns

### Cenário 1: Alta Disponibilidade (Failover)

**Configuração**:
```properties
# Primary: LM Studio local
llmservice.provider.name=LM_STUDIO
llmservice.provider.api.url=http://localhost:1234/v1

# Secondary: OpenAI backup
llmservice.provider2.enabled=true
llmservice.provider2.name=OPENAI
llmservice.provider2.api.key=${OPENAI_API_KEY}

# Estratégia
llmservice.strategy=FAILOVER
```

**Comportamento**:
- Usa LM Studio local (rápido, grátis)
- Se LM Studio estiver offline → usa OpenAI automaticamente
- Logs informam sobre failover

**Vantagens**:
- Máxima disponibilidade
- Custos mínimos (só paga cloud quando necessário)
- Sem downtime

---

### Cenário 2: Distribuição de Custos (Round-Robin)

**Configuração**:
```properties
# Primary: Provider A (mais barato)
llmservice.provider.name=OLLAMA
llmservice.provider.embedding.model=llama2

# Secondary: Provider B (mais barato)
llmservice.provider2.enabled=true
llmservice.provider2.name=LM_STUDIO
llmservice.provider2.embedding.model=mistral

# Estratégia
llmservice.strategy=ROUND_ROBIN
```

**Comportamento**:
- Alterna entre provedores
- Distribui rate limits
- Evita sobrecarga de um único provedor

**Vantagens**:
- Distribui custos
- Evita rate limits
- Load balancing

---

### Cenário 3: Híbrido Local + Cloud (Specialized)

**Configuração**:
```properties
# Primary: Local para embeddings
llmservice.provider.name=LM_STUDIO
llmservice.provider.embedding.model=nomic-embed-text
llmservice.provider.embedding.dimension=768

# Secondary: Cloud para completions
llmservice.provider2.enabled=true
llmservice.provider2.name=OPENAI
llmservice.provider2.llm.models=gpt-4
llmservice.provider2.embedding.dimension=1536

# Estratégia
llmservice.strategy=SPECIALIZED
```

**Comportamento**:
- Embeddings → Local (rápido, grátis, privado)
- Completions complexos → Cloud (qualidade superior)

**Vantagens**:
- Performance + Qualidade
- Custos otimizados
- Privacidade de dados (embeddings locais)

---

### Cenário 4: Quality Assurance (Dual Verification)

**Configuração**:
```properties
# Primary: Model A
llmservice.provider.name=OPENAI
llmservice.provider.embedding.model=text-embedding-ada-002

# Secondary: Model B
llmservice.provider2.enabled=true
llmservice.provider2.name=COHERE
llmservice.provider2.embedding.model=embed-multilingual-v3.0

# Estratégia
llmservice.strategy=DUAL_VERIFICATION
```

**Comportamento**:
- Executa em ambos os provedores
- Compara similaridade dos embeddings
- Alerta se diferença significativa

**Uso**:
- Validação de modelos
- Testes A/B
- Detecção de anomalias

**Custo**: 2x (paga ambos os provedores)

---

### Cenário 5: Roteamento por Modelo (Model-Based)

**Configuração**:
```properties
# Primary: LM Studio com modelos locais
llmservice.provider.name=LM_STUDIO
llmservice.provider.llm.models=llama2,mistral-7b,qwen
llmservice.provider.embedding.model=nomic-embed-text
llmservice.provider.api.url=http://localhost:1234/v1

# Secondary: OpenAI com modelos cloud
llmservice.provider2.enabled=true
llmservice.provider2.name=OPENAI
llmservice.provider2.llm.models=gpt-3.5-turbo,gpt-4,gpt-4-turbo
llmservice.provider2.embedding.model=text-embedding-ada-002
llmservice.provider2.api.key=${OPENAI_API_KEY}

# Estratégia
llmservice.strategy=MODEL_BASED
```

**Comportamento**:
- Request com `model="llama2"` → Automaticamente usa LM Studio (Primary)
- Request com `model="gpt-4"` → Automaticamente usa OpenAI (Secondary)
- Request com `model="unknown"` → Fallback para Primary

**Vantagens**:
- Roteamento transparente
- Sem lógica de if/else no código
- Fácil adicionar novos modelos
- Suporta múltiplos provedores com overlapping models

**Exemplo de Uso**:
```java
@RestController
public class ChatController {
    @Autowired
    private LLMServiceManager llmManager;

    @PostMapping("/chat")
    public String chat(@RequestParam String message,
                      @RequestParam(defaultValue = "llama2") String model) {
        // Roteamento automático baseado no modelo
        return llmManager.generateCompletion(
            "You are a helpful assistant",
            message,
            model
        );
    }

    @GetMapping("/models")
    public List<String> getAvailableModels() {
        // Lista todos os modelos disponíveis
        return llmManager.getAllModels();
    }
}
```

**Quando Usar**:
- Aplicações multi-tenant com preferências de modelo por usuário
- APIs que expõem múltiplos modelos aos clientes
- Ambientes com mix de modelos locais e cloud
- Desenvolvimento/teste com troca frequente de modelos

---

## Uso no Código

### Injeção Básica

```java
@Service
public class SearchService {

    @Autowired
    private LLMServiceManager llmManager;

    public List<Document> search(String query) {
        // Gera embedding com estratégia configurada
        float[] queryVector = llmManager.generateEmbedding(query);

        // Usa vector na busca
        return documentRepository.searchByVector(queryVector);
    }
}
```

### Com Tratamento de Erros

```java
@Service
public class DocumentProcessor {

    @Autowired
    private LLMServiceManager llmManager;

    public void processDocument(Document doc) {
        try {
            float[] embedding = llmManager.generateEmbedding(doc.getText());
            doc.setEmbedding(embedding);

        } catch (LLMServiceException e) {
            log.error("Failed to generate embedding: {}", e.getUserMessage());

            // Verifica tipo de erro
            switch (e.getErrorType()) {
                case RATE_LIMIT:
                    // Aguarda e tenta novamente
                    Thread.sleep(60000);
                    break;

                case AUTH_ERROR:
                    // Problema de configuração
                    alertAdmin("API key invalid");
                    break;

                case SERVICE_UNAVAILABLE:
                    // Todos os provedores offline
                    queueForRetry(doc);
                    break;
            }
        }
    }
}
```

### Monitoramento de Estatísticas

```java
@RestController
@RequestMapping("/api/v1/admin/llm")
public class LLMAdminController {

    @Autowired
    private LLMServiceManager llmManager;

    @GetMapping("/stats")
    public ResponseEntity<LLMServiceStats> getStats() {
        LLMServiceStats stats = llmManager.getStatistics();
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/reset-stats")
    public ResponseEntity<Void> resetStats() {
        llmManager.resetStatistics();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Boolean>> healthCheck() {
        Map<String, Boolean> health = new HashMap<>();
        health.put("primary", llmManager.isProviderHealthy(0));

        if (llmManager.getProviderCount() > 1) {
            health.put("secondary", llmManager.isProviderHealthy(1));
        }

        return ResponseEntity.ok(health);
    }
}
```

**Resposta de `/stats`**:
```json
{
  "providerCount": 2,
  "primaryRequests": 850,
  "secondaryRequests": 23,
  "failoverEvents": 12,
  "totalRequests": 873,
  "secondaryUsagePercentage": 2.6
}
```

---

## Provedores Suportados

| Provider | Embedding | Completion | Local | API Key | Dimensões Comuns |
|----------|-----------|------------|-------|---------|------------------|
| **LM_STUDIO** | ✅ | ✅ | ✅ | ❌ | 768, 1024 |
| **OLLAMA** | ✅ | ✅ | ✅ | ❌ | 768, 1024, 4096 |
| **OPENAI** | ✅ | ✅ | ❌ | ✅ | 1536, 3072 |
| **ANTHROPIC** | ❌ | ✅ | ❌ | ✅ | - |
| **COHERE** | ✅ | ✅ | ❌ | ✅ | 768, 1024, 4096 |
| **HUGGINGFACE** | ✅ | ✅ | ❌ | ✅ | Varia |

---

## Exemplos de Configuração por Caso de Uso

### Desenvolvimento Local (Grátis)

```properties
llmservice.provider.name=LM_STUDIO
llmservice.provider2.enabled=false
llmservice.strategy=PRIMARY_ONLY
```

### Produção (Alta Disponibilidade)

```properties
# Primary: LM Studio
llmservice.provider.name=LM_STUDIO
llmservice.provider.api.url=http://llm-server:1234/v1

# Secondary: OpenAI backup
llmservice.provider2.enabled=true
llmservice.provider2.name=OPENAI
llmservice.provider2.api.key=${OPENAI_API_KEY}

llmservice.strategy=FAILOVER
llmservice.failover.max-retries=3
```

### Custo Otimizado

```properties
# Primary: Ollama local (grátis)
llmservice.provider.name=OLLAMA
llmservice.provider.api.url=http://localhost:11434

# Secondary: OpenAI only for complex (pago)
llmservice.provider2.enabled=true
llmservice.provider2.name=OPENAI
llmservice.provider2.api.key=${OPENAI_API_KEY}

llmservice.strategy=SMART_ROUTING
```

### Máxima Qualidade

```properties
# Primary: GPT-4
llmservice.provider.name=OPENAI
llmservice.provider.llm.models=gpt-4
llmservice.provider.api.key=${OPENAI_API_KEY}

# Secondary: Claude backup
llmservice.provider2.enabled=true
llmservice.provider2.name=ANTHROPIC
llmservice.provider2.llm.models=claude-3-opus
llmservice.provider2.api.key=${ANTHROPIC_API_KEY}

llmservice.strategy=FAILOVER
```

---

## Logs e Monitoramento

### Logs de Inicialização

```
INFO  MultiLLMServiceConfig - Initializing Primary LLMService
INFO  MultiLLMServiceConfig -   Provider: LM_STUDIO
INFO  MultiLLMServiceConfig -   Models: qwen/qwen3-1.7b
INFO  MultiLLMServiceConfig - Primary LLMService initialized successfully

INFO  MultiLLMServiceConfig - Initializing Secondary LLMService
INFO  MultiLLMServiceConfig -   Provider: OPENAI
INFO  MultiLLMServiceConfig - Secondary LLMService initialized successfully

INFO  MultiLLMServiceConfig - Initializing LLMServiceManager
INFO  MultiLLMServiceConfig -   Strategy: FAILOVER
INFO  MultiLLMServiceConfig -   Max Retries: 3
INFO  MultiLLMServiceConfig -   Total Providers: 2
INFO  MultiLLMServiceConfig - LLMServiceManager initialized with 2 provider(s)
```

### Logs de Failover

```
WARN  LLMServiceManager - Primary LLM service failed: Connection refused. Trying secondary...
INFO  LLMServiceManager - Successfully failed over to secondary provider
```

### Logs de Dual Verification

```
INFO  LLMServiceManager - Dual verification: Vector similarity = 0.94
WARN  LLMServiceManager - Dual verification: Low similarity (0.65)
```

---

## Troubleshooting

### Problema: Secondary não está sendo usado

**Causa**: `llmservice.provider2.enabled=false`

**Solução**:
```properties
llmservice.provider2.enabled=true
```

### Problema: Failover não funciona

**Verificar**:
1. Secondary habilitado?
2. Estratégia é FAILOVER?
3. Secondary configurado corretamente?

```bash
# Logs devem mostrar:
INFO - Initializing Secondary LLMService
```

### Problema: Custos muito altos

**Solução**: Use estratégia SMART_ROUTING ou SPECIALIZED

```properties
llmservice.strategy=SMART_ROUTING
# Ou
llmservice.strategy=SPECIALIZED
```

### Problema: Latência alta

**Causa**: Estratégia DUAL_VERIFICATION

**Solução**: Mude para FAILOVER ou PRIMARY_ONLY

---

## Performance e Custos

### Comparação de Estratégias

| Estratégia | Latência | Custo | Disponibilidade | Uso | Flexibilidade |
|------------|----------|-------|-----------------|-----|---------------|
| PRIMARY_ONLY | ⚡ Baixa | 💰 Mínimo | ⚠️ Média | Dev | ⭐ Baixa |
| FAILOVER | ⚡ Baixa* | 💰 Baixo | ✅ Alta | Prod | ⭐⭐ Média |
| ROUND_ROBIN | ⚡⚡ Média | 💰💰 Médio | ✅ Alta | Scale | ⭐⭐ Média |
| SPECIALIZED | ⚡ Variável | 💰 Otimizado | ✅ Alta | Híbrido | ⭐⭐⭐ Alta |
| DUAL_VERIFICATION | 🐌 2x | 💰💰 2x | ✅ Alta | QA | ⭐⭐ Média |
| SMART_ROUTING | ⚡ Baixa | 💰 Otimizado | ✅ Alta | Prod+ | ⭐⭐⭐ Alta |
| **MODEL_BASED** ⭐ | ⚡ Baixa | 💰 Variável | ✅ Alta | Multi-Model | ⭐⭐⭐⭐ Muito Alta |

*Baixa em operação normal, média durante failover

---

## Próximos Passos

1. **Métricas Avançadas**: Integração com Prometheus
2. **Circuit Breaker**: Desabilitar provedor com falhas frequentes
3. **Cache de Embeddings**: Evitar regeneração
4. **Rate Limiting**: Controle de chamadas por provedor
5. **Dynamic Configuration**: Mudar estratégia em runtime

---

**Preparado por**: Claude Code
**Data**: 2025-10-13
**Versão**: 1.0
