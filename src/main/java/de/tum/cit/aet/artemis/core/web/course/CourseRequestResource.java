package de.tum.cit.aet.artemis.core.web.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.dto.CourseRequestCreateDTO;
import de.tum.cit.aet.artemis.core.dto.CourseRequestDTO;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.course.CourseRequestService;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/core/")
public class CourseRequestResource {

    private static final Logger log = LoggerFactory.getLogger(CourseRequestResource.class);

    private final CourseRequestService courseRequestService;

    public CourseRequestResource(CourseRequestService courseRequestService) {
        this.courseRequestService = courseRequestService;
    }

    /**
     * POST /course-requests : create a new course request for the logged-in user.
     *
     * @param courseRequest the request payload
     * @return the created course request
     */
    @PostMapping("course-requests")
    @EnforceAtLeastStudent
    public ResponseEntity<CourseRequestDTO> createCourseRequest(@Valid @RequestBody CourseRequestCreateDTO courseRequest) throws URISyntaxException {
        log.debug("REST request to create course request for course {}", courseRequest.shortName());
        CourseRequestDTO result = courseRequestService.createCourseRequest(courseRequest);
        return ResponseEntity.created(new URI("/api/core/course-requests/" + result.id())).body(result);
    }
}
