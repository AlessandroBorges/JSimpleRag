package bor.tools.simplerag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for Chat with messages.
 * Used by GET /api/v1/chats/{uuid}/with-messages
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
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
	messageCount = messages != null ? messages.size() : 0;
        return messageCount;
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
