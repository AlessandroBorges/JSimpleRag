package bor.tools.simplerag.controller;

import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.dto.LibraryWithAssociationDTO;
import bor.tools.simplerag.dto.UserDTO;
import bor.tools.simplerag.dto.UserWithLibrariesDTO;
import bor.tools.simplerag.entity.User;
import bor.tools.simplerag.service.UserService;
import bor.tools.simplerag.service.UserWithLibraries;
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
 * REST Controller for User management.
 * Provides CRUD operations and library association management.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Users", description = "User management")
public class UserController {

    private final UserService userService;

    /**
     * Create or update user
     */
    @PostMapping
    @Operation(summary = "Create or update user",
               description = "Saves user with email uniqueness validation")
    public ResponseEntity<UserDTO> save(@Valid @RequestBody UserDTO dto) {
        log.info("Saving user: {}", dto.getEmail());

        try {
           
            // Save
            User saved = userService.save(dto);

            // Convert back to DTO
            UserDTO response = dto;

            log.info("User saved: id={}, uuid={}", saved.getId(), saved.getUuid());

            return ResponseEntity.status(dto.getId() == null ? HttpStatus.CREATED : HttpStatus.OK)
                    .body(response);

        } catch (IllegalArgumentException e) {
            log.error("Validation error saving user: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error saving user: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao salvar usuário: " + e.getMessage(), e);
        }
    }

    /**
     * Get all users
     */
    @GetMapping
    @Operation(summary = "Get all users",
               description = "Returns all active users (not soft-deleted). Use pagination parameters if needed.")
    public ResponseEntity<List<UserDTO>> findAll() {
        log.debug("Finding all users");

        try {
            List<User> users = userService.findAllAtivos();

            List<UserDTO> userDTOs = users.stream()
                    .map(UserDTO::from)
                    .collect(Collectors.toList());

            // hide password
	    userDTOs.forEach(u -> u.setPassword(null));
            
            log.info("Found {} users", userDTOs.size());

            return ResponseEntity.ok(userDTOs);

        } catch (Exception e) {
            log.error("Error finding all users: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao buscar usuários: " + e.getMessage(), e);
        }
    }

    /**
     * Delete user (soft or hard delete)
     */
    @DeleteMapping("/{uuid}")
    @Operation(summary = "Delete user (soft or hard)",
               description = "Soft delete sets deletedAt, hard delete removes user and library associations")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid,
                                       @RequestParam(defaultValue = "false") boolean hard) {
        log.info("Deleting user: uuid={}, hard={}", uuid, hard);

        try {
            User user = userService.findByUuid(uuid)
                    .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + uuid));

            userService.delete(user, hard);

            log.info("User deleted: uuid={}, hard={}", uuid, hard);

            return ResponseEntity.noContent().build();

        } catch (IllegalArgumentException e) {
            log.error("User not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error deleting user: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao deletar usuário: " + e.getMessage(), e);
        }
    }

    /**
     * Get user by UUID
     */
    @GetMapping("/{uuid}")
    @Operation(summary = "Get user by UUID")
    public ResponseEntity<UserDTO> findByUuid(@PathVariable UUID uuid) {
        log.debug("Finding user by UUID: {}", uuid);

        User user = userService.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + uuid));

        return ResponseEntity.ok(UserDTO.from(user));
    }

    /**
     * Get user with associated libraries
     */
    @GetMapping("/{uuid}/with-libraries")
    @Operation(summary = "Get user with associated libraries",
               description = "Returns user with all library associations (PROPRIETARIO, COLABORADOR, LEITOR)")
    public ResponseEntity<UserWithLibrariesDTO> getWithLibraries(@PathVariable UUID uuid) {
        log.debug("Finding user with libraries: uuid={}", uuid);

        try {
            UserWithLibraries userWithLibraries = userService.loadUserWithLibraries(uuid)
                    .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + uuid));

            // Convert to DTO
            UserDTO userDTO = UserDTO.from(userWithLibraries.getUser());

            List<LibraryWithAssociationDTO> libraryDTOs = userWithLibraries.getLibrariesWithAssociations().stream()
                    .map(la -> LibraryWithAssociationDTO.builder()
                            .library(LibraryDTO.from(la.getLibrary()))
                            .tipoAssociacao(la.getAssociation().getTipoAssociacao())
                            .associationId(la.getAssociation().getId())
                            .build())
                    .collect(Collectors.toList());

            UserWithLibrariesDTO response = UserWithLibrariesDTO.builder()
                    .user(userDTO)
                    .libraries(libraryDTOs)
                    .build();

            log.debug("User with libraries found: {} libraries", response.getLibraryCount());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("User not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error loading user with libraries: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao carregar usuário com bibliotecas: " + e.getMessage(), e);
        }
    }

    /**
     * Get user by email
     */
    @GetMapping("/by-email/{email}")
    @Operation(summary = "Get user by email")
    public ResponseEntity<UserDTO> findByEmail(@PathVariable String email) {
        log.debug("Finding user by email: {}", email);

        User user = userService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado: " + email));

        return ResponseEntity.ok(UserDTO.from(user));
    }

    /**
     * Check if email exists
     */
    @GetMapping("/exists/{email}")
    @Operation(summary = "Check if email exists")
    public ResponseEntity<Boolean> existsByEmail(@PathVariable String email) {
        log.debug("Checking if email exists: {}", email);

        boolean exists = userService.existsByEmail(email);

        return ResponseEntity.ok(exists);
    }

    /**
     * Convert DTO to Entity
     */
    private User toEntity(UserDTO dto) {
        User user = new User();
        user.setId(dto.getId());
        user.setUuid(dto.getUuid());
        user.setNome(dto.getNome());
        user.setEmail(dto.getEmail());
        return user;
    }
}
