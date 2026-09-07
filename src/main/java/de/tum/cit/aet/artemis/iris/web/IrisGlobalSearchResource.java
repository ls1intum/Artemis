package de.tum.cit.aet.artemis.iris.web;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.LimitRequestsPerMinute;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.globalsearch.api.SearchableEntityPrefetchApi;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.IrisAccessContextService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.GlobalSearchAskRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.GlobalSearchLectureRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisEntityCandidateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;

/**
 * REST controller for Iris global search.
 * Exposes two endpoints:
 * <ul>
 * <li>{@code POST api/iris/lecture-search} — synchronous semantic lecture-unit search via Pyris.</li>
 * <li>{@code POST api/iris/search-answer} — asynchronous Iris inline-answer pipeline; results are pushed to the client via WebSocket.</li>
 * </ul>
 */
@Conditional(IrisEnabled.class)
@Lazy
@FeatureUsage("search/lecture-search")
@RestController
@RequestMapping("api/iris/")
public class IrisGlobalSearchResource {

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    private final UserRepository userRepository;

    private final UserAiPreferenceService userAiPreferenceService;

    private final IrisAccessContextService irisAccessContextService;

    private final Optional<SearchableEntityPrefetchApi> searchableEntityPrefetchApi;

    /** Entity candidates handed to the answer pipeline; Pyris caps its own intake independently. */
    private static final int ENTITY_CANDIDATE_LIMIT = 10;

    public IrisGlobalSearchResource(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, UserRepository userRepository,
            UserAiPreferenceService userAiPreferenceService, IrisAccessContextService irisAccessContextService, Optional<SearchableEntityPrefetchApi> searchableEntityPrefetchApi) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.userAiPreferenceService = userAiPreferenceService;
        this.pyrisJobService = pyrisJobService;
        this.userRepository = userRepository;
        this.irisAccessContextService = irisAccessContextService;
        this.searchableEntityPrefetchApi = searchableEntityPrefetchApi;
    }

    /**
     * POST api/iris/lecture-search: Search for lecture units using Pyris.
     *
     * @param requestDTO the search request containing query, limit, and optional courseIds filter
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of search results
     */
    @PostMapping("lecture-search")
    @EnforceAtLeastStudent
    public ResponseEntity<List<PyrisLectureSearchResultDTO>> search(@RequestBody @Valid GlobalSearchLectureRequestDTO requestDTO) {
        var user = userRepository.getUserWithCourseRolesAndAuthorities();
        var accessContext = irisAccessContextService.resolveAccessContext(user);
        return ResponseEntity.ok(pyrisConnectorService.searchLectures(requestDTO.query(), requestDTO.limit(), requestDTO.courseIds(), accessContext));
    }

    /**
     * POST api/iris/search-answer: Ask Iris to answer a question using course content (async).
     * Pyris classifies the query and sends webhook callbacks; results are pushed to the client via WebSocket.
     *
     * @param requestDTO the request containing the query and result limit
     * @param principal  the authenticated user (used to route the WebSocket response)
     * @return the {@link ResponseEntity} with status {@code 202 (Accepted)}
     */
    @PostMapping("search-answer")
    @EnforceAtLeastStudent
    @LimitRequestsPerMinute(type = RateLimitType.AI_SEARCH_PIPELINE)
    public ResponseEntity<Void> ask(@RequestBody @Valid GlobalSearchAskRequestDTO requestDTO, Principal principal) {
        var user = userRepository.getUserWithCourseRolesAndAuthorities();
        userAiPreferenceService.hasOptedIntoLlmUsageElseThrow(user.getId());
        var selectedLlmUsage = userAiPreferenceService.findDecision(user.getId());
        var accessContext = irisAccessContextService.resolveAccessContext(user);
        pyrisJobService.addGlobalSearchAnswerJob(principal.getName(), requestDTO.runId().toString());
        // Note: do NOT remove the job on exception here. Transport-level failures are ambiguous —
        // Pyris may have received the request and already started the pipeline. Removing the token
        // would break WebSocket routing for any callbacks that arrive later.
        // Jobs expire automatically via the Hazelcast TTL (default 5 minutes).
        // Entity candidates are pre-fetched with the palette's access filtering, because channel
        // membership, exam registrations and role-dependent release rules only exist in the Artemis
        // database; Pyris renders them into cards and reranks them against the lecture content.
        List<PyrisEntityCandidateDTO> entityCandidates = searchableEntityPrefetchApi
                .map(api -> api.prefetchCandidates(user, requestDTO.query(), ENTITY_CANDIDATE_LIMIT).stream().map(PyrisEntityCandidateDTO::of).toList()).orElse(List.of());
        pyrisConnectorService.executeGlobalSearchIrisAnswer(requestDTO.query(), requestDTO.limit(), requestDTO.runId().toString(), selectedLlmUsage, accessContext,
                entityCandidates);
        return ResponseEntity.accepted().build();
    }
}
