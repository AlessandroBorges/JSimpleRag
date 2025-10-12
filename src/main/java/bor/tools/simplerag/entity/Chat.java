package bor.tools.simplerag.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "chat")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Chat extends Updatable {

    /**
     * Primary key: UUID unique identifier for each chat record.
     */
    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    /**
     * UUID of the client (user) who owns this chat.
     */
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID client_uuid;

    /**
     * UUID of the privative Library associated with this chat. This Library is
     * exclusive to the user and not shared. Can be null if the chat does not use a
     * privative Library.
     */
    @Column(columnDefinition = "uuid", nullable = true)
    private UUID biblioteca_privativa;

    /**
     * Arbitrary metadata stored as JSONB.
     * <UL>
     * It may include keys like:
     * <LI>'lingua' (language) for processing.
     * <LI>'modelo' (model) to specify the AI model to use.
     * <LI>'temperatura' (temperature) to adjust response creativity.
     * <LI>'top_p' to control diversity via nucleus sampling.
     * <li>'bibliotecas' as key to a array of shared Library's UUIDs
     * <li>'documentos' as key to a map of documentos Map<String, UUID>
     * </ul>
     * 
     */
    @Column(columnDefinition = "jsonb")
    private Metadata metadata;

    /**
     * Summary or description of the chat's purpose or context. Helps in identifying
     * and recalling the chat's theme.
     */
    @Column(columnDefinition = "TEXT")
    private String resumo;

    /**
     * Title of the chat. A concise name to identify the chat.
     */
    @Column(nullable = false)
    private String titulo;

    /**
     * Timestamp of the last message in the chat. Updated whenever a new message
     */
    @PrePersist
    protected void onCreate() {
	super.onCreate();
	if (id == null)
	    id = UUID.randomUUID();
    }

}
