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

import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.entity.User;
import bor.tools.simplerag.entity.UserLibrary;
import bor.tools.simplerag.repository.LibraryRepository;
import bor.tools.simplerag.repository.UserLibraryRepository;
import bor.tools.simplerag.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for Library entity operations.
 * Handles library management, weight validation, and user associations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LibraryService {

    private final LibraryRepository libraryRepository;
    private final UserLibraryRepository userLibraryRepository;
    private final UserRepository userRepository;

    /**
     * Save (create or update) library entity
     * @param library - library to save
     * @return saved library
     */
    @Transactional
    public Library save(Library library) {
        log.debug("Saving library: {}", library.getNome());

        // Validate weights before saving (additional validation to @PrePersist)
        validateWeights(library);

        // Generate UUID if not present
        if (library.getUuid() == null) {
            library.setUuid(UUID.randomUUID());
        }

        return libraryRepository.save(library);
    }

    /**
     * Delete library (soft or hard delete)
     * @param library - library to delete
     * @param isHardDelete - true for physical delete, false for soft delete
     */
    @Transactional
    public void delete(Library library, boolean isHardDelete) {
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
     * @return Optional library
     */
    public Optional<Library> findByUuid(UUID uuid) {
        return libraryRepository.findAll().stream()
                .filter(lib -> uuid.equals(lib.getUuid()))
                .findFirst();
    }

    /**
     * Find library by name
     * @param nome - library name
     * @return Optional library
     */
    public Optional<Library> findByNome(String nome) {
        return libraryRepository.findByNomeIgnoreCase(nome);
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
        Optional<Library> libraryOpt = findByUuid(libraryUuid);

        if (libraryOpt.isEmpty()) {
            return Optional.empty();
        }

        Library library = libraryOpt.get();

        // Load user-library associations
        List<UserLibrary> associations = userLibraryRepository.findByBibliotecaId(library.getId());

        // Load users
        Set<Integer> userIds = associations.stream()
                .map(UserLibrary::getUserId)
                .collect(Collectors.toSet());

        List<User> users = userIds.isEmpty()
                ? Collections.emptyList()
                : userRepository.findAllById(userIds);

        // Create association map for quick lookup
        Map<Integer, UserLibrary> associationMap = associations.stream()
                .collect(Collectors.toMap(
                        UserLibrary::getUserId,
                        assoc -> assoc
                ));

        return Optional.of(new LibraryWithUsers(library, users, associationMap));
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
        Optional<Library> libraryOpt = findByUuid(libraryUuid);

        if (libraryOpt.isEmpty()) {
            return Optional.empty();
        }

        Library library = libraryOpt.get();

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
        private final Library library;
        private final List<User> users;
        private final Map<Integer, UserLibrary> associations;

        public LibraryWithUsers(Library library, List<User> users, Map<Integer, UserLibrary> associations) {
            this.library = library;
            this.users = users;
            this.associations = associations;
        }

        public Library getLibrary() {
            return library;
        }

        public List<User> getUsers() {
            return users;
        }

        public Map<Integer, UserLibrary> getAssociations() {
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
    public static class UserWithAssociation {
        private final User user;
        private final UserLibrary association;

        public UserWithAssociation(User user, UserLibrary association) {
            this.user = user;
            this.association = association;
        }

        public User getUser() {
            return user;
        }

        public UserLibrary getAssociation() {
            return association;
        }
    }

    /**
     * DTO class for library statistics
     */
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

        public Integer getLibraryId() {
            return libraryId;
        }

        public UUID getLibraryUuid() {
            return libraryUuid;
        }

        public String getNome() {
            return nome;
        }

        public long getUserCount() {
            return userCount;
        }

        public long getDocCount() {
            return docCount;
        }

        public long getTokenCount() {
            return tokenCount;
        }

        public long getEmbeddingCount() {
            return embeddingCount;
        }
    }
}
