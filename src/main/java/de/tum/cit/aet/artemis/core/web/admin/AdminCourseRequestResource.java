package de.tum.cit.aet.artemis.core.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.dto.CourseRequestDTO;
import de.tum.cit.aet.artemis.core.dto.CourseRequestDecisionDTO;
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
     * GET /course-requests : get all course requests.
     *
     * @return list of course requests ordered by creation date
     */
    @GetMapping("course-requests")
    // TODO: consider pagination if the number of requests becomes too large
    public ResponseEntity<List<CourseRequestDTO>> getAll() {
        return ResponseEntity.ok(courseRequestService.findAll());
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
}
