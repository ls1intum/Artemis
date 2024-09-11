package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.OrganizationRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.domain.Organization;

/**
 * REST controller for managing the Organization entities
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class OrganizationResource {

    private static final Logger log = LoggerFactory.getLogger(OrganizationResource.class);

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
    @EnforceAtLeastTutor
    public ResponseEntity<Set<Organization>> getAllOrganizationsByCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all organizations of course : {}", courseId);
        Set<Organization> organizations = organizationRepository.findAllOrganizationsByCourseId(courseId);
        return new ResponseEntity<>(organizations, HttpStatus.OK);
    }
}
