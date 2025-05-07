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
        List<AndroidAppSiteAssociationResource.AndroidAssetLinksStatement> androidAssetLinksStatement = androidAppSiteAssociationResource.getAndroidAssetLinks().getBody();

        assertThat(androidAssetLinksStatement).hasSize(2);
        assertThat(androidAssetLinksStatement.getFirst().relation().getFirst()).isEqualTo("delegate_permission/common.handle_all_urls");
        assertThat(androidAssetLinksStatement.getFirst().target().package_name()).isEqualTo("de.tum.cit.aet.artemis");
        assertThat(androidAssetLinksStatement.get(1).relation().getFirst()).isEqualTo("delegate_permission/common.get_login_creds");
        assertThat(androidAssetLinksStatement.get(1).target().package_name()).isEqualTo("de.tum.cit.aet.artemis");
    }

}
