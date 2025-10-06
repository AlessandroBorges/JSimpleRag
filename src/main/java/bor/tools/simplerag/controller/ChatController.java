package bor.tools.simplerag.controller;

import bor.tools.simplerag.dto.*;
import bor.tools.simplerag.entity.Chat;
import bor.tools.simplerag.entity.ChatMessage;
import bor.tools.simplerag.service.ChatMessageService;
import bor.tools.simplerag.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST Controller for Chat and ChatMessage management.
 * Provides CRUD operations for chats and message management.
 */
@RestController
@RequestMapping("/api/v1/chats")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Chats", description = "Chat and message management")
public class ChatController {

    private final ChatService chatService;
    private final ChatMessageService chatMessageService;

    /**
     * Create or update chat
     */
    @PostMapping
    @Operation(summary = "Create or update chat",
               description = "Saves chat with client UUID validation")
    public ResponseEntity<ChatDTO> save(@Valid @RequestBody ChatDTO dto) {
        log.info("Saving chat: {}", dto.getTitulo());

        try {
            // Convert DTO to Entity
            Chat chat = toEntity(dto);

            // Save
            Chat saved = chatService.save(chat);

            // Convert back to DTO
            ChatDTO response = ChatDTO.from(saved);

            log.info("Chat saved: id={}", saved.getId());

            return ResponseEntity.status(dto.getId() == null ? HttpStatus.CREATED : HttpStatus.OK)
                    .body(response);

        } catch (IllegalArgumentException e) {
            log.error("Validation error saving chat: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error saving chat: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao salvar chat: " + e.getMessage(), e);
        }
    }

    /**
     * Delete chat (soft or hard delete)
     * Hard delete also removes all messages
     */
    @DeleteMapping("/{uuid}")
    @Operation(summary = "Delete chat (soft or hard)",
               description = "Soft delete sets deletedAt, hard delete removes chat and all messages")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid,
                                       @RequestParam(defaultValue = "false") boolean hard) {
        log.info("Deleting chat: uuid={}, hard={}", uuid, hard);

        try {
            Chat chat = chatService.findById(uuid)
                    .orElseThrow(() -> new IllegalArgumentException("Chat não encontrado: " + uuid));

            chatService.delete(chat, hard);

            log.info("Chat deleted: uuid={}, hard={}", uuid, hard);

            return ResponseEntity.noContent().build();

        } catch (IllegalArgumentException e) {
            log.error("Chat not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error deleting chat: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao deletar chat: " + e.getMessage(), e);
        }
    }

    /**
     * Get chat by UUID
     */
    @GetMapping("/{uuid}")
    @Operation(summary = "Get chat by UUID")
    public ResponseEntity<ChatDTO> findById(@PathVariable UUID uuid) {
        log.debug("Finding chat by UUID: {}", uuid);

        Chat chat = chatService.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Chat não encontrado: " + uuid));

        return ResponseEntity.ok(ChatDTO.from(chat));
    }

    /**
     * Get chat with messages
     */
    @GetMapping("/{uuid}/with-messages")
    @Operation(summary = "Get chat with messages",
               description = "Returns chat with all messages ordered by ordem")
    public ResponseEntity<ChatWithMessagesDTO> getWithMessages(@PathVariable UUID uuid) {
        log.debug("Finding chat with messages: uuid={}", uuid);

        try {
            ChatService.ChatWithMessages chatWithMessages = chatService.loadChatWithMessages(uuid)
                    .orElseThrow(() -> new IllegalArgumentException("Chat não encontrado: " + uuid));

            // Convert to DTO
            ChatDTO chatDTO = ChatDTO.from(chatWithMessages.getChat());

            List<ChatMessageDTO> messageDTOs = chatWithMessages.getMessages().stream()
                    .map(ChatMessageDTO::from)
                    .collect(Collectors.toList());

            ChatWithMessagesDTO response = ChatWithMessagesDTO.builder()
                    .chat(chatDTO)
                    .messages(messageDTOs)
                    .build();

            log.debug("Chat with messages found: {} messages", response.getMessageCount());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Chat not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error loading chat with messages: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao carregar chat com mensagens: " + e.getMessage(), e);
        }
    }

    /**
     * Create new message in chat
     */
    @PostMapping("/{uuid}/messages")
    @Operation(summary = "Create new message in chat",
               description = "Creates message with auto-increment ordem")
    public ResponseEntity<ChatMessageDTO> createMessage(@PathVariable UUID uuid,
                                                         @Valid @RequestBody CreateMessageRequest request) {
        log.info("Creating message in chat: {}", uuid);

        try {
            // Validate chat exists
            chatService.findById(uuid)
                    .orElseThrow(() -> new IllegalArgumentException("Chat não encontrado: " + uuid));

            // Create message
            ChatMessage message = chatMessageService.createMessage(
                    uuid,
                    request.getMensagem(),
                    request.getResponse()
            );

            log.info("Message created: id={}, ordem={}", message.getId(), message.getOrdem());

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ChatMessageDTO.from(message));

        } catch (IllegalArgumentException e) {
            log.error("Validation error creating message: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error creating message: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao criar mensagem: " + e.getMessage(), e);
        }
    }

    /**
     * Update message response
     */
    @PutMapping("/{chatId}/messages/{messageId}/response")
    @Operation(summary = "Update message response",
               description = "Updates the AI response for a message")
    public ResponseEntity<ChatMessageDTO> updateResponse(@PathVariable UUID chatId,
                                                          @PathVariable UUID messageId,
                                                          @Valid @RequestBody UpdateResponseRequest request) {
        log.info("Updating response for message: {}", messageId);

        try {
            // Validate chat exists
            chatService.findById(chatId)
                    .orElseThrow(() -> new IllegalArgumentException("Chat não encontrado: " + chatId));

            // Update response
            ChatMessage updated = chatMessageService.updateResponse(messageId, request.getResponse())
                    .orElseThrow(() -> new IllegalArgumentException("Mensagem não encontrada: " + messageId));

            log.info("Message response updated: id={}", messageId);

            return ResponseEntity.ok(ChatMessageDTO.from(updated));

        } catch (IllegalArgumentException e) {
            log.error("Error updating message response: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error updating message response: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao atualizar resposta: " + e.getMessage(), e);
        }
    }

    /**
     * Get user chats ordered by update date
     */
    @GetMapping("/by-client/{clientUuid}")
    @Operation(summary = "Get user chats ordered by update date")
    public ResponseEntity<List<ChatDTO>> getUserChats(@PathVariable UUID clientUuid) {
        log.debug("Finding chats for client: {}", clientUuid);

        List<Chat> chats = chatService.loadUserChats(clientUuid);

        List<ChatDTO> response = chats.stream()
                .map(ChatDTO::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get recent chats for user
     */
    @GetMapping("/by-client/{clientUuid}/recent")
    @Operation(summary = "Get recent chats for user",
               description = "Returns most recent chats (default limit: 10)")
    public ResponseEntity<List<ChatDTO>> getRecentChats(@PathVariable UUID clientUuid,
                                                         @RequestParam(defaultValue = "10") int limit) {
        log.debug("Finding recent chats for client: {} (limit={})", clientUuid, limit);

        List<Chat> chats = chatService.findRecentChats(clientUuid, limit);

        List<ChatDTO> response = chats.stream()
                .map(ChatDTO::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get conversation summary
     */
    @GetMapping("/{uuid}/summary")
    @Operation(summary = "Get conversation summary",
               description = "Returns statistics about the conversation")
    public ResponseEntity<ChatMessageService.ConversationSummary> getConversationSummary(@PathVariable UUID uuid) {
        log.debug("Getting conversation summary for chat: {}", uuid);

        try {
            // Validate chat exists
            chatService.findById(uuid)
                    .orElseThrow(() -> new IllegalArgumentException("Chat não encontrado: " + uuid));

            ChatMessageService.ConversationSummary summary = chatMessageService.getConversationSummary(uuid);

            return ResponseEntity.ok(summary);

        } catch (IllegalArgumentException e) {
            log.error("Chat not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error getting conversation summary: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao obter resumo da conversa: " + e.getMessage(), e);
        }
    }

    /**
     * Convert DTO to Entity
     */
    private Chat toEntity(ChatDTO dto) {
        Chat chat = new Chat();
        chat.setId(dto.getId());
        chat.setClient_uuid(dto.getClientUuid());
        chat.setTitulo(dto.getTitulo());
        chat.setResumo(dto.getResumo());
        chat.setBiblioteca_privativa(dto.getBibliotecaPrivativa());
        return chat;
    }
}
