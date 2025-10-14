# Scripts de Setup - JSimpleRag

Esta pasta contém scripts para facilitar o setup e verificação dos provedores LLM necessários para testes de integração.

---

## 📜 Scripts Disponíveis

### 1. setup-ollama.sh

**Propósito**: Instalar e configurar Ollama para testes de integração

**O que faz**:
- ✅ Verifica se Ollama está instalado
- ✅ Instala Ollama se necessário (Linux/macOS)
- ✅ Inicia o servidor Ollama
- ✅ Baixa modelos necessários (tinyllama, nomic-embed-text)
- ✅ Verifica a configuração
- ✅ Executa testes de conectividade

**Uso**:
```bash
./scripts/setup-ollama.sh
```

**Modelos instalados**:
- `tinyllama` (~600MB) - Modelo rápido para testes
- `nomic-embed-text` (~274MB) - Embeddings
- `llama2` (opcional, ~3.8GB) - Modelo de melhor qualidade
- `mistral` (opcional, ~4.1GB) - Modelo de alta qualidade

**Requisitos**:
- Linux ou macOS
- Conexão com internet (para downloads)
- Permissões sudo (para instalação no Linux)

---

### 2. setup-lmstudio.sh

**Propósito**: Configurar LM Studio para testes de integração

**O que faz**:
- ✅ Verifica se LM Studio está instalado
- ✅ Guia instalação manual (é uma GUI)
- ✅ Instrui iniciar o servidor local
- ✅ Guia download e carregamento de modelos
- ✅ Verifica a configuração
- ✅ Executa testes de conectividade

**Uso**:
```bash
./scripts/setup-lmstudio.sh
```

**Modelos recomendados**:
- `qwen2.5-7b-instruct` (~4-5GB) - Modelo rápido e eficiente
- `nomic-embed-text` (~274MB) - Embeddings

**Requisitos**:
- LM Studio instalado (baixe de https://lmstudio.ai)
- Conexão com internet (para downloads de modelos)
- ~10GB de espaço em disco

**Nota**: LM Studio é uma aplicação GUI e requer passos manuais.

---

### 3. check-providers.sh

**Propósito**: Verificar status dos provedores e testar conectividade

**O que faz**:
- ✅ Verifica se Ollama está rodando
- ✅ Verifica se LM Studio está rodando
- ✅ Lista modelos disponíveis em cada provedor
- ✅ Valida modelos necessários
- ✅ Executa testes de conectividade (opcional)
- ✅ Mostra comandos Maven disponíveis

**Uso**:
```bash
# Apenas verificar status
./scripts/check-providers.sh

# Verificar e testar conectividade
./scripts/check-providers.sh --test

# Mostrar ajuda
./scripts/check-providers.sh --help
```

**Saída exemplo**:
```
▶ Ollama (localhost:11434)
  ──────────────────────────────────────────────────────────
  ✅ Server is running
  Version:                       0.1.28
  Models installed:              3

  ℹ️  Available models:
    • tinyllama
    • llama2
    • nomic-embed-text

  ℹ️  Required models check:
  ✅ tinyllama (required) ✓
  ✅ nomic-embed-text (required) ✓

▶ LM Studio (localhost:1234)
  ──────────────────────────────────────────────────────────
  ✅ Server is running
  Models loaded:                 2

  ℹ️  Loaded models:
    • qwen2.5-7b-instruct
    • nomic-embed-text

╔════════════════════════════════════════════════════════╗
║  ✅ ALL SYSTEMS GO!                                    ║
║  Both providers are ready for integration tests       ║
╚════════════════════════════════════════════════════════╝

✅ You can run: mvn verify -P integration-tests
```

---

## 🚀 Fluxo de Setup Recomendado

### Setup Inicial (primeira vez)

```bash
# 1. Setup Ollama (mais rápido e essencial)
./scripts/setup-ollama.sh

# 2. Verificar se funcionou
./scripts/check-providers.sh

# 3. Rodar testes com Ollama apenas
cd ..
mvn verify -P integration-tests-ollama

# 4. (Opcional) Setup LM Studio para testes completos
./scripts/setup-lmstudio.sh

# 5. Verificar ambos os provedores
./scripts/check-providers.sh --test

# 6. Rodar testes completos
cd ..
mvn verify -P integration-tests
```

### Verificação Diária

```bash
# Antes de rodar testes, verificar providers
./scripts/check-providers.sh

# Se algo não estiver rodando, os scripts mostram como corrigir
```

---

## 🎯 Cenários de Uso

### Cenário 1: Desenvolvimento Local (apenas Ollama)

**Quando**: Desenvolvimento diário, testes rápidos

```bash
# Verificar
./scripts/check-providers.sh

# Se Ollama não estiver rodando
ollama serve &

# Rodar testes
mvn verify -P integration-tests-ollama
```

**Tempo**: ~15 segundos

---

### Cenário 2: Pre-Merge (Ollama + LM Studio)

**Quando**: Antes de fazer merge, validação completa

```bash
# Verificar ambos
./scripts/check-providers.sh --test

# Se LM Studio não estiver rodando:
# 1. Abrir LM Studio UI
# 2. Local Server → Start Server

# Rodar testes completos
mvn verify -P integration-tests
```

**Tempo**: ~45 segundos

---

### Cenário 3: CI/CD (GitHub Actions)

**Quando**: Pull requests, pipelines

```bash
# No CI, apenas Ollama
./scripts/setup-ollama.sh
mvn verify -P integration-tests-ollama
```

**Tempo**: ~20 segundos (com cache de modelos)

---

### Cenário 4: Troubleshooting

**Problema**: Testes falhando

```bash
# 1. Verificar providers
./scripts/check-providers.sh --test

# 2. Se Ollama com problemas
./scripts/setup-ollama.sh

# 3. Se LM Studio com problemas
./scripts/setup-lmstudio.sh

# 4. Verificar logs
# Ollama: tail -f /tmp/ollama.log
# LM Studio: Verificar na UI
```

---

## 🔧 Configurações Avançadas

### Alterar Porta do Ollama

```bash
# Iniciar Ollama em porta diferente
OLLAMA_HOST=0.0.0.0:11435 ollama serve &

# Ajustar nos scripts e properties
export OLLAMA_URL="http://localhost:11435"
```

### Usar Ollama Remoto

```bash
# No check-providers.sh, ajustar:
OLLAMA_URL="http://remote-server:11434"

# Em application-integration-test.properties:
llmservice.provider.api.url=http://remote-server:11434/v1
```

### Adicionar Novos Modelos

Edite `setup-ollama.sh`:
```bash
REQUIRED_MODELS=(
    "tinyllama"
    "nomic-embed-text"
    "seu-novo-modelo"  # Adicionar aqui
)
```

---

## 📊 Comparação de Provedores

| Aspecto | Ollama | LM Studio |
|---------|--------|-----------|
| **Instalação** | CLI (script automático) | GUI (manual) |
| **Inicialização** | `ollama serve` | Botão "Start Server" na UI |
| **Porta padrão** | 11434 | 1234 |
| **Gestão modelos** | CLI (`ollama pull`) | UI (download na interface) |
| **Logs** | `/tmp/ollama.log` | Console na UI |
| **API** | OpenAI-compatible | OpenAI-compatible |
| **Uso em CI/CD** | ✅ Excelente | ❌ Difícil (requer GUI) |
| **Uso dev local** | ✅ Excelente | ✅ Excelente |

---

## 🐛 Troubleshooting Comum

### "Connection refused" no Ollama

**Causa**: Servidor não está rodando

**Solução**:
```bash
# Verificar se está rodando
curl http://localhost:11434/api/tags

# Se não, iniciar
ollama serve &

# Ou via systemd (Linux)
sudo systemctl start ollama
```

---

### "Connection refused" no LM Studio

**Causa**: Servidor não está rodando na UI

**Solução**:
1. Abrir LM Studio
2. Clicar em "↔" (Local Server) na barra lateral
3. Clicar "Start Server"
4. Verificar se mostra "Server running on http://localhost:1234"

---

### Modelos não encontrados

**Causa**: Modelos não baixados ou não carregados

**Solução Ollama**:
```bash
# Baixar modelo
ollama pull tinyllama

# Verificar modelos instalados
ollama list
```

**Solução LM Studio**:
1. Na UI, ir em "🔍" (Search)
2. Buscar o modelo (ex: "qwen2.5")
3. Clicar "Download"
4. Aguardar download
5. Ir em "Local Server" e selecionar o modelo no dropdown
6. Clicar "Load Model"

---

### Erro "Out of Memory"

**Causa**: Modelo muito grande para a RAM disponível

**Solução**:
- Use modelos menores: `tinyllama` em vez de `llama2`
- Aumente swap do sistema
- Use quantização menor (ex: Q4 em vez de Q6)

---

## 📚 Referências

- [Ollama Documentation](https://github.com/ollama/ollama/blob/main/docs/README.md)
- [LM Studio Documentation](https://lmstudio.ai/docs)
- [Guia de Testes](../INTEGRATION_TEST_EXAMPLES.md)
- [Maven Profiles](../MAVEN_PROFILES_GUIDE.md)

---

## ✅ Checklist de Setup

- [ ] Ollama instalado
- [ ] Ollama rodando (`ollama serve`)
- [ ] Modelos Ollama baixados (tinyllama, nomic-embed-text)
- [ ] LM Studio instalado
- [ ] LM Studio servidor iniciado
- [ ] Modelos LM Studio carregados
- [ ] `check-providers.sh` passa ✅
- [ ] `mvn verify -P integration-tests-ollama` passa ✅
- [ ] `mvn verify -P integration-tests` passa ✅

---

**Criado por**: Claude Code
**Data**: 2025-10-14
**Versão**: 1.0
