package de.tum.in.www1.artemis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.service.ConsistencyCheckServiceTest;

public class ConsistencyCheckGitlabJenkinsIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private ConsistencyCheckServiceTest consistencyCheckServiceTest;

    @BeforeEach
    public void setup() throws Exception {
        consistencyCheckServiceTest.setup(this, versionControlService);
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    public void tearDown() throws Exception {
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
    }

    /**
     * Test consistencyCheck feature with programming exercise without
     * inconsistencies
     * @throws Exception if an error occurs
     */
    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    public void checkConsistencyOfProgrammingExercise_noErrors() throws Exception {
        consistencyCheckServiceTest.checkConsistencyOfProgrammingExercise_noErrors();
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    public void checkConsistencyOfProgrammingExercise_missingVCSProject() throws Exception {
        consistencyCheckServiceTest.checkConsistencyOfProgrammingExercise_missingVCSProject();
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    public void checkConsistencyOfProgrammingExercise_missingVCSRepos() throws Exception {
        consistencyCheckServiceTest.checkConsistencyOfProgrammingExercise_missingVCSRepos();
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    public void checkConsistencyOfProgrammingExercise_buildPlansMissing() throws Exception {
        consistencyCheckServiceTest.checkConsistencyOfProgrammingExercise_buildPlansMissing();
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    public void checkConsistencyOfProgrammingExercise_isLocalSimulation() throws Exception {
        consistencyCheckServiceTest.checkConsistencyOfProgrammingExercise_isLocalSimulation();
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    public void checkConsistencyOfCourse() throws Exception {
        consistencyCheckServiceTest.checkConsistencyOfCourse();
    }

}
