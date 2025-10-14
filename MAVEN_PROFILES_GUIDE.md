# Maven Test Profiles - Guia de Referência Rápida

**Data**: 2025-10-14
**Status**: ✅ Configurado

---

## 📋 Profiles Disponíveis

O projeto possui 7 profiles Maven configurados para diferentes cenários de teste:

| Profile ID | Descrição | Uso | Duração |
|-----------|-----------|-----|---------|
| **(default)** | Unit tests apenas | Desenvolvimento diário | < 5s |
| `integration-tests-ollama` | Integration com Ollama | CI/CD | ~15s |
| `integration-tests` | Integration com Ollama + LM Studio | Pre-merge | ~45s |
| `multi-provider-tests` | Apenas testes multi-provedor | Validação específica | ~30s |
| `e2e-tests` | End-to-end em staging | Release | ~5min |
| `all-tests` | Todos os testes | Validação completa | Variável |
| `skip-integration-tests` | Pula integration tests | Build rápido | < 5s |

---

## 🚀 Comandos de Execução

### 1. Unit Tests (Padrão)

```bash
# Apenas unit tests (sempre rápido)
mvn test

# Ou explicitamente
mvn clean test
```

**Quando usar**:
- Desenvolvimento diário
- Pre-commit hooks
- Sempre que modificar código

**Requisitos**: Nenhum (não precisa de Ollama ou LM Studio)

---

### 2. Integration Tests - Ollama Only

```bash
# Integration tests com apenas Ollama
mvn verify -P integration-tests-ollama

# Com clean
mvn clean verify -P integration-tests-ollama
```

**Quando usar**:
- CI/CD (GitHub Actions, GitLab CI, etc)
- Pull requests
- Quando LM Studio não está disponível

**Requisitos**:
- ✅ Ollama rodando em `localhost:11434`
- ✅ Modelos: `tinyllama`, `llama2`, `nomic-embed-text`

**Tags executadas**: `@Tag("integration") && @Tag("ollama")`

---

### 3. Integration Tests - Ollama + LM Studio (Completo)

```bash
# Integration tests completos (dual-provider)
mvn verify -P integration-tests

# Com limpeza
mvn clean verify -P integration-tests
```

**Quando usar**:
- Desenvolvimento local
- Pre-merge (antes de fazer merge)
- Validação completa de multi-provider

**Requisitos**:
- ✅ Ollama rodando em `localhost:11434`
- ✅ LM Studio rodando em `localhost:1234`
- ✅ Modelos Ollama: `tinyllama`, `llama2`, `nomic-embed-text`
- ✅ Modelos LM Studio: `qwen2.5-7b-instruct`, `nomic-embed-text`

**Tags executadas**: `@Tag("integration")` (exclui `e2e`)

---

### 4. Multi-Provider Tests Only

```bash
# Apenas testes que requerem ambos provedores
mvn verify -P multi-provider-tests
```

**Quando usar**:
- Testar especificamente failover
- Validar MODEL_BASED routing
- Testes de compatibilidade entre provedores

**Requisitos**: Ollama + LM Studio (ambos)

**Tags executadas**: `@Tag("multi-provider")`

---

### 5. E2E Tests (Staging)

```bash
# Testes end-to-end em ambiente staging
mvn verify -P e2e-tests
```

**Quando usar**:
- Antes de releases
- Validação em staging
- Testes noturnos

**Requisitos**: Ambiente staging configurado

**Tags executadas**: `@Tag("e2e")`

---

### 6. All Tests

```bash
# TODOS os testes (unit + integration + e2e)
mvn verify -P all-tests
```

**Quando usar**:
- Validação completa antes de release
- Testes locais abrangentes
- Smoke test completo

**Requisitos**: Todos os providers disponíveis

---

### 7. Skip Integration Tests

```bash
# Build rápido pulando integration tests
mvn clean package -P skip-integration-tests

# Ou usando propriedade
mvn clean package -DskipITs=true
```

**Quando usar**:
- Build rápido para deploy
- Quando providers não estão disponíveis
- CI/CD stages que não precisam de integration tests

---

## 🎯 Comandos Úteis

### Executar Teste Específico

```bash
# Integration test específico
mvn verify -P integration-tests -Dit.test=OllamaProviderTest

# Classe inteira
mvn verify -P integration-tests -Dit.test=FailoverStrategyIntegrationTest

# Método específico
mvn verify -P integration-tests -Dit.test=OllamaProviderTest#testRealEmbeddings
```

### Executar por Tags JUnit

```bash
# Apenas testes com tag "ollama"
mvn verify -P integration-tests -Dgroups="ollama"

# Testes com "integration" E "model-based"
mvn verify -P integration-tests -Dgroups="integration & model-based"

# Testes com "integration" MAS SEM "slow"
mvn verify -P integration-tests -Dgroups="integration & !slow"
```

### Debug Mode

```bash
# Executar com output detalhado
mvn verify -P integration-tests -X

# Com logs de Spring
mvn verify -P integration-tests -Dspring.output.ansi.enabled=ALWAYS
```

### Parallel Execution

```bash
# Executar testes em paralelo (cuidado com providers locais)
mvn verify -P integration-tests -Djunit.jupiter.execution.parallel.enabled=true
```

---

## 📊 Matriz de Decisão

| Situação | Profile | Comando |
|----------|---------|---------|
| Mudei código, quero testar rápido | (default) | `mvn test` |
| PR no GitHub/GitLab | `integration-tests-ollama` | `mvn verify -P integration-tests-ollama` |
| Antes de fazer merge | `integration-tests` | `mvn verify -P integration-tests` |
| Testar failover Ollama→LMStudio | `multi-provider-tests` | `mvn verify -P multi-provider-tests` |
| Antes de release em produção | `e2e-tests` | `mvn verify -P e2e-tests` |
| Validação super completa | `all-tests` | `mvn verify -P all-tests` |
| Build rápido sem integration | `skip-integration-tests` | `mvn package -P skip-integration-tests` |

---

## ⚙️ Configuração de System Properties

Os profiles configuram as seguintes propriedades:

### integration-tests-ollama
```properties
ollama.url=http://localhost:11434
```

### integration-tests (e multi-provider-tests)
```properties
ollama.url=http://localhost:11434
lmstudio.url=http://localhost:1234
```

### e2e-tests
```properties
test.environment=staging
```

### all-tests
```properties
ollama.url=http://localhost:11434
lmstudio.url=http://localhost:1234
test.environment=local
```

**Como usar nos testes:**
```java
String ollamaUrl = System.getProperty("ollama.url", "http://localhost:11434");
String lmStudioUrl = System.getProperty("lmstudio.url", "http://localhost:1234");
```

---

## 🔧 Configuração de Memória (argLine)

Cada profile tem configuração de memória apropriada:

| Profile | Heap Size | Razão |
|---------|-----------|-------|
| `integration-tests-ollama` | 512MB | Single provider, poucos testes |
| `integration-tests` | 1024MB | Dual provider, mais testes |
| `multi-provider-tests` | 1024MB | Testes intensivos |
| `e2e-tests` | 2048MB | Testes longos e complexos |
| `all-tests` | 2048MB | Todos os testes juntos |

**Ajustar se necessário:**
```bash
mvn verify -P integration-tests -DargLine="-Xmx2048m"
```

---

## 🚨 Troubleshooting

### Problema: "No tests were executed"

**Causa**: Tags JUnit não encontradas ou profile errado

**Solução**:
```bash
# Verificar se os testes têm as tags corretas
mvn verify -P integration-tests -X | grep "groups"

# Listar todos os testes
mvn test -Dtest=**/*Test.java -DfailIfNoTests=false
```

### Problema: "Connection refused" para Ollama/LM Studio

**Causa**: Provider não está rodando

**Solução**:
```bash
# Verificar Ollama
curl http://localhost:11434/api/tags

# Verificar LM Studio
curl http://localhost:1234/v1/models

# Ou usar utility
java -cp target/test-classes bor.tools.simplerag.service.llm.integration.utils.TestProviderUtils
```

### Problema: Testes muito lentos

**Causa**: Modelos grandes ou hardware lento

**Solução**:
```bash
# Use modelos menores (tinyllama em vez de llama2)
# Ou execute apenas testes rápidos
mvn verify -P integration-tests -Dgroups="integration & !slow"
```

### Problema: Out of Memory

**Causa**: Heap size insuficiente

**Solução**:
```bash
# Aumentar memória
mvn verify -P integration-tests -DargLine="-Xmx2048m"
```

---

## 📁 Estrutura de Testes

```
src/test/java/
└── bor/tools/simplerag/service/llm/
    ├── unit/                           → mvn test
    │   └── LLMServiceManagerUnitTest.java
    │
    ├── integration/                    → mvn verify -P integration-tests
    │   ├── providers/
    │   │   ├── OllamaProviderTest.java         (@Tag("ollama"))
    │   │   ├── LMStudioProviderTest.java       (@Tag("lmstudio"))
    │   │   └── ProviderCompatibilityTest.java  (@Tag("compatibility"))
    │   │
    │   ├── strategies/
    │   │   ├── FailoverStrategyIntegrationTest.java    (@Tag("multi-provider"))
    │   │   └── ModelBasedStrategyIntegrationTest.java  (@Tag("model-based"))
    │   │
    │   └── utils/
    │       └── TestProviderUtils.java
    │
    └── e2e/                            → mvn verify -P e2e-tests
        └── RAGSearchE2ETest.java               (@Tag("e2e"))
```

---

## 📚 Referências

- [Maven Surefire Plugin](https://maven.apache.org/surefire/maven-surefire-plugin/)
- [Maven Failsafe Plugin](https://maven.apache.org/surefire/maven-failsafe-plugin/)
- [JUnit 5 Tags](https://junit.org/junit5/docs/current/user-guide/#writing-tests-tagging-and-filtering)
- [Maven Profiles](https://maven.apache.org/guides/introduction/introduction-to-profiles.html)

---

## ✅ Checklist de Setup

- [ ] Ollama instalado e rodando (`ollama serve`)
- [ ] LM Studio instalado e servidor iniciado
- [ ] Modelos Ollama baixados: `ollama pull tinyllama && ollama pull nomic-embed-text`
- [ ] Modelos LM Studio carregados via UI
- [ ] Testar unit tests: `mvn test` ✅
- [ ] Testar integration-ollama: `mvn verify -P integration-tests-ollama` ✅
- [ ] Testar integration completo: `mvn verify -P integration-tests` ✅

---

**Criado por**: Claude Code
**Data**: 2025-10-14
**Status**: ✅ Configuração Completa
