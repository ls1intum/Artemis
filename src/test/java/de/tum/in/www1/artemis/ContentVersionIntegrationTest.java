package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import de.tum.in.www1.artemis.config.ApiVersionFilter;

class ContentVersionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @BeforeEach
    void initTestCase() {
        database.addUsers(1, 0, 0, 0);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testContentVersionHeaderIsSetCorrectly() throws Exception {
        MvcResult res = request.getMvc().perform(MockMvcRequestBuilders.get(new URI("/api/account"))).andReturn();
        final MockHttpServletResponse response = res.getResponse();
        final String contentVersionHeader = response.getHeader(ApiVersionFilter.CONTENT_VERSION_HEADER);
        assertThat(contentVersionHeader).isEqualTo("1.3.3-beta7");
    }

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

}
