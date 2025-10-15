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
    @Query("SELECT p FROM ChatProject p WHERE p.user_uuid = :userUuid")
    List<ChatProject> findByUserId(UUID userUuid);

    /**
     * Busca projetos por UUID da biblioteca privativa
     */
    @Query("SELECT p FROM ChatProject p WHERE p.biblioteca_privativa = :biblioteca_privativa")
    List<ChatProject> findByBibliotecaPrivativa(UUID biblioteca_privativa);

    /**
     * Busca projetos por título (contendo texto, case-insensitive)
     */
    @Query("SELECT p FROM ChatProject p WHERE LOWER(p.titulo) LIKE LOWER(CONCAT('%', :titulo, '%'))")
    List<ChatProject> findByTituloContainingIgnoreCase(String titulo);

    /**
     * Busca projetos de um usuário ordenados por ordem
     */
    @Query("SELECT p FROM ChatProject p WHERE p.user_uuid = :userUuid ORDER BY p.ordem ASC")
    List<ChatProject> findByUserIdOrderByOrdemAsc(UUID userUuid);

    /**
     * Busca projetos de um usuário ordenados por data de atualização (mais recente primeiro)
     */
    @Query("SELECT p FROM ChatProject p WHERE p.user_uuid = :userUuid ORDER BY p.updatedAt DESC")
    List<ChatProject> findByUserIdOrderByUpdatedAtDesc(@Param("userUuid") UUID userUuid);

    /**
     * Busca projetos de um usuário criados após uma data específica
     */
    @Query("SELECT p FROM ChatProject p WHERE p.user_uuid = :userUuid AND p.createdAt >= :fromDate ORDER BY p.createdAt DESC")
    List<ChatProject> findRecentProjects(@Param("userUuid") UUID userUuid, @Param("fromDate") java.time.LocalDateTime fromDate);

    /**
     * Busca projetos que usam uma biblioteca privativa específica
     */
    @Query("SELECT p FROM ChatProject p WHERE p.biblioteca_privativa = :bibliotecaUuid")
    List<ChatProject> findByPrivateLibrary(@Param("bibliotecaUuid") UUID bibliotecaUuid);

    /**
     * Busca projeto por usuário e título exato
     */
    @Query("SELECT p FROM ChatProject p WHERE p.user_uuid = :userUuid AND LOWER(p.titulo) = LOWER(:titulo)")
    Optional<ChatProject> findByUserUuidAndTitulo(UUID userUuid, String titulo);

    /**
     * Verifica se existe projeto com título específico para um usuário
     */
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM ChatProject p WHERE p.user_uuid = :userUuid AND LOWER(p.titulo) = LOWER(:titulo)")
    boolean existsByUserUuidAndTitulo(UUID userUuid, String titulo);

    /**
     * Conta quantos projetos um usuário possui
     */
    @Query("SELECT COUNT(p) FROM ChatProject p WHERE p.user_uuid = :userUuid")
    long countByUserUuid(UUID userUuid);

    /**
     * Busca projetos de um usuário com descrição preenchida
     */
    @Query("SELECT p FROM ChatProject p WHERE p.user_uuid = :userUuid AND p.descricao IS NOT NULL AND p.descricao != '' ORDER BY p.updatedAt DESC")
    List<ChatProject> findProjectsWithDescription(@Param("userUuid") UUID userUuid);

    /**
     * Busca projetos de um usuário sem descrição
     */
    @Query("SELECT p FROM ChatProject p WHERE p.user_uuid = :userUuid AND (p.descricao IS NULL OR p.descricao = '') ORDER BY p.updatedAt DESC")
    List<ChatProject> findProjectsWithoutDescription(@Param("userUuid") UUID userUuid);

    /**
     * Busca os N projetos mais recentes de um usuário
     */
    @Query("SELECT p FROM ChatProject p WHERE p.user_uuid = :userUuid ORDER BY p.updatedAt DESC LIMIT :limit")
    List<ChatProject> findTopNRecentProjects(@Param("userUuid") UUID userUuid, @Param("limit") int limit);

    /**
     * Busca a próxima ordem disponível para projetos de um usuário
     */
    @Query("SELECT COALESCE(MAX(p.ordem), 0) + 1 FROM ChatProject p WHERE p.user_uuid = :userUuid")
    Integer findNextOrdem(@Param("userUuid") UUID userUuid);

    /**
     * Busca projetos de um usuário com ordem específica
     */
    @Query("SELECT p FROM ChatProject p WHERE p.user_uuid = :userUuid AND p.ordem = :ordem")
    Optional<ChatProject> findByUserUuidAndOrdem(UUID userUuid, Integer ordem);

    /**
     * Busca projetos de um usuário em intervalo de ordens
     */
    @Query("SELECT p FROM ChatProject p WHERE p.user_uuid = :userUuid AND p.ordem BETWEEN :fromOrdem AND :toOrdem ORDER BY p.ordem ASC")
    List<ChatProject> findProjectsBetweenOrdem(@Param("userUuid") UUID userUuid,
                                            @Param("fromOrdem") Integer fromOrdem,
                                            @Param("toOrdem") Integer toOrdem);

    /**
     * Remove todos os projetos de um usuário
     */
    @Query("DELETE FROM ChatProject p WHERE p.user_uuid = :userUuid")
    void deleteByUserUuid(UUID userUuid);

    /**
     * Remove todos os projetos que usam uma biblioteca privativa específica
     */
    @Query("DELETE FROM ChatProject p WHERE p.biblioteca_privativa = :bibliotecaPrivativa")
    void deleteByBibliotecaPrivativa(UUID bibliotecaPrivativa);

    /**
     * Busca projeto por usuário e título exato
     */
    @Query("SELECT p FROM ChatProject p WHERE p.user_uuid = :userId AND LOWER(p.titulo) = LOWER(:titulo)")
    List<ChatProject> findByUserIdAndTitulo(UUID userId, String titulo);
    
    /**
     * Busca projetos de um usuário com título contendo texto parcial (case-insensitive)
     */	
    @Query("SELECT p FROM ChatProject p WHERE p.user_uuid = :userId AND LOWER(p.titulo) LIKE LOWER(CONCAT('%', :titulo, '%'))")	
    List<ChatProject> findByUserIdAndContainsTitulo(UUID userId, String titulo);


    /**
     * Conta quantos projetos um usuário possui por userId (Integer).
     * Usa Join com User para conversão de Integer para UUID.
     */    
    @Query("SELECT COUNT(p) FROM ChatProject p JOIN User u ON p.user_uuid = u.uuid WHERE u.id = :userId") 
    long countByUserId(Integer userId);
    
    
    /**
     * Conta quantos projetos um usuário possui por userId (Integer)
     */
    @Query("SELECT COUNT(p) FROM ChatProject p WHERE p.user_uuid = :userId")
    long countByUserUuid(Integer userUuid);
    
}
