package de.tum.in.www1.artemis;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.vm.LoggerVM;

public class LogsResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    RequestUtilService request;

    @Test
    @WithMockUser(roles = "ADMIN")
    public void testGetList() throws Exception {
        request.get("/management/logs", HttpStatus.OK, List.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void testChangeLevel() throws Exception {
        LoggerVM logger = new LoggerVM();
        logger.setLevel("1");
        logger.setName("logger");
        request.put("/management/logs", logger, HttpStatus.NO_CONTENT);
    }
}
