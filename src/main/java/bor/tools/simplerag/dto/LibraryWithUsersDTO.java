package bor.tools.simplerag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for Library with associated users.
 * Used by GET /api/v1/libraries/{uuid}/with-users
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LibraryWithUsersDTO {

    /**
     * Library information
     */
    private LibraryDTO library;

    /**
     * Users associated with this library
     */
    private List<UserWithAssociationDTO> users;

    /**
     * Total user count
     */
    private Integer userCount;

    public Integer getUserCount() {
        return users != null ? users.size() : 0;
    }
}
