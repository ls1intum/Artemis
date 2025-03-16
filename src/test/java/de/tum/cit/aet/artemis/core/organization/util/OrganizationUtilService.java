package de.tum.cit.aet.artemis.core.organization.util;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.core.repository.OrganizationRepository;

/**
 * Service responsible for initializing the database with specific testdata related to organizations for use in integration tests.
 */
@Service
@Profile(SPRING_PROFILE_TEST)
public class OrganizationUtilService {

    @Autowired
    private OrganizationRepository organizationRepository;

    /**
     * Creates and saves an Organization with the given parameters.
     *
     * @param name         The name of the Organization
     * @param shortName    The short name of the Organization
     * @param url          The url of the Organization
     * @param description  The description of the Organization
     * @param logoUrl      The url to the logo of the Organization
     * @param emailPattern The email pattern of the Organization
     * @return The created Organization
     */
    public Organization createOrganization(String name, String shortName, String url, String description, String logoUrl, String emailPattern) {
        Organization organization = OrganizationFactory.generateOrganization(name, shortName, url, description, logoUrl, emailPattern);
        return organizationRepository.save(organization);
    }

    /**
     * Creates and saves an Organization with random parameters.
     *
     * @return The created Organization
     */
    public Organization createOrganization() {
        return createOrganization(UUID.randomUUID().toString().replace("-", ""), UUID.randomUUID().toString().replace("-", ""), UUID.randomUUID().toString().replace("-", ""),
                UUID.randomUUID().toString().replace("-", ""), null, "^.*@matching.*$");
    }
}
