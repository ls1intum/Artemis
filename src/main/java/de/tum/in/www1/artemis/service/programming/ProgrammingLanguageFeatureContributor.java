package de.tum.in.www1.artemis.service.programming;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({ "bamboo", "jenkins", "gitlabci" })
public class ProgrammingLanguageFeatureContributor implements InfoContributor {

    private final ProgrammingLanguageFeatureService programmingLanguageFeatureService;

    public ProgrammingLanguageFeatureContributor(ProgrammingLanguageFeatureService programmingLanguageFeatureService) {
        this.programmingLanguageFeatureService = programmingLanguageFeatureService;
    }

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("programmingLanguageFeatures", programmingLanguageFeatureService.getProgrammingLanguageFeatures().values());
    }
}
