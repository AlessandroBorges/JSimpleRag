
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

import bor.tools.simplerag.entity.DocumentEmbedding;
import bor.tools.simplerag.entity.MetaBiblioteca;
import bor.tools.simplerag.entity.MetaDoc;
import bor.tools.simplerag.entity.enums.TipoEmbedding;
import bor.tools.simplerag.util.VectorUtil;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * Repositório JDBC para DocumentEmbedding adaptado para JSimpleRag.
 *
 * Baseado na implementação de referência com adaptações para a arquitetura hierárquica
 * do JSimpleRag (Library → Documento → Chapter → DocumentEmbedding).
 *
 * Campos de interesse para read/write:
 * - id
 * - biblioteca_id
 * - documento_id
 * - capitulo_id (nullable)
 * - tipo_embedding
 * - texto
 * - ordem_cap (nullable)
 * - embedding_vector
 * - metadados
 * - created_at
 *
 * Campo read-only (gerado por trigger):
 * - text_search_tsv (tsvector)
 * 
 * Fornece métodos CRUD básicos e avançados, incluindo pesquisa semântica,
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
     * RowMapper para DocumentEmbedding
     */
    private RowMapper<DocumentEmbedding> rowMapper = (rs, rowNum) -> {
        DocumentEmbedding doc = DocumentEmbedding.builder()
            .id(rs.getInt("id"))
            .libraryId(rs.getInt("library_id"))
            .documentoId(rs.getInt("documento_id"))
            .chapterId(rs.getObject("chapter_id", Integer.class))
            .tipoEmbedding(TipoEmbedding.fromString(rs.getString("tipo_embedding")))
            .texto(rs.getString("texto"))
            .orderChapter(rs.getObject("order_chapter", Integer.class))
            .createdAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null)
            .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null)
            .build();

        // Parse metadados JSON
        String metadataJson = rs.getString("metadados");
        if (metadataJson != null && !metadataJson.isEmpty() && !metadataJson.equals("{}")) {
            // TODO: Usar Jackson para parse adequado do JSON
            // Por enquanto, criamos um mapa vazio para evitar NPE
            doc.setMetadados(new MetaDoc());
        }

        // Processa embedding vector
        Object pg = rs.getObject("embedding_vector");
        MetaBiblioteca config = getBibliotecaConfig(doc.getLibraryId());
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
     * RowMapper para DocumentEmbedding com scores de pesquisa
     */
    private RowMapper<DocumentEmbedding> rowMapperWithScores = (rs, rowNum) -> {
        DocumentEmbedding doc = rowMapper.mapRow(rs, rowNum);

        // Adiciona scores aos metadados
        MetaDoc metadados = doc.getMetadados();
        if (metadados == null) {
            metadados = new MetaDoc();
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

            updateMapLibraryId2VecLen();
        } catch (Exception e) {
            System.out.println("Error initializing the database with PGVector extensions");
            e.printStackTrace();
        }
        isInitialized = true;
    }

    /**
     * Atualiza o mapeamento de biblioteca_id para dimensão do vetor
     */
    private void updateMapLibraryId2VecLen() throws SQLException {
        java.sql.Connection conn = jdbcTemplate.getDataSource().getConnection();

        String sql = "SELECT id, metadados FROM library";
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

        updateMapLibraryId2VecLen();
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
    public List<DocumentEmbedding> findAll() {
        return jdbcTemplate.query("SELECT * FROM doc_embedding ORDER BY id", rowMapper);
    }

    /**
     * Retorna um registro da tabela doc_embedding pelo ID
     */
    public Optional<DocumentEmbedding> findById(@NonNull Integer id)
            throws DataAccessException, SQLException {
        var obj = jdbcTemplate.queryForObject("SELECT * FROM doc_embedding WHERE id = ?",
                                            rowMapper,
                                            new Object[]{id});
        return Optional.ofNullable(obj);
    }

    /**
     * Busca embeddings por documento
     */
    public List<DocumentEmbedding> findByDocumentoId(Integer documentoId)
            throws DataAccessException, SQLException {
        return jdbcTemplate.query("SELECT * FROM doc_embedding WHERE documento_id = ? ORDER BY order_chapter",
                                  rowMapper,
                                  new Object[]{documentoId});
    }

    /**
     * Busca embeddings por biblioteca
     */
    public List<DocumentEmbedding> findByBibliotecaId(Integer bibliotecaId)
            throws DataAccessException, SQLException {
        return jdbcTemplate.query("SELECT * FROM doc_embedding WHERE library_id = ? ORDER BY documento_id, order_chapter",
                                  rowMapper,
                                  new Object[]{bibliotecaId});
    }

    /**
     * Busca embeddings por capítulo
     */
    public List<DocumentEmbedding> findByCapituloId(Integer capituloId)
            throws DataAccessException, SQLException {
        return jdbcTemplate.query("SELECT * FROM doc_embedding WHERE chapter_id = ? ORDER BY order_chapter",
                                  rowMapper,
                                  new Object[]{capituloId});
    }

    /**
     * Busca embeddings por tipo
     */
    public List<DocumentEmbedding> findByTipoEmbedding(TipoEmbedding tipoEmbedding)
            throws DataAccessException, SQLException {
        return jdbcTemplate.query("SELECT * FROM doc_embedding WHERE tipo_embedding = ? ORDER BY id",
                                  rowMapper,
                                  new Object[]{tipoEmbedding.getDbValue()});
    }

    // ======== MÉTODOS DE PESQUISA ========

    /**
     * Realiza pesquisa híbrida (semântica + textual) com filtro por bibliotecas
     */
    public List<DocumentEmbedding> pesquisaHibrida(float[] embedding,
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
        //Integer vecLength = 1536;

        String sql = """
            WITH semantic_search AS (
                SELECT id,
                       1.0 / (? + RANK() OVER (ORDER BY embedding_vector <=> ? ASC)) AS score_semantic,
                       RANK() OVER (ORDER BY embedding_vector <=> ? ASC) AS rank_semantic
                FROM doc_embedding de
                WHERE de.library_id IN (%s)
                LIMIT ?
            ),
            text_search AS (
                SELECT id,
                       1.0 / (? + RANK() OVER (ORDER BY ts_rank_cd(text_search_tsv, ?::tsquery) DESC)) AS score_text,
                       RANK() OVER (ORDER BY ts_rank_cd(text_search_tsv, ?::tsquery) DESC) AS rank_text
                FROM doc_embedding
                WHERE library_id IN (%s)
                AND text_search_tsv @@ ?::tsquery
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
    public List<DocumentEmbedding> pesquisaSemantica(@NonNull float[] vec,
                                               @NonNull Integer[] bibliotecaIds,
                                               Integer k) {
        if (k == null) 
            k = k_pesquisa;

        String libIds = Arrays.stream(bibliotecaIds)
                             .map(String::valueOf)
                             .collect(Collectors.joining(", "));

        String sql = """
                SELECT d.*,
                1.0 / (1 + (embedding_vector <-> ?)) AS score_semantic,
                0.0 AS score_text,
                1.0 / (1 + (embedding_vector <-> ?)) AS score
                FROM doc_embedding d
                WHERE library_id IN (%s)
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
    public List<DocumentEmbedding> pesquisaTextual(@NonNull String queryString,
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
                   ts_rank_cd(text_search_tsv, ?::tsquery) AS score_text,
                   ts_rank_cd(text_search_tsv, ?::tsquery) AS score
            FROM doc_embedding d
            WHERE library_id IN (%s)
            AND text_search_tsv @@ ?::tsquery
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
     * dando opção de pesquisa ampla
     * 
     * 
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
     * Salva um DocumentEmbedding
     */
    public Integer save(@NonNull DocumentEmbedding doc) throws DataAccessException, SQLException {
        doOnce();

        if (doc.getId() != null) {
            update(doc);
            return doc.getId();
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        final String sql = """
            INSERT INTO doc_embedding
            (library_id, documento_id, chapter_id, tipo_embedding,
             texto, order_chapter, embedding_vector, metadados, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP)
            """;

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, doc.getLibraryId());
            ps.setInt(2, doc.getDocumentoId());
            ps.setObject(3, doc.getChapterId());
            ps.setString(4, doc.getTipoEmbedding().getDbValue());
            ps.setString(5, doc.getTexto());
            ps.setObject(6, doc.getOrderChapter());

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
     * Atualiza um DocumentEmbedding
     */
    public int update(DocumentEmbedding doc) throws DataAccessException, SQLException {
        if (doc.getId() == null) {
            return save(doc);
        }

        final String sql = """
            UPDATE doc_embedding SET
            library_id = ?, documento_id = ?, chapter_id = ?,
            tipo_embedding = ?, texto = ?, order_chapter = ?,
            embedding_vector = ?, metadados = ?::jsonb, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

        return jdbcTemplate.update(sql,
            doc.getLibraryId(),
            doc.getDocumentoId(),
            doc.getChapterId(),
            doc.getTipoEmbedding().getDbValue(),
            doc.getTexto(),
            doc.getOrderChapter(),
            doc.getEmbeddingVector() != null ? new PGvector(doc.getEmbeddingVector()) : null,
            doc.getMetadados() != null ? "{}" : null, // JSON serialization
            doc.getId()
        );
    }

    /**
     * Batch insert de DocumentEmbeddings com vetores NULL.
     *
     * <p>Otimizado para persistência inicial - vetores são calculados posteriormente.
     * Este método é usado no novo fluxo de processamento sequencial (v0.0.3+) onde
     * os chunks são primeiro persistidos e então os vetores são calculados em batch.</p>
     *
     * <p><b>Performance:</b> Muito mais eficiente que múltiplas chamadas a save(),
     * reduzindo round-trips ao banco de dados.</p>
     *
     * @param embeddings Lista de embeddings (vetor pode ser NULL)
     * @return Lista de IDs gerados na ordem dos embeddings fornecidos
     * @throws DataAccessException se a operação falhar
     * @throws SQLException se houver erro SQL
     * @since 0.0.3
     */
    public List<Integer> saveAll(@NonNull List<DocumentEmbedding> embeddings)
            throws DataAccessException, SQLException {
        if (embeddings == null || embeddings.isEmpty()) {
            return List.of();
        }

        doOnce(); // Ensure initialization

        final String sql = """
            INSERT INTO doc_embedding
            (library_id, documento_id, chapter_id, tipo_embedding,
             texto, order_chapter, embedding_vector, metadados, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, CURRENT_TIMESTAMP)
            """;

        List<Integer> generatedIds = new java.util.ArrayList<>();

        for (DocumentEmbedding doc : embeddings) {
            KeyHolder keyHolder = new GeneratedKeyHolder();

            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setInt(1, doc.getLibraryId());
                ps.setInt(2, doc.getDocumentoId());
                ps.setObject(3, doc.getChapterId());
                ps.setString(4, doc.getTipoEmbedding().getDbValue());
                ps.setString(5, doc.getTexto());
                ps.setObject(6, doc.getOrderChapter());

                // Vector can be NULL (calculated later)
                if (doc.getEmbeddingVector() != null) {
                    ps.setObject(7, new PGvector(doc.getEmbeddingVector()));
                } else {
                    ps.setObject(7, null);
                }

                // Metadados as JSON
                ps.setString(8, doc.getMetadados() != null ? "{}" : null);

                return ps;
            }, keyHolder);

            // Retrieve generated ID
            Map<String, Object> map = keyHolder.getKeys();
            Object pk = map.get("id");
            if (pk instanceof Number) {
                int id = ((Number) pk).intValue();
                doc.setId(id); // Update the object with generated ID
                generatedIds.add(id);
            }
        }

        return generatedIds;
    }

    /**
     * Atualiza apenas o vetor de embedding de um registro
     *
     * @param id - PK do registro DocEmbedding
     * @param embeddingVector - vetor de embedding a ser atualizado
     *
     * @throws DataAccessException
     * @throws SQLException
     */
    public void updateEmbeddingVector(int id, float[] embeddingVector)
	    throws DataAccessException, SQLException {
	final String sql = "UPDATE doc_embedding SET embedding_vector = ?, "
		         + "updated_at = CURRENT_TIMESTAMP WHERE id = ?";

	jdbcTemplate.update(sql,
	    embeddingVector != null ? new PGvector(embeddingVector) : null,
	    id
	);
    }
    
    /**
     * Deleta um registro da tabela doc_embedding
     * 
     */
    public int delete(int id) throws DataAccessException, SQLException {
        return jdbcTemplate.update("DELETE FROM doc_embedding WHERE id = ?", id);
    }

    /**
     * Delete
     */
    public int deleteAll()  throws DataAccessException, SQLException {
        return jdbcTemplate.update("DELETE FROM doc_embedding WHERE id >= ?", 1);
    }

    // ========== OVERWRITE FEATURE (v1.0) ==========

    /**
     * Counts embeddings for a document.
     *
     * Used by overwrite feature to check if document has existing embeddings.
     *
     * @param documentoId Document ID
     * @return Number of embeddings
     * @throws SQLException if query fails
     * @since 1.0 (overwrite feature)
     */
    public int countByDocumentoId(Integer documentoId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM doc_embedding WHERE documento_id = ?";

        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, documentoId);
            return count != null ? count : 0;
        } catch (DataAccessException e) {
            throw new SQLException("Failed to count embeddings for document " + documentoId, e);
        }
    }
}