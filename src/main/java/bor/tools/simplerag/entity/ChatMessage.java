/**
 * 
 */
package bor.tools.simplerag.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.*;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Class to represent a chat message.<br>
 * A chat message is part of a chat session identified by chat_id.<br>
 * Each message has an order (ordem) to maintain the sequence of messages in the chat.<br>
 * The message can contain arbitrary metadata in JSONB format to customize processing.<br>
 * The message is a complete interation, and contains both the user's input (mensagem) 
 * and the AI's response (response).<br>
 * This allows for easy retrieval and display of the full conversation history.
 * 
 * 
 */
@Entity
@Table(name = "chat_message")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class ChatMessage extends Updatable{

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;
    
    /**
     * UUID of the client (user) who owns this chat.
     */
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID chat_id;
    
    @Column(name = "ordem")
    private Integer ordem;
    
    /**
     * Arbitrary metadata stored as JSONB.	
     * <UL>It may include keys like:
     * <LI> 'lingua' (language) for processing.
     * <LI> 'modelo' (model) to specify the AI model to use.
     * <LI> 'temperatura' (temperature) to adjust response creativity.
     * <LI> 'top_p' to control diversity via nucleus sampling.
     * <li> 'stats'
     * <li> 'documents' as key to a map of documentos Map<String, UUID> uploaded during the chat
     * </ul>
     * 
     */
    @Column(columnDefinition = "jsonb")
    private Metadata metadata;
    
    /**
     * The User's message in the chat.
     * Helps in identifying and recalling the chat's theme.
     * It can be images in base64 format or text.
     * it can be very long, so we use TEXT
     * 
     */
    @Column(columnDefinition = "TEXT")
    private String mensagem;
    
    /**
     * The AI's response in the chat.
     * It can be images in base64 format or text.
     * it can be very long, so we use TEXT
     * 
     */
    @Column(columnDefinition = "TEXT")
    private String response;
        

}
