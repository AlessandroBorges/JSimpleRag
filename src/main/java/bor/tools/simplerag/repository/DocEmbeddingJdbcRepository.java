
package bor.tools.simplerag.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.pgvector.PGvector;

import bor.tools.simplerag.entity.DocEmbedding;
import bor.tools.simplerag.entity.MetaBiblioteca;
import bor.tools.simplerag.entity.enums.TipoEmbedding;
import bor.tools.simplerag.util.VectorUtil;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * Repositório JDBC para DocEmbedding adaptado para JSimpleRag.
 *
 * Baseado na implementação de referência com adaptações para a arquitetura hierárquica
 * do JSimpleRag (Biblioteca → Documento → Capitulo → DocEmbedding).
 *
 * Campos de interesse para read/write:
 * - id
 * - biblioteca_id
 * - documento_id
 * - capitulo_id (nullable)
 * - tipo_embedding
 * - trecho_texto
 * - ordem_cap (nullable)
 * - embedding_vector
 * - metadados
 * - created_at
 *
 * Campo read-only (gerado por trigger):
 * - texto_indexado (tsvector)
 */
@Repository
@SuppressWarnings("null")
public class DocEmbeddingJdbcRepository {

    private static final String TAG_EXCLUSAO = " & !";
    private static final String TAG_HOLD = " <#-#> ";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private boolean isInitialized = false;

    /**
     * Cache de configurações de embedding por biblioteca
     */
    private Map<Integer, MetaBiblioteca> mapBibliotecaConfig = new HashMap<>();

    @Getter
    private LocalTime lastUpdate = LocalTime.now().minusHours(10);

    /** Tempo para expirar cache local */
    @Getter
    @Setter
    private int expireTimeMin = 30; // em minutos

    /** Número padrão de resultados para pesquisa semântica/textual */
    @Getter
    @Setter
    private int k_pesquisa = 10;

    /**
     * Mapeamento de biblioteca_id para dimensão do vetor de embeddings
     */
    private Map<Integer, Integer> mapBibliotecaId2VecLen = new HashMap<>();

    /**
     * RowMapper para DocEmbedding
     */
    private RowMapper<DocEmbedding> rowMapper = (rs, rowNum) -> {
        DocEmbedding doc = DocEmbedding.builder()
            .id(rs.getInt("id"))
            .bibliotecaId(rs.getInt("biblioteca_id"))
            .documentoId(rs.getInt("documento_id"))
            .capituloId(rs.getObject("capitulo_id", Integer.class))
            .tipoEmbedding(TipoEmbedding.fromDbValue(rs.getString("tipo_embedding")))
            .trechoTexto(rs.getString("trecho_texto"))
            .ordemCap(rs.getObject("ordem_cap", Integer.class))
            .textoIndexado(rs.getString("texto_indexado"))
            .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
            .build();

        // Parse metadados JSON
        String metadataJson = rs.getString("metadados");
        if (metadataJson != null) {
            // Aqui poderia usar Jackson para parse do JSON
            // Por simplicidade, criamos um mapa vazio
            doc.setMetadados(new HashMap<>());
        }

        // Processa embedding vector
        Object pg = rs.getObject("embedding_vector");
        MetaBiblioteca config = getBibliotecaConfig(doc.getBibliotecaId());
        if (config != null && config.getEmbeddingDimension() != null) {
            Integer vecLength = config.getEmbeddingDimension();
            pg = fixEmbeddingLength(pg, vecLength);
        }

        if (pg instanceof PGvector pgVector) {
            doc.setEmbeddingVector(pgVector.toArray());
        } else if (pg instanceof float[] floatArray) {
            doc.setEmbeddingVector(floatArray);
        }

        return doc;
    };

    /**
     * RowMapper para DocEmbedding com scores de pesquisa
     */
    private RowMapper<DocEmbedding> rowMapperWithScores = (rs, rowNum) -> {
        DocEmbedding doc = rowMapper.mapRow(rs, rowNum);

        // Adiciona scores aos metadados
        Map<String, Object> metadados = doc.getMetadados();
        if (metadados == null) {
            metadados = new HashMap<>();
            doc.setMetadados(metadados);
        }

        try {
            metadados.put("score_semantic", rs.getFloat("score_semantic"));
            metadados.put("score_text", rs.getFloat("score_text"));
            metadados.put("score", rs.getFloat("score"));
        } catch (SQLException e) {
            // Scores podem não estar presentes em todas as consultas
        }

        return doc;
    };

    /**
     * Inicializa o banco de dados com as extensões PGVector
     */
    @PostConstruct
    public void doOnce() {
        if (isInitialized)
            return;
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");

            // Registra o tipo de vetor no banco de dados
            java.sql.Connection conn = jdbcTemplate.getDataSource().getConnection();
            PGvector.registerTypes(conn);
            conn.close();

            updateMapBibliotecaId2VecLen();
        } catch (Exception e) {
            System.out.println("Error initializing the database with PGVector extensions");
            e.printStackTrace();
        }
        isInitialized = true;
    }

    /**
     * Atualiza o mapeamento de biblioteca_id para dimensão do vetor
     */
    private void updateMapBibliotecaId2VecLen() throws SQLException {
        java.sql.Connection conn = jdbcTemplate.getDataSource().getConnection();

        String sql = "SELECT id, metadados FROM biblioteca";
        PreparedStatement ps = conn.prepareStatement(sql);
        ResultSet rs = ps.executeQuery();

        synchronized (mapBibliotecaId2VecLen) {
            mapBibliotecaId2VecLen.clear();
            while (rs.next()) {
                int id = rs.getInt("id");
                String metadataJson = rs.getString("metadados");

                // Parse básico do JSON para extrair embedding_dimension
                // Em uma implementação real, usaria Jackson
                if (metadataJson != null && metadataJson.contains("embedding_dimension")) {
                    // Valor padrão se não conseguir fazer parse
                    mapBibliotecaId2VecLen.put(id, 1536);
                } else {
                    mapBibliotecaId2VecLen.put(id, 1536); // Valor padrão
                }
            }
            rs.close();
        }

        conn.close();
    }

    /**
     * Recupera configuração de embedding de uma biblioteca
     */
    private MetaBiblioteca getBibliotecaConfig(Integer bibliotecaId) throws SQLException {
        if (bibliotecaId == null) {
            return null;
        }

        boolean expired = lastUpdate.plusMinutes(expireTimeMin).isBefore(LocalTime.now());

        synchronized (mapBibliotecaConfig) {
            if (mapBibliotecaConfig.isEmpty() || expired) {
                String sql = "SELECT id, metadados FROM biblioteca";
                List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

                mapBibliotecaConfig.clear();
                for (Map<String, Object> row : results) {
                    Integer id = (Integer) row.get("id");
                    String metadataJson = (String) row.get("metadados");

                    MetaBiblioteca meta = new MetaBiblioteca();
                    if (metadataJson != null) {
                        // Parse básico - em implementação real usaria Jackson
                        meta.setLanguage("pt");
                        meta.setEmbeddingModel("text-embedding-ada-002");
                        meta.setEmbeddingDimension(1536);
                    }
                    mapBibliotecaConfig.put(id, meta);
                }
                lastUpdate = LocalTime.now();
            }
        }

        updateMapBibliotecaId2VecLen();
        return mapBibliotecaConfig.get(bibliotecaId);
    }

    /**
     * Corrige o tamanho do vetor de embedding
     */
    private Object fixEmbeddingLength(Object embedding, Integer vecLength) {
        if (embedding == null || vecLength == null || vecLength <= 1) {
            return embedding;
        }

        if (embedding instanceof PGvector pgVector) {
            float[] vec = pgVector.toArray();
            int len = vec.length;

            if (len != vecLength) {
                float[] values = new float[vecLength];
                System.arraycopy(pgVector.toArray(), 0, values, 0, Math.min(len, vecLength));
                // Normalizar o vetor
                values = VectorUtil.normalize(values);
                embedding = new PGvector(values);
            }
        } else if (embedding instanceof float[] vec) {
            int len = vec.length;
            if (len != vecLength) {
                float[] values = new float[vecLength];
                System.arraycopy(vec, 0, values, 0, Math.min(len, vecLength));
                // Normalizar o vetor
                values = VectorUtil.normalize(values);
                embedding = values;
            }
        }
        return embedding;
    }

    // ======== MÉTODOS CRUD BÁSICOS ========

    /**
     * Retorna todos os registros da tabela doc_embedding
     */
    public List<DocEmbedding> findAll() {
        return jdbcTemplate.query("SELECT * FROM doc_embedding ORDER BY id", rowMapper);
    }

    /**
     * Retorna um registro da tabela doc_embedding pelo ID
     */
    public Optional<DocEmbedding> findById(@NonNull Integer id)
            throws DataAccessException, SQLException {
        var obj = jdbcTemplate.queryForObject("SELECT * FROM doc_embedding WHERE id = ?",
                                            rowMapper,
                                            new Object[]{id});
        return Optional.ofNullable(obj);
    }

    /**
     * Busca embeddings por documento
     */
    public List<DocEmbedding> findByDocumentoId(Integer documentoId)
            throws DataAccessException, SQLException {
        return jdbcTemplate.query("SELECT * FROM doc_embedding WHERE documento_id = ? ORDER BY ordem_cap",
                                  rowMapper,
                                  new Object[]{documentoId});
    }

    /**
     * Busca embeddings por biblioteca
     */
    public List<DocEmbedding> findByBibliotecaId(Integer bibliotecaId)
            throws DataAccessException, SQLException {
        return jdbcTemplate.query("SELECT * FROM doc_embedding WHERE biblioteca_id = ? ORDER BY documento_id, ordem_cap",
                                  rowMapper,
                                  new Object[]{bibliotecaId});
    }

    /**
     * Busca embeddings por capítulo
     */
    public List<DocEmbedding> findByCapituloId(Integer capituloId)
            throws DataAccessException, SQLException {
        return jdbcTemplate.query("SELECT * FROM doc_embedding WHERE capitulo_id = ? ORDER BY ordem_cap",
                                  rowMapper,
                                  new Object[]{capituloId});
    }

    /**
     * Busca embeddings por tipo
     */
    public List<DocEmbedding> findByTipoEmbedding(TipoEmbedding tipoEmbedding)
            throws DataAccessException, SQLException {
        return jdbcTemplate.query("SELECT * FROM doc_embedding WHERE tipo_embedding = ? ORDER BY id",
                                  rowMapper,
                                  new Object[]{tipoEmbedding.getDbValue()});
    }

    // ======== MÉTODOS DE PESQUISA ========

    /**
     * Realiza pesquisa híbrida (semântica + textual) com filtro por bibliotecas
     */
    public List<DocEmbedding> pesquisaHibrida(float[] embedding,
                                             String query,
                                             Integer[] bibliotecaIds,
                                             Integer k,
                                             Float pesoSemantico,
                                             Float pesoTextual) {
        if (k == null || k < 1) k = k_pesquisa;
        if (pesoSemantico == null) pesoSemantico = 0.6f;
        if (pesoTextual == null) pesoTextual = 0.4f;

        String queryProcessed = query_phraseto_websearch(query);
        String libIds = Arrays.stream(bibliotecaIds)
                             .map(String::valueOf)
                             .collect(Collectors.joining(", "));

        // Assume dimensão padrão - em implementação real pegaria da biblioteca
        // @TODO Recuperar dimensão correta do vetor da biblioteca
        Integer vecLength = 1536;

        String sql = """
            WITH semantic_search AS (
                SELECT id,
                       1.0 / (? + RANK() OVER (ORDER BY embedding_vector <=> ? ASC)) AS score_semantic,
                       RANK() OVER (ORDER BY embedding_vector <=> ? ASC) AS rank_semantic
                FROM doc_embedding de
                WHERE de.biblioteca_id IN (%s)
                LIMIT ?
            ),
            text_search AS (
                SELECT id,
                       1.0 / (? + RANK() OVER (ORDER BY ts_rank_cd(texto_indexado, to_tsquery('portuguese', ?)) DESC)) AS score_text,
                       RANK() OVER (ORDER BY ts_rank_cd(texto_indexado, to_tsquery('portuguese', ?)) DESC) AS rank_text
                FROM doc_embedding
                WHERE biblioteca_id IN (%s)
                AND texto_indexado @@ to_tsquery('portuguese', ?)
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
            k, // normalization factor for semantic
            new PGvector(embedding), // for semantic_search
            new PGvector(embedding), // for semantic_search (duplicate in RANK)
            k * 2, // expanded limit for semantic_search
            k, // normalization factor for text
            queryProcessed, // for text_search
            queryProcessed, // for text_search (duplicate in RANK)
            k * 2, // expanded limit for text_search
            queryProcessed, // for text_search WHERE clause
            pesoSemantico,
            pesoTextual,
            k // final results limit
        };

        return jdbcTemplate.query(sql, rowMapperWithScores, params);
    }

    /**
     * Pesquisa semântica em bibliotecas específicas
     */
    public List<DocEmbedding> pesquisaSemantica(@NonNull float[] vec,
                                               @NonNull Integer[] bibliotecaIds,
                                               @NonNull Integer k) {
        if (k == null) k = k_pesquisa;

        String libIds = Arrays.stream(bibliotecaIds)
                             .map(String::valueOf)
                             .collect(Collectors.joining(", "));

        String sql = """
                SELECT d.*,
                1.0 / (1 + (embedding_vector <-> ?)) AS score_semantic,
                0.0 AS score_text,
                1.0 / (1 + (embedding_vector <-> ?)) AS score
                FROM doc_embedding d
                WHERE biblioteca_id IN (%s)
                ORDER BY embedding_vector <-> ?
                LIMIT ?
                """.formatted(libIds);

        PGvector pgVector = new PGvector(vec);
        Object[] params = new Object[] { pgVector, pgVector, pgVector, k };

        return jdbcTemplate.query(sql, rowMapperWithScores, params);
    }

    /**
     * Pesquisa textual em bibliotecas específicas
     */
    public List<DocEmbedding> pesquisaTextual(@NonNull String queryString,
                                            @NonNull Integer[] bibliotecaIds,
                                            Integer k) {
        if (k == null) k = k_pesquisa;

        String queryProcessed = query_phraseto_websearch(queryString);
        String libIds = Arrays.stream(bibliotecaIds)
                             .map(String::valueOf)
                             .collect(Collectors.joining(", "));

        String sql = """
            SELECT d.*,
                   0.0 AS score_semantic,
                   ts_rank_cd(texto_indexado, to_tsquery('portuguese', ?)) AS score_text,
                   ts_rank_cd(texto_indexado, to_tsquery('portuguese', ?)) AS score
            FROM doc_embedding d
            WHERE biblioteca_id IN (%s)
            AND texto_indexado @@ to_tsquery('portuguese', ?)
            ORDER BY score DESC
            LIMIT ?
            """.formatted(libIds);

        Object[] params = { queryProcessed, queryProcessed, queryProcessed, k };
        return jdbcTemplate.query(sql, rowMapperWithScores, params);
    }

    // ======== MÉTODOS DE PROCESSAMENTO DE QUERY ========

    /**
     * Transforma uma frase em query para PostgreSQL websearch_to_tsquery
     */
    public String query_phraseto_websearch(String frase) {
        return query_phraseto_websearch(frase, true);
    }

    /**
     * Transforma uma frase em query para PostgreSQL websearch_to_tsquery
     */
    public String query_phraseto_websearch(String frase, boolean pesquisaAmpla) {
        if (frase == null || frase.trim().isEmpty()) {
            throw new IllegalArgumentException("Frase não pode ser nula ou vazia");
        }

        String normalizedFrase = fixQuery(frase);
        if (normalizedFrase == null) {
            throw new IllegalArgumentException("Frase inválida após normalização");
        }

        String tsQuery = jdbcTemplate.queryForObject(
            "SELECT websearch_to_tsquery('portuguese'::regconfig, ?)",
            String.class,
            normalizedFrase
        );

        if (pesquisaAmpla && tsQuery != null) {
            return tsQuery
                .replace(TAG_EXCLUSAO, TAG_HOLD)
                .replace(" & ", " | ")
                .replace(TAG_HOLD, TAG_EXCLUSAO);
        }

        return tsQuery;
    }

    /**
     * Corrige uma query textual
     */
    public String fixQuery(String query) {
        if (query == null || query.isEmpty()) {
            return query;
        }

        // Limpeza e conversão de operadores lógicos
        query = query.replaceAll("[^\\p{L}\\p{Nd}\\s]+", " ");
        query = query.replaceAll(" AND ", " ");
        query = query.replaceAll(" NOT ", " -");

        try {
            String tsquery = jdbcTemplate
                    .queryForObject("SELECT websearch_to_tsquery('portuguese'::regconfig, ?) ",
                                    String.class,
                                    new Object[] { query });
            return tsquery != null && !tsquery.isEmpty() ? tsquery : query;
        } catch (Exception e) {
            return null;
        }
    }

    // ======== MÉTODOS CRUD AVANÇADOS ========

    /**
     * Salva um DocEmbedding
     */
    public Integer save(@NonNull DocEmbedding doc) throws DataAccessException, SQLException {
        doOnce();

        if (doc.getId() != null) {
            update(doc);
            return doc.getId();
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        final String sql = """
            INSERT INTO doc_embedding
            (biblioteca_id, documento_id, capitulo_id, tipo_embedding,
             trecho_texto, ordem_cap, embedding_vector, metadados, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP)
            """;

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, doc.getBibliotecaId());
            ps.setInt(2, doc.getDocumentoId());
            ps.setObject(3, doc.getCapituloId());
            ps.setString(4, doc.getTipoEmbedding().getDbValue());
            ps.setString(5, doc.getTrechoTexto());
            ps.setObject(6, doc.getOrdemCap());

            if (doc.getEmbeddingVector() != null) {
                ps.setObject(7, new PGvector(doc.getEmbeddingVector()));
            } else {
                ps.setObject(7, null);
            }

            // Metadados como JSON - em implementação real usaria Jackson
            ps.setString(8, doc.getMetadados() != null ? "{}" : null);
            return ps;
        }, keyHolder);

        Map<String, Object> map = keyHolder.getKeys();
        Object pk = map.get("id");
        if (pk instanceof Number) {
            int value = ((Number) pk).intValue();
            doc.setId(value);
            return value;
        }
        return doc.getId();
    }

    /**
     * Atualiza um DocEmbedding
     */
    public int update(DocEmbedding doc) throws DataAccessException, SQLException {
        if (doc.getId() == null) {
            return save(doc);
        }

        final String sql = """
            UPDATE doc_embedding SET
            biblioteca_id = ?, documento_id = ?, capitulo_id = ?,
            tipo_embedding = ?, trecho_texto = ?, ordem_cap = ?,
            embedding_vector = ?, metadados = ?::jsonb
            WHERE id = ?
            """;

        return jdbcTemplate.update(sql,
            doc.getBibliotecaId(),
            doc.getDocumentoId(),
            doc.getCapituloId(),
            doc.getTipoEmbedding().getDbValue(),
            doc.getTrechoTexto(),
            doc.getOrdemCap(),
            doc.getEmbeddingVector() != null ? new PGvector(doc.getEmbeddingVector()) : null,
            doc.getMetadados() != null ? "{}" : null, // JSON serialization
            doc.getId()
        );
    }

    /**
     * Deleta um registro da tabela doc_embedding
     */
    public int delete(int id) throws DataAccessException, SQLException {
        return jdbcTemplate.update("DELETE FROM doc_embedding WHERE id = ?", id);
    }
}