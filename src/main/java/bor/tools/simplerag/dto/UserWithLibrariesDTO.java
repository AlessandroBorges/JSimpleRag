package bor.tools.simplerag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for User with associated libraries.
 * Used by GET /api/v1/users/{uuid}/with-libraries
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserWithLibrariesDTO {

    /**
     * User information
     */
    private UserDTO user;

    /**
     * Libraries associated with this user
     */
    private List<LibraryWithAssociationDTO> libraries;

    /**
     * Total library count
     */
    private Integer libraryCount;

    public Integer getLibraryCount() {
        return libraries != null ? libraries.size() : 0;
    }
}
