package bor.tools.simplerag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for ChatProject with associated chats (from MetaProject).
 * Used by GET /api/v1/projects/{uuid}/with-chats
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatProjectWithChatsDTO {

    /**
     * ChatProject information
     */
    private ChatProjectDTO project;

    /**
     * Chats associated with this project
     */
    private List<ChatDTO> chats;

    /**
     * Total chat count
     */
    private Integer chatCount;
    
	// campos declarados
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;    
    private LocalDateTime deletedAt;

    /**
     * Get total chat count
     * @return
     */
    public Integer getChatCount() {
	chatCount = chats != null ? chats.size() : 0;
        return chatCount;
    }

    /**
     * Get list of chat IDs
     */
    public List<UUID> getChatIds() {
        return chats != null
                ? chats.stream().map(ChatDTO::getId).collect(Collectors.toList())
                : List.of();
    }
}
