package bor.tools.simplerag.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.entity.User;
import bor.tools.simplerag.entity.UserLibrary;
import bor.tools.simplerag.entity.enums.TipoAssociacao;
import bor.tools.simplerag.repository.LibraryRepository;
import bor.tools.simplerag.repository.UserRepository;
import bor.tools.simplerag.service.UserLibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller for User-Library association management.
 * Manages N:N relationships between Users and Libraries.
 */
@RestController
@RequestMapping("/api/v1/user-libraries")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "User-Library Associations",
    description = """
        Manages relationships between users and libraries.

        **Association Types:**
        - PROPRIETARIO (Owner): Full access - can edit, delete library
        - COLABORADOR (Collaborator): Read-write access - can edit library
        - LEITOR (Reader): Read-only access
        """
)
public class UserLibraryController {

    private final UserLibraryService userLibraryService;
    private final UserRepository userRepository;
    private final LibraryRepository libraryRepository;

    /**
     * Create user-library association
     */
    @PostMapping
    @Operation(
        summary = "Create user-library association",
        description = """
            Creates a relationship between a user and a library using their UUIDs.

            **Association Types:**
            - PROPRIETARIO: Owner (full access)
            - COLABORADOR: Collaborator (read-write)
            - LEITOR: Reader (read-only)

            **Note:** An association between the same user and library can only exist once.
            """
    )
    public ResponseEntity<UserLibraryAssociationResponse> createAssociation(
            @Valid @RequestBody CreateAssociationRequest request) {

        log.info("Creating user-library association: userUuid={}, libraryUuid={}, tipo={}",
                request.getUserUuid(), request.getLibraryUuid(), request.getTipoAssociacao());

        try {
            // Find user by UUID
            User user = userRepository.findByUuid(request.getUserUuid())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "User not found: " + request.getUserUuid()));

            // Find library by UUID
            Library library = libraryRepository.findByUuid(request.getLibraryUuid())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Library not found: " + request.getLibraryUuid()));

            // check if association already exists
            UserLibrary association = null;
            
            Optional<UserLibrary> exists = userLibraryService.findByUserIdAndLibraryId(
		    user.getId(), library.getId());            
            if (exists.isPresent()) {
		var tipoAssociacao = request.getTipoAssociacao();
		UserLibrary existingAssociation = exists.get();
		if (existingAssociation.getTipoAssociacao() == tipoAssociacao) {
		    log.warn("Association already exists: userId={}, libraryId={}, tipo={}",
			    user.getId(), library.getId(), tipoAssociacao);    
		    }else {
		    log.warn("Association already exists: userId={}, libraryId={}, tipo={}",
			    user.getId(), library.getId(), existingAssociation.getTipoAssociacao()); 
		    // set new tipoAssociacao
		    existingAssociation.setTipoAssociacao(tipoAssociacao);
		    log.info("Updating existing association to new tipoAssociacao: {}", tipoAssociacao);
		}
		association = existingAssociation;		 
	    }
            
            // Create association
	    if (association == null) {
		association = userLibraryService.createAssociation(user.getId(), library.getId(),
			request.getTipoAssociacao());
	    }

            log.info("Association created: id={}", association.getId());

            UserLibraryAssociationResponse response = UserLibraryAssociationResponse.builder()
                    .associationId(association.getId())
                    .userUuid(user.getUuid())
                    .userName(user.getNome())
                    .libraryUuid(library.getUuid())
                    .libraryName(library.getNome())
                    .tipoAssociacao(association.getTipoAssociacao())
                    .createdAt(association.getCreatedAt())
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.error("Validation error creating association: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error creating association: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao criar associação: " + e.getMessage(), e);
        }
    }

    /**
     * Get association by user and library UUIDs
     */
    @GetMapping("/user/{userUuid}/library/{libraryUuid}")
    @Operation(
        summary = "Get association by user and library UUIDs",
        description = "Returns the association between a specific user and library if it exists"
    )
    public ResponseEntity<UserLibraryAssociationResponse> getAssociation(
            @Parameter(description = "User UUID", required = true)
            @PathVariable UUID userUuid,
            @Parameter(description = "Library UUID", required = true)
            @PathVariable UUID libraryUuid) {

        log.debug("Finding association: userUuid={}, libraryUuid={}", userUuid, libraryUuid);

        try {
            // Find user by UUID
            User user = userRepository.findByUuid(userUuid)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userUuid));

            // Find library by UUID
            Library library = libraryRepository.findByUuid(libraryUuid)
                    .orElseThrow(() -> new IllegalArgumentException("Library not found: " + libraryUuid));

            // Find association
            Optional<UserLibrary> associationOpt = userLibraryService.findByUserIdAndLibraryId(
                    user.getId(), library.getId());

            if (associationOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            UserLibrary association = associationOpt.get();

            UserLibraryAssociationResponse response = UserLibraryAssociationResponse.builder()
                    .associationId(association.getId())
                    .userUuid(user.getUuid())
                    .userName(user.getNome())
                    .libraryUuid(library.getUuid())
                    .libraryName(library.getNome())
                    .tipoAssociacao(association.getTipoAssociacao())
                    .createdAt(association.getCreatedAt())
                    .build();

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Entity not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error finding association: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao buscar associação: " + e.getMessage(), e);
        }
    }

    /**
     * Update association type
     */
    @PutMapping("/user/{userUuid}/library/{libraryUuid}")
    @Operation(
        summary = "Update association type",
        description = "Updates the association type (PROPRIETARIO, COLABORADOR, or LEITOR) for an existing user-library relationship"
    )
    public ResponseEntity<UserLibraryAssociationResponse> updateAssociationType(
            @Parameter(description = "User UUID", required = true)
            @PathVariable UUID userUuid,
            @Parameter(description = "Library UUID", required = true)
            @PathVariable UUID libraryUuid,
            @Valid @RequestBody UpdateAssociationTypeRequest request) {

        log.info("Updating association type: userUuid={}, libraryUuid={}, newTipo={}",
                userUuid, libraryUuid, request.getTipoAssociacao());

        try {
            // Find user by UUID
            User user = userRepository.findByUuid(userUuid)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userUuid));

            // Find library by UUID
            Library library = libraryRepository.findByUuid(libraryUuid)
                    .orElseThrow(() -> new IllegalArgumentException("Library not found: " + libraryUuid));

            // Update association type
            Optional<UserLibrary> updatedOpt = userLibraryService.updateAssociationType(
                    user.getId(),
                    library.getId(),
                    request.getTipoAssociacao()
            );

            if (updatedOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            UserLibrary association = updatedOpt.get();

            UserLibraryAssociationResponse response = UserLibraryAssociationResponse.builder()
                    .associationId(association.getId())
                    .userUuid(user.getUuid())
                    .userName(user.getNome())
                    .libraryUuid(library.getUuid())
                    .libraryName(library.getNome())
                    .tipoAssociacao(association.getTipoAssociacao())
                    .createdAt(association.getCreatedAt())
                    .build();

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Entity not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error updating association: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao atualizar associação: " + e.getMessage(), e);
        }
    }

    /**
     * Delete user-library association
     */
    @DeleteMapping("/user/{userUuid}/library/{libraryUuid}")
    @Operation(
        summary = "Delete user-library association",
        description = """
            Removes the relationship between a user and library.

            **Delete Types:**
            - Soft delete (default): Sets deletedAt timestamp, association remains in database
            - Hard delete: Permanently removes association from database

            **Warning:** Hard delete is irreversible!
            """
    )
    public ResponseEntity<Map<String, Object>> deleteAssociation(
            @Parameter(description = "User UUID", required = true)
            @PathVariable UUID userUuid,
            @Parameter(description = "Library UUID", required = true)
            @PathVariable UUID libraryUuid,
            @Parameter(description = "If true, performs hard delete (permanent removal)")
            @RequestParam(defaultValue = "false") boolean hard) {

        log.info("Deleting association: userUuid={}, libraryUuid={}, hard={}",
                userUuid, libraryUuid, hard);

        try {
            // Find user by UUID
            User user = userRepository.findByUuid(userUuid)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userUuid));

            // Find library by UUID
            Library library = libraryRepository.findByUuid(libraryUuid)
                    .orElseThrow(() -> new IllegalArgumentException("Library not found: " + libraryUuid));

            // Find association
            UserLibrary association = userLibraryService.findByUserIdAndLibraryId(
                            user.getId(), library.getId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Association not found between user and library"));

            // Delete association
            userLibraryService.delete(association, hard);

            log.info("Association deleted: id={}, hard={}", association.getId(), hard);

            Map<String, Object> response = new HashMap<>();
            response.put("message", hard ? "Association permanently deleted" : "Association soft deleted");
            response.put("userUuid", userUuid);
            response.put("libraryUuid", libraryUuid);
            response.put("deletedType", hard ? "HARD" : "SOFT");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Entity not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error deleting association: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao deletar associação: " + e.getMessage(), e);
        }
    }

    // ============ Request DTOs ============

    /**
     * Request DTO to create user-library association
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Request to create user-library association")
    public static class CreateAssociationRequest {

        @NotNull(message = "User UUID is required")
        @Schema(
            description = "UUID of the user",
            example = "550e8400-e29b-41d4-a716-446655440000",
            required = true
        )
        private UUID userUuid;

        @NotNull(message = "Library UUID is required")
        @Schema(
            description = "UUID of the library",
            example = "660e9500-f39c-52e5-b827-557766551111",
            required = true
        )
        private UUID libraryUuid;

        @NotNull(message = "Association type is required")
        @Schema(
            description = """
                Type of association:
                - PROPRIETARIO: Owner (full access)
                - COLABORADOR: Collaborator (read-write)
                - LEITOR: Reader (read-only)
                """,
            example = "COLABORADOR",
            required = true
        )
        private TipoAssociacao tipoAssociacao;
    }

    /**
     * Request DTO to update association type
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Request to update association type")
    public static class UpdateAssociationTypeRequest {

        @NotNull(message = "Association type is required")
        @Schema(
            description = "New association type",
            example = "LEITOR",
            required = true
        )
        private TipoAssociacao tipoAssociacao;
    }

    /**
     * Response DTO for user-library association
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "User-library association details")
    public static class UserLibraryAssociationResponse {

        @Schema(description = "Association ID", example = "123")
        private Integer associationId;

        @Schema(description = "User UUID", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID userUuid;

        @Schema(description = "User name", example = "João Silva")
        private String userName;

        @Schema(description = "Library UUID", example = "660e9500-f39c-52e5-b827-557766551111")
        private UUID libraryUuid;

        @Schema(description = "Library name", example = "Software Engineering")
        private String libraryName;

        @Schema(description = "Association type", example = "COLABORADOR")
        private TipoAssociacao tipoAssociacao;

        @Schema(description = "When the association was created", example = "2025-10-17T10:30:00")
        private java.time.LocalDateTime createdAt;
    }
}
