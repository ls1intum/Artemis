package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;

/**
 * The solution participation is looked up by project key where only the participation itself is needed, such as when
 * an access log entry has to be attributed to a repository. The project key is mapped on a secondary table, so the
 * query is exercised against a database rather than only against a mock.
 */
class SolutionParticipationByProjectKeyTest extends AbstractSpringIntegrationIndependentBatchTest {

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository;

    @Test
    void findsTheSolutionParticipationOfTheExerciseTheProjectKeyIdentifies() {
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        var found = solutionParticipationRepository.findByProjectKey(exercise.getProjectKey());

        assertThat(found).isPresent();
        assertThat(found.get().getProgrammingExercise().getId()).isEqualTo(exercise.getId());
    }

    @Test
    void findsNothingForAProjectKeyThatBelongsToNoExercise() {
        assertThat(solutionParticipationRepository.findByProjectKey("NOSUCHPROJECTKEY")).isEmpty();
    }
}
