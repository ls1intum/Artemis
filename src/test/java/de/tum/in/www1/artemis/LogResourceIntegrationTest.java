package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.web.rest.vm.LoggerVM;

class LogResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetList() throws Exception {
        request.get("/api/admin/logs", HttpStatus.OK, List.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testChangeLevel() throws Exception {
        LoggerVM logger = new LoggerVM();
        logger.setLevel("DEBUG");
        logger.setName("logger");
        LoggerVM response = request.putWithResponseBody("/api/admin/logs", logger, LoggerVM.class, HttpStatus.OK);
        assertThat(response).isEqualTo(logger);
    }
}
