package bor.tools.simplerag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating a message response.
 * Used by PUT /api/v1/chats/{chatId}/messages/{messageId}/response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateResponseRequest {

    /**
     * AI response text
     */
    @NotBlank(message = "Response text is required")
    private String response;
}
