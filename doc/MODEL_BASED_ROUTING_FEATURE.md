# Feature: Roteamento Baseado em Modelo (MODEL_BASED Strategy)

**Data**: 2025-10-14
**Status**: ✅ Implementado
**Versão**: 1.0

---

## 📋 Resumo

Implementação de uma nova estratégia de roteamento para o `LLMServiceManager` que permite selecionar automaticamente o provedor correto baseado no nome do modelo solicitado.

---

## 🎯 Objetivo

Permitir que o sistema escolha automaticamente o provedor LLM adequado baseado no nome do modelo especificado na requisição, sem necessidade de lógica condicional no código da aplicação.

---

## ✨ Funcionalidades Implementadas

### 1. Nova Estratégia: `MODEL_BASED`

Adicionada ao enum `LLMServiceStrategy`:

```java
/**
 * Route requests based on the model name specified.
 * Automatically selects the provider that supports the requested model.
 *
 * Flow:
 * 1. Check if primary provider supports the model
 * 2. If not, check secondary provider
 * 3. If model not found, fallback to primary
 *
 * Use case: Multiple providers with different models
 * Example: Local models (llama, mistral) + Cloud models (gpt-4, claude)
 */
MODEL_BASED
```

### 2. Métodos de Busca de Provedores

#### `findServiceByModel(String modelName)`
- Busca o provedor que suporta o modelo especificado
- Realiza matching inteligente (case-insensitive, partial matching)
- Fallback automático para primary se modelo não encontrado

#### `serviceSupportsModel(LLMService service, String normalizedModelName)`
- Verifica se um serviço suporta determinado modelo
- Suporta exact match e partial match
- Case-insensitive

### 3. APIs Públicas para Descoberta de Modelos

#### `getAllModels()`
Retorna lista plana de todos os modelos disponíveis:

```java
List<String> allModels = llmManager.getAllModels();
// Exemplo: ["llama2", "mistral", "gpt-3.5-turbo", "gpt-4"]
```

#### `getAllAvailableModels()`
Retorna modelos organizados por provedor:

```java
Map<Integer, List<String>> modelsByProvider = llmManager.getAllAvailableModels();
// {0: ["llama2", "mistral"], 1: ["gpt-3.5-turbo", "gpt-4"]}
```

#### `findProviderIndexByModel(String modelName)`
Descobre qual provedor suporta um modelo:

```java
int providerIndex = llmManager.findProviderIndexByModel("gpt-4");
// Retorna: 0 (primary), 1 (secondary), ou -1 (não encontrado)
```

#### `getServiceByModel(String modelName)`
Obtém o serviço diretamente:

```java
LLMService service = llmManager.getServiceByModel("llama2");
// Retorna o LLMService que suporta o modelo, ou null
```

---

## 🔧 Uso

### Configuração

```properties
# Primary: LM Studio com modelos locais
llmservice.provider.name=LM_STUDIO
llmservice.provider.llm.models=llama2,mistral-7b,qwen
llmservice.provider.api.url=http://localhost:1234/v1

# Secondary: OpenAI com modelos cloud
llmservice.provider2.enabled=true
llmservice.provider2.name=OPENAI
llmservice.provider2.llm.models=gpt-3.5-turbo,gpt-4,gpt-4-turbo
llmservice.provider2.api.key=${OPENAI_API_KEY}

# Estratégia
llmservice.strategy=MODEL_BASED
```

### Código

```java
@Service
public class ChatService {
    @Autowired
    private LLMServiceManager llmManager;

    public String chat(String message, String preferredModel) {
        // Roteamento automático baseado no modelo
        return llmManager.generateCompletion(
            "You are a helpful assistant",
            message,
            preferredModel  // "gpt-4" ou "llama2"
        );
    }

    public List<String> getAvailableModels() {
        // Lista todos os modelos disponíveis
        return llmManager.getAllModels();
    }
}
```

---

## 🎨 Características do Matching

### 1. Exact Match
```
Solicitado: "gpt-4"
Disponível: "gpt-4"
Resultado: ✅ Match
```

### 2. Partial Match
```
Solicitado: "llama2"
Disponível: "llama2-7b"
Resultado: ✅ Match
```

```
Solicitado: "gpt-4-turbo"
Disponível: "gpt-4"
Resultado: ✅ Match
```

### 3. Case-Insensitive
```
Solicitado: "GPT-4"
Disponível: "gpt-4"
Resultado: ✅ Match
```

### 4. Fallback
```
Solicitado: "unknown-model"
Disponível: (nenhum)
Resultado: ⚠️ Fallback para Primary
```

---

## 📊 Comportamento

### Fluxo de Roteamento

```
Request (model="gpt-4")
  ↓
Buscar em Primary
  ↓
Modelo encontrado? → NÃO
  ↓
Buscar em Secondary
  ↓
Modelo encontrado? → SIM
  ↓
Usar Secondary Provider
```

### Logs

```
DEBUG LLMServiceManager - Model-based routing: Model 'gpt-4' found in secondary provider
INFO  LLMServiceStats - Secondary usage: 45% (90/200 requests)
```

```
WARN  LLMServiceManager - Model 'unknown-model' not found in any provider. Falling back to primary.
DEBUG LLMServiceManager - Model-based routing: No model specified, using primary
```

---

## 🧪 Testes

Foram criados 10 novos testes unitários cobrindo:

1. ✅ Modelo encontrado no provedor primário
2. ✅ Modelo encontrado no provedor secundário
3. ✅ Modelo não encontrado (fallback)
4. ✅ Partial matching
5. ✅ Case-insensitive matching
6. ✅ Completions com MODEL_BASED
7. ✅ `getAllModels()` retorna lista combinada
8. ✅ `getAllAvailableModels()` retorna por provedor
9. ✅ `findProviderIndexByModel()` retorna índice correto
10. ✅ `getServiceByModel()` retorna serviço correto

### Executar Testes

```bash
mvn test -Dtest=LLMServiceManagerTest#testModelBasedStrategy*
```

---

## 📈 Vantagens

1. **Roteamento Transparente**: Não precisa saber qual provedor tem qual modelo
2. **Flexibilidade**: Fácil trocar entre modelos sem mudar código
3. **Matching Inteligente**: Suporta partial e case-insensitive matching
4. **Fallback Automático**: Usa primary se modelo não encontrado
5. **Descoberta de Modelos**: APIs para listar modelos disponíveis
6. **Multi-tenant Ready**: Perfeito para aplicações com preferências de modelo por usuário

---

## 💡 Casos de Uso

### 1. API Multi-Modelo
```java
@PostMapping("/chat")
public String chat(@RequestParam String message,
                  @RequestParam(defaultValue = "llama2") String model) {
    return llmManager.generateCompletion("System", message, model);
}

@GetMapping("/models")
public List<String> getModels() {
    return llmManager.getAllModels();
}
```

### 2. Preferências de Usuário
```java
@Service
public class UserPreferenceService {
    public String chatWithUserPreferredModel(User user, String message) {
        String preferredModel = user.getPreferredModel();
        return llmManager.generateCompletion("System", message, preferredModel);
    }
}
```

### 3. A/B Testing
```java
@Service
public class ABTestService {
    public ComparisonResult compareModels(String query) {
        String resultA = llmManager.generateCompletion("System", query, "gpt-4");
        String resultB = llmManager.generateCompletion("System", query, "llama2");
        return new ComparisonResult(resultA, resultB);
    }
}
```

---

## 🔄 Integração com JSimpleLLM

A implementação usa os seguintes métodos da interface `LLMService`:

- `getRegisterdModelNames()`: Lista de nomes de modelos registrados
- `getInstalledModels()`: Mapa de modelos instalados

---

## 📝 Arquivos Modificados

1. `LLMServiceStrategy.java` - Nova estratégia MODEL_BASED
2. `LLMServiceManager.java` - Implementação de roteamento e APIs públicas
3. `LLMServiceManagerTest.java` - 10 novos testes unitários
4. `MULTI_LLM_PROVIDER_GUIDE.md` - Documentação completa
5. `MODEL_BASED_ROUTING_FEATURE.md` - Este documento

---

## 🎯 Próximos Passos (Opcional)

1. **Cache de Modelos**: Cache da lista de modelos disponíveis
2. **Métricas por Modelo**: Estatísticas de uso por modelo
3. **Model Aliases**: Suporte a aliases (e.g., "gpt-4" → "gpt-4-1106-preview")
4. **Priority Rules**: Regras de prioridade quando modelo está em múltiplos provedores
5. **Health Check por Modelo**: Verificar disponibilidade de modelos específicos

---

## 📚 Referências

- [MULTI_LLM_PROVIDER_GUIDE.md](MULTI_LLM_PROVIDER_GUIDE.md) - Guia completo
- [LLMServiceFactory_guide.md](LLMServiceFactory_guide.md) - JSimpleLLM integration
- [LLMServiceManager.java:350-573](src/main/java/bor/tools/simplerag/service/llm/LLMServiceManager.java) - Implementação

---

**Implementado por**: Claude Code
**Data**: 2025-10-14
**Status**: ✅ Pronto para Uso
