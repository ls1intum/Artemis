package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.dto.OrganizationDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;

/**
 * Custom repository fragment for Organization entities with advanced querying
 * capabilities
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CustomOrganizationRepository {

    /**
     * Get all organizations with filtering and sorting based on search criteria
     *
     * @param search the search criteria containing search term and
     *                   pagination/sorting info
     * @return a page of organization DTOs with user and course counts, filtered and
     *         sorted according to criteria
     */
    Page<OrganizationDTO> getAllOrganizations(SearchTermPageableSearchDTO<String> search);
}
