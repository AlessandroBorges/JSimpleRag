package bor.tools.simplerag.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bor.tools.simplerag.entity.ChatMessage;
import bor.tools.simplerag.repository.ChatMessageRepository;
import bor.tools.simplerag.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for ChatMessage entity operations.
 * Handles message management, ordering, and history operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRepository chatRepository;

    /**
     * Save (create or update) chat message entity
     * @param chatMessage - message to save
     * @return saved message
     */
    @Transactional
    public ChatMessage save(ChatMessage chatMessage) {
        log.debug("Saving chat message for chat: {}", chatMessage.getChat_id());

        // Validate chat exists
        if (chatMessage.getChat_id() == null) {
            throw new IllegalArgumentException("Chat ID é obrigatório");
        }

        if (!chatRepository.existsById(chatMessage.getChat_id())) {
            throw new IllegalArgumentException("Chat não encontrado: " + chatMessage.getChat_id());
        }

        // Generate UUID if not present (handled by @PrePersist but ensuring)
        if (chatMessage.getId() == null) {
            chatMessage.setId(UUID.randomUUID());
        }

        // Set ordem if not present
        if (chatMessage.getOrdem() == null) {
            Integer nextOrdem = chatMessageRepository.findNextOrdem(chatMessage.getChat_id());
            chatMessage.setOrdem(nextOrdem);
        }

        // Validate ordem uniqueness
        if (chatMessage.getId() == null &&
                chatMessageRepository.existsByChatIdAndOrdem(chatMessage.getChat_id(), chatMessage.getOrdem())) {
            throw new IllegalArgumentException(
                    String.format("Ordem %d já existe para chat %s", chatMessage.getOrdem(), chatMessage.getChat_id())
            );
        }

        return chatMessageRepository.save(chatMessage);
    }

    /**
     * Delete chat message (soft or hard delete)
     * @param chatMessage - message to delete
     * @param isHardDelete - true for physical delete, false for soft delete
     */
    @Transactional
    public void delete(ChatMessage chatMessage, boolean isHardDelete) {
        log.debug("Deleting chat message: id={} (hard={})", chatMessage.getId(), isHardDelete);

        if (isHardDelete) {
            chatMessageRepository.delete(chatMessage);
            log.info("ChatMessage hard deleted: id={}", chatMessage.getId());
        } else {
            chatMessage.setDeletedAt(LocalDateTime.now());
            chatMessageRepository.save(chatMessage);
            log.info("ChatMessage soft deleted: id={}", chatMessage.getId());
        }
    }

    /**
     * Find message by ID
     * @param id - message UUID
     * @return Optional message
     */
    public Optional<ChatMessage> findById(UUID id) {
        return chatMessageRepository.findById(id);
    }

    /**
     * Load all messages for a chat ordered by ordem
     * @param chatId - chat UUID
     * @return List of messages
     */
    public List<ChatMessage> loadMessagesByChat(UUID chatId) {
        return chatMessageRepository.findByChatIdOrderByOrdemAsc(chatId);
    }

    /**
     * Load messages between ordem range
     * @param chatId - chat UUID
     * @param fromOrdem - start order (inclusive)
     * @param toOrdem - end order (inclusive)
     * @return List of messages
     */
    public List<ChatMessage> loadMessagesBetweenOrdem(UUID chatId, Integer fromOrdem, Integer toOrdem) {
        return chatMessageRepository.findMessagesBetweenOrdem(chatId, fromOrdem, toOrdem);
    }

    /**
     * Load last message of a chat
     * @param chatId - chat UUID
     * @return Optional last message
     */
    public Optional<ChatMessage> loadLastMessage(UUID chatId) {
        return chatMessageRepository.findLastMessage(chatId);
    }

    /**
     * Load first message of a chat
     * @param chatId - chat UUID
     * @return Optional first message
     */
    public Optional<ChatMessage> loadFirstMessage(UUID chatId) {
        return chatMessageRepository.findFirstMessage(chatId);
    }

    /**
     * Get next available ordem for chat messages
     * @param chatId - chat UUID
     * @return next ordem number
     */
    public Integer getNextOrdem(UUID chatId) {
        return chatMessageRepository.findNextOrdem(chatId);
    }

    /**
     * Delete messages from ordem onwards (truncate history)
     * @param chatId - chat UUID
     * @param fromOrdem - start order (inclusive)
     */
    @Transactional
    public void deleteMessagesFromOrdem(UUID chatId, Integer fromOrdem) {
        log.debug("Truncating chat history from ordem {} for chat {}", fromOrdem, chatId);
        chatMessageRepository.deleteMessagesFromOrdem(chatId, fromOrdem);
    }

    /**
     * Search messages containing text
     * @param chatId - chat UUID
     * @param searchText - text to search
     * @return List of matching messages
     */
    public List<ChatMessage> searchInMessages(UUID chatId, String searchText) {
        return chatMessageRepository.searchInChatMessages(chatId, searchText);
    }

    /**
     * Find messages without response
     * @param chatId - chat UUID
     * @return List of messages without response
     */
    public List<ChatMessage> findMessagesWithoutResponse(UUID chatId) {
        return chatMessageRepository.findMessagesWithoutResponse(chatId);
    }

    /**
     * Find messages with response
     * @param chatId - chat UUID
     * @return List of messages with response
     */
    public List<ChatMessage> findMessagesWithResponse(UUID chatId) {
        return chatMessageRepository.findMessagesWithResponse(chatId);
    }

    /**
     * Count messages in a chat
     * @param chatId - chat UUID
     * @return message count
     */
    public long countMessages(UUID chatId) {
        return chatMessageRepository.countByChatId(chatId);
    }

    /**
     * Find message by chat and ordem
     * @param chatId - chat UUID
     * @param ordem - message order
     * @return Optional message
     */
    public Optional<ChatMessage> findByChatIdAndOrdem(UUID chatId, Integer ordem) {
        return chatMessageRepository.findByChatIdAndOrdem(chatId, ordem);
    }

    /**
     * Create a new message with auto-increment ordem
     * @param chatId - chat UUID
     * @param mensagem - user message
     * @param response - AI response (can be null)
     * @return created message
     */
    @Transactional
    public ChatMessage createMessage(UUID chatId, String mensagem, String response) {
        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID());
        message.setChat_id(chatId);
        message.setMensagem(mensagem);
        message.setResponse(response);
        message.setOrdem(getNextOrdem(chatId));

        return save(message);
    }

    /**
     * Update message response
     * @param messageId - message UUID
     * @param response - AI response
     * @return updated message
     */
    @Transactional
    public Optional<ChatMessage> updateResponse(UUID messageId, String response) {
        Optional<ChatMessage> messageOpt = findById(messageId);

        if (messageOpt.isEmpty()) {
            return Optional.empty();
        }

        ChatMessage message = messageOpt.get();
        message.setResponse(response);
        return Optional.of(save(message));
    }

    /**
     * Get message context (N messages before and after)
     * @param chatId - chat UUID
     * @param ordem - target message order
     * @param contextSize - number of messages before and after
     * @return List of messages in context
     */
    public List<ChatMessage> getMessageContext(UUID chatId, Integer ordem, int contextSize) {
        int fromOrdem = Math.max(1, ordem - contextSize);
        int toOrdem = ordem + contextSize;

        return chatMessageRepository.findMessagesBetweenOrdem(chatId, fromOrdem, toOrdem);
    }

    /**
     * Check if message has images (simple heuristic)
     * @param message - chat message
     * @return true if contains images
     */
    public boolean containsImages(ChatMessage message) {
        return (message.getMensagem() != null && message.getMensagem().contains("data:image"))
                || (message.getResponse() != null && message.getResponse().contains("data:image"));
    }

    /**
     * Get conversation history summary
     * @param chatId - chat UUID
     * @return ConversationSummary object
     */
    @Transactional(readOnly = true)
    public ConversationSummary getConversationSummary(UUID chatId) {
        long totalMessages = chatMessageRepository.countByChatId(chatId);
        long messagesWithResponse = chatMessageRepository.findMessagesWithResponse(chatId).size();
        long messagesWithoutResponse = chatMessageRepository.findMessagesWithoutResponse(chatId).size();

        Optional<ChatMessage> firstMessage = chatMessageRepository.findFirstMessage(chatId);
        Optional<ChatMessage> lastMessage = chatMessageRepository.findLastMessage(chatId);

        return new ConversationSummary(
                chatId,
                totalMessages,
                messagesWithResponse,
                messagesWithoutResponse,
                firstMessage.orElse(null),
                lastMessage.orElse(null)
        );
    }

    /**
     * DTO class for conversation summary
     */
    public static class ConversationSummary {
        private final UUID chatId;
        private final long totalMessages;
        private final long messagesWithResponse;
        private final long messagesWithoutResponse;
        private final ChatMessage firstMessage;
        private final ChatMessage lastMessage;

        public ConversationSummary(UUID chatId, long totalMessages, long messagesWithResponse,
                                   long messagesWithoutResponse, ChatMessage firstMessage, ChatMessage lastMessage) {
            this.chatId = chatId;
            this.totalMessages = totalMessages;
            this.messagesWithResponse = messagesWithResponse;
            this.messagesWithoutResponse = messagesWithoutResponse;
            this.firstMessage = firstMessage;
            this.lastMessage = lastMessage;
        }

        public UUID getChatId() {
            return chatId;
        }

        public long getTotalMessages() {
            return totalMessages;
        }

        public long getMessagesWithResponse() {
            return messagesWithResponse;
        }

        public long getMessagesWithoutResponse() {
            return messagesWithoutResponse;
        }

        public ChatMessage getFirstMessage() {
            return firstMessage;
        }

        public ChatMessage getLastMessage() {
            return lastMessage;
        }

        public double getResponseRate() {
            return totalMessages > 0 ? (double) messagesWithResponse / totalMessages : 0.0;
        }
    }
}
