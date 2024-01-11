package de.tum.in.www1.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.repository.hestia.CodeHintRepository;

class IrisHestiaIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "irishestiaintegration";

    @Autowired
    private CodeHintRepository codeHintRepository;

    private ProgrammingExercise exercise;

    private CodeHint codeHint;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);

        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        activateIrisGlobally();
        activateIrisFor(course);
        activateIrisFor(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateSolutionEntriesOnSaving() throws Exception {
        addCodeHints();

        irisRequestMockProvider.mockMessageV2Response(Map.of("shortDescription", "Hello World Description", "longDescription", "Hello World Content"));

        var updatedCodeHint = request.postWithResponseBody("/api/programming-exercises/" + exercise.getId() + "/code-hints/" + codeHint.getId() + "/generate-description", null,
                CodeHint.class, HttpStatus.OK);

        assertThat(updatedCodeHint.getId()).isEqualTo(codeHint.getId());
        assertThat(updatedCodeHint.getDescription()).isEqualTo("Hello World Description");
        assertThat(updatedCodeHint.getContent()).isEqualTo("Hello World Content");
    }

    private void addCodeHints() {
        exercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(exercise);
        programmingExerciseUtilService.addHintsToExercise(exercise);
        programmingExerciseUtilService.addTasksToProgrammingExercise(exercise);
        programmingExerciseUtilService.addSolutionEntriesToProgrammingExercise(exercise);
        programmingExerciseUtilService.addCodeHintsToProgrammingExercise(exercise);
        codeHint = codeHintRepository.findByIdWithSolutionEntriesElseThrow(codeHintRepository.findByExerciseId(exercise.getId()).stream().findAny().orElseThrow().getId());
    }
}
