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
@Tag(name = "Libraries", description = "Library management")
public class LibraryController {

    private final LibraryService libraryService;

    /**
     * Create or update library
     */
    @PostMapping
    @Operation(summary = "Create or update library",
               description = "Saves library with weight validation (semantic + textual = 1.0)")
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
     * Delete library (soft or hard delete)
     */
    @DeleteMapping("/{uuid}")
    @Operation(summary = "Delete library (soft or hard)",
               description = "Soft delete sets deletedAt, hard delete removes library and associations")
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
    @Operation(summary = "Get library by UUID")
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
    @Operation(summary = "Get library with associated users",
               description = "Returns library with all user associations (PROPRIETARIO, COLABORADOR, LEITOR)")
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
