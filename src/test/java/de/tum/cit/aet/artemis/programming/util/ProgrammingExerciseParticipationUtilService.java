package de.tum.cit.aet.artemis.programming.util;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.localvc.util.LocalVCRepositoryTestService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;

@Service
@Profile(SPRING_PROFILE_TEST)
@Lazy
public class ProgrammingExerciseParticipationUtilService {

    @Autowired
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationTestRepository;

    @Autowired
    private TeamRepository teamRepositoryForParticipationLookup;

    @Autowired
    private UserTestRepository userRepositoryForParticipationLookup;

    /**
     * Finds the participation a student has in a programming exercise, by their login.
     * <p>
     * Lives here rather than in {@code ProgrammingExerciseParticipationService} because only tests look a
     * participation up by login: the server always has the student's id by the time it needs one, and selecting on the
     * joined user row makes the database read every participation of the exercise.
     *
     * @param exercise the exercise the participation belongs to
     * @param username the student's login
     * @return the participation
     * @throws EntityNotFoundException if the student has no participation in that exercise
     */
    @NonNull
    public ProgrammingExerciseStudentParticipation findStudentParticipationByExerciseAndStudentLogin(Exercise exercise, String username) {
        Optional<ProgrammingExerciseStudentParticipation> participation;
        if (exercise.isTeamMode()) {
            // The team lookup is by id, like everywhere else; only this helper starts from a login, so it resolves one.
            Optional<Team> optionalTeam = userRepositoryForParticipationLookup.findOneByLogin(username)
                    .flatMap(student -> teamRepositoryForParticipationLookup.findOneByExerciseIdAndUserId(exercise.getId(), student.getId()));
            participation = optionalTeam.flatMap(team -> programmingExerciseStudentParticipationTestRepository.findByExerciseIdAndTeamId(exercise.getId(), team.getId()));
        }
        else {
            participation = programmingExerciseStudentParticipationTestRepository.findByExerciseIdAndStudentLogin(exercise.getId(), username);
        }
        if (participation.isEmpty()) {
            throw new EntityNotFoundException("participation could not be found by exerciseId " + exercise.getId() + " and user " + username);
        }
        return participation.get();
    }

    /**
     * @param exercise the exercise the participations belong to
     * @param username the student's login
     * @return every participation the student has in that exercise, graded and practice
     */
    @NonNull
    public List<ProgrammingExerciseStudentParticipation> findStudentParticipationsByExerciseAndStudentLogin(Exercise exercise, String username) {
        return programmingExerciseStudentParticipationTestRepository.findAllByExerciseIdAndStudentLogin(exercise.getId(), username);
    }

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private TemplateProgrammingExerciseParticipationTestRepository templateProgrammingExerciseParticipationTestRepo;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepo;

    @Autowired
    private LocalVCRepositoryTestService localVCRepositoryTestService;

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
        localVCRepositoryTestService.ensureRepositoryExists(exercise.getProjectKey(), repoName);
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
        localVCRepositoryTestService.ensureRepositoryExists(exercise.getProjectKey(), repoName);
        // The tests repository has no participation of its own, but the exercise URI points at it, so it has to exist too.
        localVCRepositoryTestService.ensureRepositoryExists(exercise.getProjectKey(), exercise.generateRepositoryName(RepositoryType.TESTS));
        solutionProgrammingExerciseParticipationRepo.saveAndFlush(participation);
        exercise.setSolutionParticipation(participation);
        return programmingExerciseRepository.saveAndFlush(exercise);
    }
}
