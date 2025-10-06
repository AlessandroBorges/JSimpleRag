package bor.tools.simplerag.repository;

import bor.tools.simplerag.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório JPA para a entidade Chapter.
 *
 * Provides standard CRUD operations and custom queries for Chapter entities.
 */
@Repository
public interface ChapterRepository extends JpaRepository<Chapter, Integer> {

    /**
     * Busca capítulos por documento, ordenados por ordem
     */
    List<Chapter> findByDocumentoIdOrderByOrdemDoc(Integer documentoId);

    /**
     * Busca capítulo por documento e ordem
     */
    Optional<Chapter> findByDocumentoIdAndOrdemDoc(Integer documentoId, Integer ordemDoc);

    /**
     * Busca capítulos por título contendo texto (case-insensitive)
     */
    List<Chapter> findByTituloContainingIgnoreCase(String titulo);

    /**
     * Busca capítulos por título contendo texto em documento específico
     */
    List<Chapter> findByTituloContainingIgnoreCaseAndDocumentoId(String titulo, Integer documentoId);

    /**
     * Busca capítulos por número mínimo de tokens
     */
    @Query("SELECT c FROM Chapter c WHERE c.tokensTotal >= :tokensMinimo ORDER BY c.tokensTotal DESC")
    List<Chapter> findByTokensTotalGreaterThanEqual(@Param("tokensMinimo") Integer tokensMinimo);

    /**
     * Busca capítulos com tokens entre intervalo
     */
    @Query("SELECT c FROM Chapter c WHERE c.tokensTotal BETWEEN :tokensMin AND :tokensMax ORDER BY c.tokensTotal")
    List<Chapter> findByTokensTotalBetween(@Param("tokensMin") Integer tokensMin, @Param("tokensMax") Integer tokensMax);

    /**
     * Busca capítulos por posição de tokens (range)
     */
    @Query("SELECT c FROM Chapter c WHERE c.tokenInicio <= :posicao AND c.tokenFim > :posicao")
    List<Chapter> findByTokenPosition(@Param("posicao") Integer posicao);

    /**
     * Busca capítulos que contêm um range de tokens
     */
    @Query("SELECT c FROM Chapter c WHERE c.tokenInicio <= :inicio AND c.tokenFim >= :fim")
    List<Chapter> findByTokenRange(@Param("inicio") Integer inicio, @Param("fim") Integer fim);

    /**
     * Conta capítulos por documento
     */
    @Query("SELECT COUNT(c) FROM Chapter c WHERE c.documentoId = :documentoId")
    Long countByDocumento(@Param("documentoId") Integer documentoId);

    /**
     * Busca primeiro capítulo do documento
     */
    @Query("SELECT c FROM Chapter c WHERE c.documentoId = :documentoId ORDER BY c.ordemDoc ASC")
    Optional<Chapter> findPrimeiroCapitulo(@Param("documentoId") Integer documentoId);

    /**
     * Busca último capítulo do documento
     */
    @Query("SELECT c FROM Chapter c WHERE c.documentoId = :documentoId ORDER BY c.ordemDoc DESC")
    Optional<Chapter> findUltimoCapitulo(@Param("documentoId") Integer documentoId);

    /**
     * Busca próximo capítulo
     */
    @Query("SELECT c FROM Chapter c WHERE c.documentoId = :documentoId AND c.ordemDoc > :ordemAtual ORDER BY c.ordemDoc ASC")
    Optional<Chapter> findProximoCapitulo(@Param("documentoId") Integer documentoId, @Param("ordemAtual") Integer ordemAtual);

    /**
     * Busca capítulo anterior
     */
    @Query("SELECT c FROM Chapter c WHERE c.documentoId = :documentoId AND c.ordemDoc < :ordemAtual ORDER BY c.ordemDoc DESC")
    Optional<Chapter> findCapituloAnterior(@Param("documentoId") Integer documentoId, @Param("ordemAtual") Integer ordemAtual);

    /**
     * Busca capítulos com metadados específicos
     */
    @Query(value = "SELECT * FROM capitulo WHERE metadados @> :metadados::jsonb", nativeQuery = true)
    List<Chapter> findByMetadados(@Param("metadados") String metadados);

    /**
     * Busca capítulos que contêm uma chave específica nos metadados
     */
    @Query(value = "SELECT * FROM capitulo WHERE metadados ? :chave", nativeQuery = true)
    List<Chapter> findByMetadadosContainingKey(@Param("chave") String chave);

    /**
     * Estatísticas de tokens por documento
     */
    @Query("SELECT SUM(c.tokensTotal), AVG(c.tokensTotal), MAX(c.tokensTotal), MIN(c.tokensTotal) " +
           "FROM Chapter c WHERE c.documentoId = :documentoId")
    Object[] getTokenStatsByDocumento(@Param("documentoId") Integer documentoId);

    /**
     * Busca capítulos sem tokens calculados
     */
    @Query("SELECT c FROM Chapter c WHERE c.tokensTotal IS NULL")
    List<Chapter> findSemTokensCalculados();

    /**
     * Busca capítulos com problemas de sequência (gaps na ordem)
     */
    @Query(value = """
        SELECT c1.* FROM capitulo c1
        WHERE c1.documento_id = :documentoId
        AND NOT EXISTS (
            SELECT 1 FROM capitulo c2
            WHERE c2.documento_id = c1.documento_id
            AND c2.ordem_doc = c1.ordem_doc - 1
        )
        AND c1.ordem_doc > 1
        ORDER BY c1.ordem_doc
        """, nativeQuery = true)
    List<Chapter> findCapitulosComGapsNaOrdem(@Param("documentoId") Integer documentoId);

    /**
     * Busca capítulos para processamento (sem embeddings gerados)
     */
    @Query(value = """
        SELECT c.* FROM capitulo c
        INNER JOIN documento d ON c.documento_id = d.id
        LEFT JOIN doc_embedding de ON c.id = de.capitulo_id
        WHERE d.flag_vigente = true
        AND de.id IS NULL
        ORDER BY c.documento_id, c.ordem_doc
        """, nativeQuery = true)
    List<Chapter> findCapitulosParaProcessamento();

    /**
     * Verifica se existe capítulo com ordem específica no documento
     */
    boolean existsByDocumentoIdAndOrdemDoc(Integer documentoId, Integer ordemDoc);

    /**
     * Busca máxima ordem do documento (para inserir próximo capítulo)
     */
    @Query("SELECT COALESCE(MAX(c.ordemDoc), 0) FROM Chapter c WHERE c.documentoId = :documentoId")
    Integer findMaxOrdemByDocumento(@Param("documentoId") Integer documentoId);

    /**
     * Busca capítulos por biblioteca (via documento)
     */
    @Query(value = """
        SELECT c.* FROM capitulo c
        INNER JOIN documento d ON c.documento_id = d.id
        WHERE d.biblioteca_id = :bibliotecaId
        AND d.flag_vigente = true
        ORDER BY d.id, c.ordem_doc
        """, nativeQuery = true)
    List<Chapter> findByBiblioteca(@Param("bibliotecaId") Integer bibliotecaId);
}