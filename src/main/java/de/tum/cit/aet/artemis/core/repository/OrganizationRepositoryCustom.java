package de.tum.cit.aet.artemis.core.repository;

import org.springframework.data.domain.Page;

import de.tum.cit.aet.artemis.core.dto.OrganizationDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;

/**
 * Custom repository fragment for Organization entities with advanced querying
 * capabilities
 */
public interface OrganizationRepositoryCustom {

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
