package de.tum.cit.aet.artemis.core.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.dto.CourseRequestCreateDTO;
import de.tum.cit.aet.artemis.core.dto.CourseRequestDTO;
import de.tum.cit.aet.artemis.core.dto.CourseRequestDecisionDTO;
import de.tum.cit.aet.artemis.core.dto.CourseRequestsAdminOverviewDTO;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.course.CourseRequestService;

@Profile(PROFILE_CORE)
@EnforceAdmin
@Lazy
@RestController
@RequestMapping("api/core/admin/")
public class AdminCourseRequestResource {

    private static final Logger log = LoggerFactory.getLogger(AdminCourseRequestResource.class);

    private final CourseRequestService courseRequestService;

    public AdminCourseRequestResource(CourseRequestService courseRequestService) {
        this.courseRequestService = courseRequestService;
    }

    /**
     * GET /course-requests/overview : get admin overview of course requests.
     * Returns pending requests with instructor course count and decided requests with pagination.
     *
     * @param decidedPage     the page number for decided requests (0-indexed, default 0)
     * @param decidedPageSize the page size for decided requests (default 20)
     * @return the admin overview containing pending and decided requests
     */
    @GetMapping("course-requests/overview")
    public ResponseEntity<CourseRequestsAdminOverviewDTO> getAdminOverview(@RequestParam(defaultValue = "0") int decidedPage,
            @RequestParam(defaultValue = "20") int decidedPageSize) {
        return ResponseEntity.ok(courseRequestService.getAdminOverview(decidedPage, decidedPageSize));
    }

    /**
     * POST /course-requests/{requestId}/accept : accept a course request and create a course.
     *
     * @param requestId the request id
     * @return the updated course request DTO
     */
    @PostMapping("course-requests/{requestId}/accept")
    public ResponseEntity<CourseRequestDTO> accept(@PathVariable long requestId) {
        log.debug("REST request to accept course request {}", requestId);
        return ResponseEntity.ok(courseRequestService.acceptRequest(requestId));
    }

    /**
     * POST /course-requests/{requestId}/reject : reject a course request with a reason.
     *
     * @param requestId the request id
     * @param decision  the rejection reason
     * @return the updated course request DTO
     */
    @PostMapping("course-requests/{requestId}/reject")
    public ResponseEntity<CourseRequestDTO> reject(@PathVariable long requestId, @Valid @RequestBody CourseRequestDecisionDTO decision) {
        log.debug("REST request to reject course request {}", requestId);
        return ResponseEntity.ok(courseRequestService.rejectRequest(requestId, decision.reason()));
    }

    /**
     * PUT /course-requests/{requestId} : update a pending course request.
     * Allows admins to edit the course request before accepting it.
     *
     * @param requestId the request id
     * @param updateDTO the updated course request data
     * @return the updated course request DTO
     */
    @PutMapping("course-requests/{requestId}")
    public ResponseEntity<CourseRequestDTO> update(@PathVariable long requestId, @Valid @RequestBody CourseRequestCreateDTO updateDTO) {
        log.debug("REST request to update course request {}", requestId);
        return ResponseEntity.ok(courseRequestService.updateCourseRequest(requestId, updateDTO));
    }
}
