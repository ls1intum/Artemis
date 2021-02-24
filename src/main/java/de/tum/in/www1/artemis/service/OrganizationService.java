package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Organization;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.OrganizationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
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
     * Called at application startup, this method tries to assign organizations to
     * users which do not belong to any. For each user without organization, the email
     * patterns of the organization are used to find any matching, resulting in the assignment
     * of the user to the matcher organization.
     */
    @PostConstruct
    public void init() {
        // to avoid 'Authentication object cannot be null'
        SecurityUtils.setAuthorizationObject();

        log.debug("Start assigning organizations to users");
        List<User> usersToAssign = userRepository.findAllWithEagerOrganizations();
        List<Organization> organizations = organizationRepository.findAll();
        usersToAssign.forEach(user -> {
            for (Organization organization : organizations) {
                /*
                 * TODO: strict re-indexing policy or additive? if (user.getOrganizations().contains(organization) && !match(user, organization)) {
                 * log.debug("User {} does not match {} email pattern anymore. Removing", user.getLogin(), organization.getName()); removeUserFromOrganization(user,
                 * organization.getId()); continue; }
                 */
                if (!user.getOrganizations().contains(organization) && match(user, organization)) {
                    log.debug("User {} matches {} email pattern. Adding", user.getLogin(), organization.getName());
                    organizationRepository.addUserToOrganization(user, organization.getId());
                }
            }
        });
        log.debug("Finished assigning organizations to users");
    }

    /**
     * Utility method to try if a user's email matches a organization's email pattern
     * @param user the user to match
     * @param organization the organization to match
     * @return true if the user matches and false if not
     */
    private boolean match(User user, Organization organization) {
        Pattern pattern = Pattern.compile(organization.getEmailPattern());
        Matcher matcher = pattern.matcher(user.getEmail());
        if (matcher.matches()) {
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Performs indexing over all users using the email pattern of the provided organization.
     * Users matching the pattern will be added to the organization.
     * Users not matching the pattern will be removed from the organization (if contained).
     * @param organization the organization used to perform the indexing
     */
    public void indexing(Organization organization) {
        log.debug("Start indexing for organization: {}", organization.getName());
        List<User> usersToAssign = userRepository.findAllWithEagerOrganizations();
        usersToAssign.forEach(user -> {
            if (user.getOrganizations().contains(organization) && !match(user, organization)) {
                log.debug("User {} does not match {} email pattern anymore. Removing", user.getLogin(), organization.getName());
                organizationRepository.removeUserFromOrganization(user, organization.getId());
            }
            else if (!user.getOrganizations().contains(organization) && match(user, organization)) {
                log.debug("User {} matches {} email pattern. Adding", user.getLogin(), organization.getName());
                organizationRepository.addUserToOrganization(user, organization.getId());
            }
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
