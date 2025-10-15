# Database Migrations - JSimpleRag

## 📋 Índice
1. [Modelo de Dados Atual](#modelo-de-dados-atual)
2. [Abordagem Code First](#abordagem-code-first)
3. Orientações para Futuras Mudanças
4. Comandos de Migração

---

## 🗃️ Modelo de Dados Atual

### Estrutura Hierárquica
O JSimpleRag implementa uma hierarquia de 4 níveis para organização do conhecimento:

```
Biblioteca (Knowledge Area/Library)
├── Documento (Document - books, articles, manuals)
│   ├── Capítulo (Chapter - ~8k tokens)
│   │   └── DocEmbedding (Chunks - ~2k tokens)
│   └── DocEmbedding (Chapter-level embeddings)
└── DocEmbedding (Document-level embeddings)
```

### Entidades Principais

#### 1. **Biblioteca** (`biblioteca`)
Representa uma área de conhecimento com configurações de busca híbrida.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | SERIAL | Chave primária |
| `uuid` | VARCHAR | Identificador único |
| `nome` | VARCHAR(255) | Nome da biblioteca |
| `area_conhecimento` | VARCHAR(255) | Área de conhecimento |
| `peso_semantico` | DECIMAL(3,2) | Peso da busca semântica (0.0-1.0) |
| `peso_textual` | DECIMAL(3,2) | Peso da busca textual (0.0-1.0) |
| `metadados` | JSONB | Metadados (obrigatório: `language`) |
| `created_at` | TIMESTAMP | Data de criação |
| `updated_at` | TIMESTAMP | Data de atualização |

**Constraints:**
- `peso_semantico + peso_textual = 1.0`
- `uuid` deve ser único

#### 2. **Documento** (`documento`)
Representa documentos completos (livros, artigos, manuais, etc.).

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | SERIAL | Chave primária |
| `biblioteca_id` | INTEGER | FK para biblioteca |
| `titulo` | VARCHAR(500) | Título do documento |
| `conteudo_markdown` | TEXT | Conteúdo em Markdown |
| `flag_vigente` | BOOLEAN | Se o documento está ativo |
| `data_publicacao` | DATE | Data de publicação |
| `tokens_total` | INTEGER | Total de tokens |
| `metadados` | JSONB | Metadados do documento |
| `created_at` | TIMESTAMP | Data de criação |
| `updated_at` | TIMESTAMP | Data de atualização |

**Regras de Negócio:**
- Apenas um documento por título pode ter `flag_vigente=true` por biblioteca
- Documentos são divididos em capítulos para processamento

#### 3. **Capítulo** (`capitulo`)
Representa seções de um documento (~8k tokens por padrão).

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | SERIAL | Chave primária |
| `documento_id` | INTEGER | FK para documento |
| `titulo` | VARCHAR(500) | Título do capítulo |
| `conteudo` | TEXT | Conteúdo do capítulo |
| `ordem_doc` | INTEGER | Ordem no documento |
| `token_inicio` | INTEGER | Token inicial no documento |
| `token_fim` | INTEGER | Token final no documento |
| `tokens_total` | INTEGER | Total de tokens (calculado) |
| `metadados` | JSONB | Metadados do capítulo |
| `created_at` | TIMESTAMP | Data de criação |
| `updated_at` | TIMESTAMP | Data de atualização |

**Constraints:**
- `(documento_id, ordem_doc)` deve ser único

#### 4. **DocEmbedding** (`doc_embedding`)
Armazena embeddings em diferentes níveis hierárquicos.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | SERIAL | Chave primária |
| `biblioteca_id` | INTEGER | FK para biblioteca |
| `documento_id` | INTEGER | FK para documento |
| `capitulo_id` | INTEGER | FK para capítulo (opcional) |
| `tipo_embedding` | ENUM | Tipo: documento/capitulo/trecho |
| `trecho_texto` | TEXT | Texto do trecho |
| `ordem_cap` | INTEGER | Ordem dentro do capítulo |
| `embedding_vector` | vector(1536) | Vetor de embedding |
| `texto_indexado` | tsvector | Índice de texto completo (auto) |
| `metadados` | JSONB | Metadados do embedding |
| `created_at` | TIMESTAMP | Data de criação |

**Regras de Consistência:**
- `DOCUMENTO`: `capitulo_id` e `ordem_cap` devem ser NULL
- `CAPITULO`: `capitulo_id` obrigatório, `ordem_cap` deve ser NULL
- `TRECHO`: `capitulo_id` e `ordem_cap` obrigatórios

### Enums

#### TipoEmbedding
```sql
CREATE TYPE tipo_embedding AS ENUM (
    'documento', 'capitulo', 'trecho',
    'perguntas_respostas', 'resumo', 'outros'
);
```

#### TipoConteudo
```sql
CREATE TYPE tipo_conteudo AS ENUM (
    'constituicao', 'lei', 'decreto', 'sumula', 'acordao',
    'instrucao_normativa', 'portaria', 'normativo', 'tese',
    'artigo', 'livro', 'relatorio', 'nota_tecnica',
    'nota_auditoria', 'manual', 'papel_trabalho', 'resumo',
    'wiki', 'projeto', 'documento_interno', 'outros'
);
```

#### TipoSecao
```sql
CREATE TYPE tipo_secao AS ENUM (
    'cabecalho', 'ementa', 'sumario', 'introducao',
    'planejamento', 'metodologia', 'desenvolvimento',
    'achados', 'demonstrativo', 'recomendacoes',
    'ressalvas_divergencias', 'conclusao', 'relatorio',
    'voto', 'decisao', 'referencias_bibliografias',
    'anexo', 'outros'
);
```

### Índices Críticos
- `idx_embedding_vector`: IVFFlat para busca de similaridade vetorial
- `idx_embedding_texto`: GIN para busca textual full-text
- `idx_documento_vigente`: Índice parcial para documentos ativos

---

## 🔄 Abordagem Code First

### Configuração Atual
O projeto foi configurado para priorizar o código Java sobre a estrutura do banco de dados:

#### application.properties
```properties
# Code First Configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.generate-ddl=true
spring.jpa.properties.hibernate.hbm2ddl.auto=update
spring.jpa.defer-datasource-initialization=true

# Liquibase disabled for Code First
spring.liquibase.enabled=false
```

### Estratégias de DDL

| Estratégia | Uso | Descrição |
|------------|-----|-----------|
| `create` | Desenvolvimento inicial | **CUIDADO**: Apaga e recria todas as tabelas |
| `create-drop` | Testes | Cria na inicialização, apaga no encerramento |
| `update` | **Padrão Code First** | Atualiza schema preservando dados |
| `validate` | Produção | Apenas valida se schema está sincronizado |
| `none` | Produção com controle manual | Não gerencia schema automaticamente |

### Vantagens Code First
✅ **Desenvolvimento ágil**: Mudanças nas entidades refletem automaticamente no banco
✅ **Sincronização automática**: Sem necessidade de manter XMLs de migração
✅ **Type-safe**: Compilador Java valida estruturas
✅ **Refactoring seguro**: IDEs modernas ajudam com mudanças de nome

### Desvantagens Code First
❌ **Controle limitado**: Hibernate pode gerar DDL não otimizado
❌ **Dados complexos**: Migrações de dados devem ser feitas manualmente
❌ **Produção arriscada**: Mudanças automáticas podem impactar performance
❌ **Versionamento**: Difícil rastrear histórico de mudanças do schema

---

## 📝 Orientações para Futuras Mudanças

### 🔄 Desenvolvimento
Para mudanças durante desenvolvimento:

1. **Modificar entidades Java** primeiro
2. **Configurar `ddl-auto=update`** no application.properties
3. **Reiniciar aplicação** para aplicar mudanças
4. **Validar schema** gerado no banco

### 🧪 Testes
Para ambientes de teste:

```properties
# application-test.properties
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true
```

### 🚀 Produção
Para ambiente de produção, duas abordagens:

#### Opção 1: Code First Controlado
```properties
# application-prod.properties
spring.jpa.hibernate.ddl-auto=validate
spring.liquibase.enabled=false
```
- Use scripts SQL manuais para mudanças
- Valide schema antes do deploy

#### Opção 2: Híbrida (Recomendado)
```properties
# application-prod.properties
spring.jpa.hibernate.ddl-auto=validate
spring.liquibase.enabled=true
```
- Gere schema inicial via Code First em desenvolvimento
- Exporte DDL e crie changelogs Liquibase para produção
- Use Liquibase para versionamento e rollback

### 🔍 Validação de Schema

#### Comando para Gerar DDL
```bash
# Gerar DDL a partir das entidades
./mvnw clean compile

# Com mostrar SQL habilitado
export SHOW_SQL=true
./mvnw spring-boot:run
```

#### Comando para Validar Schema
```bash
# Validar se entidades estão sincronizadas com o banco
export DDL_AUTO=validate
./mvnw spring-boot:run
```

### 📊 Monitoramento de Mudanças

#### 1. Backup antes de mudanças
```bash
pg_dump -h localhost -U rag_user rag_db > backup_$(date +%Y%m%d_%H%M%S).sql
```

#### 2. Log de mudanças Hibernate
```properties
logging.level.org.hibernate.tool.hbm2ddl=DEBUG
logging.level.org.hibernate.SQL=DEBUG
```

#### 3. Teste de migração
```bash
# Teste com dados de exemplo
export DDL_AUTO=create
./mvnw spring-boot:run

# Inserir dados de teste
# Depois testar mudanças com update
export DDL_AUTO=update
./mvnw spring-boot:run
```

### ⚠️ Cuidados Especiais

#### Campos Especiais do PostgreSQL
Alguns campos requerem atenção especial:

```java
// Vetores PGVector
@Column(columnDefinition = "vector(1536)")
private float[] embeddingVector;

// Enums customizados
@Enumerated(EnumType.STRING)
@Column(columnDefinition = "tipo_embedding")
private TipoEmbedding tipoEmbedding;

// JSONB
@Type(JsonType.class)
@Column(columnDefinition = "jsonb")
private Map<String, Object> metadados;

// TSVector (read-only)
@Column(columnDefinition = "tsvector", insertable = false, updatable = false)
private String textoIndexado;
```

#### Constraints Complexas
Para validações complexas, mantenha no código Java:

```java
@PrePersist
@PreUpdate
private void validateWeights() {
    if (pesoSemantico != null && pesoTextual != null) {
        float sum = pesoSemantico + pesoTextual;
        if (Math.abs(sum - 1.0f) > 0.001f) {
            throw new IllegalStateException("Soma dos pesos deve ser 1.0");
        }
    }
}
```

### 🔄 Workflow Recomendado

#### Para Novas Features
1. **Desenvolvimento**: Code First com `ddl-auto=update`
2. **Teste**: Code First com `ddl-auto=create-drop`
3. **Staging**: Validação com `ddl-auto=validate`
4. **Produção**: Scripts SQL manuais + Liquibase

#### Para Mudanças Breaking
1. **Planejar migração** de dados existentes
2. **Criar scripts de backup** e restore
3. **Testar em ambiente isolado**
4. **Documentar processo** de rollback
5. **Executar em janela de manutenção**

---

## 🛠️ Comandos de Migração

### Desenvolvimento
```bash
# Aplicar mudanças automaticamente
export DDL_AUTO=update
./mvnw spring-boot:run

# Recriar schema do zero (CUIDADO: apaga dados)
export DDL_AUTO=create
./mvnw spring-boot:run
```

### Teste
```bash
# Schema temporário para testes
export DDL_AUTO=create-drop
./mvnw test
```

### Produção
```bash
# Apenas validar (não modifica)
export DDL_AUTO=validate
export LIQUIBASE_ENABLED=true
./mvnw spring-boot:run

# Gerar DDL para análise manual
export DDL_AUTO=none
./mvnw clean compile -Dspring-boot.run.arguments="--spring.jpa.properties.javax.persistence.schema-generation.scripts.action=create --spring.jpa.properties.javax.persistence.schema-generation.scripts.create-target=schema.sql"
```

### Troubleshooting
```bash
# Ver logs detalhados do Hibernate
export SHOW_SQL=true
export SQL_LOG_LEVEL=DEBUG
export HIBERNATE_BINDER_LOG=TRACE
./mvnw spring-boot:run

# Verificar conexão com banco
docker exec -it jsimplerag_postgres_1 psql -U rag_user -d rag_db -c "\dt"
```

---

## 📚 Referências

- [Hibernate DDL Auto](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.using-hibernate)
- [Spring Data JPA](https://spring.io/projects/spring-data-jpa)
- [PostgreSQL PGVector](https://github.com/pgvector/pgvector)
- [Liquibase Documentation](https://docs.liquibase.com/home.html)

---

**Última atualização**: $(date +%Y-%m-%d)
**Versão do projeto**: 0.0.1-SNAPSHOT