package de.tum.cit.aet.artemis.service.programming;

import de.tum.cit.aet.artemis.domain.ProgrammingExercise;

public interface TemplateUpgradeService {

    /**
     * Upgrades the template files provided by Artemis for the given exercise.
     * Usually this is done by checking out the repositories associated with the template/solution
     * participation and the test repository. The latest Artemis templates serve as a reference.
     * The specific strategy for the manipulation of files is defined in programming language specific services.
     *
     * @param exercise Exercise for which the template files should be upgraded
     */
    void upgradeTemplate(ProgrammingExercise exercise);
}
