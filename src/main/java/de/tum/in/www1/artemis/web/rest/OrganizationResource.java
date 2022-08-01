package de.tum.in.www1.artemis.web.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Organization;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.OrganizationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.OrganizationService;
import de.tum.in.www1.artemis.web.rest.dto.OrganizationCountDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing the Organization entities
 */
@RestController
@RequestMapping("api/")
@PreAuthorize("hasRole('ADMIN')")
public class OrganizationResource {

    private final Logger log = LoggerFactory.getLogger(OrganizationResource.class);

    private static final String ENTITY_NAME = "organization";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final OrganizationService organizationService;

    private final OrganizationRepository organizationRepository;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    public OrganizationResource(OrganizationService organizationService, OrganizationRepository organizationRepository, UserRepository userRepository,
            CourseRepository courseRepository) {
        this.organizationService = organizationService;
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * POST organizations/:organizationId/courses/:courseId :
     * Add a course to an organization
     *
     * @param courseId the id of the course to add
     * @param organizationId the id of the organization where the course should be added
     * @return empty ResponseEntity with status 200 (OK), or 404 (Not Found) otherwise
     */
    @PostMapping("organizations/{organizationId}/courses/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> addCourseToOrganization(@PathVariable Long courseId, @PathVariable Long organizationId) {
        log.debug("REST request to add course to organization : {}", organizationId);
        Organization organization = organizationRepository.findByIdElseThrow(organizationId);
        courseRepository.addOrganizationToCourse(courseId, organization);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, courseId.toString())).build();
    }

    /**
     * DELETE organizations/:organizationId/courses/:courseId :
     * Remove a course from an organization
     *
     * @param courseId the id of the course to remove
     * @param organizationId the id of the organization from with the course should be removed
     * @return empty ResponseEntity with status 200 (OK), or 404 (Not Found) otherwise
     */
    @DeleteMapping("organizations/{organizationId}/courses/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeCourseFromOrganization(@PathVariable Long courseId, @PathVariable Long organizationId) {
        Organization organization = organizationRepository.findByIdElseThrow(organizationId);
        courseRepository.removeOrganizationFromCourse(courseId, organization);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, courseId.toString())).build();
    }

    /**
     * POST organizations/:organizationId/users/:userLogin :
     * Add a user to an organization
     *
     * @param userLogin the login of the user to add
     * @param organizationId the id of the organization where the user should be added
     * @return empty ResponseEntity with status 200 (OK), or 404 (Not Found) otherwise
     */
    @PostMapping("organizations/{organizationId}/users/{userLogin}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> addUserToOrganization(@PathVariable String userLogin, @PathVariable Long organizationId) {
        User user = userRepository.getUserByLoginElseThrow(userLogin);
        Organization organization = organizationRepository.findByIdElseThrow(organizationId);
        userRepository.addOrganizationToUser(user.getId(), organization);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, user.getLogin())).build();
    }

    /**
     * DELETE organizations/:organizationId/users/:userLogin :
     * Remove a user from an organization
     *
     * Keep in mind that removing a user from an organization does not remove it
     * from the Access Groups of a course if already added.
     *
     * @param userLogin the login of the user to remove
     * @param organizationId the id of the organization from with the user should be removed
     * @return empty ResponseEntity with status 200 (OK), or 404 (Not Found) otherwise
     */
    @DeleteMapping("organizations/{organizationId}/users/{userLogin}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeUserFromOrganization(@PathVariable String userLogin, @PathVariable Long organizationId) {
        log.debug("REST request to remove course to organization : {}", organizationId);
        User user = userRepository.getUserByLoginElseThrow(userLogin);
        Organization organization = organizationRepository.findByIdElseThrow(organizationId);
        userRepository.removeOrganizationFromUser(user.getId(), organization);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, user.getLogin())).build();
    }

    /**
     * POST organizations : Add a new organization
     *
     * @param organization the organization entity to add
     * @return the ResponseEntity containing the added organization with status 200 (OK), or 404 (Not Found) otherwise
     */
    @PostMapping("organizations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Organization> addOrganization(@RequestBody Organization organization) {
        log.debug("REST request to add new organization : {}", organization);
        Organization created = organizationService.add(organization);

        return ResponseEntity.ok().body(created);
    }

    /**
     * PUT organizations/:organizationId : Update an existing organization
     *
     * @param organizationId id of the organization in the body
     * @param organization the updated organization entity
     * @return the ResponseEntity containing the updated organization with status 200 (OK), or 404 (Not Found) otherwise
     */
    @PutMapping("organizations/{organizationId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Organization> updateOrganization(@PathVariable Long organizationId, @RequestBody Organization organization) {
        log.debug("REST request to update organization : {}", organization);
        if (organization.getId() == null) {
            throw new BadRequestAlertException("The ID of the organization in the RequestBody isn't set!", ENTITY_NAME, "noId");
        }
        if (!organization.getId().equals(organizationId)) {
            throw new BadRequestAlertException("organizationId in path doesn't match the one in the RequestBody!", ENTITY_NAME, "organizationIdDoesNotMatch");
        }
        organizationRepository.findByIdElseThrow(organization.getId());
        Organization updated = organizationService.update(organization);
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE organizations/:organizationId : Delete an existing organization
     *
     * @param organizationId the id of the organization to remove
     * @return empty ResponseEntity with status 200 (OK), or 404 (Not Found) otherwise
     */
    @DeleteMapping("organizations/{organizationId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteOrganization(@PathVariable Long organizationId) {
        log.debug("REST request to delete organization : {}", organizationId);
        organizationService.deleteOrganization(organizationId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, organizationId.toString())).build();
    }

    /**
     * GET organizations : Get all organizations
     *
     * @return ResponseEntity containing a list of all organizations with status 200 (OK)
     */
    @GetMapping("organizations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Organization>> getAllOrganizations() {
        log.debug("REST request to get all organizations");
        List<Organization> organizations = organizationRepository.findAll();
        return new ResponseEntity<>(organizations, HttpStatus.OK);
    }

    /**
     * GET organizations/:organizationId/count : Get the number of users and courses currently mapped to an organization
     *
     * @param organizationId the id of the organization to retrieve the number of users and courses
     * @return ResponseEntity containing a map containing the numbers of users and courses
     */
    @GetMapping("organizations/{organizationId}/count")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrganizationCountDTO> getNumberOfUsersAndCoursesByOrganization(@PathVariable long organizationId) {
        log.debug("REST request to get number of users and courses of organization : {}", organizationId);

        OrganizationCountDTO numberOfUsersAndCourses = new OrganizationCountDTO(organizationId, organizationRepository.getNumberOfUsersByOrganizationId(organizationId),
                organizationRepository.getNumberOfCoursesByOrganizationId(organizationId));

        return new ResponseEntity<>(numberOfUsersAndCourses, HttpStatus.OK);
    }

    /**
     * GET organizations/count-all : Get the number of users and courses currently mapped to each organization
     *
     * @return ResponseEntity containing a map containing the organizations' id as key and an inner map
     * containing their relative numbers of users and courses
     */
    @GetMapping("organizations/count-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<OrganizationCountDTO>> getNumberOfUsersAndCoursesOfAllOrganizations() {
        log.debug("REST request to get number of users and courses of all organizations");

        List<OrganizationCountDTO> result = new ArrayList<>();
        List<Organization> organizations = organizationRepository.findAll();
        for (Organization organization : organizations) {
            result.add(new OrganizationCountDTO(organization.getId(), organizationRepository.getNumberOfUsersByOrganizationId(organization.getId()),
                    organizationRepository.getNumberOfCoursesByOrganizationId(organization.getId())));
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    /**
     * GET organizations/:organizationId : Get an organization by its id
     *
     * @param organizationId the id of the organization to get
     * @return ResponseEntity containing the organization with status 200 (OK)
     * if exists, else with status 404 (Not Found)
     */
    @GetMapping("organizations/{organizationId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Organization> getOrganizationById(@PathVariable long organizationId) {
        log.debug("REST request to get organization : {}", organizationId);
        Organization organization = organizationRepository.findByIdElseThrow(organizationId);
        return new ResponseEntity<>(organization, HttpStatus.OK);
    }

    /**
     * GET organizations/:organizationId/full : Get an organization by its id with eagerly loaded users and courses
     *
     * @param organizationId the id of the organization to get
     * @return ResponseEntity containing the organization with eagerly loaded users and courses, with status 200 (OK)
     * if exists, else with status 404 (Not Found)
     */
    @GetMapping("organizations/{organizationId}/full")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Organization> getOrganizationByIdWithUsersAndCourses(@PathVariable long organizationId) {
        log.debug("REST request to get organization with users and courses : {}", organizationId);
        Organization organization = organizationRepository.findByIdWithEagerUsersAndCoursesElseThrow(organizationId);
        return new ResponseEntity<>(organization, HttpStatus.OK);
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

    /**
     * GET organizations/users/:userId : Get all organizations currently containing a given user
     *
     * @param userId the id of the user that the organizations should contain
     * @return ResponseEntity containing a set of organizations containing the given user
     */
    @GetMapping("organizations/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Set<Organization>> getAllOrganizationsByUser(@PathVariable Long userId) {
        log.debug("REST request to get all organizations of user : {}", userId);
        Set<Organization> organizations = organizationRepository.findAllOrganizationsByUserId(userId);
        return new ResponseEntity<>(organizations, HttpStatus.OK);
    }

    /**
     * GET organizations/:organizationId/title : Returns the title of the organization with the given id
     *
     * @param organizationId the id of the organization
     * @return the title of the organization wrapped in an ResponseEntity or 404 Not Found if no organization with that id exists
     */
    @GetMapping("organizations/{organizationId}/title")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> getOrganizationTitle(@PathVariable Long organizationId) {
        final var title = organizationRepository.getOrganizationTitle(organizationId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }
}
