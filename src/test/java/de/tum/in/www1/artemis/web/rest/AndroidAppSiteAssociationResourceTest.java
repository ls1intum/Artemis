package de.tum.in.www1.artemis.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;

class AndroidAppSiteAssociationResourceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    AndroidAppSiteAssociationResource androidAppSiteAssociationResource;

    @Test
    void testGetAndroidAssetLinks() {
        List<AndroidAppSiteAssociationResource.AndroidAssetLinksEntry> androidAssetLinksEntry = androidAppSiteAssociationResource.getAndroidAssetLinks().getBody();

        assertThat(androidAssetLinksEntry).hasSize(2);
        assertThat(androidAssetLinksEntry.get(0).relation().get(0)).isEqualTo("delegate_permission/common.handle_all_urls");
        assertThat(androidAssetLinksEntry.get(0).target().package_name()).isEqualTo("de.tum.informatics.www1.artemis.native_app.android");
        assertThat(androidAssetLinksEntry.get(1).relation().get(0)).isEqualTo("delegate_permission/common.get_login_creds");
        assertThat(androidAssetLinksEntry.get(1).target().package_name()).isEqualTo("de.tum.informatics.www1.artemis.native_app.android");
    }

}
