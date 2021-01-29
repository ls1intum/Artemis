package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Organization;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.OrganizationRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service implementation for managing Organization entities
 */
@Service
public class OrganizationService {

    private final Logger log = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    /**
     * Save an organization
     *
     * @param organization the entity to save
     * @return the persisted entity
     */
    public Organization save(Organization organization) {
        log.debug("Request to save Organization : {}", organization);
        return organizationRepository.save(organization);
    }

    /**
     *  Alias for saving an organization
     *
     * @param organization the entity to save
     * @return the persisted entity
     */
    public Organization addOrganization(Organization organization) {
        return save(organization);
    }

    /**
     * Delete an organization entity by providing its id
     * @param organizationId the id of the entity to delete
     */
    public void deleteOrganization(long organizationId) {
        organizationRepository.deleteById(organizationId);
    }

    /**
     * Delete an organization entity
     * @param organization the entity to delete
     */
    public void deleteOrganization(Organization organization) {
        organizationRepository.delete(organization);
    }

    public Organization findOne(long organizationId) {
        log.debug("Request to get Organization : {}", organizationId);
        return organizationRepository.findById(organizationId).orElseThrow(() -> new EntityNotFoundException("Organization with id: \"" + organizationId + "\" does not exist"));
    }

    /**
     * Get an organization containing the eagerly loaded list of users and courses
     * @param organizationId the id of the organization to retrieve
     * @return the organization with the given Id containing eagerly loaded list of users and courses
     */
    public Organization findOneWithEagerUsersAndCourses(long organizationId) {
        log.debug("Request to get Organization : {}", organizationId);
        return organizationRepository.findByIdWithEagerUsersAndCourses(organizationId).orElseThrow(() -> new EntityNotFoundException("Organization with id: \"" + organizationId + "\" does not exist"));
    }

    /**
     * Get all organizations
     * @return all organizations
     */
    public List<Organization> getAllOrganizations() {
        return organizationRepository.findAll();
    }

    /**
     * Get all organizations where the given user is currently in
     * @param userId the id of the user used to retrieve the organizations
     * @return a Set of all organizations the given user is currently in
     */
    public Set<Organization> getAllOrganizationsByUser(long userId) {
        return organizationRepository.findAllOrganizationsByUserId(userId);
    }

    /**
     * Get all organizations where the given course is currently in
     * @param courseId the id of the course used to retrieve the organizations
     * @return a Set of all organizations the given course is currently in
     */
    public Set<Organization> getAllOrganizationsByCourse(long courseId) {
        return organizationRepository.findAllOrganizationsByCourseId(courseId);
    }

    /**
     * Get the number of users currently mapped to the given organization
     * @param organizationId the id of the organization where the users are in
     * @return the number of users contained in the organization
     */
    public long getNumberOfUsersByOrganization(long organizationId) {
        return organizationRepository.getNumberOfUsersByOrganizationId(organizationId);
    }

    /**
     * Get the number of courses currently mapped to the given organization
     * @param organizationId the id of the organization where the courses are in
     * @return the number of courses contained in the organization
     */
    public long getNumberOfCoursesByOrganization(long organizationId) {
        return organizationRepository.getNumberOfCoursesByOrganizationId(organizationId);
    }

    /**
     * Add a user to an existing organization
     * @param user the user to add
     * @param organizationId the id of the organization where the user should be added
     */
    public void addUserToOrganization(User user, long organizationId) {
        Optional<Organization> organization = organizationRepository.findByIdWithEagerUsers(organizationId);
        if (organization.isPresent() && !(organization.get().getUsers().contains(user))) {
            organization.get().getUsers().add(user);
            save(organization.get());
        }
    }

    /**
     * Add a user to an existing organization
     * @param user the user to add
     * @param organization the organization where the user should be added
     */
    public void addUserToOrganization(User user, Organization organization) {
        if (!organization.getUsers().contains(user)) {
            organization.getUsers().add(user);
            save(organization);
        }
    }

    /**
     * Removes a user from an existing organization
     * @param user the user to remove
     * @param organizationId the id of the organization where the user should be removed from
     */
    public void removeUserFromOrganization(User user, long organizationId) {
        Optional<Organization> organization = organizationRepository.findByIdWithEagerUsers(organizationId);
        if (organization.isPresent() && organization.get().getUsers().contains(user)) {
            organization.get().getUsers().remove(user);
            save(organization.get());
        }
    }

    /**
     * Add a course to an existing organization
     * @param course the course to add
     * @param organizationId the id of the organization where the course should be added
     */
    public void addCourseToOrganization(Course course, long organizationId) {
        Optional<Organization> organization = organizationRepository.findByIdWithEagerCourses(organizationId);
        if (organization.isPresent() && !(organization.get().getCourses().contains(course))) {
            organization.get().getCourses().add(course);
            save(organization.get());
        }
    }

    /**
     * Add a course to an existing organization
     * @param course the course to add
     * @param organization the organization where the course should be added
     */
    public void addCourseToOrganization(Course course, Organization organization) {
        if (!organization.getCourses().contains(course)) {
            organization.getCourses().add(course);
            save(organization);
        }
    }

    /**
     * Removes a course from an existing organization
     * @param course the course to remove
     * @param organization the organization where the course should be removed from
     */
    public void removeCourseFromOrganization(Course course, Organization organization) {
        if (organization.getCourses().contains(course)) {
            organization.getCourses().remove(course);
            save(organization);
        }
    }

    /**
     * Removes a course from an existing organization
     * @param course the course to remove
     * @param organizationId the id of organization where the course should be removed from
     */
    public void removeCourseFromOrganization(Course course, long organizationId) {
        Optional<Organization> organization = organizationRepository.findByIdWithEagerCourses(organizationId);
        if (organization.isPresent() && organization.get().getCourses().contains(course)) {
            organization.get().getCourses().remove(course);
            save(organization.get());
        }
    }
}
