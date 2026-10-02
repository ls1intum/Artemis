package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.history.GenerationVersionRecoveryService.Recovery;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCaseType;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTaskTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class GenerationRestoreTestCasesPersistenceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private GenerationRestoreTestCasesService restore;

    @Autowired
    private ProblemStatementMetadataUpdateService metadata;

    @Autowired
    private ProgrammingExerciseUtilService programmingExercises;

    @Autowired
    private ProgrammingExerciseTestCaseTestRepository tests;

    @Autowired
    private ProgrammingExerciseTaskTestRepository tasks;

    @Test
    void restoresGradingAndTaskBindingsDirectlyFromTheVersionWithoutABuild() throws Exception {
        var course = programmingExercises.addCourseWithOneProgrammingExercise();
        var exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        var test = new ProgrammingExerciseTestCase();
        test.setExercise(exercise);
        test.setTestName("boundary");
        test.setActive(true);
        test.setType(ProgrammingExerciseTestCaseType.DEFAULT);
        test.setWeight(4.0);
        test.setBonusMultiplier(1.0);
        test.setBonusPoints(0.0);
        test.setVisibility(Visibility.ALWAYS);
        test = tests.saveAndFlush(test);
        var before = JsonObjectMapper.get().readValue("""
                {"id":%d,"programmingData":{"testCases":[{"id":%d,"testName":"boundary","active":true,"type":"DEFAULT", "weight":2.0,
                  "bonusMultiplier":1.5,"bonusPoints":3.0,"visibility":"AFTER_DUE_DATE"}]}}
                """.formatted(exercise.getId(), test.getId()), ExerciseSnapshotDTO.class);
        var after = JsonObjectMapper.get().readValue("""
                {"id":%d,"programmingData":{"testCases":[{"id":%d,"testName":"boundary","active":true,"type":"DEFAULT", "weight":4.0,
                  "bonusMultiplier":1.0,"bonusPoints":0.0,"visibility":"ALWAYS"}]}}
                """.formatted(exercise.getId(), test.getId()), ExerciseSnapshotDTO.class);
        var pair = new Recovery("local-test", 8L, before, after, null);
        assertThat(restore.canRestore(exercise.getId(), pair)).isTrue();
        restore.restore(exercise.getId(), pair, () -> true);
        String originalStatement = "[task][Original task](<testid>" + test.getId() + "</testid>)";
        assertThat(metadata.updateProblemStatementAndTasks(exercise, originalStatement, exercise.getTitle(), exercise.getProblemStatement(), exercise.getTitle())).isEqualTo(1);

        var reloaded = tests.findByIdElseThrow(test.getId());
        assertThat(reloaded.getWeight()).isEqualTo(2.0);
        assertThat(reloaded.getBonusMultiplier()).isEqualTo(1.5);
        assertThat(reloaded.getBonusPoints()).isEqualTo(3.0);
        assertThat(reloaded.getVisibility()).isEqualTo(Visibility.AFTER_DUE_DATE);
        assertThat(reloaded.isActive()).isTrue();
        var restoredTasks = tasks.findByExerciseIdWithTestCases(exercise.getId());
        assertThat(restoredTasks).singleElement().satisfies(task -> {
            assertThat(task.getTaskName()).isEqualTo("Original task");
            assertThat(task.getTestCases()).extracting(ProgrammingExerciseTestCase::getTestName).containsExactly("boundary");
        });
        assertThat(restore.canRestore(exercise.getId(), pair)).isTrue();
    }
}
