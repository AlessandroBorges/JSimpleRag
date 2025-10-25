package bor.tools.simplerag.service;

import java.util.List;
import java.util.Map;

import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.dto.LibraryWithAssociationDTO;
import bor.tools.simplerag.dto.UserDTO;
import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.entity.User;
import bor.tools.simplerag.entity.UserLibrary;
import bor.tools.simplerag.entity.enums.TipoAssociacao;
import lombok.Builder;
import lombok.Data;

/**
 * DTO class to return user with libraries and associations
 */
@Data
@Builder
public class UserWithLibraries {
    /**
     * User information
     */
    private final User user;
    /**
     * List of libraries associated with the user
     */
    private final List<Library> libraries;
    
    /**
     * Map of library ID to UserLibrary association
     */
    private final Map<Integer, UserLibrary> associations;

    /**
     * Constructor
     * @param user The user entity
     * @param libraries List of libraries
     * @param associations Map of library ID to UserLibrary association
     */
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
    public List<LibraryWithAssociationDTO> getLibrariesWithAssociations() {
	
	List<LibraryWithAssociationDTO> result = new java.util.ArrayList<>();
	
	for(Library lib : libraries) {
	    Integer libId = lib.getId();
	    UserLibrary assoc = associations.get(libId);
	    TipoAssociacao tipoAssoc = assoc != null ? assoc.getTipoAssociacao() : null;
	    LibraryWithAssociationDTO libWithAssoc = LibraryWithAssociationDTO.builder()
		    .library(LibraryDTO.from(lib))
		    .tipoAssociacao(tipoAssoc)
		    .associationId(assoc != null ? assoc.getId() : null)
		    .build();
	    result.add(libWithAssoc);	
	}
	
        return result;
    }
}