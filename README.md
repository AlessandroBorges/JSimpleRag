# JSimpleRag - Sistema RAG Hierárquico

Sistema de Retrieval-Augmented Generation (RAG) com arquitetura hierárquica para gestão inteligente de conhecimento, desenvolvido em Java com PostgreSQL/PGVector.

## 🎯 Visão Geral

O JSimpleRag é uma plataforma que permite às organizações indexar, organizar e pesquisar grandes volumes de documentos técnicos de forma eficiente e contextualizada através de:

- **Busca Híbrida**: Combina pesquisa semântica (embeddings) e textual (full-text search)
- **Arquitetura Hierárquica**: Estrutura Biblioteca → Documento → Capítulo → Trecho
- **Processamento Assíncrono**: Geração automática de embeddings em background
- **Escalabilidade**: Suporte a milhões de documentos com PostgreSQL + PGVector

## 🏗️ Arquitetura Técnica

### Stack Tecnológico
- **Backend**: Java 17 + Spring Boot 3.x
- **Banco de Dados**: PostgreSQL 18+ com extensão PGVector
- **Busca**: Híbrida (embeddings semânticos + full-text search)
- **Documentação**: OpenAPI/Swagger
- **Containerização**: Docker + Docker Compose

### Modelo de Dados Hierárquico

```
Biblioteca (área de conhecimento)
├── Documento (livro, artigo, manual)
│   ├── Capítulo (~8k tokens)
│   │   └── DocEmbedding (trechos ~2k tokens)
│   └── DocEmbedding (capítulo completo)
└── DocEmbedding (documento completo)
```

## 🚀 Funcionalidades Principais

### MVP (Mínimo Produto Viável)
- ✅ **Gestão de Bibliotecas**: Criar/editar bibliotecas com pesos customizados (semântico vs textual)
- ✅ **Gestão de Documentos**: Upload e processamento de documentos Markdown com versionamento
- ✅ **Pesquisa Híbrida**: Busca combinando similaridade semântica e relevância textual
- ✅ **Navegação Hierárquica**: Exploração top-down dos resultados (biblioteca → documento → capítulo → trecho)
- ✅ **Processamento Assíncrono**: Geração automática de embeddings em background

### Funcionalidades Avançadas (Roadmap)
- 🔄 Cache inteligente com Redis
- 📊 Dashboard analítico com métricas de uso
- 🔌 API pública para integrações
- 🌐 Interface web administrativa
- 📈 Monitoramento com Prometheus/Grafana

## 🛠️ Configuração e Instalação

### Pré-requisitos
- Java 17+
- Docker e Docker Compose
- Chave API OpenAI (para geração de embeddings)

### Instalação Rápida com Docker

1. **Clone o repositório**:

```bash
git clone https://github.com/seu-usuario/JSimpleRag.git
cd JSimpleRag
```

2. **Configure as variáveis de ambiente**:

```bash
cp .env.example .env
# Edite o arquivo .env com suas configurações
```

3. **Execute com Docker Compose**:

```bash
docker-compose up -d
```

4. **Acesse a aplicação**:
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Documentação: http://localhost:8080/api-docs

### Configuração Manual

1. **Banco PostgreSQL com PGVector**:

```sql
CREATE DATABASE rag_db;
CREATE USER rag_user WITH PASSWORD 'rag_pass';
GRANT ALL PRIVILEGES ON DATABASE rag_db TO rag_user;

-- Conectar ao rag_db
CREATE EXTENSION vector;
```

2. **Configurar application.yml**:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/rag_db
    username: rag_user
    password: rag_pass

rag:
  embedding:
    model: "text-embedding-ada-002"
    dimensoes: 1536
```

3. **Executar aplicação**:

```bash
./mvnw spring-boot:run
```

## 📖 Uso da API

### Exemplo 1: Criar Biblioteca

```bash
curl -X POST http://localhost:8080/api/v1/bibliotecas \
  -H "Content-Type: application/json" \
  -d '{
    "nome": "Engenharia de Software",
    "areaConhecimento": "Tecnologia",
    "pesoSemantico": 0.70,
    "pesoTextual": 0.30
  }'
```

### Exemplo 2: Upload de Documento

```bash
curl -X POST http://localhost:8080/api/v1/documentos \
  -H "Content-Type: application/json" \
  -d '{
    "bibliotecaId": 1,
    "titulo": "Clean Code",
    "conteudoMarkdown": "# Clean Code\n\n## Introdução\nEste livro...",
    "flagVigente": true,
    "dataPublicacao": "2023-01-15"
  }'
```

### Exemplo 3: Processar Documento (Gerar Embeddings)

```bash
curl -X POST http://localhost:8080/api/v1/documentos/1/processar
```

### Exemplo 4: Pesquisa Híbrida

```bash
curl -X POST http://localhost:8080/api/v1/pesquisa \
  -H "Content-Type: application/json" \
  -d '{
    "query": "princípios de clean code",
    "bibliotecaId": 1,
    "apenasVigentes": true,
    "limite": 10,
    "pesoSemanticoCustom": 0.8
  }'
```

## 🔍 Guia de Sintaxe de Pesquisa

O JSimpleRag suporta queries em linguagem natural com sintaxe poderosa para pesquisas precisas.

### Sintaxe Básica

| Query | Significado | Exemplo |
|-------|-------------|---------|
| `café leite` | Busca OR (qualquer palavra) | Encontra documentos com "café" OU "leite" |
| `"pão quente"` | Frase exata | Encontra apenas "pão quente" adjacente |
| `café -açúcar` | Exclusão | Encontra "café" SEM "açúcar" |

### Recursos de Linguagem

- ✅ **Insensível a acentos**: `café` = `cafe`, `açúcar` = `acucar`
- ✅ **Insensível a maiúsculas**: `CAFÉ` = `café` = `Café`
- ✅ **Stemming em português**: `trabalho` = `trabalhar` = `trabalhando`
- ✅ **Ponderação por metadados**: Títulos têm maior relevância que conteúdo

### Exemplos de API

#### Busca Híbrida (Recomendada)

```bash
curl -X POST http://localhost:8080/api/v1/search/hybrid \
  -H "Content-Type: application/json" \
  -d '{
    "query": "machine learning algoritmos",
    "libraryIds": [1, 2],
    "limit": 10,
    "pesoSemantico": 0.6,
    "pesoTextual": 0.4
  }'
```

#### Busca Textual Apenas

```bash
curl -X POST http://localhost:8080/api/v1/search/textual \
  -H "Content-Type: application/json" \
  -d '{
    "query": "\"artigo 5º\" constituição -emenda",
    "libraryIds": [1],
    "limit": 20
  }'
```

#### Busca Semântica Apenas

```bash
curl -X POST http://localhost:8080/api/v1/search/semantic \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Como implementar autenticação em APIs REST?",
    "libraryIds": [1],
    "limit": 10
  }'
```

### Recomendações de Pesos de Pesquisa

| Tipo de Conteúdo | Semântico | Textual | Por Quê? |
|------------------|-----------|---------|----------|
| Documentação técnica | 0.7 | 0.3 | Favorece compreensão conceitual |
| Documentos legais | 0.4 | 0.6 | Terminologia exata importa |
| Artigos científicos | 0.6 | 0.4 | Balanceia conceitos e termos |
| Conhecimento geral | 0.6 | 0.4 | Abordagem balanceada padrão |
| Notícias | 0.5 | 0.5 | Importância igual |

### Dicas de Performance

1. **Use busca de frase** para correspondências exatas: `"machine learning"`
2. **Limite os resultados** para reduzir latência: `"limit": 10`
3. **Ajuste os pesos** por tipo de biblioteca (veja tabela acima)
4. **Evite queries muito longas** (máximo 500 caracteres recomendado)

## 🏗️ Estrutura do Projeto

```
src/
├── main/
│   ├── java/com/rag/hierarquico/
│   │   ├── controller/          # Controllers REST
│   │   ├── service/            # Lógica de negócio
│   │   ├── repository/         # Acesso a dados
│   │   ├── entity/            # Entidades JPA
│   │   ├── dto/               # Data Transfer Objects
│   │   ├── mapper/            # MapStruct mappers
│   │   ├── config/            # Configurações
│   │   └── exception/         # Tratamento de exceções
│   └── resources/
│       ├── application.yml    # Configuração principal
│       └── db/changelog/      # Scripts Liquibase
├── test/                      # Testes unitários e integração
└── docker/                   # Arquivos Docker
```

## 🎯 Casos de Uso Típicos

### 1. Pesquisador/Analista
**Objetivo**: Encontrar informações específicas rapidamente
- Busca por "clean architecture patterns"
- Navega pelos resultados hierarquicamente
- Acessa contexto completo do documento

### 2. Gestor de Conhecimento
**Objetivo**: Organizar documentos em bibliotecas temáticas
- Cria biblioteca "Arquitetura de Software"
- Define pesos: 70% semântico, 30% textual
- Monitora processamento dos documentos

### 3. Sistema Integrado
**Objetivo**: Usar via API para chatbots/assistentes
- Integra com sistema de help desk
- Busca automática em base de conhecimento
- Retorna trechos relevantes contextualizados

## 📊 Performance e Escalabilidade

### Métricas de Performance
- **Tempo de Resposta**: < 2s para 95% das consultas
- **Throughput**: 100+ consultas/segundo
- **Capacidade**: 10M+ documentos indexados
- **Disponibilidade**: 99.9% uptime

### Otimizações
- Índices otimizados para PGVector (IVFFlat)
- Pool de conexões configurado (HikariCP)
- Processamento assíncrono para embeddings
- Cache de resultados frequentes (futura implementação Redis)

## 🔧 Configurações Avançadas

### Parâmetros de Embedding

```yaml
rag:
  embedding:
    model: "text-embedding-ada-002"  # Modelo OpenAI
    dimensoes: 1536                   # Dimensão dos vetores
    batch-size: 100                   # Lote para processamento

  processamento:
    chunk-size-maximo: 2000          # Tamanho máximo do trecho
    capitulo-size-padrao: 8000       # Tamanho padrão do capítulo
```

### Pesos de Pesquisa
- **Semântico Alto (0.8)**: Para consultas conceituais
- **Textual Alto (0.8)**: Para busca por termos específicos
- **Balanceado (0.6/0.4)**: Para uso geral

## 🧪 Testes

O JSimpleRag possui uma estratégia de testes em 3 camadas: **Unit Tests** (Mockito), **Integration Tests** (Ollama + LM Studio locais) e **E2E Tests** (staging).

### 🎯 Arquitetura de Testes

```
┌─────────────────────────────────────────┐
│  Layer 1: Unit Tests                    │  < 1s   (sempre)
│  - Lógica de roteamento                 │
│  - Validações                           │
│  - Estatísticas                         │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│  Layer 2: Integration Tests             │  ~30s   (PR/merge)
│  - Ollama + LM Studio (locais)          │
│  - Testes com LLMs reais                │
│  - Roteamento multi-provedor            │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│  Layer 3: E2E Tests                     │  ~5min  (release)
│  - Fluxo completo RAG                   │
│  - Ambiente staging                     │
└─────────────────────────────────────────┘
```

### 🚀 Quick Start - Testes

```bash
# 1. Unit tests apenas (sempre funciona, sem setup)
mvn test

# 2. Setup provedores para integration tests
./scripts/setup-ollama.sh        # Instala e configura Ollama
./scripts/setup-lmstudio.sh      # Guia setup LM Studio

# 3. Verificar provedores
./scripts/check-providers.sh

# 4. Integration tests (Ollama apenas - rápido)
mvn verify -P integration-tests-ollama

# 5. Integration tests completo (Ollama + LM Studio)
mvn verify -P integration-tests
```

### 📋 Profiles Maven Disponíveis

| Profile | Descrição | Duração | Quando Usar |
|---------|-----------|---------|-------------|
| **(default)** | Unit tests apenas | < 5s | Desenvolvimento diário |
| `integration-tests-ollama` | Testes com Ollama | ~15s | CI/CD, PR |
| `integration-tests` | Ollama + LM Studio | ~45s | Pre-merge, validação completa |
| `multi-provider-tests` | Apenas multi-provider | ~30s | Testes de failover/routing |
| `e2e-tests` | End-to-end staging | ~5min | Antes de releases |
| `all-tests` | Todos os testes | Variável | Validação completa |

### 📦 Requisitos para Integration Tests

**Opção 1: Ollama apenas (mínimo, recomendado para CI/CD)**

```bash
# Instalar Ollama
curl -fsSL https://ollama.ai/install.sh | sh

# Iniciar servidor
ollama serve &

# Baixar modelos necessários
ollama pull tinyllama           # ~600MB - rápido
ollama pull nomic-embed-text    # ~274MB - embeddings

# Verificar
ollama list
```

**Opção 2: Ollama + LM Studio (completo, melhor para dev local)**

```bash
# 1. Setup Ollama (veja acima)

# 2. Baixar e instalar LM Studio
# https://lmstudio.ai/

# 3. Abrir LM Studio e:
#    - Ir em "Local Server"
#    - Clicar "Start Server" (porta 1234)
#    - Baixar modelos: qwen2.5-7b-instruct, nomic-embed-text

# 4. Verificar ambos
./scripts/check-providers.sh --test
```

### 🎮 Comandos de Teste

#### Unit Tests

```bash
# Executar todos unit tests
mvn test

# Teste específico
mvn test -Dtest=LLMServiceManagerTest

# Com coverage
mvn test jacoco:report
```

#### Integration Tests

```bash
# Ollama apenas (CI/CD friendly)
mvn verify -P integration-tests-ollama

# Ollama + LM Studio (desenvolvimento local)
mvn verify -P integration-tests

# Apenas testes multi-provider
mvn verify -P multi-provider-tests

# Teste específico
mvn verify -P integration-tests -Dit.test=FailoverStrategyIntegrationTest

# Com debug
mvn verify -P integration-tests -X
```

#### E2E Tests

```bash
# Requer ambiente staging configurado
mvn verify -P e2e-tests
```

### 📊 Cobertura de Testes

**Estrutura Atual:**

```
src/test/java/
├── unit/                        → 27 testes (lógica pura)
│   ├── LLMServiceManagerUnitTest
│   ├── StrategySelectionTest
│   └── ModelDiscoveryTest
│
├── integration/                 → 23 testes (LLMs reais)
│   ├── providers/
│   │   ├── OllamaProviderTest          (9 testes)
│   │   ├── LMStudioProviderTest        (6 testes)
│   │   └── ProviderCompatibilityTest   (7 testes)
│   │
│   └── strategies/
│       ├── FailoverStrategyIntegrationTest    (5 testes)
│       └── ModelBasedStrategyIntegrationTest  (+ testes)
│
└── e2e/                         → 5+ testes (fluxo completo)
    └── RAGSearchE2ETest

Total: 55+ testes automatizados
```

**Metas de Cobertura:**
- Unit Tests: >95% (lógica de negócio)
- Integration Tests: >80% (integração com LLMs)
- E2E Tests: >50% (fluxos principais)

### 🔧 Scripts de Automação

O projeto inclui scripts bash para facilitar o setup:

| Script | Descrição |
|--------|-----------|
| `scripts/setup-ollama.sh` | Instala e configura Ollama automaticamente |
| `scripts/setup-lmstudio.sh` | Guia instalação e configuração do LM Studio |
| `scripts/check-providers.sh` | Verifica status e testa conectividade |

**Exemplo de uso:**

```bash
# Setup completo automatizado
./scripts/setup-ollama.sh

# Verificar status e executar testes de conectividade
./scripts/check-providers.sh --test

# Output esperado:
# ✅ Ollama (localhost:11434)
#    ✅ Server is running
#    ✅ Models installed: 2
#    ✅ tinyllama (required) ✓
#    ✅ nomic-embed-text (required) ✓
#
# ✅ LM Studio (localhost:1234)
#    ✅ Server is running
#    ✅ Models loaded: 2
#    ✅ qwen (recommended) ✓
#
# ╔════════════════════════════════════════╗
# ║  ✅ ALL SYSTEMS GO!                    ║
# ║  Both providers ready for tests        ║
# ╚════════════════════════════════════════╝
```

### 📚 Documentação de Testes

Para informações detalhadas sobre testes, consulte:

- **[INTEGRATION_TEST_EXAMPLES.md](INTEGRATION_TEST_EXAMPLES.md)** - Exemplos completos de testes (4 classes prontas)
- **[MAVEN_PROFILES_GUIDE.md](MAVEN_PROFILES_GUIDE.md)** - Guia de uso dos profiles Maven
- **[novo_framework_testes.md](novo_framework_testes.md)** - Arquitetura e estratégia de testes
- **[scripts/README.md](scripts/README.md)** - Documentação dos scripts de setup

### 🐛 Troubleshooting

**Problema: "Connection refused" para Ollama**

```bash
# Verificar se está rodando
curl http://localhost:11434/api/tags

# Se não, iniciar
ollama serve &

# Verificar logs
tail -f /tmp/ollama.log
```

**Problema: "Connection refused" para LM Studio**

```bash
# 1. Abrir LM Studio
# 2. Ir em "Local Server" (ícone ↔)
# 3. Clicar "Start Server"
# 4. Verificar porta 1234
```

**Problema: Testes lentos**

```bash
# Usar modelos menores
ollama pull tinyllama  # Em vez de llama2

# Ou executar apenas testes rápidos
mvn verify -P integration-tests -Dgroups="integration & !slow"
```

**Problema: Class Not Found Exception para LLMService**

 Veja **Dependencias Locais** 


## Dependências Locais

Este projeto depende de **JSimpleLLM**, um projeto Maven local.

### Setup Inicial

1. Clone o JSimpleLLM:

```bash
git clone https://github.com/your-org/JSimpleLLM
```

2. Instale no repositório Maven local:

  ```bash
   cd JSimpleLLM
   mvn clean install -DskipTests
  ```

3. Compile o JSimpleRag:

  ```bash
   cd JSimpleRag
   mvn clean compile
  ```

Veja [LOCAL_MAVEN_DEPENDENCY_GUIDE.md](./doc/LOCAL_MAVEN_DEPENDENCY_GUIDE.md) para mais detalhes.

### 🎯 CI/CD Integration

**GitHub Actions exemplo:**

```yaml
name: Tests
on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Unit Tests
        run: mvn test

  integration-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Setup Ollama
        run: ./scripts/setup-ollama.sh
      - name: Integration Tests
        run: mvn verify -P integration-tests-ollama
```

### ✅ Checklist de Desenvolvimento

Antes de criar um PR:

- [ ] Todos unit tests passam: `mvn test`
- [ ] Integration tests passam: `mvn verify -P integration-tests-ollama`
- [ ] Código formatado corretamente
- [ ] Novos testes adicionados para novas features
- [ ] Documentação atualizada se necessário

## 🚀 Deploy e Produção

### Docker Compose (Recomendado)

```bash
# Produção
docker-compose -f docker-compose.prod.yml up -d

# Monitoramento
docker-compose logs -f rag-api
```

### Variáveis de Ambiente Importantes

```bash
DB_HOST=postgres
DB_PORT=5432
DB_NAME=rag_db
DB_USERNAME=rag_user
DB_PASSWORD=rag_pass
OPENAI_API_KEY=sk-...
```

## 📈 Roadmap

### Fase 1 - MVP (6 meses)
- [x] Análise e Design (concluído)
- [ ] Backend Core (em desenvolvimento)
- [ ] Frontend Básico
- [ ] Integração e Testes
- [ ] Deploy MVP

### Fase 2 - Melhorias (3 meses)
- [ ] Dashboard Analytics
- [ ] Cache e Performance
- [ ] UX Melhorado

### Fase 3 - Escalabilidade (3 meses)
- [ ] API Pública
- [ ] Multi-idioma
- [ ] Integrações

## 🤝 Personas e Cenários

### Pesquisador/Analista
- Precisa encontrar informações específicas rapidamente
- Busca precisa e contextual
- Acesso a documentos relacionados

### Gestor de Conhecimento
- Organiza e mantém bases de conhecimento
- Controle de versões e metadados
- Métricas de uso e adoção

### Administrador de Sistema
- Monitoramento e alertas
- Configuração de parâmetros
- Gestão de usuários e permissões

## 📝 Especificações Técnicas

### Banco de Dados
- **Principal**: PostgreSQL 18+ com PGVector
- **Extensões**: vector, pg_trgm, btree_gin
- **Backup**: Automático diário com retenção de 30 dias

### APIs e Protocolos
- **REST API**: OpenAPI 3.0 compliant
- **Webhooks**: Notificações de eventos
- **Streaming**: Server-sent events para updates

### Segurança
- **Autenticação**: Suporte a SSO corporativo
- **Autorização**: Controle baseado em roles
- **Dados**: Criptografia em trânsito e repouso
- **Auditoria**: Log de operações sensíveis

## 📝 Licença

Este projeto está licenciado sob a [Licença MIT](LICENSE).

## 🤝 Contribuição

1. Fork o projeto
2. Crie uma feature branch (`git checkout -b feature/nova-funcionalidade`)
3. Commit suas mudanças (`git commit -am 'Adiciona nova funcionalidade'`)
4. Push para a branch (`git push origin feature/nova-funcionalidade`)
5. Crie um Pull Request

## 📧 Suporte

- **Issues**: [GitHub Issues](https://github.com/seu-usuario/JSimpleRag/issues)
- **Documentação**: Consulte `rag_prd.md` e `rag_specification.md`
- **Discussões**: [GitHub Discussions](https://github.com/seu-usuario/JSimpleRag/discussions)

---

**JSimpleRag** - Transformando conhecimento em respostas inteligentes através de RAG hierárquico 🧠✨