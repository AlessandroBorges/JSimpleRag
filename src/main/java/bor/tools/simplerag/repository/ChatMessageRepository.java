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
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chat_id")
    List<ChatMessage> findByChatId(UUID chat_id);

    /**
     * Busca todas as mensagens de um chat ordenadas por ordem
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chat_id ORDER BY m.ordem ASC")
    List<ChatMessage> findByChatIdOrderByOrdemAsc(UUID chat_id);

    /**
     * Busca todas as mensagens de um chat ordenadas por ordem descendente
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chat_id ORDER BY m.ordem DESC")
    List<ChatMessage> findByChatIdOrderByOrdemDesc(UUID chat_id);

    /**
     * Busca mensagem por chat e ordem
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chat_id AND m.ordem = :ordem")	
    Optional<ChatMessage> findByChatIdAndOrdem(UUID chat_id, Integer ordem);

    /**
     * Busca mensagens que contêm texto específico (case-insensitive)
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chat_id AND " +
           "(LOWER(m.mensagem) LIKE LOWER(CONCAT('%', :searchText, '%')) OR " +
           "LOWER(m.response) LIKE LOWER(CONCAT('%', :searchText, '%')))")
    List<ChatMessage> searchInChatMessages(@Param("chat_id") UUID chat_id, @Param("searchText") String searchText);

    /**
     * Busca a última mensagem de um chat
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chat_id ORDER BY m.ordem DESC LIMIT 1")
    Optional<ChatMessage> findLastMessage(@Param("chat_id") UUID chat_id);

    /**
     * Busca a primeira mensagem de um chat
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chat_id ORDER BY m.ordem ASC LIMIT 1")
    Optional<ChatMessage> findFirstMessage(@Param("chat_id") UUID chat_id);

    /**
     * Busca mensagens sem resposta
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chat_id AND (m.response IS NULL OR m.response = '')")
    List<ChatMessage> findMessagesWithoutResponse(@Param("chat_id") UUID chat_id);

    /**
     * Busca mensagens com resposta
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chat_id AND m.response IS NOT NULL AND m.response != ''")
    List<ChatMessage> findMessagesWithResponse(@Param("chat_id") UUID chat_id);

    /**
     * Conta quantas mensagens um chat possui
     */
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.chat_id = :chat_id")
    long countByChatId(UUID chat_id);

    /**
     * Busca o próximo número de ordem disponível para um chat
     */
    @Query("SELECT COALESCE(MAX(m.ordem), 0) + 1 FROM ChatMessage m WHERE m.chat_id = :chat_id")
    Integer findNextOrdem(@Param("chat_id") UUID chat_id);

    /**
     * Busca mensagens a partir de uma ordem específica
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chat_id AND m.ordem >= :fromOrdem ORDER BY m.ordem ASC")
    List<ChatMessage> findMessagesFromOrdem(@Param("chat_id") UUID chat_id, @Param("fromOrdem") Integer fromOrdem);

    /**
     * Busca mensagens até uma ordem específica
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chat_id AND m.ordem <= :toOrdem ORDER BY m.ordem ASC")
    List<ChatMessage> findMessagesUntilOrdem(@Param("chat_id") UUID chat_id, @Param("toOrdem") Integer toOrdem);

    /**
     * Busca mensagens em um intervalo de ordens
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.chat_id = :chat_id AND m.ordem BETWEEN :fromOrdem AND :toOrdem ORDER BY m.ordem ASC")
    List<ChatMessage> findMessagesBetweenOrdem(@Param("chat_id") UUID chat_id,
                                                @Param("fromOrdem") Integer fromOrdem,
                                                @Param("toOrdem") Integer toOrdem);

    /**
     * Verifica se existe mensagem com ordem específica em um chat
     */ 
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM ChatMessage m WHERE m.chat_id = :chat_id AND m.ordem = :ordem")
    boolean existsByChatIdAndOrdem(UUID chat_id, Integer ordem);

    /**
     * Remove todas as mensagens de um chat
     */
    @Query("DELETE FROM ChatMessage m WHERE m.chat_id = :chat_id")	
    void deleteByChatId(UUID chat_id);

    /**
     * Remove mensagens a partir de uma ordem específica
     */
    @Query("DELETE FROM ChatMessage m WHERE m.chat_id = :chat_id AND m.ordem >= :fromOrdem")
    void deleteMessagesFromOrdem(@Param("chat_id") UUID chat_id, @Param("fromOrdem") Integer fromOrdem);
}
