package bor.tools.simplerag.service;

import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.entity.User;
import bor.tools.simplerag.entity.UserLibrary;
import bor.tools.simplerag.entity.enums.TipoAssociacao;
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
 * Service for UserLibrary (bridge) entity operations.
 * Manages N:N relationships between Users and Libraries with association types.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserLibraryService {

    private final UserLibraryRepository userLibraryRepository;
    private final UserRepository userRepository;
    private final LibraryRepository libraryRepository;

    /**
     * Save (create or update) user-library association
     * @param userLibrary - association to save
     * @return saved association
     */
    @Transactional
    public UserLibrary save(UserLibrary userLibrary) {
        log.debug("Saving user-library association: userId={}, libraryId={}, tipo={}",
                userLibrary.getUserId(), userLibrary.getLibraryId(), userLibrary.getTipoAssociacao());

        // Validate user exists
        if (!userRepository.existsById(userLibrary.getUserId())) {
            throw new IllegalArgumentException("Usuário não encontrado: " + userLibrary.getUserId());
        }

        // Validate library exists
        if (!libraryRepository.existsById(userLibrary.getLibraryId())) {
            throw new IllegalArgumentException("Biblioteca não encontrada: " + userLibrary.getLibraryId());
        }

        return userLibraryRepository.save(userLibrary);
    }

    /**
     * Delete user-library association (soft or hard delete)
     * @param userLibrary - association to delete
     * @param isHardDelete - true for physical delete, false for soft delete
     */
    @Transactional
    public void delete(UserLibrary userLibrary, boolean isHardDelete) {
        log.debug("Deleting user-library association: id={} (hard={})", userLibrary.getId(), isHardDelete);

        if (isHardDelete) {
            userLibraryRepository.delete(userLibrary);
            log.info("UserLibrary hard deleted: id={}", userLibrary.getId());
        } else {
            userLibrary.setDeletedAt(LocalDateTime.now());
            userLibraryRepository.save(userLibrary);
            log.info("UserLibrary soft deleted: id={}", userLibrary.getId());
        }
    }

    /**
     * Find association by user ID and library ID
     * @param userId - user ID
     * @param libraryId - library ID
     * @return Optional association
     */
    public Optional<UserLibrary> findByUserIdAndLibraryId(Integer userId, Integer libraryId) {
        return userLibraryRepository.findByUsuarioIdAndBibliotecaId(userId, libraryId);
    }

    /**
     * Check if association exists
     * @param userId - user ID
     * @param libraryId - library ID
     * @return true if association exists
     */
    public boolean existsAssociation(Integer userId, Integer libraryId) {
        return userLibraryRepository.existsByUsuarioIdAndBibliotecaId(userId, libraryId);
    }

    /**
     * Load user libraries with full library details
     * @param userId - user ID
     * @return List of associations with library details
     */
    @Transactional(readOnly = true)
    public List<UserLibraryWithDetails> loadUserLibrariesWithDetails(Integer userId) {
        List<UserLibrary> associations = userLibraryRepository.findByUsuarioId(userId);

        if (associations.isEmpty()) {
            return Collections.emptyList();
        }

        // Load all libraries in one query
        Set<Integer> libraryIds = associations.stream()
                .map(UserLibrary::getLibraryId)
                .collect(Collectors.toSet());

        Map<Integer, Library> libraryMap = libraryRepository.findAllById(libraryIds).stream()
                .collect(Collectors.toMap(Library::getId, lib -> lib));

        return associations.stream()
                .map(assoc -> new UserLibraryWithDetails(
                        assoc,
                        libraryMap.get(assoc.getLibraryId())
                ))
                .collect(Collectors.toList());
    }

    /**
     * Load library users with full user details
     * @param libraryId - library ID
     * @return List of associations with user details
     */
    @Transactional(readOnly = true)
    public List<UserLibraryWithDetails> loadLibraryUsersWithDetails(Integer libraryId) {
        List<UserLibrary> associations = userLibraryRepository.findByBibliotecaId(libraryId);

        if (associations.isEmpty()) {
            return Collections.emptyList();
        }

        // Load all users in one query
        Set<Integer> userIds = associations.stream()
                .map(UserLibrary::getUserId)
                .collect(Collectors.toSet());

        Map<Integer, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        return associations.stream()
                .map(assoc -> new UserLibraryWithDetails(
                        assoc,
                        userMap.get(assoc.getUserId())
                ))
                .collect(Collectors.toList());
    }

    /**
     * Find associations by user and association type
     * @param userId - user ID
     * @param tipoAssociacao - association type
     * @return List of associations
     */
    public List<UserLibrary> findByTipoAssociacao(Integer userId, TipoAssociacao tipoAssociacao) {
        return userLibraryRepository.findByUsuarioIdAndTipoAssociacao(userId, tipoAssociacao);
    }

    /**
     * Find libraries where user is owner (PROPRIETARIO)
     * @param userId - user ID
     * @return List of associations
     */
    public List<UserLibrary> findUserOwnedLibraries(Integer userId) {
        return userLibraryRepository.findBibliotecasPropriedadeByUsuarioId(userId);
    }

    /**
     * Find library owners (PROPRIETARIO associations)
     * @param libraryId - library ID
     * @return List of owner associations
     */
    public List<UserLibrary> findLibraryOwners(Integer libraryId) {
        return userLibraryRepository.findProprietariosByBibliotecaId(libraryId);
    }

    /**
     * Count user's libraries
     * @param userId - user ID
     * @return count of associations
     */
    public long countUserLibraries(Integer userId) {
        return userLibraryRepository.countByUsuarioId(userId);
    }

    /**
     * Count library's users
     * @param libraryId - library ID
     * @return count of associations
     */
    public long countLibraryUsers(Integer libraryId) {
        return userLibraryRepository.countByBibliotecaId(libraryId);
    }

    /**
     * Create association with validation
     * @param userId - user ID
     * @param libraryId - library ID
     * @param tipoAssociacao - association type
     * @return created association
     */
    @Transactional
    public UserLibrary createAssociation(Integer userId, Integer libraryId, TipoAssociacao tipoAssociacao) {
        // Check if association already exists
        if (existsAssociation(userId, libraryId)) {
            throw new IllegalArgumentException(
                    String.format("Associação já existe: userId=%d, libraryId=%d", userId, libraryId)
            );
        }

        UserLibrary association = new UserLibrary();
        association.setUserId(userId);
        association.setLibraryId(libraryId);
        association.setTipoAssociacao(tipoAssociacao);

        return save(association);
    }

    /**
     * Update association type
     * @param userId - user ID
     * @param libraryId - library ID
     * @param newTipo - new association type
     * @return updated association
     */
    @Transactional
    public Optional<UserLibrary> updateAssociationType(Integer userId, Integer libraryId, TipoAssociacao newTipo) {
        Optional<UserLibrary> assocOpt = findByUserIdAndLibraryId(userId, libraryId);

        if (assocOpt.isEmpty()) {
            return Optional.empty();
        }

        UserLibrary association = assocOpt.get();
        association.setTipoAssociacao(newTipo);
        return Optional.of(save(association));
    }

    /**
     * DTO class to return UserLibrary with full entity details
     */
    public static class UserLibraryWithDetails {
        private final UserLibrary association;
        private final User user;
        private final Library library;

        public UserLibraryWithDetails(UserLibrary association, User user) {
            this.association = association;
            this.user = user;
            this.library = null;
        }

        public UserLibraryWithDetails(UserLibrary association, Library library) {
            this.association = association;
            this.user = null;
            this.library = library;
        }

        public UserLibrary getAssociation() {
            return association;
        }

        public User getUser() {
            return user;
        }

        public Library getLibrary() {
            return library;
        }

        public TipoAssociacao getTipoAssociacao() {
            return association.getTipoAssociacao();
        }
    }
}
