package de.tum.cit.aet.artemis.globalsearch.api;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.dto.SearchableEntityCandidateDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityPrefetchService;

/**
 * API for the Iris module to pre-fetch access-filtered entity candidates for the global-search
 * answer path. Applies exactly the palette's access rules; see
 * {@link SearchableEntityPrefetchService}.
 */
@Conditional(WeaviateEnabled.class)
@Controller
@Lazy
public class SearchableEntityPrefetchApi extends AbstractGlobalSearchApi {

    private final SearchableEntityPrefetchService searchableEntityPrefetchService;

    public SearchableEntityPrefetchApi(SearchableEntityPrefetchService searchableEntityPrefetchService) {
        this.searchableEntityPrefetchService = searchableEntityPrefetchService;
    }

    /**
     * Runs the access-filtered entity search for the given user.
     *
     * @param user  the requesting user (with course roles loaded)
     * @param query the search query
     * @param limit the maximum number of candidates
     * @return the candidates, empty when the user has no accessible courses
     */
    public List<SearchableEntityCandidateDTO> prefetchCandidates(User user, String query, int limit) {
        return searchableEntityPrefetchService.prefetchCandidates(user, query, limit);
    }
}
