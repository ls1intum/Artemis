package de.tum.in.www1.artemis.service.programming;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;

@Profile("core")
@Service
public class DefaultTemplateUpgradeService implements TemplateUpgradeService {

    @Override
    public void upgradeTemplate(ProgrammingExercise exercise) {
        // Does nothing yet
    }
}
