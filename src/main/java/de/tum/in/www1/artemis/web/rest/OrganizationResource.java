package de.tum.in.www1.artemis.web.rest;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Organization;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.OrganizationRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

/**
 * REST controller for managing the Organization entities
 */
@RestController
public class OrganizationResource {

    private final Logger log = LoggerFactory.getLogger(OrganizationResource.class);

    private final OrganizationRepository organizationRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    public OrganizationResource(OrganizationRepository organizationRepository, AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository) {
        this.organizationRepository = organizationRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
    }

    /**
     * GET organizations/courses/:courseId : Get all organizations currently containing a given course
     *
     * @param courseId the id of the course that the organizations should contain
     * @return ResponseEntity containing a set of organizations containing the given course
     */
    // TODO: change URL to organizations?courseId={courseId}
    @GetMapping("organizations/courses/{courseId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Set<Organization>> getAllOrganizationsByCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all organizations of course : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        Set<Organization> organizations = organizationRepository.findAllOrganizationsByCourseId(courseId);
        return new ResponseEntity<>(organizations, HttpStatus.OK);
    }
}
