package de.tum.in.www1.artemis.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.dto.ConsistencyErrorDTO;

/**
 * Service Implementation for consistency checks
 * of programming exercises
 */
@Service
public class ConsistencyCheckService {

    private final Logger log = LoggerFactory.getLogger(ConsistencyCheckService.class);

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public ConsistencyCheckService(Optional<VersionControlService> versionControlService, Optional<ContinuousIntegrationService> continuousIntegrationService,
            ProgrammingExerciseRepository programmingExerciseRepository) {
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Check VCS and CI consistency of programming exercises contained in
     * a given course
     * @param courseId of the course containing the programming exercises to check
     * @return List containing the resulting errors, if any.
     */
    public List<ConsistencyErrorDTO> checkConsistencyOfCourse(long courseId) {
        log.debug("Checking consistency for course [{}]", courseId);
        List<ConsistencyErrorDTO> result = new ArrayList<>();
        List<ProgrammingExercise> exercises = programmingExerciseRepository.findAllByCourseWithTemplateAndSolutionParticipation(courseId);

        for (ProgrammingExercise programmingExercise : exercises) {
            result.addAll(checkConsistencyOfProgrammingExercise(programmingExercise));
        }

        return result;
    }

    /**
     * Overloaded method for consistency checks, which retrieves the programming exercise before calling
     * the consistency checks method
     *
     * @param exerciseId of the programming exercise to check
     * @return List containing the resulting errors, if any.
     */
    public List<ConsistencyErrorDTO> checkConsistencyOfProgrammingExercise(long exerciseId) {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);

        return checkConsistencyOfProgrammingExercise(programmingExercise);
    }

    /**
     * Performs multiple checks for a given programming exercise and returns a list
     * of the resulting errors, if any.
     *
     * Make sure to load Template and Solution participations along with the programming exercise.
     *
     * Checks:
     * - Existence of VCS repositories and project
     * - Existence of CI build plans
     *
     * @param programmingExercise of the programming exercise to check
     * @return List containing the resulting errors, if any.
     */
    public List<ConsistencyErrorDTO> checkConsistencyOfProgrammingExercise(ProgrammingExercise programmingExercise) {
        List<ConsistencyErrorDTO> result = new ArrayList<>();

        programmingExercise.checksAndSetsIfProgrammingExerciseIsLocalSimulation();
        if (programmingExercise.getIsLocalSimulation()) {
            result.add(new ConsistencyErrorDTO(programmingExercise, ConsistencyErrorDTO.ErrorType.IS_LOCAL_SIMULATION));
            return result;
        }

        result.addAll(checkVCSConsistency(programmingExercise));
        result.addAll(checkCIConsistency(programmingExercise));

        return result;
    }

    /**
     * Checks if a project and its repositories (TEMPLATE, TEST, SOLUTION) exists in the VCS
     * for a given programming exercise.
     * @param programmingExercise to check
     * @return List containing the resulting errors, if any.
     */
    private List<ConsistencyErrorDTO> checkVCSConsistency(ProgrammingExercise programmingExercise) {
        List<ConsistencyErrorDTO> result = new ArrayList<>();

        if (!versionControlService.get().checkIfProjectExists(programmingExercise.getProjectKey(), programmingExercise.getProjectName())) {
            result.add(new ConsistencyErrorDTO(programmingExercise, ConsistencyErrorDTO.ErrorType.VCS_PROJECT_MISSING));
        }
        else {
            if (!versionControlService.get().repositoryUrlIsValid(programmingExercise.getVcsTemplateRepositoryUrl())) {
                result.add(new ConsistencyErrorDTO(programmingExercise, ConsistencyErrorDTO.ErrorType.TEMPLATE_REPO_MISSING));
            }
            if (!versionControlService.get().repositoryUrlIsValid(programmingExercise.getVcsTestRepositoryUrl())) {
                result.add(new ConsistencyErrorDTO(programmingExercise, ConsistencyErrorDTO.ErrorType.TEST_REPO_MISSING));
            }
            if (!versionControlService.get().repositoryUrlIsValid(programmingExercise.getVcsSolutionRepositoryUrl())) {
                result.add(new ConsistencyErrorDTO(programmingExercise, ConsistencyErrorDTO.ErrorType.SOLUTION_REPO_MISSING));
            }
        }
        return result;
    }

    /**
     * Checks if build plans (TEMPLATE, SOLUTION) exist in the CI for a given
     * programming exercise.
     * @param programmingExercise to check
     * @return List containing the resulting errors, if any.
     */
    private List<ConsistencyErrorDTO> checkCIConsistency(ProgrammingExercise programmingExercise) {
        List<ConsistencyErrorDTO> result = new ArrayList<>();

        if (!continuousIntegrationService.get().checkIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId())) {
            result.add(new ConsistencyErrorDTO(programmingExercise, ConsistencyErrorDTO.ErrorType.TEMPLATE_BUILD_PLAN_MISSING));
        }
        if (!continuousIntegrationService.get().checkIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getSolutionBuildPlanId())) {
            result.add(new ConsistencyErrorDTO(programmingExercise, ConsistencyErrorDTO.ErrorType.SOLUTION_BUILD_PLAN_MISSING));
        }

        return result;
    }

}
