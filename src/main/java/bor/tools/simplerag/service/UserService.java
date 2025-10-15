package bor.tools.simplerag.service;

import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.dto.UserDTO;
import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.entity.User;
import bor.tools.simplerag.entity.UserLibrary;
import bor.tools.simplerag.repository.LibraryRepository;
import bor.tools.simplerag.repository.UserLibraryRepository;
import bor.tools.simplerag.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for User entity operations.
 * Handles authentication, profile management, and user-library associations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final UserLibraryRepository userLibraryRepository;
    private final LibraryRepository libraryRepository;

    /**
     * Save (create or update) user entity
     * @param user - user to save
     * @return saved user
     */
    @Transactional
    public User save(User user) {
        log.debug("Saving user: {}", user.getEmail());

        // Generate UUID if not present
        if (user.getUuid() == null) {
            user.setUuid(UUID.randomUUID());
        }

        // Validate email uniqueness
        if (user.getId() == null) {
            if (userRepository.existsByEmail(user.getEmail())) {
                throw new IllegalArgumentException("Email já cadastrado: " + user.getEmail());
            }
        }

        return userRepository.save(user);
    }

    /**
     * Delete user (soft or hard delete)
     * @param user - user to delete
     * @param isHardDelete - true for physical delete, false for soft delete
     */
    @Transactional
    public void delete(User user, boolean isHardDelete) {
        log.debug("Deleting user: {} (hard={})", user.getEmail(), isHardDelete);

        if (isHardDelete) {
            // Remove all user-library associations first
            userLibraryRepository.deleteByUserId(user.getId());
            userRepository.delete(user);
            log.info("User hard deleted: {}", user.getEmail());
        } else {
            user.setDeletedAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("User soft deleted: {}", user.getEmail());
        }
    }

    /**
     * Find user by ID
     * @param id - user ID
     * @return Optional user
     */
    public Optional<User> findById(Integer id) {
        return userRepository.findById(id);
    }

    /**
     * Find user by UUID
     * @param uuid - user UUID
     * @return Optional user
     */
    public Optional<User> findByUuid(UUID uuid) {
        return userRepository.findByUuid(uuid.toString());
    }

    /**
     * Find user by email
     * @param email - user email
     * @return Optional user
     */
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }

    /**
     * Find all active users
     * @return List of active users
     */
    public List<User> findAllAtivos() {
        return userRepository.findAllAtivos();
    }

    /**
     * Load user with all associated libraries
     * @param userUuid - user UUID
     * @return User with libraries loaded
     */
    @Transactional(readOnly = true)
    public Optional<UserWithLibraries> loadUserWithLibraries(UUID userUuid) {
        Optional<User> userOpt = findByUuid(userUuid);

        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        User user = userOpt.get();

        // Load user-library associations
        List<UserLibrary> associations = userLibraryRepository.findByUserId(user.getId());

        // Load libraries
        Set<Integer> libraryIds = associations.stream()
                .map(UserLibrary::getLibraryId)
                .collect(Collectors.toSet());

        List<Library> libraries = libraryIds.isEmpty()
                ? Collections.emptyList()
                : libraryRepository.findAllById(libraryIds);

        // Create association map for quick lookup
        Map<Integer, UserLibrary> associationMap = associations.stream()
                .collect(Collectors.toMap(
                        UserLibrary::getLibraryId,
                        assoc -> assoc
                ));

        return Optional.of(new UserWithLibraries(user, libraries, associationMap));
    }

    /**
     * Load user libraries only (without full library details)
     * @param userUuid - user UUID
     * @return List of UserLibrary associations
     */
    @Transactional(readOnly = true)
    public List<UserLibrary> loadUserLibraries(UUID userUuid) {
        Optional<User> userOpt = findByUuid(userUuid);
        return userOpt.map(user -> userLibraryRepository.findByUserId(user.getId()))
                .orElse(Collections.emptyList());
    }

    /**
     * Check if email exists
     * @param email - email to check
     * @return true if exists
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * DTO class to return user with libraries and associations
     */
    public static class UserWithLibraries {
        private final User user;
        private final List<Library> libraries;
        private final Map<Integer, UserLibrary> associations;

        public UserWithLibraries(User user, List<Library> libraries, Map<Integer, UserLibrary> associations) {
            this.user = user;
            this.libraries = libraries;
            this.associations = associations;
        }

        public User getUser() {
            return user;
        }

        public List<Library> getLibraries() {
            return libraries;
        }

        public Map<Integer, UserLibrary> getAssociations() {
            return associations;
        }

        /**
         * Convert to DTO with library associations
         */
        public UserDTO toDTO() {
            UserDTO dto = UserDTO.from(user);

            // Add library IDs to DTO
            Integer[] libraryIds = libraries.stream()
                    .map(Library::getId)
                    .toArray(Integer[]::new);
            dto.setLibraryIds(libraryIds);

            return dto;
        }

        /**
         * Get libraries with their association details
         */
        public List<LibraryWithAssociation> getLibrariesWithAssociations() {
            return libraries.stream()
                    .map(lib -> new LibraryWithAssociation(
                            lib,
                            associations.get(lib.getId())
                    ))
                    .collect(Collectors.toList());
        }
    }

    /**
     * DTO class to pair library with its association
     */
    public static class LibraryWithAssociation {
        private final Library library;
        private final UserLibrary association;

        public LibraryWithAssociation(Library library, UserLibrary association) {
            this.library = library;
            this.association = association;
        }

        public Library getLibrary() {
            return library;
        }

        public UserLibrary getAssociation() {
            return association;
        }

        public LibraryDTO toDTO() {
            return LibraryDTO.from(library);
        }
    }
}
