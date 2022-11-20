package de.tum.in.www1.artemis.web.rest;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Organization;
import de.tum.in.www1.artemis.repository.OrganizationRepository;

/**
 * REST controller for managing the Organization entities
 */
@RestController
@RequestMapping("api/")
public class OrganizationResource {

    private final Logger log = LoggerFactory.getLogger(OrganizationResource.class);

    private final OrganizationRepository organizationRepository;

    public OrganizationResource(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    /**
     * GET organizations/courses/:courseId : Get all organizations currently containing a given course
     *
     * @param courseId the id of the course that the organizations should contain
     * @return ResponseEntity containing a set of organizations containing the given course
     */
    @GetMapping("organizations/courses/{courseId}")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Set<Organization>> getAllOrganizationsByCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all organizations of course : {}", courseId);
        Set<Organization> organizations = organizationRepository.findAllOrganizationsByCourseId(courseId);
        return new ResponseEntity<>(organizations, HttpStatus.OK);
    }
}
