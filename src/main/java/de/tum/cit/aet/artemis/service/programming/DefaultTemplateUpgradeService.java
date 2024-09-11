package de.tum.cit.aet.artemis.service.programming;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@Profile(PROFILE_CORE)
@Service
public class DefaultTemplateUpgradeService implements TemplateUpgradeService {

    @Override
    public void upgradeTemplate(ProgrammingExercise exercise) {
        // Does nothing yet
    }
}
