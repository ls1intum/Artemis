package de.tum.in.www1.artemis.service.programming;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;

@Profile(PROFILE_CORE)
@Service
public class DefaultTemplateUpgradeService implements TemplateUpgradeService {

    @Override
    public void upgradeTemplate(ProgrammingExercise exercise) {
        // Does nothing yet
    }
}
