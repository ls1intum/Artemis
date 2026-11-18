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
        var androidStatement = androidAssetLinksStatement.getFirst();
        assertThat(androidStatement.relation().getFirst()).isEqualTo("delegate_permission/common.handle_all_urls");
        assertThat(androidStatement.relation().get(1)).isEqualTo("delegate_permission/common.get_login_creds");
        var androidTarget = androidStatement.target();
        assertThat(androidTarget).isInstanceOf(AndroidAppSiteAssociationResource.AndroidAssetLinksStatement.AndroidTarget.class);
        assertThat(((AndroidAppSiteAssociationResource.AndroidAssetLinksStatement.AndroidTarget) androidTarget).package_name()).isEqualTo("de.tum.cit.aet.artemis");

        var webStatement = androidAssetLinksStatement.get(1);
        assertThat(webStatement.relation().getFirst()).isEqualTo("delegate_permission/common.handle_all_urls");
        assertThat(webStatement.relation().get(1)).isEqualTo("delegate_permission/common.get_login_creds");
        var webTarget = webStatement.target();
        assertThat(webTarget).isInstanceOf(AndroidAppSiteAssociationResource.AndroidAssetLinksStatement.WebTarget.class);
    }

}
