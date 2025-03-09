package de.tum.cit.aet.artemis.programming;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

class ConsistencyCheckLocalVcJenkinsIntegrationTest extends AbstractProgrammingIntegrationJenkinsLocalVcTest {

    @BeforeEach
    void setup() throws Exception {
        consistencyCheckTestService.setup(this);
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsJobPermissionsService);
    }

    @AfterEach
    void tearDown() throws Exception {
        jenkinsRequestMockProvider.reset();
    }

    /**
     * Test consistencyCheck feature with programming exercise without inconsistencies
     *
     * @throws Exception if an error occurs
     */
    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void checkConsistencyOfProgrammingExercise_noErrors() throws Exception {
        consistencyCheckTestService.testCheckConsistencyOfProgrammingExercise_noErrors();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void checkConsistencyOfProgrammingExercise_missingVCSProject() throws Exception {
        consistencyCheckTestService.testCheckConsistencyOfProgrammingExercise_missingVCSProject();
    }

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void checkConsistencyOfProgrammingExercise_missingVCSRepos() throws Exception {
        consistencyCheckTestService.testCheckConsistencyOfProgrammingExercise_missingVCSRepos();
    }

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void checkConsistencyOfProgrammingExercise_buildPlansMissing() throws Exception {
        consistencyCheckTestService.testCheckConsistencyOfProgrammingExercise_buildPlansMissing();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void checkConsistencyOfProgrammingExercise_forbidden() throws Exception {
        consistencyCheckTestService.testCheckConsistencyOfProgrammingExercise_forbidden();
    }

}
