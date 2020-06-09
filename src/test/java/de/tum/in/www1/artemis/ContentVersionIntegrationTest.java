package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import de.tum.in.www1.artemis.config.ApiVersionFilter;

public class ContentVersionIntegrationTest extends AbstractSpringDevelopmentTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @WithMockUser("authenticateduser")
    public void getAccountWithoutLoggedInUser() throws Exception {
        MvcResult res = mvc.perform(MockMvcRequestBuilders.get(new URI("/api/account")).with(csrf())).andReturn();
        final MockHttpServletResponse response = res.getResponse();
        final String contentVersionHeader = response.getHeader(ApiVersionFilter.CONTENT_VERSION_HEADER);

        assertThat(contentVersionHeader).isEqualTo("1.3.3-beta7");
    }

}
