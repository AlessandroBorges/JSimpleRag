package bor.tools.simplerag.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import bor.tools.simplerag.entity.Client;

/**
 * JPA Repository for Client entity.
 *
 * Provides standard CRUD operations and custom queries for Client entities.
 * Clients represent API consumers with authentication and authorization.
 */
@Repository
public interface ClientRepository extends JpaRepository<Client, Integer> {

    /**
     * Find client by UUID
     *
     * @param uuid - client UUID
     * @return Optional client
     */
    @Query("SELECT c FROM Client c WHERE c.uuid = :uuid")
    Optional<Client> findByUuid(@Param("uuid") UUID uuid);

    /**
     * Find client by email (case-insensitive)
     *
     * @param email - client email
     * @return Optional client
     */
    @Query("SELECT c FROM Client c WHERE LOWER(c.email) = LOWER(:email)")
    Optional<Client> findByEmailIgnoreCase(@Param("email") String email);

    /**
     * Find clients by name (case-insensitive, exact match)
     *
     * @param nome - client name
     * @return List of clients with matching name
     */
    @Query("SELECT c FROM Client c WHERE LOWER(c.nome) = LOWER(:nome)")
    List<Client> findByNomeIgnoreCase(@Param("nome") String nome);

    /**
     * Find clients by name containing (case-insensitive, partial match)
     *
     * @param nome - client name pattern
     * @return List of clients with name containing the pattern
     */
    @Query("SELECT c FROM Client c WHERE LOWER(c.nome) LIKE LOWER(CONCAT('%', :nome, '%'))")
    List<Client> findByNomeContainingIgnoreCase(@Param("nome") String nome);

    /**
     * Find all active clients (ativo = true)
     *
     * @return List of active clients
     */
    @Query("SELECT c FROM Client c WHERE c.ativo = true AND c.deletedAt IS NULL")
    List<Client> findAllActive();

    /**
     * Find client by API key
     *
     * @param apiKey - API key
     * @return Optional client
     */
    @Query("SELECT c FROM Client c WHERE c.apiKey = :apiKey")
    Optional<Client> findByApiKey(@Param("apiKey") String apiKey);

    /**
     * Check if email exists (case-insensitive)
     *
     * @param email - email to check
     * @return true if exists
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Client c WHERE LOWER(c.email) = LOWER(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    /**
     * Check if API key exists
     *
     * @param apiKey - API key to check
     * @return true if exists
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Client c WHERE c.apiKey = :apiKey")
    boolean existsByApiKey(@Param("apiKey") String apiKey);

    /**
     * Find clients with expired API keys
     *
     * @return List of clients with expired API keys
     */
    @Query("SELECT c FROM Client c WHERE c.apiKeyExpiresAt IS NOT NULL AND c.apiKeyExpiresAt < CURRENT_TIMESTAMP")
    List<Client> findWithExpiredApiKeys();

    /**
     * Count active clients
     *
     * @return Number of active clients
     */
    @Query("SELECT COUNT(c) FROM Client c WHERE c.ativo = true AND c.deletedAt IS NULL")
    Long countActive();
}
