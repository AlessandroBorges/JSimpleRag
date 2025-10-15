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
    @Query("SELECT b FROM Library b WHERE LOWER(b.nome) = LOWER(:nome)")
    Optional<Library> findByNomeIgnoreCase(String nome);

    /**
     * Busca bibliotecas por área de conhecimento
     */
    @Query("SELECT b FROM Library b WHERE LOWER(b.areaConhecimento) = LOWER(:areaConhecimento)")
    List<Library> findByAreaConhecimentoIgnoreCase(String areaConhecimento);

    /**
     * Busca bibliotecas por área de conhecimento contendo texto
     */
    @Query("SELECT b FROM Library b WHERE LOWER(b.areaConhecimento) LIKE LOWER(CONCAT('%', :areaConhecimento, '%'))")
    List<Library> findByAreaConhecimentoContainingIgnoreCase(String areaConhecimento);

    /**
     * Verifica se existe biblioteca com o nome especificado
     */
    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM Library b WHERE LOWER(b.nome) = LOWER(:nome)")
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
    
    /**
     * Busca bibliotecas que contêm uma chave específica nos metadados.
     */
    @Query(value = "SELECT * FROM biblioteca WHERE metadados @?1::jsonb", nativeQuery = true)
    List<Library> findByMetadadosContaining(String chave);
    
    /**
     * Busca bibliotecas que contêm uma chave e valor específicos nos metadados.
     */
    @Query(value = "SELECT * FROM biblioteca WHERE metadados->>?1 = ?2", nativeQuery = true)
    List<Library> findByMetadadosContainingKeyAndValue(String chave , String valor);
    
    /**
     * Busca todas as bibliotecas associadas a um usuário
     */
    @Query("SELECT l FROM Library l JOIN UserLibrary ul ON l.id = ul.libraryId WHERE ul.userId = :id")
    List<Library> findByUsuarioId(Integer id);
}