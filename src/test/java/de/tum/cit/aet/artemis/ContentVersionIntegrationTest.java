package de.tum.cit.aet.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import de.tum.cit.aet.artemis.core.security.filter.ApiVersionFilter;

class ContentVersionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "contentversion";

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testContentVersionHeaderIsSetCorrectly() throws Exception {
        MvcResult res = request.performMvcRequest(MockMvcRequestBuilders.get(new URI("/api/public/account"))).andReturn();
        final MockHttpServletResponse response = res.getResponse();
        final String contentVersionHeader = response.getHeader(ApiVersionFilter.CONTENT_VERSION_HEADER);
        assertThat(contentVersionHeader).isEqualTo("1.3.3-beta7");
    }

}
