package de.tum.in.www1.artemis.programmingexercise;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.util.ProgrammingExerciseTestService;

class ProgrammingExerciseGitlabJenkinsIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @BeforeEach
    void setup() throws Exception {
        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws IOException {
        programmingExerciseTestService.tearDown();
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
    }

    @ParameterizedTest
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_validExercise_created(ExerciseMode mode) throws Exception {
        programmingExerciseTestService.createProgrammingExercise_mode_validExercise_created(mode);
    }

    // TODO: add all other test cases from ProgrammingExerciseBitbucketBambooIntegrationTest and mock the corresponding REST calls in the empty implementations in
    // AbstractSpringIntegrationJenkinsGitlabTest
}
