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
    List<Chat> findByClientUuid(UUID clientUuid);

    /**
     * Busca chats por UUID da biblioteca privativa
     */
    List<Chat> findByBibliotecaPrivativa(UUID bibliotecaPrivativa);

    /**
     * Busca chats por título (contendo texto, case-insensitive)
     */
    List<Chat> findByTituloContainingIgnoreCase(String titulo);

    /**
     * Busca chats de um cliente ordenados por data de atualização (mais recente primeiro)
     */
    @Query("SELECT c FROM Chat c WHERE c.client_uuid = :clientUuid ORDER BY c.updatedAt DESC")
    List<Chat> findByClientUuidOrderByUpdatedAtDesc(@Param("clientUuid") UUID clientUuid);

    /**
     * Busca chats de um cliente criados após uma data específica
     */
    @Query("SELECT c FROM Chat c WHERE c.client_uuid = :clientUuid AND c.createdAt >= :fromDate ORDER BY c.createdAt DESC")
    List<Chat> findRecentChats(@Param("clientUuid") UUID clientUuid, @Param("fromDate") java.time.LocalDateTime fromDate);

    /**
     * Busca chats que usam uma biblioteca privativa específica
     */
    @Query("SELECT c FROM Chat c WHERE c.biblioteca_privativa = :bibliotecaUuid")
    List<Chat> findByPrivateLibrary(@Param("bibliotecaUuid") UUID bibliotecaUuid);

    /**
     * Verifica se existe chat com título específico para um cliente
     */
    boolean existsByClientUuidAndTitulo(UUID clientUuid, String titulo);

    /**
     * Conta quantos chats um cliente possui
     */
    long countByClientUuid(UUID clientUuid);

    /**
     * Busca chats de um cliente com resumo preenchido
     */
    @Query("SELECT c FROM Chat c WHERE c.client_uuid = :clientUuid AND c.resumo IS NOT NULL ORDER BY c.updatedAt DESC")
    List<Chat> findChatsWithSummary(@Param("clientUuid") UUID clientUuid);

    /**
     * Busca os N chats mais recentes de um cliente
     */
    @Query("SELECT c FROM Chat c WHERE c.client_uuid = :clientUuid ORDER BY c.updatedAt DESC LIMIT :limit")
    List<Chat> findTopNRecentChats(@Param("clientUuid") UUID clientUuid, @Param("limit") int limit);

    /**
     * Remove todos os chats de um cliente
     */
    void deleteByClientUuid(UUID clientUuid);

    /**
     * Remove todos os chats que usam uma biblioteca privativa específica
     */
    void deleteByBibliotecaPrivativa(UUID bibliotecaPrivativa);
}
