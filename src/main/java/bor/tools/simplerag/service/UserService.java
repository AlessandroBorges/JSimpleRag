package bor.tools.simplerag.service;

import bor.tools.simplerag.dto.UserDTO;
import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.entity.User;
import bor.tools.simplerag.entity.UserLibrary;
import bor.tools.simplerag.repository.LibraryRepository;
import bor.tools.simplerag.repository.UserLibraryRepository;
import bor.tools.simplerag.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for User entity operations. Handles authentication, profile
 * management, and user-library associations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private static final String HASH_PREFIX = "hash::";

    private static final String UUID_SEED = "::JSimpleRagG_SYSTEM";

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final UserRepository userRepository;
    private final UserLibraryRepository userLibraryRepository;
    private final LibraryRepository libraryRepository;

    /**
     * Save (create or update) user from DTO
     * 
     * @param userDTO - user DTO
     * @return saved user
     */
    public User save(UserDTO userDTO) {
	try {
	    if (userDTO == null) {
		throw new IllegalArgumentException("UserDTO cannot be null");
	    }
	    User user = User.builder()
		    .id(userDTO.getId())
		    // .uuid(userDTO.getUuid()) ignore UUID on input
		    .nome(userDTO.getNome())
		    .email(userDTO.getEmail())
		    .ativo(userDTO.getAtivo())
		    .passwordHash(userDTO.getPassword()) // assume plain password to be hashed
		    .build();

	    user.setUuid(null); // ensure UUID is null

	    try {
		// Determine if it's a new user or update
		boolean isNewUser = user.getId() == null || user.getId() < 1;

		if (isNewUser) {
		    user.setId(null); // ensure ID is null for new user
		    user = save(user);
		} else {
		    user = update(user);
		}

		userDTO.setId(user.getId());
		userDTO.setUuid(user.getUuid());
		userDTO.setCreatedAt(user.getCreatedAt());
		userDTO.setUpdatedAt(user.getUpdatedAt());
		userDTO.setDeletedAt(user.getDeletedAt());

		// save associated libraries if provided
		if (userDTO.getLibraryIds() != null) {
		    // first, remove existing associations
		    userLibraryRepository.deleteByUserId(user.getId());
		    // then, add new associations
		    for (Integer libId : userDTO.getLibraryIds()) {
			UserLibrary assoc = UserLibrary.builder().userId(user.getId()).libraryId(libId).build();

			userLibraryRepository.save(assoc);
		    }
		}
	    } catch (IllegalArgumentException ex) {
		log.warn("Error saving user entity directly: {}. Attempting with password hashing.", ex.getMessage());
		throw ex;
	    }
	    return user;
	} catch (Exception ex) {
	    log.error("Error saving user: {}", ex.getMessage(), ex);
	    ex.printStackTrace();
	    throw ex;
	}
    }

    /**
     * Save (create) new user entity
     * 
     * @param user - user to save
     * @return saved user
     */
    @Transactional
    public User save(User user) {

	// checar email nulo
	if (user.getEmail() != null) {
	    user.setEmail(user.getEmail().trim());
	} else {
	    throw new IllegalArgumentException("Email não pode ser nulo");
	}

	// Validate this is a new user
	if (user.getId() != null && user.getId() > 0) {
	    var opt = userRepository.findById(user.getId());
	    if (opt.isPresent()) {
		log.warn("User ID is set for new user. Redirecting to update method.");
		return update(user);
	    }
	}

	user.setId(null); // ensure ID is null for new user

	// Validate email uniqueness
	if (userRepository.existsByEmail(user.getEmail())) {
	    throw new IllegalArgumentException("Email já cadastrado: " + user.getEmail());
	}

	log.debug("Creating new user: {}", user.getEmail());

	// Generate UUID if not present
	if (user.getUuid() == null) {
	    // For new users, generate UUID based on email + constant string
	    String seed = user.getEmail() + UUID_SEED + "::" + LocalDateTime.now().toString();
	    UUID uuid = UUID.nameUUIDFromBytes(seed.getBytes());
	    user.setUuid(uuid);
	}

	if (user.getAtivo() == null) {
	    user.setAtivo(true); // default to active
	}

	// Hash password for new user
	if (user.getPasswordHash() != null) {
	    String hashedPassword = hashPassword(user, user.getPasswordHash());
	    user.setPasswordHash(hashedPassword);
	} else {
	    throw new IllegalArgumentException("Password cannot be null for new user");
	}

	user.setDeletedAt(null); // ensure not deleted

	log.info("Creating new user: {} (UUID: {})", user.getEmail(), user.getUuid());

	return userRepository.save(user);
    }

    /**
     * Update existing user entity
     * 
     * @param user - user to update
     * @return updated user
     */
    @Transactional
    public User update(User user) {

	// checar email nulo
	if (user.getEmail() != null) {
	    user.setEmail(user.getEmail().trim());
	} else {
	    throw new IllegalArgumentException("Email não pode ser nulo");
	}

	// Validate this is an existing user
	if (user.getId() == null || user.getId() < 1) {
	    user.setId(null); // ensure ID is null for new user
	    return save(user);
	}

	// Load existing user
	Optional<User> existingUserOpt = userRepository.findById(user.getId());
	if (!existingUserOpt.isPresent()) {
	    throw new IllegalArgumentException("User not found with ID: " + user.getId());
	}

	User existingUser = existingUserOpt.get();

	// Check if email is being changed and validate uniqueness
	if (!existingUser.getEmail().equalsIgnoreCase(user.getEmail())) {
	    var optEmailUser = userRepository.findByEmail(user.getEmail());
	    // check if found user is different from current one but with same email
	    if (optEmailUser.isPresent() && !optEmailUser.get().getId().equals(existingUser.getId())) {
		throw new IllegalArgumentException("Email já cadastrado: " + user.getEmail());
	    }
	}

	log.debug("Updating user: {}", user.getEmail());

	// Keep existing UUID if not provided
	if (user.getUuid() == null) {
	    user.setUuid(existingUser.getUuid());
	}

	if (user.getAtivo() == null) {
	    user.setAtivo(existingUser.getAtivo()); // keep existing active status
	}

	// Hash password if it has changed
	if (user.getPasswordHash() != null && !user.getPasswordHash().equals(existingUser.getPasswordHash())) {
	    // assume passwordHash field contains plain password to be hashed
	    String hashedPassword = hashPassword(user, user.getPasswordHash());
	    user.setPasswordHash(hashedPassword);
	} else {
	    // keep existing password hash
	    user.setPasswordHash(existingUser.getPasswordHash());
	}

	user.setDeletedAt(null); // ensure not deleted

	log.info("Updating user: {} (UUID: {})", user.getEmail(), user.getUuid());

	return userRepository.save(user);
    }

    /**
     * Hash user password
     * 
     * @param user          - user
     * @param plainPassword - plain password
     * @return hashed password
     */
    private String hashPassword(User user, String plainPassword) {
	// Simple hash example (not secure, use a proper hashing algorithm in
	// production)
	if (plainPassword == null || plainPassword.trim().isEmpty()) {
	    throw new IllegalArgumentException("Password cannot be null");
	}

	if (plainPassword.length() < 6) {
	    throw new IllegalArgumentException("Password is too short, minimum 6 characters required");
	}

	if (plainPassword.startsWith(HASH_PREFIX)) {
	    // already hashed - avoid double hashing
	    return plainPassword;
	}

	if (user.getEmail() == null) {
	    throw new IllegalArgumentException("User email cannot be null for password hashing");
	}

	if (plainPassword.startsWith("$2a$") || plainPassword.startsWith("$2b$") || plainPassword.startsWith("$2y$")) {
	    // Already a BCrypt hash - avoid double hashing
	    return plainPassword;
	}

	String saltedPassword = saltPassword(user, plainPassword);
	return passwordEncoder.encode(saltedPassword);
    }

    /**
     * Salt user password using user-specific data
     * 
     * @param user          - user
     * @param plainPassword - plain password
     * @return salted password
     */
    private String saltPassword(User user, String plainPassword) {
	// String saltedPassword = plainPassword + "<:email:>" + user.getEmail() +
	// "<:uuid:>" + user.getUuid().toString();
	String saltedPassword = plainPassword + "<:uuid:>" + user.getUuid().toString();
	;
	return saltedPassword;
    }

    /**
     * Validate user password
     * 
     * @param user         - user to validate
     * @param passwordHash - password hash to check
     * @return true if valid
     */
    public boolean isPasswordValid(User user, String plainPassword) {
	// check nulls
	if (user == null) {
	    throw new IllegalArgumentException("User cannot be null");
	}
	if (plainPassword == null || plainPassword.trim().isEmpty() || plainPassword.length() < 6) {
	    String msg = (plainPassword == null) ? "null" : (plainPassword.trim().isEmpty() ? "empty" : "too short");
	    throw new IllegalArgumentException("Password is " + msg);
	}
	if (user.getEmail() == null) {
	    throw new IllegalArgumentException("User email cannot be null for password salting");
	}
	if (user.getUuid() == null) {
	    throw new IllegalArgumentException("User UUID cannot be null for password salting");
	}

	String saltedPassword = saltPassword(user, plainPassword);
	return passwordEncoder.matches(saltedPassword, user.getPasswordHash());
    }

    /**
     * Delete user (soft or hard delete)
     * 
     * @param user         - user to delete
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
     * 
     * @param id - user ID
     * @return Optional user
     */
    public Optional<User> findById(Integer id) {
	return userRepository.findById(id);
    }

    /**
     * Find user by UUID
     * 
     * @param uuid - user UUID
     * @return Optional user
     */
    public Optional<User> findByUuid(UUID uuid) {
	return userRepository.findByUuid(uuid);
    }

    /**
     * Find user by email
     * 
     * @param email - user email
     * @return Optional user
     */
    public Optional<User> findByEmail(String email) {
	return userRepository.findByEmailIgnoreCase(email);
    }

    /**
     * Find all active users
     * 
     * @return List of active users
     */
    public List<User> findAllAtivos() {
	return userRepository.findAllAtivos();
    }

    /**
     * Load user with all associated libraries
     * 
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
	Set<Integer> libraryIds = associations.stream().map(UserLibrary::getLibraryId).collect(Collectors.toSet());

	List<Library> libraries = libraryIds.isEmpty() ? Collections.emptyList()
		: libraryRepository.findAllById(libraryIds);

	// Create association map for quick lookup
	Map<Integer, UserLibrary> associationMap = associations.stream()
		.collect(Collectors.toMap(UserLibrary::getLibraryId, assoc -> assoc));

	return Optional.of(new UserWithLibraries(user, libraries, associationMap));
    }

    /**
     * Load user libraries only (without full library details)
     * 
     * @param userUuid - user UUID
     * @return List of UserLibrary associations
     */
    @Transactional(readOnly = true)
    public List<UserLibrary> loadUserLibraries(UUID userUuid) {
	Optional<User> userOpt = findByUuid(userUuid);
	return userOpt.map(user -> userLibraryRepository.findByUserId(user.getId())).orElse(Collections.emptyList());
    }

    /**
     * Check if email exists
     * 
     * @param email - email to check
     * @return true if exists
     */
    public boolean existsByEmail(String email) {
	return userRepository.existsByEmail(email);
    }
}
