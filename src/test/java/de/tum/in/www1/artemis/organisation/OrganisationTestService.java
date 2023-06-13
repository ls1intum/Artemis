package de.tum.in.www1.artemis.organisation;

import java.util.UUID;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Organization;
import de.tum.in.www1.artemis.util.ModelFactory;

@Service
public class OrganisationTestService {

    public Organization createOrganization(String name, String shortName, String url, String description, String logoUrl, String emailPattern) {
        Organization organization = ModelFactory.generateOrganization(name, shortName, url, description, logoUrl, emailPattern);
        return organizationRepository.save(organization);
    }

    public Organization createOrganization() {
        return createOrganization(UUID.randomUUID().toString().replace("-", ""), UUID.randomUUID().toString().replace("-", ""), UUID.randomUUID().toString().replace("-", ""),
                UUID.randomUUID().toString().replace("-", ""), null, "^.*@matching.*$");
    }
}
