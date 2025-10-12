package bor.tools.simplerag.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Class to represent a project.<br>
 * A project can group multiple chats and related resources.<br>
 * It helps in organizing and managing different chat sessions under a common theme or purpose.
 * 
 * 
 */
@Entity
@Table(name = "chat_project")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = false)
public class Project extends Updatable{

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;
    
    /**
     * UUID of the privative Library associated with this Project.
     * This Library is exclusive to the user and not shared.
     * Can be null if the Project does not use a privative Library.
     */
    @Column(columnDefinition = "uuid", nullable = true)
    private UUID biblioteca_privativa;

    /**
     * Title of the project.
     * A concise name to identify the project.
     */	
    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String titulo;
    
    /**
     * Summary or description of the project's purpose or context.
     * Helps in identifying and recalling the project's theme.
     */
    @Column(columnDefinition ="VARCHAR(255)")
    private String descricao;
    
    /**
     * Arbitrary metadata stored as JSONB.	
     * <UL>It may include keys like:
     * <LI> 'lingua' (language) for processing.
     * <LI> 'modelo' (model) to specify the AI model to use.
     * <LI> 'temperatura' (temperature) to adjust response creativity.
     * <LI> 'top_p' to control diversity via nucleus sampling.
     * <li> 'stats'
     * <li> 'biblotecas' as key to a map of libraries used in the project documentos Map<String, UUID> uploaded during the chat
     * </ul>
     * 
     */
    @Column(columnDefinition = "jsonb")
    private MetaProject metadata;
    
    /**
     * UUID of the client (user) who owns this chat.
     */
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID user_id;
    
    @Column(name = "ordem")
    private Integer ordem;

}
