package de.tum.in.www1.artemis.exercise.programmingexercise;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.service.ConsistencyCheckTestService;

class ConsistencyCheckBitbucketBambooIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ConsistencyCheckTestService consistencyCheckTestService;

    @BeforeEach
    void setup() throws Exception {
        consistencyCheckTestService.setup(this);
        bambooRequestMockProvider.enableMockingOfRequests();
        bitbucketRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        bitbucketRequestMockProvider.reset();
        bambooRequestMockProvider.reset();
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
