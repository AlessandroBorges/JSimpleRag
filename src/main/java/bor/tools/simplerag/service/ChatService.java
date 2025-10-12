package bor.tools.simplerag.service;

import bor.tools.simplerag.entity.Chat;
import bor.tools.simplerag.entity.ChatMessage;
import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.repository.ChatMessageRepository;
import bor.tools.simplerag.repository.ChatRepository;
import bor.tools.simplerag.repository.LibraryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for Chat entity operations.
 * Handles chat management, message loading, and library associations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatRepository chatRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final LibraryRepository libraryRepository;

    /**
     * Save (create or update) chat entity
     * @param chat - chat to save
     * @return saved chat
     */
    @Transactional
    public Chat save(Chat chat) {
        log.debug("Saving chat: {}", chat.getTitulo());

        // Validate client UUID
        if (chat.getUser_uuid() == null) {
            throw new IllegalArgumentException("Client UUID é obrigatório");
        }

        // Generate UUID if not present (handled by @PrePersist but ensuring)
        if (chat.getId() == null) {
            chat.setId(UUID.randomUUID());
        }

        return chatRepository.save(chat);
    }

    /**
     * Delete chat (soft or hard delete)
     * @param chat - chat to delete
     * @param isHardDelete - true for physical delete, false for soft delete
     */
    @Transactional
    public void delete(Chat chat, boolean isHardDelete) {
        log.debug("Deleting chat: {} (hard={})", chat.getTitulo(), isHardDelete);

        if (isHardDelete) {
            // Remove all chat messages first
            chatMessageRepository.deleteByChatId(chat.getId());
            chatRepository.delete(chat);
            log.info("Chat hard deleted: {}", chat.getTitulo());
        } else {
            chat.setDeletedAt(LocalDateTime.now());
            chatRepository.save(chat);
            log.info("Chat soft deleted: {}", chat.getTitulo());
        }
    }

    /**
     * Find chat by ID
     * @param id - chat UUID
     * @return Optional chat
     */
    public Optional<Chat> findById(UUID id) {
        return chatRepository.findById(id);
    }

    /**
     * Load user chats ordered by update date
     * @param clientUuid - client/user UUID
     * @return List of chats
     */
    public List<Chat> loadUserChats(UUID clientUuid) {
        return chatRepository.findByClientUuidOrderByUpdatedAtDesc(clientUuid);
    }

    /**
     * Load chat with all messages ordered
     * @param chatId - chat UUID
     * @return ChatWithMessages wrapper
     */
    @Transactional(readOnly = true)
    public Optional<ChatWithMessages> loadChatWithMessages(UUID chatId) {
        Optional<Chat> chatOpt = findById(chatId);

        if (chatOpt.isEmpty()) {
            return Optional.empty();
        }

        Chat chat = chatOpt.get();
        List<ChatMessage> messages = chatMessageRepository.findByChatIdOrderByOrdemAsc(chatId);

        return Optional.of(new ChatWithMessages(chat, messages));
    }

    /**
     * Load chat messages in a range
     * @param chatId - chat UUID
     * @param fromOrdem - start order (inclusive)
     * @param toOrdem - end order (inclusive)
     * @return List of messages
     */
    public List<ChatMessage> loadChatMessages(UUID chatId, Integer fromOrdem, Integer toOrdem) {
        if (fromOrdem == null && toOrdem == null) {
            return chatMessageRepository.findByChatIdOrderByOrdemAsc(chatId);
        } else if (fromOrdem != null && toOrdem != null) {
            return chatMessageRepository.findMessagesBetweenOrdem(chatId, fromOrdem, toOrdem);
        } else if (fromOrdem != null) {
            return chatMessageRepository.findMessagesFromOrdem(chatId, fromOrdem);
        } else {
            return chatMessageRepository.findMessagesUntilOrdem(chatId, toOrdem);
        }
    }

    /**
     * Load chat's library if bibliotecaPrivativa is set
     * @param chatId - chat UUID
     * @return Optional library
     */
    @Transactional(readOnly = true)
    public Optional<Library> loadChatLibrary(UUID chatId) {
        Optional<Chat> chatOpt = findById(chatId);

        if (chatOpt.isEmpty() || chatOpt.get().getBiblioteca_privativa() == null) {
            return Optional.empty();
        }

        UUID libraryUuid = chatOpt.get().getBiblioteca_privativa();

        return libraryRepository.findAll().stream()
                .filter(lib -> libraryUuid.equals(lib.getUuid()))
                .findFirst();
    }

    /**
     * Find recent chats for user
     * @param clientUuid - client/user UUID
     * @param limit - number of chats to return
     * @return List of recent chats
     */
    public List<Chat> findRecentChats(UUID clientUuid, int limit) {
        return chatRepository.findTopNRecentChats(clientUuid, limit);
    }

    /**
     * Generate summary for chat based on messages
     * @param chatId - chat UUID
     * @return updated chat with generated summary
     */
    @Transactional
    public Optional<Chat> generateResumo(UUID chatId) {
        Optional<Chat> chatOpt = findById(chatId);

        if (chatOpt.isEmpty()) {
            return Optional.empty();
        }

        Chat chat = chatOpt.get();
        List<ChatMessage> messages = chatMessageRepository.findByChatIdOrderByOrdemAsc(chatId);

        if (messages.isEmpty()) {
            return Optional.of(chat);
        }

        // TODO: Implement LLM-based summary generation
        // For now, use a simple approach: first message + count
        String firstMessage = messages.get(0).getMensagem();
        String summary = String.format("%s (%d mensagens)",
                firstMessage.length() > 100 ? firstMessage.substring(0, 100) + "..." : firstMessage,
                messages.size());

        chat.setResumo(summary);
        return Optional.of(save(chat));
    }

    /**
     * Count user's chats
     * @param clientUuid - client/user UUID
     * @return chat count
     */
    public long countUserChats(UUID clientUuid) {
        return chatRepository.countByClientUuid(clientUuid);
    }

    /**
     * Search chats by title
     * @param clientUuid - client/user UUID
     * @param titulo - title search text
     * @return List of matching chats
     */
    public List<Chat> searchByTitulo(UUID clientUuid, String titulo) {
        return chatRepository.findByClientUuid(clientUuid).stream()
                .filter(chat -> chat.getTitulo() != null &&
                        chat.getTitulo().toLowerCase().contains(titulo.toLowerCase()))
                .toList();
    }

    /**
     * Check if chat exists
     * @param clientUuid - client/user UUID
     * @param titulo - chat title
     * @return true if exists
     */
    public boolean existsByClientUuidAndTitulo(UUID clientUuid, String titulo) {
        return chatRepository.existsByClientUuidAndTitulo(clientUuid, titulo);
    }

    /**
     * DTO class to return chat with messages
     */
    public static class ChatWithMessages {
        private final Chat chat;
        private final List<ChatMessage> messages;

        public ChatWithMessages(Chat chat, List<ChatMessage> messages) {
            this.chat = chat;
            this.messages = messages;
        }

        public Chat getChat() {
            return chat;
        }

        public List<ChatMessage> getMessages() {
            return messages;
        }

        public int getMessageCount() {
            return messages.size();
        }

        public Optional<ChatMessage> getLastMessage() {
            return messages.isEmpty()
                    ? Optional.empty()
                    : Optional.of(messages.get(messages.size() - 1));
        }

        public Optional<ChatMessage> getFirstMessage() {
            return messages.isEmpty()
                    ? Optional.empty()
                    : Optional.of(messages.get(0));
        }
    }
}
