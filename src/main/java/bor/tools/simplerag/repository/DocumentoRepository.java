package bor.tools.simplerag.repository;

import bor.tools.simplerag.entity.Documento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repositório JPA para a entidade Documento.
 *
 * Provides standard CRUD operations and custom queries for Documento entities.
 */
@Repository
public interface DocumentoRepository extends JpaRepository<Documento, Integer> {

    /**
     * Busca documentos por biblioteca
     */
    List<Documento> findByBibliotecaId(Integer bibliotecaId);

    /**
     * Busca documentos vigentes por biblioteca
     */
    List<Documento> findByBibliotecaIdAndFlagVigenteTrue(Integer bibliotecaId);

    /**
     * Busca documento por título e biblioteca (case-insensitive)
     */
    Optional<Documento> findByTituloIgnoreCaseAndBibliotecaId(String titulo, Integer bibliotecaId);

    /**
     * Busca documentos por título contendo texto (case-insensitive)
     */
    List<Documento> findByTituloContainingIgnoreCase(String titulo);

    /**
     * Busca documentos por título contendo texto em biblioteca específica
     */
    List<Documento> findByTituloContainingIgnoreCaseAndBibliotecaId(String titulo, Integer bibliotecaId);

    /**
     * Busca documentos vigentes
     */
    List<Documento> findByFlagVigenteTrue();

    /**
     * Busca documentos não vigentes
     */
    List<Documento> findByFlagVigenteFalse();

    /**
     * Busca documentos por data de publicação
     */
    List<Documento> findByDataPublicacao(LocalDate dataPublicacao);

    /**
     * Busca documentos publicados após uma data
     */
    List<Documento> findByDataPublicacaoAfter(LocalDate dataInicio);

    /**
     * Busca documentos publicados entre duas datas
     */
    List<Documento> findByDataPublicacaoBetween(LocalDate dataInicio, LocalDate dataFim);

    /**
     * Verifica se existe documento vigente com mesmo título na biblioteca
     */
    boolean existsByTituloIgnoreCaseAndBibliotecaIdAndFlagVigenteTrue(String titulo, Integer bibliotecaId);

    /**
     * Busca documentos por número mínimo de tokens
     */
    @Query("SELECT d FROM Documento d WHERE d.tokensTotal >= :tokensMinimo ORDER BY d.tokensTotal DESC")
    List<Documento> findByTokensTotalGreaterThanEqual(@Param("tokensMinimo") Integer tokensMinimo);

    /**
     * Busca documentos com tokens entre intervalo
     */
    @Query("SELECT d FROM Documento d WHERE d.tokensTotal BETWEEN :tokensMin AND :tokensMax ORDER BY d.tokensTotal")
    List<Documento> findByTokensTotalBetween(@Param("tokensMin") Integer tokensMin, @Param("tokensMax") Integer tokensMax);

    /**
     * Busca documentos recentes por biblioteca (vigentes, ordenados por data de publicação)
     */
    @Query("SELECT d FROM Documento d WHERE d.bibliotecaId = :bibliotecaId AND d.flagVigente = true ORDER BY d.dataPublicacao DESC")
    List<Documento> findRecentesByBiblioteca(@Param("bibliotecaId") Integer bibliotecaId);

    /**
     * Busca documentos com metadados específicos
     */
    @Query(value = "SELECT * FROM documento WHERE metadados @> ?1::jsonb", nativeQuery = true)
    List<Documento> findByMetadados(String metadados);

    /**
     * Busca documentos que contêm uma chave específica nos metadados. <br>
     * Metadados estão formato JSONB no bando de dados. <br>
     * Exemplo de uso: chave = 'autor' retorna documentos com metadados que possuem a chave 'autor'
     */
    @Query(value = "SELECT * FROM documento WHERE jsonb_exists(metadados, ?1)", nativeQuery = true)
    List<Documento> findByMetadadosContainingKey(String chave);

    /**
     * Busca documentos com valor específico em metadados
     */
    @Query(value = "SELECT * FROM documento WHERE metadados->>?1 = ?2", nativeQuery = true)
    List<Documento> findByMetadadosValue(String key, String valor);

    /**
     * Busca documentos por biblioteca com paginação e ordenação por data
     */
    @Query("SELECT d FROM Documento d WHERE d.bibliotecaId = :bibliotecaId ORDER BY d.dataPublicacao DESC")
    List<Documento> findByBibliotecaIdOrderByDataPublicacaoDesc(@Param("bibliotecaId") Integer bibliotecaId);

    /**
     * Conta documentos vigentes por biblioteca
     */
    @Query("SELECT COUNT(d) FROM Documento d WHERE d.bibliotecaId = :bibliotecaId AND d.flagVigente = true")
    Long countVigentesByBiblioteca(@Param("bibliotecaId") Integer bibliotecaId);

    /**
     * Estatísticas de tokens por biblioteca
     */
    @Query("SELECT SUM(d.tokensTotal), AVG(d.tokensTotal), MAX(d.tokensTotal), MIN(d.tokensTotal) " +
           "FROM Documento d WHERE d.bibliotecaId = :bibliotecaId AND d.flagVigente = true")
    Object[] getTokenStatsByBiblioteca(@Param("bibliotecaId") Integer bibliotecaId);

    /**
     * Lista títulos únicos na biblioteca (útil para validação de duplicatas)
     */
    @Query("SELECT DISTINCT d.titulo FROM Documento d WHERE d.bibliotecaId = :bibliotecaId AND d.flagVigente = true ORDER BY d.titulo")
    List<String> findDistinctTitulosByBiblioteca(@Param("bibliotecaId") Integer bibliotecaId);

    /**
     * Busca documentos sem tokens calculados
     */
    @Query("SELECT d FROM Documento d WHERE d.tokensTotal IS NULL")
    List<Documento> findSemTokensCalculados();

    /**
     * Busca documentos para processamento (sem embeddings gerados)
     */
    @Query(value = """
        SELECT d.* FROM documento d
        LEFT JOIN doc_embedding de ON d.id = de.documento_id
        WHERE d.flag_vigente = true
        AND de.id IS NULL
        ORDER BY d.data_publicacao DESC
        """, nativeQuery = true)
    List<Documento> findDocumentosParaProcessamento();
}