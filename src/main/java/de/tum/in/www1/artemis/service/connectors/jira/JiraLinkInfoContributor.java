package de.tum.in.www1.artemis.service.connectors.jira;

import static de.tum.in.www1.artemis.config.Constants.SPRING_PROFILE_JIRA;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.Constants;

@Component
@Profile(SPRING_PROFILE_JIRA)
public class JiraLinkInfoContributor implements InfoContributor {

    @Value("${artemis.user-management.external.url}")
    private URL JIRA_URL;

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail(Constants.EXTERNAL_USER_MANAGEMENT_URL, JIRA_URL);
        builder.withDetail(Constants.EXTERNAL_USER_MANAGEMENT_NAME, "JIRA");
    }
}
