package de.tum.cit.aet.artemis.atlas.profile;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.shared.TestRepositoryConfiguration;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
@Execution(ExecutionMode.CONCURRENT)
@Import(TestRepositoryConfiguration.class)
@AutoConfigureEmbeddedDatabase
public class LearnerProfileArchitectureTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "learningpathintegration";

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
    void testAll_asStudent() {
        User user = userTestRepository.getUserWithGroupsAndAuthorities(STUDENT1_OF_COURSE);
        assertFalse(Hibernate.isInitialized(user.getLearnerProfile()));
    }
}
