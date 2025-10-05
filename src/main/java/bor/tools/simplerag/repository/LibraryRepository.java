package bor.tools.simplerag.repository;

import bor.tools.simplerag.entity.Library;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositório JPA para a entidade Library.
 *
 * Provides standard CRUD operations and custom queries for Library entities.
 */
@Repository
public interface LibraryRepository extends JpaRepository<Library, Integer> {

    /**
     * Busca biblioteca por nome (case-insensitive)
     */
    Optional<Library> findByNomeIgnoreCase(String nome);

    /**
     * Busca bibliotecas por área de conhecimento
     */
    List<Library> findByAreaConhecimentoIgnoreCase(String areaConhecimento);

    /**
     * Busca bibliotecas por área de conhecimento contendo texto
     */
    List<Library> findByAreaConhecimentoContainingIgnoreCase(String areaConhecimento);

    /**
     * Verifica se existe biblioteca com o nome especificado
     */
    boolean existsByNomeIgnoreCase(String nome);

    /**
     * Lista todas as áreas de conhecimento distintas
     */
    @Query("SELECT DISTINCT b.areaConhecimento FROM Library b ORDER BY b.areaConhecimento")
    List<String> findDistinctAreasConhecimento();

    /**
     * Busca bibliotecas por peso semântico maior que o especificado
     */
    @Query("SELECT b FROM Library b WHERE b.pesoSemantico >= :pesoMinimo ORDER BY b.pesoSemantico DESC")
    List<Library> findByPesoSemanticoGreaterThanEqual(@Param("pesoMinimo") Float pesoMinimo);

    /**
     * Busca bibliotecas por peso textual maior que o especificado
     */
    @Query("SELECT b FROM Library b WHERE b.pesoTextual >= :pesoMinimo ORDER BY b.pesoTextual DESC")
    List<Library> findByPesoTextualGreaterThanEqual(@Param("pesoMinimo") Float pesoMinimo);

    /**
     * Busca bibliotecas com configuração de idioma específico nos metadados
     */
    @Query(value = "SELECT * FROM biblioteca WHERE metadados->>'language' = :idioma", nativeQuery = true)
    List<Library> findByIdioma(@Param("idioma") String idioma);

    /**
     * Busca bibliotecas com modelo de embedding específico nos metadados
     */
    @Query(value = "SELECT * FROM biblioteca WHERE metadados->>'embedding_model' = :modelo", nativeQuery = true)
    List<Library> findByModeloEmbedding(@Param("modelo") String modelo);

    /**
     * Busca bibliotecas criadas após uma data específica
     */
    @Query("SELECT b FROM Library b WHERE b.createdAt >= :dataInicio ORDER BY b.createdAt DESC")
    List<Library> findBibliotecasRecentes(@Param("dataInicio") java.time.LocalDateTime dataInicio);

    /**
     * Conta o número de bibliotecas por área de conhecimento
     */
    @Query("SELECT b.areaConhecimento, COUNT(b) FROM Library b GROUP BY b.areaConhecimento ORDER BY COUNT(b) DESC")
    List<Object[]> countByAreaConhecimento();
}