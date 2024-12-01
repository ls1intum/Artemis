package de.tum.cit.aet.artemis.programming;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

class ConsistencyCheckGitlabJenkinsIntegrationTest extends AbstractProgrammingIntegrationJenkinsGitlabTest {

    @BeforeEach
    void setup() throws Exception {
        consistencyCheckTestService.setup(this);
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
    }

    /**
     * Test consistencyCheck feature with programming exercise without
     * inconsistencies
     *
     * @throws Exception if an error occurs
     */
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

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void checkConsistencyOfProgrammingExercise_missingVCSRepos() throws Exception {
        consistencyCheckTestService.testCheckConsistencyOfProgrammingExercise_missingVCSRepos();
    }

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
