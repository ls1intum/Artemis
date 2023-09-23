package de.tum.in.www1.artemis.organization;

import de.tum.in.www1.artemis.domain.Organization;

/**
 * Factory for creating Organizations and related objects.
 */
public class OrganizationFactory {

    /**
     * Generate an example organization entity
     *
     * @param name         of organization
     * @param shortName    of organization
     * @param url          of organization
     * @param description  of organization
     * @param logoUrl      of organization
     * @param emailPattern of organization
     * @return An organization entity
     */
    public static Organization generateOrganization(String name, String shortName, String url, String description, String logoUrl, String emailPattern) {
        Organization organization = new Organization();
        organization.setName(name);
        organization.setShortName(shortName);
        organization.setUrl(url);
        organization.setDescription(description);
        organization.setLogoUrl(logoUrl);
        organization.setEmailPattern(emailPattern);
        return organization;
    }
}
