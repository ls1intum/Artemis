package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.dto.OrganizationCourseDTO;
import de.tum.cit.aet.artemis.core.dto.OrganizationDTO;
import de.tum.cit.aet.artemis.core.dto.OrganizationMemberDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;

/**
 * Custom repository fragment for Organization entities with advanced querying capabilities
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CustomOrganizationRepository {

    /**
     * Get all organizations with filtering and sorting based on search criteria
     *
     * @param search     the search criteria containing search term and pagination/sorting info
     * @param withCounts whether to include aggregated user and course counts in the result;
     *                       when {@code false} the query skips the JOIN-heavy aggregation for better performance
     * @return a page of organization DTOs filtered and sorted according to criteria
     */
    Page<OrganizationDTO> getAllOrganizations(SearchTermPageableSearchDTO<String> search, boolean withCounts);

    /**
     * Get a paginated, filtered, and sorted list of users belonging to the given organization
     *
     * @param organizationId the id of the organization
     * @param search         the search criteria containing search term and pagination/sorting info
     * @return a page of member DTOs for users in the organization
     */
    Page<OrganizationMemberDTO> getUsersByOrganizationId(long organizationId, SearchTermPageableSearchDTO<String> search);

    /**
     * Get a paginated, filtered, and sorted list of courses belonging to the given organization
     *
     * @param organizationId the id of the organization
     * @param search         the search criteria containing search term and pagination/sorting info
     * @return a page of course DTOs for courses in the organization
     */
    Page<OrganizationCourseDTO> getCoursesByOrganizationId(long organizationId, SearchTermPageableSearchDTO<String> search);
}
