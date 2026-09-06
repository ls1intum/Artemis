package de.tum.cit.aet.artemis.hyperion.service.variants;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.service.variants.ProgrammingVariantTools.SolutionBuildForTestDiscovery;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.service.RepositoryService;

/**
 * Builds the per-round {@link ProgrammingVariantTools} toolset. The toolset is not a Spring bean (it carries
 * per-round state), so this service holds the collaborators it needs instead of the adapter service.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class ProgrammingVariantToolsetService {

    private final ExerciseVariantJobService jobService;

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final String defaultBranch;

    public ProgrammingVariantToolsetService(ExerciseVariantJobService jobService, GitService gitService, RepositoryService repositoryService,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseTaskService programmingExerciseTaskService,
            ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository, @Value("${artemis.version-control.default-branch:main}") String defaultBranch) {
        this.jobService = jobService;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.defaultBranch = defaultBranch;
    }

    /**
     * Creates the toolset for one agent round.
     *
     * @param exercise                      the variant exercise the tools operate on
     * @param sourceExercise                the exercise the variant was generated from, read only for diffs
     * @param user                          the job initiator, used for repository access
     * @param jobId                         the job id, for tool-call telemetry
     * @param solutionBuildForTestDiscovery runs a solution build so newly written tests become test cases
     * @return the toolset for this round
     */
    public VariantToolset create(ProgrammingExercise exercise, ProgrammingExercise sourceExercise, User user, String jobId,
            SolutionBuildForTestDiscovery solutionBuildForTestDiscovery) {
        return new ProgrammingVariantTools(exercise, user, jobId, jobService, gitService, repositoryService, programmingExerciseRepository, programmingExerciseTaskService,
                defaultBranch, sourceExercise, programmingExerciseTestCaseRepository, solutionBuildForTestDiscovery);
    }
}
