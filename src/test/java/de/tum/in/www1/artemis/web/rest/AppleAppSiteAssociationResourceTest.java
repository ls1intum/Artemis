package de.tum.in.www1.artemis.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;

class AppleAppSiteAssociationResourceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    AppleAppSiteAssociationResource appleAppSiteAssociationResource;

    @Test
    void testGetAppleAppSiteAssociation() {
        AppleAppSiteAssociationResource.AppleAppSiteAssociation appleAppSiteAssociation = appleAppSiteAssociationResource.getAppleAppSiteAssociation().getBody();

        assertThat(appleAppSiteAssociation.applinks().details()).hasSize(1);
        assertThat(appleAppSiteAssociation.applinks().details()[0].appID()).isEqualTo("2J3C6P6X3N.de.tum.cit.artemis");
        assertThat(appleAppSiteAssociation.applinks().details()[0].paths()).hasSize(1);
        assertThat(appleAppSiteAssociation.webcredentials().apps()[0]).isEqualTo("2J3C6P6X3N.de.tum.cit.artemis");
    }
}
