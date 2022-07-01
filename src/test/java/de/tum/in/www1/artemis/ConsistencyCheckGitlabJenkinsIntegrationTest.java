package de.tum.in.www1.artemis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.service.ConsistencyCheckServiceTest;

class ConsistencyCheckGitlabJenkinsIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private ConsistencyCheckServiceTest consistencyCheckServiceTest;

    @BeforeEach
    void setup() throws Exception {
        consistencyCheckServiceTest.setup(this);
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
        database.resetDatabase();
    }

    /**
     * Test consistencyCheck feature with programming exercise without
     * inconsistencies
     * @throws Exception if an error occurs
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void checkConsistencyOfProgrammingExercise_noErrors() throws Exception {
        consistencyCheckServiceTest.testCheckConsistencyOfProgrammingExercise_noErrors();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void checkConsistencyOfProgrammingExercise_missingVCSProject() throws Exception {
        consistencyCheckServiceTest.testCheckConsistencyOfProgrammingExercise_missingVCSProject();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void checkConsistencyOfProgrammingExercise_missingVCSRepos() throws Exception {
        consistencyCheckServiceTest.testCheckConsistencyOfProgrammingExercise_missingVCSRepos();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void checkConsistencyOfProgrammingExercise_buildPlansMissing() throws Exception {
        consistencyCheckServiceTest.testCheckConsistencyOfProgrammingExercise_buildPlansMissing();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void checkConsistencyOfProgrammingExercise_isLocalSimulation() throws Exception {
        consistencyCheckServiceTest.testCheckConsistencyOfProgrammingExercise_isLocalSimulation();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void checkConsistencyOfProgrammingExercise_forbidden() throws Exception {
        consistencyCheckServiceTest.testCheckConsistencyOfProgrammingExercise_forbidden();
    }

}
