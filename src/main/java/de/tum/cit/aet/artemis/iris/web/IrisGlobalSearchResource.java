package de.tum.cit.aet.artemis.iris.web;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.LimitRequestsPerMinute;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UsageInteraction;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.globalsearch.api.SearchableEntityPrefetchApi;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.IrisAccessContextService;
import de.tum.cit.aet.artemis.iris.service.IrisSearchScopeService;
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
@FeatureUsage(UserFeature.IRIS_SEARCH_ANSWER)
@RestController
@RequestMapping("api/iris/")
public class IrisGlobalSearchResource {

    private static final Logger log = LoggerFactory.getLogger(IrisGlobalSearchResource.class);

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    private final UserRepository userRepository;

    private final UserAiPreferenceService userAiPreferenceService;

    private final IrisAccessContextService irisAccessContextService;

    private final Optional<SearchableEntityPrefetchApi> searchableEntityPrefetchApi;

    private final IrisSearchScopeService irisSearchScopeService;

    /** Entity candidates handed to the answer pipeline; recall is deliberately deep, the reranker judges. */
    private static final int ENTITY_CANDIDATE_LIMIT = 25;

    public IrisGlobalSearchResource(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, UserRepository userRepository,
            UserAiPreferenceService userAiPreferenceService, IrisAccessContextService irisAccessContextService, Optional<SearchableEntityPrefetchApi> searchableEntityPrefetchApi,
            IrisSearchScopeService irisSearchScopeService) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.userAiPreferenceService = userAiPreferenceService;
        this.pyrisJobService = pyrisJobService;
        this.userRepository = userRepository;
        this.irisAccessContextService = irisAccessContextService;
        this.searchableEntityPrefetchApi = searchableEntityPrefetchApi;
        this.irisSearchScopeService = irisSearchScopeService;
    }

    /**
     * POST api/iris/lecture-search: Search for lecture units using Pyris.
     * <p>
     * This is search, not an Iris feature: it covers every course the caller can access, including courses with Iris switched off in the course
     * settings, because that setting only switches off the Iris chat and the other interactive Iris features.
     *
     * Requires the AI opt-in like the Iris answer, because the query is processed by AI models in Pyris.
     *
     * @param requestDTO the search request containing query, limit, and the optional course filters
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of search results
     */
    @FeatureUsage(UserFeature.GLOBAL_SEARCH)
    @UsageInteraction(FeatureInteraction.VIEW)
    @PostMapping("lecture-search")
    @EnforceAtLeastStudent
    @LimitRequestsPerMinute(type = RateLimitType.CONTENT_SEARCH)
    public ResponseEntity<List<PyrisLectureSearchResultDTO>> search(@RequestBody @Valid GlobalSearchLectureRequestDTO requestDTO) {
        var user = userRepository.getUserWithCourseRolesAndAuthorities();
        userAiPreferenceService.hasOptedIntoLlmUsageElseThrow(user.getId());
        var accessContext = irisAccessContextService.resolveAccessContext(user);
        var scope = irisSearchScopeService.resolveScope(requestDTO.courseIds(), requestDTO.excludeCourseIds(), accessContext, false);
        if (scope.searchesNothing()) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(pyrisConnectorService.searchLectures(requestDTO.query(), requestDTO.limit(), scope.courseIds(), scope.excludeCourseIds(), accessContext));
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
        // Resolved BEFORE the job is registered: an all-courses-Iris-disabled request is a
        // genuine rejection (AccessForbiddenAlertException from resolveScope), not a transient
        // failure, and must never leave an orphaned job behind for that case.
        var scope = irisSearchScopeService.resolveScope(requestDTO.courseIds(), requestDTO.excludeCourseIds(), accessContext, true);
        // Entity candidates are pre-fetched with the palette's access filtering, because channel
        // membership, exam registrations and role-dependent release rules only exist in the Artemis
        // database; Pyris renders them into cards and reranks them against the lecture content.
        // Pre-fetched BEFORE the job is registered, same reasoning as scope above: its strict
        // access-filter path can throw synchronously on a stale or inaccessible course ID, and a
        // repository or mapping failure can escape too, none of it a transport-level ambiguity, so
        // none of it may leave an orphaned job behind either. A genuine Weaviate hiccup is still
        // swallowed below rather than failing the whole answer, since the lecture-content-only
        // answer could still succeed.
        // searchesNothing (every requested course excluded or inaccessible) still answers rather than short-circuiting
        // like /lecture-search does, but "search nothing" means nothing: entity candidates are skipped
        // entirely rather than falling back to an unscoped candidate search (an empty, non-null
        // courseIds list is read as "unscoped" by the access filter, not "scoped to nothing").
        List<PyrisEntityCandidateDTO> entityCandidates = scope.searchesNothing() ? List.of() : fetchEntityCandidates(user, requestDTO, scope.courseIds(), scope.excludeCourseIds());
        var job = pyrisJobService.addGlobalSearchAnswerJob(principal.getName(), requestDTO.runId().toString());
        // Before dispatch, like a chat run: continuing the answer in the chat pins its citations to the material it was written from. An unrestricted, unscoped search
        // has no bounded set of courses to capture, so its citations stay unverified in the chat; reading the material when the answer arrives could pin them to a
        // version the answer was not written from.
        if (scope.courseIds() != null && !scope.searchesNothing()) {
            pyrisJobService.captureGlobalSearchMaterialVersions(principal.getName(), requestDTO.runId().toString(), scope.courseIds());
        }
        // Note: do NOT remove the job on exception here. Transport-level failures are ambiguous —
        // Pyris may have received the request and already started the pipeline. Removing the job
        // would break WebSocket routing for any callbacks that arrive later.
        // Jobs expire automatically via the Hazelcast TTL (default 5 minutes).
        pyrisConnectorService.executeGlobalSearchIrisAnswer(requestDTO.query(), requestDTO.limit(), job, selectedLlmUsage, accessContext, entityCandidates, scope.courseIds(),
                scope.excludeCourseIds(), scope.searchesNothing());
        return ResponseEntity.accepted().build();
    }

    private List<PyrisEntityCandidateDTO> fetchEntityCandidates(User user, GlobalSearchAskRequestDTO requestDTO, @Nullable List<Long> courseIds,
            @Nullable List<Long> excludeCourseIds) {
        if (searchableEntityPrefetchApi.isEmpty()) {
            return List.of();
        }
        try {
            var excludedIds = excludeCourseIds == null ? List.<Long>of() : excludeCourseIds;
            return searchableEntityPrefetchApi.get().prefetchCandidates(user, requestDTO.query(), ENTITY_CANDIDATE_LIMIT, courseIds, excludedIds).stream()
                    .map(PyrisEntityCandidateDTO::of).toList();
        }
        catch (WeaviateException e) {
            log.warn("Entity candidate prefetch failed for global search run {}; answering from lecture content only: {}", requestDTO.runId(), e.getMessage());
            return List.of();
        }
    }
}
