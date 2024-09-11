package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.service.connectors.vcs.VersionControlService;
import de.tum.cit.aet.artemis.service.dto.ConsistencyErrorDTO;

/**
 * Service Implementation for consistency checks
 * of programming exercises
 */
@Profile(PROFILE_CORE)
@Service
public class ConsistencyCheckService {

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
     * Overloaded method for consistency checks, which retrieves the programming exercise before calling
     * the consistency checks method
     *
     * @param exerciseId of the programming exercise to check
     * @return List containing the resulting errors, if any.
     */
    public List<ConsistencyErrorDTO> checkConsistencyOfProgrammingExercise(long exerciseId) {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesElseThrow(exerciseId);
        return checkConsistencyOfProgrammingExercise(programmingExercise);
    }

    /**
     * Performs multiple checks for a given programming exercise and returns a list
     * of the resulting errors, if any.
     * <p>
     * Make sure to load Template and Solution participations along with the programming exercise.
     * <p>
     * Checks:
     * - Existence of VCS repositories and project
     * - Existence of CI build plans
     *
     * @param programmingExercise of the programming exercise to check
     * @return List containing the resulting errors, if any.
     */
    public List<ConsistencyErrorDTO> checkConsistencyOfProgrammingExercise(ProgrammingExercise programmingExercise) {
        List<ConsistencyErrorDTO> result = new ArrayList<>();
        result.addAll(checkVCSConsistency(programmingExercise));
        result.addAll(checkCIConsistency(programmingExercise));
        return result;
    }

    /**
     * Checks if a project and its repositories (TEMPLATE, TEST, SOLUTION) exists in the VCS
     * for a given programming exercise.
     *
     * @param programmingExercise to check
     * @return List containing the resulting errors, if any.
     */
    private List<ConsistencyErrorDTO> checkVCSConsistency(ProgrammingExercise programmingExercise) {
        List<ConsistencyErrorDTO> result = new ArrayList<>();

        VersionControlService versionControl = versionControlService.orElseThrow();
        if (!versionControl.checkIfProjectExists(programmingExercise.getProjectKey(), programmingExercise.getProjectName())) {
            result.add(new ConsistencyErrorDTO(programmingExercise, ConsistencyErrorDTO.ErrorType.VCS_PROJECT_MISSING));
        }
        else {
            if (!versionControl.repositoryUriIsValid(programmingExercise.getVcsTemplateRepositoryUri())) {
                result.add(new ConsistencyErrorDTO(programmingExercise, ConsistencyErrorDTO.ErrorType.TEMPLATE_REPO_MISSING));
            }
            if (!versionControl.repositoryUriIsValid(programmingExercise.getVcsTestRepositoryUri())) {
                result.add(new ConsistencyErrorDTO(programmingExercise, ConsistencyErrorDTO.ErrorType.TEST_REPO_MISSING));
            }
            if (!versionControl.repositoryUriIsValid(programmingExercise.getVcsSolutionRepositoryUri())) {
                result.add(new ConsistencyErrorDTO(programmingExercise, ConsistencyErrorDTO.ErrorType.SOLUTION_REPO_MISSING));
            }
            for (var auxiliaryRepository : programmingExercise.getAuxiliaryRepositories()) {
                if (!versionControl.repositoryUriIsValid(auxiliaryRepository.getVcsRepositoryUri())) {
                    result.add(new ConsistencyErrorDTO(programmingExercise, ConsistencyErrorDTO.ErrorType.AUXILIARY_REPO_MISSING));
                }
            }
        }
        return result;
    }

    /**
     * Checks if build plans (TEMPLATE, SOLUTION) exist in the CI for a given
     * programming exercise.
     *
     * @param programmingExercise to check
     * @return List containing the resulting errors, if any.
     */
    private List<ConsistencyErrorDTO> checkCIConsistency(ProgrammingExercise programmingExercise) {
        List<ConsistencyErrorDTO> result = new ArrayList<>();

        ContinuousIntegrationService continuousIntegration = continuousIntegrationService.orElseThrow();
        if (!continuousIntegration.checkIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId())) {
            result.add(new ConsistencyErrorDTO(programmingExercise, ConsistencyErrorDTO.ErrorType.TEMPLATE_BUILD_PLAN_MISSING));
        }
        if (!continuousIntegration.checkIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getSolutionBuildPlanId())) {
            result.add(new ConsistencyErrorDTO(programmingExercise, ConsistencyErrorDTO.ErrorType.SOLUTION_BUILD_PLAN_MISSING));
        }

        return result;
    }

}
