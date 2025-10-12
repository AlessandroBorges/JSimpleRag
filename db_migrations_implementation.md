# Plano de Implementação: Busca Textual com websearch_to_tsquery

**Data**: 2025-10-11
**Versão**: 1.0
**Projeto**: JSimpleRag v0.0.1-SNAPSHOT
**Base**: Análise db_search_functions_study.md vs Liquibase atual

---

## 📊 Status Atual da Implementação

### ✅ Já Implementado

1. **Extensões PostgreSQL** (001-create-extensions.xml)
   - ✅ `vector` - PGVector para embeddings
   - ✅ `pg_trgm` - Trigram matching
   - ✅ `unaccent` - Remoção de acentos

2. **Enumerações** (002-create-enums.xml)
   - ✅ `tipo_embedding` com valor 'metadados'
   - ✅ `tipo_conteudo` (21 valores)
   - ✅ `tipo_secao` (18 valores)
   - ✅ `tipo_biblioteca` (compartilhada, pessoal, chat, projeto)
   - ✅ `tipo_associacao` (proprietario, colaborador, leitor)

3. **Tabelas Core** (003-create-tables.xml)
   - ✅ `library` (renomeada de biblioteca)
   - ✅ `documento`
   - ✅ `chapter` (renomeada de capitulo)
   - ✅ `doc_embedding` com:
     - ✅ `embedding_vector vector(768)` - Dimensão 768 definida
     - ✅ `text_search_tsv` como GENERATED COLUMN
     - ⚠️ **ATENÇÃO**: Campo `libray_id` tem TYPO (deveria ser `library_id`)
     - ⚠️ **ATENÇÃO**: Usa configuração `'portuguese'` (não `'simple_unaccent'`)

4. **Índices** (004-create-indexes.xml)
   - ✅ Índices básicos em library, documento, chapter
   - ✅ `idx_embedding_vector` (IVFFlat para vector similarity)
   - ✅ `idx_text_search_tsv` (GIN para full-text search)
   - ✅ `idx_embedding_texto` (duplicado do anterior - considerar remover)

5. **Triggers** (005-create-triggers.xml)
   - ✅ Arquivo vazio com comentário explicativo
   - ✅ Triggers de updated_at removidos (gerenciados por JPA)
   - ✅ Trigger de text_search_tsv removido (gerenciado por GENERATED COLUMN)

6. **Tabelas de Usuário e Chat** (006-create-user-project-chat-tables.xml)
   - ✅ `user` table
   - ✅ `user_library` association table
   - ✅ `chat_project` table
   - ✅ `chat` table
   - ✅ `chat_message` table

### ❌ Pendente de Implementação

1. **Text Search Configuration** `simple_unaccent`
   - ❌ Configuração NÃO criada
   - ❌ Generated column ainda usa `'portuguese'` ao invés de `'simple_unaccent'`

2. **Correção do Typo**
   - ⚠️ Campo `libray_id` precisa ser renomeado para `library_id`

3. **Atualização do Repository**
   - ❌ DocEmbeddingJdbcRepository.java ainda usa `to_tsquery`
   - ❌ Precisa migrar para `websearch_to_tsquery`
   - ❌ Precisa atualizar de `'portuguese'` para `'simple_unaccent'`

4. **Índice Duplicado**
   - ⚠️ `idx_embedding_texto` e `idx_text_search_tsv` parecem redundantes

---

## 🎯 Tarefas Prioritárias

### Prioridade 1: Correções Críticas na Estrutura

#### Tarefa 1.1: Corrigir Typo no Campo libray_id
**Arquivo**: Criar `007-fix-libray-id-typo.xml`

**Descrição**: O campo `libray_id` na tabela `doc_embedding` tem um typo. Deve ser `library_id`.

**Impacto**:
- 🔴 **CRÍTICO** - Afeta integridade referencial
- 🔴 **BLOCKER** - Todas as foreign keys e queries usam esse nome
- 🔴 **CÓDIGO** - Entity DocumentEmbedding.java precisa ser atualizado também

**Changeset**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                   http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.3.xsd">

    <changeSet id="007-001-rename-libray-id-to-library-id" author="jsimplerag">
        <sql>
            -- Renomear coluna com typo
            ALTER TABLE doc_embedding RENAME COLUMN libray_id TO library_id;
        </sql>
        <rollback>
            ALTER TABLE doc_embedding RENAME COLUMN library_id TO libray_id;
        </rollback>
    </changeSet>

</databaseChangeLog>
```

**Atualização Java Necessária**:
```java
// DocumentEmbedding.java - linha 51
// ANTES:
@Column(name = "libray_id")
private Integer libraryId;

// DEPOIS:
@Column(name = "library_id")
private Integer libraryId;
```

**Verificação**:
```sql
-- Verificar se rename foi bem-sucedido
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'doc_embedding'
AND column_name IN ('library_id', 'libray_id');

-- Deve retornar apenas 'library_id'
```

---

#### Tarefa 1.2: Remover Índice Duplicado
**Arquivo**: Atualizar `004-create-indexes.xml` ou criar `007-fix-duplicate-index.xml`

**Descrição**: Os índices `idx_text_search_tsv` e `idx_embedding_texto` são redundantes (ambos GIN em text_search_tsv).

**Changeset**:
```xml
<changeSet id="007-002-remove-duplicate-text-search-index" author="jsimplerag">
    <sql>
        -- Remover índice duplicado (idx_embedding_texto é duplicado de idx_text_search_tsv)
        DROP INDEX IF EXISTS idx_embedding_texto;
    </sql>
    <rollback>
        -- Recriar se necessário
        CREATE INDEX idx_embedding_texto ON doc_embedding USING gin(text_search_tsv);
    </rollback>
</changeSet>
```

**Justificativa**:
- Ambos indexam o mesmo campo (`text_search_tsv`) com o mesmo tipo (GIN)
- Índices duplicados desperdiçam espaço e degradam performance de INSERT/UPDATE
- Manter apenas `idx_text_search_tsv` (nome mais descritivo)

---

### Prioridade 2: Implementar Text Search Configuration

#### Tarefa 2.1: Criar Configuração simple_unaccent
**Arquivo**: Criar `008-create-text-search-config.xml`

**Descrição**: Criar configuração PostgreSQL `simple_unaccent` para busca textual sem stemming e sem acentos.

**Changeset**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                   http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.3.xsd">

    <!--
    NOTE: Text Search Configuration for Unaccented Simple Search

    Why 'simple_unaccent' instead of 'portuguese'?
    1. No stemming: Keeps words intact (important for legal/technical terms)
    2. Unaccent: Removes accents automatically (café = cafe)
    3. Case-insensitive: Built-in normalization
    4. Web-friendly: Works seamlessly with websearch_to_tsquery

    Example:
    - Input: "café açúcar"
    - Portuguese config: 'cafe' | 'acucar' (but with stemming: 'caf' | 'acucar')
    - Simple_unaccent: 'cafe' | 'acucar' (no stemming, perfect match)
    -->

    <changeSet id="008-001-create-simple-unaccent-config" author="jsimplerag">
        <preConditions onFail="MARK_RAN">
            <sqlCheck expectedResult="0">
                SELECT COUNT(*) FROM pg_ts_config WHERE cfgname = 'simple_unaccent'
            </sqlCheck>
        </preConditions>

        <sql>
            -- Criar configuração baseada em 'simple' (sem stemming)
            CREATE TEXT SEARCH CONFIGURATION simple_unaccent (COPY = simple);

            -- Adicionar unaccent à pipeline de processamento
            -- Aplica para: palavras ASCII, palavras com acentos, números, palavras hifenizadas
            ALTER TEXT SEARCH CONFIGURATION simple_unaccent
                ALTER MAPPING FOR asciiword, word, numword, hword, hword_part, hword_numpart
                WITH unaccent, simple;
        </sql>

        <rollback>
            DROP TEXT SEARCH CONFIGURATION IF EXISTS simple_unaccent;
        </rollback>
    </changeSet>

    <!-- Validação: testar se configuração funciona corretamente -->
    <changeSet id="008-002-validate-simple-unaccent-config" author="jsimplerag">
        <sql splitStatements="false">
            DO $$
            DECLARE
                test_result text;
            BEGIN
                -- Testar websearch_to_tsquery com a nova configuração
                SELECT websearch_to_tsquery('simple_unaccent', 'café açúcar "pão quente"')::text
                INTO test_result;

                -- Verificar se resultado contém tokens esperados
                IF test_result NOT LIKE '%cafe%' OR test_result NOT LIKE '%acucar%' THEN
                    RAISE EXCEPTION 'Text search configuration validation failed: %', test_result;
                END IF;

                RAISE NOTICE 'Text search configuration validated successfully: %', test_result;
            END $$;
        </sql>
        <rollback>
            -- No rollback needed for validation
        </rollback>
    </changeSet>

</databaseChangeLog>
```

**Teste Manual**:
```sql
-- Teste 1: Verificar se configuração foi criada
SELECT cfgname, cfgnamespace::regnamespace
FROM pg_ts_config
WHERE cfgname = 'simple_unaccent';

-- Teste 2: Testar remoção de acentos
SELECT to_tsvector('simple_unaccent', 'café açúcar pão');
-- Esperado: 'acucar':2 'cafe':1 'pao':3

-- Teste 3: Testar websearch_to_tsquery
SELECT websearch_to_tsquery('simple_unaccent', 'café açúcar "pão quente"');
-- Esperado: 'cafe' | 'acucar' | ( 'pao' <-> 'quente' )

-- Teste 4: Comparar com 'portuguese' (mostra diferença do stemming)
SELECT
    to_tsvector('portuguese', 'açúcar') as portuguese,
    to_tsvector('simple_unaccent', 'açúcar') as simple_unaccent;
-- Portuguese pode fazer stemming: 'acucar'
-- Simple_unaccent mantém palavra: 'acucar'
```

---

#### Tarefa 2.2: Atualizar Generated Column para usar simple_unaccent
**Arquivo**: Criar `009-update-text-search-tsv-config.xml`

**Descrição**: Alterar a coluna generated `text_search_tsv` para usar `'simple_unaccent'` ao invés de `'portuguese'`.

**⚠️ ATENÇÃO**: Esta operação requer recriar a generated column.

**Changeset**:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                   http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.3.xsd">

    <changeSet id="009-001-drop-old-text-search-tsv" author="jsimplerag">
        <sql>
            -- Remover coluna generated existente (usa 'portuguese')
            ALTER TABLE doc_embedding DROP COLUMN IF EXISTS text_search_tsv;
        </sql>
        <rollback>
            -- Recriar coluna antiga com 'portuguese'
            ALTER TABLE doc_embedding ADD COLUMN text_search_tsv tsvector
            GENERATED ALWAYS AS (
                setweight(to_tsvector('portuguese', COALESCE(metadados->>'nome', '')), 'A') ||
                setweight(to_tsvector('portuguese', COALESCE(metadados->>'capitulo', '')), 'A') ||
                setweight(to_tsvector('portuguese', COALESCE(metadados->>'descricao', '')), 'B') ||
                setweight(to_tsvector('portuguese', COALESCE(metadados->>'area_conhecimento', '')), 'C') ||
                setweight(to_tsvector('portuguese', COALESCE(metadados->>'palavras_chave', '')), 'C') ||
                setweight(to_tsvector('portuguese', COALESCE(metadados->>'autor', '')), 'D') ||
                setweight(to_tsvector('portuguese', COALESCE(metadados->>'metadados', '')), 'D') ||
                setweight(to_tsvector('portuguese', COALESCE(texto, '')), 'C')
            ) STORED;
        </rollback>
    </changeSet>

    <changeSet id="009-002-create-new-text-search-tsv" author="jsimplerag">
        <sql>
            -- Criar coluna generated com 'simple_unaccent'
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

                -- Outros metadados (peso D)
                setweight(to_tsvector('simple_unaccent', COALESCE(metadados->>'metadados', '')), 'D') ||

                -- Texto completo do chunk (peso C)
                setweight(to_tsvector('simple_unaccent', COALESCE(texto, '')), 'C')
            ) STORED;
        </sql>
        <rollback>
            ALTER TABLE doc_embedding DROP COLUMN IF EXISTS text_search_tsv;
        </rollback>
    </changeSet>

    <!-- Recriar índice GIN após atualizar a coluna -->
    <changeSet id="009-003-recreate-text-search-index" author="jsimplerag">
        <sql>
            -- Índice GIN para busca textual eficiente
            CREATE INDEX IF NOT EXISTS idx_text_search_tsv
            ON doc_embedding USING GIN(text_search_tsv);
        </sql>
        <rollback>
            DROP INDEX IF EXISTS idx_text_search_tsv;
        </rollback>
    </changeSet>

</databaseChangeLog>
```

**⚠️ Impacto**:
- A coluna será recalculada para todos os registros existentes
- Índice será reconstruído
- Pode levar tempo em tabelas grandes (avaliar fazer em janela de manutenção)

**Teste de Regressão**:
```sql
-- Antes da migração: contar registros
SELECT COUNT(*) as total_before FROM doc_embedding;

-- Após migração: verificar se nenhum registro foi perdido
SELECT COUNT(*) as total_after FROM doc_embedding;

-- Testar busca com nova configuração
SELECT id, texto, text_search_tsv
FROM doc_embedding
WHERE text_search_tsv @@ websearch_to_tsquery('simple_unaccent', 'café')
LIMIT 5;
```

---

### Prioridade 3: Atualizar Código Java

#### Tarefa 3.1: Atualizar Entity DocumentEmbedding.java
**Arquivo**: `src/main/java/bor/tools/simplerag/entity/DocumentEmbedding.java`

**Mudanças**:

1. **Corrigir typo no campo library_id** (linha ~51):
```java
// ANTES:
@Column(name = "libray_id")
private Integer libraryId;

// DEPOIS:
@Column(name = "library_id")
private Integer libraryId;
```

2. **Atualizar documentação do campo text_search_tsv**:
```java
/**
 * Full-text search vector with weighted metadata, automatically generated by PostgreSQL.<br>
 * This is a GENERATED ALWAYS column that combines multiple metadata fields with different weights:<br>
 * <ul>
 *   <li>Weight A (highest): nome, capitulo</li>
 *   <li>Weight B: descricao</li>
 *   <li>Weight C: area_conhecimento, palavras_chave, texto</li>
 *   <li>Weight D (lowest): autor, metadados</li>
 * </ul>
 *
 * <p>Uses 'simple_unaccent' text search configuration for accent-insensitive search without stemming.</p>
 *
 * <h2>Usage in queries:</h2>
 * <pre>{@code
 * -- Web-friendly query syntax (use with websearch_to_tsquery)
 * SELECT * FROM doc_embedding
 * WHERE text_search_tsv @@ websearch_to_tsquery('simple_unaccent', 'café "pão quente"')
 * ORDER BY ts_rank_cd(text_search_tsv, websearch_to_tsquery('simple_unaccent', 'café "pão quente"')) DESC;
 * }</pre>
 *
 * <h2>Query Examples:</h2>
 * <ul>
 *   <li><code>café açúcar</code> → OR search (finds either word)</li>
 *   <li><code>"pão quente"</code> → Phrase search (finds exact phrase)</li>
 *   <li><code>café "pão quente"</code> → Mixed search (café OR phrase)</li>
 *   <li><code>-açúcar</code> → Negation (excludes word)</li>
 * </ul>
 *
 * <h2>Notes:</h2>
 * <ul>
 *   <li>This field is managed by the database and should not be set manually</li>
 *   <li>Automatically updates when texto or metadados change</li>
 *   <li>Accent-insensitive: café = cafe, açúcar = acucar</li>
 *   <li>No stemming: preserves original word forms</li>
 *   <li>For full-text search, use native queries via {@link DocEmbeddingJdbcRepository}</li>
 *   <li>Do not include in toString, equals, hashCode, or JSON serialization</li>
 * </ul>
 *
 * @see DocEmbeddingJdbcRepository#pesquisaTextual
 * @see DocEmbeddingJdbcRepository#pesquisaHibrida
 */
@Column(name = "text_search_tsv", columnDefinition = "tsvector", insertable = false, updatable = false)
@ToString.Exclude
@JsonIgnore
private Object textSearchTsv;
```

3. **Considerar remover campo obsoleto** (se existir):
```java
// DEPRECADO - será removido em versão futura
@Deprecated(since = "0.0.2", forRemoval = true)
@Column(name = "texto_indexado", columnDefinition = "tsvector", insertable = false, updatable = false)
@ToString.Exclude
private String textoIndexado;
```

---

#### Tarefa 3.2: Atualizar Repository DocEmbeddingJdbcRepository.java
**Arquivo**: `src/main/java/bor/tools/simplerag/repository/DocEmbeddingJdbcRepository.java`

**Mudanças Principais**:

1. **Substituir `to_tsquery` por `websearch_to_tsquery`**
2. **Substituir `'portuguese'` por `'simple_unaccent'`**
3. **Atualizar comentários e documentação**

**Método pesquisaHibrida** (exemplo):
```java
public List<DocumentEmbedding> pesquisaHibrida(
        @NonNull float[] embedding,
        @NonNull String queryTexto, // Query do usuário em linguagem natural
        @NonNull Integer[] bibliotecaIds,
        @NonNull Float pesoSemantico,
        @NonNull Float pesoTextual,
        @NonNull Integer k) {

    if (k == null) k = k_pesquisa;

    String libIds = Arrays.stream(bibliotecaIds)
                         .map(String::valueOf)
                         .collect(Collectors.joining(", "));

    // NOTA: websearch_to_tsquery aceita sintaxe web-friendly:
    // - "café açúcar" → busca OR (cafe | acucar)
    // - "\"pão quente\"" → busca frase exata (pao <-> quente)
    // - "café -açúcar" → café sem açúcar (cafe & !acucar)
    String sql = """
        WITH semantic_search AS (
            SELECT id,
                   1.0 / (? + RANK() OVER (ORDER BY embedding_vector <=> ? ASC)) AS score_semantic,
                   RANK() OVER (ORDER BY embedding_vector <=> ? ASC) AS rank_semantic
            FROM doc_embedding
            WHERE library_id IN (%s)
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
            WHERE library_id IN (%s)
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
        WHERE d.library_id IN (%s)
        AND (s.id IS NOT NULL OR t.id IS NOT NULL)
        ORDER BY score DESC
        LIMIT ?
        """.formatted(libIds, libIds, libIds);

    Object[] params = new Object[] {
        k, // normalization factor semantic
        new PGvector(embedding),
        new PGvector(embedding),
        k * 2, // expanded limit semantic
        k, // normalization factor text
        queryTexto, // ← Input direto do usuário (web-friendly)
        queryTexto,
        queryTexto,
        k * 2, // expanded limit text
        pesoSemantico,
        pesoTextual,
        k
    };

    return jdbcTemplate.query(sql, rowMapperWithScores, params);
}
```

**Método pesquisaTextual**:
```java
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
        WHERE library_id IN (%s)
        AND text_search_tsv @@ websearch_to_tsquery('simple_unaccent', ?)
        ORDER BY score DESC
        LIMIT ?
        """.formatted(libIds);

    Object[] params = new Object[] { queryTexto, queryTexto, queryTexto, k };

    return jdbcTemplate.query(sql, rowMapperWithScores, params);
}
```

**Adicionar método auxiliar de validação**:
```java
/**
 * Valida se a query de texto é válida para websearch_to_tsquery.
 *
 * @param queryTexto Query do usuário
 * @return true se válida, false caso contrário
 */
public boolean isValidTextQuery(String queryTexto) {
    if (queryTexto == null || queryTexto.isBlank()) {
        return false;
    }

    try {
        // Testar se websearch_to_tsquery aceita a query
        jdbcTemplate.queryForObject(
            "SELECT websearch_to_tsquery('simple_unaccent', ?)::text",
            String.class,
            queryTexto
        );
        return true;
    } catch (Exception e) {
        log.warn("Invalid text query: {}", queryTexto, e);
        return false;
    }
}
```

---

#### Tarefa 3.3: Atualizar Controllers (se necessário)
**Arquivos**: SearchController.java, ChatController.java, etc.

**Mudanças**:
1. Remover pré-processamento manual de queries (se existir)
2. Passar query do usuário diretamente para o repository
3. Atualizar documentação da API
4. Adicionar exemplos de queries válidas

**Exemplo** (SearchController.java):
```java
/**
 * Pesquisa híbrida (semântica + textual).
 *
 * <p>A query de texto aceita sintaxe web-friendly:</p>
 * <ul>
 *   <li><code>café açúcar</code> - Busca OR (qualquer palavra)</li>
 *   <li><code>"pão quente"</code> - Frase exata</li>
 *   <li><code>café "pão quente"</code> - Combinado</li>
 *   <li><code>café -açúcar</code> - Negação</li>
 * </ul>
 *
 * <p>A busca é insensível a acentos e maiúsculas/minúsculas.</p>
 *
 * @param request SearchRequest contendo query de texto e bibliotecas
 * @return Lista de resultados ordenados por relevância
 */
@PostMapping("/pesquisa-hibrida")
public ResponseEntity<SearchResponse> pesquisaHibrida(@RequestBody SearchRequest request) {
    // Não fazer pré-processamento - passar query diretamente
    String queryTexto = request.getQuery();

    // Validação básica
    if (queryTexto == null || queryTexto.isBlank()) {
        return ResponseEntity.badRequest().build();
    }

    // Gerar embedding da query
    float[] embedding = embeddingService.generateEmbedding(queryTexto);

    // Executar busca híbrida
    List<DocumentEmbedding> results = repository.pesquisaHibrida(
        embedding,
        queryTexto, // ← Query original do usuário
        request.getBibliotecaIds(),
        request.getPesoSemantico(),
        request.getPesoTextual(),
        request.getK()
    );

    return ResponseEntity.ok(SearchResponse.from(results));
}
```

---

### Prioridade 4: Testes

#### Tarefa 4.1: Testes Unitários
**Arquivo**: Criar `DocEmbeddingJdbcRepositoryTest.java` (ou atualizar existente)

```java
@SpringBootTest
@Transactional
class DocEmbeddingJdbcRepositoryTest {

    @Autowired
    private DocEmbeddingJdbcRepository repository;

    @Autowired
    private LibraryRepository libraryRepository;

    @Autowired
    private DocumentoRepository documentoRepository;

    @Test
    void testWebsearchToTsquery_simpleWord() {
        // Arrange
        setupTestData();

        // Act
        List<DocumentEmbedding> results = repository.pesquisaTextual(
            "café",  // Query simples
            new Integer[]{testLibraryId},
            10
        );

        // Assert
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(r ->
            r.getTexto().toLowerCase().contains("cafe") ||
            r.getTexto().toLowerCase().contains("café")
        ));
    }

    @Test
    void testWebsearchToTsquery_orSearch() {
        setupTestData();

        // Query com OR implícito
        List<DocumentEmbedding> results = repository.pesquisaTextual(
            "café açúcar",  // Busca OR
            new Integer[]{testLibraryId},
            10
        );

        assertFalse(results.isEmpty());
        // Deve encontrar documentos com "café" OU "açúcar"
    }

    @Test
    void testWebsearchToTsquery_phraseSearch() {
        setupTestData();

        // Query com frase exata
        List<DocumentEmbedding> results = repository.pesquisaTextual(
            "\"pão quente\"",  // Frase exata
            new Integer[]{testLibraryId},
            10
        );

        // Deve encontrar apenas documentos com "pão quente" adjacentes
        assertFalse(results.isEmpty());
    }

    @Test
    void testWebsearchToTsquery_accentInsensitive() {
        setupTestData();

        // Testar insensibilidade a acentos
        List<DocumentEmbedding> results1 = repository.pesquisaTextual(
            "café",
            new Integer[]{testLibraryId},
            10
        );

        List<DocumentEmbedding> results2 = repository.pesquisaTextual(
            "cafe",
            new Integer[]{testLibraryId},
            10
        );

        // Deve retornar os mesmos resultados
        assertEquals(results1.size(), results2.size());
    }

    @Test
    void testWebsearchToTsquery_negation() {
        setupTestData();

        // Query com negação
        List<DocumentEmbedding> results = repository.pesquisaTextual(
            "café -açúcar",  // café sem açúcar
            new Integer[]{testLibraryId},
            10
        );

        assertFalse(results.isEmpty());
        // Nenhum resultado deve conter "açúcar"
        assertTrue(results.stream().noneMatch(r ->
            r.getTexto().toLowerCase().contains("açúcar") ||
            r.getTexto().toLowerCase().contains("acucar")
        ));
    }

    @Test
    void testHybridSearch_withWeights() {
        setupTestData();
        float[] embedding = generateTestEmbedding();

        // Busca híbrida com pesos diferentes
        List<DocumentEmbedding> results = repository.pesquisaHibrida(
            embedding,
            "café",
            new Integer[]{testLibraryId},
            0.6f,  // 60% semântico
            0.4f,  // 40% textual
            10
        );

        assertFalse(results.isEmpty());
        // Verificar se score foi calculado corretamente
        results.forEach(r -> {
            float expectedScore = r.getScoreSemantic() * 0.6f + r.getScoreText() * 0.4f;
            assertEquals(expectedScore, r.getScore(), 0.001);
        });
    }

    private void setupTestData() {
        // Criar biblioteca de teste
        Library library = new Library();
        library.setNome("Test Library");
        library.setAreaConhecimento("Test Area");
        library.setTipo(TipoBiblioteca.PESSOAL);
        library = libraryRepository.save(library);
        testLibraryId = library.getId();

        // Criar documentos de teste com variações
        createTestDocument("Receita de café com açúcar");
        createTestDocument("Como fazer pão quente");
        createTestDocument("Café sem açúcar é saudável");
        createTestDocument("Padaria vende pão quente toda manhã");
    }

    private void createTestDocument(String texto) {
        // Implementar criação de documento de teste
        // com embeddings e metadados
    }

    private float[] generateTestEmbedding() {
        // Gerar embedding de teste (768 dimensões)
        return new float[768]; // Implementar geração realista
    }
}
```

---

#### Tarefa 4.2: Testes de Integração SQL
**Arquivo**: Criar `src/test/resources/sql/test_text_search.sql`

```sql
-- ========================================
-- Testes de Integração: Text Search
-- ========================================

-- Teste 1: Verificar extensões instaladas
SELECT
    extname,
    extversion,
    extnamespace::regnamespace as namespace
FROM pg_extension
WHERE extname IN ('vector', 'pg_trgm', 'unaccent')
ORDER BY extname;

-- Esperado: 3 linhas (vector, pg_trgm, unaccent)

-- Teste 2: Verificar configuração simple_unaccent
SELECT cfgname, cfgnamespace::regnamespace
FROM pg_ts_config
WHERE cfgname = 'simple_unaccent';

-- Esperado: 1 linha

-- Teste 3: Testar remoção de acentos
SELECT to_tsvector('simple_unaccent', 'café açúcar pão');

-- Esperado: 'acucar':2 'cafe':1 'pao':3

-- Teste 4: Testar websearch_to_tsquery com OR
SELECT websearch_to_tsquery('simple_unaccent', 'café açúcar');

-- Esperado: 'cafe' | 'acucar'

-- Teste 5: Testar websearch_to_tsquery com frase
SELECT websearch_to_tsquery('simple_unaccent', '"pão quente"');

-- Esperado: 'pao' <-> 'quente'

-- Teste 6: Testar websearch_to_tsquery com negação
SELECT websearch_to_tsquery('simple_unaccent', 'café -açúcar');

-- Esperado: 'cafe' & !'acucar'

-- Teste 7: Verificar estrutura da tabela doc_embedding
SELECT
    column_name,
    data_type,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_name = 'doc_embedding'
AND column_name IN ('library_id', 'texto', 'embedding_vector', 'text_search_tsv')
ORDER BY ordinal_position;

-- Esperado:
-- library_id | bigint | NO | NULL
-- texto | text | YES | NULL
-- embedding_vector | USER-DEFINED (vector) | YES | NULL
-- text_search_tsv | USER-DEFINED (tsvector) | YES | GENERATED ALWAYS

-- Teste 8: Verificar índices
SELECT
    indexname,
    indexdef
FROM pg_indexes
WHERE tablename = 'doc_embedding'
AND indexname LIKE '%text_search%' OR indexname LIKE '%vector%'
ORDER BY indexname;

-- Esperado:
-- idx_embedding_vector | CREATE INDEX ... USING ivfflat (embedding_vector(768) ...)
-- idx_text_search_tsv | CREATE INDEX ... USING gin (text_search_tsv)

-- Teste 9: Inserir documento de teste e verificar generated column
BEGIN;

-- Inserir na biblioteca (se não existir)
INSERT INTO library (uuid, nome, area_conhecimento, tipo)
VALUES (gen_random_uuid(), 'Test Library', 'Test Area', 'pessoal')
ON CONFLICT (uuid) DO NOTHING;

-- Obter ID da biblioteca
DO $$
DECLARE
    lib_id bigint;
    doc_id bigint;
BEGIN
    SELECT id INTO lib_id FROM library WHERE nome = 'Test Library' LIMIT 1;

    -- Inserir documento
    INSERT INTO documento (biblioteca_id, titulo, conteudo_markdown, data_publicacao)
    VALUES (lib_id, 'Teste', 'Conteúdo teste', CURRENT_DATE)
    RETURNING id INTO doc_id;

    -- Inserir embedding com texto
    INSERT INTO doc_embedding (
        library_id,
        documento_id,
        tipo_embedding,
        texto,
        metadados
    ) VALUES (
        lib_id,
        doc_id,
        'trecho',
        'Receita de pão quente com manteiga e café fresquinho',
        '{"nome": "Livro de Receitas", "capitulo": "Cafés da Manhã", "descricao": "Receitas matinais"}'::jsonb
    );

    -- Verificar se text_search_tsv foi gerado
    IF NOT EXISTS (
        SELECT 1 FROM doc_embedding
        WHERE texto LIKE '%pão quente%'
        AND text_search_tsv @@ websearch_to_tsquery('simple_unaccent', 'pão')
    ) THEN
        RAISE EXCEPTION 'Generated column text_search_tsv not working';
    END IF;

    RAISE NOTICE 'Generated column working correctly';
END $$;

ROLLBACK;

-- Teste 10: Performance - EXPLAIN ANALYZE de busca textual
EXPLAIN ANALYZE
SELECT id, texto, ts_rank_cd(text_search_tsv, websearch_to_tsquery('simple_unaccent', 'café')) as rank
FROM doc_embedding
WHERE text_search_tsv @@ websearch_to_tsquery('simple_unaccent', 'café')
ORDER BY rank DESC
LIMIT 10;

-- Verificar se usa Bitmap Index Scan em idx_text_search_tsv
```

---

#### Tarefa 4.3: Testes de Carga (Opcional)
**Arquivo**: Criar `load_test_text_search.sql`

```sql
-- ========================================
-- Teste de Carga: Text Search
-- ========================================

-- Criar dados de teste em lote
DO $$
DECLARE
    lib_id bigint;
    doc_id bigint;
    i integer;
BEGIN
    -- Obter ID da biblioteca de teste
    SELECT id INTO lib_id FROM library WHERE nome = 'Test Library' LIMIT 1;

    IF lib_id IS NULL THEN
        RAISE EXCEPTION 'Test library not found';
    END IF;

    -- Criar documento de teste
    INSERT INTO documento (biblioteca_id, titulo, conteudo_markdown, data_publicacao)
    VALUES (lib_id, 'Load Test Document', 'Conteúdo para teste de carga', CURRENT_DATE)
    RETURNING id INTO doc_id;

    -- Inserir 10.000 embeddings com texto variado
    FOR i IN 1..10000 LOOP
        INSERT INTO doc_embedding (
            library_id,
            documento_id,
            tipo_embedding,
            texto,
            embedding_vector,
            metadados
        ) VALUES (
            lib_id,
            doc_id,
            'trecho',
            'Texto de teste número ' || i || ' com café, pão, açúcar e manteiga',
            ARRAY(SELECT random() FROM generate_series(1, 768))::real[], -- Embedding aleatório
            format('{"nome": "Doc %s", "capitulo": "Cap %s"}', i, i % 100)::jsonb
        );

        -- Commit a cada 1000 registros
        IF i % 1000 = 0 THEN
            RAISE NOTICE 'Inserted % records', i;
        END IF;
    END LOOP;

    RAISE NOTICE 'Load test data created: 10000 embeddings';
END $$;

-- Teste de performance: 100 buscas textuais
DO $$
DECLARE
    start_time timestamp;
    end_time timestamp;
    duration interval;
    i integer;
    queries text[] := ARRAY[
        'café',
        'pão quente',
        '"café açúcar"',
        'manteiga -açúcar',
        'pão café açúcar'
    ];
    result_count integer;
BEGIN
    start_time := clock_timestamp();

    FOR i IN 1..20 LOOP
        -- Executar cada query 20 vezes (100 queries no total)
        SELECT COUNT(*) INTO result_count
        FROM doc_embedding
        WHERE text_search_tsv @@ websearch_to_tsquery('simple_unaccent', queries[i % 5 + 1])
        LIMIT 10;
    END LOOP;

    end_time := clock_timestamp();
    duration := end_time - start_time;

    RAISE NOTICE 'Performance test completed:';
    RAISE NOTICE '  Total time: %', duration;
    RAISE NOTICE '  Average per query: % ms', EXTRACT(EPOCH FROM duration) * 1000 / 100;

    -- Verificar se atende SLA (< 50ms por query)
    IF EXTRACT(EPOCH FROM duration) * 1000 / 100 > 50 THEN
        RAISE WARNING 'Performance SLA not met: average > 50ms';
    ELSE
        RAISE NOTICE 'Performance SLA met: average < 50ms';
    END IF;
END $$;
```

---

### Prioridade 5: Documentação

#### Tarefa 5.1: Atualizar README.md
Adicionar seção sobre busca textual:

```markdown
## Busca Textual

### Sintaxe Web-Friendly

JSimpleRag usa `websearch_to_tsquery` do PostgreSQL, permitindo queries em linguagem natural:

| Query | Significado | Exemplo de Resultado |
|-------|-------------|---------------------|
| `café açúcar` | Busca OR (qualquer palavra) | Documentos com "café" OU "açúcar" |
| `"pão quente"` | Frase exata | Documentos com "pão quente" adjacentes |
| `café "pão quente"` | Combinado | Documentos com "café" OU frase "pão quente" |
| `café -açúcar` | Negação | Documentos com "café" SEM "açúcar" |

### Características

- ✅ **Insensível a acentos**: `café` = `cafe`, `açúcar` = `acucar`
- ✅ **Insensível a maiúsculas/minúsculas**: `CAFÉ` = `café` = `Café`
- ✅ **Sem stemming**: Preserva palavras inteiras (importante para termos técnicos/legais)
- ✅ **Weighted search**: Metadados têm pesos diferentes (título > descrição > texto)
- ✅ **Generated column**: Atualização automática ao modificar texto ou metadados

### Exemplo de Uso (API)

```bash
curl -X POST http://localhost:8080/api/v1/pesquisa \
  -H "Content-Type: application/json" \
  -d '{
    "query": "café açúcar",
    "bibliotecaIds": [1, 2],
    "k": 10
  }'
```

### Exemplo de Uso (SQL Direto)

```sql
-- Busca simples
SELECT texto, ts_rank_cd(text_search_tsv, websearch_to_tsquery('simple_unaccent', 'café')) as rank
FROM doc_embedding
WHERE text_search_tsv @@ websearch_to_tsquery('simple_unaccent', 'café')
ORDER BY rank DESC
LIMIT 10;

-- Busca com frase
SELECT texto
FROM doc_embedding
WHERE text_search_tsv @@ websearch_to_tsquery('simple_unaccent', '"pão quente"')
LIMIT 10;
```
```

---

#### Tarefa 5.2: Atualizar db_search_functions_study.md
Adicionar seção "Status de Implementação":

```markdown
## 📊 Status de Implementação (Atualizado 2025-10-11)

### ✅ Implementado

- [x] Extensão unaccent (001-create-extensions.xml)
- [x] Generated column text_search_tsv (003-create-tables.xml)
- [x] Índice GIN para text_search_tsv (004-create-indexes.xml)
- [x] Remoção de triggers redundantes (005-create-triggers.xml)
- [x] Text search configuration simple_unaccent (008-create-text-search-config.xml)
- [x] Atualização de text_search_tsv para usar simple_unaccent (009-update-text-search-tsv-config.xml)
- [x] Correção de typo libray_id → library_id (007-fix-libray-id-typo.xml)
- [x] Remoção de índice duplicado (007-fix-duplicate-index.xml)

### ✅ Código Java Atualizado

- [x] DocumentEmbedding.java - Corrigido typo library_id
- [x] DocumentEmbedding.java - Atualizada documentação text_search_tsv
- [x] DocEmbeddingJdbcRepository.java - Migrado para websearch_to_tsquery
- [x] DocEmbeddingJdbcRepository.java - Atualizado para simple_unaccent

### ✅ Testes Criados

- [x] Testes unitários (DocEmbeddingJdbcRepositoryTest.java)
- [x] Testes de integração SQL (test_text_search.sql)
- [x] Testes de carga (load_test_text_search.sql)

### 📝 Pendente

- [ ] Atualizar controllers para remover pré-processamento de queries
- [ ] Adicionar validação de queries na camada de serviço
- [ ] Criar documentação de API (Swagger/OpenAPI)
- [ ] Testes de regressão em staging
- [ ] Deploy em produção
```

---

## 📅 Cronograma de Execução

### Fase 1: Correções Estruturais (Dia 1)
- [x] ~~Tarefa 1.1: Corrigir typo libray_id (007-fix-libray-id-typo.xml)~~
- [x] ~~Tarefa 1.2: Remover índice duplicado (007-fix-duplicate-index.xml)~~
- [x] ~~Tarefa 3.1: Atualizar DocumentEmbedding.java (corrigir typo)~~

**Duração estimada**: 2 horas
**Risco**: Baixo
**Blocker**: Nenhum

### Fase 2: Text Search Configuration (Dia 1-2)
- [x] ~~Tarefa 2.1: Criar configuração simple_unaccent (008-create-text-search-config.xml)~~
- [x] ~~Tarefa 2.2: Atualizar generated column (009-update-text-search-tsv-config.xml)~~

**Duração estimada**: 4 horas
**Risco**: Médio (requer rebuild de índice)
**Blocker**: Nenhum (mas avaliar fazer em janela de manutenção se banco grande)

### Fase 3: Atualização do Código Java (Dia 2-3)
- [ ] Tarefa 3.1: Atualizar DocumentEmbedding.java (documentação)
- [ ] Tarefa 3.2: Atualizar DocEmbeddingJdbcRepository.java
- [ ] Tarefa 3.3: Atualizar Controllers

**Duração estimada**: 6 horas
**Risco**: Baixo
**Blocker**: Fase 2 completa

### Fase 4: Testes (Dia 3-4)
- [ ] Tarefa 4.1: Testes unitários
- [ ] Tarefa 4.2: Testes de integração SQL
- [ ] Tarefa 4.3: Testes de carga (opcional)

**Duração estimada**: 8 horas
**Risco**: Baixo
**Blocker**: Fase 3 completa

### Fase 5: Documentação (Dia 4)
- [ ] Tarefa 5.1: Atualizar README.md
- [ ] Tarefa 5.2: Atualizar db_search_functions_study.md
- [ ] Criar documentação de API

**Duração estimada**: 3 horas
**Risco**: Baixo
**Blocker**: Nenhum (pode ser paralelo)

---

## 🚨 Riscos e Mitigações

### Risco 1: Perda de Performance ao Recriar Índice
**Probabilidade**: Média
**Impacto**: Alto

**Mitigação**:
1. Executar em janela de manutenção
2. Criar índice com CONCURRENTLY (se possível)
3. Monitorar tamanho da tabela antes da migração
4. Ter plano de rollback pronto

**Comando para estimar tempo**:
```sql
SELECT
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size,
    n_live_tup as estimated_rows
FROM pg_stat_user_tables
WHERE tablename = 'doc_embedding';
```

### Risco 2: Quebra de Compatibilidade com Queries Existentes
**Probabilidade**: Alta
**Impacto**: Médio

**Mitigação**:
1. Manter suporte temporário para sintaxe antiga (to_tsquery)
2. Adicionar logs de depreciação
3. Comunicar mudança para todos os consumers da API
4. Criar período de transição com ambas sintaxes funcionando

### Risco 3: Diferenças de Resultado entre 'portuguese' e 'simple_unaccent'
**Probabilidade**: Média
**Impacto**: Médio

**Mitigação**:
1. Executar testes A/B comparando resultados
2. Documentar diferenças esperadas
3. Permitir configuração por biblioteca (usar 'portuguese' ou 'simple_unaccent')
4. Ter dados de baseline antes da migração

---

## ✅ Checklist de Aprovação

Antes de considerar a implementação completa, verificar:

### Database
- [ ] Extensão unaccent instalada e funcionando
- [ ] Configuração simple_unaccent criada
- [ ] Generated column text_search_tsv atualizada
- [ ] Índice GIN recriado
- [ ] Campo libray_id renomeado para library_id
- [ ] Índice duplicado removido
- [ ] Testes SQL passando

### Código Java
- [ ] Entity DocumentEmbedding atualizada
- [ ] Repository usando websearch_to_tsquery
- [ ] Repository usando simple_unaccent
- [ ] Controllers atualizados
- [ ] Testes unitários passando
- [ ] Testes de integração passando

### Documentação
- [ ] README.md atualizado
- [ ] db_search_functions_study.md atualizado
- [ ] API documentation atualizada
- [ ] CHANGELOG.md atualizado

### Qualidade
- [ ] Code review aprovado
- [ ] Testes de performance aceitáveis (< 50ms/query)
- [ ] Sem regressões em funcionalidades existentes
- [ ] Logs de migração revisados
- [ ] Plano de rollback testado

---

## 📞 Contatos e Responsabilidades

| Responsabilidade | Pessoa | Contato |
|-----------------|--------|---------|
| Implementação Liquibase | [Nome] | [Email] |
| Atualização Código Java | [Nome] | [Email] |
| Testes e QA | [Nome] | [Email] |
| Documentação | [Nome] | [Email] |
| Deploy Produção | [Nome] | [Email] |
| DBA / Review SQL | [Nome] | [Email] |

---

## 📚 Referências

1. [PostgreSQL Full Text Search Documentation](https://www.postgresql.org/docs/current/textsearch.html)
2. [websearch_to_tsquery Documentation](https://www.postgresql.org/docs/current/textsearch-controls.html#TEXTSEARCH-PARSING-QUERIES)
3. [PGVector Extension](https://github.com/pgvector/pgvector)
4. [Unaccent Extension](https://www.postgresql.org/docs/current/unaccent.html)
5. [Liquibase Best Practices](https://www.liquibase.org/get-started/best-practices)
6. db_search_functions_study.md (este repositório)
7. CLAUDE.md (instruções do projeto)

---

**Documento preparado por**: Claude Code
**Data**: 2025-10-11
**Status**: ✅ Pronto para execução
**Versão**: 1.0
