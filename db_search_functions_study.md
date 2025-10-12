# Estudo: Busca Textual e Híbrida - JSimpleRag

**Data**: 2025-10-06
**Versão**: 1.0
**Projeto**: JSimpleRag v0.0.1-SNAPSHOT

---

## 📋 Índice

1. [Contexto e Problema](#contexto-e-problema)
2. [Análise da Implementação Atual](#análise-da-implementação-atual)
3. [Requisitos de Busca Textual](#requisitos-de-busca-textual)
4. [Estratégias de Implementação](#estratégias-de-implementação)
5. [Code First vs Database First](#code-first-vs-database-first)
6. [Recomendações e Solução Proposta](#recomendações-e-solução-proposta)
7. [Implementação Detalhada](#implementação-detalhada)
8. [Plano de Migração](#plano-de-migração)

---

## 🎯 Contexto e Problema

### Situação Atual

O projeto JSimpleRag implementa um sistema de busca híbrida combinando:
- **Busca Semântica**: Usando PGVector para similaridade de embeddings
- **Busca Textual**: Usando PostgreSQL Full-Text Search com tsvector

### Problemas Identificados

1. **Campos Especiais PostgreSQL**:
   - `tsvector` para full-text search
   - `vector` para embeddings (PGVector)
   - Hibernate não gera DDL otimizado para esses tipos

2. **Requisitos de Busca Textual**:
   - Conversão para minúsculas
   - Remoção de acentos (unaccent)
   - Operador OR entre palavras/expressões
   - Suporte a expressões multi-palavra (entre aspas duplas)
   - Exemplo: `manteiga OR "pão quente"` → `mantei | (pao <=> quent)`

3. **Complexidade de DDL**:
   - Generated columns (`text_search_tsv`)
   - Triggers para atualização automática
   - Índices especializados (GIN, IVFFlat)
   - Text search configurations customizadas

4. **Conflito Code First vs Liquibase**:
   - Code First: Automático mas limitado
   - Liquibase: Controle total mas trabalhoso
   - Divergências entre entidades Java e schema real

---

## 🔍 Análise da Implementação Atual

### 1. Entidade DocumentEmbedding

```java
// src/main/java/bor/tools/simplerag/entity/DocumentEmbedding.java

// Campo text_search_tsv (GENERATED COLUMN - comentado na entidade)
@Column(name = "text_search_tsv", columnDefinition = "tsvector",
        insertable = false, updatable = false)
@ToString.Exclude
private Object text_search_tsv;

// Campo embedding_vector (sem dimensão fixa)
@Column(name = "embedding_vector", columnDefinition = "vector")
@ToString.Exclude
private float[] embeddingVector;

// Campo texto_indexado (atualizado por trigger)
@Column(name = "texto_indexado", columnDefinition = "tsvector",
        insertable = false, updatable = false)
@ToString.Exclude
private String textoIndexado;
```


### 2. Liquibase Atual (003-create-tables.xml)

```xml
<changeSet id="003-004-create-doc-embedding-table" author="jsimplerag">
    <sql>
        CREATE TABLE doc_embedding (
            id SERIAL PRIMARY KEY,
            biblioteca_id BIGINT NOT NULL,
            documento_id BIGINT NOT NULL,
            capitulo_id BIGINT,
            tipo_embedding tipo_embedding NOT NULL,
            trecho_texto TEXT,
            ordem_cap INTEGER,
            embedding_vector vector(),
            text_search_tsv tsvector GENERATED ALWAYS AS (
				    setweight(to_tsvector('portuguese', COALESCE(metadados->>'nome', '')), 'A') ||         -- Título ou nome da obra
				    setweight(to_tsvector('portuguese', COALESCE(metadados->>'capitulo', '')), 'A') ||     -- Nome do capítulo
				    setweight(to_tsvector('portuguese', COALESCE(metadados->>'descricao', '')), 'B') ||    -- Descrição geral
				    setweight(to_tsvector('portuguese', COALESCE(metadados->>'area_conhecimento', '')), 'C') || -- Área temática
				    setweight(to_tsvector('portuguese', COALESCE(metadados->>'palavras_chave', '')), 'C') ||   -- Palavras-chave
				    setweight(to_tsvector('portuguese', COALESCE(metadados->>'autor', '')), 'D') ||        -- Autor (menos relevante para conteúdo)
				    setweight(to_tsvector('portuguese', COALESCE(metadados->>'metadados', '')), 'D') ||    -- Outros metadados
				    setweight(to_tsvector('portuguese', COALESCE(texto, '')), 'C')                         -- Texto completo do chunk
				) stored,
            metadados JSONB,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            -- Foreign keys...
        );
    </sql>
</changeSet>
```

**Problemas**:
1. `embedding_vector` tem dimensão fixa (1536) - limita uso de outros modelos
2. `texto_indexado` não é generated column - depende de trigger
3. **FALTA** o campo `text_search_tsv` documentado na entidade

### 3. Trigger Atual (005-create-triggers.xml)

```sql
CREATE OR REPLACE FUNCTION update_texto_indexado()
RETURNS TRIGGER AS $$
BEGIN
    -- Apenas indexa trecho_texto com configuração portuguesa
    NEW.texto_indexado = to_tsvector('portuguese', COALESCE(NEW.trecho_texto, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

**Limitações**:
1. ❌ Não usa `unaccent` (acentos permanecem)
2. ❌ Não indexa metadados (apenas `trecho_texto`)
3. ❌ Não usa `setweight` para priorização
4. ❌ Não implementa a lógica complexa descrita na entidade

### 4. Repository Atual (DocEmbeddingJdbcRepository.java)

```java
// Busca textual usa to_tsquery
text_search AS (
    SELECT id,
           1.0 / (? + RANK() OVER (ORDER BY ts_rank_cd(texto_indexado,
                  to_tsquery('portuguese', ?)) DESC)) AS score_text,
           RANK() OVER (ORDER BY ts_rank_cd(texto_indexado,
                  to_tsquery('portuguese', ?)) DESC) AS rank_text
    FROM doc_embedding
    WHERE biblioteca_id IN (...)
    AND texto_indexado @@ to_tsquery('portuguese', ?)
    LIMIT ?
)
```

**Problema**: Usa `to_tsquery` que exige sintaxe específica (`&`, `|`, `<->`) - não é user-friendly.

---

## 📝 Requisitos de Busca Textual

### Especificação do Usuário

> "As buscas textuais terão que usar webSearch, com todas as palavras em minúsculas e sem acentos, fazendo operações OR entre todas as palavras ou expressões. Expressões são multipalavras entre aspas duplas."

### Exemplos de Conversão

| Query do Usuário | Query PostgreSQL Esperada | Explicação |
|------------------|---------------------------|------------|
| `manteiga` | `to_tsquery('simple', 'mantei')` | Palavra única normalizada |
| `manteiga pão` | `to_tsquery('simple', 'mantei \| pao')` | OR entre palavras |
| `"pão quente"` | `phraseto_tsquery('simple', 'pao quent')` | Frase exata (adjacência) |
| `manteiga "pão quente"` | `to_tsquery('simple', 'mantei \| (pao <=> quent)')` | Palavra OR frase |
| `café açúcar` | `to_tsquery('simple', 'cafe \| acucar')` | Remoção de acentos |

### Função Recomendada: `websearch_to_tsquery`

PostgreSQL oferece `websearch_to_tsquery` que implementa exatamente essa sintaxe web:

```sql
-- Exemplo oficial
SELECT websearch_to_tsquery('english', '"sad cat" or "fat rat"');
-- Resultado: 'sad' <-> 'cat' | 'fat' <-> 'rat'
```

**Vantagens**:
- ✅ Entende `or` como operador `|`
- ✅ Entende `-` como NOT
- ✅ Aspas para frases (`"pão quente"` → `pao <=> quent`)
- ✅ Nunca gera erro de sintaxe
- ✅ Aceita input direto do usuário

---

## 🛠️ Estratégias de Implementação

### Opção 1: Função PL/pgSQL Customizada (❌ Não Recomendado)

```sql
CREATE OR REPLACE FUNCTION search_query_processor(query TEXT)
RETURNS tsquery AS $$
DECLARE
    result tsquery;
BEGIN
    -- Processar manualmente: minúsculas, unaccent, split, OR...
    -- Muita complexidade, risco de bugs
END;
$$ LANGUAGE plpgsql;
```

**Problemas**:
- Reimplementa lógica já existente no PostgreSQL
- Difícil manter e testar
- Performance pode ser inferior

### Opção 2: Text Search Configuration + unaccent (✅ RECOMENDADO)

```sql
-- 1. Instalar extensão unaccent
CREATE EXTENSION IF NOT EXISTS unaccent;

-- 2. Criar configuração customizada
CREATE TEXT SEARCH CONFIGURATION simple_unaccent (COPY = simple);

-- 3. Adicionar unaccent à pipeline
ALTER TEXT SEARCH CONFIGURATION simple_unaccent
    ALTER MAPPING FOR asciiword, word, numword, hword, hword_part, hword_numpart
    WITH unaccent, simple;

-- 4. Usar em queries
SELECT websearch_to_tsquery('simple_unaccent', 'café açúcar "pão quente"');
-- Resultado: cafe | acucar | (pao <=> quente)
```

**Vantagens**:
- ✅ Usa recursos nativos do PostgreSQL
- ✅ Performance otimizada
- ✅ Manutenção mínima
- ✅ Testado e documentado
- ✅ Atualizado automaticamente com PostgreSQL

### Opção 3: Application-Level Processing (⚠️ Alternativa)

```java
// Processar no Java antes de enviar ao banco
public String processSearchQuery(String userQuery) {
    // Normalizar, remover acentos, construir sintaxe OR...
    return normalizedQuery;
}
```

**Problemas**:
- Duplica lógica (banco já tem isso)
- Dificulta otimizações do query planner
- Mais pontos de falha

---

## ⚖️ Code First vs Database First

### Análise para Tipos Especiais

#### 1. Generated Columns

**Code First (Hibernate)**:
```java
// ❌ Hibernate NÃO suporta generated columns nativamente
@Column(name = "text_search_tsv", columnDefinition = "tsvector GENERATED ALWAYS AS (...)")
```

**Database First (Liquibase)**:
```xml
<!-- ✅ Liquibase permite DDL completo -->
<sql>
    ALTER TABLE doc_embedding ADD COLUMN text_search_tsv tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('simple_unaccent', COALESCE(metadados->>'nome', '')), 'A') ||
        setweight(to_tsvector('simple_unaccent', COALESCE(trecho_texto, '')), 'C')
    ) STORED;
</sql>
```

#### 2. Vector Dimensions

**Code First**:
```java
// ❌ Dimensão fixa no código
@Column(columnDefinition = "vector(1536)")
private float[] embeddingVector;

// Se mudar para vector(768), precisa alterar código Java
```

**Database First**:
```xml
<!-- ✅ Sem dimensão fixa -->
<sql>
    ALTER TABLE doc_embedding ADD COLUMN embedding_vector vector;
</sql>
```

#### 3. Text Search Configurations

**Code First**: ❌ Impossível criar text search configurations via Hibernate

**Database First**: ✅ Controle total via SQL nativo

### Conclusão

**Para tipos PostgreSQL especiais, Database First (Liquibase) é OBRIGATÓRIO.**

Code First pode ser usado para estruturas simples, mas:
- Generated columns
- Vector dimensions flexíveis
- Text search configurations
- Triggers complexos
- Índices especializados (GIN, IVFFlat)

**Todos requerem Liquibase.**

---

## 🎯 Recomendações e Solução Proposta

### Estratégia Híbrida Refinada

| Componente | Ferramenta | Justificativa |
|------------|------------|---------------|
| **Tabelas básicas** | Code First (dev) → Liquibase (prod) | Agilidade + controle |
| **Tipos especiais** | Liquibase SEMPRE | Hibernate limitado |
| **Generated columns** | Liquibase SEMPRE | Hibernate não suporta |
| **Text search config** | Liquibase SEMPRE | Funcionalidade PostgreSQL |
| **Triggers** | Liquibase SEMPRE | Controle fino necessário |
| **Índices GIN/IVFFlat** | Liquibase SEMPRE | Performance crítica |

### Solução para Busca Textual

**NÃO criar função PL/pgSQL customizada.**

**Usar `websearch_to_tsquery` + text search configuration com unaccent:**

```sql
-- 1. Setup (uma vez)
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE TEXT SEARCH CONFIGURATION simple_unaccent (COPY = simple);
ALTER TEXT SEARCH CONFIGURATION simple_unaccent
    ALTER MAPPING FOR asciiword, word, numword, hword, hword_part, hword_numpart
    WITH unaccent, simple;

-- 2. Generated column para indexação rica
ALTER TABLE doc_embedding ADD COLUMN text_search_tsv tsvector
GENERATED ALWAYS AS (
    setweight(to_tsvector('simple_unaccent', COALESCE(metadados->>'nome', '')), 'A') ||
    setweight(to_tsvector('simple_unaccent', COALESCE(metadados->>'capitulo', '')), 'A') ||
    setweight(to_tsvector('simple_unaccent', COALESCE(metadados->>'descricao', '')), 'B') ||
    setweight(to_tsvector('simple_unaccent', COALESCE(trecho_texto, '')), 'C')
) STORED;

-- 3. Índice GIN
CREATE INDEX idx_text_search_tsv ON doc_embedding USING GIN(text_search_tsv);

-- 4. Uso no Java
String userQuery = "café açúcar \"pão quente\"";
String sql = """
    SELECT * FROM doc_embedding
    WHERE text_search_tsv @@ websearch_to_tsquery('simple_unaccent', ?)
    ORDER BY ts_rank_cd(text_search_tsv, websearch_to_tsquery('simple_unaccent', ?)) DESC
    """;
```

### Benefícios

1. ✅ **Sem função customizada**: Usa recursos nativos
2. ✅ **Unaccent automático**: café = cafe
3. ✅ **Sintaxe web-friendly**: Aceita input do usuário diretamente
4. ✅ **OR por padrão**: `café açúcar` = `cafe | acucar`
5. ✅ **Frases entre aspas**: `"pão quente"` = `pao <=> quente`
6. ✅ **Weighted search**: Metadados mais relevantes que texto
7. ✅ **Generated column**: Atualização automática
8. ✅ **Performance**: Índice GIN otimizado

---

## 📐 Implementação Detalhada

### Changeset 1: Extensão Unaccent

```xml
<!-- 006-add-unaccent-extension.xml -->
<changeSet id="006-001-create-unaccent-extension" author="jsimplerag">
    <sql>
        CREATE EXTENSION IF NOT EXISTS unaccent;
    </sql>
    <rollback>
        DROP EXTENSION IF EXISTS unaccent;
    </rollback>
</changeSet>
```

### Changeset 2: Text Search Configuration

```xml
<changeSet id="006-002-create-text-search-config" author="jsimplerag">
    <sql>
        -- Criar configuração sem acentos baseada em 'simple'
        CREATE TEXT SEARCH CONFIGURATION simple_unaccent (COPY = simple);

        -- Adicionar unaccent à pipeline de processamento
        ALTER TEXT SEARCH CONFIGURATION simple_unaccent
            ALTER MAPPING FOR asciiword, word, numword, hword, hword_part, hword_numpart
            WITH unaccent, simple;
    </sql>
    <rollback>
        DROP TEXT SEARCH CONFIGURATION IF EXISTS simple_unaccent;
    </rollback>
</changeSet>
```

**Nota**: Usar `simple` (não `portuguese`) porque:
- `simple` não faz stemming (mantém palavras inteiras)
- Stemming português pode distorcer queries: "quente" → "quent"
- Unaccent é suficiente para normalização

### Changeset 3: Generated Column text_search_tsv

```xml
<changeSet id="006-003-add-text-search-tsv-column" author="jsimplerag">
    <sql>
        -- Adicionar coluna generated para busca textual enriquecida
        ALTER TABLE doc_embedding ADD COLUMN text_search_tsv tsvector
        GENERATED ALWAYS AS (
            -- Título ou nome da obra (peso A - máxima relevância)
            setweight(to_tsvector('simple_unaccent', COALESCE(metadados->>'nome', '')), 'A') ||

            -- Nome do capítulo (peso A)
            setweight(to_tsvector('simple_unaccent', COALESCE(metadados->>'capitulo', '')), 'A') ||

            -- Descrição geral (peso B)
            setweight(to_tsvector('simple_unaccent', COALESCE(metadados->>'descricao', '')), 'B') ||

            -- Área de conhecimento (peso C)
            setweight(to_tsvector('simple_unaccent', COALESCE(metadados->>'area_conhecimento', '')), 'C') ||

            -- Palavras-chave (peso C)
            setweight(to_tsvector('simple_unaccent', COALESCE(metadados->>'palavras_chave', '')), 'C') ||

            -- Autor (peso D - menos relevante para conteúdo)
            setweight(to_tsvector('simple_unaccent', COALESCE(metadados->>'autor', '')), 'D') ||

            -- Texto completo do chunk (peso C)
            setweight(to_tsvector('simple_unaccent', COALESCE(trecho_texto, '')), 'C')
        ) STORED;
    </sql>
    <rollback>
        ALTER TABLE doc_embedding DROP COLUMN IF EXISTS text_search_tsv;
    </rollback>
</changeSet>
```

### Changeset 4: Índice GIN

```xml
<changeSet id="006-004-create-text-search-index" author="jsimplerag">
    <sql>
        -- Índice GIN para busca textual eficiente
        CREATE INDEX idx_text_search_tsv ON doc_embedding USING GIN(text_search_tsv);
    </sql>
    <rollback>
        DROP INDEX IF EXISTS idx_text_search_tsv;
    </rollback>
</changeSet>
```

### Changeset 5: Remover Dimensão Fixa do Vector

```xml
<changeSet id="006-005-update-vector-dimension" author="jsimplerag">
    <sql>
        -- Alterar embedding_vector para não ter dimensão fixa
        -- Permite uso de diferentes modelos (768, 1536, 3072, etc.)
        ALTER TABLE doc_embedding ALTER COLUMN embedding_vector TYPE vector;
    </sql>
    <rollback>
        -- Voltar para dimensão fixa 1536
        ALTER TABLE doc_embedding ALTER COLUMN embedding_vector TYPE vector(1536);
    </rollback>
</changeSet>
```

### Changeset 6: Remover Trigger Obsoleto

```xml
<changeSet id="006-006-remove-obsolete-trigger" author="jsimplerag">
    <sql>
        -- Remover trigger antigo pois agora usamos generated column
        DROP TRIGGER IF EXISTS trigger_update_texto_indexado ON doc_embedding;
        DROP FUNCTION IF EXISTS update_texto_indexado();

        -- Opcional: remover coluna texto_indexado se não for mais usada
        -- ALTER TABLE doc_embedding DROP COLUMN IF EXISTS texto_indexado;
    </sql>
    <rollback>
        -- Recriar trigger original (ver 005-create-triggers.xml)
        CREATE OR REPLACE FUNCTION update_texto_indexado()
        RETURNS TRIGGER AS $$
        BEGIN
            NEW.texto_indexado = to_tsvector('portuguese', COALESCE(NEW.trecho_texto, ''));
            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;

        CREATE TRIGGER trigger_update_texto_indexado
            BEFORE INSERT OR UPDATE OF trecho_texto
            ON doc_embedding
            FOR EACH ROW
            EXECUTE FUNCTION update_texto_indexado();
    </rollback>
</changeSet>
```

### Atualização do Repository

```java
// src/main/java/bor/tools/simplerag/repository/DocEmbeddingJdbcRepository.java

public List<DocumentEmbedding> pesquisaHibrida(
        @NonNull float[] embedding,
        @NonNull String queryTexto, // Query do usuário, ex: "café açúcar \"pão quente\""
        @NonNull Integer[] bibliotecaIds,
        @NonNull Float pesoSemantico,
        @NonNull Float pesoTextual,
        @NonNull Integer k) {

    if (k == null) k = k_pesquisa;

    String libIds = Arrays.stream(bibliotecaIds)
                         .map(String::valueOf)
                         .collect(Collectors.joining(", "));

    String sql = """
        WITH semantic_search AS (
            SELECT id,
                   1.0 / (? + RANK() OVER (ORDER BY embedding_vector <=> ? ASC)) AS score_semantic,
                   RANK() OVER (ORDER BY embedding_vector <=> ? ASC) AS rank_semantic
            FROM doc_embedding
            WHERE biblioteca_id IN (%s)
            LIMIT ?
        ),
        text_search AS (
            SELECT id,
                   1.0 / (? + RANK() OVER (
                       ORDER BY ts_rank_cd(text_search_tsv,
                                           websearch_to_tsquery('simple_unaccent', ?)) DESC
                   )) AS score_text,
                   RANK() OVER (
                       ORDER BY ts_rank_cd(text_search_tsv,
                                           websearch_to_tsquery('simple_unaccent', ?)) DESC
                   ) AS rank_text
            FROM doc_embedding
            WHERE biblioteca_id IN (%s)
            AND text_search_tsv @@ websearch_to_tsquery('simple_unaccent', ?)
            LIMIT ?
        )
        SELECT d.*,
               COALESCE(s.score_semantic, 0.0) AS score_semantic,
               COALESCE(t.score_text, 0.0) AS score_text,
               (COALESCE(s.score_semantic, 0.0) * ? + COALESCE(t.score_text, 0.0) * ?) AS score
        FROM doc_embedding d
        LEFT JOIN semantic_search s ON d.id = s.id
        LEFT JOIN text_search t ON d.id = t.id
        WHERE d.biblioteca_id IN (%s)
        AND (s.id IS NOT NULL OR t.id IS NOT NULL)
        ORDER BY score DESC
        LIMIT ?
        """.formatted(libIds, libIds, libIds);

    Object[] params = new Object[] {
        k, // normalization factor semantic
        new PGvector(embedding), // semantic_search
        new PGvector(embedding), // semantic_search RANK
        k * 2, // expanded limit semantic
        k, // normalization factor text
        queryTexto, // ← Input direto do usuário!
        queryTexto, // text_search RANK
        k * 2, // expanded limit text
        queryTexto, // text_search WHERE
        pesoSemantico,
        pesoTextual,
        k
    };

    return jdbcTemplate.query(sql, rowMapperWithScores, params);
}

public List<DocumentEmbedding> pesquisaTextual(
        @NonNull String queryTexto,
        @NonNull Integer[] bibliotecaIds,
        @NonNull Integer k) {

    if (k == null) k = k_pesquisa;

    String libIds = Arrays.stream(bibliotecaIds)
                         .map(String::valueOf)
                         .collect(Collectors.joining(", "));

    String sql = """
        SELECT d.*,
               0.0 AS score_semantic,
               ts_rank_cd(text_search_tsv, websearch_to_tsquery('simple_unaccent', ?)) AS score_text,
               ts_rank_cd(text_search_tsv, websearch_to_tsquery('simple_unaccent', ?)) AS score
        FROM doc_embedding d
        WHERE biblioteca_id IN (%s)
        AND text_search_tsv @@ websearch_to_tsquery('simple_unaccent', ?)
        ORDER BY score DESC
        LIMIT ?
        """.formatted(libIds);

    Object[] params = new Object[] { queryTexto, queryTexto, queryTexto, k };

    return jdbcTemplate.query(sql, rowMapperWithScores, params);
}
```

### Atualização da Entidade

```java
// src/main/java/bor/tools/simplerag/entity/DocumentEmbedding.java

/**
 * Full-text search vector with weighted metadata, automatically generated by PostgreSQL.<br>
 * This is a GENERATED ALWAYS column that combines multiple metadata fields with different weights:<br>
 * <ul>
 *   <li>Weight A (highest): nome, capitulo</li>
 *   <li>Weight B: descricao</li>
 *   <li>Weight C: area_conhecimento, palavras_chave, trecho_texto</li>
 *   <li>Weight D (lowest): autor</li>
 * </ul>
 *
 * Uses 'simple_unaccent' text search configuration for accent-insensitive search.
 *
 * <h2>Usage in queries:</h2>
 * <pre>
 * SELECT * FROM doc_embedding
 * WHERE text_search_tsv @@ websearch_to_tsquery('simple_unaccent', 'café "pão quente"')
 * ORDER BY ts_rank_cd(text_search_tsv, websearch_to_tsquery('simple_unaccent', 'café "pão quente"')) DESC;
 * </pre>
 *
 * <h2>Notes:</h2>
 * <ul>
 *   <li>This field is managed by the database and should not be set manually.
 *   <li>For full-text search, use native queries via DocEmbeddingJdbcRepository.
 *   <li>Do not include in toString, equals, hashCode, or JSON serialization.
 * </ul>
 */
@Column(name = "text_search_tsv", columnDefinition = "tsvector", insertable = false, updatable = false)
@ToString.Exclude
private Object textSearchTsv;

/**
 * Vector embedding stored as float array with flexible dimensions.<br>
 * Supports multiple embedding models (768, 1536, 3072, etc.).
 *
 * <h2>Usage:</h2>
 * Use DocEmbeddingJdbcRepository for vector similarity operations.
 *
 * <h2>Notes:</h2>
 * <ul>
 *   <li>Dimension is not fixed at DDL level.
 *   <li>Use float[] for Java representation.
 *   <li>Converted to PGvector for database operations.
 *   <li>Do not include in toString, equals, or hashCode.
 * </ul>
 */
@Column(name = "embedding_vector", columnDefinition = "vector")
@ToString.Exclude
private float[] embeddingVector;
```

---

## 🚀 Plano de Migração

### Fase 1: Preparação (Desenvolvimento)

1. **Criar changesets Liquibase** (006-*.xml)
2. **Testar em ambiente local**:
   ```bash
   docker-compose up -d postgres
   ./mvnw liquibase:update
   ```
3. **Validar queries**:
   ```sql
   -- Teste manual
   SELECT websearch_to_tsquery('simple_unaccent', 'café açúcar "pão quente"');
   -- Esperado: 'cafe' | 'acucar' | ( 'pao' <-> 'quente' )
   ```

### Fase 2: Atualização do Código

1. **Atualizar DocEmbeddingJdbcRepository.java**
   - Substituir `to_tsquery` por `websearch_to_tsquery`
   - Substituir `texto_indexado` por `text_search_tsv`
   - Atualizar configuração de `'portuguese'` para `'simple_unaccent'`

2. **Atualizar DocumentEmbedding.java**
   - Atualizar documentação do campo `text_search_tsv`
   - Remover/depreciar campo `texto_indexado` se não for mais usado

3. **Atualizar SearchController.java** (se existir)
   - Aceitar queries em linguagem natural
   - Remover processamento manual de sintaxe

### Fase 3: Testes

1. **Testes Unitários**:
   ```java
   @Test
   void testWebSearchQuery() {
       String userQuery = "café açúcar \"pão quente\"";
       List<DocumentEmbedding> results = repository.pesquisaTextual(
           userQuery, new Integer[]{1}, 10
       );
       assertNotNull(results);
   }
   ```

2. **Testes de Integração**:
   ```bash
   ./mvnw test -Dtest=DocEmbeddingJdbcRepositoryTest
   ```

3. **Testes Manuais via SQL**:
   ```sql
   -- Inserir documento de teste
   INSERT INTO doc_embedding (biblioteca_id, documento_id, tipo_embedding, trecho_texto, metadados)
   VALUES (1, 1, 'trecho', 'Receita de pão quente com manteiga e café fresquinho',
           '{"nome": "Livro de Receitas", "capitulo": "Cafés da Manhã"}');

   -- Testar busca
   SELECT trecho_texto,
          ts_rank_cd(text_search_tsv, websearch_to_tsquery('simple_unaccent', 'café "pão quente"')) AS rank
   FROM doc_embedding
   WHERE text_search_tsv @@ websearch_to_tsquery('simple_unaccent', 'café "pão quente"')
   ORDER BY rank DESC;
   ```

### Fase 4: Deploy

1. **Staging**:
   ```properties
   # application-staging.properties
   spring.jpa.hibernate.ddl-auto=validate
   spring.liquibase.enabled=true
   ```

2. **Produção** (com backup):
   ```bash
   # Backup
   pg_dump -h prod_host -U rag_user rag_db > backup_before_migration.sql

   # Deploy
   ./mvnw liquibase:update -Pproduction

   # Validar
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=prod
   ```

### Fase 5: Monitoramento

1. **Query Performance**:
   ```sql
   EXPLAIN ANALYZE
   SELECT * FROM doc_embedding
   WHERE text_search_tsv @@ websearch_to_tsquery('simple_unaccent', 'café "pão quente"')
   LIMIT 10;
   ```

2. **Index Usage**:
   ```sql
   SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
   FROM pg_stat_user_indexes
   WHERE indexname = 'idx_text_search_tsv';
   ```

---

## 📊 Comparação: Antes vs Depois

| Aspecto | ANTES | DEPOIS |
|---------|-------|--------|
| **Query do usuário** | Precisa usar `&`, `\|`, `<->` | Linguagem natural |
| **Acentos** | `café` ≠ `cafe` | `café` = `cafe` |
| **Frases** | `pao & quente` (separados) | `"pão quente"` (frase) |
| **Operador padrão** | AND (`&`) | OR (`\|`) |
| **Campos indexados** | Apenas `trecho_texto` | Metadados + texto |
| **Priorização** | Sem pesos | 4 níveis (A/B/C/D) |
| **Manutenção** | Trigger manual | Generated column |
| **Dimensão vector** | Fixa 1536 | Flexível |
| **Configuração** | `portuguese` (stemming) | `simple_unaccent` (sem stem) |

---

## ✅ Conclusões

### Decisões Finais

1. **NÃO criar função PL/pgSQL customizada** para processar queries
   - Usar `websearch_to_tsquery` (recurso nativo PostgreSQL)

2. **Usar Text Search Configuration com unaccent**
   - Configuração `simple_unaccent` (sem stemming)
   - Remoção automática de acentos

3. **Generated Column para indexação**
   - `text_search_tsv` GENERATED ALWAYS
   - Combina metadados + texto com pesos

4. **Liquibase para gerenciar complexidade**
   - Code First não suporta generated columns
   - Database First necessário para tipos PostgreSQL especiais

5. **Vector sem dimensão fixa**
   - Suporta múltiplos modelos de embedding
   - Flexibilidade para futuras mudanças

### Próximos Passos

1. ✅ Criar changesets Liquibase (006-*.xml)
2. ✅ Atualizar DocEmbeddingJdbcRepository.java
3. ✅ Atualizar DocumentEmbedding.java
4. ✅ Criar testes de integração
5. ✅ Validar em ambiente local
6. ✅ Deploy em staging
7. ✅ Monitorar performance
8. ✅ Deploy em produção

### Benefícios Esperados

- 🚀 **Performance**: Índice GIN otimizado, generated column
- 🎯 **UX**: Queries em linguagem natural
- 🔧 **Manutenção**: Menos código customizado
- 🔒 **Confiabilidade**: Recursos nativos e testados
- 📈 **Escalabilidade**: Suporta milhões de documentos
- 🌍 **Flexibilidade**: Múltiplos idiomas e modelos

---

**Documento preparado por**: Claude Code
**Data**: 2025-10-06
**Status**: ✅ Pronto para implementação
