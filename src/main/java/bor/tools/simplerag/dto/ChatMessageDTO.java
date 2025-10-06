package bor.tools.simplerag.dto;

import java.util.UUID;

import bor.tools.simplerag.entity.ChatMessage;
import bor.tools.simplerag.entity.Metadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for ChatMessage entity.
 *
 * Represents a complete interaction in a chat session, containing both
 * the user's input (mensagem) and the AI's response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDTO {

    /**
     * Unique identifier for the message
     */
    private UUID id;

    /**
     * UUID of the chat this message belongs to
     */
    private UUID chatId;

    /**
     * Order/sequence number of the message in the chat
     */
    private Integer ordem;

    /**
     * Arbitrary metadata stored as JSONB
     * May include language, model, temperature, top_p, stats, documents
     */
    private Metadata metadata;

    /**
     * The user's message in the chat
     * Can be text or images in base64 format
     */
    private String mensagem;

    /**
     * The AI's response to the user's message
     * Can be text or images in base64 format
     */
    private String response;

    /**
     * Create DTO from entity
     * @param src - source entity
     * @return DTO instance
     */
    public static ChatMessageDTO from(ChatMessage src) {
        if (src == null) {
            return null;
        }
        return ChatMessageDTO.builder()
                .id(src.getId())
                .chatId(src.getChat_id())
                .ordem(src.getOrdem())
                .metadata(src.getMetadata())
                .mensagem(src.getMensagem())
                .response(src.getResponse())
                .build();
    }

    /**
     * Convert DTO to entity
     * @return ChatMessage entity
     */
    public ChatMessage toEntity() {
        ChatMessage entity = new ChatMessage();
        entity.setId(this.id);
        entity.setChat_id(this.chatId);
        entity.setOrdem(this.ordem);
        entity.setMetadata(this.metadata);
        entity.setMensagem(this.mensagem);
        entity.setResponse(this.response);
        return entity;
    }

    /**
     * Validates if required fields are present
     */
    public boolean isValid() {
        return chatId != null
                && ordem != null
                && mensagem != null && !mensagem.trim().isEmpty();
    }

    /**
     * Check if message has a response
     */
    public boolean hasResponse() {
        return response != null && !response.trim().isEmpty();
    }

    /**
     * Check if message has metadata
     */
    public boolean hasMetadata() {
        return metadata != null && !metadata.isEmpty();
    }

    /**
     * Check if message contains images (simple heuristic)
     */
    public boolean containsImages() {
        return (mensagem != null && mensagem.contains("data:image"))
                || (response != null && response.contains("data:image"));
    }
}
