package bor.tools.simplerag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * DTO for Project with associated chats (from MetaProject).
 * Used by GET /api/v1/projects/{uuid}/with-chats
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectWithChatsDTO {

    /**
     * Project information
     */
    private ProjectDTO project;

    /**
     * Chats associated with this project
     */
    private List<ChatDTO> chats;

    /**
     * Total chat count
     */
    private Integer chatCount;

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
