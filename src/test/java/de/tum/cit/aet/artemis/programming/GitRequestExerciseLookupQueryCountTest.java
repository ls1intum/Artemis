package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

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
 * so a secondary select here is one the server pays on every git operation of every student. Authorization reads a
 * projection rather than the entity, and the projection has to stay a single query: it joins the course, and for an
 * exam exercise the exercise group, its exam and that exam's course, but selects only scalars from them. Splitting one
 * of those joins off does not avoid reading it, it turns it into an extra round trip - which is what these tests catch.
 * <p>
 * The entity lookup is pinned alongside it because the ssh path and the build-agent paths still read the exercise, and
 * are subject to the same rule: a missing eager association becomes a secondary select rather than no read at all.
 */
class GitRequestExerciseLookupQueryCountTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "gitlookupcount";

    /**
     * One query, whether the path reads the entity with its eager associations or the projection over the same joins.
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

    /**
     * The projection the authorization path actually reads. It joins the same associations but selects scalars from
     * them, so it stays one query and, unlike the entity, hydrates nothing.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExamExerciseAccessProjectionIsResolvedInOneQuery() {
        assertThatDb(() -> programmingExerciseTestRepository.findAccessProjectionByProjectKey(examProgrammingExercise.getProjectKey()))
                .hasBeenCalledAtMostTimes(EXERCISE_LOOKUP_QUERY_COUNT);
    }

    /**
     * A course exercise leaves the whole exam side of the join empty. Every value taken from it has to survive that,
     * which a primitive component would not: the projection failed to instantiate for every course exercise until
     * {@code testExam} became nullable. It also has to stay within the same budget as the exam case, since the empty
     * side of the join is where a follow-up load would be easiest to introduce unnoticed.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCourseExerciseAccessProjectionResolvesWithoutAnExam() {
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise courseExercise = (ProgrammingExercise) course.getExercises().iterator().next();

        var projection = assertThatDb(() -> programmingExerciseTestRepository.findAccessProjectionByProjectKey(courseExercise.getProjectKey()))
                .hasBeenCalledAtMostTimes(EXERCISE_LOOKUP_QUERY_COUNT);

        assertThat(projection).singleElement().satisfies(access -> {
            assertThat(access.isExamExercise()).isFalse();
            assertThat(access.examId()).isNull();
            assertThat(access.isTestExamExercise()).isFalse();
            assertThat(access.courseId()).isEqualTo(courseExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        });
    }
}
