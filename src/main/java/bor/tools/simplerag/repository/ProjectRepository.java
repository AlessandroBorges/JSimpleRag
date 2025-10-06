package bor.tools.simplerag.repository;

import bor.tools.simplerag.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório JPA para a entidade Project.
 *
 * Provides standard CRUD operations and custom queries for Project entities.
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /**
     * Busca todos os projetos de um usuário
     */
    List<Project> findByUserId(UUID userId);

    /**
     * Busca projetos por UUID da biblioteca privativa
     */
    List<Project> findByBibliotecaPrivativa(UUID bibliotecaPrivativa);

    /**
     * Busca projetos por título (contendo texto, case-insensitive)
     */
    List<Project> findByTituloContainingIgnoreCase(String titulo);

    /**
     * Busca projetos de um usuário ordenados por ordem
     */
    List<Project> findByUserIdOrderByOrdemAsc(UUID userId);

    /**
     * Busca projetos de um usuário ordenados por data de atualização (mais recente primeiro)
     */
    @Query("SELECT p FROM Project p WHERE p.user_id = :userId ORDER BY p.updatedAt DESC")
    List<Project> findByUserIdOrderByUpdatedAtDesc(@Param("userId") UUID userId);

    /**
     * Busca projetos de um usuário criados após uma data específica
     */
    @Query("SELECT p FROM Project p WHERE p.user_id = :userId AND p.createdAt >= :fromDate ORDER BY p.createdAt DESC")
    List<Project> findRecentProjects(@Param("userId") UUID userId, @Param("fromDate") java.time.LocalDateTime fromDate);

    /**
     * Busca projetos que usam uma biblioteca privativa específica
     */
    @Query("SELECT p FROM Project p WHERE p.biblioteca_privativa = :bibliotecaUuid")
    List<Project> findByPrivateLibrary(@Param("bibliotecaUuid") UUID bibliotecaUuid);

    /**
     * Busca projeto por usuário e título exato
     */
    Optional<Project> findByUserIdAndTitulo(UUID userId, String titulo);

    /**
     * Verifica se existe projeto com título específico para um usuário
     */
    boolean existsByUserIdAndTitulo(UUID userId, String titulo);

    /**
     * Conta quantos projetos um usuário possui
     */
    long countByUserId(UUID userId);

    /**
     * Busca projetos de um usuário com descrição preenchida
     */
    @Query("SELECT p FROM Project p WHERE p.user_id = :userId AND p.descricao IS NOT NULL AND p.descricao != '' ORDER BY p.updatedAt DESC")
    List<Project> findProjectsWithDescription(@Param("userId") UUID userId);

    /**
     * Busca projetos de um usuário sem descrição
     */
    @Query("SELECT p FROM Project p WHERE p.user_id = :userId AND (p.descricao IS NULL OR p.descricao = '') ORDER BY p.updatedAt DESC")
    List<Project> findProjectsWithoutDescription(@Param("userId") UUID userId);

    /**
     * Busca os N projetos mais recentes de um usuário
     */
    @Query("SELECT p FROM Project p WHERE p.user_id = :userId ORDER BY p.updatedAt DESC LIMIT :limit")
    List<Project> findTopNRecentProjects(@Param("userId") UUID userId, @Param("limit") int limit);

    /**
     * Busca a próxima ordem disponível para projetos de um usuário
     */
    @Query("SELECT COALESCE(MAX(p.ordem), 0) + 1 FROM Project p WHERE p.user_id = :userId")
    Integer findNextOrdem(@Param("userId") UUID userId);

    /**
     * Busca projetos de um usuário com ordem específica
     */
    Optional<Project> findByUserIdAndOrdem(UUID userId, Integer ordem);

    /**
     * Busca projetos de um usuário em intervalo de ordens
     */
    @Query("SELECT p FROM Project p WHERE p.user_id = :userId AND p.ordem BETWEEN :fromOrdem AND :toOrdem ORDER BY p.ordem ASC")
    List<Project> findProjectsBetweenOrdem(@Param("userId") UUID userId,
                                            @Param("fromOrdem") Integer fromOrdem,
                                            @Param("toOrdem") Integer toOrdem);

    /**
     * Remove todos os projetos de um usuário
     */
    void deleteByUserId(UUID userId);

    /**
     * Remove todos os projetos que usam uma biblioteca privativa específica
     */
    void deleteByBibliotecaPrivativa(UUID bibliotecaPrivativa);
}
