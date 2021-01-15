package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import de.tum.in.www1.artemis.config.ApiVersionFilter;

public class ContentVersionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(1, 0, 0);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getAccountWithoutLoggedInUser() throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.get(new URI("/api/account"))).andReturn();
        final MockHttpServletResponse response = res.getResponse();
        final String contentVersionHeader = response.getHeader(ApiVersionFilter.CONTENT_VERSION_HEADER);

        assertThat(contentVersionHeader).isEqualTo("1.3.3-beta7");
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

}
