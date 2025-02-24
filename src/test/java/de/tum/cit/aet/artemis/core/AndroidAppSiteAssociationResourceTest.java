package de.tum.cit.aet.artemis.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.communication.web.AndroidAppSiteAssociationResource;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class AndroidAppSiteAssociationResourceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private AndroidAppSiteAssociationResource androidAppSiteAssociationResource;

    @Test
    void testGetAndroidAssetLinks() {
        List<AndroidAppSiteAssociationResource.AndroidAssetLinksEntry> androidAssetLinksEntry = androidAppSiteAssociationResource.getAndroidAssetLinks().getBody();

        assertThat(androidAssetLinksEntry).hasSize(2);
        assertThat(androidAssetLinksEntry.getFirst().relation().getFirst()).isEqualTo("delegate_permission/common.handle_all_urls");
        assertThat(androidAssetLinksEntry.getFirst().target().package_name()).isEqualTo("de.tum.cit.aet.artemis");
        assertThat(androidAssetLinksEntry.get(1).relation().getFirst()).isEqualTo("delegate_permission/common.get_login_creds");
        assertThat(androidAssetLinksEntry.get(1).target().package_name()).isEqualTo("de.tum.cit.aet.artemis");
    }

}
