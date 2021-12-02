package de.tum.in.www1.artemis.service.programming;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

import static de.tum.in.www1.artemis.config.Constants.SPRING_PROFILE_BAMBOO;
import static de.tum.in.www1.artemis.config.Constants.SPRING_PROFILE_JENKINS;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_DEVELOPMENT;

/**
 * This service is only needed for TESTS that neither provide the bamboo nor jenkins profile (but the dev profile).
 * It will NEVER be available in prod OR when bamboo OR jenkins is used.
 */
@Service
@Profile(SPRING_PROFILE_DEVELOPMENT + " & !" + SPRING_PROFILE_BAMBOO + " & !" + SPRING_PROFILE_JENKINS)
public class DevProgrammingLanguageFeatureService extends ProgrammingLanguageFeatureService {

    public DevProgrammingLanguageFeatureService() {
        programmingLanguageFeatures.put(ProgrammingLanguage.JAVA, new ProgrammingLanguageFeature(ProgrammingLanguage.JAVA, true, true, true, true, false, List.of()));
    }
}
