package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class ProgrammingExerciseRepositoryTest extends AbstractProgrammingIntegrationIndependentTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    void updateProblemStatementAndTitleIfUnchanged_updatesOnlyWhenCurrentMetadataMatchesExpectation() {
        ProgrammingExercise exercise = createExercise("Original Title", "old statement");

        int updatedRows = programmingExerciseRepository.updateProblemStatementAndTitleIfUnchanged(exercise.getId(), "new statement", "New Title", "old statement",
                "Original Title");

        assertThat(updatedRows).isOne();
        assertPersistedMetadata(exercise.getId(), "new statement", "New Title");

        int staleUpdateRows = programmingExerciseRepository.updateProblemStatementAndTitleIfUnchanged(exercise.getId(), "stale overwrite", "Stale Title", "old statement",
                "Original Title");

        assertThat(staleUpdateRows).isZero();
        assertPersistedMetadata(exercise.getId(), "new statement", "New Title");
    }

    @Test
    void updateProblemStatementAndTitleIfUnchanged_isIdempotentForAlreadyAppliedTargetMetadata() {
        ProgrammingExercise exercise = createExercise("Original Title", "old statement");

        int firstUpdateRows = programmingExerciseRepository.updateProblemStatementAndTitleIfUnchanged(exercise.getId(), "new statement", "New Title", "old statement",
                "Original Title");
        int retryRows = programmingExerciseRepository.updateProblemStatementAndTitleIfUnchanged(exercise.getId(), "new statement", "New Title", "old statement", "Original Title");

        assertThat(firstUpdateRows).isOne();
        assertThat(retryRows).isOne();
        assertPersistedMetadata(exercise.getId(), "new statement", "New Title");
    }

    @Test
    void updateProblemStatementAndTitleIfUnchanged_rejectsMixedExpectedAndTargetMetadata() {
        ProgrammingExercise exercise = createExercise("Original Title", "new statement");

        int updatedRows = programmingExerciseRepository.updateProblemStatementAndTitleIfUnchanged(exercise.getId(), "new statement", "New Title", "old statement",
                "Original Title");

        assertThat(updatedRows).isZero();
        assertPersistedMetadata(exercise.getId(), "new statement", "Original Title");
    }

    @Test
    void updateProblemStatementAndTitleIfUnchanged_matchesNullProblemStatementSafely() {
        ProgrammingExercise exercise = createExercise("Original Title", null);

        int updatedRows = programmingExerciseRepository.updateProblemStatementAndTitleIfUnchanged(exercise.getId(), "new statement", "New Title", null, "Original Title");

        assertThat(updatedRows).isOne();
        assertPersistedMetadata(exercise.getId(), "new statement", "New Title");
    }

    private ProgrammingExercise createExercise(String title, String problemStatement) {
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise(false, title, "CAS" + System.nanoTime());
        ProgrammingExercise exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise.setTitle(title);
        exercise.setProblemStatement(problemStatement);
        exercise = programmingExerciseRepository.saveAndFlush(exercise);
        entityManager.clear();
        return exercise;
    }

    private void assertPersistedMetadata(long exerciseId, String problemStatement, String title) {
        entityManager.clear();
        ProgrammingExercise exercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);
        assertThat(exercise.getProblemStatement()).isEqualTo(problemStatement);
        assertThat(exercise.getTitle()).isEqualTo(title);
    }
}
