package bor.tools.simplerag.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import bor.tools.simplerag.dto.UserDTO;
import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.entity.User;
import bor.tools.simplerag.entity.UserLibrary;

/**
 * DTO class to return user with libraries and associations
 */
public class UserWithLibraries {
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