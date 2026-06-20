package de.tum.cit.aet.artemis.videosource.web;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.videosource.config.GocastEnabled;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingStatus;
import de.tum.cit.aet.artemis.videosource.domain.GocastCourseBinding;
import de.tum.cit.aet.artemis.videosource.dto.GocastBindingDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastBindingWithApprovalDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastCourseDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastCreateBindingRequestDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastPlaybackTokenDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastStreamDTO;
import de.tum.cit.aet.artemis.videosource.service.GocastApprovalLinkService;
import de.tum.cit.aet.artemis.videosource.service.GocastBindingService;
import de.tum.cit.aet.artemis.videosource.service.GocastConnectorService;
import de.tum.cit.aet.artemis.videosource.service.GocastIntegrationException;

/**
 * REST controller for the gocast (TUM Live) service-account integration.
 * <p>
 * Exposes endpoints for:
 * <ul>
 * <li>Listing the current instructor's administered TUM Live courses (EP1).</li>
 * <li>Listing the streams of the bound gocast course (EP8).</li>
 * <li>Creating, reading, and deleting a gocast course binding.</li>
 * <li>Flipping a {@code PENDING} binding to {@code ACTIVE} when the server-to-server check (EP7)
 * confirms the service account is a course admin.</li>
 * <li>Obtaining a signed playback token for a stream on behalf of a student (EP2).</li>
 * </ul>
 * <p>
 * This controller is only registered when the gocast integration is fully configured;
 * see {@link GocastEnabled}.
 */
@Lazy
@RestController
@RequestMapping("api/videosource/")
@Conditional(GocastEnabled.class)
public class GocastIntegrationResource {

    private static final Logger log = LoggerFactory.getLogger(GocastIntegrationResource.class);

    /**
     * Default TTL (in seconds) requested when obtaining a playback token from gocast.
     * The gocast server clamps this value to its configured bounds.
     */
    private static final int DEFAULT_PLAYBACK_TOKEN_TTL_SECONDS = 7200;

    private final GocastConnectorService connectorService;

    private final GocastBindingService bindingService;

    private final GocastApprovalLinkService approvalLinkService;

    private final UserRepository userRepository;

    @Value("${server.url}")
    private String serverUrl;

    public GocastIntegrationResource(GocastConnectorService connectorService, GocastBindingService bindingService, GocastApprovalLinkService approvalLinkService,
            UserRepository userRepository) {
        this.connectorService = connectorService;
        this.bindingService = bindingService;
        this.approvalLinkService = approvalLinkService;
        this.userRepository = userRepository;
    }

    /**
     * GET /courses/:courseId/tumlive-courses : List the TUM Live courses administered by the current instructor.
     * <p>
     * Calls EP1 on gocast on behalf of the current user (identified by their Artemis login / LRZ ID).
     * The optional {@code year} and {@code term} query parameters filter the results; when omitted
     * gocast returns all administered courses.
     *
     * @param courseId the Artemis course id (used for role check)
     * @param year     optional academic year filter (e.g. {@code 2026})
     * @param term     optional teaching term filter ({@code "W"} or {@code "S"})
     * @return 200 with the list of administered TUM Live courses; 502/503 if gocast is unreachable
     */
    @GetMapping("courses/{courseId}/tumlive-courses")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<GocastCourseDTO>> listAdministeredCourses(@PathVariable long courseId, @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String term) {
        log.debug("REST request to list administered TUM Live courses for Artemis course {}", courseId);
        String lrzId = userRepository.getUserWithGroupsAndAuthorities().getLogin();
        try {
            List<GocastCourseDTO> courses = connectorService.listAdministeredCourses(lrzId, year != null ? year : 0, term != null ? term : "");
            return ResponseEntity.ok(courses);
        }
        catch (GocastIntegrationException ex) {
            log.warn("EP1 listAdministeredCourses failed for course {}: {}", courseId, ex.getMessage());
            return ResponseEntity.status(ex.getUpstreamStatus()).build();
        }
    }

    /**
     * GET /courses/:courseId/tumlive-streams : List the streams of the bound gocast course.
     * <p>
     * Requires an {@code ACTIVE} binding for the course. A {@code PENDING} or {@code REVOKED} binding yields a
     * raw 403 from gocast (EP8 rejects the service account when it is not yet a confirmed admin), so we
     * guard early with a {@code 409 CONFLICT} — the same pattern as {@link #getPlaybackToken}.
     * Calls EP8 on gocast (bearer-only, no OBO).
     *
     * @param courseId the Artemis course id
     * @return 200 with the list of streams; 404 if no binding exists; 409 if the binding is not
     *         {@code ACTIVE}; 502/503 if gocast is unreachable
     */
    @GetMapping("courses/{courseId}/tumlive-streams")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<GocastStreamDTO>> listCourseStreams(@PathVariable long courseId) {
        log.debug("REST request to list TUM Live streams for Artemis course {}", courseId);
        GocastCourseBinding binding = bindingService.getBindingByCourseIdElseThrow(courseId);
        if (binding.getStatus() != GocastBindingStatus.ACTIVE) {
            // EP8 requires the service account to be a confirmed course admin (ACTIVE binding). Reject early
            // with a clear 409 instead of forwarding the request and surfacing an opaque upstream 403.
            log.debug("Stream list requested for non-ACTIVE binding (status={}) on course {}", binding.getStatus(), courseId);
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        try {
            List<GocastStreamDTO> streams = connectorService.listCourseStreams(binding.getGocastCourseId());
            return ResponseEntity.ok(streams);
        }
        catch (GocastIntegrationException ex) {
            if (HttpStatus.FORBIDDEN.isSameCodeAs(ex.getUpstreamStatus())) {
                // EP8 returned 403 — the service account lost admin access. Mark the binding REVOKED
                // and return 409 CONFLICT so the UI can show the correct state.
                log.warn("EP8 listCourseStreams returned 403 for gocast course {} — marking binding REVOKED", binding.getGocastCourseId());
                bindingService.updateStatus(binding, GocastBindingStatus.REVOKED);
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
            log.warn("EP8 listCourseStreams failed for gocast course {}: {}", binding.getGocastCourseId(), ex.getMessage());
            return ResponseEntity.status(ex.getUpstreamStatus()).build();
        }
    }

    /**
     * POST /courses/:courseId/binding : Create a new gocast course binding.
     * <p>
     * Creates a {@code PENDING} binding and returns the binding DTO together with the gocast approval
     * link that the instructor must visit to authorize the Artemis service account as a course admin.
     *
     * @param courseId   the Artemis course id
     * @param requestDTO body containing {@code gocastCourseId} and {@code gocastCourseSlug}
     * @return 201 with the binding DTO and the approval URL
     */
    @PostMapping("courses/{courseId}/binding")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<GocastBindingWithApprovalDTO> createBinding(@PathVariable long courseId, @Valid @RequestBody @NotNull GocastCreateBindingRequestDTO requestDTO) {
        log.debug("REST request to create gocast binding for Artemis course {}: gocastCourseId={}", courseId, requestDTO.gocastCourseId());
        String instructorLrzId = userRepository.getUserWithGroupsAndAuthorities().getLogin();
        try {
            GocastCourseBinding binding = bindingService.createBinding(courseId, requestDTO.gocastCourseId(), requestDTO.gocastCourseSlug(), instructorLrzId);
            String callbackUrl = serverUrl + "/course-management/" + courseId + "/gocast-binding";
            String approvalUrl = approvalLinkService.buildApprovalLink(binding.getGocastCourseId(), callbackUrl);
            GocastBindingWithApprovalDTO response = new GocastBindingWithApprovalDTO(GocastBindingDTO.fromBinding(binding), approvalUrl);
            URI location = URI.create("/api/videosource/courses/" + courseId + "/binding");
            return ResponseEntity.created(location).body(response);
        }
        catch (GocastIntegrationException ex) {
            // EP1 threw (e.g. gocast returned 403/404/503) — propagate the upstream status instead of a generic 500.
            log.warn("EP1 listAdministeredCourses failed during createBinding for course {}: {}", courseId, ex.getMessage());
            return ResponseEntity.status(ex.getUpstreamStatus()).build();
        }
        catch (IllegalStateException ex) {
            log.warn("Cannot create binding for course {}: {}", courseId, ex.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    /**
     * GET /courses/:courseId/binding : Get the gocast binding and (if PENDING) verify it server-to-server.
     * <p>
     * Delegates the EP7 state-machine to {@link GocastBindingService#refreshStatusFromUpstream}: a
     * {@code PENDING} binding is flipped to {@code ACTIVE} only when EP7 confirms it, to {@code REVOKED}
     * on a {@code 403}, and is left unchanged on any transient gocast error. A binding is never set to
     * {@code ACTIVE} without EP7 confirming it.
     * <p>
     * For {@code PENDING} bindings, the response includes the {@code approvalUrl} so that the instructor
     * can re-open the TUM Live approval page after a page reload without having to recreate the binding.
     *
     * @param courseId the Artemis course id
     * @return 200 with the (possibly updated) binding DTO (including approvalUrl for PENDING); 404 if no binding exists
     */
    @GetMapping("courses/{courseId}/binding")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<GocastBindingWithApprovalDTO> getBinding(@PathVariable long courseId) {
        log.debug("REST request to get gocast binding for Artemis course {}", courseId);
        GocastCourseBinding binding = bindingService.getBindingByCourseIdElseThrow(courseId);
        GocastCourseBinding refreshed = bindingService.refreshStatusFromUpstream(binding);
        String approvalUrl = null;
        if (refreshed.getStatus() == GocastBindingStatus.PENDING) {
            String callbackUrl = serverUrl + "/course-management/" + courseId + "/gocast-binding";
            approvalUrl = approvalLinkService.buildApprovalLink(refreshed.getGocastCourseId(), callbackUrl);
        }
        return ResponseEntity.ok(new GocastBindingWithApprovalDTO(GocastBindingDTO.fromBinding(refreshed), approvalUrl));
    }

    /**
     * DELETE /courses/:courseId/binding : Remove the gocast binding for this course.
     * <p>
     * The service performs the lookup-and-delete in one path; a missing binding results in a
     * {@code 404 Not Found} (via {@code findByCourseIdElseThrow}).
     *
     * @param courseId the Artemis course id
     * @return 204 No Content on success; 404 if no binding exists
     */
    @DeleteMapping("courses/{courseId}/binding")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> deleteBinding(@PathVariable long courseId) {
        log.debug("REST request to delete gocast binding for Artemis course {}", courseId);
        bindingService.deleteBinding(courseId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /courses/:courseId/streams/:streamId/playback-tokens : Obtain a signed playback token.
     * <p>
     * Calls EP2 on gocast on behalf of the current user (identified by their Artemis login / LRZ ID).
     * gocast independently verifies both service-account course-admin access and the user's eligibility.
     * <p>
     * Requires an {@code ACTIVE} binding for the course.
     *
     * @param courseId the Artemis course id (used for role check and to look up the gocast course id)
     * @param streamId the gocast stream id for which a playback token is requested
     * @return 200 with signed playlist URLs and token TTL; 404 if no binding; 409 if the binding is not
     *         {@code ACTIVE}; 403 if user not eligible; 502/503 if gocast is unreachable
     */
    @PostMapping("courses/{courseId}/streams/{streamId}/playback-tokens")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<GocastPlaybackTokenDTO> getPlaybackToken(@PathVariable long courseId, @PathVariable long streamId) {
        log.debug("REST request to get playback token for Artemis course {}, stream {}", courseId, streamId);
        GocastCourseBinding binding = bindingService.getBindingByCourseIdElseThrow(courseId);
        if (binding.getStatus() != GocastBindingStatus.ACTIVE) {
            // EP2 requires the service account to be a confirmed course admin (ACTIVE binding). Reject early
            // with a clear 409 instead of forwarding the request and surfacing an opaque upstream error.
            log.debug("Playback token requested for non-ACTIVE binding (status={}) on course {}", binding.getStatus(), courseId);
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        String lrzId = userRepository.getUserWithGroupsAndAuthorities().getLogin();
        try {
            GocastPlaybackTokenDTO token = connectorService.getPlaybackToken(binding.getGocastCourseId(), streamId, DEFAULT_PLAYBACK_TOKEN_TTL_SECONDS, lrzId);
            return ResponseEntity.ok(token);
        }
        catch (GocastIntegrationException ex) {
            if (HttpStatus.FORBIDDEN.isSameCodeAs(ex.getUpstreamStatus())) {
                // Disambiguate the 403: it may mean either (a) the service account is no longer bound
                // (binding should be REVOKED) or (b) the student is not eligible (leave as ACTIVE).
                // The binding is only ever mutated when EP7 *definitively* reports not-bound. A transient
                // EP7 outage (503/timeout/5xx/401/etc.) must leave the binding ACTIVE and call no updateStatus.
                try {
                    boolean stillBound = connectorService.getBindingStatus(binding.getGocastCourseId());
                    if (!stillBound) {
                        // EP7 definitively reports unbound — revoke the binding and return 409 CONFLICT.
                        log.warn("EP2 returned 403 and EP7 reports unbound — marking binding REVOKED for gocast course {}", binding.getGocastCourseId());
                        bindingService.updateStatus(binding, GocastBindingStatus.REVOKED);
                        return ResponseEntity.status(HttpStatus.CONFLICT).build();
                    }
                    // EP7 confirms still bound — student is ineligible. Fall through to return the original EP2 403.
                    log.debug("EP2 returned 403 but EP7 confirms still bound — student {} is ineligible for stream {}", lrzId, streamId);
                }
                catch (GocastIntegrationException ep7Ex) {
                    if (HttpStatus.FORBIDDEN.isSameCodeAs(ep7Ex.getUpstreamStatus())) {
                        // EP7 itself returned 403 — the service account is definitively not a course admin
                        // anymore. Mark REVOKED and return 409 CONFLICT.
                        log.warn("EP2 returned 403 and EP7 returned 403 (definitively unbound) — marking binding REVOKED for gocast course {}", binding.getGocastCourseId());
                        bindingService.updateStatus(binding, GocastBindingStatus.REVOKED);
                        return ResponseEntity.status(HttpStatus.CONFLICT).build();
                    }
                    // EP7 failed transiently/ambiguously (503/timeout/5xx/401/etc.). Do NOT mutate the binding —
                    // leave it ACTIVE and fall through to surface the original EP2 403 to the caller.
                    log.warn("EP2 returned 403 but EP7 failed transiently (status={}) — leaving binding ACTIVE for gocast course {}: {}", ep7Ex.getUpstreamStatus(),
                            binding.getGocastCourseId(), ep7Ex.getMessage());
                }
            }
            log.warn("EP2 getPlaybackToken failed for course {}, stream {}: status={}", courseId, streamId, ex.getUpstreamStatus());
            return ResponseEntity.status(ex.getUpstreamStatus()).build();
        }
    }
}
