package de.tum.cit.aet.artemis.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.core.web.AndroidAppSiteAssociationResource;

class AndroidAppSiteAssociationResourceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    AndroidAppSiteAssociationResource androidAppSiteAssociationResource;

    @Test
    void testGetAndroidAssetLinks() {
        List<AndroidAppSiteAssociationResource.AndroidAssetLinksEntry> androidAssetLinksEntry = androidAppSiteAssociationResource.getAndroidAssetLinks().getBody();

        assertThat(androidAssetLinksEntry).hasSize(2);
        assertThat(androidAssetLinksEntry.getFirst().relation().getFirst()).isEqualTo("delegate_permission/common.handle_all_urls");
        assertThat(androidAssetLinksEntry.getFirst().target().package_name()).isEqualTo("de.tum.informatics.www1.artemis.native_app.android");
        assertThat(androidAssetLinksEntry.get(1).relation().getFirst()).isEqualTo("delegate_permission/common.get_login_creds");
        assertThat(androidAssetLinksEntry.get(1).target().package_name()).isEqualTo("de.tum.informatics.www1.artemis.native_app.android");
    }

}
