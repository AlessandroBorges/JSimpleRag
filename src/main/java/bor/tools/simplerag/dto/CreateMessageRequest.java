package bor.tools.simplerag.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new chat message.
 * Used by POST /api/v1/chats/{uuid}/messages
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateMessageRequest {

    /**
     * User message text (required)
     */
    @NotBlank(message = "Message text is required")
    private String mensagem;

    /**
     * AI response text (optional - can be set later)
     */
    private String response;
}
