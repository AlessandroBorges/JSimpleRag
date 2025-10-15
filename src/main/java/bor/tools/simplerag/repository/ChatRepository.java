package bor.tools.simplerag.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import bor.tools.simplerag.entity.Chat;

/**
 * Repositório JPA para a entidade Chat.
 *
 * Provides standard CRUD operations and custom queries for Chat entities.
 * 
 */
@Repository
public interface ChatRepository extends JpaRepository<Chat, UUID> {

    /**
     * Busca todos os chats de um cliente/usuário
     */
    @Query("SELECT c FROM Chat c WHERE c.user_uuid = :userUuid")
    List<Chat> findByUserUuid(UUID userUuid);

    /**
     * Busca chats por UUID da biblioteca privativa
     */
    @Query("SELECT c FROM Chat c WHERE c.biblioteca_privativa = :bibliotecaPrivativa")
    List<Chat> findByBibliotecaPrivativa(UUID bibliotecaPrivativa);

    /**
     * Busca chats por título (contendo texto, case-insensitive)
     */
    List<Chat> findByTituloContainingIgnoreCase(String titulo);

    /**
     * Busca chats de um cliente ordenados por data de atualização (mais recente primeiro)
     */
    @Query("SELECT c FROM Chat c WHERE c.user_uuid = :userUuiid ORDER BY c.updatedAt DESC")
    List<Chat> findByUserUuidOrderByUpdatedAtDesc(@Param("userUuiid") UUID userUuiid);

    /**
     * Busca chats de um cliente criados após uma data específica
     */
    @Query("SELECT c FROM Chat c WHERE c.user_uuid = :userUuiid AND c.createdAt >= :fromDate ORDER BY c.createdAt DESC")
    List<Chat> findRecentChats(@Param("userUuid") UUID userUuid, @Param("fromDate") java.time.LocalDateTime fromDate);

    /**
     * Busca chats que usam uma biblioteca privativa específica
     */
    @Query("SELECT c FROM Chat c WHERE c.biblioteca_privativa = :bibliotecaUuid")
    List<Chat> findByPrivateLibrary(@Param("bibliotecaUuid") UUID bibliotecaUuid);

    /**
     * Verifica se existe chat com título específico para um cliente
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Chat c WHERE c.user_uuid = :userUuid AND LOWER(c.titulo) = LOWER(:titulo)")	
    boolean existsByUserUuidAndTitulo(UUID userUuid, String titulo);

    /**
     * Conta quantos chats um cliente possui
     */
    @Query("SELECT COUNT(c) FROM Chat c WHERE c.user_uuid = :userUuid")
    long countByUserUuid(UUID userUuid);

    /**
     * Busca chats de um cliente com resumo preenchido
     */
    @Query("SELECT c FROM Chat c WHERE c.user_uuid = :userUuid AND c.resumo IS NOT NULL ORDER BY c.updatedAt DESC")
    List<Chat> findChatsWithSummary(@Param("userUuid") UUID userUuid);

    /**
     * Busca os N chats mais recentes de um cliente
     */
    @Query("SELECT c FROM Chat c WHERE c.user_uuid = :userUuid ORDER BY c.updatedAt DESC LIMIT :limit")
    List<Chat> findTopNRecentChats(@Param("userUuid") UUID userUuid, @Param("limit") int limit);

    /**
     * Remove todos os chats de um cliente
     */
    @Query("DELETE FROM Chat c WHERE c.user_uuid = :userUuid")
    void deleteByUserUuid(UUID userUuid);

    /**
     * Remove todos os chats que usam uma biblioteca privativa específica
     */
    @Query("DELETE FROM Chat c WHERE c.biblioteca_privativa = :bibliotecaPrivativa")
    void deleteByBibliotecaPrivativa(UUID bibliotecaPrivativa);
}
