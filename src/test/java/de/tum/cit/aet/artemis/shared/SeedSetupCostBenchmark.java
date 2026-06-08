package de.tum.cit.aet.artemis.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Manually-run (NOT part of CI) benchmark that quantifies what a typical heavy {@code @BeforeEach} costs versus reusing
 * the shared seed. Both loops run in the same warm Spring context, so the only difference measured is the per-test data
 * setup: a real class with N tests pays this cost N times.
 * <p>
 * Run with {@code SEED_BENCHMARK=true ./gradlew test -x webapp --tests 'SeedSetupCostBenchmark'} and read the
 * {@code [SEED BENCHMARK]} lines from the output.
 */
@EnabledIfEnvironmentVariable(named = "SEED_BENCHMARK", matches = "true")
class SeedSetupCostBenchmark extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "seedbench";

    private static final int WARMUP = 3;

    private static final int ITERATIONS = 12;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Test
    void compareHeavySetupAgainstSeedReuse() {
        // Warm up both paths (JIT, first-touch caches) so the measured iterations are representative.
        for (int i = 0; i < WARMUP; i++) {
            createTypicalBeforeEachFixture("warmup" + i);
            readSeedFixture();
        }

        long createNanos = 0;
        long seedNanos = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            long t0 = System.nanoTime();
            createTypicalBeforeEachFixture("iter" + i);
            createNanos += System.nanoTime() - t0;

            long t1 = System.nanoTime();
            readSeedFixture();
            seedNanos += System.nanoTime() - t1;
        }

        double createMs = createNanos / 1_000_000.0 / ITERATIONS;
        double seedMs = seedNanos / 1_000_000.0 / ITERATIONS;
        System.out.println(String.format(Locale.ROOT, "%n[SEED BENCHMARK] heavy @BeforeEach (users + course + text exercise + 3 assessed submissions): %.1f ms/test", createMs));
        System.out.println(String.format(Locale.ROOT, "[SEED BENCHMARK] reuse shared seed instead:                                              %.1f ms/test", seedMs));
        System.out.println(
                String.format(Locale.ROOT, "[SEED BENCHMARK] saved per test: %.1f ms  ->  a 20-test class saves ~%.1f s%n", createMs - seedMs, (createMs - seedMs) * 20 / 1000.0));
    }

    /**
     * Builds the same graph the shared seed already provides — a cohort of users, a course with a finished text
     * exercise, and three students' assessed text submissions with feedback — which is what a heavy {@code @BeforeEach}
     * in an assessment/submission test recreates for every single test method.
     */
    private void createTypicalBeforeEachFixture(String prefix) {
        userUtilService.addUsers(prefix, 3, 1, 0, 1);
        Course course = textExerciseUtilService.addCourseWithOneFinishedTextExercise();
        TextExercise exercise = ExerciseUtilService.findTextExerciseWithTitle(course.getExercises(), "Finished");
        for (int student = 1; student <= 3; student++) {
            textExerciseUtilService.addTextSubmissionWithResultAndAssessorAndFeedbacks(exercise,
                    ParticipationFactory.generateTextSubmission("submission " + student, Language.ENGLISH, true), prefix + "student" + student, prefix + "tutor1",
                    ParticipationFactory.generateManualFeedback());
        }
    }

    /** Reusing the seed instead: no creation, just reference the pre-seeded course and assessed text exercise. */
    private void readSeedFixture() {
        assertThat(courseRepository.findById(SeedData.COURSE_CHANNEL_1_ID)).isPresent();
        assertThat(userTestRepository.findOneByLogin(SeedData.STUDENT_1_LOGIN)).isPresent();
    }
}
