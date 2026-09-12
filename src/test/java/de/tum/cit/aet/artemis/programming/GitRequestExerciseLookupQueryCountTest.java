package de.tum.cit.aet.artemis.programming;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Pins the number of queries the git request path spends resolving its exercise.
 * <p>
 * Every clone, fetch and push resolves the exercise from the project key in the URL before it can authorize anything,
 * so a secondary select here is one the server pays on every git operation of every student. The lookup is a single
 * query only as long as its entity graph covers every eager association the path reads: the course for the role
 * checks, and for an exam exercise the exercise group, its exam and that exam's course for the date checks. Leaving one
 * out does not avoid reading it, it turns it into an extra round trip - which is what this test would catch.
 */
class GitRequestExerciseLookupQueryCountTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "gitlookupcount";

    /**
     * One query: the exercise with its submission policy, course, exercise group, exam and the exam's course.
     */
    private static final int EXERCISE_LOOKUP_QUERY_COUNT = 1;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseTestRepository;

    private ProgrammingExercise examProgrammingExercise;

    @BeforeEach
    void init() {
        examProgrammingExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExamExerciseIsResolvedInOneQuery() {
        assertThatDb(() -> programmingExerciseTestRepository.findOneByProjectKeyOrThrow(examProgrammingExercise.getProjectKey(), true))
                .hasBeenCalledAtMostTimes(EXERCISE_LOOKUP_QUERY_COUNT);
    }
}
