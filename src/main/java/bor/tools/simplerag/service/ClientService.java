package bor.tools.simplerag.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bor.tools.simplerag.dto.ClientDTO;
import bor.tools.simplerag.entity.Client;
import bor.tools.simplerag.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for Client entity operations.
 * Handles client management, authentication, and API key operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {

    protected static final String DEFAULT_CREATED_BY_SYSTEM = "system";

    protected static final String CREATED_BY_KEY = "createdBy";

    private static final String UUID_SEED = "::JSimpleRagClient_SYSTEM";

    private final ClientRepository clientRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Save (create or update) client from DTO
     *
     * @param clientDTO - client DTO
     * @return saved client DTO
     */
    @Transactional
    public ClientDTO save(ClientDTO clientDTO) {
        log.debug("Saving client: {}", clientDTO.getEmail());

        try {
            Client client = Client.fromDTO(clientDTO);

            // Determine if it's a new client or update
            boolean isNewClient = client.getId() == null || client.getId() < 1;
                     
	    if (isNewClient) {			
                client = create(client);                
            } else {
                client = update(client);
            }

            var dto = client.toDTO();
            log.info("Client saved: {} (UUID: {})", dto.getEmail(), dto.getUuid());
            
            dto.setId(null); // do not expose internal ID
            dto.setPasswordHash(null); // do not expose password hash
            
            return dto;

        } catch (Exception ex) {
            log.error("Error saving client: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Create new client
     *
     * @param client - client to create
     * @return saved client
     */
    @Transactional
    protected Client create(Client client) {
        // Validate email
        if (client.getEmail() == null || client.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email não pode ser nulo ou vazio");
        }
        client.setEmail(client.getEmail().trim());

        // Check email uniqueness
        if (clientRepository.existsByEmailIgnoreCase(client.getEmail())) {
            throw new IllegalArgumentException("Email já cadastrado: " + client.getEmail());
        }

        // Generate UUID if not present
        if (client.getUuid() == null) {
            String seed = client.getEmail() + UUID_SEED + "::" + LocalDateTime.now().toString();
            UUID uuid = UUID.nameUUIDFromBytes(seed.getBytes());
            client.setUuid(uuid);
        }

        // Generate API key if not present
        if (client.getApiKey() == null || client.getApiKey().trim().isEmpty()) {
            client.setApiKey(generateApiKey(client));
        }

        // Hash password if present
        if (client.getPasswordHash() != null && !client.getPasswordHash().trim().isEmpty()) {
            if (!isPasswordHashed(client.getPasswordHash())) {
                client.setPasswordHash(hashPassword(client.getPasswordHash()));
            }
        } else {
            throw new IllegalArgumentException("Password não pode ser nulo para novo cliente");
        }

        // Set default values
        if (client.getAtivo() == null) {
            client.setAtivo(true);
        }
        
        
        client.setApiKeyExpiresAt(LocalDateTime.now().plusYears(1));
        client.setDeletedAt(null);
        client.getMetadata().addMetadata(CREATED_BY_KEY, 
        	client.getChangedBy() != null ? client.getChangedBy() : DEFAULT_CREATED_BY_SYSTEM);

        log.info("Creating new client: {} (UUID: {})", client.getEmail(), client.getUuid());

        return clientRepository.save(client);
    }

    /**
     * Update existing client
     *
     * @param client - client to update
     * @return updated client
     */
    @Transactional
    protected Client update(Client client) {
        // Validate email
        if (client.getEmail() == null || client.getEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("Email não pode ser nulo ou vazio");
        }
        client.setEmail(client.getEmail().trim());

        // Load existing client by id or uuid
        Client existingClient = client.getId() != null ?
        		     clientRepository.findById(client.getId())
        		     		.orElseThrow(() -> new IllegalArgumentException("Client não encontrado com ID: " + client.getId())) 
        		     :clientRepository.findByUuid(client.getUuid())
        		     		.orElseThrow(() -> new IllegalArgumentException("Client não encontrado com ID: " + client.getId()));

        // Check email uniqueness if changed
        if (!existingClient.getEmail().equalsIgnoreCase(client.getEmail())) {
            if (clientRepository.existsByEmailIgnoreCase(client.getEmail())) {
                throw new IllegalArgumentException("Email já cadastrado: " + client.getEmail());
            }
        }
        
        //id, uuid, apikey, passwordHash must remain the same
        client.setId(existingClient.getId());
        client.setUuid(existingClient.getUuid());
        client.setApiKey(existingClient.getApiKey());
        client.setPasswordHash(existingClient.getPasswordHash());
        // Keep active status if not provided
        if (client.getAtivo() == null) {
            client.setAtivo(existingClient.getAtivo());
        }
        // Clear deletedAt on update
        client.setDeletedAt(null);

        log.info("Updating client: {} (UUID: {})", client.getEmail(), client.getUuid());

        return clientRepository.save(client);
    }

    /**
     * Soft delete client
     *
     * @param id - client ID
     */
    @Transactional
    public void softDelete(Integer id) {
        log.debug("Soft deleting client ID: {}", id);

        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Client não encontrado: " + id));

        client.setDeletedAt(LocalDateTime.now());
        client.setAtivo(false);
        clientRepository.save(client);

        log.info("Client soft deleted: {} (ID: {})", client.getEmail(), id);
    }

    /**
     * Soft delete client by UUID
     *
     * @param uuid - client UUID
     */
    @Transactional
    public void softDeleteByUuid(UUID uuid) {
        log.debug("Soft deleting client UUID: {}", uuid);

        Client client = clientRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Client não encontrado: " + uuid));

        softDelete(client.getId());
    }

    /**
     * Find all clients
     *
     * @return List of all clients
     */
    public List<ClientDTO> findAll() {
        return clientRepository.findAll().stream()
                .map(Client::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Find all active clients
     *
     * @return List of active clients
     */
    public List<ClientDTO> findAllActive() {
        return clientRepository.findAllActive().stream()
                .map(Client::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Find client by ID
     *
     * @param id - client ID
     * @return Optional client DTO
     */
    public Optional<ClientDTO> findById(Integer id) {
        return clientRepository.findById(id)
                .map(Client::toDTO);
    }

    /**
     * Find client by UUID
     *
     * @param uuid - client UUID
     * @return Optional client DTO
     */
    public Optional<ClientDTO> findByUuid(UUID uuid) {
        return clientRepository.findByUuid(uuid)
                .map(Client::toDTO);
    }

    /**
     * Find client by email
     *
     * @param email - client email
     * @return Optional client DTO
     */
    public Optional<ClientDTO> findByEmail(String email) {
        return clientRepository.findByEmailIgnoreCase(email)
                .map(Client::toDTO);
    }

    /**
     * Find clients by name (exact match)
     *
     * @param nome - client name
     * @return List of clients with matching name
     */
    public List<ClientDTO> findByNome(String nome) {
        return clientRepository.findByNomeIgnoreCase(nome).stream()
                .map(Client::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Find clients by name containing (partial match)
     *
     * @param nome - client name pattern
     * @return List of clients with name containing the pattern
     */
    public List<ClientDTO> findByNomeContaining(String nome) {
        return clientRepository.findByNomeContainingIgnoreCase(nome).stream()
                .map(Client::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Find client by API key
     *
     * @param apiKey - API key
     * @return Optional client DTO
     */
    public Optional<ClientDTO> findByApiKey(String apiKey) {
        return clientRepository.findByApiKey(apiKey)
                .map(Client::toDTO);
    }

    /**
     * Validate client password
     *
     * @param clientId - client ID
     * @param plainPassword - plain password to validate
     * @return true if password is valid
     */
    public boolean validatePassword(Integer clientId, String plainPassword) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client não encontrado"));

        return passwordEncoder.matches(plainPassword, client.getPasswordHash());
    }

    /**
     * Regenerate API key for client
     *
     * @param clientId - client ID
     * @return new API key
     */
    @Transactional
    public String regenerateApiKey(Integer clientId) {
	
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client não encontrado"));
        // must save the OLD api key for logging before overwriting
        // lets save the old key on metadata, 
        
        String newApiKey = generateApiKey(client);
        client.setApiKey(newApiKey);
        clientRepository.save(client);

        log.info("Regenerated API key for client: {}", client.getEmail());

        return newApiKey;
    }

    /**
     * Set API key expiration
     *
     * @param clientId - client ID
     * @param expiresAt - expiration timestamp
     */
    @Transactional
    public void setApiKeyExpiration(Integer clientId, LocalDateTime expiresAt) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client não encontrado"));

        client.setApiKeyExpiresAt(expiresAt);
        clientRepository.save(client);

        log.info("Set API key expiration for client {}: {}", client.getEmail(), expiresAt);
    }

    /**
     * Check if email exists
     *
     * @param email - email to check
     * @return true if exists
     */
    public boolean existsByEmail(String email) {
        return clientRepository.existsByEmailIgnoreCase(email);
    }

    // ============ Helper Methods ============

    /**
     * Hash password using BCrypt
     *
     * @param plainPassword - plain password
     * @return hashed password
     */
    private String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password não pode ser nulo ou vazio");
        }

        if (plainPassword.length() < 6) {
            throw new IllegalArgumentException("Password deve ter no mínimo 6 caracteres");
        }

        return passwordEncoder.encode(plainPassword);
    }

    /**
     * Check if password is already hashed (BCrypt format)
     *
     * @param password - password to check
     * @return true if already hashed
     */
    private boolean isPasswordHashed(String password) {
        return password != null &&
               (password.startsWith("$2a$") ||
                password.startsWith("$2b$") ||
                password.startsWith("$2y$"));
    }

    /**
     * Generate API key for client
     *
     * @param client - client
     * @return generated API key
     */
    private String generateApiKey(Client client) {
        String seed = client.getEmail() + "::API_KEY::" + LocalDateTime.now().toString() + "::" + UUID.randomUUID();
        String apiKey = "sk_" + UUID.nameUUIDFromBytes(seed.getBytes()).toString().replace("-", "");

        // Ensure uniqueness
        while (clientRepository.existsByApiKey(apiKey)) {
            seed = seed + "::RETRY::" + System.nanoTime();
            apiKey = "sk_" + UUID.nameUUIDFromBytes(seed.getBytes()).toString().replace("-", "");
        }

        return apiKey;
    }
}
