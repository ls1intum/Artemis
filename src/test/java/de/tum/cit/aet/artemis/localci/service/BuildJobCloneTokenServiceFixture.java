package de.tum.cit.aet.artemis.localci.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Creates a {@link BuildJobCloneTokenService} that reads the repositories of one participation and one exercise from mocked repositories, in the way the database holds them: the
 * participation 1 with the repository {@code testexercise-student1}, and the exercise 3 with the project key {@code TESTEXERCISE} and the repositories {@code tests},
 * {@code solution}, {@code aux1} and {@code aux2}.
 */
public final class BuildJobCloneTokenServiceFixture {

    public static final long PARTICIPATION_ID = 1L;

    public static final long EXERCISE_ID = 3L;

    private static final String BASE = "http://localhost:8000/git/TESTEXERCISE/testexercise-";

    private BuildJobCloneTokenServiceFixture() {
        // utility class
    }

    /**
     * Creates the service on top of the repositories of the fixture.
     *
     * @return a service whose repositories hold the participation and the exercise of the fixture
     */
    public static BuildJobCloneTokenService service() {
        return service(mock(ParticipationTestRepository.class), mock(ProgrammingExerciseTestRepository.class));
    }

    /**
     * Fills the given repositories with the participation and the exercise of the fixture and creates a service on top of them.
     *
     * @param participationRepository       the repository to fill
     * @param programmingExerciseRepository the repository to fill
     * @return the service
     */
    public static BuildJobCloneTokenService service(ParticipationTestRepository participationRepository, ProgrammingExerciseTestRepository programmingExerciseRepository) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setTestRepositoryUri(BASE + "tests.git");
        SolutionProgrammingExerciseParticipation solutionParticipation = new SolutionProgrammingExerciseParticipation();
        solutionParticipation.setRepositoryUri(BASE + "solution.git");
        exercise.setSolutionParticipation(solutionParticipation);
        AuxiliaryRepository first = new AuxiliaryRepository();
        first.setRepositoryUri(BASE + "aux1.git");
        AuxiliaryRepository second = new AuxiliaryRepository();
        second.setRepositoryUri(BASE + "aux2.git");
        exercise.setAuxiliaryRepositories(Set.of(first, second));

        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(PARTICIPATION_ID);
        participation.setRepositoryUri(BASE + "student1.git");
        participation.setProgrammingExercise(exercise);

        when(participationRepository.findWithProgrammingExerciseById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));
        when(participationRepository.findWithProgrammingExerciseById(anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            return id == PARTICIPATION_ID ? Optional.of(participation) : Optional.empty();
        });
        when(programmingExerciseRepository.getProgrammingExerciseFromParticipation(any())).thenReturn(exercise);
        when(programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            return id == EXERCISE_ID ? Optional.of(exercise) : Optional.empty();
        });
        return new BuildJobCloneTokenService(participationRepository, programmingExerciseRepository);
    }
}
