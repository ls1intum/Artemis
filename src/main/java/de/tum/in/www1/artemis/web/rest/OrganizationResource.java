package de.tum.in.www1.artemis.web.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Organization;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.CourseService;
import de.tum.in.www1.artemis.service.OrganizationService;
import de.tum.in.www1.artemis.service.UserService;
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

    @Value("${artemis.user-management.organizations.enable-multiple-organizations:#{null}}")
    private Optional<Boolean> isMultiOrganizationEnabled;

    private final OrganizationService organizationService;

    private final UserService userService;

    private final CourseService courseService;

    public OrganizationResource(OrganizationService organizationService, UserService userService, CourseService courseService) {
        this.organizationService = organizationService;
        this.userService = userService;
        this.courseService = courseService;
    }

    @PostMapping("/organizations/course/{courseId}/organization/{organizationId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> addCourseToOrganization(@PathVariable Long courseId, @PathVariable Long organizationId) {
        Course course = courseService.findOne(courseId);
        organizationService.addCourseToOrganization(course, organizationId);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, course.getTitle())).build();
    }

    @DeleteMapping("/organizations/course/{courseId}/organization/{organizationId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> removeCourseToOrganization(@PathVariable Long courseId, @PathVariable Long organizationId) {
        Course course = courseService.findOne(courseId);
        organizationService.removeCourseFromOrganization(course, organizationId);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, course.getTitle())).build();
    }

    @PostMapping("/organizations/user/{userLogin}/organization/{organizationId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> addUserToOrganization(@PathVariable String userLogin, @PathVariable Long organizationId) {
        Optional<User> user = userService.getUserByLogin(userLogin);
        if (user.isPresent()) {
            organizationService.addUserToOrganization(user.get(), organizationId);
        }
        else {
            return ResponseEntity.notFound().headers(HeaderUtil.createAlert(applicationName, "User couldn't be found.", "userNotFound")).build();
        }

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, user.get().getLogin())).build();
    }

    @DeleteMapping("/organizations/user/{userLogin}/organization/{organizationId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> removeUserFromOrganization(@PathVariable String userLogin, @PathVariable Long organizationId) {
        Optional<User> user = userService.getUserByLogin(userLogin);
        if (user.isPresent()) {
            organizationService.removeUserFromOrganization(user.get(), organizationId);
        }

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, user.get().getLogin())).build();
    }

    @PostMapping("/organizations/add")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Organization> addOrganization(@RequestBody Organization organization) {
        Organization created = organizationService.save(organization);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, created.getName())).body(created);
    }

    @PutMapping("/organizations/update")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Organization> updateOrganization(@RequestBody Organization organization) {
        if (organization.getId() != null && organizationService.findOne(organization.getId()) != null) {
            Organization updated = organizationService.update(organization);
            return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, updated.getName())).body(updated);
        }
        else {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "The organization to update doesn't have an ID.", "NoIdProvided")).body(null);
        }
    }

    @DeleteMapping("/organizations/delete/{organizationId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Void> deleteOrganization(@PathVariable Long organizationId) {
        Organization organization = organizationService.findOne(organizationId);
        organizationService.deleteOrganization(organization);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, organization.getName())).build();
    }

    @GetMapping("/organizations/all")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<List<Organization>> getAllOrganizations() {
        List<Organization> organizations = organizationService.getAllOrganizations();
        return new ResponseEntity<>(organizations, HttpStatus.OK);
    }

    @GetMapping("/organizations/allCount")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<Long, Map<String, Long>>> getAllOrganizationsWithNumberOfUsersAndCourses() {
        Map<Long, Map<String, Long>> result = new HashMap<>();

        List<Organization> organizations = organizationService.getAllOrganizations();
        for (Organization organization : organizations) {
            Map<String, Long> numberOfUsersAndCourses = new HashMap<>();
            numberOfUsersAndCourses.put("users", organizationService.getNumberOfUsersByOrganization(organization.getId()));
            numberOfUsersAndCourses.put("courses", organizationService.getNumberOfCoursesByOrganization(organization.getId()));
            result.put(organization.getId(), numberOfUsersAndCourses);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @GetMapping("/organizations/{organizationId}")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Organization> getOrganizationById(@PathVariable long organizationId) {
        Organization organization = organizationService.findOne(organizationId);
        return new ResponseEntity<>(organization, HttpStatus.OK);
    }

    @GetMapping("/organizations/{organizationId}/full")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Organization> getOrganizationByIdWithUsersAndCourses(@PathVariable long organizationId) {
        Organization organization = organizationService.findOneWithEagerUsersAndCourses(organizationId);
        return new ResponseEntity<>(organization, HttpStatus.OK);
    }

    @GetMapping("/organizations/{organizationId}/count")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> getNumberOfUsersAndCoursesByOrganization(@PathVariable long organizationId) {
        Map<String, Long> numberOfUsersAndCourses = new HashMap<>();
        numberOfUsersAndCourses.put("users", organizationService.getNumberOfUsersByOrganization(organizationId));
        numberOfUsersAndCourses.put("courses", organizationService.getNumberOfCoursesByOrganization(organizationId));
        return new ResponseEntity<>(numberOfUsersAndCourses, HttpStatus.OK);
    }
}
