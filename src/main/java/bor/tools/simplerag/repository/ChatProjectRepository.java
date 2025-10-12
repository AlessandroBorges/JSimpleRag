package bor.tools.simplerag.repository;

import bor.tools.simplerag.entity.ChatProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório JPA para a entidade ChatProject.
 *
 * Provides standard CRUD operations and custom queries for ChatProject entities.
 */
@Repository
public interface ChatProjectRepository extends JpaRepository<ChatProject, UUID> {

    /**
     * Busca todos os projetos de um usuário
     */
    List<ChatProject> findByUserId(UUID userId);

    /**
     * Busca projetos por UUID da biblioteca privativa
     */
    List<ChatProject> findByBibliotecaPrivativa(UUID bibliotecaPrivativa);

    /**
     * Busca projetos por título (contendo texto, case-insensitive)
     */
    List<ChatProject> findByTituloContainingIgnoreCase(String titulo);

    /**
     * Busca projetos de um usuário ordenados por ordem
     */
    List<ChatProject> findByUserIdOrderByOrdemAsc(UUID userId);

    /**
     * Busca projetos de um usuário ordenados por data de atualização (mais recente primeiro)
     */
    @Query("SELECT p FROM ChatProject p WHERE p.user_id = :userId ORDER BY p.updatedAt DESC")
    List<ChatProject> findByUserIdOrderByUpdatedAtDesc(@Param("userId") UUID userId);

    /**
     * Busca projetos de um usuário criados após uma data específica
     */
    @Query("SELECT p FROM ChatProject p WHERE p.user_id = :userId AND p.createdAt >= :fromDate ORDER BY p.createdAt DESC")
    List<ChatProject> findRecentProjects(@Param("userId") UUID userId, @Param("fromDate") java.time.LocalDateTime fromDate);

    /**
     * Busca projetos que usam uma biblioteca privativa específica
     */
    @Query("SELECT p FROM ChatProject p WHERE p.biblioteca_privativa = :bibliotecaUuid")
    List<ChatProject> findByPrivateLibrary(@Param("bibliotecaUuid") UUID bibliotecaUuid);

    /**
     * Busca projeto por usuário e título exato
     */
    Optional<ChatProject> findByUserIdAndTitulo(UUID userId, String titulo);

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
    @Query("SELECT p FROM ChatProject p WHERE p.user_id = :userId AND p.descricao IS NOT NULL AND p.descricao != '' ORDER BY p.updatedAt DESC")
    List<ChatProject> findProjectsWithDescription(@Param("userId") UUID userId);

    /**
     * Busca projetos de um usuário sem descrição
     */
    @Query("SELECT p FROM ChatProject p WHERE p.user_id = :userId AND (p.descricao IS NULL OR p.descricao = '') ORDER BY p.updatedAt DESC")
    List<ChatProject> findProjectsWithoutDescription(@Param("userId") UUID userId);

    /**
     * Busca os N projetos mais recentes de um usuário
     */
    @Query("SELECT p FROM ChatProject p WHERE p.user_id = :userId ORDER BY p.updatedAt DESC LIMIT :limit")
    List<ChatProject> findTopNRecentProjects(@Param("userId") UUID userId, @Param("limit") int limit);

    /**
     * Busca a próxima ordem disponível para projetos de um usuário
     */
    @Query("SELECT COALESCE(MAX(p.ordem), 0) + 1 FROM ChatProject p WHERE p.user_id = :userId")
    Integer findNextOrdem(@Param("userId") UUID userId);

    /**
     * Busca projetos de um usuário com ordem específica
     */
    Optional<ChatProject> findByUserIdAndOrdem(UUID userId, Integer ordem);

    /**
     * Busca projetos de um usuário em intervalo de ordens
     */
    @Query("SELECT p FROM ChatProject p WHERE p.user_id = :userId AND p.ordem BETWEEN :fromOrdem AND :toOrdem ORDER BY p.ordem ASC")
    List<ChatProject> findProjectsBetweenOrdem(@Param("userId") UUID userId,
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
