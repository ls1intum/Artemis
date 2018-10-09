package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Service
@Transactional
public class ProgrammingExerciseService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseService.class);

    private final VersionControlService versionControlService;
    private final ContinuousIntegrationService continuousIntegrationService;
    private final ContinuousIntegrationUpdateService continuousIntegrationUpdateService;

    public ProgrammingExerciseService(VersionControlService versionControlService, ContinuousIntegrationService continuousIntegrationService,
                                      ContinuousIntegrationUpdateService continuousIntegrationUpdateService) {
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.continuousIntegrationUpdateService = continuousIntegrationUpdateService;
    }

    /**
     * Notifies all particpation of the given programmingExercise about changes of the test cases.
     *
     * @param programmingExercise The programmingExercise where the test cases got changed
     */
    public void notifyChangedTestCases(ProgrammingExercise programmingExercise) {
        for (Participation participation : programmingExercise.getParticipations()) {
            continuousIntegrationUpdateService.triggerUpdate(participation.getBuildPlanId(), false);
        }
    }

    /**
     * Setups all needed repositories etc. for the given programmingExercise.
     *
     * @param programmingExercise The programmingExercise that should be setup
     */
    public void setupProgrammingExercise(ProgrammingExercise programmingExercise, String exerciseShortForm) throws Exception {
        versionControlService.createTopLevelEntity(exerciseShortForm, null); // Create project

        versionControlService.createLowerLevelEntity(exerciseShortForm, exerciseShortForm, null); // Create exercise repository
        versionControlService.createLowerLevelEntity("tests", exerciseShortForm, null); // Create tests repository
        versionControlService.createLowerLevelEntity("solution", exerciseShortForm, null); // Create solution repository

        continuousIntegrationService.createProject(exerciseShortForm);
        continuousIntegrationService.copyBuildPlanFromTemplate(exerciseShortForm, exerciseShortForm, "JAVA", "TEMPLATE");
    }

}
