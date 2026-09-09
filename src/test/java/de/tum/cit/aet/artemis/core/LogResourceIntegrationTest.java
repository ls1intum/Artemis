package de.tum.cit.aet.artemis.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.dto.vm.LoggerVM;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class LogResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "logresource";

    @BeforeEach
    void setUp() {
        // The admin endpoints resolve the authenticated login against the database, so the account the tests
        // authenticate as has to exist there with the admin authority rather than only in the mock security context.
        userUtilService.addAdmin(TEST_PREFIX);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetList() throws Exception {
        request.get("/api/admin/logs", HttpStatus.OK, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testChangeLevel() throws Exception {
        LoggerVM logger = new LoggerVM();
        logger.setLevel("DEBUG");
        logger.setName("logger");
        LoggerVM response = request.putWithResponseBody("/api/admin/logs", logger, LoggerVM.class, HttpStatus.OK);
        assertThat(response).isEqualTo(logger);
    }
}
