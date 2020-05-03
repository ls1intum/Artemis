package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.util.RequestUtilService;

public class UserMetricsResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    RequestUtilService request;

    @Test
    @WithMockUser(roles = "ADMIN")
    public void testGetNumberOfUsers() throws Exception {
        int numberOfUsers = request.get("/api/management/usermetrics", HttpStatus.OK, Integer.class);
        assertThat(numberOfUsers).isInstanceOf(Integer.class);
    }
}
