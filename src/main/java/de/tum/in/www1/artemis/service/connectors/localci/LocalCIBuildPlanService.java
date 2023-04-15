package de.tum.in.www1.artemis.service.connectors.localci;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;

/**
 * This service is responsible for updating the build plan status of a participation when the local CI system is used.
 */
@Service
@Profile("localci")
public class LocalCIBuildPlanService {

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    public LocalCIBuildPlanService(TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository) {
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
    }

    /**
     * Updates the build plan status of the given participation to the given status.
     * This method attaches the new status to the build plan id and saves it in the database. This way no new database table must be added just for this purpose.
     * Inactive build plan id: "TESTCOURSE1TESTEX2-USER1"
     * Queued build plan id: "TESTCOURSE1TESTEX2-USER1_QUEUED"
     * Building build plan id: "TESTCOURSE1TESTEX2-USER1_BUILDING"
     *
     * @param participation  the participation for which the build plan status should be updated.
     * @param newBuildStatus the new build plan status.
     */
    public void updateBuildPlanStatus(ProgrammingExerciseParticipation participation, ContinuousIntegrationService.BuildStatus newBuildStatus) {
        String buildPlanId = participation.getBuildPlanId();
        if (buildPlanId == null) {
            throw new LocalCIException("Build plan id is null.");
        }
        buildPlanId = buildPlanId.replace("_" + ContinuousIntegrationService.BuildStatus.QUEUED.name(), "").replace("_" + ContinuousIntegrationService.BuildStatus.BUILDING.name(),
                "");

        if (!newBuildStatus.equals(ContinuousIntegrationService.BuildStatus.INACTIVE)) {
            buildPlanId += "_" + newBuildStatus.name();
        }

        participation.setBuildPlanId(buildPlanId);

        if (participation instanceof TemplateProgrammingExerciseParticipation templateParticipation) {
            templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        }
        else if (participation instanceof SolutionProgrammingExerciseParticipation solutionParticipation) {
            solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
        }
        else {
            programmingExerciseStudentParticipationRepository.save((ProgrammingExerciseStudentParticipation) participation);
        }
    }
}
