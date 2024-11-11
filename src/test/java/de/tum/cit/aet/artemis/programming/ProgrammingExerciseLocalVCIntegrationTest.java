package de.tum.cit.aet.artemis.programming;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

class ProgrammingExerciseLocalVCIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "programmingexerciselocalvc";

    @BeforeEach
    void initTestCase() throws Exception {
        programmingExerciseIntegrationTestService.setup(TEST_PREFIX, this, versionControlService, continuousIntegrationService);
    }

    @AfterEach
    void tearDown() throws IOException {
        programmingExerciseIntegrationTestService.tearDown();
    }

    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void firstTest() throws Exception {
        programmingExerciseIntegrationTestService.testValidateValidAuxiliaryRepository();
    }

    // TODO add all other tests
}
