package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.web.rest.vm.LoggerVM;

class LogsResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetList() throws Exception {
        var logs = request.getList("/management/logs", HttpStatus.OK, LoggerVM.class);
        assertThat(logs).isNotNull();
        // TODO: add meaningful assertions
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testChangeLevel() throws Exception {
        LoggerVM logger = new LoggerVM();
        logger.setLevel("1");
        logger.setName("logger");
        request.put("/management/logs", logger, HttpStatus.NO_CONTENT);
        // TODO: add an assertion
    }
}
