package de.tum.cit.aet.artemis.iris.web;

import java.security.Principal;
import java.util.List;

import jakarta.validation.Valid;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.ErrorConstants;
import de.tum.cit.aet.artemis.core.security.RateLimitType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.LimitRequestsPerMinute;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.IrisAccessContextService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.GlobalSearchAskRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.GlobalSearchLectureRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisAccessContextDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

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

    private static final String ENTITY_NAME = "iris";

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    private final UserRepository userRepository;

    private final UserAiPreferenceService userAiPreferenceService;

    private final IrisAccessContextService irisAccessContextService;

    private final IrisSettingsService irisSettingsService;

    public IrisGlobalSearchResource(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService, UserRepository userRepository,
            UserAiPreferenceService userAiPreferenceService, IrisAccessContextService irisAccessContextService, IrisSettingsService irisSettingsService) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.userAiPreferenceService = userAiPreferenceService;
        this.pyrisJobService = pyrisJobService;
        this.userRepository = userRepository;
        this.irisAccessContextService = irisAccessContextService;
        this.irisSettingsService = irisSettingsService;
    }

    /**
     * POST api/iris/lecture-search: Search for lecture units using Pyris.
     * <p>
     * Courses with Iris switched off in the course settings are dropped from the requested scope, so content search respects the same toggle as every other Iris feature.
     * Disabling a course does not remove what was already ingested, which is why the scope has to be narrowed here rather than relying on an empty index.
     *
     * @param requestDTO the search request containing query, limit, and the optional course filters
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of search results
     */
    @PostMapping("lecture-search")
    @EnforceAtLeastStudent
    public ResponseEntity<List<PyrisLectureSearchResultDTO>> search(@RequestBody @Valid GlobalSearchLectureRequestDTO requestDTO) {
        var user = userRepository.getUserWithCourseRolesAndAuthorities();
        var accessContext = irisAccessContextService.resolveAccessContext(user);
        var excludedCourseIds = requestDTO.excludeCourseIds() == null ? List.<Long>of() : requestDTO.excludeCourseIds();
        var scope = lectureSearchScope(requestDTO.courseIds(), excludedCourseIds, accessContext);
        if (scope.searchesNothing()) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(pyrisConnectorService.searchLectures(requestDTO.query(), requestDTO.limit(), scope.courseIds(), scope.excludeCourseIds(), accessContext));
    }

    /**
     * The resolved search scope: the courses to search, the exclusions Pyris still has to apply, and whether there is anything left to search at all.
     *
     * @param courseIds        the courses to search, or {@code null} to leave an unrestricted caller unscoped
     * @param excludeCourseIds the courses Pyris has to hide itself, or {@code null} when Artemis already removed them from {@link #courseIds}
     * @param searchesNothing  whether the caller's own exclusions left no course to search, which is answered without asking Pyris
     */
    private record LectureSearchScope(@Nullable List<Long> courseIds, @Nullable List<Long> excludeCourseIds, boolean searchesNothing) {

        static LectureSearchScope nothing() {
            return new LectureSearchScope(null, null, true);
        }

        static LectureSearchScope of(@Nullable List<Long> courseIds, @Nullable List<Long> excludeCourseIds) {
            return new LectureSearchScope(courseIds, excludeCourseIds, false);
        }
    }

    /**
     * Narrows a search to the courses whose Iris course settings are enabled.
     * <p>
     * An unscoped request cannot be forwarded untouched. Pyris falls back to the access context when it receives no
     * course list, and that context is built from course roles alone, so lecture content belonging to a course whose
     * instructor switched Iris off would still be searchable. Disabling never removes what was already ingested, which
     * is why the scope has to be narrowed here rather than relying on an empty index.
     * <p>
     * The narrowed scope is never forwarded empty: {@code PyrisLectureSearchRequestDTO} omits an empty list on the wire and
     * Pyris reads an absent list as unscoped, so a caller whose courses all have Iris switched off is refused instead.
     *
     * @param requestedCourseIds the course IDs the client asked for, {@code null} or empty for an unscoped search
     * @param excludedCourseIds  the course IDs the client asked to hide, empty when nothing is hidden
     * @param accessContext      the caller's resolved access context
     * @return the scope to search, see {@link LectureSearchScope}
     */
    private LectureSearchScope lectureSearchScope(@Nullable List<Long> requestedCourseIds, List<Long> excludedCourseIds, PyrisAccessContextDTO accessContext) {
        boolean isUnscoped = requestedCourseIds == null || requestedCourseIds.isEmpty();
        if (isUnscoped && accessContext.unrestricted()) {
            // An unrestricted caller carries no course list to narrow, so Pyris keeps its own no-ceiling behaviour. With no
            // ceiling to subtract from, it is also the only caller whose exclusions have to be applied by the query itself.
            return LectureSearchScope.of(null, excludedCourseIds.isEmpty() ? null : excludedCourseIds);
        }
        // An unscoped request is narrowed from every course the caller can access, a scoped one from what it asked for.
        var candidateCourseIds = isUnscoped ? accessContext.courseIds() : requestedCourseIds;
        // Every other caller travels with a ceiling, so a hidden course is subtracted here and never named to Pyris.
        var scopedCourseIds = candidateCourseIds.stream().filter(courseId -> !excludedCourseIds.contains(courseId)).toList();
        if (scopedCourseIds.isEmpty() && !candidateCourseIds.isEmpty()) {
            // The exclusions removed every course in scope. An empty list is dropped on the wire and read as unscoped, so
            // answering here is what keeps "hide all of them" from searching all of them.
            return LectureSearchScope.nothing();
        }
        var enabledCourseIds = irisSettingsService.filterCourseIdsWithIrisEnabled(scopedCourseIds);
        // A caller without any course has nothing to narrow; its access context is empty too, so Pyris searches nothing.
        if (enabledCourseIds.isEmpty() && !scopedCourseIds.isEmpty()) {
            // suppress the error alert with skipAlert: true so that the client can fall back to its standard metadata search
            throw new AccessForbiddenAlertException(ErrorConstants.DEFAULT_TYPE, "Iris is disabled for every course in the search scope", ENTITY_NAME, "iris.course_disabled",
                    true);
        }
        return LectureSearchScope.of(enabledCourseIds, null);
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
        pyrisConnectorService.executeGlobalSearchIrisAnswer(requestDTO.query(), requestDTO.limit(), requestDTO.runId().toString(), selectedLlmUsage, accessContext);
        return ResponseEntity.accepted().build();
    }
}
