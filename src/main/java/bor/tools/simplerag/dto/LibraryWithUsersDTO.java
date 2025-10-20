package bor.tools.simplerag.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Library with associated users.
 * Used by GET /api/v1/libraries/{uuid}/with-users
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
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
	userCount = users != null ? users.size() : 0;
        return userCount;
    }
}
