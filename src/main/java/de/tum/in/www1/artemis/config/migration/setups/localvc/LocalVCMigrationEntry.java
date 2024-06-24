package de.tum.in.www1.artemis.config.migration.setups.localvc;

import java.net.URL;

import org.springframework.beans.factory.annotation.Value;

import de.tum.in.www1.artemis.config.migration.setups.ProgrammingExerciseMigrationEntry;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;

public abstract class LocalVCMigrationEntry extends ProgrammingExerciseMigrationEntry {

    @Value("${server.url}")
    protected URL localVCBaseUrl;

    protected LocalVCMigrationEntry(ProgrammingExerciseRepository programmingExerciseRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository) {
        super(programmingExerciseRepository, solutionProgrammingExerciseParticipationRepository, templateProgrammingExerciseParticipationRepository,
                programmingExerciseStudentParticipationRepository);
    }

    @Override
    protected boolean areValuesIncomplete() {
        if (localVCBaseUrl == null) {
            log.error("Migration failed because the local VC base URL is not configured.");
            return true;
        }
        return false;
    }
}
