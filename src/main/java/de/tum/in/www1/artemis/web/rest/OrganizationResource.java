package de.tum.in.www1.artemis.web.rest;

import java.util.*;

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
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing the Organization entities
 */
@RestController
@RequestMapping("/api")
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
     * POST /organizations/course/:courseId/organization/:organizationId :
     * Add a course to an organization
     *
     * @param courseId the id of the course to add
     * @param organizationId the id of the organization where the course should be added
     * @return empty ResponseEntity with status 200 (OK), or 404 (Not Found) otherwise
     */
    @PostMapping("/organizations/course/{courseId}/organization/{organizationId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> addCourseToOrganization(@PathVariable Long courseId, @PathVariable Long organizationId) {
        log.debug("REST request to add course to organization : {}", organizationId);
        Organization organization = organizationRepository.findOneOrElseThrow(organizationId);
        courseRepository.addOrganizationToCourse(courseId, organization);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, courseId.toString())).build();
    }

    /**
     * DELETE /organizations/course/:courseId/organization/:organizationId :
     * Remove a course from an organization
     *
     * @param courseId the id of the course to remove
     * @param organizationId the id of the organization from with the course should be removed
     * @return empty ResponseEntity with status 200 (OK), or 404 (Not Found) otherwise
     */
    @DeleteMapping("/organizations/course/{courseId}/organization/{organizationId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> removeCourseToOrganization(@PathVariable Long courseId, @PathVariable Long organizationId) {
        Organization organization = organizationRepository.findOneOrElseThrow(organizationId);
        courseRepository.removeOrganizationFromCourse(courseId, organization);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, courseId.toString())).build();
    }

    /**
     * POST /organizations/user/:userlogin/organization/:organizationId :
     * Add a user to an organization
     *
     * @param userLogin the login of the user to add
     * @param organizationId the id of the organization where the user should be added
     * @return empty ResponseEntity with status 200 (OK), or 404 (Not Found) otherwise
     */
    @PostMapping("/organizations/user/{userLogin}/organization/{organizationId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> addUserToOrganization(@PathVariable String userLogin, @PathVariable Long organizationId) {
        User user = userRepository.getUserByLoginElseThrow(userLogin);
        Organization organization = organizationRepository.findOneOrElseThrow(organizationId);
        userRepository.addOrganizationToUser(user.getId(), organization);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, user.getLogin())).build();
    }

    /**
     * DELETE /organizations/user/:userLogin/organization/:organizationId :
     * Remove a user from an organization
     *
     * @param userLogin the login of the user to remove
     * @param organizationId the id of the organization from with the user should be removed
     * @return empty ResponseEntity with status 200 (OK), or 404 (Not Found) otherwise
     */
    @DeleteMapping("/organizations/user/{userLogin}/organization/{organizationId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> removeUserFromOrganization(@PathVariable String userLogin, @PathVariable Long organizationId) {
        log.debug("REST request to remove course to organization : {}", organizationId);
        User user = userRepository.getUserByLoginElseThrow(userLogin);
        Organization organization = organizationRepository.findOneOrElseThrow(organizationId);
        userRepository.removeOrganizationFromUser(user.getId(), organization);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, user.getLogin())).build();
    }

    /**
     * POST /organizations/add : Add a new organization
     *
     * @param organization the organization entity to add
     * @return the ResponseEntity containing the added organization with status 200 (OK), or 404 (Not Found) otherwise
     */
    @PostMapping("/organizations/add")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Organization> addOrganization(@RequestBody Organization organization) {
        log.debug("REST request to add new organization : {}", organization);
        Organization created = organizationService.add(organization);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, created.getName())).body(created);
    }

    /**
     * PUT /organizations/update : Update an existing organization
     *
     * @param organization the updated organization entity
     * @return the ResponseEntity containing the updated organization with status 200 (OK), or 404 (Not Found) otherwise
     */
    @PutMapping("/organizations/update")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Organization> updateOrganization(@RequestBody Organization organization) {
        log.debug("REST request to update organization : {}", organization);
        if (organization.getId() != null && organizationRepository.findOneOrElseThrow(organization.getId()) != null) {
            Organization updated = organizationService.update(organization);
            return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, updated.getName())).body(updated);
        }
        else {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The organization to update doesn't have an ID.", "NoIdProvided")).body(null);
        }
    }

    /**
     * DELETE /organizations/delete/:organizationId : Delete an existing organization
     *
     * @param organizationId the id of the organization to remove
     * @return empty ResponseEntity with status 200 (OK), or 404 (Not Found) otherwise
     */
    @DeleteMapping("/organizations/delete/{organizationId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> deleteOrganization(@PathVariable Long organizationId) {
        log.debug("REST request to delete organization : {}", organizationId);
        organizationService.deleteOrganization(organizationId);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, organizationId.toString())).build();
    }

    /**
     * GET /organizations/all : Get all organizations
     *
     * @return ResponseEntity containing a list of all organizations with status 200 (OK)
     */
    @GetMapping("/organizations/all")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<List<Organization>> getAllOrganizations() {
        log.debug("REST request to get all organizations");
        List<Organization> organizations = organizationRepository.findAll();
        return new ResponseEntity<>(organizations, HttpStatus.OK);
    }

    /**
     * GET /organizations/:organizationId/count : Get the number of users and courses currently mapped to an organization
     *
     * @param organizationId the id of the organization to retrieve the number of users and courses
     * @return ResponseEntity containing a map containing the numbers of users and courses
     */
    @GetMapping("/organizations/{organizationId}/count")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> getNumberOfUsersAndCoursesByOrganization(@PathVariable long organizationId) {
        log.debug("REST request to get number of users and courses of organization : {}", organizationId);
        Map<String, Long> numberOfUsersAndCourses = new HashMap<>();
        numberOfUsersAndCourses.put("users", organizationRepository.getNumberOfUsersByOrganizationId(organizationId));
        numberOfUsersAndCourses.put("courses", organizationRepository.getNumberOfCoursesByOrganizationId(organizationId));
        return new ResponseEntity<>(numberOfUsersAndCourses, HttpStatus.OK);
    }

    /**
     * GET /organizations/allCount : Get the number of users and courses currently mapped to each organization
     *
     * @return ResponseEntity containing a map containing the organizations' id as key and an inner map
     * containing their relative numbers of users and courses
     */
    @GetMapping("/organizations/allCount")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<Long, Map<String, Long>>> getNumberOfUsersAndCoursesOfAllOrganizations() {
        log.debug("REST request to get number of users and courses of all organizations");
        Map<Long, Map<String, Long>> result = new HashMap<>();

        List<Organization> organizations = organizationRepository.findAll();
        for (Organization organization : organizations) {
            Map<String, Long> numberOfUsersAndCourses = new HashMap<>();
            numberOfUsersAndCourses.put("users", organizationRepository.getNumberOfUsersByOrganizationId(organization.getId()));
            numberOfUsersAndCourses.put("courses", organizationRepository.getNumberOfCoursesByOrganizationId(organization.getId()));
            result.put(organization.getId(), numberOfUsersAndCourses);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    /**
     * GET /organizations/:organizationId : Get an organization by its id
     *
     * @param organizationId the id of the organization to get
     * @return ResponseEntity containing the organization with status 200 (OK)
     * if exists, else with status 404 (Not Found)
     */
    @GetMapping("/organizations/{organizationId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Organization> getOrganizationById(@PathVariable long organizationId) {
        log.debug("REST request to get organization : {}", organizationId);
        Organization organization = organizationRepository.findOneOrElseThrow(organizationId);
        return new ResponseEntity<>(organization, HttpStatus.OK);
    }

    /**
     * GET /organizations/:organizationId/full : Get an organization by its id with eagerly loaded users and courses
     *
     * @param organizationId the id of the organization to get
     * @return ResponseEntity containing the organization with eagerly loaded users and courses, with status 200 (OK)
     * if exists, else with status 404 (Not Found)
     */
    @GetMapping("/organizations/{organizationId}/full")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Organization> getOrganizationByIdWithUsersAndCourses(@PathVariable long organizationId) {
        log.debug("REST request to get organization with users and courses : {}", organizationId);
        Organization organization = organizationRepository.findOneWithEagerUsersAndCoursesOrElseThrow(organizationId);
        return new ResponseEntity<>(organization, HttpStatus.OK);
    }

    /**
     * GET /organizations/course/:courseId : Get all organizations currently containing a given course
     *
     * @param courseId the id of the course that the organizations should contain
     * @return ResponseEntity containing a set of organizations containing the given course
     */
    @GetMapping("/organizations/course/{courseId}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<Organization>> getAllOrganizationsByCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all organizations of course : {}", courseId);
        Set<Organization> organizations = organizationRepository.findAllOrganizationsByCourseId(courseId);
        return new ResponseEntity<>(organizations, HttpStatus.OK);
    }

    /**
     * GET /organizations/user/:userId : Get all organizations currently containing a given user
     *
     * @param userId the id of the user that the organizations should contain
     * @return ResponseEntity containing a set of organizations containing the given user
     */
    @GetMapping("/organizations/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Set<Organization>> getAllOrganizationsByUser(@PathVariable Long userId) {
        log.debug("REST request to get all organizations of user : {}", userId);
        Set<Organization> organizations = organizationRepository.findAllOrganizationsByUserId(userId);
        return new ResponseEntity<>(organizations, HttpStatus.OK);
    }
}
