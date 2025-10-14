# Setup de Múltiplos Provedores LLM - Quick Start

**Versão**: 1.0
**Data**: 2025-10-13
**Status**: ✅ Pronto para Uso

---

## 🎯 O Que Foi Implementado

Sistema completo de gerenciamento de múltiplos provedores LLM com:

✅ **Configuração Primária e Secundária**
✅ **6 Estratégias de Uso Diferentes**
✅ **Failover Automático**
✅ **Load Balancing**
✅ **Estatísticas e Monitoramento**
✅ **Tratamento de Erros Robusto**

---

## 🚀 Quick Start

### 1. Configuração Básica (Sem Secundário)

**Arquivo**: `.env` ou `application.properties`

```properties
# Apenas provedor primário (LM Studio local)
llmservice.provider.name=LM_STUDIO
llmservice.provider.embedding.model=text-embedding-nomic-embed-text-v1.5@q8_0
llmservice.provider.embedding.dimension=768
llmservice.provider.api.url=http://localhost:1234/v1

# Secundário desabilitado
llmservice.provider2.enabled=false
llmservice.strategy=PRIMARY_ONLY
```

### 2. Configuração com Backup (Recomendado para Produção)

```properties
# Primary: LM Studio local (rápido, grátis)
llmservice.provider.name=LM_STUDIO
llmservice.provider.embedding.model=text-embedding-nomic-embed-text-v1.5@q8_0
llmservice.provider.embedding.dimension=768
llmservice.provider.api.url=http://localhost:1234/v1

# Secondary: OpenAI backup (confiável, pago)
llmservice.provider2.enabled=true
llmservice.provider2.name=OPENAI
llmservice.provider2.embedding.model=text-embedding-ada-002
llmservice.provider2.embedding.dimension=1536
llmservice.provider2.api.key=${OPENAI_API_KEY}

# Estratégia: Failover automático
llmservice.strategy=FAILOVER
llmservice.failover.max-retries=3
```

### 3. Uso no Código

```java
@Service
public class MyService {

    @Autowired
    private LLMServiceManager llmManager;

    public void processDocument(String text) {
        // Gera embedding com failover automático
        float[] embedding = llmManager.generateEmbedding(text);

        // Use o embedding...
    }
}
```

**Pronto!** O sistema automaticamente:
- Usa o provedor primário (LM Studio)
- Se falhar, usa o secundário (OpenAI)
- Registra estatísticas
- Trata erros

---

## 📋 Arquivos Criados

| Arquivo | Descrição |
|---------|-----------|
| `MultiLLMServiceConfig.java` | Configuração Spring para múltiplos provedores |
| `LLMServiceManager.java` | Gerenciador com estratégias e failover |
| `LLMServiceStrategy.java` | Enum com 6 estratégias disponíveis |
| `LLMServiceException.java` | Exceções tipadas para tratamento de erros |
| `LLMServiceManagerTest.java` | Testes unitários completos |
| `MULTI_LLM_PROVIDER_GUIDE.md` | Documentação detalhada |
| `.env.example` | Template de configuração |

---

## 🎨 Estratégias Disponíveis

### 1. PRIMARY_ONLY
Usa apenas o primário. Ideal para desenvolvimento.

```properties
llmservice.strategy=PRIMARY_ONLY
```

### 2. FAILOVER (Recomendado)
Primário com backup automático. Ideal para produção.

```properties
llmservice.strategy=FAILOVER
```

**Fluxo**:
```
Request → Primary
  ├─ ✅ Success → Return
  └─ ❌ Failure → Secondary
      ├─ ✅ Success → Return
      └─ ❌ Failure → Exception
```

### 3. ROUND_ROBIN
Distribui carga alternadamente.

```properties
llmservice.strategy=ROUND_ROBIN
```

### 4. SPECIALIZED
Embeddings no primário, completions no secundário.

```properties
llmservice.strategy=SPECIALIZED
```

### 5. DUAL_VERIFICATION
Executa em ambos e compara resultados (QA).

```properties
llmservice.strategy=DUAL_VERIFICATION
```

### 6. SMART_ROUTING
Roteia baseado na complexidade da query.

```properties
llmservice.strategy=SMART_ROUTING
```

---

## 📊 Monitoramento

### Endpoint de Estatísticas

```java
@RestController
public class MonitoringController {

    @Autowired
    private LLMServiceManager llmManager;

    @GetMapping("/api/llm/stats")
    public LLMServiceStats getStats() {
        return llmManager.getStatistics();
    }
}
```

**Resposta**:
```json
{
  "providerCount": 2,
  "primaryRequests": 1250,
  "secondaryRequests": 42,
  "failoverEvents": 15,
  "totalRequests": 1292,
  "secondaryUsagePercentage": 3.2
}
```

### Health Check

```java
boolean primaryHealthy = llmManager.isProviderHealthy(0);
boolean secondaryHealthy = llmManager.isProviderHealthy(1);
```

---

## 🔧 Exemplos de Configuração

### Desenvolvimento (Grátis)

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

# Secondary: OpenAI
llmservice.provider2.enabled=true
llmservice.provider2.name=OPENAI
llmservice.provider2.api.key=${OPENAI_API_KEY}

llmservice.strategy=FAILOVER
llmservice.failover.max-retries=3
```

### Híbrido Local + Cloud

```properties
# Primary: Local (embeddings)
llmservice.provider.name=OLLAMA
llmservice.provider.embedding.model=nomic-embed-text

# Secondary: Cloud (completions)
llmservice.provider2.enabled=true
llmservice.provider2.name=OPENAI
llmservice.provider2.llm.models=gpt-4

llmservice.strategy=SPECIALIZED
```

---

## 🐛 Troubleshooting

### Problema: Secondary não é usado

**Causa**: Não está habilitado

**Solução**:
```properties
llmservice.provider2.enabled=true  # ← Certifique-se que está true
```

### Problema: Custos muito altos

**Causa**: Estratégia DUAL_VERIFICATION ou ROUND_ROBIN

**Solução**: Mude para FAILOVER ou SMART_ROUTING
```properties
llmservice.strategy=FAILOVER  # Só usa secondary em falhas
```

### Problema: Primary sempre falha

**Verificar**:
1. LM Studio está rodando?
2. URL está correta?
3. Modelo está carregado?

```bash
# Teste manual
curl http://localhost:1234/v1/models
```

---

## 📚 Documentação Completa

Consulte:
- **`MULTI_LLM_PROVIDER_GUIDE.md`** - Guia completo com todos os detalhes
- **`LLM_SERVICE_CONFIGURATION.md`** - Configuração básica (single provider)

---

## 🧪 Testes

```bash
# Teste de configuração
./mvnw test -Dtest=LLMServiceManagerTest

# Teste de conectividade
./mvnw test -Dtest=LLMServiceConfigTest
```

---

## 🎯 Casos de Uso Recomendados

| Cenário | Estratégia | Primary | Secondary |
|---------|-----------|---------|-----------|
| **Desenvolvimento** | PRIMARY_ONLY | LM_STUDIO | - |
| **Produção** | FAILOVER | LM_STUDIO | OPENAI |
| **Alta Carga** | ROUND_ROBIN | OLLAMA | LM_STUDIO |
| **Custo Otimizado** | SMART_ROUTING | Local | Cloud |
| **Máxima Qualidade** | FAILOVER | GPT-4 | Claude |
| **QA/Testing** | DUAL_VERIFICATION | Provider A | Provider B |

---

## ✅ Checklist de Setup

- [ ] Copiar `.env.example` para `.env`
- [ ] Configurar provedor primário
- [ ] Decidir se precisa de secundário
- [ ] Se sim, configurar provedor secundário e habilitar
- [ ] Escolher estratégia apropriada
- [ ] Configurar API keys (se necessário)
- [ ] Testar conectividade
- [ ] Deploy!

---

## 🔐 Segurança

**Importante**: Nunca commite API keys!

```bash
# .gitignore deve conter:
.env
*.env
application-local.properties
```

Use variáveis de ambiente:
```properties
llmservice.provider2.api.key=${OPENAI_API_KEY}
```

---

## 📞 Suporte

**Problemas?**
1. Consulte `MULTI_LLM_PROVIDER_GUIDE.md`
2. Verifique logs de inicialização
3. Teste health checks
4. Verifique estatísticas

**Logs Esperados**:
```
INFO  MultiLLMServiceConfig - Initializing Primary LLMService
INFO  MultiLLMServiceConfig -   Provider: LM_STUDIO
INFO  MultiLLMServiceConfig - Primary LLMService initialized successfully
INFO  MultiLLMServiceConfig - Initializing Secondary LLMService
INFO  MultiLLMServiceConfig -   Provider: OPENAI
INFO  MultiLLMServiceConfig - Secondary LLMService initialized successfully
INFO  MultiLLMServiceConfig - LLMServiceManager initialized with 2 provider(s)
```

---

**Preparado por**: Claude Code
**Data**: 2025-10-13
**Versão**: 1.0
**Status**: ✅ Pronto para Produção
