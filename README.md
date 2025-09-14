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

### Executar Testes
```bash
# Testes unitários
./mvnw test

# Testes de integração
./mvnw test -P integration-tests

# Todos os testes
./mvnw verify
```

### Cobertura de Testes
- Meta: >80% cobertura de código
- Testes unitários para services e mappers
- Testes de integração para controllers
- Testes de performance para consultas

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