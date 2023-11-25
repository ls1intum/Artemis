package de.tum.in.www1.artemis.organization;

import de.tum.in.www1.artemis.domain.Organization;

/**
 * Factory for creating Organizations and related objects.
 */
public class OrganizationFactory {

    /**
     * Generates an Organization.
     *
     * @param name         The name of the Organization
     * @param shortName    The short name of the Organization
     * @param url          The url of the Organization
     * @param description  The description of the Organization
     * @param logoUrl      The url to the logo of the Organization
     * @param emailPattern The email pattern of the Organization
     * @return The generated Organization
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
