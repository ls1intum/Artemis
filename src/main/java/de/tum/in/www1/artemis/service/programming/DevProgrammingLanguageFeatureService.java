package de.tum.in.www1.artemis.service.programming;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

/**
 * This service is only needed for TESTS that neither provide the bamboo nor jenkins nor gitlabci profile (but the dev profile).
 * It will NEVER be available in prod OR when bamboo OR jenkins OR gitlabci is used.
 */
@Service
@Profile("dev & !bamboo & !jenkins & !gitlabci")
public class DevProgrammingLanguageFeatureService extends ProgrammingLanguageFeatureService {

    public DevProgrammingLanguageFeatureService() {
        programmingLanguageFeatures.put(ProgrammingLanguage.JAVA, new ProgrammingLanguageFeature(ProgrammingLanguage.JAVA, true, true, true, true, false, List.of()));
    }
}
