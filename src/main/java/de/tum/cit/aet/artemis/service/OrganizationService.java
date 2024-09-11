package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.Organization;
import de.tum.cit.aet.artemis.repository.CourseRepository;
import de.tum.cit.aet.artemis.repository.OrganizationRepository;
import de.tum.cit.aet.artemis.repository.UserRepository;

/**
 * Service implementation for managing Organization entities
 */
@Profile(PROFILE_CORE)
@Service
public class OrganizationService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository organizationRepository;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    public OrganizationService(OrganizationRepository organizationRepository, UserRepository userRepository, CourseRepository courseRepository) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * Performs indexing over all users using the email pattern of the provided organization.
     * Users matching the pattern will be added to the organization.
     * Users not matching the pattern will be removed from the organization (if contained).
     *
     * @param organization the organization used to perform the indexing
     */
    public void index(final Organization organization) {
        log.debug("Start indexing for organization: {}", organization.getName());
        organization.getUsers().forEach(user -> userRepository.removeOrganizationFromUser(user.getId(), organization));
        userRepository.findAllMatchingEmailPattern(organization.getEmailPattern()).forEach(user -> {
            log.debug("User {} matches {} email pattern. Adding", user.getLogin(), organization.getName());
            userRepository.addOrganizationToUser(user.getId(), organization);
        });
    }

    /**
     * Save an organization
     *
     * @param organization the entity to save
     * @return the persisted entity
     */
    public Organization save(Organization organization) {
        log.debug("Request to save Organization : {}", organization.getName());
        return organizationRepository.save(organization);
    }

    /**
     * Add a new organization and execute indexing based on its emailPattern
     *
     * @param organization the organization to add
     * @return the persisted organization entity
     */
    public Organization add(Organization organization) {
        Organization addedOrganization = save(organization);
        addedOrganization = organizationRepository.findByIdWithEagerUsersAndCoursesElseThrow(addedOrganization.getId());
        index(addedOrganization);
        return addedOrganization;
    }

    /**
     * Update an organization
     * To avoid removing the currently mapped users and courses of the organization,
     * these are loaded eagerly and the edited values changed within the loaded entity.
     *
     * @param organization the organization to update
     * @return the updated organization
     */
    public Organization update(Organization organization) {
        log.debug("Request to update Organization : {}", organization);
        boolean indexingRequired = false;
        var oldOrganization = organizationRepository.findByIdWithEagerUsersAndCoursesElseThrow(organization.getId());
        if (!oldOrganization.getEmailPattern().equals(organization.getEmailPattern())) {
            indexingRequired = true;
        }
        oldOrganization.setName(organization.getName());
        oldOrganization.setShortName(organization.getShortName());
        oldOrganization.setUrl(organization.getUrl());
        oldOrganization.setDescription(organization.getDescription());
        oldOrganization.setLogoUrl(organization.getLogoUrl());
        oldOrganization.setEmailPattern(organization.getEmailPattern());
        if (indexingRequired) {
            index(oldOrganization);
        }
        return organizationRepository.save(oldOrganization);
    }

    /**
     * Delete an organization
     *
     * @param organizationId the id of the organization to delete
     */
    public void deleteOrganization(Long organizationId) {
        final Organization organization = organizationRepository.findByIdWithEagerUsersAndCoursesElseThrow(organizationId);

        // we make sure to delete all relations before deleting the organization
        organization.getUsers().forEach(user -> userRepository.removeOrganizationFromUser(user.getId(), organization));
        organization.getCourses().forEach(course -> courseRepository.removeOrganizationFromCourse(course.getId(), organization));

        organizationRepository.delete(organization);
    }
}
