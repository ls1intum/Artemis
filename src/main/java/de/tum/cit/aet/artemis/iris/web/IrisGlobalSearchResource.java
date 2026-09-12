package de.tum.cit.aet.artemis.iris.web;

import java.security.Principal;
import java.util.List;

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
     * @param requestDTO the search request containing query, limit, and optional courseIds filter
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of search results
     */
    @PostMapping("lecture-search")
    @EnforceAtLeastStudent
    public ResponseEntity<List<PyrisLectureSearchResultDTO>> search(@RequestBody @Valid GlobalSearchLectureRequestDTO requestDTO) {
        var courseIds = requestDTO.courseIds();
        if (courseIds != null && !courseIds.isEmpty()) {
            courseIds = irisSettingsService.filterCourseIdsWithIrisEnabled(courseIds);
            if (courseIds.isEmpty()) {
                // suppress the error alert with skipAlert: true so that the client can fall back to its standard metadata search
                throw new AccessForbiddenAlertException(ErrorConstants.DEFAULT_TYPE, "Iris is disabled for the requested courses", ENTITY_NAME, "iris.course_disabled", true);
            }
        }
        var user = userRepository.getUserWithCourseRolesAndAuthorities();
        var accessContext = irisAccessContextService.resolveAccessContext(user);
        return ResponseEntity.ok(pyrisConnectorService.searchLectures(requestDTO.query(), requestDTO.limit(), courseIds, accessContext));
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
