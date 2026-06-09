package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.localvc.service.vcs.VersionControlService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ConsistencyErrorDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Service Implementation for consistency checks
 * of programming exercises
 */
@Profile(PROFILE_CORE)
@Lazy
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
            if (!hasValidRepository(programmingExercise.getVcsTemplateRepositoryUri())) {
                result.add(new ConsistencyErrorDTO(programmingExercise, ConsistencyErrorDTO.ErrorType.TEMPLATE_REPO_MISSING));
            }
            if (!hasValidRepository(programmingExercise.getVcsTestRepositoryUri())) {
                result.add(new ConsistencyErrorDTO(programmingExercise, ConsistencyErrorDTO.ErrorType.TEST_REPO_MISSING));
            }
            if (!hasValidRepository(programmingExercise.getVcsSolutionRepositoryUri())) {
                result.add(new ConsistencyErrorDTO(programmingExercise, ConsistencyErrorDTO.ErrorType.SOLUTION_REPO_MISSING));
            }
            for (var auxiliaryRepository : programmingExercise.getAuxiliaryRepositories()) {
                if (!hasValidRepository(auxiliaryRepository.getVcsRepositoryUri())) {
                    result.add(new ConsistencyErrorDTO(programmingExercise, ConsistencyErrorDTO.ErrorType.AUXILIARY_REPO_MISSING));
                }
            }
        }
        return result;
    }

    /**
     * Checks if a valid repository exists behind the given URI. This requires that the URI is not null (a null URI typically means the stored URI could not be parsed),
     * that it is syntactically valid, and that a valid git repository exists for it in the VCS. The repository check is required because a syntactically valid URI can
     * still point to a repository that is missing or corrupt, which breaks operations such as starting the exercise (see issue #12840).
     *
     * @param repositoryUri the repository URI to check, may be null
     * @return true if a valid repository exists, false otherwise
     */
    private boolean hasValidRepository(@Nullable LocalVCRepositoryUri repositoryUri) {
        VersionControlService versionControl = versionControlService.orElseThrow();
        return versionControl.repositoryUriIsValid(repositoryUri) && versionControl.isValidGitRepository(repositoryUri);
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
