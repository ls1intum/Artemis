package de.tum.in.www1.artemis.service.programming;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import static de.tum.in.www1.artemis.config.Constants.SPRING_PROFILE_BAMBOO;
import static de.tum.in.www1.artemis.config.Constants.SPRING_PROFILE_JENKINS;

@Component
@Profile({ SPRING_PROFILE_BAMBOO, SPRING_PROFILE_JENKINS })
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
