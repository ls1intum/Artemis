package de.tum.cit.aet.artemis.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Verifies that the shared, read-only CSV seed ({@code config/liquibase/e2e/*.csv}) is loaded into the Independent
 * bucket's database when the Liquibase {@code seed} context is active. This is the plumbing check for Increment 1 of the
 * shared-seed-data work: tests in this bucket can reference {@link SeedData} entities instead of creating their own.
 */
class SeedDataIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Test
    void seededUsersArePresent() {
        assertThat(userTestRepository.findOneByLogin(SeedData.ADMIN_LOGIN)).as("seed admin").isPresent();
        assertThat(userTestRepository.findOneByLogin(SeedData.STUDENT_1_LOGIN)).as("seed student").isPresent();
        assertThat(userTestRepository.findOneByLogin(SeedData.TUTOR_LOGIN)).as("seed tutor").isPresent();
        assertThat(userTestRepository.findOneByLogin(SeedData.INSTRUCTOR_LOGIN)).as("seed instructor").isPresent();
    }

    @Test
    void allSeededStudentsArePresent() {
        for (int index : SeedData.STUDENT_INDICES) {
            assertThat(userTestRepository.findOneByLogin(SeedData.userLogin(index))).as("seed student %d", index).isPresent();
        }
    }

    @Test
    void seededBaselineCourseIsPresent() {
        assertThat(courseRepository.findById(SeedData.COURSE_CHANNEL_1_ID)).as("seed course 9001").get().returns(SeedData.COURSE_CHANNEL_1_SHORT_NAME,
                course -> course.getShortName());
    }

    /**
     * Demonstrates that the seeded text exercise (with its submissions, results and feedback) is fully usable through the
     * real REST API with zero data creation: the seed instructor — enrolled in course 9001 — fetches the exercise's
     * submissions and gets the three seeded, assessed submissions back. This is the pattern read-only assessment and
     * submission tests can adopt to drop their per-test course/exercise/submission setup.
     */
    @Test
    @WithMockUser(username = SeedData.INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void seededTextExerciseSubmissionsAreReadableViaApi() throws Exception {
        List<TextSubmission> submissions = request.getList("/api/text/exercises/" + SeedData.TEXT_EXERCISE_ID + "/text-submissions", HttpStatus.OK, TextSubmission.class);
        assertThat(submissions).as("seeded, assessed submissions for the seed text exercise").hasSize(3).allSatisfy(submission -> assertThat(submission.isSubmitted()).isTrue());
    }
}
