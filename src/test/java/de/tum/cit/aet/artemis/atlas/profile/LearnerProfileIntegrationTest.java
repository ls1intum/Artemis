package de.tum.cit.aet.artemis.atlas.profile;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.core.domain.User;

class LearnerProfileIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "learnerprofiledatabase";

    private static final int NUMBER_OF_STUDENTS = 1;

    private static final String STUDENT1_OF_COURSE = TEST_PREFIX + "student1";

    @BeforeEach
    void setupTestScenario() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 1, 1, 1);

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student1337");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor1337");

        learnerProfileUtilService.createLearnerProfilesForUsers(TEST_PREFIX);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testLazyFetchLearnerProfile() {
        User user = userTestRepository.getUserWithGroupsAndAuthorities(STUDENT1_OF_COURSE);
        assertThat(Hibernate.isInitialized(user.getLearnerProfile())).isFalse();
    }
}
