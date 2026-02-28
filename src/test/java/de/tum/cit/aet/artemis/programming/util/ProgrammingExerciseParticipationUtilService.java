package de.tum.cit.aet.artemis.programming.util;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;

@Service
@Profile(SPRING_PROFILE_TEST)
@Lazy
public class ProgrammingExerciseParticipationUtilService {

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private TemplateProgrammingExerciseParticipationTestRepository templateProgrammingExerciseParticipationTestRepo;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepo;

    @Value("${artemis.version-control.url}")
    protected URI localVCBaseUri;

    /**
     * Adds template participation to the provided programming exercise.
     *
     * @param exercise The exercise to which the template participation should be added.
     * @return The programming exercise to which a participation was added.
     */
    public ProgrammingExercise addTemplateParticipationForProgrammingExercise(ProgrammingExercise exercise) {
        final var repoName = exercise.generateRepositoryName(RepositoryType.TEMPLATE);
        TemplateProgrammingExerciseParticipation participation = new TemplateProgrammingExerciseParticipation();
        participation.setProgrammingExercise(exercise);
        participation.setBuildPlanId(exercise.generateBuildPlanId(BuildPlanType.TEMPLATE));
        var localVcRepoUri = new LocalVCRepositoryUri(localVCBaseUri, exercise.getProjectKey(), repoName);
        participation.setRepositoryUri(localVcRepoUri.toString());
        participation.setInitializationState(InitializationState.INITIALIZED);
        templateProgrammingExerciseParticipationTestRepo.saveAndFlush(participation);
        exercise.setTemplateParticipation(participation);
        return programmingExerciseRepository.saveAndFlush(exercise);
    }

    /**
     * Adds a solution participation to the provided programming exercise.
     *
     * @param exercise The exercise to which the solution participation should be added.
     * @return The programming exercise to which a participation was added.
     */
    public ProgrammingExercise addSolutionParticipationForProgrammingExercise(ProgrammingExercise exercise) {
        final var repoName = exercise.generateRepositoryName(RepositoryType.SOLUTION);
        SolutionProgrammingExerciseParticipation participation = new SolutionProgrammingExerciseParticipation();
        participation.setProgrammingExercise(exercise);
        participation.setBuildPlanId(exercise.generateBuildPlanId(BuildPlanType.SOLUTION));
        var localVcRepoUri = new LocalVCRepositoryUri(localVCBaseUri, exercise.getProjectKey(), repoName);
        participation.setRepositoryUri(localVcRepoUri.toString());
        participation.setInitializationState(InitializationState.INITIALIZED);
        solutionProgrammingExerciseParticipationRepo.saveAndFlush(participation);
        exercise.setSolutionParticipation(participation);
        return programmingExerciseRepository.saveAndFlush(exercise);
    }
}
