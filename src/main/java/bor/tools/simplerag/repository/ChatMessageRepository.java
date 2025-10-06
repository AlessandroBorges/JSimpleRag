package bor.tools.simplerag.repository;

import bor.tools.simplerag.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório JPA para a entidade ChatMessage.
 *
 * Provides standard CRUD operations and custom queries for ChatMessage entities.
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /**
     * Busca todas as mensagens de um chat específico
     */
    List<ChatMessage> findByChatId(UUID chatId);

    /**
     * Busca todas as mensagens de um chat ordenadas por ordem
     */
    List<ChatMessage> findByChatIdOrderByOrdemAsc(UUID chatId);

    /**
     * Busca todas as mensagens de um chat ordenadas por ordem descendente
     */
    List<ChatMessage> findByChatIdOrderByOrdemDesc(UUID chatId);

    /**
     * Busca mensagem por chat e ordem
     */
    Optional<ChatMessage> findByChatIdAndOrdem(UUID chatId, Integer ordem);

    /**
     * Busca mensagens que contêm texto específico (case-insensitive)
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chatId AND " +
           "(LOWER(m.mensagem) LIKE LOWER(CONCAT('%', :searchText, '%')) OR " +
           "LOWER(m.response) LIKE LOWER(CONCAT('%', :searchText, '%')))")
    List<ChatMessage> searchInChatMessages(@Param("chatId") UUID chatId, @Param("searchText") String searchText);

    /**
     * Busca a última mensagem de um chat
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chatId ORDER BY m.ordem DESC LIMIT 1")
    Optional<ChatMessage> findLastMessage(@Param("chatId") UUID chatId);

    /**
     * Busca a primeira mensagem de um chat
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chatId ORDER BY m.ordem ASC LIMIT 1")
    Optional<ChatMessage> findFirstMessage(@Param("chatId") UUID chatId);

    /**
     * Busca mensagens sem resposta
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chatId AND (m.response IS NULL OR m.response = '')")
    List<ChatMessage> findMessagesWithoutResponse(@Param("chatId") UUID chatId);

    /**
     * Busca mensagens com resposta
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chatId AND m.response IS NOT NULL AND m.response != ''")
    List<ChatMessage> findMessagesWithResponse(@Param("chatId") UUID chatId);

    /**
     * Conta quantas mensagens um chat possui
     */
    long countByChatId(UUID chatId);

    /**
     * Busca o próximo número de ordem disponível para um chat
     */
    @Query("SELECT COALESCE(MAX(m.ordem), 0) + 1 FROM ChatMessage m WHERE m.chat_id = :chatId")
    Integer findNextOrdem(@Param("chatId") UUID chatId);

    /**
     * Busca mensagens a partir de uma ordem específica
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chatId AND m.ordem >= :fromOrdem ORDER BY m.ordem ASC")
    List<ChatMessage> findMessagesFromOrdem(@Param("chatId") UUID chatId, @Param("fromOrdem") Integer fromOrdem);

    /**
     * Busca mensagens até uma ordem específica
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chatId AND m.ordem <= :toOrdem ORDER BY m.ordem ASC")
    List<ChatMessage> findMessagesUntilOrdem(@Param("chatId") UUID chatId, @Param("toOrdem") Integer toOrdem);

    /**
     * Busca mensagens em um intervalo de ordens
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chatId AND m.ordem BETWEEN :fromOrdem AND :toOrdem ORDER BY m.ordem ASC")
    List<ChatMessage> findMessagesBetweenOrdem(@Param("chatId") UUID chatId,
                                                @Param("fromOrdem") Integer fromOrdem,
                                                @Param("toOrdem") Integer toOrdem);

    /**
     * Verifica se existe mensagem com ordem específica em um chat
     */
    boolean existsByChatIdAndOrdem(UUID chatId, Integer ordem);

    /**
     * Remove todas as mensagens de um chat
     */
    void deleteByChatId(UUID chatId);

    /**
     * Remove mensagens a partir de uma ordem específica
     */
    @Query("DELETE FROM ChatMessage m WHERE m.chat_id = :chatId AND m.ordem >= :fromOrdem")
    void deleteMessagesFromOrdem(@Param("chatId") UUID chatId, @Param("fromOrdem") Integer fromOrdem);
}
