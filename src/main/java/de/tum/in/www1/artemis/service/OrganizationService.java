package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Organization;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.OrganizationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Service implementation for managing Organization entities
 */
@Service
public class OrganizationService {

    private final Logger log = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository organizationRepository;

    private final UserRepository userRepository;

    public OrganizationService(OrganizationRepository organizationRepository, UserRepository userRepository) {
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
    }

    /**
     * Performs indexing over all users using the email pattern of the provided organization.
     * Users matching the pattern will be added to the organization.
     * Users not matching the pattern will be removed from the organization (if contained).
     * @param organization the organization used to perform the indexing
     */
    public void indexing(Organization organization) {
        log.debug("Start indexing for organization: {}", organization.getName());
        List<User> usersToAssign = userRepository.findAllMatchingEmailPattern(organization.getEmailPattern());
        organizationRepository.removeAllUsersFromOrganization(organization.getId());
        usersToAssign.forEach(user -> {
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
     * @param organization the organization zo add
     * @return the persisted organization entity
     */
    public Organization add(Organization organization) {
        Organization addedOrganization = save(organization);
        indexing(addedOrganization);
        return addedOrganization;
    }

    /**
     * Update an organization
     * To avoid removing the currently mapped users and courses of the organization,
     * these are loaded eagerly and the edited values changed within the loaded entity.
     * @param organization the organization to update
     * @return the updated organization
     */
    public Organization update(Organization organization) {
        log.debug("Request to update Organization : {}", organization);
        boolean indexingRequired = false;
        Optional<Organization> optionalOldOrganization = organizationRepository.findByIdWithEagerUsersAndCourses(organization.getId());
        if (optionalOldOrganization.isPresent()) {
            Organization oldOrganization = optionalOldOrganization.get();
            if (!oldOrganization.getEmailPattern().equals(organization.getEmailPattern())) {
                indexingRequired = true;
            }
            oldOrganization.setName(organization.getName());
            oldOrganization.setShortName(organization.getShortName());
            oldOrganization.setUrl(organization.getUrl());
            oldOrganization.setDescription(organization.getDescription());
            oldOrganization.setLogoUrl(organization.getLogoUrl());
            oldOrganization.setEmailPattern(organization.getEmailPattern());
            Organization savedOrganization = organizationRepository.save(oldOrganization);
            if (indexingRequired) {
                indexing(savedOrganization);
            }
            return savedOrganization;
        }
        else {
            throw new EntityNotFoundException("Organization with id: \"" + organization.getId() + "\" does not exist");
        }
    }
}
