package de.tum.cit.aet.artemis.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

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
}
