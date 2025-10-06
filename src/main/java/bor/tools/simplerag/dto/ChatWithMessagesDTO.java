package bor.tools.simplerag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for Chat with messages.
 * Used by GET /api/v1/chats/{uuid}/with-messages
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatWithMessagesDTO {

    /**
     * Chat information
     */
    private ChatDTO chat;

    /**
     * Messages in this chat (ordered by ordem)
     */
    private List<ChatMessageDTO> messages;

    /**
     * Total message count
     */
    private Integer messageCount;

    public Integer getMessageCount() {
        return messages != null ? messages.size() : 0;
    }

    /**
     * Get first message
     */
    public ChatMessageDTO getFirstMessage() {
        return (messages != null && !messages.isEmpty()) ? messages.get(0) : null;
    }

    /**
     * Get last message
     */
    public ChatMessageDTO getLastMessage() {
        return (messages != null && !messages.isEmpty())
                ? messages.get(messages.size() - 1)
                : null;
    }
}
