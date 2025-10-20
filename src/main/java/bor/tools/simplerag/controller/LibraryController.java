package bor.tools.simplerag.controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.dto.LibraryWithUsersDTO;
import bor.tools.simplerag.dto.UserWithAssociationDTO;
import bor.tools.simplerag.service.LibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller for Library management.
 * Provides CRUD operations and user association management.
 */
@RestController
@RequestMapping("/api/v1/libraries")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Libraries",
    description = """
        Library management operations.

        **Key Concepts:**
        - Libraries are containers for related documents (books, regulations, articles, manuals)
        - Each library has configurable search weights (semantic + textual = 1.0)
        - Libraries use UUID for external APIs and integer ID for internal operations

        **Search Weight Configuration:**
        - Legal documents: Higher textual weight (e.g., 0.4 semantic + 0.6 textual) for precise term matching
        - Technical documentation: Higher semantic weight (e.g., 0.7 semantic + 0.3 textual) for conceptual understanding
        - General knowledge: Balanced weights (e.g., 0.6 semantic + 0.4 textual)
        """
)
public class LibraryController {

    private final LibraryService libraryService;

    /**
     * Create or update library
     */
    @PostMapping
    @Operation(
        summary = "Create or update library",
        description = """
            Creates or updates a library with search weight configuration.

            **Weight Validation:**
            - pesoSemantico + pesoTextual must equal 1.0
            - Both values must be between 0.0 and 1.0

            **Recommended Weights by Content Type:**
            - Legal/Regulatory (0.4/0.6): Emphasizes exact term matching (article numbers, law names)
            - Technical Documentation (0.7/0.3): Favors conceptual understanding
            - Scientific Articles (0.6/0.4): Balanced approach
            - General Knowledge (0.6/0.4): Default balanced configuration

            **Examples:**
            ```json
            {
              "nome": "Brazilian Legislation",
              "areaConhecimento": "Legal",
              "pesoSemantico": 0.4,
              "pesoTextual": 0.6,
              "tipo": "PUBLICA"
            }
            ```
            """
    )
    public ResponseEntity<LibraryDTO> save(@Valid @RequestBody LibraryDTO dto) {
        log.info("Saving library: {}", dto.getNome());

        try {
            LibraryDTO saved = libraryService.save(dto);
            log.info("Library saved: id={}, uuid={}", saved.getId(), saved.getUuid());
            return ResponseEntity.status(dto.getId() == null ? HttpStatus.CREATED : HttpStatus.OK)
                    .body(saved);

        } catch (IllegalArgumentException e) {
            log.error("Validation error saving library: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error saving library: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao salvar biblioteca: " + e.getMessage(), e);
        }
    }

    /**
     * Get all libraries
     */
    @GetMapping
    @Operation(
        summary = "Get all libraries",
        description = """
            Returns all active libraries (not soft-deleted).

            **Use Cases:**
            - Display library catalog
            - Browse available knowledge areas
            - Select library for document upload
            - Administrative overview

            **Response includes:**
            - Library UUID (for external API use)
            - Name and knowledge area
            - Search weights configuration
            - Library type (PUBLIC/PRIVATE)
            - Creation and update timestamps
            """
    )
    public ResponseEntity<List<LibraryDTO>> findAll() {
        log.debug("Finding all libraries");

        try {
            List<LibraryDTO> libraries = libraryService.findAll();

            log.info("Found {} libraries", libraries.size());

            return ResponseEntity.ok(libraries);

        } catch (Exception e) {
            log.error("Error finding all libraries: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao buscar bibliotecas: " + e.getMessage(), e);
        }
    }

    /**
     * Delete library (soft or hard delete)
     */
    @DeleteMapping("/{uuid}")
    @Operation(
        summary = "Delete library (soft or hard)",
        description = """
            Deletes a library using its UUID.

            **Delete Types:**
            1. **Soft Delete (default, hard=false)**:
               - Sets deletedAt timestamp to current date/time
               - Library remains in database but is marked as deleted
               - Can be recovered by clearing deletedAt field
               - Recommended for most cases

            2. **Hard Delete (hard=true)**:
               - Permanently removes library from database
               - CASCADE delete removes all associated:
                 * Documents
                 * Chapters
                 * Embeddings
                 * User-library associations
               - **IRREVERSIBLE** - use with extreme caution!

            **Security Note:** Uses UUID for external API access. Internal integer ID is not exposed.

            **Example:**
            - Soft delete: `DELETE /api/v1/libraries/{uuid}?hard=false`
            - Hard delete: `DELETE /api/v1/libraries/{uuid}?hard=true`
            """
    )
    public ResponseEntity<Void> delete(@PathVariable UUID uuid,
                                       @RequestParam(defaultValue = "false") boolean hard) {
        log.info("Deleting library: uuid={}, hard={}", uuid, hard);

        try {
            libraryService.delete(uuid, hard);

            log.info("Library deleted: uuid={}, hard={}", uuid, hard);

            return ResponseEntity.noContent().build();

        } catch (IllegalArgumentException e) {
            log.error("Library not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error deleting library: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao deletar biblioteca: " + e.getMessage(), e);
        }
    }

    /**
     * Get library by UUID
     */
    @GetMapping("/{uuid}")
    @Operation(
        summary = "Get library by UUID",
        description = """
            Retrieves library details using its UUID.

            **UUID vs ID Pattern:**
            - **UUID**: Used for external API access (security, prevents database enumeration)
            - **Integer ID**: Used internally for high-performance database operations
            - External clients should always use UUID

            Returns library with all configuration including search weights.
            """
    )
    public ResponseEntity<LibraryDTO> findByUuid(@PathVariable UUID uuid) {
        log.debug("Finding library by UUID: {}", uuid);

        LibraryDTO library = libraryService.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Biblioteca não encontrada: " + uuid));

        return ResponseEntity.ok(library);
    }

    /**
     * Get library with associated users
     */
    @GetMapping("/{uuid}/with-users")
    @Operation(
        summary = "Get library with associated users",
        description = """
            Returns library with all associated users and their access levels.

            **User Association Types:**
            - **PROPRIETARIO (Owner)**:
              * Full access to library
              * Can edit library settings
              * Can delete library
              * Can manage other users' access

            - **COLABORADOR (Collaborator)**:
              * Read and write access
              * Can add/edit documents
              * Cannot delete library
              * Cannot change library settings

            - **LEITOR (Reader)**:
              * Read-only access
              * Can search and view documents
              * Cannot modify anything

            **Response includes:**
            - Library details
            - All associated users with their names and access types
            - User count

            **Use Case:** Managing library permissions and viewing who has access
            """
    )
    public ResponseEntity<LibraryWithUsersDTO> getWithUsers(@PathVariable UUID uuid) {
        log.debug("Finding library with users: uuid={}", uuid);

        try {
            LibraryService.LibraryWithUsers libraryWithUsers = libraryService.loadLibraryWithUsers(uuid)
                    .orElseThrow(() -> new IllegalArgumentException("Biblioteca não encontrada: " + uuid));

            // Convert to DTO
            LibraryDTO libraryDTO = libraryWithUsers.getLibrary();

            List<UserWithAssociationDTO> userDTOs = libraryWithUsers.getUsersWithAssociations().stream()
                    .map(ua -> UserWithAssociationDTO.builder()
                            .user(ua.getUser())  // Já é UserDTO
                            .tipoAssociacao(ua.getAssociation().getTipoAssociacao())
                            .associationId(ua.getAssociation().getId())
                            .build())
                    .collect(Collectors.toList());

            LibraryWithUsersDTO response = LibraryWithUsersDTO.builder()
                    .library(libraryDTO)
                    .users(userDTOs)
                    .build();

            log.debug("Library with users found: {} users", response.getUserCount());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Library not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error loading library with users: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao carregar biblioteca com usuários: " + e.getMessage(), e);
        }
    }

    /**
     * Get library by name
     */
    @GetMapping("/by-name/{nome}")
    @Operation(summary = "Get library by name (case-insensitive)")
    public ResponseEntity<LibraryDTO> findByNome(@PathVariable String nome) {
        log.debug("Finding library by name: {}", nome);

        LibraryDTO library = libraryService.findByNome(nome)
                .orElseThrow(() -> new IllegalArgumentException("Biblioteca não encontrada: " + nome));

        return ResponseEntity.ok(library);
    }

    /**
     * Get distinct knowledge areas
     */
    @GetMapping("/areas")
    @Operation(summary = "Get all distinct knowledge areas")
    public ResponseEntity<List<String>> getKnowledgeAreas() {
        log.debug("Finding distinct knowledge areas");

        List<String> areas = libraryService.findDistinctAreasConhecimento();

        return ResponseEntity.ok(areas);
    }

}
