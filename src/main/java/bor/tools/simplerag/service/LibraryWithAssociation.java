package bor.tools.simplerag.service;

import bor.tools.simplerag.dto.LibraryDTO;
import bor.tools.simplerag.entity.Library;
import bor.tools.simplerag.entity.UserLibrary;

/**
 * DTO class to pair library with its association
 */
public class LibraryWithAssociation {
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