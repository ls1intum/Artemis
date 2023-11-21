package de.tum.in.www1.artemis.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.service.connectors.aeolus.*;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

class AeolusTemplateResourceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "aeolusintegration";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    protected RequestUtilService request;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAeolusTemplateFile() throws Exception {
        String javaDefaultAeolusTemplate = request.get("/api/aeolus/templates/JAVA/PLAIN_GRADLE", HttpStatus.OK, String.class);
        assertThat(javaDefaultAeolusTemplate).isNotEmpty();
        String javaSequentialAeolusTemplate = request.get("/api/aeolus/templates/JAVA/PLAIN_GRADLE?sequentialRuns=true", HttpStatus.OK, String.class);
        assertThat(javaSequentialAeolusTemplate).isNotEmpty();
        String javaNormalAeolusTemplate = request.get("/api/aeolus/templates/JAVA/PLAIN_MAVEN", HttpStatus.OK, String.class);
        assertThat(javaNormalAeolusTemplate).isNotEmpty();
        javaSequentialAeolusTemplate = request.get("/api/aeolus/templates/JAVA/PLAIN_MAVEN?sequentialRuns=true", HttpStatus.OK, String.class);
        assertThat(javaSequentialAeolusTemplate).isNotEmpty();
        String pythonTemplate = request.get("/api/aeolus/templates/PYTHON/", HttpStatus.OK, String.class);
        assertThat(pythonTemplate).isNotEmpty();
    }
}
