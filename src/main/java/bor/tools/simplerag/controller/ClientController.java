package bor.tools.simplerag.controller;

import java.util.List;
import java.util.UUID;

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

import bor.tools.simplerag.dto.ClientDTO;
import bor.tools.simplerag.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST Controller for Client management.
 * Provides CRUD operations for API client management.
 */
@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Clients",
    description = """
        API Client management operations.

        **Key Concepts:**
        - Clients represent external applications or services consuming the API
        - Each client has unique UUID, email, and API key for authentication
        - Clients can have different association types (PROPRIETARIO, COLABORADOR, LEITOR)
        - API keys can have expiration dates for enhanced security

        **Authentication:**
        - Clients authenticate using API keys
        - Passwords are securely hashed using BCrypt
        - API keys follow format: sk_[32-character-hash]
        """
)
public class ClientController {

    private final ClientService clientService;

    /**
     * Create or update client
     */
    @PostMapping
    @Operation(
        summary = "Create or update client",
        description = """
            Creates or updates a client with authentication credentials.

            **For New Clients:**
            - Email must be unique
            - Password will be hashed automatically
            - UUID and API key will be auto-generated if not provided
            - Default tipoAssociacao is LEITOR if not specified

            **For Updates:**
            - Must provide ID to update existing client
            - Email uniqueness is validated (excluding current client)
            - Password is only rehashed if changed

            **Example Request:**
            ```json
            {
              "nome": "Mobile App Client",
              "email": "mobile@example.com",
              "passwordHash": "mySecurePassword123",
              "tipoAssociacao": "COLABORADOR",
              "ativo": true
            }
            ```
            """
    )
    public ResponseEntity<ClientDTO> save(@Valid @RequestBody ClientDTO dto) {
        log.info("Saving client: {}", dto.getEmail());

        try {
            ClientDTO saved = clientService.save(dto);

            // Hide password hash in response
            saved.setPasswordHash(null);

            log.info("Client saved: id={}, uuid={}", saved.getId(), saved.getUuid());

            return ResponseEntity.status(dto.getId() == null ? HttpStatus.CREATED : HttpStatus.OK)
                    .body(saved);

        } catch (IllegalArgumentException e) {
            log.error("Validation error saving client: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error saving client: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao salvar cliente: " + e.getMessage(), e);
        }
    }

    /**
     * Soft delete client
     */
    @DeleteMapping("/{id}")
    @Operation(
        summary = "Soft delete client",
        description = """
            Soft deletes a client by setting deletedAt timestamp and ativo=false.

            **Soft Delete:**
            - Sets deletedAt to current timestamp
            - Sets ativo to false
            - Client remains in database
            - Can be recovered by clearing deletedAt and setting ativo=true

            **Note:** This does not invalidate the API key immediately.
            For immediate access revocation, regenerate the API key first.

            **Use client ID (integer) for this operation.**
            """
    )
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        log.info("Soft deleting client: id={}", id);

        try {
            clientService.softDelete(id);

            log.info("Client soft deleted: id={}", id);

            return ResponseEntity.noContent().build();

        } catch (IllegalArgumentException e) {
            log.error("Client not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error deleting client: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao deletar cliente: " + e.getMessage(), e);
        }
    }

    /**
     * Get all clients
     */
    @GetMapping
    @Operation(
        summary = "Get all clients",
        description = """
            Returns all clients in the system.

            **Use Cases:**
            - Administrative overview
            - Client management dashboard
            - API usage monitoring

            **Security Note:** Passwords are hidden in response (set to null)

            **Query Parameters:**
            - activeOnly: Set to true to return only active clients (default: false)
            """
    )
    public ResponseEntity<List<ClientDTO>> findAll(
            @RequestParam(defaultValue = "false") boolean activeOnly) {

        log.debug("Finding all clients (activeOnly={})", activeOnly);

        try {
            List<ClientDTO> clients = activeOnly
                    ? clientService.findAllActive()
                    : clientService.findAll();

            // Hide passwords
            clients.forEach(c -> c.setPasswordHash(null));

            log.info("Found {} clients", clients.size());

            return ResponseEntity.ok(clients);

        } catch (Exception e) {
            log.error("Error finding all clients: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao buscar clientes: " + e.getMessage(), e);
        }
    }

    /**
     * Get client by UUID
     */
    @GetMapping("/uuid/{uuid}")
    @Operation(
        summary = "Get client by UUID",
        description = """
            Retrieves client details using UUID.

            **UUID vs ID:**
            - UUID: Used for external API access (security)
            - Integer ID: Used internally

            **Response:** Password hash is hidden (set to null)
            """
    )
    public ResponseEntity<ClientDTO> findByUuid(@PathVariable UUID uuid) {
        log.debug("Finding client by UUID: {}", uuid);

        ClientDTO client = clientService.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Client não encontrado: " + uuid));

        // Hide password
        client.setPasswordHash(null);

        return ResponseEntity.ok(client);
    }

    /**
     * Get client by email
     */
    @GetMapping("/email/{email}")
    @Operation(
        summary = "Get client by email",
        description = """
            Retrieves client details using email address.

            **Email matching is case-insensitive**

            **Response:** Password hash is hidden (set to null)

            **Example:** /api/v1/clients/email/mobile@example.com
            """
    )
    public ResponseEntity<ClientDTO> findByEmail(@PathVariable String email) {
        log.debug("Finding client by email: {}", email);

        ClientDTO client = clientService.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Client não encontrado: " + email));

        // Hide password
        client.setPasswordHash(null);

        return ResponseEntity.ok(client);
    }

    /**
     * Get clients by name
     */
    @GetMapping("/nome/{nome}")
    @Operation(
        summary = "Get clients by name",
        description = """
            Retrieves clients by name (case-insensitive).

            **Search Types:**
            - Default: Exact match (case-insensitive)
            - With partial=true: Partial match (LIKE %nome%)

            **Examples:**
            - /api/v1/clients/nome/Mobile App Client (exact match)
            - /api/v1/clients/nome/Mobile?partial=true (finds "Mobile App Client", "Mobile Web", etc.)

            **Response:** Passwords are hidden (set to null)
            """
    )
    public ResponseEntity<List<ClientDTO>> findByNome(
            @PathVariable String nome,
            @RequestParam(defaultValue = "false") boolean partial) {

        log.debug("Finding clients by name: {} (partial={})", nome, partial);

        List<ClientDTO> clients = partial
                ? clientService.findByNomeContaining(nome)
                : clientService.findByNome(nome);

        // Hide passwords
        clients.forEach(c -> c.setPasswordHash(null));

        log.info("Found {} clients with name: {}", clients.size(), nome);

        return ResponseEntity.ok(clients);
    }

    /**
     * Check if email exists
     */
    @GetMapping("/exists/email/{email}")
    @Operation(
        summary = "Check if email exists",
        description = """
            Checks if an email is already registered.

            **Use Case:** Validate email uniqueness before creating client

            **Returns:** Boolean (true if exists, false otherwise)
            """
    )
    public ResponseEntity<Boolean> existsByEmail(@PathVariable String email) {
        log.debug("Checking if email exists: {}", email);

        boolean exists = clientService.existsByEmail(email);

        return ResponseEntity.ok(exists);
    }

    /**
     * Regenerate API key
     */
    @PostMapping("/{id}/regenerate-api-key")
    @Operation(
        summary = "Regenerate API key",
        description = """
            Generates a new API key for the client.

            **Use Cases:**
            - API key compromised
            - Regular key rotation for security
            - Client lost their API key

            **Important:**
            - Old API key becomes immediately invalid
            - Client must update their applications with new key
            - New key follows format: sk_[32-character-hash]

            **Returns:** New API key (this is the only time it will be shown in plain text)
            """
    )
    public ResponseEntity<String> regenerateApiKey(@PathVariable Integer id) {
        log.info("Regenerating API key for client: id={}", id);

        try {
            String newApiKey = clientService.regenerateApiKey(id);

            log.info("API key regenerated for client: id={}", id);

            return ResponseEntity.ok(newApiKey);

        } catch (IllegalArgumentException e) {
            log.error("Client not found: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error regenerating API key: {}", e.getMessage(), e);
            throw new RuntimeException("Erro ao regenerar API key: " + e.getMessage(), e);
        }
    }
}
