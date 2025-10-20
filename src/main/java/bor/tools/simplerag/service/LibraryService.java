package bor.tools.simplerag.service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.dto.UserDTO;
import bor.tools.simplerag.dto.UserLibraryDTO;
import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.entity.User;
import bor.tools.simplerag.entity.UserLibrary;
import bor.tools.simplerag.repository.LibraryRepository;
import bor.tools.simplerag.repository.UserLibraryRepository;
import bor.tools.simplerag.repository.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for Library entity operations.
 * Handles library management, weight validation, and user associations.
 *
 * <h2>Search Weight Configuration</h2>
 *
 * <p>Each library can configure default search weights that balance semantic
 * and textual search components:</p>
 *
 * <ul>
 *   <li><b>pesoSemantico</b> (0.0-1.0): Weight for embedding-based semantic search</li>
 *   <li><b>pesoTextual</b> (0.0-1.0): Weight for PostgreSQL full-text search</li>
 *   <li><b>Constraint</b>: pesoSemantico + pesoTextual = 1.0</li>
 * </ul>
 *
 * <h3>Recommended Weights by Content Type</h3>
 * <table border="1">
 *   <tr>
 *     <th>Content Type</th>
 *     <th>Semantic</th>
 *     <th>Textual</th>
 *     <th>Rationale</th>
 *   </tr>
 *   <tr>
 *     <td>Technical documentation</td>
 *     <td>0.7</td>
 *     <td>0.3</td>
 *     <td>Conceptual understanding &gt; exact keywords</td>
 *   </tr>
 *   <tr>
 *     <td>Legal documents</td>
 *     <td>0.4</td>
 *     <td>0.6</td>
 *     <td>Exact terminology matters</td>
 *   </tr>
 *   <tr>
 *     <td>Scientific papers</td>
 *     <td>0.6</td>
 *     <td>0.4</td>
 *     <td>Balance concepts and terms</td>
 *   </tr>
 *   <tr>
 *     <td>General knowledge</td>
 *     <td>0.6</td>
 *     <td>0.4</td>
 *     <td>Default balanced approach</td>
 *   </tr>
 *   <tr>
 *     <td>News articles</td>
 *     <td>0.5</td>
 *     <td>0.5</td>
 *     <td>Equal importance</td>
 *   </tr>
 * </table>
 *
 * <h3>Usage</h3>
 * <pre>
 * // Library-level defaults (can be overridden per query)
 * LibraryDTO library = new LibraryDTO();
 * library.setPesoSemantico(0.6f);
 * library.setPesoTextual(0.4f);
 * libraryService.save(library);
 *
 * // Query-level override
 * SearchRequest request = new SearchRequest();
 * request.setPesoSemantico(0.8f);  // Override library default
 * request.setPesoTextual(0.2f);
 * </pre>
 *
 * @see bor.tools.simplerag.controller.SearchController#hybridSearch
 * @since 0.0.1
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LibraryService {

    private final LibraryRepository libraryRepository;
    private final UserLibraryRepository userLibraryRepository;
    private final UserRepository userRepository;

    /**
     * Save (create or update) library
     * @param dto - library DTO to save
     * @return saved library DTO
     */
    @Transactional
    public LibraryDTO save(LibraryDTO dto) {
        log.debug("Saving library: {}", dto.getNome());

        // Validate weights
        if (!dto.isWeightValid()) {
            throw new IllegalArgumentException(
                    String.format("A soma dos pesos deve ser 1.0 (atual: %.2f + %.2f = %.2f)",
                            dto.getPesoSemantico(), dto.getPesoTextual(),
                            dto.getPesoSemantico() + dto.getPesoTextual())
            );
        }

        // Convert DTO to Entity
        Library library = dto.toEntity();

        // Generate UUID if not present
        if (library.getUuid() == null) {
            library.setUuid(UUID.randomUUID());
        }

        Library saved = libraryRepository.save(library);
        return LibraryDTO.from(saved);
    }

    

    /**
     * Delete library (soft or hard delete)
     * @param uuid - library UUID
     * @param isHardDelete - true for physical delete, false for soft delete
     */
    @Transactional
    public void delete(UUID uuid, boolean isHardDelete) {
        Library library = libraryRepository.findAll().stream()
                .filter(lib -> uuid.equals(lib.getUuid()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Biblioteca não encontrada: " + uuid));

        log.debug("Deleting library: {} (hard={})", library.getNome(), isHardDelete);

        if (isHardDelete) {
            // Remove all user-library associations first
            userLibraryRepository.deleteByBibliotecaId(library.getId());
            libraryRepository.delete(library);
            log.info("Library hard deleted: {}", library.getNome());
        } else {
            library.setDeletedAt(LocalDateTime.now());
            libraryRepository.save(library);
            log.info("Library soft deleted: {}", library.getNome());
        }
    }

    /**
     * Find all libraries
     * @return List of all libraries
     */
    public List<LibraryDTO> findAll() {
        return libraryRepository.findAll().stream()
                .map(LibraryDTO::from)
                .collect(Collectors.toList());
    }

    /**
     * Find library by ID
     * @param id - library ID
     * @return Optional library
     */
    public Optional<Library> findById(Integer id) {
        return libraryRepository.findById(id);
    }

    /**
     * Find library by UUID
     * @param uuid - library UUID
     * @return Optional library DTO
     */
    public Optional<LibraryDTO> findByUuid(UUID uuid) {
        return libraryRepository.findAll().stream()
                .filter(lib -> uuid.equals(lib.getUuid()))
                .findFirst()
                .map(LibraryDTO::from);
    }

    /**
     * Find library by name
     * @param nome - library name
     * @return Optional library DTO
     */
    public Optional<LibraryDTO> findByNome(String nome) {
        return libraryRepository.findByNomeIgnoreCase(nome)
                .map(LibraryDTO::from);
    }

    /**
     * Validate semantic and textual weights sum to 1.0
     * @param library - library to validate
     * @throws IllegalArgumentException if weights are invalid
     */
    public void validateWeights(Library library) {
        if (library.getPesoSemantico() == null || library.getPesoTextual() == null) {
            throw new IllegalArgumentException("Pesos semântico e textual não podem ser nulos");
        }

        float sum = library.getPesoSemantico() + library.getPesoTextual();
        if (Math.abs(sum - 1.0f) > 0.001f) {
            throw new IllegalArgumentException(
                    String.format("A soma dos pesos deve ser 1.0 (atual: %.2f + %.2f = %.2f)",
                            library.getPesoSemantico(), library.getPesoTextual(), sum)
            );
        }
    }

    /**
     * Load library with all associated users
     * @param libraryUuid - library UUID
     * @return LibraryWithUsers wrapper
     */
    @Transactional(readOnly = true)
    public Optional<LibraryWithUsers> loadLibraryWithUsers(UUID libraryUuid) {
        Optional<LibraryDTO> libraryOpt = findByUuid(libraryUuid);

        if (libraryOpt.isEmpty()) {
            return Optional.empty();
        }

        LibraryDTO library = libraryOpt.get();

        // Load user-library associations
        List<UserLibraryDTO> associations = toListUserLibs(userLibraryRepository.findByLibraryId(library.getId()));

        // Load users
        Set<Integer> userIds = associations.stream()
                .map(UserLibraryDTO::getUserId)
                .collect(Collectors.toSet());

        List<UserDTO> users = userIds.isEmpty()
                ? Collections.emptyList()
                : toList(userRepository.findAllById(userIds));

        // Create association map for quick lookup
        Map<Integer, UserLibraryDTO> associationMap = associations.stream()
                .collect(Collectors.toMap(
                        UserLibraryDTO::getUserId,
                        assoc -> assoc
                ));

        return Optional.of(new LibraryWithUsers(library, users, associationMap));
    }

    private List<UserLibraryDTO> toListUserLibs(List<UserLibrary> byBibliotecaId) {
	List<UserLibraryDTO> list = byBibliotecaId.stream()
		.map(UserLibraryDTO::from)
		.collect(Collectors.toList());
	return list;
    }

    /**
     * 
     * @param allById
     * @return
     */
    private List<UserDTO> toList(List<User> allById) {
	List<UserDTO> list = allById.stream()
		.map(UserDTO::from)
		.collect(Collectors.toList());
	return list;
    }

    /**
     * Find all distinct knowledge areas
     * @return List of knowledge areas
     */
    public List<String> findDistinctAreasConhecimento() {
        return libraryRepository.findDistinctAreasConhecimento();
    }

    /**
     * Check if library name exists
     * @param nome - library name
     * @return true if exists
     */
    public boolean existsByNome(String nome) {
        return libraryRepository.existsByNomeIgnoreCase(nome);
    }

    /**
     * Get library statistics
     * @param libraryUuid - library UUID
     * @return LibraryStats object
     */
    @Transactional(readOnly = true)
    public Optional<LibraryStats> getLibraryStats(UUID libraryUuid) {
        Optional<LibraryDTO> libraryOpt = findByUuid(libraryUuid);

        if (libraryOpt.isEmpty()) {
            return Optional.empty();
        }

        LibraryDTO library = libraryOpt.get();

        // Count users
        long userCount = userLibraryRepository.countByBibliotecaId(library.getId());

        // TODO: Add document count, token count, embedding count when repositories are available

        return Optional.of(new LibraryStats(
                library.getId(),
                library.getUuid(),
                library.getNome(),
                userCount,
                0L, // docCount - TODO
                0L, // tokenCount - TODO
                0L  // embeddingCount - TODO
        ));
    }

    /**
     * DTO class to return library with users and associations
     */
    public static class LibraryWithUsers {
        private final LibraryDTO library;
        private final List<UserDTO> users;
        private final Map<Integer, UserLibraryDTO> associations;

        public LibraryWithUsers(LibraryDTO library, List<UserDTO> users, Map<Integer, UserLibraryDTO> associations) {
            this.library = library;
            this.users = users;
            this.associations = associations;
        }

        public LibraryDTO getLibrary() {
            return library;
        }

        public List<UserDTO> getUsers() {
            return users;
        }

        public Map<Integer, UserLibraryDTO> getAssociations() {
            return associations;
        }

        /**
         * Get users with their association details
         */
        public List<UserWithAssociation> getUsersWithAssociations() {
            return users.stream()
                    .map(user -> new UserWithAssociation(
                            user,
                            associations.get(user.getId())
                    ))
                    .collect(Collectors.toList());
        }
    }

    /**
     * DTO class to pair user with association
     */
    @Getter
    public static class UserWithAssociation {
        private final UserDTO user;
        private final UserLibraryDTO association;

        public UserWithAssociation(UserDTO user, UserLibraryDTO association) {
            this.user = user;
            this.association = association;
        }       
    }

    /**
     * DTO class for library statistics
     */
    @Getter
    public static class LibraryStats {
        private final Integer libraryId;
        private final UUID libraryUuid;
        private final String nome;
        private final long userCount;
        private final long docCount;
        private final long tokenCount;
        private final long embeddingCount;

        public LibraryStats(Integer libraryId, UUID libraryUuid, String nome,
                           long userCount, long docCount, long tokenCount, long embeddingCount) {
            this.libraryId = libraryId;
            this.libraryUuid = libraryUuid;
            this.nome = nome;
            this.userCount = userCount;
            this.docCount = docCount;
            this.tokenCount = tokenCount;
            this.embeddingCount = embeddingCount;
        }
       
    }
}
